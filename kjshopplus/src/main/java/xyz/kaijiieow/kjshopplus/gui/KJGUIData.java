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
    private ShopItem tradeItem; // Only used for QUANTITY_SELECTOR type

    // --- เพิ่ม field ใหม่ ---
    private final boolean isBuyMode; // ใช้ทั้งใน SHOP_PAGE และ QUANTITY_SELECTOR
    private final int currentAmount; // ใช้ใน QUANTITY_SELECTOR
    private final int previousPage;  // หน้าที่จากมา (สำหรับปุ่ม cancel/confirm)

    // --- แก้ Constructor ---
    public KJGUIData(InventoryHolder holder, GUITYPE type, String categoryId, int page, boolean isBuyMode, int currentAmount, int previousPage) {
        this.guiType = type;
        this.categoryId = categoryId;
        this.page = page; // page นี้อาจจะหมายถึง 'currentPage' ของ SHOP_PAGE
        this.isBuyMode = isBuyMode;
        this.currentAmount = currentAmount;
        this.previousPage = previousPage; // หน้าที่ควรกลับไป
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

    // --- เพิ่ม Getter ใหม่ ---
    public boolean isBuyMode() {
        return isBuyMode;
    }

    public int getCurrentAmount() {
        return currentAmount;
    }

    public int getPreviousPage() {
        return previousPage;
    }
}

