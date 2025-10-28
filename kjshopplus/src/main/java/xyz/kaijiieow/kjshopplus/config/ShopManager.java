 package xyz.kaijiieow.kjshopplus.config;

 import org.bukkit.Material;
 import org.bukkit.configuration.ConfigurationSection;
 import org.bukkit.configuration.InvalidConfigurationException; // Import
 import org.bukkit.configuration.file.FileConfiguration;
 import org.bukkit.configuration.file.YamlConfiguration;
 import org.bukkit.inventory.ItemStack; // Import ItemStack
 import xyz.kaijiieow.kjshopplus.KJShopPlus;
 import xyz.kaijiieow.kjshopplus.config.model.MainCategoryMenu;
 import xyz.kaijiieow.kjshopplus.config.model.ShopCategory;
 import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
 import java.util.ArrayList;

 import java.io.File;
 import java.io.IOException; // Import
 import java.util.ArrayList;
 import java.util.Collection;
 import java.util.Collections;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Set; // Import Set
 import java.util.UUID; // Import UUID

 public class ShopManager {

     private final KJShopPlus plugin;
     private MainCategoryMenu mainCategoryMenu;
     private final Map<String, ShopCategory> shopCategories = new HashMap<>();
     private final Map<String, ShopItem> allShopItems = new HashMap<>();
     private final Map<Material, List<ShopItem>> itemsByMaterial = new HashMap<>(); // Used for /sellall


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

                 category.getShopItems().forEach(item -> {
                     allShopItems.put(item.getGlobalId(), item);
                     if (item.isAllowSell()) {
                         // Use the item's actual material (could be from custom item)
                         itemsByMaterial.computeIfAbsent(item.getMaterial(), key -> new ArrayList<>()).add(item);
                     }
                 });

             } catch (Exception e) {
                 plugin.getLogger().severe("Failed to load shop file: " + shopFile.getName());
                 e.printStackTrace();
             }
         }
     }

     private void saveDefaultShopConfigs() {
         String[] defaultShops = {"ores.yml", "farming.yml", "blocks.yml", "combat.yml", "mob_drops.yml", "brewing.yml", "redstone.yml", "misc.yml"};
         for (String shopFileName : defaultShops) {
             plugin.saveResource("shops/" + shopFileName, false);
         }
     }

     // --- NEW METHOD ---
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

         // Generate a unique ID (simple approach, might collide rarely but ok for admin command)
         String newItemId = itemStack.getType().name().toLowerCase() + "_" + (System.currentTimeMillis() % 10000);
         // Ensure uniqueness within this category
         while (itemsSection.contains(newItemId)) {
             newItemId = itemStack.getType().name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 4);
         }

         ConfigurationSection newItemSection = itemsSection.createSection(newItemId);

         // Set defaults
         newItemSection.set("slot", -1);
         newItemSection.set("currency", "vault");
         newItemSection.set("buy", 0.0); // Default to not buyable
         newItemSection.set("sell", 0.0); // Default to not sellable
         newItemSection.set("trade.allow_buy", false);
         newItemSection.set("trade.allow_sell", false);
         newItemSection.set("dynamic.enabled", false);
         // Add display name and lore from the item itself, if they exist
         if (itemStack.hasItemMeta()) {
             if (itemStack.getItemMeta().hasDisplayName()) {
                  newItemSection.set("display.name", itemStack.getItemMeta().getDisplayName()); // Note: This saves with section symbols (§)
             }
              if (itemStack.getItemMeta().hasLore()) {
                  newItemSection.set("display.lore", itemStack.getItemMeta().getLore()); // Note: This saves with section symbols (§)
             }
         }


         // **Crucially, save the ItemStack itself**
         newItemSection.set("itemstack", itemStack); // Bukkit handles serialization here

         try {
             shopConfig.save(shopFile);
             // Optionally, reload just this category or the whole shop manager
             // For simplicity with an admin command, maybe just inform the admin to reload.
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

     // --- NEW ---
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
