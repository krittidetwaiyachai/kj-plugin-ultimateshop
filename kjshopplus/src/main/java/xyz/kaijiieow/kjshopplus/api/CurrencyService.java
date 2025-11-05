package xyz.kaijiieow.kjshopplus.api;

import org.bukkit.entity.Player;
import java.util.UUID;

public interface CurrencyService {
    
    
    double getBalance(Player player, String currencyId);
    boolean hasBalance(Player player, String currencyId, double amount);
    boolean addBalance(Player player, String currencyId, double amount);
    boolean removeBalance(Player player, String currencyId, double amount);
    
    
    double getBalance(UUID uuid, String currencyId);
    boolean addBalance(UUID uuid, String currencyId, double amount);
    boolean removeBalance(UUID uuid, String currencyId, double amount);
    
    
    String getCurrencySymbol(String currencyId);
}