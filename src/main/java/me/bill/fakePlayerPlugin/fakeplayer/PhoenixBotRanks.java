package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import xyz.refinedev.phoenix.CommonPlatform;
import xyz.refinedev.phoenix.PlatformGetter;
import xyz.refinedev.phoenix.handler.IRankHandler;
import xyz.refinedev.phoenix.rank.IRank;

/**
 * Applies Phoenix rank data to fake players without creating or saving Phoenix profiles or grants.
 */
public final class PhoenixBotRanks {

    private static final ConcurrentMap<UUID, PermissionAttachment> ACTIVE_ATTACHMENTS = new ConcurrentHashMap<>();

    private PhoenixBotRanks() {}

    public static boolean isAvailable() {
        Plugin phoenix = Bukkit.getPluginManager().getPlugin("Phoenix");
        return phoenix != null && phoenix.isEnabled() && rankHandler() != null;
    }

    public static boolean rankExists(String rankName) {
        return findRank(rankName) != null;
    }

    /** Returns Phoenix rank names ordered from highest to lowest priority. */
    public static List<String> rankNames() {
        return sortedRanks().stream().map(IRank::getName).distinct().toList();
    }

    /** Picks a random Phoenix rank whose priority does not exceed the selected maximum. */
    public static String chooseRank(String maximumRank) {
        IRank maximum = findRank(maximumRank);
        if (maximum == null) return null;

        int maximumPriority = maximum.getPriority();
        List<IRank> eligible = new ArrayList<>();
        for (IRank rank : sortedRanks()) {
            if (rank.getPriority() <= maximumPriority) eligible.add(rank);
        }
        if (eligible.isEmpty()) eligible.add(maximum);
        return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()))
                .getName();
    }

    public static String decorateDisplayName(String rankName, String displayName) {
        IRank rank = findRank(rankName);
        if (rank == null) return displayName;

        String prefix = rank.getPlayerListPrefix();
        if (prefix == null || prefix.isBlank()) prefix = rank.getPrefix();
        if (prefix == null || prefix.isBlank()) return displayName;
        return prefix + (displayName != null ? displayName : "");
    }

    /**
     * Applies the selected Phoenix rank's effective permissions as a session-only Bukkit
     * attachment.
     */
    public static void applyToPlayer(Player player, Plugin plugin, String rankName) {
        if (player == null || plugin == null || rankName == null || rankName.isBlank()) return;

        IRank rank = findRank(rankName);
        if (rank == null) return;

        cleanup(player.getUniqueId());
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String rawPermission : rank.getAvailablePermissions()) {
            if (rawPermission == null || rawPermission.isBlank()) continue;
            String permission = rawPermission.trim();
            boolean value = true;
            if (permission.startsWith("-")) {
                permission = permission.substring(1);
                value = false;
            }
            if (!permission.isBlank()) attachment.setPermission(permission, value);
        }
        ACTIVE_ATTACHMENTS.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
    }

    public static void cleanup(UUID uuid) {
        if (uuid == null) return;

        PermissionAttachment attachment = ACTIVE_ATTACHMENTS.remove(uuid);
        if (attachment == null) return;
        try {
            attachment.remove();
        } catch (Throwable ignored) {
        }
    }

    private static List<IRank> sortedRanks() {
        IRankHandler handler = rankHandler();
        if (handler == null) return List.of();

        try {
            return handler.getSortedRanks().stream()
                    .filter(rank -> rank != null && rank.getName() != null)
                    .sorted(Comparator.comparingInt(IRank::getPriority)
                            .reversed()
                            .thenComparing(IRank::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static IRank findRank(String rankName) {
        if (rankName == null || rankName.isBlank()) return null;

        IRankHandler handler = rankHandler();
        if (handler == null) return null;
        try {
            IRank exact = handler.getRank(rankName);
            if (exact != null) return exact;

            String requested = rankName.toLowerCase(Locale.ROOT);
            return sortedRanks().stream()
                    .filter(rank -> rank.getName().toLowerCase(Locale.ROOT).equals(requested))
                    .findFirst()
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IRankHandler rankHandler() {
        try {
            CommonPlatform platform = PlatformGetter.getInstance();
            return platform != null && platform.isApiEnabled() ? platform.getRankHandler() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
