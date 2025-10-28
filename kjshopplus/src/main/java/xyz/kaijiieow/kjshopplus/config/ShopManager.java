package xyz.kaijiieow.kjshopplus.config;

import org.bukkit.Material; // --- เพิ่ม Import ---
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.model.MainCategoryMenu;
import xyz.kaijiieow.kjshopplus.config.model.ShopCategory;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;

import java.io.File;
// ... (imports อื่นๆ) ...
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // 1. Load categories.yml
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
             return; 
        }


        // 2. Load shop files from /shops/ directory
        File shopsDir = new File(plugin.getDataFolder(), "shops");
        if (!shopsDir.exists()) {
            shopsDir.mkdirs();
            // --- เพิ่มการ save-all-shops (แก้จากรอบที่แล้ว) ---
            saveDefaultShopConfigs(); 
        }

        File[] shopFiles = shopsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (shopFiles == null) {
            plugin.getLogger().warning("No shop files found in /shops/ directory.");
        } else {
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

                     // Add all items from this category to the global map
                     category.getShopItems().forEach(item -> {
                         allShopItems.put(item.getGlobalId(), item);
                         if (item.isAllowSell()) {
                             itemsByMaterial.computeIfAbsent(item.getMaterial(), key -> new ArrayList<>()).add(item);
                         }
                     });

                 } catch (Exception e) {
                     plugin.getLogger().severe("Failed to load shop file: " + shopFile.getName());
                     e.printStackTrace();
                 }
             }
        }

        plugin.getLogger().info("Loaded " + (mainCategoryMenu != null ? mainCategoryMenu.getCategoryItems().size() : 0) + " main categories from categories.yml.");
        plugin.getLogger().info("Loaded " + shopCategories.size() + " shop categories from /shops/ folder.");
        plugin.getLogger().info("Loaded " + allShopItems.size() + " total shop items.");
        plugin.getLogger().info("Mapped " + itemsByMaterial.size() + " unique materials for /sellall.");
    }

    // --- เมธอดใหม่สำหรับ save-all-shops ---
    private void saveDefaultShopConfigs() {
        plugin.saveResource("shops/ores.yml", false);
        plugin.saveResource("shops/farming.yml", false);
        plugin.saveResource("shops/blocks.yml", false);
        plugin.saveResource("shops/combat.yml", false);
        plugin.saveResource("shops/mob_drops.yml", false);
        plugin.saveResource("shops/brewing.yml", false);
        plugin.saveResource("shops/redstone.yml", false);
        plugin.saveResource("shops/misc.yml", false);
    }
    // --- สิ้นสุด ---

    public MainCategoryMenu getMainCategoryMenu() {
        return mainCategoryMenu;
    }

    public ShopCategory getShopCategory(String categoryId) {
        return shopCategories.get(categoryId);
    }

    public ShopItem getShopItem(String globalId) {
        return allShopItems.get(globalId);
    }

    public List<ShopItem> getSellableItems(Material material) {
        return itemsByMaterial.getOrDefault(material, Collections.emptyList());
    }


    public Collection<ShopItem> getAllShopItems() {
        return allShopItems.values();
    }
}
