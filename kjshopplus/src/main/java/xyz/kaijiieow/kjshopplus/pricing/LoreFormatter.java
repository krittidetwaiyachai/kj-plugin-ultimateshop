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
 
     
     public List<String> getPriceLore(ShopItem item) {
         List<String> priceLore = new ArrayList<>();
 
         
         double buyPrice = plugin.getDynamicPriceManager().getBuyPrice(item);
         double sellPrice = plugin.getDynamicPriceManager().getSellPrice(item);
         String symbol = plugin.getCurrencyService().getCurrencySymbol(item.getCurrencyId());
         String buyStr = PriceUtil.format(buyPrice);
         String sellStr = PriceUtil.format(sellPrice);
 
         if (item.isAllowBuy()) {
             priceLore.add(translateColors("&7Buy Price: &a" + symbol + buyStr));
         }
         if (item.isAllowSell()) {
             priceLore.add(translateColors("&7Sell Price: &c" + symbol + sellStr));
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