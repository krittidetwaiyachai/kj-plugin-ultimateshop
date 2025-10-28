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
        CATEGORY_MAIN, SHOP_PAGE, TRADE_CONFIRM
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


    public void openTradeMenu(Player player, ShopItem item) {
        if (item == null) {
            player.closeInventory(); // Close just in case
            return;
        }

        String title = ChatColor.translateAlternateColorCodes('&', "&8Trade: " + item.getMaterial().name());
        int guiSize = 45; // 5 rows

        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.TRADE_CONFIRM, item.getCategoryId(), 1); // Page doesn't matter here
        Inventory inv = Bukkit.createInventory(guiData, guiSize, title);
        guiData.setInventory(inv);
        guiData.setTradeItem(item); // Store the item being traded

        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());

        // Fill background
        ItemStack fill = new ItemBuilder(mapBedrockMaterial(safeMaterial("GRAY_STAINED_GLASS_PANE", Material.STONE), player))
                .setName(" ")
                .build();
        for (int i = 0; i < guiSize; i++) inv.setItem(i, fill);

        // Item Display (Center Top)
        inv.setItem(4, item.buildDisplayItem(player, isBedrock));

        // Go Back Button (Bottom Middle)
        inv.setItem(40, new ItemBuilder(Material.ARROW) // Use Arrow, Barrier might be confusing
            .setName("&aGo Back")
            .setPDCAction("OPEN_CATEGORY") // Action to re-open the previous category page
            .setPDCValue(item.getCategoryId()) // Value is the category ID
            .build());

        // Prices and Currency
        double buyPrice = plugin.getDynamicPriceManager().getBuyPrice(item);
        double sellPrice = plugin.getDynamicPriceManager().getSellPrice(item);
        String symbol = plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId());

        // Materials for Buy/Sell buttons (Bedrock compatible)
        Material buyMat = mapBedrockMaterial(safeMaterial("LIME_CONCRETE", Material.LIME_WOOL), player); // Concrete preferred
        Material sellMat = mapBedrockMaterial(safeMaterial("RED_CONCRETE", Material.RED_WOOL), player); // Concrete preferred

        // Buy Buttons (Row 2: slots 10-14)
        if (item.isAllowBuy()) {
            inv.setItem(10, new ItemBuilder(buyMat).setAmount(1).setName("&a&lBuy 1").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice))).setPDCData("BUY", "1").build());
            inv.setItem(11, new ItemBuilder(buyMat).setAmount(8).setName("&a&lBuy 8").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice * 8))).setPDCData("BUY", "8").build());
            inv.setItem(12, new ItemBuilder(buyMat).setAmount(16).setName("&a&lBuy 16").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice * 16))).setPDCData("BUY", "16").build());
            inv.setItem(13, new ItemBuilder(buyMat).setAmount(32).setName("&a&lBuy 32").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice * 32))).setPDCData("BUY", "32").build());
            inv.setItem(14, new ItemBuilder(buyMat).setAmount(64).setName("&a&lBuy 64").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice * 64))).setPDCData("BUY", "64").build());
        }

        // Sell Buttons (Row 3: slots 19-23)
        if (item.isAllowSell()) {
            inv.setItem(19, new ItemBuilder(sellMat).setAmount(1).setName("&c&lSell 1").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice))).setPDCData("SELL", "1").build());
            inv.setItem(20, new ItemBuilder(sellMat).setAmount(8).setName("&c&lSell 8").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * 8))).setPDCData("SELL", "8").build());
            inv.setItem(21, new ItemBuilder(sellMat).setAmount(16).setName("&c&lSell 16").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * 16))).setPDCData("SELL", "16").build());
            inv.setItem(22, new ItemBuilder(sellMat).setAmount(32).setName("&c&lSell 32").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * 32))).setPDCData("SELL", "32").build());
            inv.setItem(23, new ItemBuilder(sellMat).setAmount(64).setName("&c&lSell 64").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * 64))).setPDCData("SELL", "64").build());

            // Sell All Button (Row 3, slot 25)
            int sellAllAmount = getAmountInInventory(player, item.getMaterial());
            inv.setItem(25, new ItemBuilder(mapBedrockMaterial(Material.HOPPER, player)) // Hopper is intuitive for selling all
                .setName("&c&lSell All (" + sellAllAmount + ")")
                .setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * sellAllAmount)))
                .setPDCAction("SELL_ALL")
                // No value needed for SELL_ALL, we calculate amount dynamically
                .build());
        }

        openMenu(player, inv);
    }

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

        ShopItem tradeItem = guiData.getTradeItem(); // Item associated with TRADE_CONFIRM GUI
        String categoryId = guiData.getCategoryId(); // Category ID for SHOP_PAGE or TRADE_CONFIRM
        int currentPage = guiData.getPage();        // Page number for SHOP_PAGE

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
                        openTradeMenu(player, itemToTrade);
                    } else {
                        plugin.getLogger().warning("TRADE_ITEM action received invalid globalId: " + value);
                        player.closeInventory(); // Close GUI if item is invalid
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
            case "BUY":
                if (tradeItem != null && value != null) { // Ensure we are in trade menu and have amount
                    try {
                        int amount = Integer.parseInt(value);
                        performTransaction(player, tradeItem, amount, true);
                        // Re-open trade menu to show updated Sell All amount
                        openTradeMenu(player, tradeItem);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid BUY amount: " + value);
                    }
                }
                break;
            
            // --- MODIFIED SELL CASE ---
            case "SELL":
                 // Check if tradeItem and value are NOT null
                 if (tradeItem != null && value != null) {
                    try {
                        int requestedAmount = Integer.parseInt(value); // This is the amount the button requested (e.g., 16)
                        if (requestedAmount <= 0) return; // Don't sell 0 or negative

                        // --- NEW LOGIC: Check how many items the player actually has ---
                        int playerAmount = getAmountInInventory(player, tradeItem.getMaterial()); // Find out the real amount (e.g., 8)
                        
                        // Determine the actual amount to sell:
                        // It's the smaller value between what the button requested (e.g., 16)
                        // and what the player actually has (e.g., 8)
                        int amountToSell = Math.min(requestedAmount, playerAmount); // This will be 8

                        if (amountToSell <= 0) {
                            // Player clicked "Sell 16" but has 0 items
                            plugin.getMessageManager().sendMessage(player, "not_enough_items");
                            openTradeMenu(player, tradeItem); // Re-open to refresh Sell All button
                            return; // Stop here
                        }
                        
                        // Perform the transaction with the *actual* amount they have (e.g., 8)
                        performTransaction(player, tradeItem, amountToSell, false);
                        // --- END NEW LOGIC ---

                         // Re-open trade menu to show updated Sell All amount
                        openTradeMenu(player, tradeItem);
                    } catch (NumberFormatException e) {
                         plugin.getLogger().warning("Invalid SELL amount: " + value);
                    }
                }
                break;
            // --- END MODIFIED SELL CASE ---

            case "SELL_ALL":
                if (tradeItem != null) { // Ensure we are in trade menu
                    performSellAllTransaction(player, tradeItem);
                     // Re-open trade menu to show updated Sell All amount (should be 0 now)
                    openTradeMenu(player, tradeItem);
                }
                break;
            case "CLOSE":
                player.closeInventory();
                break;
            default:
                 // Unknown action, maybe log it?
                 plugin.getLogger().warning("Unknown GUI action clicked: " + action);
                 break;
        }
    }

    private void performSellAllTransaction(Player player, ShopItem item) {
        if (item == null || !item.isAllowSell()) return; // Check if selling is allowed
        int amount = getAmountInInventory(player, item.getMaterial());

        if (amount <= 0) {
            plugin.getMessageManager().sendMessage(player, "not_enough_items");
            return;
        }

        performTransaction(player, item, amount, false); // Call main transaction logic
    }

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
        Map<String, String> placeholders = Map.of(
            "amount", String.valueOf(amount),
            "item", item.getMaterial().name(), // Consider using a display name if available
            "price", PriceUtil.format(totalPrice),
            "currency_symbol", plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId())
        );


        if (isBuy) {
            // Check balance
            if (!plugin.getCurrencyService().hasBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getMessageManager().sendMessage(player, "not_enough_money", placeholders);
                return;
            }
            // Check inventory space
            int maxStack = item.getMaterial().getMaxStackSize();
            int neededSlots = (int) Math.ceil((double) amount / maxStack);
            if (getEmptySlots(player) < neededSlots && getPartialStackSpace(player, item.getMaterial(), amount) < amount) {
                 plugin.getMessageManager().sendMessage(player, "inventory_full", placeholders);
                 return;
            }


            // Attempt transaction
            if (!plugin.getCurrencyService().removeBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getMessageManager().sendMessage(player, "not_enough_money", placeholders);
                return;
            }

            // Give items
            player.getInventory().addItem(new ItemStack(item.getMaterial(), amount));

            // Record for dynamic pricing
            plugin.getDynamicPriceManager().recordBuy(item, amount);

            // Send success message
            plugin.getMessageManager().sendMessage(player, "buy_success", placeholders);

            // Log to Discord
            plugin.getDiscordWebhookService().logBuy(player, item, amount, totalPrice);

        } else { // Selling
            // Check if player has enough items
            // This check is now guaranteed to pass because of the logic in handleClick / performSellAll
            if (!player.getInventory().contains(item.getMaterial(), amount)) {
                plugin.getMessageManager().sendMessage(player, "not_enough_items", placeholders);
                return;
            }

            // Take items
            player.getInventory().removeItem(new ItemStack(item.getMaterial(), amount));

            // Attempt to give money
            if (!plugin.getCurrencyService().addBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getLogger().severe("CRITICAL: Failed to add balance for " + player.getName() + " after removing items! Returning items.");
                player.getInventory().addItem(new ItemStack(item.getMaterial(), amount)); // Give items back
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
