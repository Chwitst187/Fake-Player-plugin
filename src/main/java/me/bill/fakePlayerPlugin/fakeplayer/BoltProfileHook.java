package me.bill.fakePlayerPlugin.fakeplayer;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;

import xyz.refinedev.practice.api.BoltAPI;

/** Optional Bolt integration that keeps synthetic FPP players out of Bolt's profile lifecycle. */
public final class BoltProfileHook {

    public record HookState(boolean detected, boolean active, Plugin plugin) {}

    private BoltProfileHook() {}

    /**
     * Detects Bolt after soft dependencies have loaded and verifies that its public API is visible.
     * Profile creation itself is prevented by temporarily suppressing Bolt's join/quit listeners
     * while a synthetic player is placed or removed.
     */
    public static HookState initialize(FakePlayerPlugin plugin) {
        Plugin owner = detectBoltOwner(plugin);
        if (owner == null) {
            Config.debugStartup("Bolt hook: Bolt not detected.");
            return new HookState(false, false, null);
        }

        String mode = "listener guard";
        try {
            ApiAccess.verifyProfileApi();
            mode = "API linked + listener guard";
        } catch (Throwable unavailable) {
            Config.debugStartup("Bolt hook: API not visible/ready; listener guard remains active ("
                    + unavailable.getClass().getSimpleName()
                    + ").");
        }

        FppLogger.info("Bolt hook enabled via "
                + owner.getName()
                + " - fake-player profile creation and saving are blocked ("
                + mode
                + ").");
        return new HookState(true, true, owner);
    }

    private static Plugin detectBoltOwner(FakePlayerPlugin plugin) {
        for (Plugin candidate : plugin.getServer().getPluginManager().getPlugins()) {
            String name = candidate.getName();
            String mainClass = candidate.getClass().getName();
            if (name.equalsIgnoreCase("Bolt")
                    || name.equalsIgnoreCase("BoltPractice")
                    || mainClass.startsWith("xyz.refinedev.practice.")) {
                return candidate;
            }
        }

        for (RegisteredListener registered : PlayerJoinEvent.getHandlerList().getRegisteredListeners()) {
            if (registered.getPlugin() == null || registered.getListener() == null) continue;
            if (FakePlayerJoinListenerPolicy.shouldSuppressBolt(
                    registered.getPlugin().getName(),
                    registered.getListener().getClass().getName())) {
                return registered.getPlugin();
            }
        }

        return null;
    }

    /** Keeps BoltAPI resolution lazy so servers without Bolt never load its compile-only classes. */
    private static final class ApiAccess {
        private static void verifyProfileApi() {
            BoltAPI.INSTANCE.getProfileAPI();
        }
    }
}
