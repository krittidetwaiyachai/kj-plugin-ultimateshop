package xyz.kaijiieow.kjshopplus.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.kaijiieow.kjshopplus.config.model.MainCategoryMenu;
import xyz.kaijiieow.kjshopplus.config.model.MenuItem;
import xyz.kaijiieow.kjshopplus.config.model.ShopCategory;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
import xyz.kaijiieow.kjshopplus.economy.PriceUtil;
import xyz.kaijiieow.kjshopplus.gui.util.ItemBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class GUIManager {

    private final KJShopPlus plugin;
    private final Set<UUID> openMenus = new HashSet<>();

    // --- เพิ่ม GUITYPE ใหม่ ---
    public enum GUITYPE {
        CATEGORY_MAIN, SHOP_PAGE, QUANTITY_SELECTOR // เปลี่ยน TRADE_CONFIRM เป็น QUANTITY_SELECTOR
    }
    // --- จบ ---

    public GUIManager(KJShopPlus plugin) {
        this.plugin = plugin;
    }

    public void openCategoryMenu(Player player) {
        MainCategoryMenu menuConfig = plugin.getShopManager().getMainCategoryMenu();
        if (menuConfig == null) {
            plugin.getLogger().severe("Cannot open category menu: MainCategoryMenu is null! (Check categories.yml)");
            plugin.getMessageManager().sendMessage(player, "shop_disabled");
            return;
        }

        String title = ChatColor.translateAlternateColorCodes('&', menuConfig.getTitle());
        int size = menuConfig.getSize();

        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.CATEGORY_MAIN, "main", 1);
        Inventory inv = Bukkit.createInventory(guiData, size, title);
        guiData.setInventory(inv);

        // --- START FIXED LOGIC ---

        // 1. Place category items FIRST
        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId()); // Get once
        for (MenuItem menuItem : menuConfig.getCategoryItems().values()) {
            if (menuItem.isEnable()) {
                ItemStack item = menuItem.build(player, isBedrock);
                if (item != null && item.getType() != Material.AIR) {
                    if (menuItem.getSlot() >= 0 && menuItem.getSlot() < size) {
                        inv.setItem(menuItem.getSlot(), item); // Set directly
                    } else {
                        plugin.getLogger().warning("Category item '" + menuItem.getId() + "' has invalid slot: " + menuItem.getSlot());
                    }
                }
            }
        }

        // 2. Place close button
        MenuItem closeButtonConfig = menuConfig.getCloseButton();
        if (closeButtonConfig != null) {
            ItemStack closeItem = closeButtonConfig.build(player, isBedrock);
            if (closeItem != null && closeItem.getType() != Material.AIR) {
                 if (closeButtonConfig.getSlot() >= 0 && closeButtonConfig.getSlot() < size) {
                     inv.setItem(closeButtonConfig.getSlot(), closeItem); // Set directly
                 } else {
                     plugin.getLogger().warning("Close button has invalid slot: " + closeButtonConfig.getSlot());
                 }
            }
        }

        // 3. Place player info item
        MenuItem playerInfoConfig = menuConfig.getPlayerInfoItem();
        if (playerInfoConfig != null) {
            ItemStack infoItem = buildPlayerInfoItem(player, playerInfoConfig);
             if (infoItem != null && infoItem.getType() != Material.AIR) {
                 if (playerInfoConfig.getSlot() >= 0 && playerInfoConfig.getSlot() < size) {
                    inv.setItem(playerInfoConfig.getSlot(), infoItem); // Set directly
                 } else {
                      plugin.getLogger().warning("Player info item has invalid slot: " + playerInfoConfig.getSlot());
                 }
             }
        }

        // 4. Fill REMAINING empty slots with fillItem
        if (menuConfig.getFillItem() != null) {
            ItemStack fill = menuConfig.getFillItem().build(player, false); // No need for bedrock check on fill item
            for (int i = 0; i < size; i++) {
                // Only set if the slot is currently empty (null or AIR)
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, fill);
                }
            }
        }

        // --- END FIXED LOGIC ---

        openMenu(player, inv);
    }

    private ItemStack buildPlayerInfoItem(Player player, MenuItem config) {
        Material headMaterial = mapBedrockMaterial(Material.PLAYER_HEAD, player);
        ItemBuilder builder = new ItemBuilder(headMaterial);

        if (headMaterial == Material.PLAYER_HEAD) {
            ItemStack head = builder.build();
            ItemMeta meta = head.getItemMeta();
             if (meta instanceof SkullMeta skullMeta) {
                 skullMeta.setOwningPlayer(player);
                 head.setItemMeta(skullMeta);
                 builder = new ItemBuilder(head);
             }
        }

        String name = config.getName();
        name = name.replace("{player_name}", player.getName());
        builder.setName(ChatColor.translateAlternateColorCodes('&', name));

        List<String> finalLore = new ArrayList<>();
        if (config.getLore() != null) {
            config.getLore().forEach(line -> finalLore.add(ChatColor.translateAlternateColorCodes('&', line)));
        }

        for (String currencyId : plugin.getConfigManager().getAllCurrencyIds()) {
            String symbol = plugin.getCurrencyService().getCurrencySymbol(currencyId);
            double balance = plugin.getCurrencyService().getBalance(player, currencyId);
            String balanceLine = "&f" + capitalize(currencyId) + ": &a" + symbol + PriceUtil.format(balance);
            finalLore.add(ChatColor.translateAlternateColorCodes('&', balanceLine));
        }

        builder.setLore(finalLore);

        return builder.build();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }


    public void openShopPage(Player player, String categoryId, int page) {
        ShopCategory category = plugin.getShopManager().getShopCategory(categoryId);
        if (category == null) {
            plugin.getLogger().severe("Cannot open shop page: ShopCategory '" + categoryId + "' not found in ShopManager!");
            plugin.getMessageManager().sendMessage(player, "shop_disabled");
            return;
        }

        List<ShopItem> items = category.getShopItems();
        if (items.isEmpty()) {
            plugin.getLogger().warning("Opening category '" + categoryId + "' but it has 0 items loaded.");
            // Don't return, still show the empty page with buttons
        }

        int itemsPerPage = calculateItemsPerPage(category.getSize());
        int totalPages = (itemsPerPage <= 0) ? 1 : (int) Math.ceil((double) items.size() / itemsPerPage);
        if (page < 1) page = 1;
        if (totalPages == 0) totalPages = 1; // Ensure totalPages is at least 1
        if (page > totalPages) page = totalPages;

        String title = ChatColor.translateAlternateColorCodes('&', category.getTitle(page, totalPages));

        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.SHOP_PAGE, categoryId, page);
        Inventory inv = Bukkit.createInventory(guiData, category.getSize(), title);
        guiData.setInventory(inv);

        // --- START CHANGES for openShopPage ---

        // Map to keep track of occupied slots by layout items
        Set<Integer> layoutSlots = new HashSet<>();

        // 1. Place layout items (buttons) FIRST and record their slots
        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());
        for (MenuItem layoutItem : category.getLayoutItems().values()) {
            String action = layoutItem.getAction();
            if (action == null) continue;

            // Skip page buttons if not applicable
            if (action.equals("PAGE_PREV") && page <= 1) continue;
            if (action.equals("PAGE_NEXT") && page >= totalPages) continue;

            ItemStack item = layoutItem.build(player, isBedrock);
             if (item != null && item.getType() != Material.AIR) {
                 int slot = layoutItem.getSlot();
                 if (slot >= 0 && slot < category.getSize()) {
                     inv.setItem(slot, item);
                     layoutSlots.add(slot); // Record the occupied slot
                 } else {
                     plugin.getLogger().warning("Layout item '" + layoutItem.getId() + "' has invalid slot: " + slot + " in category '" + categoryId + "'");
                 }
            }
        }

        // 2. Place shop items into available slots
        int startIndex = (page - 1) * itemsPerPage;
        int slotIndex = 0; // Index for auto-placement
        int itemsPlacedThisPage = 0;

        if (itemsPerPage > 0) { // Only try to place items if there's space
            // --- แก้ไข: วนลูป items ---
            for (int i = 0; i < items.size(); i++) {
                if (i < startIndex || itemsPlacedThisPage >= itemsPerPage) {
                    continue; // ข้ามไอเทมหน้าก่อนๆ หรือถ้าหน้านี้เต็มแล้ว
                }

                ShopItem shopItem = items.get(i);
                // --- จบ ---
                int targetSlot = -1;

                // Check if a specific slot is defined and VALID and NOT occupied by layout
                if (shopItem.getSlot() != -1 && shopItem.getSlot() < category.getSize()) {
                    if (!layoutSlots.contains(shopItem.getSlot())) {
                         // --- แก้ไข: เช็คช่องว่างใน inv ---
                        ItemStack currentItemInSlot = inv.getItem(shopItem.getSlot());
                        if (currentItemInSlot == null || currentItemInSlot.getType() == Material.AIR) {
                            targetSlot = shopItem.getSlot();
                        }
                        // --- จบ ---
                    } else {
                        plugin.getLogger().warning("Shop item " + shopItem.getGlobalId() + " defined slot " + shopItem.getSlot() + " is occupied by layout! Trying auto-slot...");
                        // Fall through to auto-slotting
                    }
                }

                // If no valid specific slot, find the next available auto-slot
                if (targetSlot == -1) {
                    while (slotIndex < category.getSize()) {
                        // --- แก้ไข: เช็คช่องว่างใน inv และ layoutSlots ---
                        if (!layoutSlots.contains(slotIndex) && (inv.getItem(slotIndex) == null || inv.getItem(slotIndex).getType() == Material.AIR)) {
                        // --- จบ ---
                            targetSlot = slotIndex;
                            slotIndex++; // Move to next potential slot for the NEXT item
                            break;
                        }
                        slotIndex++;
                    }
                }

                // Place the item if a valid slot was found
                if (targetSlot != -1 && targetSlot < category.getSize()) {
                    ItemStack displayItem = shopItem.buildDisplayItem(player, isBedrock);
                    inv.setItem(targetSlot, displayItem);
                    itemsPlacedThisPage++;
                } else if (targetSlot >= category.getSize()) { // --- เพิ่ม else if ---
                    // No more GUI slots left to check
                    plugin.getLogger().warning("Could not find a free slot for shop item " + shopItem.getGlobalId() + " on page " + page + " in category '" + categoryId + "' (Size: " + category.getSize() + ")");
                    break; // Stop trying for this page
                }
            }
        }


        // 3. Fill REMAINING empty slots
        if (category.getFillItem() != null) {
            ItemStack fill = category.getFillItem().build(player, false);
            for (int i = 0; i < category.getSize(); i++) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, fill);
                }
            }
        }
         // --- END CHANGES for openShopPage ---

        openMenu(player, inv);
    }


    // --- เปลี่ยนชื่อเมธอด openTradeMenu เป็น openQuantitySelectorMenu และแก้ไข GUI ---
    public void openQuantitySelectorMenu(Player player, ShopItem item, boolean isBuying, int currentQuantity) {
        if (item == null) {
            player.closeInventory();
            return;
        }

        // --- แก้ไข: ตรวจสอบจำนวนที่มีในตัว (กรณีขาย) ---
        int playerAmount = getAmountInInventory(player, item.getMaterial());
        // --- จบ ---

        // ทำให้จำนวนไม่ติดลบ
        if (currentQuantity < 0) currentQuantity = 0;

        // --- แก้ไข: ถ้าเป็นการขาย จำกัดจำนวนที่เลือกไม่ให้เกินที่มี ---
        if (!isBuying) {
            if (playerAmount == 0) {
                 plugin.getMessageManager().sendMessage(player, "not_enough_items"); // แจ้งเตือนถ้าไม่มีของเลย
                 openShopPage(player, item.getCategoryId(), 1); // กลับไปหน้าหมวดหมู่
                 return;
            }
            if (currentQuantity > playerAmount) {
                currentQuantity = playerAmount; // ปรับจำนวนให้เท่ากับที่มี
            }
             if (currentQuantity == 0 && playerAmount > 0) {
                 currentQuantity = 1; // ถ้ามีของ แต่ดันเลือก 0 ให้เป็น 1
             }
        }
        // --- จบ ---


        // ดึงราคาปัจจุบัน
        double pricePerItem = isBuying
                ? plugin.getDynamicPriceManager().getBuyPrice(item)
                : plugin.getDynamicPriceManager().getSellPrice(item);
        double totalPrice = pricePerItem * currentQuantity;
        String symbol = plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId());

        String title = ChatColor.translateAlternateColorCodes('&',
                isBuying ? "&aBuy: " + item.getMaterial().name() : "&cSell: " + item.getMaterial().name());
        int guiSize = 45; // 5 แถว

        // สร้าง KJGUIData สำหรับหน้าเลือกจำนวน
        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.QUANTITY_SELECTOR, item.getCategoryId(), 1); // Page ไม่สำคัญ
        Inventory inv = Bukkit.createInventory(guiData, guiSize, title);
        guiData.setInventory(inv);
        guiData.setTradeItem(item); // เก็บไอเทมที่กำลังจะซื้อ/ขาย
        guiData.setIsBuying(isBuying); // เก็บสถานะว่ากำลังซื้อหรือขาย
        guiData.setCartQuantity(currentQuantity); // เก็บจำนวนปัจจุบัน

        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());

        // --- Layout ใหม่ ---

        // เติมพื้นหลัง
        ItemStack fill = new ItemBuilder(mapBedrockMaterial(safeMaterial("GRAY_STAINED_GLASS_PANE", Material.STONE), player))
                .setName(" ")
                .build();
        for (int i = 0; i < guiSize; i++) inv.setItem(i, fill);

        // ไอเทมแสดงผล (ช่อง 4)
        inv.setItem(4, item.buildDisplayItem(player, isBedrock));

        // ปุ่มลดจำนวน (-)
        // ปุ่ม -1 (ช่อง 19)
        if (currentQuantity >= 1) { // แสดงปุ่มต่อเมื่อจำนวน >= 1
            inv.setItem(19, new ItemBuilder(mapBedrockMaterial(safeMaterial("RED_CONCRETE", Material.RED_WOOL), player))
                    .setName("&c-1")
                    .setLore(List.of("&7Click to decrease quantity by 1"))
                    .setPDCData("REMOVE_QUANTITY", "1")
                    .build());
        }
        // ปุ่ม -32 (ช่อง 20)
        if (currentQuantity >= 32) { // แสดงปุ่มต่อเมื่อจำนวน >= 32
             inv.setItem(20, new ItemBuilder(mapBedrockMaterial(safeMaterial("RED_CONCRETE", Material.RED_WOOL), player))
                    .setAmount(32) // Set amount for visual cue
                    .setName("&c-32")
                    .setLore(List.of("&7Click to decrease quantity by 32"))
                    .setPDCData("REMOVE_QUANTITY", "32")
                    .build());
        }
        // ปุ่ม -64 (ช่อง 21)
         if (currentQuantity >= 64) { // แสดงปุ่มต่อเมื่อจำนวน >= 64
             inv.setItem(21, new ItemBuilder(mapBedrockMaterial(safeMaterial("RED_CONCRETE", Material.RED_WOOL), player))
                    .setAmount(64) // Set amount for visual cue
                    .setName("&c-64")
                    .setLore(List.of("&7Click to decrease quantity by 64"))
                    .setPDCData("REMOVE_QUANTITY", "64")
                    .build());
        }


        // ไอเทมแสดงจำนวนและราคา (ช่อง 22)
        inv.setItem(22, new ItemBuilder(Material.PAPER)
                .setName("&eQuantity: &f" + currentQuantity)
                .setLore(Arrays.asList(
                        "&7Price per item: &f" + symbol + PriceUtil.format(pricePerItem),
                        "&r",
                        "&bTotal Price: &6" + symbol + PriceUtil.format(totalPrice),
                        (!isBuying ? "&7You have: &f" + playerAmount : "") // แสดงจำนวนที่มีถ้าเป็นการขาย
                 ))
                .build()); // ไม่มี Action

        // ปุ่มเพิ่มจำนวน (+)
        // ปุ่ม +1 (ช่อง 23)
        inv.setItem(23, new ItemBuilder(mapBedrockMaterial(safeMaterial("LIME_CONCRETE", Material.LIME_WOOL), player))
                .setName("&a+1")
                .setLore(List.of("&7Click to increase quantity by 1"))
                .setPDCData("ADD_QUANTITY", "1")
                .build());
        // ปุ่ม +32 (ช่อง 24)
        inv.setItem(24, new ItemBuilder(mapBedrockMaterial(safeMaterial("LIME_CONCRETE", Material.LIME_WOOL), player))
                .setAmount(32) // Set amount for visual cue
                .setName("&a+32")
                .setLore(List.of("&7Click to increase quantity by 32"))
                .setPDCData("ADD_QUANTITY", "32")
                .build());
        // ปุ่ม +64 (ช่อง 25)
         inv.setItem(25, new ItemBuilder(mapBedrockMaterial(safeMaterial("LIME_CONCRETE", Material.LIME_WOOL), player))
                .setAmount(64) // Set amount for visual cue
                .setName("&a+64")
                .setLore(List.of("&7Click to increase quantity by 64"))
                .setPDCData("ADD_QUANTITY", "64")
                .build());

        // --- เพิ่มปุ่ม Buy/Sell Mode (ถ้าทำได้ทั้งคู่) ---
        if (item.isAllowBuy() && item.isAllowSell()) {
            inv.setItem(38, new ItemBuilder(isBuying ? Material.REDSTONE_TORCH : Material.LEVER)
                    .setName(isBuying ? "&eMode: &aBUYING" : "&eMode: &cSELLING")
                    .setLore(List.of("&7Click to switch to " + (isBuying ? "&cSELLING" : "&aBUYING")))
                    .setPDCAction("SWITCH_MODE")
                    .build());
        }
        
        // ปุ่มยืนยัน (ช่อง 40 - กลาง)
        inv.setItem(40, new ItemBuilder(mapBedrockMaterial(safeMaterial("LIME_WOOL", Material.GREEN_WOOL), player)) // ใช้วัสดุที่ต่างกัน
                .setName(isBuying ? "&a&lConfirm Purchase" : "&a&lConfirm Sale")
                .setLore(List.of(
                        "&7Amount: &f" + currentQuantity,
                        "&7Total: &6" + symbol + PriceUtil.format(totalPrice),
                        "&r",
                        "&aClick to confirm."
                        ))
                .setPDCAction("CONFIRM_TRANSACTION") // Action ใหม่
                .addGlow()
                .build());

        // ปุ่มยกเลิก (ช่อง 44 - ล่างขวา)
        inv.setItem(44, new ItemBuilder(mapBedrockMaterial(safeMaterial("RED_WOOL", Material.RED_WOOL), player))
                .setName("&c&lCancel")
                .setLore(List.of("&7Click to cancel and go back."))
                .setPDCAction("CANCEL_TRANSACTION") // Action ใหม่
                .build());

        openMenu(player, inv);
    }
    // --- จบเมธอด openQuantitySelectorMenu ---


    private void openMenu(Player player, Inventory inv) {
        player.openInventory(inv);
        openMenus.add(player.getUniqueId());
    }

    // Centralized Click Handling Logic
    public void handleClick(Player player, int slot, KJGUIData guiData) {
        ItemStack clickedItem = guiData.getInventory().getItem(slot);
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String action = ItemBuilder.getPDCAction(clickedItem);
        String value = ItemBuilder.getPDCValue(clickedItem); // Can be null (e.g., for SELL_ALL)
        
        // --- DEBUG LOG ---
        System.out.println("[KJShopPlus DEBUG] Click processing: Slot=" + slot
                + " | Item=" + (clickedItem != null ? clickedItem.getType() : "NULL")
                + " | PDC Action=" + action + " | PDC Value=" + value);
        // --- END DEBUG LOG ---


        if (action == null) {
             System.out.println("[KJShopPlus DEBUG] Click ignored: No PDC Action found on item.");
             return;
        }

        ShopItem tradeItem = guiData.getTradeItem(); // Item associated with QUANTITY_SELECTOR GUI
        String categoryId = guiData.getCategoryId(); // Category ID for SHOP_PAGE or QUANTITY_SELECTOR
        int currentPage = guiData.getPage();        // Page number for SHOP_PAGE
        // --- ดึงข้อมูลจาก KJGUIData ---
        int currentQuantity = guiData.getCartQuantity();
        boolean isBuying = guiData.isBuying();
        // --- จบ ---

        switch (action) {
            case "OPEN_CATEGORY":
                if (value != null && value.equals("main")) {
                    openCategoryMenu(player);
                } else if (value != null) {
                    // Could be from main menu (value=category ID) or trade menu (value=category ID)
                    openShopPage(player, value, 1);
                }
                break;
            case "TRADE_ITEM": // Clicked on an item in SHOP_PAGE
                if (value != null) { // Value should be the globalId (category:item)
                    ShopItem itemToTrade = plugin.getShopManager().getShopItem(value);
                    if (itemToTrade != null) {
                        // --- เปิดหน้าเลือกจำนวนแทน ---
                        int startQuantity = 1;
                        boolean startBuying = itemToTrade.isAllowBuy(); 
                        
                        if (!startBuying && itemToTrade.isAllowSell()){
                            startBuying = false; 
                            // --- แก้ไข: กลับไปใช้ Material ---
                             startQuantity = getAmountInInventory(player, itemToTrade.getMaterial()); 
                             if(startQuantity == 0) {
                                 // ถ้าจะขายแต่ไม่มีของเลย ก็เด้งกลับ
                                 plugin.getMessageManager().sendMessage(player, "not_enough_items");
                                 return; 
                             }
                             // ให้เริ่มที่ 1 ชิ้นก่อน แม้จะมีเยอะ
                             startQuantity = 1; 
                        } else if (!itemToTrade.isAllowBuy() && !itemToTrade.isAllowSell()) {
                             return; // ถ้าของชิ้นนี้ห้ามซื้อและห้ามขาย ก็ไม่ต้องทำอะไร
                        }
                        
                        openQuantitySelectorMenu(player, itemToTrade, startBuying, startQuantity);
                        // --- จบ ---
                    } else {
                        plugin.getLogger().warning("TRADE_ITEM action received invalid globalId: " + value);
                        player.closeInventory();
                    }
                }
                break;
            case "PAGE_NEXT":
                if (categoryId != null && guiData.getGuiType() == GUITYPE.SHOP_PAGE) {
                    openShopPage(player, categoryId, currentPage + 1);
                }
                break;
            case "PAGE_PREV":
                if (categoryId != null && guiData.getGuiType() == GUITYPE.SHOP_PAGE) {
                    openShopPage(player, categoryId, currentPage - 1);
                }
                break;

             // --- ลบ case BUY, SELL, SELL_ALL ---

            // --- เคสใหม่สำหรับปุ่ม +/- ---
            case "ADD_QUANTITY":
                if (tradeItem != null && value != null) {
                    try {
                        int amountToAdd = Integer.parseInt(value);
                        int newQuantity = currentQuantity + amountToAdd;
                        
                        // --- แก้ไข: ถ้าขาย ห้ามบวกเกินจำนวนที่มี ---
                        if(!isBuying) {
                            int playerAmount = getAmountInInventory(player, tradeItem.getMaterial());
                            if (newQuantity > playerAmount) {
                                newQuantity = playerAmount;
                            }
                        }
                        // --- จบ ---

                        openQuantitySelectorMenu(player, tradeItem, isBuying, newQuantity);
                    } catch (NumberFormatException e) {
                         plugin.getLogger().warning("Invalid ADD_QUANTITY value: " + value);
                    }
                }
                break;
            case "REMOVE_QUANTITY":
                if (tradeItem != null && value != null) {
                    try {
                        int amountToRemove = Integer.parseInt(value);
                        int newQuantity = currentQuantity - amountToRemove;
                        if (newQuantity < 0) newQuantity = 0; // กันติดลบ
                        openQuantitySelectorMenu(player, tradeItem, isBuying, newQuantity);
                    } catch (NumberFormatException e) {
                         plugin.getLogger().warning("Invalid REMOVE_QUANTITY value: " + value);
                    }
                }
                break;
            // --- จบเคส +/- ---

            // --- เคสใหม่สำหรับปุ่มสลับโหมด ---
            case "SWITCH_MODE":
                if (tradeItem != null) {
                    boolean newIsBuying = !isBuying; // สลับโหมด
                    int newQuantity = 1; // กลับไปเริ่มที่ 1
                    if (!newIsBuying) { // ถ้าเพิ่งสลับไปโหมดขาย
                        // --- แก้ไข: กลับไปใช้ Material ---
                        newQuantity = getAmountInInventory(player, tradeItem.getMaterial());
                        if (newQuantity == 0) {
                            plugin.getMessageManager().sendMessage(player, "not_enough_items");
                            // ไม่เปลี่ยนโหมดถ้าไม่มีของขาย
                            // openQuantitySelectorMenu(player, tradeItem, isBuying, currentQuantity); // <-- รีเฟรชหน้าเดิม
                            return; 
                        }
                         // ให้เริ่มที่ 1 ชิ้นก่อน แม้จะมีเยอะ
                         newQuantity = 1;
                    }
                    openQuantitySelectorMenu(player, tradeItem, newIsBuying, newQuantity);
                }
                break;
            // --- จบ ---

            // --- เคสใหม่สำหรับปุ่มยืนยัน / ยกเลิก ---
            case "CONFIRM_TRANSACTION":
                 if (tradeItem != null && currentQuantity > 0) {
                     performTransaction(player, tradeItem, currentQuantity, isBuying);
                     // ทำเสร็จแล้ว กลับไปหน้าหมวดหมู่เดิม
                     openShopPage(player, categoryId, 1); // อาจจะต้องจำ page เดิมไว้? ตอนนี้กลับไปหน้า 1 ก่อน
                 } else if (tradeItem != null && currentQuantity <= 0){
                     // ถ้ากด Confirm ตอนจำนวนเป็น 0 ก็แค่กลับไปหน้าหมวดหมู่
                     openShopPage(player, categoryId, 1);
                 }
                 break;
            case "CANCEL_TRANSACTION":
                // กลับไปหน้าหมวดหมู่
                openShopPage(player, categoryId, 1);
                break;
            // --- จบ ---

            case "CLOSE":
                player.closeInventory();
                break;
            default:
                 plugin.getLogger().warning("Unknown GUI action clicked: " + action);
                 break;
        }
    }

    // --- ลบ performSellAllTransaction ---

    private void performTransaction(Player player, ShopItem item, int amount, boolean isBuy) {
        if (item == null || amount <= 0) return;

        double pricePerItem;
        if (isBuy) {
            if (!item.isAllowBuy()) return; // Double check if allowed
            pricePerItem = plugin.getDynamicPriceManager().getBuyPrice(item);
        } else {
            if (!item.isAllowSell()) return; // Double check if allowed
            pricePerItem = plugin.getDynamicPriceManager().getSellPrice(item);
        }
        double totalPrice = pricePerItem * amount;

        // Placeholders for messages
        // --- แก้ไข: กลับไปใช้ Material.name() หรือ DisplayName ---
        String itemName = (item.getDisplayName() != null && !item.getDisplayName().isBlank() && !item.getDisplayName().equals(" "))
                ? ChatColor.translateAlternateColorCodes('&', item.getDisplayName()) // ใช้ชื่อที่ตั้ง ถ้ามีและไม่ใช่แค่ช่องว่าง
                : item.getMaterial().name();
        Map<String, String> placeholders = Map.of(
            "amount", String.valueOf(amount),
            "item", ChatColor.stripColor(itemName), // เอาสีออกก่อนส่ง message
            "price", PriceUtil.format(totalPrice),
            "currency_symbol", plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId())
        );
        // --- จบ ---


        if (isBuy) {
            // Check balance
            if (!plugin.getCurrencyService().hasBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getMessageManager().sendMessage(player, "not_enough_money", placeholders);
                return;
            }
            // Check inventory space
            int maxStack = item.getMaterial().getMaxStackSize();
            int neededSlots = (int) Math.ceil((double) amount / maxStack);
            // --- แก้ไข: กลับไปใช้ Material ---
            if (getEmptySlots(player) < neededSlots && getPartialStackSpace(player, item.getMaterial(), amount) < amount) {
                 plugin.getMessageManager().sendMessage(player, "inventory_full", placeholders);
                 return;
            }
            // --- จบ ---


            // Attempt transaction
            if (!plugin.getCurrencyService().removeBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getMessageManager().sendMessage(player, "not_enough_money", placeholders);
                return;
            }

            // --- แก้ไข: ให้ไอเทมแบบ Vanilla ---
            player.getInventory().addItem(new ItemStack(item.getMaterial(), amount));
            // --- จบ ---

            // Record for dynamic pricing
            plugin.getDynamicPriceManager().recordBuy(item, amount);

            // Send success message
            plugin.getMessageManager().sendMessage(player, "buy_success", placeholders);

            // Log to Discord
            plugin.getDiscordWebhookService().logBuy(player, item, amount, totalPrice);

        } else { // Selling
            // Check if player has enough items (amount should already be capped)
            // --- แก้ไข: กลับไปใช้ Material ---
            int playerAmount = getAmountInInventory(player, item.getMaterial());
            if (playerAmount < amount) { // เช็คอีกรอบ เผื่อมีอะไรผิดพลาด
                plugin.getMessageManager().sendMessage(player, "not_enough_items", placeholders);
                return;
            }
            // --- จบ ---

            // Take items
            // --- แก้ไข: ลบไอเทมแบบ Vanilla ---
            player.getInventory().removeItem(new ItemStack(item.getMaterial(), amount));
            // --- จบ ---

            // Attempt to give money
            if (!plugin.getCurrencyService().addBalance(player, item.getCurrencyId(), totalPrice)) {
                // Critical failure: Return items
                plugin.getLogger().severe("CRITICAL: Failed to add balance for " + player.getName() + " after removing items! Returning items.");
                // --- แก้ไข: คืนไอเทมแบบ Vanilla ---
                player.getInventory().addItem(new ItemStack(item.getMaterial(), amount)); // Give items back
                // --- จบ ---
                plugin.getMessageManager().sendMessage(player, "error_occurred"); // Inform player
                return;
            }

            // Record for dynamic pricing
            plugin.getDynamicPriceManager().recordSell(item, amount);

            // Send success message
            plugin.getMessageManager().sendMessage(player, "sell_success", placeholders);

            // Log to Discord
            plugin.getDiscordWebhookService().logSell(player, item, amount, totalPrice);
        }
    }
     // Helper to check space in partially filled stacks
    // --- แก้ไข: กลับไปใช้ Material ---
    private int getPartialStackSpace(Player player, Material material, int amountNeeded) {
        int space = 0;
        int maxStack = material.getMaxStackSize();
        for (ItemStack item : player.getInventory().getStorageContents()) { // Use getStorageContents to exclude armor/offhand
            if (item != null && item.getType() == material && item.getAmount() < maxStack) { // <--- เช็คแค่ Material
                space += maxStack - item.getAmount();
                if (space >= amountNeeded) {
                    return amountNeeded; // Found enough space
                }
            }
        }
        return space;
    }
    // --- จบ ---


    // Counts items in player's main inventory slots (0-35)
    // --- แก้ไข: กลับไปใช้ Material ---
    private int getAmountInInventory(Player player, Material material) {
        int amount = 0;
        ItemStack[] contents = player.getInventory().getStorageContents(); // Includes hotbar + main inv
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) { // <--- เช็คแค่ Material
                amount += item.getAmount();
            }
        }
        return amount;
    }
    // --- จบ ---

    // Counts empty slots in player's main inventory (0-35)
    private int getEmptySlots(Player player) {
        int emptySlots = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    // Calculates how many item slots are available, excluding layout slots
    private int calculateItemsPerPage(int guiSize) {
        if (guiSize <= 9) return 0;
        if (guiSize == 18) return 9;
        if (guiSize == 27) return 18;
        if (guiSize == 36) return 27;
        if (guiSize == 45) return 36;
        if (guiSize == 54) return 45; // Max size typically leaves last row for controls
        return Math.max(0, guiSize - 9); // Default guess: subtract bottom row
    }

    // Safely gets a Material, returning fallback if invalid
    private Material safeMaterial(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material name in GUI config: " + name);
            return fallback;
        }
    }

    // Maps Material for Bedrock players if compatibility is enabled
    public Material mapBedrockMaterial(Material javaMaterial, Player player) {
        if (javaMaterial == null) return Material.STONE; // Fallback for null input
        // Check if player UUID is actually a bedrock player AND if compat is enabled
        if (plugin.isBedrockPlayer(player.getUniqueId()) && plugin.getConfigManager().isBedrockCompatEnabled()) {
            return plugin.getConfigManager().getBedrockMappedMaterial(javaMaterial);
        }
        return javaMaterial; // Return original if Java player or compat disabled
    }

    // Called by GUIListener when a KJShopPlus GUI is closed
    public void onMenuClose(Player player) {
        openMenus.remove(player.getUniqueId());
        plugin.getPlayerTapManager().clear(player.getUniqueId()); // Clear debounce timer on close
    }

    // Closes all currently open KJShopPlus GUIs (e.g., on reload/disable)
    public void closeAllMenus() {
        // Use an iterator to safely remove while iterating
        Iterator<UUID> iterator = openMenus.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Player player = Bukkit.getPlayer(uuid); // Get player associated with the UUID
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof KJGUIData) {
                // Check if they still have our GUI open before closing
                player.closeInventory();
            }
            iterator.remove(); // Remove UUID from the set
        }
         // Clear the set completely just in case
        openMenus.clear();
    }
}

