package xyz.kaijiieow.kjshopplus.gui;

import org.bukkit.Bukkit; // <-- IMPORT BUKKIT
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

import java.util.*; // <-- IMPORT UTIL (includes Map)
import java.util.stream.Collectors;

public class GUIManager {

    private final KJShopPlus plugin;
    private final Set<UUID> openMenus = new HashSet<>();

    // Enum defined inside GUIManager
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

        // --- FIX: Reference GUITYPE correctly ---
        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.CATEGORY_MAIN, "main", 1);
        Inventory inv = Bukkit.createInventory(guiData, size, title);
        guiData.setInventory(inv);

        // Fill background first
        if (menuConfig.getFillItem() != null) {
            ItemStack fill = menuConfig.getFillItem().build(player, false);
            for (int i = 0; i < size; i++) {
                inv.setItem(i, fill);
            }
        }

        // Place category items
        for (MenuItem menuItem : menuConfig.getCategoryItems().values()) {
            if (menuItem.isEnable()) {
                ItemStack item = menuItem.build(player, plugin.isBedrockPlayer(player.getUniqueId()));
                if (item != null && item.getType() != Material.AIR) {
                    if (menuItem.getSlot() >= 0 && menuItem.getSlot() < size && inv.getItem(menuItem.getSlot()) == null) {
                        inv.setItem(menuItem.getSlot(), item);
                    } else if (menuItem.getSlot() >= 0 && menuItem.getSlot() < size) {
                        plugin.getLogger().warning("Category item '" + menuItem.getId() + "' slot " + menuItem.getSlot() + " is already occupied in main menu!");
                    } else {
                        plugin.getLogger().warning("Category item '" + menuItem.getId() + "' has invalid slot: " + menuItem.getSlot());
                    }
                }
            }
        }

        MenuItem closeButtonConfig = menuConfig.getCloseButton();
        if (closeButtonConfig != null) {
            ItemStack closeItem = closeButtonConfig.build(player, plugin.isBedrockPlayer(player.getUniqueId()));
            if (closeItem != null && closeItem.getType() != Material.AIR) {
                 if (closeButtonConfig.getSlot() >= 0 && closeButtonConfig.getSlot() < size) {
                     inv.setItem(closeButtonConfig.getSlot(), closeItem);
                 } else {
                     plugin.getLogger().warning("Close button has invalid slot: " + closeButtonConfig.getSlot());
                 }
            }
        }

        MenuItem playerInfoConfig = menuConfig.getPlayerInfoItem();
        if (playerInfoConfig != null) {
            ItemStack infoItem = buildPlayerInfoItem(player, playerInfoConfig);
             if (infoItem != null && infoItem.getType() != Material.AIR) {
                 if (playerInfoConfig.getSlot() >= 0 && playerInfoConfig.getSlot() < size) {
                    inv.setItem(playerInfoConfig.getSlot(), infoItem);
                 } else {
                      plugin.getLogger().warning("Player info item has invalid slot: " + playerInfoConfig.getSlot());
                 }
             }
        }

        openMenu(player, inv);
    }

    private ItemStack buildPlayerInfoItem(Player player, MenuItem config) {
        Material headMaterial = mapBedrockMaterial(Material.PLAYER_HEAD, player);
        ItemBuilder builder = new ItemBuilder(headMaterial);

        if (headMaterial == Material.PLAYER_HEAD) {
            ItemStack head = builder.build(); // Get current stack
            ItemMeta meta = head.getItemMeta();
             if (meta instanceof SkullMeta skullMeta) { // Check if it's actually SkullMeta
                 skullMeta.setOwningPlayer(player);
                 head.setItemMeta(skullMeta); // Apply back to the ItemStack
                 builder = new ItemBuilder(head); // Re-wrap with ItemBuilder
             }
        }

        // --- FIX: Use correct getName() method ---
        String name = config.getName(); // Get name from config's getter
        name = name.replace("{player_name}", player.getName());
        builder.setName(ChatColor.translateAlternateColorCodes('&', name));

        List<String> finalLore = new ArrayList<>();
        // --- FIX: Use correct getLore() method ---
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
        }

        int itemsPerPage = calculateItemsPerPage(category.getSize());
        int totalPages = (int) Math.ceil((double) items.size() / itemsPerPage);
        if (page < 1) page = 1;
        if (totalPages == 0) totalPages = 1;
        if (page > totalPages) page = totalPages;

        String title = ChatColor.translateAlternateColorCodes('&', category.getTitle(page, totalPages));

        // --- FIX: Reference GUITYPE correctly ---
        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.SHOP_PAGE, categoryId, page);
        Inventory inv = Bukkit.createInventory(guiData, category.getSize(), title);
        guiData.setInventory(inv);

        // Fill background first
        if (category.getFillItem() != null) {
            ItemStack fill = category.getFillItem().build(player, false);
            for (int i = 0; i < category.getSize(); i++) {
                inv.setItem(i, fill);
            }
        }

        // Place layout items (buttons)
        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());
        for (MenuItem layoutItem : category.getLayoutItems().values()) {
            String action = layoutItem.getAction();
            if (action == null) continue;

            if (action.equals("PAGE_PREV") && page <= 1) continue;
            if (action.equals("PAGE_NEXT") && page >= totalPages) continue;

            ItemStack item = layoutItem.build(player, isBedrock);
             if (item != null && item.getType() != Material.AIR) {
                 if (layoutItem.getSlot() >= 0 && layoutItem.getSlot() < category.getSize() && inv.getItem(layoutItem.getSlot()) == null) {
                     inv.setItem(layoutItem.getSlot(), item);
                 } else if (layoutItem.getSlot() >= 0 && layoutItem.getSlot() < category.getSize()) {
                      plugin.getLogger().warning("Layout item '" + layoutItem.getId() + "' slot " + layoutItem.getSlot() + " is already occupied in category '" + categoryId + "'!");
                 } else {
                     plugin.getLogger().warning("Layout item '" + layoutItem.getId() + "' has invalid slot: " + layoutItem.getSlot());
                 }
            }
        }

        // Place shop items
        int startIndex = (page - 1) * itemsPerPage;
        int slotIndex = 0;
        int itemsPlacedThisPage = 0;
        for (int i = 0; i < items.size(); i++) {
            if (i < startIndex || itemsPlacedThisPage >= itemsPerPage) {
                continue;
            }

            ShopItem shopItem = items.get(i);
            int targetSlot = -1;

            if (shopItem.getSlot() != -1 && shopItem.getSlot() < category.getSize()) {
                 ItemStack currentItemInSlot = inv.getItem(shopItem.getSlot());
                 if (currentItemInSlot == null || !ItemBuilder.hasPDCAction(currentItemInSlot)) {
                     targetSlot = shopItem.getSlot();
                 } else {
                     plugin.getLogger().warning("Shop item " + shopItem.getGlobalId() + " defined slot " + shopItem.getSlot() + " is occupied by layout! Trying auto-slot...");
                 }
            }

            if (targetSlot == -1) {
                while (slotIndex < category.getSize()) {
                    ItemStack currentSlotItem = inv.getItem(slotIndex);
                    if (currentSlotItem == null || !ItemBuilder.hasPDCAction(currentSlotItem)) {
                        targetSlot = slotIndex;
                        slotIndex++;
                        break;
                    }
                    slotIndex++;
                }
            }

            if (targetSlot != -1 && targetSlot < category.getSize()) {
                ItemStack displayItem = shopItem.buildDisplayItem(player, isBedrock);
                inv.setItem(targetSlot, displayItem);
                itemsPlacedThisPage++;
            } else if (targetSlot >= category.getSize()) {
                 break;
            }
        }

        openMenu(player, inv);
    }


    public void openTradeMenu(Player player, ShopItem item) {
        if (item == null) {
            player.closeInventory();
            return;
        }

        String title = ChatColor.translateAlternateColorCodes('&', "&8Trade: " + item.getMaterial().name());
        int guiSize = 45;

        // --- FIX: Reference GUITYPE correctly ---
        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.TRADE_CONFIRM, item.getCategoryId(), 1);
        Inventory inv = Bukkit.createInventory(guiData, guiSize, title);
        guiData.setInventory(inv);
        guiData.setTradeItem(item);

        ItemStack fill = new ItemBuilder(mapBedrockMaterial(safeMaterial("GRAY_STAINED_GLASS_PANE", Material.STONE), player))
                .setName(" ")
                .build();
        for (int i = 0; i < guiSize; i++) inv.setItem(i, fill);

        inv.setItem(4, item.buildDisplayItem(player, plugin.isBedrockPlayer(player.getUniqueId())));
        inv.setItem(40, new ItemBuilder(Material.ARROW).setName("&aGo Back")
            .setPDCAction("OPEN_CATEGORY")
            .setPDCValue(item.getCategoryId())
            .build());

        double buyPrice = plugin.getDynamicPriceManager().getBuyPrice(item);
        double sellPrice = plugin.getDynamicPriceManager().getSellPrice(item);
        String symbol = plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId());
        Material buyMat = mapBedrockMaterial(safeMaterial("LIME_CONCRETE", Material.STONE), player);
        Material sellMat = mapBedrockMaterial(safeMaterial("RED_CONCRETE", Material.STONE), player);

        if (item.isAllowBuy()) {
            inv.setItem(10, new ItemBuilder(buyMat).setName("&a&lBuy 1").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice))).setPDCData("BUY", "1").build());
            inv.setItem(11, new ItemBuilder(buyMat).setName("&a&lBuy 8").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice * 8))).setPDCData("BUY", "8").build());
            inv.setItem(12, new ItemBuilder(buyMat).setName("&a&lBuy 16").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice * 16))).setPDCData("BUY", "16").build());
            inv.setItem(13, new ItemBuilder(buyMat).setName("&a&lBuy 32").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice * 32))).setPDCData("BUY", "32").build());
            inv.setItem(14, new ItemBuilder(buyMat).setName("&a&lBuy 64").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(buyPrice * 64))).setPDCData("BUY", "64").build());
        }

        if (item.isAllowSell()) {
            inv.setItem(19, new ItemBuilder(sellMat).setName("&c&lSell 1").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice))).setPDCData("SELL", "1").build());
            inv.setItem(20, new ItemBuilder(sellMat).setName("&c&lSell 8").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * 8))).setPDCData("SELL", "8").build());
            inv.setItem(21, new ItemBuilder(sellMat).setName("&c&lSell 16").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * 16))).setPDCData("SELL", "16").build());
            inv.setItem(22, new ItemBuilder(sellMat).setName("&c&lSell 32").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * 32))).setPDCData("SELL", "32").build());
            inv.setItem(23, new ItemBuilder(sellMat).setName("&c&lSell 64").setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * 64))).setPDCData("SELL", "64").build());

            int sellAllAmount = getAmountInInventory(player, item.getMaterial());
            inv.setItem(25, new ItemBuilder(mapBedrockMaterial(Material.HOPPER, player))
                .setName("&c&lSell All (" + sellAllAmount + ")")
                .setLore(List.of("&7Price: &a" + symbol + PriceUtil.format(sellPrice * sellAllAmount)))
                .setPDCAction("SELL_ALL")
                .build());
        }

        openMenu(player, inv);
    }

    private void openMenu(Player player, Inventory inv) {
        player.openInventory(inv);
        openMenus.add(player.getUniqueId());
    }

    public void handleClick(Player player, int slot, KJGUIData guiData) {
        ItemStack clickedItem = guiData.getInventory().getItem(slot);
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String action = ItemBuilder.getPDCAction(clickedItem);
        String value = ItemBuilder.getPDCValue(clickedItem);
        if (action == null) {
             return;
        }

        ShopItem tradeItem = guiData.getTradeItem();
        String categoryId = guiData.getCategoryId();
        int currentPage = guiData.getPage();

        switch (action) {
            case "OPEN_CATEGORY":
                if (value != null && value.equals("main")) {
                    openCategoryMenu(player);
                } else if (value != null) {
                    openShopPage(player, value, 1);
                }
                break;
            case "TRADE_ITEM":
                ShopItem itemToTrade = plugin.getShopManager().getShopItem(value);
                if (itemToTrade != null) {
                    openTradeMenu(player, itemToTrade);
                }
                break;
            case "PAGE_NEXT":
                if (categoryId != null) {
                    openShopPage(player, categoryId, currentPage + 1);
                }
                break;
            case "PAGE_PREV":
                if (categoryId != null) {
                    openShopPage(player, categoryId, currentPage - 1);
                }
                break;
            case "BUY":
                if (tradeItem == null) return;
                try {
                    performTransaction(player, tradeItem, Integer.parseInt(value), true);
                    openTradeMenu(player, tradeItem);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid BUY amount: " + value);
                }
                break;
            case "SELL":
                 if (tradeItem == null) return;
                try {
                    performTransaction(player, tradeItem, Integer.parseInt(value), false);
                    openTradeMenu(player, tradeItem);
                } catch (NumberFormatException e) {
                     plugin.getLogger().warning("Invalid SELL amount: " + value);
                }
                break;
            case "SELL_ALL":
                performSellAllTransaction(player, tradeItem);
                openTradeMenu(player, tradeItem);
                break;
            case "CLOSE":
                player.closeInventory();
                break;
        }
    }

    private void performSellAllTransaction(Player player, ShopItem item) {
        if (item == null) return;
        int amount = getAmountInInventory(player, item.getMaterial());

        if (amount <= 0) {
            plugin.getMessageManager().sendMessage(player, "not_enough_items");
            return;
        }

        performTransaction(player, item, amount, false);
    }

    private void performTransaction(Player player, ShopItem item, int amount, boolean isBuy) {
        if (item == null) return;
        if (amount <= 0) return;

        double pricePerItem = isBuy ? plugin.getDynamicPriceManager().getBuyPrice(item)
                                     : plugin.getDynamicPriceManager().getSellPrice(item);
        double totalPrice = pricePerItem * amount;

        Map<String, String> placeholders = new HashMap<>() {{
            put("amount", String.valueOf(amount));
            put("item", item.getMaterial().name());
            put("price", PriceUtil.format(totalPrice));
            put("currency_symbol", plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId()));
        }};

        if (isBuy) {
            double balance = plugin.getCurrencyService().getBalance(player, item.getCurrencyId());
            if (balance < totalPrice) {
                plugin.getMessageManager().sendMessage(player, "not_enough_money", placeholders);
                return;
            }
            if (getEmptySlots(player) < (int) Math.ceil((double) amount / item.getMaterial().getMaxStackSize())) {
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

        } else {
            if (!player.getInventory().contains(item.getMaterial(), amount)) {
                plugin.getMessageManager().sendMessage(player, "not_enough_items", placeholders);
                return;
            }

            player.getInventory().removeItem(new ItemStack(item.getMaterial(), amount));
            if (!plugin.getCurrencyService().addBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getLogger().warning("Failed to add balance for " + player.getName() + " after selling items! This is critical.");
                player.getInventory().addItem(new ItemStack(item.getMaterial(), amount));
                plugin.getMessageManager().sendMessage(player, "error_occurred");
                return;
            }
            plugin.getDynamicPriceManager().recordSell(item, amount);
            plugin.getMessageManager().sendMessage(player, "sell_success", placeholders);

            plugin.getDiscordWebhookService().logSell(player, item, amount, totalPrice);
        }
    }

    private int getAmountInInventory(Player player, Material material) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    private int getEmptySlots(Player player) {
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    private int calculateItemsPerPage(int size) {
        if (size == 54) return 45;
        if (size == 45) return 36;
        if (size == 36) return 27;
        if (size == 27) return 18;
        return Math.max(0, size - 9);
    }

    private Material safeMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public Material mapBedrockMaterial(Material javaMaterial, Player player) {
        if (javaMaterial == null) return Material.STONE;
        if (plugin.isBedrockPlayer(player.getUniqueId()) && plugin.getConfigManager().isBedrockCompatEnabled()) {
            return plugin.getConfigManager().getBedrockMappedMaterial(javaMaterial);
        }
        return javaMaterial;
    }

    public void onMenuClose(Player player) {
        openMenus.remove(player.getUniqueId());
        plugin.getPlayerTapManager().clear(player.getUniqueId());
    }

    public void closeAllMenus() {
        Iterator<UUID> iterator = openMenus.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            }
            iterator.remove();
        }
    }
}

