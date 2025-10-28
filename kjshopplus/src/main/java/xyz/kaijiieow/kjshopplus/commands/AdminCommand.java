package xyz.kaijiieow.kjshopplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections; // <-- IMPORT เพิ่ม
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors; // <-- IMPORT เพิ่ม

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
                // --- FIX: เปลี่ยน Map.of (Java 9+) เป็น Collections.singletonMap (Java 8) ---
                plugin.getMessageManager().sendMessage(sender, "price_reset_manual", Collections.singletonMap("count", String.valueOf(count)));
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
            // --- FIX: เปลี่ยน .toList() (Java 16+) เป็น .collect(Collectors.toList()) (Java 8) ---
            return subCommands.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        // --- FIX: เปลี่ยน List.of() (Java 9+) เป็น Collections.emptyList() (Java 8) ---
        return Collections.emptyList();
    }
}
