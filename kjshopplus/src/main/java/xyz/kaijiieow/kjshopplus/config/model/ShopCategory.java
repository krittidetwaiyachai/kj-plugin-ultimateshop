package xyz.kaijiieow.kjshopplus.config.model;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import xyz.kaijiieow.kjshopplus.KJShopPlus; // Import KJShopPlus

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopCategory {

    private final String id;
    private final String title;
    private final int size;
    private final MenuItem fillItem;
    private final Map<String, MenuItem> layoutItems = new HashMap<>();
    private final List<ShopItem> shopItems = new ArrayList<>();

    // Ensure constructor accepts (id, config)
    public ShopCategory(String categoryId, ConfigurationSection config) {
        this.id = categoryId;
        this.title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "&8Category"));
        this.size = config.getInt("size", 54);

        ConfigurationSection fillSection = config.getConfigurationSection("fill-item");
        // Ensure MenuItem constructor receives (id, config)
        this.fillItem = (fillSection != null) ? new MenuItem("fill", fillSection) : null;

        ConfigurationSection layoutSection = config.getConfigurationSection("layout");
        if (layoutSection != null) {
            for (String key : layoutSection.getKeys(false)) {
                ConfigurationSection layoutConfig = layoutSection.getConfigurationSection(key);
                if (layoutConfig != null) {
                    // Ensure MenuItem constructor receives (id, config)
                    layoutItems.put(key, new MenuItem(key, layoutConfig));
                }
            }
        } else {
             // Check if KJShopPlus instance is available before logging
             if (KJShopPlus.getInstance() != null) {
                 KJShopPlus.getInstance().getLogger().warning("Category '" + categoryId + "' is missing 'layout:' section.");
             } else {
                 System.out.println("[KJShopPlus ERROR] ShopCategory loaded before plugin instance! Cannot log warning for missing layout.");
             }
        }


        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
                if (itemConfig != null) {
                    // Ensure ShopItem constructor receives (categoryId, itemId, config)
                    shopItems.add(new ShopItem(this.id, key, itemConfig));
                }
            }
        } else {
             // Check if KJShopPlus instance is available before logging
             if (KJShopPlus.getInstance() != null) {
                 KJShopPlus.getInstance().getLogger().warning("Category '" + categoryId + "' is missing 'items:' section!");
             } else {
                  System.out.println("[KJShopPlus ERROR] ShopCategory loaded before plugin instance! Cannot log warning for missing items.");
             }
        }
    }

    public String getTitle(int page, int totalPages) {
        // Apply color codes AFTER replacing placeholders
        String formattedTitle = this.title
            .replace("{page}", String.valueOf(page))
            .replace("{total_pages}", String.valueOf(totalPages));
        return ChatColor.translateAlternateColorCodes('&', formattedTitle);
    }

    public int getSize() { return size; }
    public MenuItem getFillItem() { return fillItem; }
    public Map<String, MenuItem> getLayoutItems() { return layoutItems; }
    public List<ShopItem> getShopItems() { return shopItems; }
    public String getId() { return id; }
}

