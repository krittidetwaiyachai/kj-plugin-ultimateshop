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
import java.util.ArrayList; // Import ที่จำเป็น

public class GUIManager {

    private final KJShopPlus plugin;
    private final Set<UUID> openMenus = new HashSet<>();

    public enum GUITYPE {
        CATEGORY_MAIN,
        SHOP_PAGE,
        QUANTITY_SELECTOR
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

        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.CATEGORY_MAIN, "main", 1, true, 0, 1);
        Inventory inv = Bukkit.createInventory(guiData, size, title);
        guiData.setInventory(inv);

        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());
        for (MenuItem menuItem : menuConfig.getCategoryItems().values()) {
            if (menuItem.isEnable()) {
                ItemStack item = menuItem.build(player, isBedrock);
                if (item != null && item.getType() != Material.AIR) {
                    if (menuItem.getSlot() >= 0 && menuItem.getSlot() < size) {
                        inv.setItem(menuItem.getSlot(), item);
                    } else {
                        plugin.getLogger().warning("Category item '" + menuItem.getId() + "' has invalid slot: " + menuItem.getSlot());
                    }
                }
            }
        }

        MenuItem closeButtonConfig = menuConfig.getCloseButton();
        if (closeButtonConfig != null) {
            ItemStack closeItem = closeButtonConfig.build(player, isBedrock);
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

        if (menuConfig.getFillItem() != null) {
            ItemStack fill = menuConfig.getFillItem().build(player, false);
            for (int i = 0; i < size; i++) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, fill);
                }
            }
        }

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


    public void openShopPage(Player player, String categoryId, int page, boolean isBuyMode) {
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
        int totalPages = (itemsPerPage <= 0) ? 1 : (int) Math.ceil((double) items.size() / itemsPerPage);
        if (page < 1) page = 1;
        if (totalPages == 0) totalPages = 1;
        if (page > totalPages) page = totalPages;

        String title = ChatColor.translateAlternateColorCodes('&', category.getTitle(page, totalPages));

        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.SHOP_PAGE, categoryId, page, isBuyMode, 0, page);
        Inventory inv = Bukkit.createInventory(guiData, category.getSize(), title);
        guiData.setInventory(inv);

        Set<Integer> layoutSlots = new HashSet<>();
        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());
        boolean toggleButtonPlaced = false;


        for (MenuItem layoutItem : category.getLayoutItems().values()) {
            String action = layoutItem.getAction();
            if (action == null) continue;

            if (action.equals("PAGE_PREV") && page <= 1) continue;
            if (action.equals("PAGE_NEXT") && page >= totalPages) continue;

            int slot = layoutItem.getSlot();
            ItemStack item;

            if (action.equals("TOGGLE_MODE")) {
                item = createToggleModeItem(player, isBuyMode);
            } else {
                item = layoutItem.build(player, isBedrock);
            }


             if (item != null && item.getType() != Material.AIR) {
                 if (slot >= 0 && slot < category.getSize()) {
                     inv.setItem(slot, item);
                     layoutSlots.add(slot);
                     if ("TOGGLE_MODE".equals(action)) {
                         toggleButtonPlaced = true;
                     }
                 } else {
                     plugin.getLogger().warning("Layout item '" + layoutItem.getId() + "' has invalid slot: " + slot + " in category '" + categoryId + "'");
                 }
            }
        }

        if (!toggleButtonPlaced) {
            int toggleSlot = findToggleSlot(inv, layoutSlots);
            if (toggleSlot != -1) {
                inv.setItem(toggleSlot, createToggleModeItem(player, isBuyMode));
                layoutSlots.add(toggleSlot);
            } else {
                plugin.getLogger().warning("Unable to place toggle mode button automatically for category '" + categoryId + "'");
            }
        }

        int startIndex = (page - 1) * itemsPerPage;
        int slotIndex = 0;
        int itemsPlacedThisPage = 0;

        if (itemsPerPage > 0) {
            for (int i = startIndex; i < items.size() && itemsPlacedThisPage < itemsPerPage; i++) {
                ShopItem shopItem = items.get(i);

                if (isBuyMode && !shopItem.isAllowBuy()) continue;
                if (!isBuyMode && !shopItem.isAllowSell()) continue;


                int targetSlot = -1;

                if (shopItem.getSlot() != -1 && shopItem.getSlot() < category.getSize()) {
                    if (!layoutSlots.contains(shopItem.getSlot())) {
                        targetSlot = shopItem.getSlot();
                    } else {
                        plugin.getLogger().warning("Shop item " + shopItem.getGlobalId() + " defined slot " + shopItem.getSlot() + " is occupied by layout! Trying auto-slot...");
                    }
                }

                if (targetSlot == -1) {
                    while (slotIndex < category.getSize()) {
                        if (!layoutSlots.contains(slotIndex) && (inv.getItem(slotIndex) == null || inv.getItem(slotIndex).getType() == Material.AIR)) {
                            targetSlot = slotIndex;
                            slotIndex++;
                            break;
                        }
                        slotIndex++;
                    }
                }

                if (targetSlot != -1 && targetSlot < category.getSize()) {
                    ItemStack displayItem = shopItem.buildDisplayItem(player, isBedrock, isBuyMode);
                    inv.setItem(targetSlot, displayItem);
                    itemsPlacedThisPage++;
                } else {
                    plugin.getLogger().warning("Could not find a free slot for shop item " + shopItem.getGlobalId() + " on page " + page + " in category '" + categoryId + "' (Size: " + category.getSize() + ")");
                    break;
                }
            }
        }

        if (category.getFillItem() != null) {
            ItemStack fill = category.getFillItem().build(player, false);
            for (int i = 0; i < category.getSize(); i++) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, fill);
                }
            }
        }

        openMenu(player, inv);
    }


    private void openQuantitySelector(Player player, ShopItem item, boolean isBuyMode, int currentAmount, int previousPage) {
        if (item == null) {
            player.closeInventory();
            return;
        }
        
        if (isBuyMode && !item.isAllowBuy()) {
             plugin.getMessageManager().sendMessage(player, "gui_buy_disabled");
             openShopPage(player, item.getCategoryId(), previousPage, isBuyMode);
             return;
        }
        if (!isBuyMode && !item.isAllowSell()) {
             plugin.getMessageManager().sendMessage(player, "gui_sell_disabled");
             openShopPage(player, item.getCategoryId(), previousPage, isBuyMode);
             return;
        }


        String title = ChatColor.translateAlternateColorCodes('&', isBuyMode ? "&a&lSelect Buy Amount" : "&c&lSelect Sell Amount");
        int guiSize = 45;

        KJGUIData guiData = new KJGUIData(null, GUIManager.GUITYPE.QUANTITY_SELECTOR, item.getCategoryId(), previousPage, isBuyMode, currentAmount, previousPage);
        Inventory inv = Bukkit.createInventory(guiData, guiSize, title);
        guiData.setInventory(inv);
        guiData.setTradeItem(item);

        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());
        ItemStack fill = new ItemBuilder(mapBedrockMaterial(safeMaterial("GRAY_STAINED_GLASS_PANE", Material.STONE), player))
                .setName(" ")
                .build();
        for (int i = 0; i < guiSize; i++) inv.setItem(i, fill);

        final int displaySlot = 22;
        final int confirmSlot = 37;
        final int cancelSlot = 43;
        final int sellAllSlot = 41;

        Material confirmMat = mapBedrockMaterial(isBuyMode ? safeMaterial("LIME_CONCRETE", Material.LIME_WOOL)
                                                           : safeMaterial("RED_CONCRETE", Material.RED_WOOL), player);
        String confirmMessage = isBuyMode ? plugin.getMessageManager().getMessage("gui_confirm_buy_button", "&a&lConfirm Purchase")
                                          : plugin.getMessageManager().getMessage("gui_confirm_sell_button", "&c&lConfirm Sale");
        inv.setItem(confirmSlot, new ItemBuilder(confirmMat)
            .setName(confirmMessage)
            .setLore(Collections.singletonList(plugin.getMessageManager().getMessage("gui_confirm_lore", "&7Complete this transaction")))
            .setPDCAction("CONFIRM_TRANSACTION")
            .build());

        inv.setItem(cancelSlot, new ItemBuilder(mapBedrockMaterial(Material.BARRIER, player))
            .setName(plugin.getMessageManager().getMessage("gui_cancel_button", "&c&lCancel"))
            .setLore(Collections.singletonList(plugin.getMessageManager().getMessage("gui_cancel_lore", "&7Return to shop")))
            .setPDCAction("CANCEL_TRANSACTION")
            .build());

        Material addMat = mapBedrockMaterial(safeMaterial("GREEN_STAINED_GLASS_PANE", Material.LIME_STAINED_GLASS_PANE), player);
        Material subMat = mapBedrockMaterial(safeMaterial("RED_STAINED_GLASS_PANE", Material.RED_STAINED_GLASS_PANE), player);

        int[] amountSteps = {1, 8, 32, 64};
        int[] addSlots = {24, 25, 33, 34};
        int[] subtractSlots = {20, 19, 29, 28};

        for (int i = 0; i < amountSteps.length; i++) {
            int step = amountSteps[i];
            int stackAmount = Math.max(1, Math.min(step, addMat.getMaxStackSize()));
            int stackAmountSub = Math.max(1, Math.min(step, subMat.getMaxStackSize()));
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.valueOf(step));

            inv.setItem(addSlots[i], new ItemBuilder(addMat)
                .setName("&a&l+" + step)
                .setLore(Collections.singletonList(plugin.getMessageManager().getMessage("gui_add_amount_lore", placeholders, "&7Increase by {amount}")))
                .setAmount(stackAmount)
                .setPDCData("ADD_AMOUNT", String.valueOf(step))
                .build());

            if (currentAmount >= step && subtractSlots[i] >= 0) {
                inv.setItem(subtractSlots[i], new ItemBuilder(subMat)
                    .setName("&c&l-" + step)
                    .setLore(Collections.singletonList(plugin.getMessageManager().getMessage("gui_sub_amount_lore", placeholders, "&7Decrease by {amount}")))
                    .setAmount(stackAmountSub)
                    .setPDCData("SUB_AMOUNT", String.valueOf(step))
                    .build());
            }
        }

        if (!isBuyMode) {
             int sellAllAmount = getAmountInInventory(player, item.getMaterial());
             double sellPrice = plugin.getDynamicPriceManager().getSellPrice(item);
             String symbol = plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId());

             Map<String, String> sellAllPlaceholders = new HashMap<>();
             sellAllPlaceholders.put("amount", String.valueOf(sellAllAmount));
             sellAllPlaceholders.put("currency_symbol", symbol != null ? symbol : "");
             sellAllPlaceholders.put("total_price", PriceUtil.format(sellPrice * sellAllAmount));

             List<String> sellAllLore = new ArrayList<>();
             sellAllLore.add(plugin.getMessageManager().getMessage("gui_total_price", sellAllPlaceholders, "&7Total Price: &6{currency_symbol}{total_price}"));
             if (sellAllAmount == 0) {
                 sellAllLore.add(plugin.getMessageManager().getMessage("not_enough_items", "&cYou have no items to sell."));
             }

             inv.setItem(sellAllSlot, new ItemBuilder(mapBedrockMaterial(Material.HOPPER, player))
                 .setName(plugin.getMessageManager().getMessage("gui_sell_all_button", "&c&lSell All") + " &f(" + sellAllAmount + ")")
                 .setLore(sellAllLore)
                 .setPDCAction("SELL_ALL_CART")
                 .build());
        }


        updateQuantityDisplay(inv, player, item, isBuyMode, currentAmount, displaySlot);

        openMenu(player, inv);
    }

    private void updateQuantityDisplay(Inventory inv, Player player, ShopItem item, boolean isBuyMode, int amount, int displaySlot) {
        int safeAmount = Math.max(0, amount);
        double pricePerItem = isBuyMode ? plugin.getDynamicPriceManager().getBuyPrice(item)
                                        : plugin.getDynamicPriceManager().getSellPrice(item);
        double totalPrice = pricePerItem * safeAmount;
        String symbol = plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId());
        if (symbol == null) {
            symbol = "";
        }

        String modeName = isBuyMode ? plugin.getMessageManager().getMessage("gui_buying", "&aBuying")
                                   : plugin.getMessageManager().getMessage("gui_selling", "&cSelling");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(safeAmount));
        placeholders.put("total_price", PriceUtil.format(totalPrice));
        placeholders.put("price_per_item", PriceUtil.format(pricePerItem));
        placeholders.put("currency_symbol", symbol);

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageManager().getMessage("gui_selected_amount", placeholders, "&7Selected: &b{amount}"));
        lore.add(plugin.getMessageManager().getMessage("gui_item_price", placeholders, "&7Price / item: &e{currency_symbol}{price_per_item}"));
        lore.add(plugin.getMessageManager().getMessage("gui_total_price", placeholders, "&7Total Price: &6{currency_symbol}{total_price}"));
        lore.add(" ");
        if (isBuyMode) {
            lore.add(plugin.getMessageManager().getMessage("gui_click_to_confirm_buy", "&aClick confirm (bottom left) to buy."));
        } else {
            int playerAmount = getAmountInInventory(player, item.getMaterial());
            lore.add(plugin.getMessageManager().getMessage("gui_you_have", "&7You have: &e") + playerAmount);
            lore.add(plugin.getMessageManager().getMessage("gui_click_to_confirm_sell", "&cClick confirm (bottom left) to sell."));
        }

        // *** FIX: Use getConfigDisplayName() ***
        String baseName = item.getConfigDisplayName() != null ? item.getConfigDisplayName() : item.getMaterial().name();
        ItemStack displayItem = new ItemBuilder(item.getMaterial())
            .setName(modeName + " " + ChatColor.translateAlternateColorCodes('&', baseName) + " x" + safeAmount)
            .setAmount(1)
            .setLore(lore)
            .build();

        inv.setItem(displaySlot, displayItem);
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
        boolean isBuyMode = guiData.isBuyMode();
        int currentAmount = guiData.getCurrentAmount();
        int previousPage = guiData.getPreviousPage();

        switch (action) {
            case "OPEN_CATEGORY":
                if (value != null && value.equals("main")) {
                    openCategoryMenu(player);
                } else if (value != null) {
                    openShopPage(player, value, 1, true);
                }
                break;
            
            case "TRADE_ITEM_BUY":
                if (value != null) {
                    ShopItem itemToBuy = plugin.getShopManager().getShopItem(value);
                    if (itemToBuy != null) {
                        openQuantitySelector(player, itemToBuy, true, 1, currentPage);
                    }
                }
                break;
            
            case "TRADE_ITEM_SELL":
                 if (value != null) {
                    ShopItem itemToSell = plugin.getShopManager().getShopItem(value);
                    if (itemToSell != null) {
                        if (!itemToSell.isAllowSell()) {
                             plugin.getMessageManager().sendMessage(player, "gui_sell_disabled");
                             return;
                        }
                        int playerHas = getAmountInInventory(player, itemToSell.getMaterial());
                        if (playerHas == 0) {
                            plugin.getMessageManager().sendMessage(player, "not_enough_items");
                            return;
                        }
                        openQuantitySelector(player, itemToSell, false, 1, currentPage);
                    }
                }
                break;
            
            case "TOGGLE_MODE":
                if (categoryId != null && guiData.getGuiType() == GUITYPE.SHOP_PAGE) {
                    openShopPage(player, categoryId, currentPage, !isBuyMode);
                }
                break;

            case "PAGE_NEXT":
                if (categoryId != null && guiData.getGuiType() == GUITYPE.SHOP_PAGE) {
                    openShopPage(player, categoryId, currentPage + 1, isBuyMode);
                }
                break;
            case "PAGE_PREV":
                if (categoryId != null && guiData.getGuiType() == GUITYPE.SHOP_PAGE) {
                    openShopPage(player, categoryId, currentPage - 1, isBuyMode);
                }
                break;

            case "ADD_AMOUNT":
                if (tradeItem != null && value != null && guiData.getGuiType() == GUITYPE.QUANTITY_SELECTOR) {
                    try {
                        int amountToAdd = Integer.parseInt(value);
                        int newAmount = currentAmount + amountToAdd;
                        openQuantitySelector(player, tradeItem, isBuyMode, newAmount, previousPage);
                    } catch (NumberFormatException e) { /* ignore */ }
                }
                break;
            case "SUB_AMOUNT":
                 if (tradeItem != null && value != null && guiData.getGuiType() == GUITYPE.QUANTITY_SELECTOR) {
                    try {
                        int amountToSub = Integer.parseInt(value);
                        int newAmount = Math.max(0, currentAmount - amountToSub);
                        openQuantitySelector(player, tradeItem, isBuyMode, newAmount, previousPage);
                    } catch (NumberFormatException e) { /* ignore */ }
                }
                break;
            case "SELL_ALL_CART":
                if (tradeItem != null && !isBuyMode && guiData.getGuiType() == GUITYPE.QUANTITY_SELECTOR) {
                    performSellAllTransaction(player, tradeItem);
                    if (categoryId != null) {
                        openShopPage(player, categoryId, previousPage, false);
                    } else {
                        player.closeInventory();
                    }
                }
                break;
            case "CONFIRM_TRANSACTION":
                if (tradeItem != null && currentAmount > 0 && guiData.getGuiType() == GUITYPE.QUANTITY_SELECTOR) {
                    performTransaction(player, tradeItem, currentAmount, isBuyMode);
                    openShopPage(player, categoryId, previousPage, isBuyMode);
                } else if (currentAmount <= 0) {
                    plugin.getMessageManager().sendMessage(player, "invalid_amount");
                    openShopPage(player, categoryId, previousPage, isBuyMode);
                }
                break;
            case "CANCEL_TRANSACTION":
                 openShopPage(player, categoryId, previousPage, isBuyMode);
                break;

            case "CLOSE":
                player.closeInventory();
                break;
            default:
                 plugin.getLogger().warning("Unknown GUI action clicked: " + action);
                 break;
        }
    }

    private void performSellAllTransaction(Player player, ShopItem item) {
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

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("item", item.getMaterial().name());
        placeholders.put("price", PriceUtil.format(totalPrice));
        placeholders.put("currency_symbol", plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId()));


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
             int playerAmount = getAmountInInventory(player, item.getMaterial());
             int amountToSell = Math.min(amount, playerAmount);

             if (amountToSell <= 0) {
                 plugin.getMessageManager().sendMessage(player, "not_enough_items", placeholders);
                 return;
             }
             totalPrice = pricePerItem * amountToSell;
             placeholders.put("amount", String.valueOf(amountToSell));
             placeholders.put("price", PriceUtil.format(totalPrice));


            player.getInventory().removeItem(new ItemStack(item.getMaterial(), amountToSell));

            if (!plugin.getCurrencyService().addBalance(player, item.getCurrencyId(), totalPrice)) {
                plugin.getLogger().severe("CRITICAL: Failed to add balance for " + player.getName() + " after removing items! Returning items.");
                player.getInventory().addItem(new ItemStack(item.getMaterial(), amountToSell));
                plugin.getMessageManager().sendMessage(player, "error_occurred");
                return;
            }

            plugin.getDynamicPriceManager().recordSell(item, amountToSell);
            plugin.getMessageManager().sendMessage(player, "sell_success", placeholders);
            plugin.getDiscordWebhookService().logSell(player, item, amountToSell, totalPrice);
        }
    }
     private int getPartialStackSpace(Player player, Material material, int amountNeeded) {
        int space = 0;
        int maxStack = material.getMaxStackSize();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material && item.getAmount() < maxStack) {
                space += maxStack - item.getAmount();
                if (space >= amountNeeded) {
                    return amountNeeded;
                }
            }
        }
        return space;
    }


    private int getAmountInInventory(Player player, Material material) {
        int amount = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

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

    private int calculateItemsPerPage(int guiSize) {
        if (guiSize <= 9) return 0;
        if (guiSize == 18) return 9;
        if (guiSize == 27) return 18;
        if (guiSize == 36) return 27;
        if (guiSize == 45) return 36;
        if (guiSize == 54) return 45;
        return Math.max(0, guiSize - 9);
    }

    private ItemStack createToggleModeItem(Player player, boolean isBuyMode) {
        Material baseMaterial = isBuyMode ? safeMaterial("EMERALD_BLOCK", Material.LIME_WOOL)
                                          : safeMaterial("REDSTONE_BLOCK", Material.RED_WOOL);
        ItemBuilder builder = new ItemBuilder(mapBedrockMaterial(baseMaterial, player));
        if (isBuyMode) {
            builder.setName(plugin.getMessageManager().getMessage("gui_buy_mode_button", "&a&lBuy Mode"));
            builder.setLore(Collections.singletonList(plugin.getMessageManager().getMessage("gui_buy_mode_lore", "&7Click to switch to Sell Mode")));
        } else {
            builder.setName(plugin.getMessageManager().getMessage("gui_sell_mode_button", "&c&lSell Mode"));
            builder.setLore(Collections.singletonList(plugin.getMessageManager().getMessage("gui_sell_mode_lore", "&7Click to switch to Buy Mode")));
        }
        return builder.setPDCAction("TOGGLE_MODE").build();
    }

    private int findToggleSlot(Inventory inv, Set<Integer> reservedSlots) {
        List<Integer> candidates = buildPreferredToggleSlots(inv.getSize());
        for (int slot : candidates) {
            if (slot < 0 || slot >= inv.getSize()) continue;
            if (reservedSlots.contains(slot)) continue;
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    private List<Integer> buildPreferredToggleSlots(int size) {
        LinkedHashSet<Integer> ordered = new LinkedHashSet<>();
        if (size <= 0) return new ArrayList<>();

        int rows = (int) Math.ceil(size / 9.0);
        if (rows <= 0) rows = 1;
        int lastRowStart = Math.max(0, (rows - 1) * 9);
        int lastRowEnd = Math.min(size, lastRowStart + 9);
        int center = Math.min(size - 1, lastRowStart + 4);
        ordered.add(center);

        for (int offset = 1; offset < 5; offset++) {
            int left = center - offset;
            int right = center + offset;
            if (left >= lastRowStart) ordered.add(left);
            if (right < lastRowEnd) ordered.add(right);
        }

        if (rows > 1) {
            int previousRowStart = Math.max(0, lastRowStart - 9);
            int previousRowEnd = Math.min(size, previousRowStart + 9);
            int previousCenter = Math.min(size - 1, previousRowStart + 4);
            if (previousCenter >= previousRowStart && previousCenter < previousRowEnd) {
                ordered.add(previousCenter);
            }
        }

        for (int i = 0; i < size; i++) {
            ordered.add(i);
        }
        return new ArrayList<>(ordered);
    }

    private Material safeMaterial(String name, Material fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material name in GUI config: " + name);
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
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof KJGUIData) {
                player.closeInventory();
            }
            iterator.remove();
        }
        openMenus.clear();
    }
}

