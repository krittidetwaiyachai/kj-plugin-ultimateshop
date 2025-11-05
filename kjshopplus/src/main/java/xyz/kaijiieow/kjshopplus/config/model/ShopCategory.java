package xyz.kaijiieow.kjshopplus.config.model;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopCategory {

    private final String id;
    private final String title;
    private final int size;
    private final MenuItem fillItem;
    private final Map<String, MenuItem> layoutItems = new HashMap<>();
    
    
    private final Map<Integer, List<ShopItem>> itemsByPage = new HashMap<>();
    private int maxPage = 1;
    

    
    public ShopCategory(String categoryId, ConfigurationSection config) {
        this.id = categoryId;
        this.title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "&8Category"));
        this.size = config.getInt("size", 54);

        ConfigurationSection fillSection = config.getConfigurationSection("fill-item");
        
        this.fillItem = (fillSection != null) ? new MenuItem("fill", fillSection) : null;

        ConfigurationSection layoutSection = config.getConfigurationSection("layout");
        if (layoutSection != null) {
            for (String key : layoutSection.getKeys(false)) {
                ConfigurationSection layoutConfig = layoutSection.getConfigurationSection(key);
                if (layoutConfig != null) {
                    
                    layoutItems.put(key, new MenuItem(key, layoutConfig));
                }
            }
        } else {
             
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
                    
                    
                    
                    
                    
                    ShopItem newItem = new ShopItem(this.id, key, itemConfig);
                    int itemPage = newItem.getPage();
                    itemsByPage.computeIfAbsent(itemPage, k -> new ArrayList<>()).add(newItem);
                    if (itemPage > maxPage) {
                        maxPage = itemPage;
                    }
                    
                }
            }
        } else {
             
             if (KJShopPlus.getInstance() != null) {
                 KJShopPlus.getInstance().getLogger().warning("Category '" + categoryId + "' is missing 'items:' section!");
             } else {
                  System.out.println("[KJShopPlus ERROR] ShopCategory loaded before plugin instance! Cannot log warning for missing items.");
             }
        }
    }

    public String getTitle(int page, int totalPages) {
        
        String formattedTitle = this.title
            .replace("{page}", String.valueOf(page))
            .replace("{total_pages}", String.valueOf(totalPages));
        return ChatColor.translateAlternateColorCodes('&', formattedTitle);
    }

    public int getSize() { return size; }
    public MenuItem getFillItem() { return fillItem; }
    public Map<String, MenuItem> getLayoutItems() { return layoutItems; }
    public String getId() { return id; }

    
    
    
    
    public List<ShopItem> getShopItems(int page) {
        return itemsByPage.getOrDefault(page, Collections.emptyList());
    }
        
    public int getTotalPages() {
        return maxPage;
    }
    
}