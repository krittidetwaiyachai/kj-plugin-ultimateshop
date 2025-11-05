package xyz.kaijiieow.kjshopplus.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;

import xyz.kaijiieow.kjshopplus.gui.GUIManager.GUITYPE;


public class KJGUIData implements InventoryHolder {

    private Inventory inventory;
    private final GUITYPE guiType;
    private final String categoryId;
    private final int page;
    private ShopItem tradeItem;

    
    private final boolean isBuyMode;
    private final int currentAmount;
    private final int previousPage;

    
    public KJGUIData(InventoryHolder holder, GUITYPE type, String categoryId, int page, boolean isBuyMode, int currentAmount, int previousPage) {
        this.guiType = type;
        this.categoryId = categoryId;
        this.page = page;
        this.isBuyMode = isBuyMode;
        this.currentAmount = currentAmount;
        this.previousPage = previousPage;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    
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