package xyz.kaijiieow.kjshopplus.config.model;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
// --- ลบ Import ---
// import org.bukkit.inventory.meta.ItemMeta;
// --- จบ ---
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.gui.util.ItemBuilder;
import xyz.kaijiieow.kjshopplus.pricing.LoreFormatter;

import java.util.Collections; 
import java.util.List;
// --- ลบ Import ---
// import java.util.Map;
// import java.util.stream.Collectors;
// --- จบ ---

public class ShopItem {

    private final String categoryId;
    private final String itemId;
    private final String globalId;
    private final int slot;
    private final Material material; // <--- กลับมาใช้อันนี้
    private final boolean allowBuy;
    private final boolean allowSell;
    private final String currencyId;
    private final double baseBuyPrice;
    private final double baseSellPrice;

    private final String displayName; // จาก config (สำหรับของ Vanilla)
    private final List<String> baseLore; // จาก config (สำหรับของ Vanilla)

    // --- ลบ field นี้ ---
    // private final ItemStack customItemStack; 
    // private final boolean isCustom;
    // --- จบ ---

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

        // --- Logic ย้อนกลับไปเป็นแบบเดิม ---
        // if (config.isConfigurationSection("itemstack")) { ... } // <--- ลบ Block นี้ทั้งหมด
        
        // นี่คือไอเทม Vanilla (แบบเดิม)
        // this.customItemStack = null; // ลบบรรทัดนี้
        // this.isCustom = false; // ลบบรรทัดนี้
        String matName = config.getString("material", "STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            KJShopPlus.getInstance().getLogger().warning("Invalid material: '" + matName + "' for item '" + globalId + "'. Using STONE.");
            this.material = Material.STONE;
        } else {
            this.material = mat;
        }
        // --- จบ Logic ย้อนกลับ ---


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
             // if (!this.isCustom) { // <--- แก้ไข/ลบ
                 KJShopPlus.getInstance().getLogger().warning("Item '" + globalId + "' is missing 'display:' section in YML.");
             // }
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
        
        ItemBuilder builder;

        // --- Logic ย้อนกลับไปเป็นแบบเดิม ---
        // if (isCustom) { ... } // <--- ลบ Block นี้
        
        // ถ้าเป็น Vanilla Item สร้างจาก Material
        Material mat = material;
        if (isBedrock) {
            mat = KJShopPlus.getInstance().getConfigManager().getBedrockMappedMaterial(material);
        }
        builder = new ItemBuilder(mat);
        // ตั้งชื่อจาก config
        builder.setName(ChatColor.translateAlternateColorCodes('&', this.displayName));
        // --- จบ ---

        // ทั้ง Custom และ Vanilla จะถูก setLore ใหม่ (ที่รวมราคาแล้ว)
        builder.setLore(formatter.formatItemLore(this)) 
            .setPDCAction("TRADE_ITEM")
            .setPDCValue(this.globalId);
        
        // if (isCustom) { builder.addGlow(); } // <--- ลบบรรทัดนี้

        return builder.build();
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

    // --- แก้ไข Getter 2 อันนี้ ---
    public String getDisplayName() {
        // if (isCustom && ...) { ... } // <--- ลบ Block นี้
        return this.displayName; // คืนค่าจาก config (ยังไม่มีสี)
    }
    public List<String> getBaseLore() {
        // if (isCustom && ...) { ... } // <--- ลบ Block นี้
        return this.baseLore; // คืนค่าจาก config (ยังไม่มีสี)
    }
    // --- จบ ---

    // --- ลบ Getter นี้ ---
    // public ItemStack getCustomItemStack() { return customItemStack; }
    // public boolean isCustomItem() { return isCustom; }
    // --- จบ ---

    public boolean isDynamicEnabled() { return dynamicEnabled; }
    public double getDynamicBuyStep() { return dynamicBuyStep; }
    public double getDynamicSellStep() { return dynamicSellStep; }
    public double getDynamicMaxPrice() { return dynamicMaxPrice; }
    public double getDynamicMinPrice() { return dynamicMinPrice; }
}

