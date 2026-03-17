package com.gpstracker.manager;

import com.gpstracker.GPSPlugin;
import com.gpstracker.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Основной менеджер GPS: запросы дружбы, отслеживание, навигация.
 * ConcurrentHashMap используется для потокобезопасности на случай
 * асинхронных обращений (хотя все операции идут из Main Thread).
 */
public final class GPSManager {

    private final GPSPlugin plugin;
    private final DataManager dataManager;

    /**
     * Ожидающие запросы: targetUUID -> senderUUID.
     * Один входящий запрос на игрока (последний перезаписывает).
     */
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Активное отслеживание: trackerUUID -> targetUUID.
     */
    private final Map<UUID, UUID> activeTracking = new ConcurrentHashMap<>();

    /**
     * Состояние навигации: множество UUID с включённой навигацией.
     *(я заебался писать комментарии, но я в будущем забуду что тут к чему).
     *Плагин написан Вестом(то есть мной великим).
     */
    private final Set<UUID> navigationEnabled = ConcurrentHashMap.newKeySet();

    public GPSManager(GPSPlugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    // ========== Запросы дружбы ==========

    /**
     * Отправить запрос на дружбу.
     * @return true если запрос отправлен успешно
     */
    public boolean sendRequest(Player sender, Player target) {
        UUID senderUUID = sender.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        if (senderUUID.equals(targetUUID)) {
            return false;
        }

        if (dataManager.areFriends(senderUUID, targetUUID)) {
            return false;
        }

        // Проверяем, не отправлен ли уже запрос этому игроку от этого же отправителя
        UUID existingRequest = pendingRequests.get(targetUUID);
        if (existingRequest != null && existingRequest.equals(senderUUID)) {
            return false; // Уже отправлен
        }

        pendingRequests.put(targetUUID, senderUUID);

        // Автоудаление запроса через заданное время
        long expireSeconds = plugin.getConfig().getLong("settings.request-expire-seconds", 60);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID current = pendingRequests.get(targetUUID);
            if (current != null && current.equals(senderUUID)) {
                pendingRequests.remove(targetUUID);

                // Уведомляем отправителя, если он онлайн
                Player senderPlayer = Bukkit.getPlayer(senderUUID);
                if (senderPlayer != null && senderPlayer.isOnline()) {
                    String msg = plugin.getConfig().getString("messages.request-expired",
                            "<gradient:#ff6600:#ffcc00>⏰ Запрос к {player} истёк!</gradient>");
                    MessageUtils.sendPrefixedMessage(plugin, senderPlayer,
                            msg.replace("{player}", target.getName()));
                }
            }
        }, expireSeconds * 20L);

        return true;
    }

    /**
     * Получить UUID отправителя ожидающего запроса для данного игрока.
     */
    public UUID getPendingRequest(UUID targetUUID) {
        return pendingRequests.get(targetUUID);
    }

    /**
     * Принять ожидающий запрос.
     */
    public UUID acceptRequest(UUID targetUUID) {
        UUID senderUUID = pendingRequests.remove(targetUUID);
        if (senderUUID != null) {
            dataManager.addFriend(senderUUID, targetUUID);
        }
        return senderUUID;
    }

    /**
     * Отклонить ожидающий запрос.
     */
    public UUID denyRequest(UUID targetUUID) {
        return pendingRequests.remove(targetUUID);
    }

    // ========== Друзья ==========

    public boolean areFriends(UUID player1, UUID player2) {
        return dataManager.areFriends(player1, player2);
    }

    public Set<UUID> getFriends(UUID playerUUID) {
        return dataManager.getFriends(playerUUID);
    }

    public void removeFriend(UUID player, UUID friend) {
        dataManager.removeFriend(player, friend);

        // Останавливаем навигацию, если отслеживали удалённого друга
        UUID tracked1 = activeTracking.get(player);
        if (tracked1 != null && tracked1.equals(friend)) {
            activeTracking.remove(player);
        }

        UUID tracked2 = activeTracking.get(friend);
        if (tracked2 != null && tracked2.equals(player)) {
            activeTracking.remove(friend);
        }
    }

    // ========== Навигация ==========

    /**
     * Начать отслеживание целевого игрока.
     */
    public void startTracking(UUID trackerUUID, UUID targetUUID) {
        activeTracking.put(trackerUUID, targetUUID);
        navigationEnabled.add(trackerUUID);
    }

    /**
     * Остановить отслеживание.
     */
    public void stopTracking(UUID trackerUUID) {
        activeTracking.remove(trackerUUID);
    }

    /**
     * Получить UUID отслеживаемого игрока.
     */
    public UUID getTrackedPlayer(UUID trackerUUID) {
        return activeTracking.get(trackerUUID);
    }

    /**
     * Проверить, включена ли навигация у игрока.
     */
    public boolean isNavigationEnabled(UUID playerUUID) {
        return navigationEnabled.contains(playerUUID);
    }

    /**
     * Переключить состояние навигации.
     * @return новое состояние (true = включено)
     */
    public boolean toggleNavigation(UUID playerUUID) {
        if (navigationEnabled.contains(playerUUID)) {
            navigationEnabled.remove(playerUUID);
            return false;
        } else {
            navigationEnabled.add(playerUUID);
            return true;
        }
    }

    /**
     * Получить неизменяемую копию карты активного отслеживания.
     */
    public Map<UUID, UUID> getActiveTracking() {
        return Collections.unmodifiableMap(activeTracking);
    }

    /**
     * Очистка данных при выходе игрока (опционально).
     * Не удаляем отслеживание — оно сохраняется до перезагрузки.
     */
    public void handlePlayerQuit(UUID playerUUID) {
        pendingRequests.remove(playerUUID);
        // Не удаляем activeTracking — пусть сохраняется при релогине
    }
}