package xyz.kaijiieow.kjshopplus.pricing;

 import org.bukkit.ChatColor;
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
 
     private String translateColors(String text) {
         if (text == null) return "";
         return ChatColor.translateAlternateColorCodes('&', text);
     }
 
     
     public List<String> formatItemLore(ShopItem item) {
         List<String> lore = new ArrayList<>();
         
         
         List<String> configLore = item.getConfigBaseLore();
         if (configLore != null) {
             lore.addAll(configLore.stream()
                     .map(this::translateColors)
                     .collect(Collectors.toList()));
         }
 
         
         lore.addAll(getPriceLore(item));
 
         return lore;
     }

     private String getPriceChange(double current, double base) {
        if (base <= 0.001 || Math.abs(current - base) < 0.001) {
            return translateColors(" &7(0.0%)");
        }
        double diff = current - base;
        double percent = (diff / base) * 100.0;
        String percentStr = String.format("%.1f", Math.abs(percent));

        if (diff > 0) {
            return translateColors(" &a(⏶ " + percentStr + "%)");
        } else {
            return translateColors(" &c(⏷ " + percentStr + "%)");
        }
     }
 
     
     public List<String> getPriceLore(ShopItem item) {
         List<String> priceLore = new ArrayList<>();
 
         
         double buyPrice = plugin.getDynamicPriceManager().getBuyPrice(item);
         double sellPrice = plugin.getDynamicPriceManager().getSellPrice(item);
         String symbol = plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId());
         String buyStr = PriceUtil.format(buyPrice);
         String sellStr = PriceUtil.format(sellPrice);
 
         if (item.isAllowBuy()) {
            String change = item.isDynamicEnabled() ? getPriceChange(buyPrice, item.getBaseBuyPrice()) : "";
             priceLore.add(translateColors("&7Buy Price: &a" + symbol + buyStr + change));
         }
         if (item.isAllowSell()) {
            String change = item.isDynamicEnabled() ? getPriceChange(sellPrice, item.getBaseSellPrice()) : "";
             priceLore.add(translateColors("&7Sell Price: &c" + symbol + sellStr + change));
         }
         
         
         if (item.isDynamicEnabled()) {
             priceLore.add(translateColors("&8(Dynamic Pricing Active)"));
         } else {
             
             if (item.isAllowBuy() || item.isAllowSell()) {
                  priceLore.add(translateColors("&8(Static Pricing)"));
             }
         }
 
         return priceLore;
     }
 }