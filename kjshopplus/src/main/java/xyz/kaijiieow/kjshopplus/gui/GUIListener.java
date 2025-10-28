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
        this.guiManager = plugin.getGuiManager(); // Get GUIManager instance
        this.tapManager = plugin.getPlayerTapManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();

        // 1. Check if the GUI is ours. If not, ignore completely.
        if (!(topInventory.getHolder() instanceof KJGUIData guiData)) {
            return;
        }

        // 2. Handle clicks *outside* the GUI windows (e.g., dropping items, slot -999)
        if (clickedInventory == null) {
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ClickType clickType = event.getClick();
        InventoryAction action = event.getAction();
        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());

        // 3. Handle clicks *inside the player's inventory*
        if (clickedInventory != topInventory) {
            // Player is clicking their own inventory.
            // We only care if they are trying to interact *with* the shop GUI.

            // If shift-click (MOVE_TO_OTHER_INVENTORY), cancel it
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || (configManager.isDisableShiftClick() && clickType.isShiftClick())) {
                event.setCancelled(true);
                return;
            }

            // If hotbar swap, cancel it
            if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
                 event.setCancelled(true);
                 return;
            }
            
            // Otherwise (e.g., moving items *within* player inv), let it happen.
            // DO NOT CANCEL. DO NOT RETURN. Let vanilla handle it.
            return;
        }

        // 4. Handle clicks *inside the shop GUI* (clickedInventory == topInventory)
        
        // ALWAYS cancel the vanilla event. Player cannot take items from the shop GUI.
        event.setCancelled(true);

        // 5. Check all preventative input controls *for shop GUI clicks*
        if (configManager.isLeftTapOnly() && clickType != ClickType.LEFT) {
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Left tap only mode.");
             return;
        }
        if (!configManager.isLeftTapOnly() && configManager.isDisableRightClick() && clickType == ClickType.RIGHT) {
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Right click disabled.");
             return;
        }
        
        // This check is needed if shift-clicking is used for buy/sell actions
        // But for now, if it's disabled, we stop it.
        if (configManager.isDisableShiftClick() && clickType.isShiftClick()) {
            System.out.println("[KJShopPlus DEBUG] Click cancelled: Shift click disabled (inside shop).");
            return;
        }
        
        // This check is also needed
        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Hotbar swap action (inside shop).");
             return;
        }


        // 6. Check debounce
        if (!tapManager.canTap(player.getUniqueId(), configManager.getTapDebounceMs())) {
             // System.out.println("[KJShopPlus DEBUG] Click cancelled: Debounce."); // Debug (Maybe spammy)
             return;
        }

        // 7. Process the click
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