package xyz.kaijiieow.kjshopplus.services;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
import xyz.kaijiieow.kjshopplus.economy.PriceUtil;
// Removed Colorizer import

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone; // Import TimeZone for UTC

public class DiscordWebhookService {

    private final KJShopPlus plugin;
    private String username;
    private String avatarUrl;

    public DiscordWebhookService(KJShopPlus plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    // Method to reload webhook username/avatar if config changes
    public void loadConfig() {
        this.username = plugin.getConfigManager().getWebhookUsername();
        this.avatarUrl = plugin.getConfigManager().getWebhookAvatarUrl();
    }

    public void logBuy(Player player, ShopItem item, int amount, double totalPrice) {
        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("buy");
        if (url == null || url.isEmpty()) return;

        String title = "Player Purchase";
        String description = String.format("**%s** bought **%dx %s** for **%s %s**.",
                player.getName(),
                amount,
                item.getMaterial().name(),
                item.getCurrencyId(), // Using ID is simpler than symbol here
                PriceUtil.format(totalPrice));

        String json = buildEmbedJson(title, description, 5763719); // Green
        sendAsync(url, json);
    }

    public void logSell(Player player, ShopItem item, int amount, double totalPrice) {
        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("sell");
        if (url == null || url.isEmpty()) return;

        String title = "Player Sale";
        String description = String.format("**%s** sold **%dx %s** for **%s %s**.",
                player.getName(),
                amount,
                item.getMaterial().name(),
                item.getCurrencyId(),
                PriceUtil.format(totalPrice));

        String json = buildEmbedJson(title, description, 15548997); // Red
        sendAsync(url, json);
    }

    public void logAdmin(Player player, String action) {
        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("admin");
        if (url == null || url.isEmpty()) return;

        String title = "Admin Action";
        // Escape markdown in action to prevent formatting issues
        String safeAction = action.replace("*", "\\*").replace("_", "\\_").replace("`", "\\`");
        String description = String.format("Admin **%s** performed action: `%s`",
                player.getName(),
                safeAction);

        String json = buildEmbedJson(title, description, 3447003); // Blue
        sendAsync(url, json);
    }

    public void logPriceReset(int itemsReset) {
        if (!plugin.getConfigManager().isDiscordLoggingEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl("price_reset");
        if (url == null || url.isEmpty()) return;

        String title = "Dynamic Price Reset";
        String description = String.format("Successfully reset **%d** dynamic item prices.", itemsReset);

        String json = buildEmbedJson(title, description, 16705372); // Yellow
        sendAsync(url, json);
    }

    private String buildEmbedJson(String title, String description, int color) {
        // Ensure timestamp is in ISO 8601 format (UTC)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        // Basic JSON structure for a Discord embed
        return "{"
                + "\"username\": \"" + escapeJson(username) + "\","
                + "\"avatar_url\": \"" + escapeJson(avatarUrl) + "\","
                + "\"embeds\": [{"
                + "\"title\": \"" + escapeJson(title) + "\","
                + "\"description\": \"" + escapeJson(description) + "\","
                + "\"color\": " + color + ","
                + "\"footer\": {\"text\": \"KJShopPlus Logger\"},"
                + "\"timestamp\": \"" + timestamp + "\"" // ISO 8601 timestamp
                + "}]"
                + "}";
    }

    private void sendAsync(String urlString, String jsonPayload) {
        // Basic validation for URL format
        if (urlString == null || urlString.isEmpty() || !urlString.startsWith("https://discord.com/api/webhooks/")) {
            plugin.getLogger().warning("Discord webhook URL is invalid or empty. Cannot send log. URL: " + urlString);
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                HttpsURLConnection connection = null;
                try {
                    URL url = new URL(urlString);
                    connection = (HttpsURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json; utf-8"); // Specify UTF-8
                    connection.setRequestProperty("User-Agent", "KJShopPlus-Webhook/1.0"); // Standard User-Agent
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(5000); // 5 second connect timeout
                    connection.setReadTimeout(5000);    // 5 second read timeout

                    // Write JSON payload
                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    // Check response code
                    int responseCode = connection.getResponseCode();
                    if (responseCode < 200 || responseCode >= 300) { // Check for non-2xx codes
                        String errorResponse = "";
                        // Try reading error stream
                        try (java.io.InputStream errorStream = connection.getErrorStream()) {
                            if (errorStream != null) {
                                errorResponse = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                            } else {
                                // Sometimes error stream is null, try input stream
                                try (java.io.InputStream inputStream = connection.getInputStream()) {
                                     if (inputStream != null) {
                                         errorResponse = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                                     }
                                } catch (Exception ignored) {} // Ignore if input stream also fails
                            }
                        } catch (Exception readEx) {
                             plugin.getLogger().warning("Failed to read error stream from Discord webhook: " + readEx.getMessage());
                        }
                        plugin.getLogger().warning("Discord webhook failed with code: " + responseCode + ". Response: " + errorResponse);
                    }
                } catch (java.net.SocketTimeoutException e) {
                     plugin.getLogger().warning("Failed to send Discord webhook: Connection timed out.");
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to send Discord webhook due to an unexpected error: " + e.getMessage());
                    e.printStackTrace(); // Print stack trace for debugging
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // Improved JSON escaping
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\") // Escape backslashes first
                   .replace("\"", "\\\"") // Escape double quotes
                   .replace("\n", "\\n")  // Escape newlines
                   .replace("\r", "\\r")  // Escape carriage returns
                   .replace("\t", "\\t")  // Escape tabs
                   .replace("\b", "\\b")  // Escape backspaces
                   .replace("\f", "\\f"); // Escape form feeds
    }
}

