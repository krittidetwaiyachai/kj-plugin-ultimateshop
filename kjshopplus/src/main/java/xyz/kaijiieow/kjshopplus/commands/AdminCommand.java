package xyz.kaijiieow.kjshopplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map; // <-- IMPORT MISSING CLASS

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final KJShopPlus plugin;

    public AdminCommand(KJShopPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.getMessageManager().sendMessage(sender, "invalid_command");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("kjshopplus.admin.reload")) {
                    plugin.getMessageManager().sendMessage(sender, "no_permission");
                    return true;
                }
                plugin.reload();
                plugin.getMessageManager().sendMessage(sender, "reload_done");
                if (sender instanceof Player) {
                    plugin.getDiscordWebhookService().logAdmin((Player) sender, "Reloaded plugin configuration");
                }
                break;

            case "resetprices":
                if (!sender.hasPermission("kjshopplus.admin.resetprices")) {
                     plugin.getMessageManager().sendMessage(sender, "no_permission");
                     return true;
                }
                int count = plugin.getDynamicPriceManager().resetAllPricesNow();
                // Use Map.of() to create placeholders
                plugin.getMessageManager().sendMessage(sender, "price_reset_manual", Map.of("count", String.valueOf(count)));
                if (sender instanceof Player) {
                     plugin.getDiscordWebhookService().logAdmin((Player) sender, "Manually reset " + count + " dynamic prices");
                }
                 plugin.getDiscordWebhookService().logPriceReset(count);
                 break;

            case "help":
                 plugin.getMessageManager().sendMessage(sender, "invalid_command"); // Placeholder
                 break;

            default:
                plugin.getMessageManager().sendMessage(sender, "invalid_command");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("reload", "resetprices", "help"));
            subCommands.removeIf(sub -> !sender.hasPermission("kjshopplus.admin." + sub));
            // Use stream().filter().toList() for modern Java
            return subCommands.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        // Return empty list for subsequent arguments
        return List.of();
    }
}

