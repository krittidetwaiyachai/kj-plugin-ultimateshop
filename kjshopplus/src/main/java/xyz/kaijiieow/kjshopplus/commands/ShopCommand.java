package xyz.kaijiieow.kjshopplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
public class ShopCommand implements CommandExecutor {

    private final KJShopPlus plugin;

    public ShopCommand(KJShopPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        
        if (args.length == 0) {
            plugin.getGuiManager().openCategoryMenu(player);
            return true;
        }

        // Optional: Open a specific category directly
        // /shop <category_id>
        String categoryId = args[0];
        if (plugin.getShopManager().getShopCategory(categoryId) != null) {
            // --- เปิดหน้าหมวดหมู่ โดยบังคับเป็น Buy Mode เสมอ ---
            plugin.getGuiManager().openShopPage(player, categoryId, 1, true);
        } else {
            plugin.getGuiManager().openCategoryMenu(player);
        }
        
        return true;
    }
}

