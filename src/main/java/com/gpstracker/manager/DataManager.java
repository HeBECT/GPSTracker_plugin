package com.gpstracker.manager;

import com.gpstracker.GPSPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Менеджер данных — хранение списков друзей в YAML файле.
 * Все операции записи происходят на основном потоке (Bukkit Main Thread),
 * поэтому синхронизация не требуется.
 */
public final class DataManager {

    private final GPSPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    public DataManager(GPSPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        loadData();
    }

    /**
     * Загрузка данных из файла.
     */
    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
                plugin.getLogger().info("Created data.yml");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create data.yml!", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * Получить множество UUID друзей игрока.
     */
    public Set<UUID> getFriends(UUID playerUUID) {
        Set<UUID> friends = new LinkedHashSet<>();
        String path = "players." + playerUUID.toString() + ".friends";
        List<String> list = dataConfig.getStringList(path);
        for (String s : list) {
            try {
                friends.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // Пропускаем невалидные UUID
            }
        }
        return friends;
    }

    /**
     * Добавить взаимную дружбу между двумя игроками.
     */
    public void addFriend(UUID player, UUID friend) {
        // Добавляем friend в список player
        Set<UUID> playerFriends = getFriends(player);
        playerFriends.add(friend);
        setFriendsList(player, playerFriends);

        // Добавляем player в список friend (взаимная связь)
        Set<UUID> friendFriends = getFriends(friend);
        friendFriends.add(player);
        setFriendsList(friend, friendFriends);

        saveData();
    }

    /**
     * Удалить взаимную дружбу между двумя игроками.
     */
    public void removeFriend(UUID player, UUID friend) {
        Set<UUID> playerFriends = getFriends(player);
        playerFriends.remove(friend);
        setFriendsList(player, playerFriends);

        Set<UUID> friendFriends = getFriends(friend);
        friendFriends.remove(player);
        setFriendsList(friend, friendFriends);

        saveData();
    }

    /**
     * Проверить, являются ли два игрока друзьями.
     */
    public boolean areFriends(UUID player1, UUID player2) {
        return getFriends(player1).contains(player2);
    }

    /**
     * Записать список друзей в конфигурацию (без сохранения на диск).
     */
    private void setFriendsList(UUID player, Set<UUID> friends) {
        String path = "players." + player.toString() + ".friends";
        List<String> list = new ArrayList<>();
        for (UUID uuid : friends) {
            list.add(uuid.toString());
        }
        dataConfig.set(path, list);
    }

    /**
     * Сохранить данные на диск.
     */
    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data.yml!", e);
        }
    }

    /**
     * Перезагрузить данные с диска.
     */
    public void reloadData() {
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
}