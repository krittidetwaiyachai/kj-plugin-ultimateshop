package xyz.kaijiieow.kjshopplus.gui.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import xyz.kaijiieow.kjshopplus.KJShopPlus; // Ensure KJShopPlus is imported

import java.util.Collections; // Import Collections
import java.util.List;
import java.util.stream.Collectors;

public class ItemBuilder {

    // --- ประกาศซ้ำซ้อนถูกลบออกแล้ว ---

    private final ItemStack item;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material != null ? material : Material.STONE);
    }

    public ItemBuilder(ItemStack item) {
        this.item = (item != null) ? item.clone() : new ItemStack(Material.STONE);
    }

    public ItemBuilder setName(String name) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && name != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                item.setItemMeta(meta);
            }
        } catch (Exception e) {
             System.err.println("[KJShopPlus ERROR] Failed to set item name for " + item.getType() + ": " + e.getMessage());
        }
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && lore != null && !lore.isEmpty()) {
                List<String> coloredLore = lore.stream()
                        .map(line -> line != null ? ChatColor.translateAlternateColorCodes('&', line) : "")
                        .collect(Collectors.toList());
                meta.setLore(coloredLore);
                item.setItemMeta(meta);
            } else if (meta != null) {
                 meta.setLore(Collections.emptyList());
                 item.setItemMeta(meta);
            }
        } catch (Exception e) {
            System.err.println("[KJShopPlus ERROR] Failed to set item lore for " + item.getType() + ": " + e.getMessage());
        }
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        if (amount > 0) {
             int maxStack = (item.getType() != null) ? item.getType().getMaxStackSize() : 64;
             item.setAmount(Math.min(amount, maxStack));
        } else {
             item.setAmount(1);
        }
        return this;
    }

    public ItemBuilder setPDCAction(String action) {
        // --- แก้ไปใช้ Key จาก KJShopPlus ---
        if (action == null || KJShopPlus.PDC_ACTION_KEY == null) return this;
         try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(KJShopPlus.PDC_ACTION_KEY, PersistentDataType.STRING, action);
                item.setItemMeta(meta);
            }
         } catch (Exception e) {
             System.err.println("[KJShopPlus ERROR] Failed to set PDC Action for " + item.getType() + ": " + e.getMessage());
         }
        return this;
    }

    public ItemBuilder setPDCValue(String value) {
        // --- แก้ไปใช้ Key จาก KJShopPlus ---
        if (value == null || KJShopPlus.PDC_VALUE_KEY == null) return this;
         try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(KJShopPlus.PDC_VALUE_KEY, PersistentDataType.STRING, value);
                item.setItemMeta(meta);
            }
         } catch (Exception e) {
             System.err.println("[KJShopPlus ERROR] Failed to set PDC Value for " + item.getType() + ": " + e.getMessage());
         }
        return this;
    }

    public ItemBuilder setPDCData(String action, String value) {
        setPDCAction(action);
        setPDCValue(value);
        return this;
    }

    public ItemBuilder addGlow() {
         try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (Enchantment.UNBREAKING.canEnchantItem(item)) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);
                }
            }
         } catch (Exception e) {
             System.err.println("[KJShopPlus ERROR] Failed to add glow for " + item.getType() + ": " + e.getMessage());
         }
        return this;
    }

    public ItemStack build() {
        return item.clone();
    }

    // --- Static PDC Getters ---
    public static String getPDCAction(ItemStack item) {
        // --- แก้ไปใช้ Key จาก KJShopPlus ---
        if (item == null || item.getType() == Material.AIR || KJShopPlus.PDC_ACTION_KEY == null) return null;
         try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.getPersistentDataContainer().has(KJShopPlus.PDC_ACTION_KEY, PersistentDataType.STRING)) {
                return null;
            }
            return meta.getPersistentDataContainer().get(KJShopPlus.PDC_ACTION_KEY, PersistentDataType.STRING);
         } catch (Exception e) {
             System.err.println("[KJShopPlus ERROR] Failed to get PDC Action for " + item.getType() + ": " + e.getMessage());
             return null;
         }
    }

    public static String getPDCValue(ItemStack item) {
        // --- แก้ไปใช้ Key จาก KJShopPlus ---
        if (item == null || item.getType() == Material.AIR || KJShopPlus.PDC_VALUE_KEY == null) return null;
         try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.getPersistentDataContainer().has(KJShopPlus.PDC_VALUE_KEY, PersistentDataType.STRING)) {
                return null;
            }
            return meta.getPersistentDataContainer().get(KJShopPlus.PDC_VALUE_KEY, PersistentDataType.STRING);
         } catch (Exception e) {
             System.err.println("[KJShopPlus ERROR] Failed to get PDC Value for " + item.getType() + ": " + e.getMessage());
             return null;
         }
    }

    public static boolean hasPDCAction(ItemStack item) {
        // --- แก้ไปใช้ Key จาก KJShopPlus ---
        if (item == null || item.getType() == Material.AIR || KJShopPlus.PDC_ACTION_KEY == null) return false;
         try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;
            return meta.getPersistentDataContainer().has(KJShopPlus.PDC_ACTION_KEY, PersistentDataType.STRING);
         } catch (Exception e) {
             System.err.println("[KJShopPlus ERROR] Failed check PDC Action for " + item.getType() + ": " + e.getMessage());
             return false;
         }
    }
}

