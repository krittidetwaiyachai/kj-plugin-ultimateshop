package xyz.kaijiieow.kjshopplus.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.MessageManager;
import xyz.kaijiieow.kjshopplus.config.ShopManager;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
import xyz.kaijiieow.kjshopplus.economy.KJCurrencyService;
import xyz.kaijiieow.kjshopplus.economy.PriceUtil;
import xyz.kaijiieow.kjshopplus.pricing.DynamicPriceManager;
import xyz.kaijiieow.kjshopplus.services.DiscordWebhookService;
import java.util.ArrayList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SellAllCommand implements CommandExecutor {

    private final KJShopPlus plugin;
    private final ShopManager shopManager;
    private final DynamicPriceManager dynamicPriceManager;
    private final KJCurrencyService currencyService;
    private final MessageManager messageManager;
    private final DiscordWebhookService discordWebhookService;

    public SellAllCommand(KJShopPlus plugin) {
        this.plugin = plugin;
        this.shopManager = plugin.getShopManager();
        this.dynamicPriceManager = plugin.getDynamicPriceManager();
        this.currencyService = plugin.getCurrencyService();
        this.messageManager = plugin.getMessageManager();
        this.discordWebhookService = plugin.getDiscordWebhookService();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        new BukkitRunnable() {
            @Override
            public void run() {
                Map<Material, Integer> itemsToSell = new HashMap<>();
                ItemStack[] contents = player.getInventory().getStorageContents();

                for (ItemStack itemStack : contents) {
                    if (itemStack == null || itemStack.getType() == Material.AIR) {
                        continue;
                    }

                    Material mat = itemStack.getType();
                    List<ShopItem> sellable = shopManager.getSellableItems(mat);
                    if (!sellable.isEmpty()) {
                        itemsToSell.put(mat, itemsToSell.getOrDefault(mat, 0) + itemStack.getAmount());
                    }
                }

                if (itemsToSell.isEmpty()) {
                    messageManager.sendMessage(player, "sellall_nothing_to_sell");
                    return;
                }

                List<SellRequest> sellRequests = new ArrayList<>();
                for (Map.Entry<Material, Integer> entry : itemsToSell.entrySet()) {
                    Material mat = entry.getKey();
                    int amount = entry.getValue();
                    List<ShopItem> candidates = shopManager.getSellableItems(mat);
                    if (candidates.isEmpty() || amount <= 0) {
                        continue;
                    }
                    ShopItem best = selectBestSellCandidate(candidates);
                    if (best == null) {
                        continue;
                    }
                    double unitPrice = dynamicPriceManager.getSellPrice(best);
                    if (unitPrice <= 0) {
                        continue;
                    }
                    sellRequests.add(new SellRequest(mat, amount, best, unitPrice));
                }

                if (sellRequests.isEmpty()) {
                    messageManager.sendMessage(player, "sellall_nothing_to_sell");
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(playerUUID);
                    if (p == null || !p.isOnline()) {
                        return;
                    }
                    
                    Map<String, Double> payoutTotals = new HashMap<>();
                    Map<String, Integer> payoutItemCounts = new HashMap<>();
                    Map<Material, Integer> removedAmounts = new HashMap<>();

                    for (SellRequest request : sellRequests) {
                        ItemStack toRemove = new ItemStack(request.material, request.amount);
                        Map<Integer, ItemStack> leftovers = p.getInventory().removeItem(toRemove);
                        int notRemoved = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                        int removed = request.amount - notRemoved;

                        if (removed > 0) {
                            removedAmounts.merge(request.material, removed, Integer::sum);
                            
                            double totalRequestPrice = request.unitPrice * removed;
                            payoutTotals.merge(request.currencyId, totalRequestPrice, Double::sum);
                            payoutItemCounts.merge(request.currencyId, removed, Integer::sum);
                        }

                        if (notRemoved > 0) {
                            rollbackInventory(p, removedAmounts);
                            messageManager.sendMessage(p, "not_enough_items");
                            return; 
                        }
                    }

                    for (Map.Entry<String, Double> payout : payoutTotals.entrySet()) {
                        double amount = payout.getValue();
                        if (amount <= 0) continue;
                        if (!currencyService.addBalance(p, payout.getKey(), amount)) {
                            rollbackInventory(p, removedAmounts);
                            plugin.getLogger().severe("CRITICAL: Failed to add balance (" + payout.getKey() + ") for " + p.getName() + " after /sellall! Returning items.");
                            messageManager.sendMessage(p, "error_occurred");
                            return;
                        }
                    }

                    for (SellRequest request : sellRequests) {
                        int soldAmount = removedAmounts.getOrDefault(request.material, 0);
                        if (soldAmount > 0) {
                            dynamicPriceManager.recordSell(request.shopItem, soldAmount);
                            discordWebhookService.logSell(p, request.shopItem, soldAmount, request.unitPrice * soldAmount);
                        }
                    }

                    for (Map.Entry<String, Double> payout : payoutTotals.entrySet()) {
                        String currencyId = payout.getKey();
                        double totalPrice = payout.getValue();
                        int itemsSold = payoutItemCounts.getOrDefault(currencyId, 0);

                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("amount", String.valueOf(itemsSold));
                        placeholders.put("price", PriceUtil.format(totalPrice));
                        String symbol = currencyService.getCurrencySymbol(currencyId);
                        placeholders.put("currency_symbol", symbol != null ? symbol : "");

                        messageManager.sendMessage(p, "sellall_success", placeholders);
                    }
                });
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    private void rollbackInventory(Player player, Map<Material, Integer> removed) {
        removed.forEach((material, amount) -> {
            if (amount > 0) {
                player.getInventory().addItem(new ItemStack(material, amount));
            }
        });
    }

    private ShopItem selectBestSellCandidate(List<ShopItem> candidates) {
        ShopItem best = null;
        double bestPrice = Double.NEGATIVE_INFINITY;
        for (ShopItem candidate : candidates) {
            if (candidate == null || !candidate.isAllowSell()) continue;
            double price = dynamicPriceManager.getSellPrice(candidate);
            if (price > bestPrice) {
                bestPrice = price;
                best = candidate;
            }
        }
        return best;
    }

    private static class SellRequest {
        private final Material material;
        private final int amount;
        private final ShopItem shopItem;
        private final String currencyId;
        private final double unitPrice;
        private final double totalPrice;

        SellRequest(Material material, int amount, ShopItem shopItem, double unitPrice) {
            this.material = material;
            this.amount = amount;
            this.shopItem = shopItem;
            this.currencyId = shopItem.getCurrencyId();
            this.unitPrice = unitPrice;
            this.totalPrice = unitPrice * amount;
        }
    }
}