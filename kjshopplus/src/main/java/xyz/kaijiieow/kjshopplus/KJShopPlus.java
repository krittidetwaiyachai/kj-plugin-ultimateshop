package xyz.kaijiieow.kjshopplus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import xyz.kaijiieow.kjshopplus.commands.AdminCommand;
import xyz.kaijiieow.kjshopplus.commands.SellAllCommand; // --- เพิ่ม Import ---
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

import java.util.logging.FileHandler; // --- เพิ่ม Import ---
import java.util.logging.Level;
import java.util.logging.Logger; // --- เพิ่ม Import ---
import java.util.logging.SimpleFormatter; // --- เพิ่ม Import ---
import java.io.IOException; // --- เพิ่ม Import ---
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

    // --- เพิ่ม Logger สำหรับไฟล์ ---
    private static final Logger fileLogger = Logger.getLogger("KJShopPlusFileLogger");
    // --- สิ้นสุด ---

    @Override
    public void onEnable() {
        instance = this;

        PDC_ACTION_KEY = new NamespacedKey(this, "kjshop_action");
        PDC_VALUE_KEY = new NamespacedKey(this, "kjshop_value");

        // --- เพิ่มการตั้งค่า File Logger ---
        try {
            setupLogFile();
        } catch (IOException e) {
            getLogger().severe("Failed to initialize file logger!");
            e.printStackTrace();
        }
        // --- สิ้นสุด ---


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

        reload(); // โหลด Config ทั้งหมด

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        Objects.requireNonNull(getCommand("shop")).setExecutor(new ShopCommand(this));
        Objects.requireNonNull(getCommand("kjshopadmin")).setExecutor(new AdminCommand(this));
        Objects.requireNonNull(getCommand("sellall")).setExecutor(new SellAllCommand(this)); // --- เพิ่มบรรทัดนี้ ---

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

    // --- เพิ่มเมธอดสำหรับตั้งค่า File Logger ---
    private void setupLogFile() throws IOException {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        FileHandler fh = new FileHandler(getDataFolder() + "/shop-log.txt", true); // true = append
        fh.setFormatter(new SimpleFormatter());
        fh.setLevel(Level.INFO); // Log เฉพาะ INFO และสูงกว่า
        fileLogger.addHandler(fh);
        fileLogger.setUseParentHandlers(false); // ไม่ต้องพิมพ์ใน console
        fileLogger.info("--- KJShopPlus Log Started ---");
    }
    // --- สิ้นสุด ---

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
        if (discordWebhookService != null) {
             discordWebhookService.loadConfig(); // สั่งให้โหลด username/avatar ใหม่หลัง reload config
        }
        messageManager.load();
        shopManager.load();

        if (dynamicPriceManager != null) {
            dynamicPriceManager.stopPriceResetTask();
            dynamicPriceManager.loadPrices();
            dynamicPriceManager.startPriceResetTask();
        }
    }

    // Public Getters
    public static KJShopPlus getInstance() { return instance; }
    // --- เพิ่ม Getter สำหรับ File Logger ---
    public static Logger getFileLogger() { return fileLogger; }
    // --- สิ้นสุด ---
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
