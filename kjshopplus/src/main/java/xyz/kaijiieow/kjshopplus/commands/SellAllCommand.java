package xyz.kaijiieow.kjshopplus.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.MessageManager;
import xyz.kaijiieow.kjshopplus.config.ShopManager;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
import xyz.kaijiieow.kjshopplus.economy.KJCurrencyService;
import xyz.kaijiieow.kjshopplus.economy.PriceUtil;
import xyz.kaijiieow.kjshopplus.pricing.DynamicPriceManager;
import xyz.kaijiieow.kjshopplus.services.DiscordWebhookService;

import java.util.HashMap;
import java.util.Map;

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

        // สร้าง Map ของไอเทมที่จะขาย (Material -> จำนวน)
        Map<Material, Integer> itemsToSell = new HashMap<>();
        // สร้าง Map เพื่ออ้างอิง Material กลับไปที่ ShopItem (เพื่อดึง config)
        Map<Material, ShopItem> itemConfigs = new HashMap<>();

        // วนลูปในช่องเก็บของหลัก (ไม่รวมเกราะและ off-hand)
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                continue;
            }

            Material mat = itemStack.getType();
            ShopItem shopItem = shopManager.getShopItemByMaterial(mat); // ใช้วิธีใหม่ในการดึง ShopItem

            // เช็คว่า: 1. ร้านมีของนี้ 2. ร้านรับซื้อ 3. สกุลเงินเป็น vault
            if (shopItem != null && shopItem.isAllowSell() && shopItem.getCurrencyId().equalsIgnoreCase("vault")) {
                itemsToSell.put(mat, itemsToSell.getOrDefault(mat, 0) + itemStack.getAmount());
                itemConfigs.putIfAbsent(mat, shopItem); // เก็บ config ของไอเทมไว้
            }
        }

        if (itemsToSell.isEmpty()) {
            messageManager.sendMessage(player, "sellall_nothing_to_sell");
            return true;
        }

        double totalSellPrice = 0.0;
        int totalItemsSold = 0;

        // ทำการขาย
        for (Map.Entry<Material, Integer> entry : itemsToSell.entrySet()) {
            Material mat = entry.getKey();
            int amount = entry.getValue();
            ShopItem shopItem = itemConfigs.get(mat); // ดึง config ที่เก็บไว้

            double sellPrice = dynamicPriceManager.getSellPrice(shopItem);
            double itemTotalPrice = sellPrice * amount;

            totalSellPrice += itemTotalPrice;
            totalItemsSold += amount;

            // ลบของออกจากตัวผู้เล่น
            player.getInventory().removeItem(new ItemStack(mat, amount));
            // บันทึกการขาย (สำหรับ Dynamic Price)
            dynamicPriceManager.recordSell(shopItem, amount);
            // ส่ง Log ไป Discord/File
            discordWebhookService.logSell(player, shopItem, amount, itemTotalPrice);
        }

        // เพิ่มเงินให้ผู้เล่น
        if (!currencyService.addBalance(player, "vault", totalSellPrice)) {
            // คืนของถ้าให้เงินไม่สำเร็จ (ยากที่จะเกิด แต่กันไว้)
            for (Map.Entry<Material, Integer> entry : itemsToSell.entrySet()) {
                player.getInventory().addItem(new ItemStack(entry.getKey(), entry.getValue()));
            }
            plugin.getLogger().severe("CRITICAL: Failed to add balance for " + player.getName() + " after /sellall! Returning items.");
            messageManager.sendMessage(player, "error_occurred");
            return true;
        }

        // ส่งข้อความสำเร็จ
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(totalItemsSold));
        placeholders.put("price", PriceUtil.format(totalSellPrice));
        placeholders.put("currency_symbol", currencyService.getCurrencySymbol("vault"));

        messageManager.sendMessage(player, "sellall_success", placeholders);

        return true;
    }
}
