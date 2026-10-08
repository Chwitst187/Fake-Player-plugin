package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * Loads a fake player's LuckPerms user before its synthetic join event.
 *
 * <p>Real clients are loaded during async pre-login. Fake players do not pass through that phase,
 * so join listeners such as TAB would otherwise trigger a forbidden synchronous Vault lookup.
 */
final class LuckPermsUserPreloader {

    /** Never leave a registered bot bodyless because a permissions backend stopped responding. */
    private static final long PRELOAD_TIMEOUT_MILLIS = 3_000L;

    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private LuckPermsUserPreloader() {}

    static CompletableFuture<Boolean> preload(UUID uuid, String name) {
        return preload(uuid, name, null);
    }

    static CompletableFuture<Boolean> preload(UUID uuid, String name, String groupName) {
        Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPerms == null || !luckPerms.isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        return withTimeout(LuckPermsIntegration.preloadAndAssign(uuid, name, groupName), PRELOAD_TIMEOUT_MILLIS)
                .handle((loaded, error) -> {
                    if (error != null) {
                        logFailure(error);
                        return false;
                    }
                    return loaded;
                });
    }

    static CompletableFuture<Boolean> withTimeout(CompletableFuture<Boolean> preload, long timeoutMillis) {
        if (preload == null) return CompletableFuture.completedFuture(false);
        return preload.completeOnTimeout(false, Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
    }

    static boolean isEnabled() {
        Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        return luckPerms != null && luckPerms.isEnabled();
    }

    private static void logFailure(Throwable error) {
        if (!FAILURE_LOGGED.compareAndSet(false, true)) return;
        FppLogger.warn("LuckPerms user preload failed; TAB may not resolve fake-player groups safely: "
                + error.getClass().getSimpleName()
                + ": "
                + error.getMessage());
    }
}
