package xyz.kaijiieow.kjshopplus.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.ConfigManager;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
import xyz.kaijiieow.kjshopplus.gui.util.ItemBuilder;
import xyz.kaijiieow.kjshopplus.gui.util.PlayerTapManager;

public class GUIListener implements Listener {

    private final KJShopPlus plugin;
    private final ConfigManager configManager;
    private final GUIManager guiManager;
    private final PlayerTapManager tapManager;

    public GUIListener(KJShopPlus plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.guiManager = plugin.getGuiManager();
        this.tapManager = plugin.getPlayerTapManager();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();

        
        if (!(topInventory.getHolder() instanceof KJGUIData guiData)) {
            return;
        }

        
        if (clickedInventory == null) {
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ClickType clickType = event.getClick();
        InventoryAction action = event.getAction();
        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());

        // Handle Sell GUI - allow items to be placed (will be sold on close)
        if (guiData.getGuiType() == GUIManager.GUITYPE.SELL_GUI) {
            if (clickedInventory == topInventory) {
                // Allow taking items out of sell GUI - don't cancel
                // Items will be sold when window is closed
                return;
            } else {
                // Allow moving items from player inventory to sell GUI
                // Items will be collected and sold when window is closed
                // Don't cancel - let the item be placed
                return;
            }
        }
        
        if (clickedInventory != topInventory) {
            
            

            
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || (configManager.isDisableShiftClick() && clickType.isShiftClick())) {
                event.setCancelled(true);
                return;
            }

            
            if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
                 event.setCancelled(true);
                 return;
            }
            
            
            
            return;
        }

        
        
        
        event.setCancelled(true);

        
        ItemStack itemClicked = clickedInventory.getItem(event.getSlot());
        String pdcAction = ItemBuilder.getPDCAction(itemClicked);
        String pdcValue = ItemBuilder.getPDCValue(itemClicked);
        
        boolean isShopItem = pdcAction != null;
        if (configManager.isLeftTapOnly() && clickType != ClickType.LEFT && !isShopItem) {
            return;
        }
        if (!configManager.isLeftTapOnly() && configManager.isDisableRightClick() && clickType == ClickType.RIGHT && !isShopItem) {
            return;
        }
        
        
        
        if (configManager.isDisableShiftClick() && clickType.isShiftClick()) {
            // System.out.println("[KJShopPlus DEBUG] Click cancelled: Shift click disabled (inside shop).");
            return;
        }

        
        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
             // System.out.println("[KJShopPlus DEBUG] Click cancelled: Hotbar swap action (inside shop).");
             return;
        }


        
        if (!tapManager.canTap(player.getUniqueId(), configManager.getTapDebounceMs())) {
             
             return;
        }
        
        // System.out.println("[KJShopPlus DEBUG] Click processing: Slot=" + event.getSlot()
        //         + " | Item=" + (itemClicked != null ? itemClicked.getType() : "NULL")
        //         + " | PDC Action=" + pdcAction + " | PDC Value=" + pdcValue);

        if (pdcAction != null) {
             guiManager.handleClick(player, event.getSlot(), guiData, clickType);
        } else {
             // System.out.println("[KJShopPlus DEBUG] Click ignored: No PDC Action found on item.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof KJGUIData guiData) {
            if (guiData.getGuiType() == GUIManager.GUITYPE.SELL_GUI) {
                // Allow dragging items into sell GUI
                // Items will be collected and sold when window is closed
                // Don't cancel - let items be placed
                return;
            }
            if (configManager.isDisableInventoryDrag()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof KJGUIData guiData) {
            if (guiData.getGuiType() == GUIManager.GUITYPE.SELL_GUI) {
                // Process all items in sell GUI when closing
                Player player = (Player) event.getPlayer();
                Inventory inv = event.getInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (int i = 0; i < inv.getSize(); i++) {
                        ItemStack item = inv.getItem(i);
                        if (item != null && item.getType() != Material.AIR) {
                            guiManager.processSellGUIItem(player, item, i, guiData);
                        }
                    }
                });
            }
            guiManager.onMenuClose((Player) event.getPlayer());
        }
    }
}
