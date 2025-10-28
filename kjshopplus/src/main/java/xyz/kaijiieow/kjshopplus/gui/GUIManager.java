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

    public enum GUITYPE {
        CATEGORY_MAIN, SHOP_PAGE,
        QUANTITY_SELECTOR // หน้าเลือกจำนวน
    }

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
            for (int i = startIndex; i < items.size() && itemsPlacedThisPage < itemsPerPage; i++) {
                ShopItem shopItem = items.get(i);
                int targetSlot = -1;

                // Check if a specific slot is defined and VALID and NOT occupied by layout
                if (shopItem.getSlot() != -1 && shopItem.getSlot() < category.getSize()) {
                    if (!layoutSlots.contains(shopItem.getSlot())) {
                        targetSlot = shopItem.getSlot();
                    } else {
                        plugin.getLogger().warning("Shop item " + shopItem.getGlobalId() + " defined slot " + shopItem.getSlot() + " is occupied by layout! Trying auto-slot...");
                        // Fall through to auto-slotting
                    }
                }

                // If no valid specific slot, find the next available auto-slot
                if (targetSlot == -1) {
                    while (slotIndex < category.getSize()) {
                        if (!layoutSlots.contains(slotIndex) && (inv.getItem(slotIndex) == null || inv.getItem(slotIndex).getType() == Material.AIR)) {
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
                } else {
                    plugin.getLogger().warning("Could not find a free slot for shop item " + shopItem.getGlobalId() + " on page " + page + " in category '" + categoryId + "' (Size: " + category.getSize() + ")");
                    // If even auto-slotting fails, stop trying for this page
                    break;
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


    // --- MODIFIED: openQuantitySelector (ระบบตะกร้า) ---
    /**
     * Opens the GUI for selecting item quantity.
     * @param player The player
     * @param item The item to trade
     * @param currentAmount The current amount in the "cart"
     * @param isBuy True if buying, false if selling
     * @param previousPage The shop page number the player came from
     */
    public void openQuantitySelector(Player player, ShopItem item, int currentAmount, boolean isBuy, int previousPage) {
        if (item == null) {
            player.closeInventory();
            return;
        }

        String mode = isBuy ? "&a&lBUYING" : "&c&lSELLING";
        String title = ChatColor.translateAlternateColorCodes('&', mode + " " + item.getMaterial().name());
        int guiSize = 45; // 5 rows

        // Store all necessary data in the KJGUIData
        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.QUANTITY_SELECTOR, item.getCategoryId(), previousPage);
        guiData.setTradeItem(item);
        guiData.setCurrentAmount(currentAmount);
        guiData.setBuyMode(isBuy);

        Inventory inv = Bukkit.createInventory(guiData, guiSize, title);
        guiData.setInventory(inv);

        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());
        Material fillMat = mapBedrockMaterial(Material.GRAY_STAINED_GLASS_PANE, player);
        ItemStack fill = new ItemBuilder(fillMat).setName(" ").build();
        for (int i = 0; i < guiSize; i++) inv.setItem(i, fill);

        // Prices and Currency
        double buyPrice = plugin.getDynamicPriceManager().getBuyPrice(item);
        double sellPrice = plugin.getDynamicPriceManager().getSellPrice(item);
        double pricePerItem = isBuy ? buyPrice : sellPrice;
        double totalPrice = pricePerItem * currentAmount;
        String symbol = plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId());

        // --- Row 0: Item Display ---
        inv.setItem(4, item.buildDisplayItem(player, isBedrock));

        // --- Row 1: Mode Display ---
        Material modeMat = isBuy ? mapBedrockMaterial(Material.LIME_STAINED_GLASS_PANE, player) : mapBedrockMaterial(Material.RED_STAINED_GLASS_PANE, player);
        inv.setItem(13, new ItemBuilder(modeMat).setName(mode).build()); // Slot 13 (Row 1, Middle)

        // --- Row 2: Controls & Info ---
        // Decrease Buttons
        if (currentAmount >= 1) {
            inv.setItem(21, new ItemBuilder(Material.RED_WOOL).setName("&c-1").setAmount(1).setPDCAction("ADJUST_QUANTITY").setPDCValue("-1").build());
        }
        if (currentAmount >= 32) {
            inv.setItem(20, new ItemBuilder(Material.RED_WOOL).setName("&c-32").setAmount(32).setPDCAction("ADJUST_QUANTITY").setPDCValue("-32").build());
        }
        if (currentAmount >= 64) {
            inv.setItem(19, new ItemBuilder(Material.RED_WOOL).setName("&c-64").setAmount(64).setPDCAction("ADJUST_QUANTITY").setPDCValue("-64").build());
        }

        // Info Item (Slot 22)
        // (ใช้ Java 8 Map creation)
        Map<String, String> pricePlaceholders = new HashMap<>();
        pricePlaceholders.put("price", PriceUtil.format(totalPrice));
        pricePlaceholders.put("symbol", symbol);

        ItemStack infoItem = new ItemBuilder(item.getMaterial())
                .setName(isBuy ? "&aConfirm Buy" : "&cConfirm Sell")
                .setAmount(Math.max(1, Math.min(currentAmount, item.getMaterial().getMaxStackSize()))) // Clamp amount 1-maxStack
                .setLore(Arrays.asList(
                        "&7Amount: &e" + currentAmount,
                        plugin.getMessageManager().getMessage("gui_total_price", pricePlaceholders)
                ))
                .build();
        inv.setItem(22, infoItem);

        // Increase Buttons
        inv.setItem(23, new ItemBuilder(Material.GREEN_WOOL).setName("&a+1").setAmount(1).setPDCAction("ADJUST_QUANTITY").setPDCValue("1").build());
        inv.setItem(24, new ItemBuilder(Material.GREEN_WOOL).setName("&a+32").setAmount(32).setPDCAction("ADJUST_QUANTITY").setPDCValue("32").build());
        inv.setItem(25, new ItemBuilder(Material.GREEN_WOOL).setName("&a+64").setAmount(64).setPDCAction("ADJUST_QUANTITY").setPDCValue("64").build());


        // --- Row 3: Sell All (NEW) ---
        if (!isBuy) {
            int playerAmount = getAmountInInventory(player, item.getMaterial());
            double totalSellAllPrice = sellPrice * playerAmount;
            
            // (ใช้ Java 8 Map creation)
            Map<String, String> sellAllAmountMap = new HashMap<>();
            sellAllAmountMap.put("amount", String.valueOf(playerAmount));
            
            Map<String, String> sellAllPriceMap = new HashMap<>();
            sellAllPriceMap.put("price", PriceUtil.format(totalSellAllPrice));
            sellAllPriceMap.put("symbol", symbol);

            ItemStack sellAllItem = new ItemBuilder(mapBedrockMaterial(Material.HOPPER, player))
                    .setName(plugin.getMessageManager().getMessage("gui_sell_all_button", sellAllAmountMap))
                    .setLore(Arrays.asList(
                            plugin.getMessageManager().getMessage("gui_total_price", sellAllPriceMap)
                    ))
                    .setPDCAction("SELL_ALL_CART") // New action
                    .build();
            inv.setItem(31, sellAllItem); // Slot 31 (middle of 4th row)
        }


        // --- Row 4: Confirm/Cancel ---
        // Slot 39 & 41 (ห่างกัน 1 ช่อง)
        inv.setItem(39, new ItemBuilder(Material.BARRIER).setName("&cCancel Transaction").setPDCAction("CANCEL_TRANSACTION").build());
        inv.setItem(41, new ItemBuilder(Material.LIME_CONCRETE).setName("&aConfirm Transaction").setPDCAction("CONFIRM_TRANSACTION").build());


        openMenu(player, inv);
    }
    // --- END MODIFIED METHOD ---


    private void openMenu(Player player, Inventory inv) {
        player.openInventory(inv);
        openMenus.add(player.getUniqueId());
    }

    // Centralized Click Handling Logic
    public void handleClick(Player player, int slot, KJGUIData guiData) {
        ItemStack clickedItem = guiData.getInventory().getItem(slot);
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String action = ItemBuilder.getPDCAction(clickedItem);
        String value = ItemBuilder.getPDCValue(clickedItem);
        
        System.out.println("[KJShopPlus DEBUG] Click processing: Slot=" + slot
                + " | Item=" + (clickedItem != null ? clickedItem.getType() : "NULL")
                + " | PDC Action=" + action + " | PDC Value=" + value);


        if (action == null) {
             System.out.println("[KJShopPlus DEBUG] Click ignored: No PDC Action found on item.");
             return;
        }

        ShopItem tradeItem = guiData.getTradeItem();
        String categoryId = guiData.getCategoryId();
        int currentPage = guiData.getPage();
        // --- ADDED ---
        boolean isBuy = guiData.isBuyMode();
        int currentAmount = guiData.getCurrentAmount();
        // --- END ---

        switch (action) {
            case "OPEN_CATEGORY":
                if (value != null && value.equals("main")) {
                    openCategoryMenu(player);
                } else if (value != null) {
                    openShopPage(player, value, 1);
                }
                break;
            
            case "TRADE_ITEM": // Clicked on an item in SHOP_PAGE
                if (value != null) {
                    ShopItem itemToTrade = plugin.getShopManager().getShopItem(value);
                    if (itemToTrade != null) {
                        // --- FIX: Pass the current page number (guiData.getPage()) ---
                        if (player.isSneaking()) { // Use shift-click for sell
                             if (itemToTrade.isAllowSell()) {
                                // เปิดหน้าเลือกจำนวน (Sell) โดยเริ่มที่ 1 ชิ้น
                                openQuantitySelector(player, itemToTrade, 1, false, guiData.getPage());
                             }
                        } else { // Normal click for buy
                             if (itemToTrade.isAllowBuy()) {
                                // เปิดหน้าเลือกจำนวน (Buy) โดยเริ่มที่ 1 ชิ้น
                                openQuantitySelector(player, itemToTrade, 1, true, guiData.getPage());
                             }
                        }
                    } else {
                        plugin.getLogger().warning("TRADE_ITEM action received invalid globalId: " + value);
                        player.closeInventory();
                    }
                }
                break;
            
            case "PAGE_NEXT":
                if (categoryId != null && guiData.getGuiType() == GUIManager.GUITYPE.SHOP_PAGE) {
                    openShopPage(player, categoryId, currentPage + 1);
                }
                break;
            
            case "PAGE_PREV":
                if (categoryId != null && guiData.getGuiType() == GUIManager.GUITYPE.SHOP_PAGE) {
                    openShopPage(player, categoryId, currentPage - 1);
                }
                break;
            
            // --- NEW CASES FOR QUANTITY SELECTOR ---
            case "ADJUST_QUANTITY":
                if (tradeItem != null && value != null && guiData.getGuiType() == GUIManager.GUITYPE.QUANTITY_SELECTOR) {
                    try {
                        int amountChange = Integer.parseInt(value);
                        int newAmount = Math.max(0, currentAmount + amountChange); // Don't go below 0
                        
                        // If selling, check against max player has
                        if (!isBuy) {
                            int playerAmount = getAmountInInventory(player, tradeItem.getMaterial());
                            newAmount = Math.min(newAmount, playerAmount); // Don't go above what player has
                        }
                        
                        // Re-open the GUI with the new amount, pass previous page (currentPage)
                        openQuantitySelector(player, tradeItem, newAmount, isBuy, currentPage);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid ADJUST_QUANTITY value: " + value);
                    }
                }
                break;

            case "CONFIRM_TRANSACTION":
                if (tradeItem != null && guiData.getGuiType() == GUIManager.GUITYPE.QUANTITY_SELECTOR && currentAmount > 0) {
                    performTransaction(player, tradeItem, currentAmount, isBuy);
                    // Go back to the shop page, using the stored page number
                    openShopPage(player, tradeItem.getCategoryId(), currentPage); // currentPage is previousPage
                }
                break;

            case "CANCEL_TRANSACTION":
                if (tradeItem != null && guiData.getGuiType() == GUIManager.GUITYPE.QUANTITY_SELECTOR) {
                    // Go back to the shop page, using the stored page number
                    openShopPage(player, tradeItem.getCategoryId(), currentPage); // currentPage is previousPage
                }
                break;

            // --- NEW CASE FOR SELL ALL IN CART ---
            case "SELL_ALL_CART":
                if (tradeItem != null && !isBuy && guiData.getGuiType() == GUIManager.GUITYPE.QUANTITY_SELECTOR) {
                    int playerAmount = getAmountInInventory(player, tradeItem.getMaterial());
                    if (playerAmount <= 0) {
                        plugin.getMessageManager().sendMessage(player, "not_enough_items");
                        // Re-open selector, still at 0, passing the previous page
                        openQuantitySelector(player, tradeItem, 0, false, currentPage);
                    } else {
                        performTransaction(player, tradeItem, playerAmount, false);
                        // Sell all done, go back to shop page
                        openShopPage(player, tradeItem.getCategoryId(), currentPage);
                    }
                }
                break;
            // --- END NEW CASES ---

            case "CLOSE":
                player.closeInventory();
                break;
            
            default:
                 plugin.getLogger().warning("Unknown GUI action clicked: " + action);
                 break;
        }
    }

    private void performSellAllTransaction(Player player, ShopItem item) {
        // This method is no longer called by SELL_ALL button, but keep for legacy?
        // Or refactor: SELL_ALL_CART logic is now the main way
        if (item == null || !item.isAllowSell()) return;
        int amount = getAmountInInventory(player, item.getMaterial());

        if (amount <= 0) {
            plugin.getMessageManager().sendMessage(player, "not_enough_items");
            return;
        }

        performTransaction(player, item, amount, false);
    }

    private void performTransaction(Player player, ShopItem item, int amount, boolean isBuy) {
        if (item == null || amount <= 0) return;

        double pricePerItem;
        if (isBuy) {
            if (!item.isAllowBuy()) return;
            pricePerItem = plugin.getDynamicPriceManager().getBuyPrice(item);
        } else {
            if (!item.isAllowSell()) return;
            pricePerItem = plugin.getDynamicPriceManager().getSellPrice(item);
        }
        double totalPrice = pricePerItem * amount;

        // --- FIX for Java 8 ---
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("item", item.getMaterial().name());
        placeholders.put("price", PriceUtil.format(totalPrice));
        placeholders.put("currency_symbol", plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId()));
        // --- END FIX ---


        if (isBuy) {
            if (!plugin.getCurrencyService().hasBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getMessageManager().sendMessage(player, "not_enough_money", placeholders);
                return;
            }
            int maxStack = item.getMaterial().getMaxStackSize();
            int neededSlots = (int) Math.ceil((double) amount / maxStack);
            if (getEmptySlots(player) < neededSlots && getPartialStackSpace(player, item.getMaterial(), amount) < amount) {
                 plugin.getMessageManager().sendMessage(player, "inventory_full", placeholders);
                 return;
            }

            if (!plugin.getCurrencyService().removeBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getMessageManager().sendMessage(player, "not_enough_money", placeholders);
                return;
            }

            player.getInventory().addItem(new ItemStack(item.getMaterial(), amount));
            plugin.getDynamicPriceManager().recordBuy(item, amount);
            plugin.getMessageManager().sendMessage(player, "buy_success", placeholders);
            plugin.getDiscordWebhookService().logBuy(player, item, amount, totalPrice);

        } else { // Selling
            
            // We already checked the amount in handleClick (SELL and SELL_ALL_CART)
            // So 'amount' here is guaranteed to be <= player's total amount
            if (!player.getInventory().contains(item.getMaterial(), amount)) {
                // This check is now redundant due to logic in handleClick, but keep as fallback
                plugin.getMessageManager().sendMessage(player, "not_enough_items", placeholders);
                return;
            }

            player.getInventory().removeItem(new ItemStack(item.getMaterial(), amount));
            if (!plugin.getCurrencyService().addBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getLogger().severe("CRITICAL: Failed to add balance for " + player.getName() + " after removing items! Returning items.");
                player.getInventory().addItem(new ItemStack(item.getMaterial(), amount)); // Give items back
                plugin.getMessageManager().sendMessage(player, "error_occurred");
                return;
            }
            plugin.getDynamicPriceManager().recordSell(item, amount);
            plugin.getMessageManager().sendMessage(player, "sell_success", placeholders);
            plugin.getDiscordWebhookService().logSell(player, item, amount, totalPrice);
        }
    }
     // Helper to check space in partially filled stacks
    private int getPartialStackSpace(Player player, Material material, int amountNeeded) {
        int space = 0;
        int maxStack = material.getMaxStackSize();
        for (ItemStack item : player.getInventory().getStorageContents()) { // Use getStorageContents to exclude armor/offhand
            if (item != null && item.getType() == material && item.getAmount() < maxStack) {
                space += maxStack - item.getAmount();
                if (space >= amountNeeded) {
                    return amountNeeded; // Found enough space
                }
            }
        }
        return space;
    }


    // Counts items in player's main inventory slots (0-35)
    private int getAmountInInventory(Player player, Material material) {
        int amount = 0;
        ItemStack[] contents = player.getInventory().getStorageContents(); // Includes hotbar + main inv
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

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
        Iterator<UUID> iterator = openMenus.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                 if (player.getOpenInventory().getTopInventory().getHolder() instanceof KJGUIData) {
                    player.closeInventory();
                 }
            }
            iterator.remove();
        }
    }
}

