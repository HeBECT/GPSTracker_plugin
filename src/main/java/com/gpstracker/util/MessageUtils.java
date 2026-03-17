package com.gpstracker.util;

import com.gpstracker.GPSPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Утилита для работы с сообщениями.
 * Использует MiniMessage (встроен в Paper/Purpur 1.20.1) для поддержки
 * градиентов, HEX цветов, hover/click событий.
 */
public final class MessageUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtils() {
        // Utility class
    }

    /**
     * Парсинг строки MiniMessage в Adventure Component.
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(message);
        } catch (Exception e) {
            // Фолбэк на legacy формат, если MiniMessage не может распарсить
            return LEGACY.deserialize(message);
        }
    }

    /**
     * Отправить сообщение игроку (без префикса).
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        player.sendMessage(parse(message));
    }

    /**
     * Отправить сообщение игроку с префиксом из конфига.
     */
    public static void sendPrefixedMessage(GPSPlugin plugin, Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        player.sendMessage(parse(prefix + message));
    }

    /**
     * Отправить Action Bar игроку.
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        player.sendActionBar(parse(message));
    }

    /**
     * Отправить Action Bar как Component.
     */
    public static void sendActionBar(Player player, Component component) {
        if (player == null || component == null) return;
        player.sendActionBar(component);
    }
}