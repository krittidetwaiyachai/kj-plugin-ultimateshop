package xyz.kaijiieow.kjshopplus.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
// Import the GUITYPE enum from GUIManager
import xyz.kaijiieow.kjshopplus.gui.GUIManager.GUITYPE;

/**
 * Custom InventoryHolder to store data about the currently open GUI.
 */
public class KJGUIData implements InventoryHolder {

    private Inventory inventory; // Reference back to the inventory itself
    private final GUITYPE guiType;
    private final String categoryId; // e.g., "ores", "main"
    private final int page;
    private ShopItem tradeItem; // Only used for TRADE_CONFIRM type

    // --- THIS IS THE CORRECT CONSTRUCTOR ---
    public KJGUIData(InventoryHolder holder, GUITYPE type, String categoryId, int page) {
        // We don't actually use the holder argument, but Bukkit needs it for createInventory
        this.guiType = type;
        this.categoryId = categoryId;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    // Setter for the inventory reference, called after Bukkit.createInventory
    public void setInventory(Inventory inv) {
        this.inventory = inv;
    }

    public GUITYPE getGuiType() {
        return guiType;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public int getPage() {
        return page;
    }

    public ShopItem getTradeItem() {
        return tradeItem;
    }

    public void setTradeItem(ShopItem tradeItem) {
        this.tradeItem = tradeItem;
    }
}

