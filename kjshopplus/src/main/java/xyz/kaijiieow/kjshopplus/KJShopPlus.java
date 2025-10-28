package xyz.kaijiieow.kjshopplus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import xyz.kaijiieow.kjshopplus.commands.AdminCommand;
import xyz.kaijiieow.kjshopplus.commands.ShopCommand;
import xyz.kaijiieow.kjshopplus.config.ConfigManager;
import xyz.kaijiieow.kjshopplus.config.MessageManager;
import xyz.kaijiieow.kjshopplus.config.ShopManager;
import xyz.kaijiieow.kjshopplus.economy.KJCurrencyService;
import xyz.kaijiieow.kjshopplus.gui.GUIManager;
import xyz.kaijiieow.kjshopplus.gui.GUIListener;
import xyz.kaijiieow.kjshopplus.gui.util.PlayerTapManager;
import xyz.kaijiieow.kjshopplus.pricing.DynamicPriceManager;
import xyz.kaijiieow.kjshopplus.pricing.LoreFormatter;
import xyz.kaijiieow.kjshopplus.services.DiscordWebhookService;

// --- เพิ่ม Import ---
import java.io.File; 
// --- จบ ---

import java.util.logging.Level;
import java.util.Objects;
import java.util.UUID;


public final class KJShopPlus extends JavaPlugin {

    private static KJShopPlus instance;
    private Economy vaultEconomy;
    private FloodgateApi floodgateApi;

    public static NamespacedKey PDC_ACTION_KEY;
    public static NamespacedKey PDC_VALUE_KEY;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private ShopManager shopManager;
    private KJCurrencyService currencyService;
    private DynamicPriceManager dynamicPriceManager;
    private DiscordWebhookService discordWebhookService;
    private LoreFormatter loreFormatter;
    private GUIManager guiManager;
    private PlayerTapManager playerTapManager;

    @Override
    public void onEnable() {
        instance = this;

        PDC_ACTION_KEY = new NamespacedKey(this, "kjshop_action");
        PDC_VALUE_KEY = new NamespacedKey(this, "kjshop_value");

        if (!setupDependencies()) {
            getLogger().severe("Missing critical dependencies (Vault or CoinsEngine). Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // --- ลำดับการสร้าง Instance สำคัญ ---
        this.configManager = new ConfigManager(this); // สร้าง Config ก่อน
        this.messageManager = new MessageManager(this);
        this.shopManager = new ShopManager(this);
        this.currencyService = new KJCurrencyService(vaultEconomy, this);
        this.discordWebhookService = new DiscordWebhookService(this); // สร้าง Webhook ทีหลัง Config
        this.dynamicPriceManager = new DynamicPriceManager(this);
        this.loreFormatter = new LoreFormatter(this);
        this.guiManager = new GUIManager(this);
        this.playerTapManager = new PlayerTapManager(this);

        // --- เพิ่มการสร้างไฟล์ Log ---
        setupLogFile();
        // --- จบ ---

        reload(); // โหลด Config ทั้งหมด

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        Objects.requireNonNull(getCommand("shop")).setExecutor(new ShopCommand(this)); // แก้ kjshop เป็น shop
        Objects.requireNonNull(getCommand("kjshopadmin")).setExecutor(new AdminCommand(this));

        getLogger().info("KJShopPlus v" + getDescription().getVersion() + " enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.closeAllMenus();
        }
        if (dynamicPriceManager != null) {
            dynamicPriceManager.stopPriceResetTask();
        }
        getLogger().info("KJShopPlus disabled.");
    }

    // --- เพิ่มเมธอดนี้ ---
    private void setupLogFile() {
        File logFile = new File(getDataFolder(), "shop-log.txt");
        if (!logFile.exists()) {
            try {
                if (logFile.createNewFile()) {
                    getLogger().info("Created shop-log.txt for file logging.");
                }
            } catch (java.io.IOException e) {
                getLogger().warning("Failed to create shop-log.txt: " + e.getMessage());
            }
        }
    }
    // --- จบ ---

    private boolean setupDependencies() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        vaultEconomy = rsp.getProvider();

        if (getServer().getPluginManager().getPlugin("CoinsEngine") == null) {
            getLogger().warning("CoinsEngine not found. Custom currencies will not work.");
        }

        if (getServer().getPluginManager().getPlugin("floodgate") != null) {
            try {
                this.floodgateApi = FloodgateApi.getInstance();
            } catch (Exception e) {
                 getLogger().warning("Failed to hook into Floodgate API. Bedrock support disabled.");
            }
        }

        return vaultEconomy != null;
    }

    public void reload() {
        if (guiManager != null) {
            guiManager.closeAllMenus();
        }

        configManager.load();
        // --- ADD THIS LINE (Keep webhook config reload) ---
        if (discordWebhookService != null) {
             discordWebhookService.loadConfig(); // สั่งให้โหลด username/avatar ใหม่หลัง reload config
        }
        // --- END ADD ---
        messageManager.load();
        shopManager.load();

        if (dynamicPriceManager != null) {
            dynamicPriceManager.stopPriceResetTask();
            dynamicPriceManager.loadPrices();
            dynamicPriceManager.startPriceResetTask();
        }
        getLogger().log(Level.INFO, "KJShopPlus configuration reloaded."); // Log เพิ่มตอน reload สำเร็จ
    }
    
    // Public Getters
    public static KJShopPlus getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public ShopManager getShopManager() { return shopManager; }
    public KJCurrencyService getCurrencyService() { return currencyService; }
    public DynamicPriceManager getDynamicPriceManager() { return dynamicPriceManager; }
    public DiscordWebhookService getDiscordWebhookService() { return discordWebhookService; }
    public LoreFormatter getLoreFormatter() { return loreFormatter; }
    public GUIManager getGuiManager() { return guiManager; }
    public PlayerTapManager getPlayerTapManager() { return playerTapManager; }
    
    public boolean isBedrockPlayer(UUID uuid) {
        return floodgateApi != null && floodgateApi.isFloodgatePlayer(uuid);
    }
}

