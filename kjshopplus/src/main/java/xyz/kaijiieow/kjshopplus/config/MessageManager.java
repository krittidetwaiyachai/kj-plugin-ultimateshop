package xyz.kaijiieow.kjshopplus.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.io.File;
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
        sendMessage(sender, key, new HashMap<>());
    }

    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = messages.get(key);
        if (message == null) {
            sender.sendMessage(ChatColor.RED + "Error: Message key '" + key + "' not found.");
            return;
        }
        
        message = prefix + message;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
