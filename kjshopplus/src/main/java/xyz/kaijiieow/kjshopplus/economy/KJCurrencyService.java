package xyz.kaijiieow.kjshopplus.economy;

import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import xyz.kaijiieow.kjshopplus.KJShopPlus;
import xyz.kaijiieow.kjshopplus.api.CurrencyService;

import java.util.UUID;

public final class KJCurrencyService implements CurrencyService {
    
    private final Economy vault;
    private final KJShopPlus plugin;

    public KJCurrencyService(Economy vaultEconomy, KJShopPlus plugin) {
        this.vault = vaultEconomy;
        this.plugin = plugin;
    }

    
    @Override
    public double getBalance(Player player, String currencyId) {
        if (player == null) return 0D;
        return getBalance(player.getUniqueId(), currencyId);
    }

    @Override
    public boolean hasBalance(Player player, String currencyId, double amount) {
        if (player == null) return false;
        return getBalance(player.getUniqueId(), currencyId) >= amount;
    }

    @Override
    public boolean addBalance(Player player, String currencyId, double amount) {
         if (player == null) return false;
        return addBalance(player.getUniqueId(), currencyId, amount);
    }

    @Override
    public boolean removeBalance(Player player, String currencyId, double amount) {
        if (player == null) return false;
        return removeBalance(player.getUniqueId(), currencyId, amount);
    }

    
    @Override
    public double getBalance(UUID uuid, String currencyId) {
        if (uuid == null) return 0D;
        
        if (currencyId.equalsIgnoreCase("vault")) {
            return vault.getBalance(Bukkit.getOfflinePlayer(uuid));
        }
        
        if (Bukkit.getPluginManager().getPlugin("CoinsEngine") != null) {
            try {
                var cur = CoinsEngineAPI.getCurrency(currencyId);
                return cur != null ? CoinsEngineAPI.getBalance(uuid, cur) : 0D;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get CoinsEngine balance: " + e.getMessage());
                return 0D;
            }
        }
        return 0D;
    }
    
    @Override
    public boolean addBalance(UUID uuid, String currencyId, double amount) {
        if (uuid == null || amount < 0) return false;

        if (currencyId.equalsIgnoreCase("vault")) {
            return vault.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess();
        }

        if (Bukkit.getPluginManager().getPlugin("CoinsEngine") != null) {
            try {
                var cur = CoinsEngineAPI.getCurrency(currencyId);
                if (cur == null) return false;
                CoinsEngineAPI.addBalance(uuid, cur, amount);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to add CoinsEngine balance: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean removeBalance(UUID uuid, String currencyId, double amount) {
        if (uuid == null || amount < 0) return false;
        
        
        if (getBalance(uuid, currencyId) < amount) {
            return false;
        }

        if (currencyId.equalsIgnoreCase("vault")) {
            return vault.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess();
        }

        if (Bukkit.getPluginManager().getPlugin("CoinsEngine") != null) {
            try {
                var cur = CoinsEngineAPI.getCurrency(currencyId);
                if (cur == null) return false;
                CoinsEngineAPI.removeBalance(uuid, cur, amount);
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to remove CoinsEngine balance: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    @Override
    public String getCurrencySymbol(String currencyId) {
        return plugin.getConfigManager().getCurrencyDisplayName(currencyId);
    }
}