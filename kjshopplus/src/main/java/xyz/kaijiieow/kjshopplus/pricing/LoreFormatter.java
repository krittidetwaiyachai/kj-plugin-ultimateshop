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
         if (text == null) return ""; // Safety check
         return ChatColor.translateAlternateColorCodes('&', text);
     }
 
     /**
      * Formats the FULL lore, including base lore + price info.
      * Used for VANILLA items.
      */
     public List<String> formatItemLore(ShopItem item) {
         List<String> lore = new ArrayList<>();
         
         // 1. Add base lore (from config's display.lore)
         List<String> configLore = item.getConfigBaseLore();
         if (configLore != null) {
             lore.addAll(configLore.stream()
                     .map(this::translateColors)
                     .collect(Collectors.toList()));
         }
 
         // 2. Add price lore
         lore.addAll(getPriceLore(item)); // Calls the new method
 
         return lore;
     }
 
     /**
      * Formats ONLY the price/dynamic info.
      * Used for CUSTOM items to append to their existing lore.
      */
     public List<String> getPriceLore(ShopItem item) {
         List<String> priceLore = new ArrayList<>();
 
         // 1. Add prices (translated)
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
         
         // 2. Add dynamic status (translated)
         if (item.isDynamicEnabled()) {
             priceLore.add(translateColors("&8(Dynamic Pricing Active)"));
         } else {
             // Only add "Static Pricing" if at least one price is set, otherwise it's just clutter
             if (item.isAllowBuy() || item.isAllowSell()) {
                  priceLore.add(translateColors("&8(Static Pricing)"));
             }
         }
 
         return priceLore;
     }
 }

