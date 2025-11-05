package xyz.kaijiieow.kjshopplus.services;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
import xyz.kaijiieow.kjshopplus.economy.PriceUtil;
import org.bukkit.ChatColor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DiscordWebhookService {

    private final KJShopPlus plugin;
    private String username = "KJShopPlus Bot";
    private String avatarUrl = "";
    private final SimpleDateFormat fileTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public DiscordWebhookService(KJShopPlus plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        if (plugin.getConfigManager() == null) {
            plugin.getLogger().warning("DiscordWebhookService#loadConfig called before ConfigManager was initialized!");
            return;
        }
        this.username = plugin.getConfigManager().getWebhookUsername();
        this.avatarUrl = plugin.getConfigManager().getWebhookAvatarUrl();

        if (this.username == null || this.username.trim().isEmpty()) {
            plugin.getLogger().warning("Discord webhook username is empty in config.yml! Using default 'KJShopPlus Bot'.");
            this.username = "KJShopPlus Bot";
        }
    }

    private void logToFile(String message) {
        String timestamp = fileTimestampFormatter.format(new Date());
        String strippedMessage = ChatColor.stripColor(message);
        String logEntry = String.format("[%s] %s", timestamp, strippedMessage);

        File logFile = new File(plugin.getDataFolder(), "shop-log.txt");
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(logEntry);
            bw.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write to shop-log.txt: " + e.getMessage());
        }
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("`", "\\`")
                   .replace("~", "\\~")
                   .replace(">", "\\>");
    }

    public void logBuy(Player player, ShopItem item, int amount, double totalPrice) {
        
        String configName = item.getConfigDisplayName();
        String itemName = (configName != null && !configName.isBlank())
                ? ChatColor.stripColor(configName)
                : item.getMaterial().name();
        
        String logMsg = String.format("BUY: %s bought %dx %s for %.2f %s",
                player.getName(), amount, itemName, totalPrice, item.getCurrencyId());
        logToFile(logMsg);

        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("buy");
        if (url == null || url.isEmpty()) return;

        String title = "🛒 รายการซื้อสินค้า"; // 🛒 Purchase
        String description = String.format(
                "**ผู้เล่น:** `%s`\n" +
                "**ซื้อไอเทม:** %s (x%d)\n" +
                "**ราคา:** %s %s",
                player.getName(),
                escapeMarkdown(itemName),
                amount,
                plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId()),
                PriceUtil.format(totalPrice));

        String json = buildEmbedJson(title, description, 5763719); // สีเขียว (เดิม)
        sendAsync(url, json);
    }

    public void logSell(Player player, ShopItem item, int amount, double totalPrice) {
        
        String configName = item.getConfigDisplayName();
        String itemName = (configName != null && !configName.isBlank())
                ? ChatColor.stripColor(configName)
                : item.getMaterial().name();

        String logMsg = String.format("SELL: %s sold %dx %s for %.2f %s",
                player.getName(), amount, itemName, totalPrice, item.getCurrencyId());
        logToFile(logMsg);

        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("sell");
        if (url == null || url.isEmpty()) return;

        String title = "💰 รายการขายสินค้า"; // 💰 Sale
        String description = String.format(
                "**ผู้เล่น:** `%s`\n" +
                "**ขายไอเทม:** %s (x%d)\n" +
                "**ได้รับ:** %s %s",
                player.getName(),
                escapeMarkdown(itemName),
                amount,
                plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId()),
                PriceUtil.format(totalPrice));

        String json = buildEmbedJson(title, description, 15548997); // สีแดง (เดิม)
        sendAsync(url, json);
    }

    public void logAdmin(Player player, String action) {
        String logMsg = String.format("ADMIN: %s executed: %s", player.getName(), action);
        logToFile(logMsg);

        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("admin");
        if (url == null || url.isEmpty()) return;

        String title = "🛡️ การดำเนินการของแอดมิน"; // 🛡️ Admin Action
        String safeAction = escapeMarkdown(action);
        String description = String.format(
                "**แอดมิน:** `%s`\n" +
                "**ดำเนินการ:** `%s`",
                player.getName(),
                safeAction);

        String json = buildEmbedJson(title, description, 3447003); // สีฟ้า (เดิม)
        sendAsync(url, json);
    }

    public void logPriceReset(int itemsReset) {
        logToFile("SYSTEM: Dynamic prices reset for " + itemsReset + " items.");

        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("price_reset");
        if (url == null || url.isEmpty()) return;

        String title = "🔄 รีเซ็ตราคาอัตโนมัติ"; // 🔄 Price Reset
        String description = String.format("รีเซ็ตราคาไอเทม (Dynamic) จำนวน **%d** รายการเรียบร้อยแล้ว", itemsReset);

        String json = buildEmbedJson(title, description, 16705372); // สีเหลือง (เดิม)
        sendAsync(url, json);
    }

    private String buildEmbedJson(String title, String description, int color) {
        
        SimpleDateFormat sdfDiscord = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        sdfDiscord.setTimeZone(TimeZone.getTimeZone("UTC"));
        String discordTimestamp = sdfDiscord.format(new Date());

        
        SimpleDateFormat sdfFooter = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        String footerTimestamp = sdfFooter.format(new Date());

        String finalUsername = (this.username == null || this.username.trim().isEmpty()) ? "KJShopPlus Bot" : this.username;
        String finalAvatarUrl = (this.avatarUrl == null) ? "" : this.avatarUrl;

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
                   .append("\"footer\": {\"text\": \"KJShopPlus Logger • ").append(escapeJson(footerTimestamp)).append("\"},")
                   .append("\"timestamp\": \"").append(discordTimestamp).append("\"")
                   .append("}]")
                   .append("}");
        
        return jsonBuilder.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
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