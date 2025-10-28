package xyz.kaijiieow.kjshopplus.services;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
import xyz.kaijiieow.kjshopplus.economy.PriceUtil;
// --- เพิ่ม Imports ---
import org.bukkit.ChatColor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
// --- จบ ---

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone; 

public class DiscordWebhookService {

    private final KJShopPlus plugin;
    // --- แก้ไข: เพิ่ม Default value และ Formatter ---
    private String username = "KJShopPlus Bot";
    private String avatarUrl = "";
    private final SimpleDateFormat fileTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    // --- จบ ---

    public DiscordWebhookService(KJShopPlus plugin) {
        this.plugin = plugin;
        // --- ลบ loadConfig() ออกจากที่นี่ ---
        // loadConfig();
    }

    // Method to reload webhook username/avatar if config changes
    public void loadConfig() {
        // --- เพิ่ม Safety checks ---
        if (plugin.getConfigManager() == null) {
            plugin.getLogger().warning("DiscordWebhookService#loadConfig called before ConfigManager was initialized!");
            return;
        }
        // --- จบ ---
        this.username = plugin.getConfigManager().getWebhookUsername();
        this.avatarUrl = plugin.getConfigManager().getWebhookAvatarUrl();

        // --- เพิ่ม Fallback ---
        if (this.username == null || this.username.trim().isEmpty()) {
            plugin.getLogger().warning("Discord webhook username is empty in config.yml! Using default 'KJShopPlus Bot'.");
            this.username = "KJShopPlus Bot";
        }
        // --- จบ ---
    }

    // --- เมธอดใหม่สำหรับ Log ลงไฟล์ ---
    private void logToFile(String message) {
        String timestamp = fileTimestampFormatter.format(new Date());
        // เอา Code สีออกจาก message ก่อนเซฟ
        String strippedMessage = ChatColor.stripColor(message);
        String logEntry = String.format("[%s] %s", timestamp, strippedMessage);

        File logFile = new File(plugin.getDataFolder(), "shop-log.txt");
        try (FileWriter fw = new FileWriter(logFile, true); // true = append
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(logEntry);
            bw.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write to shop-log.txt: " + e.getMessage());
        }
    }
    // --- จบเมธอดใหม่ ---

    // --- Helper เมธอดใหม่สำหรับ escape markdown ---
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("`", "\\`")
                   .replace("~", "\\~")
                   .replace(">", "\\>");
    }
    // --- จบ ---

    public void logBuy(Player player, ShopItem item, int amount, double totalPrice) {
        // --- แก้ไข: ใช้ DisplayName ถ้ามี, ถ้าไม่มีใช้ Material ---
        String itemName = (item.getDisplayName() != null && !item.getDisplayName().isBlank())
                ? ChatColor.stripColor(item.getDisplayName()) // เอาสีออก
                : item.getMaterial().name();
        
        // --- เพิ่ม File Log ---
        String logMsg = String.format("BUY: %s bought %dx %s for %.2f %s",
                player.getName(), amount, itemName, totalPrice, item.getCurrencyId());
        logToFile(logMsg);
        // --- จบ ---

        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("buy");
        if (url == null || url.isEmpty()) return; // Check if URL specifically is missing

        String title = "Player Purchase";
        String description = String.format("**%s** bought **%dx %s** for **%s %s**.",
                player.getName(),
                amount,
                escapeMarkdown(itemName), // ใช้ itemName ที่ strip สี + escape
                plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId()), // ใชสัญลักษณ์
                PriceUtil.format(totalPrice));

        String json = buildEmbedJson(title, description, 5763719); // Green
        sendAsync(url, json);
    }

    public void logSell(Player player, ShopItem item, int amount, double totalPrice) {
        // --- แก้ไข: ใช้ DisplayName ถ้ามี, ถ้าไม่มีใช้ Material ---
        String itemName = (item.getDisplayName() != null && !item.getDisplayName().isBlank())
                ? ChatColor.stripColor(item.getDisplayName()) // เอาสีออก
                : item.getMaterial().name();

        // --- เพิ่ม File Log ---
        String logMsg = String.format("SELL: %s sold %dx %s for %.2f %s",
                player.getName(), amount, itemName, totalPrice, item.getCurrencyId());
        logToFile(logMsg);
        // --- จบ ---

        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("sell");
        if (url == null || url.isEmpty()) return;

        String title = "Player Sale";
        String description = String.format("**%s** sold **%dx %s** for **%s %s**.",
                player.getName(),
                amount,
                escapeMarkdown(itemName), // ใช้ itemName ที่ strip สี + escape
                plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId()), // ใชสัญลักษณ์
                PriceUtil.format(totalPrice));

        String json = buildEmbedJson(title, description, 15548997); // Red
        sendAsync(url, json);
    }

    public void logAdmin(Player player, String action) {
        // --- เพิ่ม File Log ---
        String logMsg = String.format("ADMIN: %s executed: %s", player.getName(), action);
        logToFile(logMsg);
        // --- จบ ---

        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("admin");
        if (url == null || url.isEmpty()) return;

        String title = "Admin Action";
        String safeAction = escapeMarkdown(action); // Use helper
        String description = String.format("Admin **%s** performed action: `%s`",
                player.getName(),
                safeAction);

        String json = buildEmbedJson(title, description, 3447003); // Blue
        sendAsync(url, json);
    }

    public void logPriceReset(int itemsReset) {
        // --- เพิ่ม File Log ---
        logToFile("SYSTEM: Dynamic prices reset for " + itemsReset + " items.");
        // --- จบ ---

        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("price_reset");
        if (url == null || url.isEmpty()) return;

        String title = "Dynamic Price Reset";
        String description = String.format("Successfully reset **%d** dynamic item prices.", itemsReset);

        String json = buildEmbedJson(title, description, 16705372); // Yellow
        sendAsync(url, json);
    }

    private String buildEmbedJson(String title, String description, int color) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        // --- แก้ไข: ใช้ Default value และเช็คค่าว่าง ---
        String finalUsername = (this.username == null || this.username.trim().isEmpty()) ? "KJShopPlus Bot" : this.username;
        String finalAvatarUrl = (this.avatarUrl == null) ? "" : this.avatarUrl;

        // Build JSON (ensure avatar_url is only included if not empty)
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{")
                   .append("\"username\": \"").append(escapeJson(finalUsername)).append("\",");
        if (!finalAvatarUrl.isEmpty()) {
             jsonBuilder.append("\"avatar_url\": \"").append(escapeJson(finalAvatarUrl)).append("\",");
        }
        jsonBuilder.append("\"embeds\": [{")
                   .append("\"title\": \"").append(escapeJson(title)).append("\",")
                   .append("\"description\": \"").append(escapeJson(description)).append("\",")
                   .append("\"color\": ").append(color).append(",")
                   .append("\"footer\": {\"text\": \"KJShopPlus Logger\"},")
                   .append("\"timestamp\": \"").append(timestamp).append("\"") // ISO 8601 timestamp
                   .append("}]")
                   .append("}");
        
        return jsonBuilder.toString();
        // --- จบ ---
    }

    // Helper to escape strings for JSON values
    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

private void sendAsync(String urlString, String jsonPayload) {
    // Basic validation for URL format
    if (urlString == null || urlString.isEmpty()) {
        plugin.getLogger().warning("Discord webhook URL is empty or null!");
        return;
    }

    new BukkitRunnable() {
        @Override
        public void run() {
            try {
                URL url = new URL(urlString);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != 204) {
                    plugin.getLogger().warning("Discord webhook request failed with code: " + responseCode);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        }
    }.runTaskAsynchronously(plugin);
}
}
