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
    // Use ConcurrentHashMap for thread safety with async task
    private final Map<String, Double> currentBuyPrices = new ConcurrentHashMap<>();
    private final Map<String, Double> currentSellPrices = new ConcurrentHashMap<>();
    private BukkitTask resetTask;

    public DynamicPriceManager(KJShopPlus plugin) {
        this.plugin = plugin;
        loadPrices(); // Load initial prices on creation
    }

    // Loads base prices from ShopManager into current prices
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
        return count; // Return count for feedback
    }

    // --- ADDED MANUAL RESET METHOD ---
    /**
     * Resets all dynamic prices to their base values immediately.
     * @return The number of prices reset.
     */
    public int resetAllPricesNow() {
        plugin.getLogger().info("Manually resetting all dynamic prices...");
        return loadPrices(); // Re-loading base prices effectively resets them
    }


    public double getBuyPrice(ShopItem item) {
        if (!item.isDynamicEnabled()) {
            return item.getBaseBuyPrice();
        }
        // Ensure the item exists in the map, otherwise load its base price
        return currentBuyPrices.computeIfAbsent(item.getGlobalId(), k -> item.getBaseBuyPrice());
    }

    public double getSellPrice(ShopItem item) {
        if (!item.isDynamicEnabled()) {
            return item.getBaseSellPrice();
        }
        return currentSellPrices.computeIfAbsent(item.getGlobalId(), k -> item.getBaseSellPrice());
    }

    // Record a purchase and update the buy/sell prices accordingly
    public void recordBuy(ShopItem item, int amount) {
        if (!item.isDynamicEnabled() || !item.isAllowBuy() || amount <= 0) return;

        String globalId = item.getGlobalId();
        double currentBuy = getBuyPrice(item);
        double currentSell = getSellPrice(item);

        // Increase buy price based on step and amount
        double newBuy = currentBuy * (1.0 + (item.getDynamicBuyStep() * amount));
        newBuy = Math.min(newBuy, item.getDynamicMaxPrice()); // Clamp to max

        // Slightly increase sell price too (less than buy increase)
        double sellIncreaseFactor = item.getDynamicBuyStep() * 0.5; // Example: Sell increases half as much as buy
        double newSell = currentSell * (1.0 + (sellIncreaseFactor * amount));
        newSell = Math.min(newSell, item.getDynamicMaxPrice() * 0.8); // Clamp sell price (e.g., max 80% of max buy)
        newSell = Math.max(newSell, item.getDynamicMinPrice()); // Ensure sell doesn't drop below min

        currentBuyPrices.put(globalId, newBuy);
        currentSellPrices.put(globalId, newSell);

         // Optional: Log price change? Maybe too spammy.
         // plugin.getLogger().info(String.format("BUY %s: Buy %.2f -> %.2f, Sell %.2f -> %.2f",
         //       item.getItemId(), currentBuy, newBuy, currentSell, newSell));
    }

    // Record a sale and update the buy/sell prices accordingly
    public void recordSell(ShopItem item, int amount) {
        if (!item.isDynamicEnabled() || !item.isAllowSell() || amount <= 0) return;

        String globalId = item.getGlobalId();
        double currentBuy = getBuyPrice(item);
        double currentSell = getSellPrice(item);

        // Decrease sell price based on step and amount
        double newSell = currentSell * (1.0 - (item.getDynamicSellStep() * amount));
        newSell = Math.max(newSell, item.getDynamicMinPrice()); // Clamp to min

        // Slightly decrease buy price too (less than sell decrease)
        double buyDecreaseFactor = item.getDynamicSellStep() * 0.5; // Example: Buy decreases half as much as sell
        double newBuy = currentBuy * (1.0 - (buyDecreaseFactor * amount));
        newBuy = Math.max(newBuy, item.getDynamicMinPrice() * 1.2); // Clamp buy price (e.g., min 120% of min sell)
        newBuy = Math.min(newBuy, item.getDynamicMaxPrice()); // Ensure buy doesn't exceed max


        currentBuyPrices.put(globalId, newBuy);
        currentSellPrices.put(globalId, newSell);

         // Optional: Log price change?
         // plugin.getLogger().info(String.format("SELL %s: Buy %.2f -> %.2f, Sell %.2f -> %.2f",
         //       item.getItemId(), currentBuy, newBuy, currentSell, newSell));
    }


    public void startPriceResetTask() {
        stopPriceResetTask(); // Ensure any old task is stopped

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
                int resetCount = loadPrices(); // loadPrices resets and returns count
                plugin.getLogger().info("Automatically reset " + resetCount + " dynamic prices.");
                // Log to Discord (only if reset actually happened)
                if (resetCount > 0) {
                     plugin.getDiscordWebhookService().logPriceReset(resetCount);
                }
            }
        }.runTaskTimerAsynchronously(plugin, resetIntervalTicks, resetIntervalTicks);

         plugin.getMessageManager().sendMessage(Bukkit.getConsoleSender(), "task_started"); // Use ConsoleSender
    }

    public void stopPriceResetTask() {
        if (this.resetTask != null && !this.resetTask.isCancelled()) {
            this.resetTask.cancel();
            this.resetTask = null; // Clear the reference
             plugin.getMessageManager().sendMessage(Bukkit.getConsoleSender(), "task_stopped"); // Use ConsoleSender
        }
    }
}

