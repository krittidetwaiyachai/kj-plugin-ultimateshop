package xyz.kaijiieow.kjshopplus.config;

import org.bukkit.Material; // เพิ่ม Import
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.model.MainCategoryMenu;
import xyz.kaijiieow.kjshopplus.config.model.ShopCategory;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ShopManager {

    private final KJShopPlus plugin;
    private MainCategoryMenu mainCategoryMenu;
    private final Map<String, ShopCategory> shopCategories = new HashMap<>();
    private final Map<String, ShopItem> allShopItems = new HashMap<>();
    // --- เพิ่ม Map ใหม่สำหรับค้นหาด้วย Material ---
    private final Map<Material, ShopItem> itemsByMaterial = new HashMap<>();

    public ShopManager(KJShopPlus plugin) {
        this.plugin = plugin;
    }

    public void load() {
        shopCategories.clear();
        allShopItems.clear();
        itemsByMaterial.clear(); // --- เพิ่มบรรทัดนี้ ---
        mainCategoryMenu = null; // Clear old menu

        // 1. Load categories.yml
        File categoriesFile = new File(plugin.getDataFolder(), "categories.yml");
        if (!categoriesFile.exists()) {
            plugin.saveResource("categories.yml", false);
        }
        FileConfiguration categoriesConfig = YamlConfiguration.loadConfiguration(categoriesFile);

        ConfigurationSection mainSection = categoriesConfig.getConfigurationSection("main_menu");
        if (mainSection == null) {
            plugin.getLogger().severe("categories.yml is invalid! Missing 'main_menu:' key.");
            // Prevent further loading if main menu is broken
            return;
        }
        try {
            // Ensure MainCategoryMenu constructor is correct (receives ConfigurationSection)
            this.mainCategoryMenu = new MainCategoryMenu(mainSection);
        } catch (Exception e) {
             plugin.getLogger().severe("Failed to load main_menu from categories.yml!");
             e.printStackTrace();
             return; // Stop if main menu fails
        }


        // 2. Load shop files from /shops/ directory
        File shopsDir = new File(plugin.getDataFolder(), "shops");
        if (!shopsDir.exists()) {
            shopsDir.mkdirs();
            // --- แก้ให้ก๊อปไฟล์ Config ทั้งหมดออกมา ---
            plugin.saveResource("shops/ores.yml", false);
            plugin.saveResource("shops/farming.yml", false);
            plugin.saveResource("shops/blocks.yml", false);
            plugin.saveResource("shops/combat.yml", false);
            plugin.saveResource("shops/mob_drops.yml", false);
            plugin.saveResource("shops/brewing.yml", false);
            plugin.saveResource("shops/redstone.yml", false);
            plugin.saveResource("shops/misc.yml", false);
            // --- จบการแก้ไข ---
        }

        File[] shopFiles = shopsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (shopFiles == null) {
            plugin.getLogger().warning("No shop files found in /shops/ directory.");
            // Don't return here, main menu might still work
        } else {
             for (File shopFile : shopFiles) {
                 try {
                     FileConfiguration shopConfig = YamlConfiguration.loadConfiguration(shopFile);
                     // Use filename without .yml as the category ID
                     String categoryId = shopFile.getName().replace(".yml", "");

                     ConfigurationSection categorySection = shopConfig.getConfigurationSection("category");
                     if (categorySection == null) {
                         plugin.getLogger().severe("File " + shopFile.getName() + " is invalid! Missing 'category:' key at the top.");
                         continue; // Skip this broken file
                     }

                     // Ensure ShopCategory constructor receives (id, config)
                     ShopCategory category = new ShopCategory(categoryId, categorySection);
                     shopCategories.put(categoryId, category);

                     // Add all items from this category to the global map for easy lookup
                     category.getShopItems().forEach(item -> {
                        allShopItems.put(item.getGlobalId(), item);
                        // --- เพิ่มบรรทัดนี้ ---
                        // ใส่ใน Map ใหม่ (ถ้ายังไม่มี) เพื่อให้ /sellall หาง่าย
                        itemsByMaterial.putIfAbsent(item.getMaterial(), item);
                    });

                 } catch (Exception e) {
                     plugin.getLogger().severe("Failed to load shop file: " + shopFile.getName());
                     e.printStackTrace();
                     // Continue loading other files even if one fails
                 }
             }
        }

        plugin.getLogger().info("Loaded " + (mainCategoryMenu != null ? mainCategoryMenu.getCategoryItems().size() : 0) + " main categories from categories.yml.");
        plugin.getLogger().info("Loaded " + shopCategories.size() + " shop categories from /shops/ folder.");
        plugin.getLogger().info("Loaded " + allShopItems.size() + " total shop items.");
    }

    public MainCategoryMenu getMainCategoryMenu() {
        return mainCategoryMenu;
    }

    public ShopCategory getShopCategory(String categoryId) {
        return shopCategories.get(categoryId);
    }

    public ShopItem getShopItem(String globalId) {
        // Global ID is "categoryId:itemId"
        return allShopItems.get(globalId);
    }

    // --- เพิ่มเมธอดใหม่ที่นายเรียกใช้ ---
    public ShopItem getShopItemByMaterial(Material material) {
        return itemsByMaterial.get(material);
    }
    // --- สิ้นสุดเมธอดใหม่ ---

    public Collection<ShopItem> getAllShopItems() {
        // Used by DynamicPriceManager
        return allShopItems.values();
    }
}

