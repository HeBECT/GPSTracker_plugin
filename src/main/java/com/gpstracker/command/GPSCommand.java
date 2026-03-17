package com.gpstracker.command;

import com.gpstracker.GPSPlugin;
import com.gpstracker.manager.GPSManager;
import com.gpstracker.util.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class GPSCommand implements CommandExecutor, TabCompleter {

    private final GPSPlugin plugin;
    private final GPSManager gpsManager;

    public GPSCommand(GPSPlugin plugin, GPSManager gpsManager) {
        this.plugin = plugin;
        this.gpsManager = gpsManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("gps.use")) {
            sendMsg(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add"    -> handleAdd(player, args);
            case "accept" -> handleAccept(player);
            case "deny"   -> handleDeny(player);
            case "list"   -> handleList(player);
            case "track"  -> handleTrack(player, args);
            case "toggle" -> handleToggle(player);
            case "remove" -> handleRemove(player, args);
            case "reload" -> handleReload(player);
            case "help"   -> sendHelp(player);
            default       -> sendHelp(player);
        }

        return true;
    }

    // ========== ADD ==========
    private void handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            sendMsg(player, "usage-add");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sendMsg(player, "player-not-found");
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            sendMsg(player, "cannot-add-self");
            return;
        }

        if (gpsManager.areFriends(player.getUniqueId(), target.getUniqueId())) {
            sendMsg(player, "already-friends");
            return;
        }

        // Проверяем, не отправлен ли уже запрос
        boolean sent = gpsManager.sendRequest(player, target);
        if (!sent) {
            sendMsg(player, "already-sent-request");
            return;
        }

        // Уведомление отправителю
        String sentMsg = getMsg("request-sent").replace("{player}", target.getName());
        sendPrefixed(player, sentMsg);

        // Уведомление получателю
        String receivedMsg = getMsg("request-received").replace("{player}", player.getName());
        sendPrefixed(target, receivedMsg);

        // Кнопки принять / отклонить
        String buttonsMsg = getMsg("request-buttons");
        MessageUtils.sendMessage(target, buttonsMsg);
    }

    // ========== ACCEPT ==========
    private void handleAccept(Player player) {
        UUID senderUUID = gpsManager.getPendingRequest(player.getUniqueId());
        if (senderUUID == null) {
            sendMsg(player, "no-pending-request");
            return;
        }

        UUID acceptedSender = gpsManager.acceptRequest(player.getUniqueId());
        if (acceptedSender == null) {
            sendMsg(player, "no-pending-request");
            return;
        }

        sendMsg(player, "request-accepted");

        Player sender = Bukkit.getPlayer(acceptedSender);
        if (sender != null && sender.isOnline()) {
            String msg = getMsg("request-accepted-sender").replace("{player}", player.getName());
            sendPrefixed(sender, msg);
        }
    }

    // ========== DENY ==========
    private void handleDeny(Player player) {
        UUID senderUUID = gpsManager.denyRequest(player.getUniqueId());
        if (senderUUID == null) {
            sendMsg(player, "no-pending-request");
            return;
        }

        sendMsg(player, "request-denied");

        Player sender = Bukkit.getPlayer(senderUUID);
        if (sender != null && sender.isOnline()) {
            String msg = getMsg("request-denied-sender").replace("{player}", player.getName());
            sendPrefixed(sender, msg);
        }
    }

    // ========== LIST ==========
    private void handleList(Player player) {
        Set<UUID> friends = gpsManager.getFriends(player.getUniqueId());

        if (friends.isEmpty()) {
            sendMsg(player, "no-friends");
            return;
        }

        sendMsg(player, "list-header");

        for (UUID friendUUID : friends) {
            Player friendPlayer = Bukkit.getPlayer(friendUUID);

            if (friendPlayer != null && friendPlayer.isOnline()) {
                // Онлайн
                if (player.getWorld().equals(friendPlayer.getWorld())) {
                    // Тот же мир — показываем координаты и дистанцию
                    Location loc = friendPlayer.getLocation();
                    double distance = player.getLocation().distance(loc);

                    String entry = getMsg("list-entry-online")
                            .replace("{player}", friendPlayer.getName())
                            .replace("{distance}", String.format("%.0f", distance))
                            .replace("{x}", String.valueOf(loc.getBlockX()))
                            .replace("{y}", String.valueOf(loc.getBlockY()))
                            .replace("{z}", String.valueOf(loc.getBlockZ()));

                    // Клик для навигации
                    Component comp = MessageUtils.parse(entry);
                    player.sendMessage(comp);
                } else {
                    // Другой мир
                    String entry = getMsg("list-entry-online-different-world")
                            .replace("{player}", friendPlayer.getName())
                            .replace("{world}", friendPlayer.getWorld().getName());
                    MessageUtils.sendMessage(player, entry);
                }
            } else {
                // Оффлайн
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(friendUUID);
                String name = offlinePlayer.getName();
                if (name == null) name = "Unknown";

                String entry = getMsg("list-entry-offline")
                        .replace("{player}", name);
                MessageUtils.sendMessage(player, entry);
            }
        }

        sendMsg(player, "list-footer");
    }

    // ========== TRACK ==========
    private void handleTrack(Player player, String[] args) {
        if (args.length < 2) {
            sendMsg(player, "usage-track");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sendMsg(player, "player-not-online");
            return;
        }

        if (!gpsManager.areFriends(player.getUniqueId(), target.getUniqueId())) {
            sendMsg(player, "not-friends");
            return;
        }

        // Проверка максимальной дистанции
        double maxDistance = plugin.getConfig().getDouble("settings.max-tracking-distance", -1);
        if (maxDistance > 0) {
            if (!player.getWorld().equals(target.getWorld())) {
                sendMsg(player, "different-world");
                return;
            }
            double distance = player.getLocation().distance(target.getLocation());
            if (distance > maxDistance) {
                String msg = getMsg("too-far")
                        .replace("{max}", String.format("%.0f", maxDistance));
                sendPrefixed(player, msg);
                return;
            }
        }

        gpsManager.startTracking(player.getUniqueId(), target.getUniqueId());

        String msg = getMsg("tracking-started").replace("{player}", target.getName());
        sendPrefixed(player, msg);
    }

    // ========== TOGGLE ==========
    private void handleToggle(Player player) {
        UUID trackedUUID = gpsManager.getTrackedPlayer(player.getUniqueId());
        if (trackedUUID == null) {
            sendMsg(player, "not-tracking-anyone");
            return;
        }

        boolean newState = gpsManager.toggleNavigation(player.getUniqueId());

        if (newState) {
            sendMsg(player, "navigation-enabled");
        } else {
            sendMsg(player, "navigation-disabled");
        }
    }

    // ========== REMOVE ==========
    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            sendMsg(player, "usage-remove");
            return;
        }

        String targetName = args[1];
        Set<UUID> friends = gpsManager.getFriends(player.getUniqueId());
        UUID targetUUID = null;

        for (UUID friendUUID : friends) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(friendUUID);
            String name = offlinePlayer.getName();
            if (name != null && name.equalsIgnoreCase(targetName)) {
                targetUUID = friendUUID;
                break;
            }
        }

        if (targetUUID == null) {
            sendMsg(player, "not-friends");
            return;
        }

        gpsManager.removeFriend(player.getUniqueId(), targetUUID);

        String msg = getMsg("friend-removed").replace("{player}", targetName);
        sendPrefixed(player, msg);
    }

    // ========== RELOAD ==========
    private void handleReload(Player player) {
        if (!player.hasPermission("gps.admin")) {
            sendMsg(player, "no-permission");
            return;
        }

        plugin.reloadGPSConfig();
        sendMsg(player, "config-reloaded");
    }

    // ========== HELP ==========
    private void sendHelp(Player player) {
        List<String> helpLines = plugin.getConfig().getStringList("messages.help");
        if (helpLines.isEmpty()) {
            sendPrefixed(player, "<gray>Use /gps help for commands</gray>");
            return;
        }
        for (String line : helpLines) {
            MessageUtils.sendMessage(player, line);
        }
    }

    // ========== Tab Complete ==========
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(
                    Arrays.asList("add", "accept", "deny", "list", "track", "toggle", "remove", "help")
            );
            if (player.hasPermission("gps.admin")) {
                subcommands.add("reload");
            }
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "add" -> {
                    // Показываем всех онлайн-игроков, кроме себя и текущих друзей
                    Set<UUID> friends = gpsManager.getFriends(player.getUniqueId());
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                            .filter(p -> !friends.contains(p.getUniqueId()))
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase(Locale.ROOT)
                                    .startsWith(args[1].toLowerCase(Locale.ROOT)))
                            .collect(Collectors.toList());
                }
                case "track" -> {
                    // Показываем только онлайн-друзей
                    Set<UUID> friends = gpsManager.getFriends(player.getUniqueId());
                    return friends.stream()
                            .map(Bukkit::getPlayer)
                            .filter(Objects::nonNull)
                            .filter(Player::isOnline)
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase(Locale.ROOT)
                                    .startsWith(args[1].toLowerCase(Locale.ROOT)))
                            .collect(Collectors.toList());
                }
                case "remove" -> {
                    // Показываем всех друзей (онлайн и оффлайн)
                    Set<UUID> friends = gpsManager.getFriends(player.getUniqueId());
                    return friends.stream()
                            .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                            .filter(Objects::nonNull)
                            .filter(name -> name.toLowerCase(Locale.ROOT)
                                    .startsWith(args[1].toLowerCase(Locale.ROOT)))
                            .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }

    // ========== Вспомогательные методы ==========

    /**
     * Получить строку сообщения из конфига по ключу.
     */
    private String getMsg(String key) {
        return plugin.getConfig().getString("messages." + key, "<red>Missing message: " + key + "</red>");
    }

    /**
     * Отправить сообщение с префиксом по ключу конфига.
     */
    private void sendMsg(Player player, String key) {
        sendPrefixed(player, getMsg(key));
    }

    /**
     * Отправить сообщение с префиксом.
     */
    private void sendPrefixed(Player player, String message) {
        MessageUtils.sendPrefixedMessage(plugin, player, message);
    }
}