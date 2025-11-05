package xyz.kaijiieow.kjshopplus.gui.util;

import org.bukkit.scheduler.BukkitRunnable;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTapManager {

    private final KJShopPlus plugin;
    private final Map<UUID, Long> lastTapTime = new ConcurrentHashMap<>();

    
    
    public PlayerTapManager(KJShopPlus plugin) {
        this.plugin = plugin;
    }
    

    public boolean canTap(UUID uuid, long debounceMs) {
        if (debounceMs <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        Long lastTap = lastTapTime.get(uuid);

        if (lastTap == null || (now - lastTap) > debounceMs) {
            lastTapTime.put(uuid, now);
            return true;
        }

        
        return false;
    }

    public void clear(UUID uuid) {
        lastTapTime.remove(uuid);
    }
}