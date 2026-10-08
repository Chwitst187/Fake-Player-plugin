package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;

/** Selects Phoenix ranks when Phoenix is ready, otherwise LuckPerms groups. */
public final class RankIntegration {

    private RankIntegration() {}

    public static boolean isAvailable() {
        return PhoenixIntegration.isAvailable() || LuckPermsIntegration.isAvailable();
    }

    public static String providerName() {
        if (PhoenixIntegration.isAvailable()) return "Phoenix";
        if (LuckPermsIntegration.isAvailable()) return "LuckPerms";
        return "Phoenix/LuckPerms";
    }

    public static boolean rankExists(String rankName) {
        return PhoenixIntegration.isAvailable()
                ? PhoenixIntegration.rankExists(rankName)
                : LuckPermsIntegration.groupExists(rankName);
    }

    public static List<String> rankNames() {
        return PhoenixIntegration.isAvailable() ? PhoenixIntegration.rankNames() : LuckPermsIntegration.groupNames();
    }

    public static String chooseRank(String maximumRank) {
        return PhoenixIntegration.isAvailable()
                ? PhoenixIntegration.chooseRank(maximumRank)
                : LuckPermsIntegration.chooseRank(maximumRank);
    }

    public static String decorateDisplayName(String rankName, String displayName) {
        return PhoenixIntegration.isAvailable()
                ? PhoenixIntegration.decorateDisplayName(rankName, displayName)
                : displayName;
    }

    static CompletableFuture<Boolean> preload(UUID uuid, String username, String rankName) {
        if (PhoenixIntegration.isAvailable()) return CompletableFuture.completedFuture(false);
        if (LuckPermsIntegration.isAvailable()) {
            return LuckPermsUserPreloader.preload(uuid, username, rankName);
        }
        return CompletableFuture.completedFuture(false);
    }

    static boolean shouldSuppressPermissionJoin() {
        return PhoenixIntegration.isAvailable() || LuckPermsUserPreloader.isEnabled();
    }

    static void applyToPlayer(Player player, String rankName) {
        if (!PhoenixIntegration.isAvailable()) return;
        PhoenixIntegration.applyToPlayer(player, FakePlayerPlugin.getInstance(), rankName);
    }

    public static void cleanup(UUID uuid) {
        PhoenixIntegration.cleanup(uuid);
        LuckPermsIntegration.cleanup(uuid);
    }
}
