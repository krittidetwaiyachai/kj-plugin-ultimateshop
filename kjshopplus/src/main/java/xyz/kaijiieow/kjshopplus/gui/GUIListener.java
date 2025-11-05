package xyz.kaijiieow.kjshopplus.gui;

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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

        
        if (configManager.isLeftTapOnly() && clickType != ClickType.LEFT) {
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Left tap only mode.");
             return;
        }
        if (!configManager.isLeftTapOnly() && configManager.isDisableRightClick() && clickType == ClickType.RIGHT) {
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Right click disabled.");
             return;
        }
        
        
        
        if (configManager.isDisableShiftClick() && clickType.isShiftClick()) {
            System.out.println("[KJShopPlus DEBUG] Click cancelled: Shift click disabled (inside shop).");
            return;
        }
        
        
        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Hotbar swap action (inside shop).");
             return;
        }


        
        if (!tapManager.canTap(player.getUniqueId(), configManager.getTapDebounceMs())) {
             
             return;
        }

        
        ItemStack itemClicked = clickedInventory.getItem(event.getSlot());
        String pdcAction = ItemBuilder.getPDCAction(itemClicked);
        String pdcValue = ItemBuilder.getPDCValue(itemClicked);
        
        System.out.println("[KJShopPlus DEBUG] Click processing: Slot=" + event.getSlot()
                + " | Item=" + (itemClicked != null ? itemClicked.getType() : "NULL")
                + " | PDC Action=" + pdcAction + " | PDC Value=" + pdcValue);

        if (pdcAction != null) {
             guiManager.handleClick(player, event.getSlot(), guiData);
        } else {
             System.out.println("[KJShopPlus DEBUG] Click ignored: No PDC Action found on item.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof KJGUIData) {
            if (configManager.isDisableInventoryDrag()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof KJGUIData) {
            guiManager.onMenuClose((Player) event.getPlayer());
        }
    }
}