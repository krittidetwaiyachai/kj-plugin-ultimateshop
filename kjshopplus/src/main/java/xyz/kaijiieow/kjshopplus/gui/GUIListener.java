package xyz.kaijiieow.kjshopplus.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType; // Import ClickType
import org.bukkit.event.inventory.InventoryAction; // Import InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent; // Import InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder; // Import InventoryHolder
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
        // --- FIX: Check if clickedInventory is null before proceeding ---
        if (clickedInventory == null) {
            // This happens when clicking outside the inventory area (like dropping an item)
             // Check if the holder IS KJGUIData before cancelling, otherwise let vanilla handle it
             if (event.getView().getTopInventory().getHolder() instanceof KJGUIData) {
                 event.setCancelled(true);
             }
             return;
        }
        // --- END FIX ---

        Inventory topInventory = event.getView().getTopInventory();


        if (!(topInventory.getHolder() instanceof KJGUIData guiData)) {
            return;
        }

        if (event.getSlot() == -999) { // Should be covered by clickedInventory == null now, but keep for safety
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ClickType clickType = event.getClick();
        InventoryAction action = event.getAction();
        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());

        // --- Input Controls ---
        // Combine left-tap-only and disable-right-click for clarity
        if (configManager.isLeftTapOnly() && clickType != ClickType.LEFT) {
             event.setCancelled(true);
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Left tap only mode."); // Debug
             return;
        }
        // If left-tap-only is false, THEN check if right click disable is true
        if (!configManager.isLeftTapOnly() && configManager.isDisableRightClick() && clickType == ClickType.RIGHT) {
             event.setCancelled(true);
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Right click disabled."); // Debug
             return;
        }

        if (configManager.isDisableShiftClick() && clickType.isShiftClick()) {
            event.setCancelled(true);
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Shift click disabled."); // Debug
            return;
        }

        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
             event.setCancelled(true);
             System.out.println("[KJShopPlus DEBUG] Click cancelled: Hotbar swap action."); // Debug
             return;
        }

        // Prevent moving items INTO the GUI or moving items within player inv trying to interact with GUI
        if (clickedInventory != topInventory || action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
             // Check if the item being moved actually came FROM the top inventory
             // This logic might be complex and unnecessary if we just cancel all player inv interactions
             event.setCancelled(true);
             // System.out.println("[KJShopPlus DEBUG] Click cancelled: Interaction outside top inventory or MOVE_TO_OTHER."); // Debug (Might be too spammy)
             return;
        }


        // --- Debounce ---
        if (!tapManager.canTap(player.getUniqueId(), configManager.getTapDebounceMs())) {
             event.setCancelled(true);
             // System.out.println("[KJShopPlus DEBUG] Click cancelled: Debounce."); // Debug (Maybe spammy)
             return;
        }

        // --- Handle Click Action ---
        // At this point, we know the click was within the topInventory (our GUI)
        // and passed all preventative checks.

        event.setCancelled(true); // Always cancel the vanilla event in our GUI

        // --- ADD DEBUG LOG HERE ---
        ItemStack itemClicked = clickedInventory.getItem(event.getSlot());
        String pdcAction = ItemBuilder.getPDCAction(itemClicked);
        String pdcValue = ItemBuilder.getPDCValue(itemClicked);
        System.out.println("[KJShopPlus DEBUG] Click processing: Slot=" + event.getSlot()
                + " | Item=" + (itemClicked != null ? itemClicked.getType() : "NULL")
                + " | PDC Action=" + pdcAction + " | PDC Value=" + pdcValue);
        // --- END DEBUG LOG ---

        // Only call handleClick if there's actually an action attached
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

