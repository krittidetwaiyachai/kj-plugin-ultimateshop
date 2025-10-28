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
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.ConfigManager;
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) // Ignore already cancelled events
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();

        // Check if the top inventory has our custom holder
        if (!(topInventory.getHolder() instanceof KJGUIData guiData)) {
            // If the click is IN the top inventory but it's not ours, allow it (e.g., Anvil)
            // If the click is in the PLAYER inventory while a non-KJ GUI is open, allow it
            return;
        }

        // --- FIX: Handle clicks outside the inventory window ---
        if (event.getSlot() == -999) {
            event.setCancelled(true); // Prevent item dropping outside
            return;
        }


        // Now we know the top inventory IS a KJGUIData GUI.

        Player player = (Player) event.getWhoClicked();

        // --- Input Controls ---
        ClickType clickType = event.getClick();
        InventoryAction action = event.getAction();

        // 1. Bedrock Tap = Left Click Check (If enabled)
        boolean isBedrock = plugin.isBedrockPlayer(player.getUniqueId());
        // Bedrock often sends LEFT click for single taps. We might not need special handling if left_tap_only is true.

        // 2. Left Tap Only (Primary control)
        if (configManager.isLeftTapOnly() && clickType != ClickType.LEFT) {
            event.setCancelled(true);
            return;
        }

        // 3. Disable Right Click
        if (configManager.isDisableRightClick() && clickType == ClickType.RIGHT) {
            event.setCancelled(true);
            return;
        }

        // 4. Disable Shift Click (Covers Shift+Left and Shift+Right)
        if (configManager.isDisableShiftClick() && clickType.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        // 5. Disable Number Keys (Hotbar Swap)
        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
             event.setCancelled(true);
             return;
        }
        
        // 6. Prevent moving items INTO the GUI (e.g., clicking item in player inv while GUI is open)
        // If click was in player inventory OR tried to move items around the GUI
        if (clickedInventory != topInventory || action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Exception: Allow Shift+Click OUT of the GUI if needed? No, shop interaction only.
            event.setCancelled(true);
            return;
        }

        // --- Debounce ---
        if (!tapManager.canTap(player.getUniqueId(), configManager.getTapDebounceMs())) {
             event.setCancelled(true);
             return;
        }

        // --- Handle Click Action ---
        // We passed all restrictions, process the click if it was in our GUI
        if (clickedInventory == topInventory) {
             event.setCancelled(true); // Always cancel the vanilla event in our GUI
             guiManager.handleClick(player, event.getSlot(), guiData); // Pass data to GUIManager
        }
        // Clicks in player inventory were already cancelled above if they tried to interact with GUI items
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Prevent dragging items within or into our GUI
        if (event.getInventory().getHolder() instanceof KJGUIData) {
            if (configManager.isDisableInventoryDrag()) {
                event.setCancelled(true);
            }
        }
    }

    // --- Add InventoryCloseEvent handler ---
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof KJGUIData) {
            guiManager.onMenuClose((Player) event.getPlayer());
        }
    }
}

