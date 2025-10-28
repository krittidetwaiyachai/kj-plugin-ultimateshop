 package xyz.kaijiieow.kjshopplus.config.model;

 import org.bukkit.ChatColor;
 import org.bukkit.Material;
 import org.bukkit.configuration.ConfigurationSection;
 import org.bukkit.entity.Player;
 import org.bukkit.inventory.ItemStack;
 import org.bukkit.inventory.meta.ItemMeta;
 import xyz.kaijiieow.kjshopplus.KJShopPlus;
 import xyz.kaijiieow.kjshopplus.gui.util.ItemBuilder;
 import xyz.kaijiieow.kjshopplus.pricing.LoreFormatter;
 import java.util.ArrayList; // *** ADDED IMPORT ***
 import java.util.Collections;
 import java.util.List;
 import java.util.stream.Collectors;

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

     private final String configDisplayName;
     private final List<String> configBaseLore;

     private final ItemStack customItemStack;
     private final boolean isCustom;

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

         if (config.isConfigurationSection("itemstack") || config.contains("itemstack")) {
             ItemStack loadedStack = config.getItemStack("itemstack");
             if (loadedStack != null && loadedStack.getType() != Material.AIR) {
                 this.customItemStack = loadedStack;
                 this.isCustom = true;
                 this.material = loadedStack.getType();
             } else {
                 KJShopPlus.getInstance().getLogger().warning("Invalid or empty 'itemstack' section for item '" + globalId + "'. Using STONE.");
                 this.customItemStack = null;
                 this.isCustom = false;
                 this.material = Material.STONE;
             }
         } else {
             this.customItemStack = null;
             this.isCustom = false;
             String matName = config.getString("material", "STONE");
             Material mat = Material.matchMaterial(matName);
             if (mat == null) {
                 KJShopPlus.getInstance().getLogger().warning("Invalid material: '" + matName + "' for item '" + globalId + "'. Using STONE.");
                 this.material = Material.STONE;
             } else {
                 this.material = mat;
             }
         }

         this.allowBuy = config.getBoolean("trade.allow_buy", config.contains("buy"));
         this.allowSell = config.getBoolean("trade.allow_sell", config.contains("sell"));
         this.currencyId = config.getString("currency", "vault");
         this.baseBuyPrice = config.getDouble("buy", 0.0);
         this.baseSellPrice = config.getDouble("sell", 0.0);

         ConfigurationSection displaySection = config.getConfigurationSection("display");
         if (displaySection != null) {
             this.configDisplayName = displaySection.getString("name");
             this.configBaseLore = displaySection.getStringList("lore");
         } else {
             this.configDisplayName = null;
             this.configBaseLore = Collections.emptyList();
             if (!this.isCustom) {
                 KJShopPlus.getInstance().getLogger().warning("Item '" + globalId + "' is missing 'display:' section in YML.");
             }
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
         return buildDisplayItem(player, isBedrock, true);
     }

     public ItemStack buildDisplayItem(Player player, boolean isBedrock, boolean isBuyMode) {
         LoreFormatter formatter = KJShopPlus.getInstance().getLoreFormatter();
         ItemBuilder builder;

         if (isCustom && customItemStack != null) {
             // Start with a clone of the custom item
             builder = new ItemBuilder(customItemStack.clone());
             ItemMeta meta = builder.build().getItemMeta(); // Get meta to check existing values
             
             List<String> finalLore = new ArrayList<>();

             // 1. Check for lore OVERRIDE from display.lore
             if (configBaseLore != null && !configBaseLore.isEmpty()) {
                 // If display.lore exists, it REPLACES the item's lore
                 finalLore.addAll(configBaseLore.stream()
                     .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                     .collect(Collectors.toList()));
             } 
             // 2. ELSE, use the item's internal lore
             else if (meta != null && meta.hasLore()) {
                 finalLore.addAll(meta.getLore());
             }

             // 3. Add price lore (price/dynamic)
             finalLore.addAll(formatter.getPriceLore(this)); // Use new method

             // 4. Set the combined lore
             builder.setLore(finalLore);

             // 5. Handle Name (Prioritize config override)
             if (configDisplayName != null && !configDisplayName.isBlank()) {
                 builder.setName(ChatColor.translateAlternateColorCodes('&', configDisplayName));
             }
             // If configDisplayName is null, the builder automatically has the item's internal name from the clone.

         } else {
             // Vanilla item: build from material
             Material mat = this.material;
             if (isBedrock) {
                 mat = KJShopPlus.getInstance().getConfigManager().getBedrockMappedMaterial(this.material);
             }
             builder = new ItemBuilder(mat);
             // Use config display name, fallback to material name
             String displayName = (configDisplayName != null) ? configDisplayName : "&f" + this.material.name();
             builder.setName(ChatColor.translateAlternateColorCodes('&', displayName));
             
             // Set combined lore (base lore from config + price info)
             builder.setLore(formatter.formatItemLore(this)); // Use old method that includes base lore
         }

         builder.setPDCAction(isBuyMode ? "TRADE_ITEM_BUY" : "TRADE_ITEM_SELL")
                .setPDCValue(this.globalId);

         return builder.build();
     }

     // This helper is no longer needed because LoreFormatter is split
     @Deprecated
     private ConfigurationSection createEmptyConfigForFormatter() {
          org.bukkit.configuration.file.YamlConfiguration tempConfig = new org.bukkit.configuration.file.YamlConfiguration();
          tempConfig.set("material", this.material.name()); 
          tempConfig.set("currency", this.currencyId);
          tempConfig.set("buy", this.baseBuyPrice);
          tempConfig.set("sell", this.baseSellPrice);
          tempConfig.set("trade.allow_buy", this.allowBuy);
          tempConfig.set("trade.allow_sell", this.allowSell);
          if(this.dynamicEnabled) {
              tempConfig.set("dynamic.enabled", true);
          }
         return tempConfig;
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

     public String getConfigDisplayName() {
         return configDisplayName;
     }
     public List<String> getConfigBaseLore() {
         return configBaseLore;
     }
     
     // --- ADDED DELEGATE METHODS ---
     public String getDisplayName() {
         return getConfigDisplayName();
     }
     public List<String> getBaseLore() {
         return getConfigBaseLore();
     }
     // --- END DELEGATE METHODS ---

     public ItemStack getCustomItemStack() {
         return customItemStack;
     }
     public boolean isCustomItem() {
         return isCustom;
     }
     public boolean isDynamicEnabled() { return dynamicEnabled; }
     public double getDynamicBuyStep() { return dynamicBuyStep; }
     public double getDynamicSellStep() { return dynamicSellStep; }
     public double getDynamicMaxPrice() { return dynamicMaxPrice; }
     public double getDynamicMinPrice() { return dynamicMinPrice; }
 }

