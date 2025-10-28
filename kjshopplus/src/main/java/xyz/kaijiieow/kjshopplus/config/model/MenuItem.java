package xyz.kaijiieow.kjshopplus.config.model;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.gui.util.ItemBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MenuItem {

    private final String id;
    private final boolean enable;
    private final int slot;
    private final Material material;
    private final String name; // Keep raw name
    private final List<String> lore; // Keep raw lore
    private final String action;
    private final String value;

    public MenuItem(String id, ConfigurationSection config) {
        this.id = id;
        this.enable = config.getBoolean("enable", true);
        this.slot = config.getInt("slot", -1);

        String matName = config.getString("material", "STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            KJShopPlus.getInstance().getLogger().warning("Invalid material: '" + matName + "' for menu item '" + id + "'. Using STONE.");
            this.material = Material.STONE;
        } else {
            this.material = mat;
        }

        this.name = config.getString("displayname", " ");
        this.lore = config.getStringList("lore");

        this.action = config.getString("action", null);
        this.value = config.getString("value", null);
    }

    public ItemStack build(Player player, boolean isBedrock) {
        Material mat = material;

        if (isBedrock) {
            mat = KJShopPlus.getInstance().getConfigManager().getBedrockMappedMaterial(material);
        }

        // Colorize lore here, keep original list untouched
        List<String> coloredLore = (lore != null)
                ? lore.stream().map(line -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList())
                : Collections.emptyList();

        ItemBuilder builder = new ItemBuilder(mat)
            // Colorize name here
            .setName(ChatColor.translateAlternateColorCodes('&', name))
            .setLore(coloredLore);

        String finalAction = this.action;
        String finalValue = this.value;

        // Default action for main category items is OPEN_CATEGORY
        if (finalAction == null && id != null && !id.equals("fill")) {
             finalAction = "OPEN_CATEGORY";
        }

        // If value is missing for OPEN_CATEGORY, use the item's ID
        if (finalValue == null && "OPEN_CATEGORY".equals(finalAction)) {
            finalValue = this.id;
        }

        if (finalAction != null) {
            builder.setPDCAction(finalAction);
        }
        if (finalValue != null) {
            builder.setPDCValue(finalValue);
        }

        return builder.build();
    }

    public boolean isEnable() { return enable; }
    public int getSlot() { return slot; }
    public String getAction() { return action; }
    public String getValue() { return value; }
    public String getId() { return id; }

    // --- ADDED GETTERS FOR NAME AND LORE ---
    public String getName() { return name; } // Return raw name
    public List<String> getLore() { return lore; } // Return raw lore list
}

