package xyz.kaijiieow.kjshopplus.pricing;

import org.bukkit.ChatColor; // Import ChatColor
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.config.model.ShopItem;
import xyz.kaijiieow.kjshopplus.economy.PriceUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LoreFormatter {

    private final KJShopPlus plugin;

    public LoreFormatter(KJShopPlus plugin) {
        this.plugin = plugin;
    }

    // --- THIS IS THE FIX for Color Codes ---
    private String translateColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public List<String> formatItemLore(ShopItem item) {
        List<String> lore = new ArrayList<>();
        
        // 1. Add base lore (translated)
        if (item.getBaseLore() != null) {
            lore.addAll(item.getBaseLore().stream()
                    .map(this::translateColors) // <-- Use translator
                    .collect(Collectors.toList()));
        }

        // 2. Add prices (translated)
        double buyPrice = plugin.getDynamicPriceManager().getBuyPrice(item);
        double sellPrice = plugin.getDynamicPriceManager().getSellPrice(item);
        String symbol = plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId());
        String buyStr = PriceUtil.format(buyPrice);
        String sellStr = PriceUtil.format(sellPrice);

        if (item.isAllowBuy()) {
            lore.add(translateColors("&7Buy Price: &a" + symbol + buyStr)); // <-- Use translator
        }
        if (item.isAllowSell()) {
            lore.add(translateColors("&7Sell Price: &c" + symbol + sellStr)); // <-- Use translator
        }
        
        // 3. Add dynamic status (translated)
        if (item.isDynamicEnabled()) {
            lore.add(translateColors("&8(Dynamic Pricing Active)")); // <-- Use translator
        } else {
            lore.add(translateColors("&8(Static Pricing)")); // <-- Use translator
        }

        return lore;
    }
    // --- END FIX ---
}

