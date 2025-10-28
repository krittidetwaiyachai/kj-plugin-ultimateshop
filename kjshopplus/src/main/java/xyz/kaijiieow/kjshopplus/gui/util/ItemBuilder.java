package xyz.kaijiieow.kjshopplus.gui.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta; // <-- IMPORT MISSING CLASS
import org.bukkit.persistence.PersistentDataType;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.util.List;
import java.util.stream.Collectors;

public class ItemBuilder {

    // These are initialized by KJShopPlus.java on startup
    public static NamespacedKey ACTION_KEY;
    public static NamespacedKey VALUE_KEY;

    private final ItemStack item;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public ItemBuilder(ItemStack item) {
        this.item = (item != null) ? item.clone() : new ItemStack(Material.STONE);
    }

    public ItemBuilder setName(String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && name != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && lore != null && !lore.isEmpty()) {
            List<String> coloredLore = lore.stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder setPDCAction(String action) {
        if (action == null || ACTION_KEY == null) return this;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setPDCValue(String value) {
        if (value == null || VALUE_KEY == null) return this;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(VALUE_KEY, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder setPDCData(String action, String value) {
        setPDCAction(action);
        setPDCValue(value);
        return this;
    }

    public ItemBuilder addGlow() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        return item;
    }

    // --- Static PDC Getters ---
    public static String getPDCAction(ItemStack item) {
        if (item == null || ACTION_KEY == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(ACTION_KEY, PersistentDataType.STRING)) {
            return null;
        }
        return meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
    }

    public static String getPDCValue(ItemStack item) {
        if (item == null || VALUE_KEY == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(VALUE_KEY, PersistentDataType.STRING)) {
            return null;
        }
        return meta.getPersistentDataContainer().get(VALUE_KEY, PersistentDataType.STRING);
    }

    public static boolean hasPDCAction(ItemStack item) {
        if (item == null || ACTION_KEY == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(ACTION_KEY, PersistentDataType.STRING);
    }
}

