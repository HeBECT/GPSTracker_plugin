package com.gpstracker;

import com.gpstracker.command.GPSCommand;
import com.gpstracker.listener.PlayerListener;
import com.gpstracker.manager.DataManager;
import com.gpstracker.manager.GPSManager;
import com.gpstracker.task.NavigationTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class GPSPlugin extends JavaPlugin {

    private static GPSPlugin instance;

    private DataManager dataManager;
    private GPSManager gpsManager;
    private NavigationTask navigationTask;

    @Override
    public void onEnable() {
        instance = this;

        // Сохраняем конфиг по умолчанию, но только если его нет
        saveDefaultConfig();

        // Инициализация менеджеров
        this.dataManager = new DataManager(this);
        this.gpsManager = new GPSManager(this, dataManager);

        // Регистрация команды
        GPSCommand gpsCommand = new GPSCommand(this, gpsManager);
        var command = getCommand("gps");
        if (command != null) {
            command.setExecutor(gpsCommand);
            command.setTabCompleter(gpsCommand);
        } else {
            getLogger().severe("Failed to register /gps command! Check plugin.yml");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Регистрация слушателя событий
        Bukkit.getPluginManager().registerEvents(new PlayerListener(gpsManager), this);

        // Запуск задачи навигации (Action Bar)
        long updateInterval = getConfig().getLong("settings.update-interval", 10L);
        this.navigationTask = new NavigationTask(this, gpsManager);
        this.navigationTask.runTaskTimer(this, 20L, updateInterval);

        // Автосохранение данных
        long autosaveInterval = getConfig().getLong("settings.autosave-interval", 300L) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            dataManager.saveData();
            getLogger().info("GPS data auto-saved.");
        }, autosaveInterval, autosaveInterval);

        getLogger().info("═══════════════════════════════");
        getLogger().info("  GPSTracker v" + getDescription().getVersion());
        getLogger().info("  Plugin enabled successfully!");
        getLogger().info("  Server: Purpur 1.20.1");
        getLogger().info("═══════════════════════════════");
    }

    @Override
    public void onDisable() {
        // Останавливаем задачу навигации
        if (navigationTask != null && !navigationTask.isCancelled()) {
            navigationTask.cancel();
        }

        // Сохраняем все данные
        if (dataManager != null) {
            dataManager.saveData();
        }

        getLogger().info("GPSTracker disabled. Data saved.");
        instance = null;
    }

    /**
     * Перезагрузка конфига плагина.
     * Безопасно вызывается из команды /gps reload.
     */
    public void reloadGPSConfig() {
        reloadConfig();

        // Перезапуск задачи навигации с новым интервалом
        if (navigationTask != null && !navigationTask.isCancelled()) {
            navigationTask.cancel();
        }
        long updateInterval = getConfig().getLong("settings.update-interval", 10L);
        this.navigationTask = new NavigationTask(this, gpsManager);
        this.navigationTask.runTaskTimer(this, 20L, updateInterval);
    }

    public static GPSPlugin getInstance() {
        return instance;
    }

    public GPSManager getGPSManager() {
        return gpsManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}