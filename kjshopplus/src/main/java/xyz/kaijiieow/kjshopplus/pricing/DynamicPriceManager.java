package xyz.kaijiieow.kjshopplus.pricing;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicPriceManager {

    private final KJShopPlus plugin;
    
    private final Map<String, Double> currentBuyPrices = new ConcurrentHashMap<>();
    private final Map<String, Double> currentSellPrices = new ConcurrentHashMap<>();
    private BukkitTask resetTask;

    public DynamicPriceManager(KJShopPlus plugin) {
        this.plugin = plugin;
        loadPrices();
    }

    
    public int loadPrices() {
        currentBuyPrices.clear();
        currentSellPrices.clear();
        int count = 0;
        for (ShopItem item : plugin.getShopManager().getAllShopItems()) {
            if (item.isDynamicEnabled()) {
                currentBuyPrices.put(item.getGlobalId(), item.getBaseBuyPrice());
                currentSellPrices.put(item.getGlobalId(), item.getBaseSellPrice());
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
        
        return currentBuyPrices.computeIfAbsent(item.getGlobalId(), k -> item.getBaseBuyPrice());
    }

    public double getSellPrice(ShopItem item) {
        if (!item.isDynamicEnabled()) {
            return item.getBaseSellPrice();
        }
        return currentSellPrices.computeIfAbsent(item.getGlobalId(), k -> item.getBaseSellPrice());
    }

    
    public void recordBuy(ShopItem item, int amount) {
        if (!item.isDynamicEnabled() || !item.isAllowBuy() || amount <= 0) return;

        String globalId = item.getGlobalId();
        double currentBuy = getBuyPrice(item);
        double currentSell = getSellPrice(item);

        
        double newBuy = currentBuy * (1.0 + (item.getDynamicBuyStep() * amount));
        newBuy = Math.min(newBuy, item.getDynamicMaxPrice());

        
        double sellIncreaseFactor = item.getDynamicBuyStep() * 0.5;
        double newSell = currentSell * (1.0 + (sellIncreaseFactor * amount));
        newSell = Math.min(newSell, item.getDynamicMaxPrice() * 0.8);
        newSell = Math.max(newSell, item.getDynamicMinPrice());

        currentBuyPrices.put(globalId, newBuy);
        currentSellPrices.put(globalId, newSell);

         
         
         
    }

    
    public void recordSell(ShopItem item, int amount) {
        if (!item.isDynamicEnabled() || !item.isAllowSell() || amount <= 0) return;

        String globalId = item.getGlobalId();
        double currentBuy = getBuyPrice(item);
        double currentSell = getSellPrice(item);

        
        double newSell = currentSell * (1.0 - (item.getDynamicSellStep() * amount));
        newSell = Math.max(newSell, item.getDynamicMinPrice());

        
        double buyDecreaseFactor = item.getDynamicSellStep() * 0.5;
        double newBuy = currentBuy * (1.0 - (buyDecreaseFactor * amount));
        newBuy = Math.max(newBuy, item.getDynamicMinPrice() * 1.2);
        newBuy = Math.min(newBuy, item.getDynamicMaxPrice());


        currentBuyPrices.put(globalId, newBuy);
        currentSellPrices.put(globalId, newSell);

         
         
         
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