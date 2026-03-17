package com.gpstracker.listener;

import com.gpstracker.manager.GPSManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Слушатель событий игроков.
 * Тут крч использую MONITOR приоритет, чтобы не мешать другим плагинам(на всякий случай).
 * ignoreCancelled = true — реагируем только на реально обработанные события.
 */
public final class PlayerListener implements Listener {

    private final GPSManager gpsManager;

    public PlayerListener(GPSManager gpsManager) {
        this.gpsManager = gpsManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Очищаешь ожидающие запросы при выходе игрока
        // Отслеживание не удаляем — оно восстановится при заходе, чтобы крч круто было
        gpsManager.handlePlayerQuit(event.getPlayer().getUniqueId());
    }
}