package xyz.kaijiieow.kjshopplus.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.util.ArrayList; // Import ArrayList
import java.util.HashMap;
import java.util.List; // Import List
import java.util.Map;
import java.util.Set; // Import Set

public class ConfigManager {

    private final KJShopPlus plugin;
    private FileConfiguration config;

    // Input Control
    private boolean leftTapOnly, disableRightClick, disableShiftClick, disableInventoryDrag;
    private int tapDebounceMs;

    // Bedrock
    private boolean bedrockCompatEnabled;
    private final Map<Material, Material> bedrockMaterialMap = new HashMap<>();

    // Discord
    private boolean discordLoggingEnabled;
    private String webhookUsername, webhookAvatarUrl;
    private final Map<String, String> webhookUrls = new HashMap<>();

    // Dynamic Pricing
    private boolean dynamicPricingEnabled;
    private long dynamicPriceResetInterval; // in seconds

    // Currencies
    private final Map<String, String> currencyDisplayNames = new HashMap<>();

    public ConfigManager(KJShopPlus plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        loadInputControl();
        loadBedrockCompat();
        loadDiscordLogging();
        loadDynamicPricing();
        loadCustomCurrencies(); // Make sure this is called
    }

    private void loadInputControl() {
        ConfigurationSection ic = config.getConfigurationSection("input_mode");
        if (ic == null) return;
        this.leftTapOnly = ic.getBoolean("left_tap_only", true);
        this.disableRightClick = ic.getBoolean("disable_right_click", true);
        this.disableShiftClick = ic.getBoolean("disable_shift_click", true);
        this.disableInventoryDrag = ic.getBoolean("disable_inventory_drag", true);
        this.tapDebounceMs = ic.getInt("tap_debounce_ms", 200);
    }

    private void loadBedrockCompat() {
        ConfigurationSection bc = config.getConfigurationSection("bedrock_compat");
        if (bc == null) return;
        this.bedrockCompatEnabled = bc.getBoolean("enabled", true);
        bedrockMaterialMap.clear();
        ConfigurationSection map = bc.getConfigurationSection("material_map");
        if (map != null) {
            for (String key : map.getKeys(false)) {
                try {
                    // Use valueOf for exact match, less prone to errors than matchMaterial
                    Material original = Material.valueOf(key.toUpperCase());
                    Material replacement = Material.valueOf(map.getString(key).toUpperCase());
                    bedrockMaterialMap.put(original, replacement);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid bedrock material map entry: " + key + " -> " + map.getString(key));
                }
            }
        }
    }

    private void loadDiscordLogging() {
        ConfigurationSection dc = config.getConfigurationSection("discord_logging");
        if (dc == null) return;
        this.discordLoggingEnabled = dc.getBoolean("enabled", true);
        this.webhookUsername = dc.getString("username", "KJShopPlus Bot");
        this.webhookAvatarUrl = dc.getString("avatar_url", "");

        webhookUrls.clear();
        ConfigurationSection urls = dc.getConfigurationSection("webhooks");
        if (urls != null) {
            webhookUrls.put("buy", urls.getString("buy", ""));
            webhookUrls.put("sell", urls.getString("sell", ""));
            webhookUrls.put("admin", urls.getString("admin", ""));
            webhookUrls.put("price_reset", urls.getString("price_reset", ""));
        }
    }

    private void loadDynamicPricing() {
        ConfigurationSection dp = config.getConfigurationSection("dynamic");
        if (dp == null) return;
        this.dynamicPricingEnabled = dp.getBoolean("enabled", true);
        this.dynamicPriceResetInterval = dp.getLong("reset_interval_seconds", 3600); // Default 1 hour
    }

    private void loadCustomCurrencies() {
        currencyDisplayNames.clear();
        // Always include Vault
        currencyDisplayNames.put("vault", config.getString("vault_display_symbol", "$"));

        ConfigurationSection cc = config.getConfigurationSection("custom_currencies");
        if (cc != null) {
            for (String key : cc.getKeys(false)) {
                 // Use lowercase key internally, get display name
                 currencyDisplayNames.put(key.toLowerCase(), cc.getString(key + ".display_name", key));
            }
        }
        plugin.getLogger().info("Loaded currencies: " + String.join(", ", currencyDisplayNames.keySet()));
    }

    // --- ADDED: Method to get all currency IDs ---
    /**
     * Gets a list of all configured currency IDs (e.g., "vault", "gems", "tokens").
     * @return A list of currency IDs.
     */
    public List<String> getAllCurrencyIds() {
        return new ArrayList<>(currencyDisplayNames.keySet());
    }


    // Getters
    public boolean isLeftTapOnly() { return leftTapOnly; }
    public boolean isDisableRightClick() { return disableRightClick; }
    public boolean isDisableShiftClick() { return disableShiftClick; }
    public boolean isDisableInventoryDrag() { return disableInventoryDrag; }
    public int getTapDebounceMs() { return tapDebounceMs; }
    public boolean isBedrockCompatEnabled() { return bedrockCompatEnabled; }
    public Material getBedrockMappedMaterial(Material original) {
        return bedrockMaterialMap.getOrDefault(original, original);
    }
    public boolean isDiscordLoggingEnabled() { return discordLoggingEnabled; }
    public String getWebhookUsername() { return webhookUsername; }
    public String getWebhookAvatarUrl() { return webhookAvatarUrl; }
    public String getWebhookUrl(String key) { return webhookUrls.getOrDefault(key.toLowerCase(), ""); }
    public boolean isDynamicPricingEnabled() { return dynamicPricingEnabled; }
    public long getDynamicPriceResetInterval() { return dynamicPriceResetInterval; }
    // Get display name (symbol) for a currency ID
    public String getCurrencyDisplayName(String currencyId) {
        return currencyDisplayNames.getOrDefault(currencyId.toLowerCase(), currencyId); // Use lowercase lookup
    }
}

