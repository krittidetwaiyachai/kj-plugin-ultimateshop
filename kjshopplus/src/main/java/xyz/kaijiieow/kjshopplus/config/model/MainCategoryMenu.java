package xyz.kaijiieow.kjshopplus.config.model;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.util.HashMap;
import java.util.Map;

public class MainCategoryMenu {

    private final String title;
    private final int size;
    private final MenuItem fillItem;
    private final MenuItem closeButton;
    private final MenuItem playerInfoItem;
    private final Map<String, MenuItem> categoryItems = new HashMap<>();

    public MainCategoryMenu(ConfigurationSection config) {
        if (config == null) {
            this.title = "&cError Loading Menu";
            this.size = 9;
            this.fillItem = null;
            this.closeButton = null;
            this.playerInfoItem = null;
             // Use System.out.println for early errors before logger might be ready
             System.out.println("[KJShopPlus ERROR] MainCategoryMenu constructor received null config!");
             return;
        }

        this.title = config.getString("title", "&eServer Shop");
        this.size = config.getInt("size", 54);

        ConfigurationSection fillSection = config.getConfigurationSection("fill-item");
        this.fillItem = (fillSection != null) ? new MenuItem("fill", fillSection) : null;

        ConfigurationSection closeSection = config.getConfigurationSection("close_button");
        this.closeButton = (closeSection != null && closeSection.getBoolean("enable", true))
                ? new MenuItem("close", closeSection)
                : null;

        ConfigurationSection playerInfoSection = config.getConfigurationSection("player_info_item");
        this.playerInfoItem = (playerInfoSection != null && playerInfoSection.getBoolean("enable", true))
                ? new MenuItem("player_info", playerInfoSection)
                : null;


        // --- THIS IS THE CRITICAL PART ---
        // Ensure it reads "categories" (plural with 's')
        ConfigurationSection itemsSection = config.getConfigurationSection("categories");
        if (itemsSection != null) {
            int loadedCount = 0; // Debug counter
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
                if (itemConfig != null && itemConfig.getBoolean("enable", true)) {
                    // Create the MenuItem using (id, config) constructor
                    MenuItem menuItem = new MenuItem(key, itemConfig);
                    categoryItems.put(key, menuItem);
                    loadedCount++;
                     // Debug log for each loaded category item
                     System.out.println("[KJShopPlus DEBUG] Loaded category item '" + key + "' for main menu.");
                } else if (itemConfig == null) {
                    System.out.println("[KJShopPlus ERROR] Invalid config section for category item '" + key + "' in categories.yml");
                }
            }
             System.out.println("[KJShopPlus DEBUG] Finished loading main menu category items. Count: " + loadedCount);
        } else {
             // Log error if 'categories' section is completely missing
             System.out.println("[KJShopPlus ERROR] main_menu in categories.yml is missing 'categories:' section!");
        }
    }

    public String getTitle() { return title; }
    public int getSize() { return size; }
    public MenuItem getFillItem() { return fillItem; }
    public MenuItem getCloseButton() { return closeButton; }
    public MenuItem getPlayerInfoItem() { return playerInfoItem; }
    public Map<String, MenuItem> getCategoryItems() { return categoryItems; }
}

