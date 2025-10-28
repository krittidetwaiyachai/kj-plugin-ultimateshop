 package xyz.kaijiieow.kjshopplus.config.model;

 import org.bukkit.ChatColor;
 import org.bukkit.Material;
 import org.bukkit.configuration.ConfigurationSection;
 import org.bukkit.entity.Player;
 import org.bukkit.inventory.ItemStack;
 import org.bukkit.inventory.meta.ItemMeta; // Import ItemMeta
 import xyz.kaijiieow.kjshopplus.KJShopPlus;
 import xyz.kaijiieow.kjshopplus.gui.util.ItemBuilder;
 import xyz.kaijiieow.kjshopplus.pricing.LoreFormatter;
 import java.util.ArrayList;

 import java.util.Collections;
 import java.util.List;
 import java.util.stream.Collectors; // Keep this import

 public class ShopItem {

     private final String categoryId;
     private final String itemId;
     private final String globalId;
     private final int slot;
     private final Material material; // Represents the TYPE, always derived.
     private final boolean allowBuy;
     private final boolean allowSell;
     private final String currencyId;
     private final double baseBuyPrice;
     private final double baseSellPrice;

     // Display overrides from config
     private final String configDisplayName;
     private final List<String> configBaseLore;

     // Custom Item Data
     private final ItemStack customItemStack; // Holds the fully loaded custom item
     private final boolean isCustom;

     // Dynamic Pricing Data
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

         // --- NEW Logic: Prioritize 'itemstack' section ---
         if (config.isConfigurationSection("itemstack") || config.contains("itemstack")) {
             // Attempt to load the ItemStack
             ItemStack loadedStack = config.getItemStack("itemstack");
             if (loadedStack != null && loadedStack.getType() != Material.AIR) {
                 this.customItemStack = loadedStack;
                 this.isCustom = true;
                 this.material = loadedStack.getType(); // Derive material from the loaded stack
             } else {
                 // Invalid itemstack section
                 KJShopPlus.getInstance().getLogger().warning("Invalid or empty 'itemstack' section for item '" + globalId + "'. Using STONE.");
                 this.customItemStack = null;
                 this.isCustom = false;
                 this.material = Material.STONE;
             }
         } else {
             // Fallback to 'material' key (Vanilla item)
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
         // --- END NEW Logic ---

         this.allowBuy = config.getBoolean("trade.allow_buy", config.contains("buy"));
         this.allowSell = config.getBoolean("trade.allow_sell", config.contains("sell"));
         this.currencyId = config.getString("currency", "vault");
         this.baseBuyPrice = config.getDouble("buy", 0.0);
         this.baseSellPrice = config.getDouble("sell", 0.0);

         // Load display overrides EVEN FOR CUSTOM ITEMS
         ConfigurationSection displaySection = config.getConfigurationSection("display");
         if (displaySection != null) {
             this.configDisplayName = displaySection.getString("name"); // Can be null if not set
             this.configBaseLore = displaySection.getStringList("lore");
         } else {
             this.configDisplayName = null;
             this.configBaseLore = Collections.emptyList();
             // Don't warn for custom items missing display, they might rely on internal meta
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

     // Overload for convenience
     public ItemStack buildDisplayItem(Player player, boolean isBedrock) {
         return buildDisplayItem(player, isBedrock, true); // Default to buy mode
     }

     public ItemStack buildDisplayItem(Player player, boolean isBedrock, boolean isBuyMode) {
         LoreFormatter formatter = KJShopPlus.getInstance().getLoreFormatter();
         ItemBuilder builder;

         if (isCustom && customItemStack != null) {
             // Start with a clone of the custom item
             builder = new ItemBuilder(customItemStack.clone());

             // --- Apply display overrides from config if they exist ---
             ItemMeta meta = builder.build().getItemMeta(); // Get meta to check existing values
             if (meta != null) {
                 // Override Name if config has one specified
                 if (configDisplayName != null && !configDisplayName.isBlank()) {
                     builder.setName(ChatColor.translateAlternateColorCodes('&', configDisplayName));
                 } else if (!meta.hasDisplayName()) {
                     // If item has no name and config has no name, generate a default one
                     builder.setName("&f" + this.material.name());
                 }
                 // If config has lore, use it (replacing item's lore)
                 if (configBaseLore != null && !configBaseLore.isEmpty()) {
                      // Note: formatItemLore expects the configBaseLore, not the item's internal lore
                      builder.setLore(formatter.formatItemLore(this)); // Formatter uses configBaseLore internally
                 } else if (!meta.hasLore()) {
                     // If item has no lore and config has no lore, add only price info etc.
                     builder.setLore(formatter.formatItemLore(this)); // Formatter handles empty base lore
                 } else {
                      // Item HAS lore, config DOES NOT. We need to combine item lore + price lore.
                      List<String> combinedLore = new ArrayList<>();
                      if (meta.getLore() != null) {
                           combinedLore.addAll(meta.getLore()); // Add existing lore
                      }
                      // Use formatter, but it will think baseLore is empty. Manually add price lines.
                      List<String> priceLore = formatter.formatItemLore(new ShopItem(categoryId, itemId, createEmptyConfigForFormatter())); // Hacky way?
                      combinedLore.addAll(priceLore); // Add price/dynamic lines
                      builder.setLore(combinedLore);
                 }

             } else {
                  // No meta? Fallback to basic building (shouldn't happen often)
                  builder.setName(configDisplayName != null ? ChatColor.translateAlternateColorCodes('&', configDisplayName) : "&f" + this.material.name());
                  builder.setLore(formatter.formatItemLore(this));
             }
             // --- End display overrides ---

         } else {
             // Vanilla item: build from material
             Material mat = this.material;
             if (isBedrock) {
                 mat = KJShopPlus.getInstance().getConfigManager().getBedrockMappedMaterial(this.material);
             }
             builder = new ItemBuilder(mat);
             // Use config display name, fallback to material name
             builder.setName(configDisplayName != null ? ChatColor.translateAlternateColorCodes('&', configDisplayName) : "&f" + this.material.name());
             // Set combined lore (base lore from config + price info)
             builder.setLore(formatter.formatItemLore(this));
         }

         // Set PDC tags common to both
         builder.setPDCAction(isBuyMode ? "TRADE_ITEM_BUY" : "TRADE_ITEM_SELL")
                 .setPDCValue(this.globalId);

         return builder.build();
     }

     // Helper for the custom item lore combination logic
     private ConfigurationSection createEmptyConfigForFormatter() {
         // Create a dummy section so the formatter doesn't try to read base lore
          org.bukkit.configuration.file.YamlConfiguration tempConfig = new org.bukkit.configuration.file.YamlConfiguration();
          tempConfig.set("material", this.material.name()); // Need material at least
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

     // --- Getters ---
     public String getCategoryId() { return categoryId; }
     public String getItemId() { return itemId; }
     public String getGlobalId() { return globalId; }
     public int getSlot() { return slot; }
     public Material getMaterial() { return material; } // Always returns the Material TYPE
     public boolean isAllowBuy() { return allowBuy; }
     public boolean isAllowSell() { return allowSell; }
     public String getCurrencyId() { return currencyId; }
     public double getBaseBuyPrice() { return baseBuyPrice; }
     public double getBaseSellPrice() { return baseSellPrice; }

     // Returns display name override from config (may be null)
     public String getConfigDisplayName() {
         return configDisplayName;
     }

     // Returns base lore override from config (may be empty list)
     public List<String> getConfigBaseLore() {
         return configBaseLore;
     }

     // Returns the custom ItemStack if loaded, otherwise null
     public ItemStack getCustomItemStack() {
         return customItemStack;
     }

     // Returns true if item was loaded from an 'itemstack' section
     public boolean isCustomItem() {
         return isCustom;
     }

     public boolean isDynamicEnabled() { return dynamicEnabled; }
     public double getDynamicBuyStep() { return dynamicBuyStep; }
     public double getDynamicSellStep() { return dynamicSellStep; }
     public double getDynamicMaxPrice() { return dynamicMaxPrice; }
     public double getDynamicMinPrice() { return dynamicMinPrice; }
 }
