package xyz.kaijiieow.kjshopplus.config;

 import org.bukkit.Material;
 import org.bukkit.configuration.ConfigurationSection;
 import org.bukkit.configuration.InvalidConfigurationException;
 import org.bukkit.configuration.file.FileConfiguration;
 import org.bukkit.configuration.file.YamlConfiguration;
 import org.bukkit.inventory.ItemStack;
 import org.bukkit.inventory.meta.ItemMeta; // เพิ่ม import
 import xyz.kaijiieow.kjshopplus.KJShopPlus;
 import xyz.kaijiieow.kjshopplus.config.model.MainCategoryMenu;
 import xyz.kaijiieow.kjshopplus.config.model.ShopCategory;
 import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
 import java.util.ArrayList;

 import java.io.File;
 import java.io.IOException;
 import java.util.ArrayList;
 import java.util.Collection;
 import java.util.Collections;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 import java.util.UUID;

 public class ShopManager {

     private final KJShopPlus plugin;
     private MainCategoryMenu mainCategoryMenu;
     private final Map<String, ShopCategory> shopCategories = new HashMap<>();
     private final Map<String, ShopItem> allShopItems = new HashMap<>();
     private final Map<Material, List<ShopItem>> itemsByMaterial = new HashMap<>();


     public ShopManager(KJShopPlus plugin) {
         this.plugin = plugin;
     }

     public void load() {
         shopCategories.clear();
         allShopItems.clear();
         itemsByMaterial.clear();
         mainCategoryMenu = null;

         loadCategoriesYml();
         loadShopFiles();

         plugin.getLogger().info("Loaded " + (mainCategoryMenu != null ? mainCategoryMenu.getCategoryItems().size() : 0) + " main categories from categories.yml.");
         plugin.getLogger().info("Loaded " + shopCategories.size() + " shop categories from /shops/ folder.");
         plugin.getLogger().info("Loaded " + allShopItems.size() + " total shop items.");
         plugin.getLogger().info("Mapped " + itemsByMaterial.size() + " unique materials for /sellall.");
     }

     private void loadCategoriesYml() {
         File categoriesFile = new File(plugin.getDataFolder(), "categories.yml");
         if (!categoriesFile.exists()) {
             plugin.saveResource("categories.yml", false);
         }
         FileConfiguration categoriesConfig = YamlConfiguration.loadConfiguration(categoriesFile);

         ConfigurationSection mainSection = categoriesConfig.getConfigurationSection("main_menu");
         if (mainSection == null) {
             plugin.getLogger().severe("categories.yml is invalid! Missing 'main_menu:' key.");
             return;
         }
         try {
             this.mainCategoryMenu = new MainCategoryMenu(mainSection);
         } catch (Exception e) {
             plugin.getLogger().severe("Failed to load main_menu from categories.yml!");
             e.printStackTrace();
         }
     }

     private void loadShopFiles() {
         File shopsDir = new File(plugin.getDataFolder(), "shops");
         if (!shopsDir.exists()) {
             shopsDir.mkdirs();
             saveDefaultShopConfigs();
         }

         File[] shopFiles = shopsDir.listFiles((dir, name) -> name.endsWith(".yml"));
         if (shopFiles == null) {
             plugin.getLogger().warning("No shop files found in /shops/ directory.");
             return;
         }

         for (File shopFile : shopFiles) {
             try {
                 FileConfiguration shopConfig = YamlConfiguration.loadConfiguration(shopFile);
                 String categoryId = shopFile.getName().replace(".yml", "");

                 ConfigurationSection categorySection = shopConfig.getConfigurationSection("category");
                 if (categorySection == null) {
                     plugin.getLogger().severe("File " + shopFile.getName() + " is invalid! Missing 'category:' key at the top.");
                     continue;
                 }

                 ShopCategory category = new ShopCategory(categoryId, categorySection);
                 shopCategories.put(categoryId, category);

                 // --- *** START MODIFICATION *** ---
                 // Loop through all pages defined in the category
                 for (int page = 1; page <= category.getTotalPages(); page++) {
                     // Get items for the current page
                     List<ShopItem> itemsOnPage = category.getShopItems(page);
                     // Add each item to the global maps
                     itemsOnPage.forEach(item -> {
                         allShopItems.put(item.getGlobalId(), item);
                         if (item.isAllowSell()) {
                             itemsByMaterial.computeIfAbsent(item.getMaterial(), key -> new ArrayList<>()).add(item);
                         }
                     });
                 }
                 // --- *** END MODIFICATION *** ---

             } catch (Exception e) {
                 plugin.getLogger().severe("Failed to load shop file: " + shopFile.getName());
                 e.printStackTrace();
             }
         }
     }

     private void saveDefaultShopConfigs() {
         String[] defaultShops = {"ores.yml", "farming.yml", "blocks.yml", "combat.yml", "mob_drops.yml", "brewing.yml", "redstone.yml", "misc.yml", "item_custom.yml"}; // เพิ่ม item_custom.yml ถ้ายังไม่มี
         for (String shopFileName : defaultShops) {
              File shopFile = new File(plugin.getDataFolder(), "shops/" + shopFileName);
              if (!shopFile.exists()) {
                 plugin.saveResource("shops/" + shopFileName, false);
              }
         }
     }

     public boolean addItemStackToCategory(String categoryId, ItemStack itemStack) {
         File shopFile = new File(plugin.getDataFolder(), "shops/" + categoryId + ".yml");
         if (!shopFile.exists()) {
             plugin.getLogger().severe("Cannot add item: Shop file not found for category '" + categoryId + "'");
             return false;
         }

         YamlConfiguration shopConfig = YamlConfiguration.loadConfiguration(shopFile);
         ConfigurationSection categorySection = shopConfig.getConfigurationSection("category");
         if (categorySection == null) {
             plugin.getLogger().severe("Cannot add item: Invalid shop file format for category '" + categoryId + "' (missing 'category:' key).");
             return false;
         }

         ConfigurationSection itemsSection = categorySection.getConfigurationSection("items");
         if (itemsSection == null) {
             itemsSection = categorySection.createSection("items");
         }

         String newItemId = itemStack.getType().name().toLowerCase() + "_" + (System.currentTimeMillis() % 10000);
         while (itemsSection.contains(newItemId)) {
             newItemId = itemStack.getType().name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 4);
         }

         ConfigurationSection newItemSection = itemsSection.createSection(newItemId);

         // Set defaults
         newItemSection.set("slot", -1); // Auto-slotting
         newItemSection.set("page", 1); // Default to page 1
         newItemSection.set("currency", "vault");
         newItemSection.set("buy", 0.0); // Default to 0, admin needs to set price
         newItemSection.set("sell", 0.0); // Default to 0
         newItemSection.set("trade.allow_buy", false); // Default to false
         newItemSection.set("trade.allow_sell", false); // Default to false
         newItemSection.set("dynamic.enabled", false);

         // --- เพิ่มส่วนนี้ ---
         // ดึง display name จาก ItemMeta (ถ้ามี)
         if (itemStack.hasItemMeta()) {
             ItemMeta meta = itemStack.getItemMeta();
             if (meta != null && meta.hasDisplayName()) {
                 // เพิ่ม comment และ set display name สำหรับอ้างอิง
                 newItemSection.set("display_name_comment", meta.getDisplayName()); // ใช้ชื่อ key ที่ต่างออกไป
                 newItemSection.setComments("display_name_comment", Collections.singletonList(" This field is just a comment to show the item name, not used by the plugin."));
             }
         }
         // --- จบส่วนที่เพิ่ม ---

         // --- DO NOT SAVE THE 'display' SECTION ---
         // We rely on the 'itemstack' section to store display info (NBT)
         // The user can add a 'display' section MANUALLY later if they want to OVERRIDE the item's NBT.

         // Save the ItemStack itself
         newItemSection.set("itemstack", itemStack);

         try {
             shopConfig.save(shopFile);
             // Reload shop manager after adding item to reflect changes immediately in memory
             // This might cause a brief lag depending on the number of items.
             // Consider just adding the new item to the in-memory maps instead of full reload.
             plugin.getShopManager().load(); // Or a lighter reload method if available
             return true;
         } catch (IOException e) {
             plugin.getLogger().severe("Failed to save shop file: " + shopFile.getName());
             e.printStackTrace();
             return false;
         }
     }


     public MainCategoryMenu getMainCategoryMenu() {
         return mainCategoryMenu;
     }

     public ShopCategory getShopCategory(String categoryId) {
         return shopCategories.get(categoryId);
     }

     public Set<String> getAllCategoryIds() {
         return shopCategories.keySet();
     }


     public ShopItem getShopItem(String globalId) {
         return allShopItems.get(globalId);
     }

     public List<ShopItem> getSellableItems(Material material) {
         return itemsByMaterial.getOrDefault(material, Collections.emptyList());
     }


     public Collection<ShopItem> getAllShopItems() {
         return Collections.unmodifiableCollection(allShopItems.values());
     }
 }