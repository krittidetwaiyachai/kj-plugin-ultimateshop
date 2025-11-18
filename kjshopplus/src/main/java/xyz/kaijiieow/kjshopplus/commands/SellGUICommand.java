package xyz.kaijiieow.kjshopplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

public class SellGUICommand implements CommandExecutor {

    private final KJShopPlus plugin;

    public SellGUICommand(KJShopPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        
        // Open sell GUI - use "main" as default category, or first category if available
        String categoryId = "main";
        if (args.length > 0) {
            categoryId = args[0];
        } else {
            // Try to get first available category
            var categories = plugin.getShopManager().getAllCategoryIds();
            if (!categories.isEmpty()) {
                categoryId = categories.iterator().next();
            }
        }
        
        plugin.getGuiManager().openSellGUI(player, categoryId);
        return true;
    }
}

