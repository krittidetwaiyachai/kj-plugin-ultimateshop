package xyz.kaijiieow.kjshopplus.pricing;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.model.ShopCategory;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicPriceManager {

    private final KJShopPlus plugin;
    
    // Track prices per item (using globalId: categoryId:itemId)
    private final Map<String, Double> itemBuyMultipliers = new ConcurrentHashMap<>();
    private final Map<String, Double> itemSellMultipliers = new ConcurrentHashMap<>();
    // Store buy/sell formulas per category (shared formula for items in same category)
    private final Map<String, Double> categoryBuySteps = new ConcurrentHashMap<>();
    private final Map<String, Double> categorySellSteps = new ConcurrentHashMap<>();
    private BukkitTask resetTask;

    // Default dynamic pricing settings
    private static final double DEFAULT_MAX_MULTIPLIER = 2.0; // Max 2x base price
    private static final double DEFAULT_MIN_MULTIPLIER = 0.5; // Min 0.5x base price

    public DynamicPriceManager(KJShopPlus plugin) {
        this.plugin = plugin;
        loadPrices();
    }

    
    public int loadPrices() {
        itemBuyMultipliers.clear();
        itemSellMultipliers.clear();
        categoryBuySteps.clear();
        categorySellSteps.clear();
        
        int count = 0;
        double defaultBuyStep = plugin.getConfigManager().getDynamicBuyFormula();
        double defaultSellStep = plugin.getConfigManager().getDynamicSellFormula();
        
        for (ShopItem item : plugin.getShopManager().getAllShopItems()) {
            if (item.isDynamicEnabled()) {
                String globalId = item.getGlobalId();
                String categoryId = item.getCategoryId();
                
                // Initialize multipliers to 1.0 (no change) per item
                itemBuyMultipliers.putIfAbsent(globalId, 1.0);
                itemSellMultipliers.putIfAbsent(globalId, 1.0);
                
                // Load formulas for this category (only once per category, shared by all items in category)
                if (!categoryBuySteps.containsKey(categoryId)) {
                    ShopCategory category = plugin.getShopManager().getShopCategory(categoryId);
                    if (category != null && category.hasCustomDynamicFormulas()) {
                        // Use custom formulas from category
                        categoryBuySteps.put(categoryId, category.getBuyFormula());
                        categorySellSteps.put(categoryId, category.getSellFormula());
                    } else {
                        // Use default formulas from config.yml
                        categoryBuySteps.put(categoryId, defaultBuyStep);
                        categorySellSteps.put(categoryId, defaultSellStep);
                    }
                }
                
                count++;
            }
        }
        plugin.getLogger().info("Loaded/Reset " + count + " dynamic item prices.");
        return count;
    }

    
    
    public int resetAllPricesNow() {
        plugin.getLogger().info("Manually resetting all dynamic prices...");
        return loadPrices();
    }


    public double getBuyPrice(ShopItem item) {
        if (!item.isDynamicEnabled()) {
            return item.getBaseBuyPrice();
        }
        
        // Calculate price based on item multiplier
        double multiplier = itemBuyMultipliers.getOrDefault(item.getGlobalId(), 1.0);
        return item.getBaseBuyPrice() * multiplier;
    }

    public double getSellPrice(ShopItem item) {
        if (!item.isDynamicEnabled()) {
            return item.getBaseSellPrice();
        }
        // Calculate price based on item multiplier
        double multiplier = itemSellMultipliers.getOrDefault(item.getGlobalId(), 1.0);
        return item.getBaseSellPrice() * multiplier;
    }

    
    public void recordBuy(ShopItem item, int amount) {
        if (!item.isDynamicEnabled() || !item.isAllowBuy() || amount <= 0) return;

        String globalId = item.getGlobalId();
        String categoryId = item.getCategoryId();
        // Use shared formula from category
        double buyStep = categoryBuySteps.getOrDefault(categoryId, 0.01);
        double currentBuyMultiplier = itemBuyMultipliers.getOrDefault(globalId, 1.0);
        double currentSellMultiplier = itemSellMultipliers.getOrDefault(globalId, 1.0);

        // Exponential increase: ราคาเพิ่มขึ้นเร็วขึ้นเมื่อมีคนซื้อเยอะ
        // ใช้สูตร: multiplier * (1 + step)^amount เพื่อให้ราคาเพิ่มแบบ exponential
        // ยิ่งซื้อเยอะ ยิ่งเพิ่มเร็ว
        double increaseFactor = Math.pow(1.0 + buyStep, amount);
        double newBuyMultiplier = currentBuyMultiplier * increaseFactor;
        newBuyMultiplier = Math.min(newBuyMultiplier, DEFAULT_MAX_MULTIPLIER);

        // Increase sell price multiplier slightly (half the rate, slower)
        double sellIncreaseFactor = Math.pow(1.0 + (buyStep * 0.5), amount);
        double newSellMultiplier = currentSellMultiplier * sellIncreaseFactor;
        newSellMultiplier = Math.min(newSellMultiplier, DEFAULT_MAX_MULTIPLIER * 0.8);
        newSellMultiplier = Math.max(newSellMultiplier, DEFAULT_MIN_MULTIPLIER);

        itemBuyMultipliers.put(globalId, newBuyMultiplier);
        itemSellMultipliers.put(globalId, newSellMultiplier);
    }

    
    public void recordSell(ShopItem item, int amount) {
        if (!item.isDynamicEnabled() || !item.isAllowSell() || amount <= 0) return;

        String globalId = item.getGlobalId();
        String categoryId = item.getCategoryId();
        // Use shared formula from category
        double sellStep = categorySellSteps.getOrDefault(categoryId, 0.01);
        double currentBuyMultiplier = itemBuyMultipliers.getOrDefault(globalId, 1.0);
        double currentSellMultiplier = itemSellMultipliers.getOrDefault(globalId, 1.0);

        // Logarithmic decrease: ราคาลดลงช้าลง ไม่ลดเยอะเกินไป
        // ใช้สูตร: multiplier * (1 - step * log(1 + amount)) เพื่อให้ลดช้าๆ
        // ยิ่งขายเยอะ ราคาลดแต่ลดช้าลง (diminishing returns)
        double decreaseFactor = Math.log(1.0 + (sellStep * amount * 10)) / Math.log(1.0 + (sellStep * 10));
        double newSellMultiplier = currentSellMultiplier * (1.0 - (sellStep * decreaseFactor));
        newSellMultiplier = Math.max(newSellMultiplier, DEFAULT_MIN_MULTIPLIER);

        // Decrease buy price multiplier slightly (half the rate, even slower)
        double buyDecreaseFactor = Math.log(1.0 + (sellStep * amount * 5)) / Math.log(1.0 + (sellStep * 5));
        double newBuyMultiplier = currentBuyMultiplier * (1.0 - (sellStep * 0.5 * buyDecreaseFactor));
        newBuyMultiplier = Math.max(newBuyMultiplier, DEFAULT_MIN_MULTIPLIER * 1.2);
        newBuyMultiplier = Math.min(newBuyMultiplier, DEFAULT_MAX_MULTIPLIER);

        itemBuyMultipliers.put(globalId, newBuyMultiplier);
        itemSellMultipliers.put(globalId, newSellMultiplier);
    }


    public void startPriceResetTask() {
        stopPriceResetTask();

        if (!plugin.getConfigManager().isDynamicPricingEnabled()) {
             plugin.getLogger().info("Automatic dynamic price reset task is disabled in config.");
             return;
        }

        long resetIntervalTicks = plugin.getConfigManager().getDynamicPriceResetInterval() * 20L;
        if (resetIntervalTicks <= 0) {
            plugin.getLogger().warning("Dynamic price reset interval is zero or negative. Task not started.");
            return;
        }

        this.resetTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Executing automatic dynamic price reset...");
                int resetCount = loadPrices();
                plugin.getLogger().info("Automatically reset " + resetCount + " dynamic prices.");
                
                if (resetCount > 0) {
                     plugin.getDiscordWebhookService().logPriceReset(resetCount);
                }
            }
        }.runTaskTimerAsynchronously(plugin, resetIntervalTicks, resetIntervalTicks);

         plugin.getMessageManager().sendMessage(Bukkit.getConsoleSender(), "task_started");
    }

    public void stopPriceResetTask() {
        if (this.resetTask != null && !this.resetTask.isCancelled()) {
            this.resetTask.cancel();
            this.resetTask = null;
             plugin.getMessageManager().sendMessage(Bukkit.getConsoleSender(), "task_stopped");
        }
    }
}