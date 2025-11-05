package xyz.kaijiieow.kjshopplus.commands;

 import org.bukkit.Material;
 import org.bukkit.command.Command;
 import org.bukkit.command.CommandExecutor;
 import org.bukkit.command.CommandSender;
 import org.bukkit.command.TabCompleter;
 import org.bukkit.entity.Player;
 import org.bukkit.inventory.ItemStack;
 import xyz.kaijiieow.kjshopplus.KJShopPlus;
 import xyz.kaijiieow.kjshopplus.config.model.ShopCategory;
 import java.util.ArrayList;

 import java.util.ArrayList;
 import java.util.Arrays;
 import java.util.Collections;
 import java.util.List;
 import java.util.stream.Collectors;

 public class AdminCommand implements CommandExecutor, TabCompleter {

     private final KJShopPlus plugin;

     public AdminCommand(KJShopPlus plugin) {
         this.plugin = plugin;
     }

     @Override
     public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
         if (args.length == 0) {
             sendUsage(sender);
             return true;
         }

         String subCommand = args[0].toLowerCase();

         switch (subCommand) {
             case "reload":
                 handleReload(sender);
                 break;

             case "resetprices":
                 handleResetPrices(sender);
                 break;

             case "additem":
                 handleAddItem(sender, args);
                 break;

             case "help":
             default:
                 sendUsage(sender);
                 break;
         }

         return true;
     }

     private void handleReload(CommandSender sender) {
         if (!sender.hasPermission("kjshopplus.admin.reload")) {
             plugin.getMessageManager().sendMessage(sender, "no_permission");
             return;
         }
         plugin.reload();
         plugin.getMessageManager().sendMessage(sender, "reload_done");
         if (sender instanceof Player player) {
             plugin.getDiscordWebhookService().logAdmin(player, "Reloaded plugin configuration");
         }
     }

     private void handleResetPrices(CommandSender sender) {
         if (!sender.hasPermission("kjshopplus.admin.resetprices")) {
             plugin.getMessageManager().sendMessage(sender, "no_permission");
             return;
         }
         int count = plugin.getDynamicPriceManager().resetAllPricesNow();
         plugin.getMessageManager().sendMessage(sender, "price_reset_manual", Collections.singletonMap("count", String.valueOf(count)));
         if (sender instanceof Player player) {
             plugin.getDiscordWebhookService().logAdmin(player, "Manually reset " + count + " dynamic prices");
         }
         plugin.getDiscordWebhookService().logPriceReset(count);
     }

     private void handleAddItem(CommandSender sender, String[] args) {
         if (!(sender instanceof Player player)) {
             plugin.getMessageManager().sendMessage(sender, "player_only_command");
             return;
         }
         if (!player.hasPermission("kjshopplus.admin.additem")) {
             plugin.getMessageManager().sendMessage(sender, "no_permission");
             return;
         }
         if (args.length != 2) {
             plugin.getMessageManager().sendMessage(sender, "additem_usage");
             return;
         }

         ItemStack itemInHand = player.getInventory().getItemInMainHand();
         if (itemInHand == null || itemInHand.getType() == Material.AIR) {
             plugin.getMessageManager().sendMessage(sender, "additem_no_item");
             return;
         }

         String categoryId = args[1].toLowerCase();
         ShopCategory category = plugin.getShopManager().getShopCategory(categoryId);
         if (category == null) {
             plugin.getMessageManager().sendMessage(sender, "category_not_found", Collections.singletonMap("category", categoryId));
             return;
         }

         
         boolean success = plugin.getShopManager().addItemStackToCategory(categoryId, itemInHand);

         if (success) {
             plugin.getMessageManager().sendMessage(sender, "additem_success", Collections.singletonMap("category", categoryId));
             
             plugin.getMessageManager().sendMessage(sender, "additem_reload_suggestion");
         } else {
             plugin.getMessageManager().sendMessage(sender, "additem_failed");
         }
     }

     private void sendUsage(CommandSender sender) {
         plugin.getMessageManager().sendMessage(sender, "admin_usage_header");
         if (sender.hasPermission("kjshopplus.admin.reload")) {
             plugin.getMessageManager().sendMessage(sender, "admin_usage_reload");
         }
         if (sender.hasPermission("kjshopplus.admin.resetprices")) {
             plugin.getMessageManager().sendMessage(sender, "admin_usage_resetprices");
         }
         if (sender.hasPermission("kjshopplus.admin.additem")) {
             plugin.getMessageManager().sendMessage(sender, "admin_usage_additem");
         }
         plugin.getMessageManager().sendMessage(sender, "admin_usage_help");
     }


     @Override
     public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
         if (args.length == 1) {
             List<String> subCommands = new ArrayList<>(Arrays.asList("reload", "resetprices", "additem", "help"));
             
             subCommands.removeIf(sub -> !sender.hasPermission("kjshopplus.admin." + sub.toLowerCase()));
             return subCommands.stream()
                     .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                     .collect(Collectors.toList());
         }

         if (args.length == 2 && args[0].equalsIgnoreCase("additem")) {
             if (sender.hasPermission("kjshopplus.admin.additem")) {
                 
                 return plugin.getShopManager().getAllCategoryIds().stream()
                         .filter(catId -> catId.toLowerCase().startsWith(args[1].toLowerCase()))
                         .collect(Collectors.toList());
             }
         }

         return Collections.emptyList();
     }
 }