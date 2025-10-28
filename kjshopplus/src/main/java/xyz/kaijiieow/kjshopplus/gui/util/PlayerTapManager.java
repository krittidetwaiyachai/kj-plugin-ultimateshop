package xyz.kaijiieow.kjshopplus.gui.util;

import org.bukkit.scheduler.BukkitRunnable;
import xyz.kaijiieow.kjshopplus.KJShopPlus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTapManager {

    private final KJShopPlus plugin;
    private final Map<UUID, Long> lastTapTime = new ConcurrentHashMap<>();

    // --- THIS IS THE FIX ---
    // The constructor now accepts the KJShopPlus plugin instance
    public PlayerTapManager(KJShopPlus plugin) {
        this.plugin = plugin;
    }
    // --- END FIX ---

    public boolean canTap(UUID uuid, long debounceMs) {
        if (debounceMs <= 0) {
            return true; // Debounce disabled
        }

        long now = System.currentTimeMillis();
        Long lastTap = lastTapTime.get(uuid);

        if (lastTap == null || (now - lastTap) > debounceMs) {
            lastTapTime.put(uuid, now);
            return true;
        }

        // Tapped too fast
        return false;
    }

    public void clear(UUID uuid) {
        lastTapTime.remove(uuid);
    }
}

