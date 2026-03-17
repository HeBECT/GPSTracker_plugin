package com.gpstracker.task;

import com.gpstracker.GPSPlugin;
import com.gpstracker.manager.GPSManager;
import com.gpstracker.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

/**
 * Задача обновления Action Bar навигации.
 * Выполняется каждые N тиков (настраивается в конфиге).
 * Показывает стрелку направления и расстояние до отслеживаемого игрока.
 * Плагин написан крутым челом(мной)
 * (Вест ник если что, в майнкрафте WeastBr или HeBECT)
 */
public final class NavigationTask extends BukkitRunnable {

    private final GPSPlugin plugin;
    private final GPSManager gpsManager;

    // Символы стрелок для 8 направлений (8 направлений по часовой стрелке от "Вперёд")
    private static final String[] ARROWS = {
            "⬆",  // Вперёд (0°)
            "⬈",  // Вперёд-вправо (45°)
            "➡",  // Вправо (90°)
            "⬊",  // Назад-вправо (135°)
            "⬇",  // Назад (180°)
            "⬋",  // Назад-влево (225°)
            "⬅",  // Влево (270°)
            "⬉"   // Вперёд-влево (315°)
    };

    public NavigationTask(GPSPlugin plugin, GPSManager gpsManager) {
        this.plugin = plugin;
        this.gpsManager = gpsManager;
    }

    @Override
    public void run() {
        Map<UUID, UUID> tracking = gpsManager.getActiveTracking();

        for (Map.Entry<UUID, UUID> entry : tracking.entrySet()) {
            UUID trackerUUID = entry.getKey();
            UUID targetUUID = entry.getValue();

            // Проверяем, включена ли навигация
            if (!gpsManager.isNavigationEnabled(trackerUUID)) {
                continue;
            }

            Player tracker = Bukkit.getPlayer(trackerUUID);
            if (tracker == null || !tracker.isOnline()) {
                continue;
            }

            Player target = Bukkit.getPlayer(targetUUID);

            // Цель оффлайн
            if (target == null || !target.isOnline()) {
                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetUUID);
                String name = offlineTarget.getName();
                if (name == null) name = "Unknown";

                String msg = plugin.getConfig().getString("messages.target-offline-actionbar",
                        "<gradient:#ff6600:#ffcc00>⚠ {player} — не в сети</gradient>");
                msg = msg.replace("{player}", name);
                MessageUtils.sendActionBar(tracker, msg);
                continue;
            }

            // Разные миры
            if (!tracker.getWorld().equals(target.getWorld())) {
                String msg = plugin.getConfig().getString("messages.different-world-actionbar",
                        "<gradient:#ff6600:#ffcc00>⚠ {player} — другой мир</gradient>");
                msg = msg.replace("{player}", target.getName());
                MessageUtils.sendActionBar(tracker, msg);
                continue;
            }

            // Вычисляем расстояние и направление
            Location trackerLoc = tracker.getLocation();
            Location targetLoc = target.getLocation();

            double distance = trackerLoc.distance(targetLoc);

            // Проверка максимальной дистанции
            double maxDistance = plugin.getConfig().getDouble("settings.max-tracking-distance", -1);
            if (maxDistance > 0 && distance > maxDistance) {
                String msg = plugin.getConfig().getString("messages.different-world-actionbar",
                        "<gradient:#ff6600:#ffcc00>⚠ {player} — слишком далеко</gradient>");
                msg = msg.replace("{player}", target.getName());
                MessageUtils.sendActionBar(tracker, msg);
                continue;
            }

            // Определяем стрелку направления
            String arrow = getDirectionArrow(trackerLoc, targetLoc);

            // Форматируем Action Bar
            String format = plugin.getConfig().getString("messages.navigation-actionbar",
                    "<gradient:#00ff88:#00ccff>{arrow} {player} • {distance}м</gradient>");

            format = format
                    .replace("{arrow}", arrow)
                    .replace("{player}", target.getName())
                    .replace("{distance}", String.format("%.0f", distance));

            MessageUtils.sendActionBar(tracker, format);
        }
    }

    /**
     * Определить символ стрелки направления от игрока к цели.
     *
     * Алгоритм:
     * 1. Вычисляем угол от игрока к цели в мировых координатах (0° = север, по часовой)
     * 2. Вычисляем куда смотрит игрок (конвертируем Minecraft yaw)
     * 3. Находим относительный угол (0° = прямо перед игроком)
     * 4. Выбираем одну из 8 стрелок
     * Надеюсь кому-то пригодятся эти гайды (этот кусок кода писался с помощью нейросети)
     */
    private String getDirectionArrow(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        // Угол к цели в мировых координатах (0° = север/-Z, по часовой стрелке)
        double targetAngle = Math.toDegrees(Math.atan2(dx, -dz));
        if (targetAngle < 0) targetAngle += 360.0;

        // Конвертация Minecraft yaw в тот же формат
        // Minecraft: 0 = юг(+Z), 90 = запад(-X), ±180 = север(-Z), -90 = восток(+X)
        // Нужно: 0 = север, 90 = восток, 180 = юг, 270 = запад
        double playerYaw = from.getYaw();
        double playerAngle = ((playerYaw + 180.0) % 360.0 + 360.0) % 360.0;

        // Относительный угол: 0° = прямо перед игроком, 90° = справа и т.д.
        double relative = ((targetAngle - playerAngle) % 360.0 + 360.0) % 360.0;

        // Выбираем стрелку (каждая покрывает сектор 45°)
        int index = (int) Math.round(relative / 45.0) % 8;
        return ARROWS[index];
    }
}