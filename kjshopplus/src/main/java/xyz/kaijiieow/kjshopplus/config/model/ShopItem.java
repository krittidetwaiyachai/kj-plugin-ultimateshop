package xyz.kaijiieow.kjshopplus.config.model;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.gui.util.ItemBuilder;
import xyz.kaijiieow.kjshopplus.pricing.LoreFormatter;

import java.util.Collections; 
import java.util.List;

public class ShopItem {

    private final String categoryId;
    private final String itemId;
    private final String globalId;
    private final int slot;
    private final Material material;
    private final boolean allowBuy;
    private final boolean allowSell;
    private final String currencyId;
    private final double baseBuyPrice;
    private final double baseSellPrice;

    private final String displayName;
    private final List<String> baseLore;

    private final boolean dynamicEnabled;
    private final double dynamicBuyStep;
    private final double dynamicSellStep;
    private final double dynamicMaxPrice;
    private final double dynamicMinPrice;

    public ShopItem(String categoryId, String itemId, ConfigurationSection config) {
        this.categoryId = categoryId;
        this.itemId = itemId;
        this.globalId = categoryId + ":" + itemId;
        this.slot = config.getInt("slot", -1);

        String matName = config.getString("material", "STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            KJShopPlus.getInstance().getLogger().warning("Invalid material: '" + matName + "' for item '" + globalId + "'. Using STONE.");
            this.material = Material.STONE;
        } else {
            this.material = mat;
        }

        this.allowBuy = config.getBoolean("trade.allow_buy", config.contains("buy")); 
        this.allowSell = config.getBoolean("trade.allow_sell", config.contains("sell")); 
        this.currencyId = config.getString("currency", "vault");
        this.baseBuyPrice = config.getDouble("buy", 0.0);
        this.baseSellPrice = config.getDouble("sell", 0.0);

        ConfigurationSection displaySection = config.getConfigurationSection("display");
        if (displaySection != null) {
            this.displayName = displaySection.getString("name", "&f" + this.material.name());
            this.baseLore = displaySection.getStringList("lore");
        } else {
            this.displayName = "&f" + this.material.name();
            this.baseLore = Collections.emptyList();
             KJShopPlus.getInstance().getLogger().warning("Item '" + globalId + "' is missing 'display:' section in YML.");
        }


        ConfigurationSection dynamicSection = config.getConfigurationSection("dynamic");
        if (dynamicSection != null) {
            this.dynamicEnabled = dynamicSection.getBoolean("enabled", false);
            this.dynamicBuyStep = dynamicSection.getDouble("buy_step_percent", 1.0) / 100.0;
            this.dynamicSellStep = dynamicSection.getDouble("sell_step_percent", 1.0) / 100.0;
            this.dynamicMaxPrice = dynamicSection.getDouble("max_price", 1000000.0);
            this.dynamicMinPrice = dynamicSection.getDouble("min_price", 0.01);
        } else {
            this.dynamicEnabled = false;
            this.dynamicBuyStep = 0;
            this.dynamicSellStep = 0;
            this.dynamicMaxPrice = 0;
            this.dynamicMinPrice = 0;
        }
    }

    public ItemStack buildDisplayItem(Player player, boolean isBedrock) {
        LoreFormatter formatter = KJShopPlus.getInstance().getLoreFormatter();
        Material mat = material;

        if (isBedrock) {
            mat = KJShopPlus.getInstance().getConfigManager().getBedrockMappedMaterial(material);
        }

        return new ItemBuilder(mat)
            .setName(ChatColor.translateAlternateColorCodes('&', this.displayName))
            .setLore(formatter.formatItemLore(this)) 
            .setPDCAction("TRADE_ITEM")
            .setPDCValue(this.globalId)
            .build();
    }

    public String getCategoryId() { return categoryId; }
    public String getItemId() { return itemId; }
    public String getGlobalId() { return globalId; }
    public int getSlot() { return slot; }
    public Material getMaterial() { return material; }
    public boolean isAllowBuy() { return allowBuy; }
    public boolean isAllowSell() { return allowSell; }
    public String getCurrencyId() { return currencyId; }
    public double getBaseBuyPrice() { return baseBuyPrice; }
    public double getBaseSellPrice() { return baseSellPrice; }
    public String getDisplayName() { return displayName; }
    public List<String> getBaseLore() { return baseLore; }
    public boolean isDynamicEnabled() { return dynamicEnabled; }
    public double getDynamicBuyStep() { return dynamicBuyStep; }
    public double getDynamicSellStep() { return dynamicSellStep; }
    public double getDynamicMaxPrice() { return dynamicMaxPrice; }
    public double getDynamicMinPrice() { return dynamicMinPrice; }
}

