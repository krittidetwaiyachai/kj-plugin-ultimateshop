package xyz.kaijiieow.kjshopplus.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.io.File;
import java.util.Collections; // <-- IMPORT
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final KJShopPlus plugin;
    private FileConfiguration messagesConfig;
    private String prefix;
    private final Map<String, String> messages = new HashMap<>();

    public MessageManager(KJShopPlus plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        prefix = messagesConfig.getString("prefix", "&e[KJShopPlus] ");
        messages.clear();
        for (String key : messagesConfig.getKeys(false)) {
            if (!key.equals("prefix")) {
                messages.put(key, messagesConfig.getString(key));
            }
        }
    }

    public void sendMessage(CommandSender sender, String key) {
        sendMessage(sender, key, Collections.emptyMap());
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        return getMessageInternal(key, placeholders, null);
    }

    public String getMessage(String key) {
        return getMessageInternal(key, Collections.emptyMap(), null);
    }

    public String getMessage(String key, String defaultValue) {
        return getMessageInternal(key, Collections.emptyMap(), defaultValue);
    }

    public String getMessage(String key, Map<String, String> placeholders, String defaultValue) {
        return getMessageInternal(key, placeholders, defaultValue);
    }

    private String getMessageInternal(String key, Map<String, String> placeholders, String defaultValue) {
        String message = messages.get(key);
        if (message == null) {
            message = defaultValue;
        }
        if (message == null) {
            return ChatColor.RED + "Missing msg: " + key;
        }

        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }


    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        // --- MODIFIED ---
        String message = messages.get(key);
        if (message == null) {
            sender.sendMessage(ChatColor.RED + "Error: Message key '" + key + "' not found.");
            return;
        }
        
        // Use the new getMessage method to format, then add prefix
        String formattedMessage = getMessage(key, placeholders); // This will re-color
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + formattedMessage));
        // --- END MODIFIED ---
    }
}

