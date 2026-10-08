package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.Locale;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * Optional VoiceChat integration marker.
 * Attempts lightweight activation handshake via Simple Voice Chat API if available.
 */
public final class VoiceChatHook {

    public record HookState(boolean detected, boolean active, Plugin plugin) {}

    private VoiceChatHook() {}

    /**
     * Detects whether Simple Voice Chat is installed and marks hook state.
     */
    public static HookState initialize(FakePlayerPlugin plugin) {
        Plugin pluginInstance = findVoiceChatPlugin(plugin);
        if (pluginInstance == null) {
            Config.debugStartup("VoiceChat hook: plugin not detected, hook intentionally skipped.");
            return new HookState(false, false, null);
        }

        boolean active = activateHook(pluginInstance);
        if (active) {
            FppLogger.info("VoiceChat hook active for " + pluginInstance.getName() + " ("
                    + pluginVersion(pluginInstance) + ").");
        } else {
            FppLogger.warn("VoiceChat hook detected (" + pluginInstance.getName() + ") but activation attempt failed.");
        }

        return new HookState(true, active, pluginInstance);
    }

    private static boolean activateHook(Plugin voiceChatPlugin) {
        try {
            Class<?> apiClass = Class.forName("de.maxhenkel.voicechat.api.VoicechatApi");
            return apiClass != null;
        } catch (Throwable t) {
            FppLogger.debug("VoiceChat hook activation skipped: " + t.getMessage());
            return false;
        }
    }

    private static Plugin findVoiceChatPlugin(FakePlayerPlugin plugin) {
        PluginManager pluginManager = plugin.getServer().getPluginManager();

        for (Plugin candidate : pluginManager.getPlugins()) {
            String name = candidate.getName().toLowerCase(Locale.ROOT);
            String mainClass = candidate.getClass().getName().toLowerCase(Locale.ROOT);

            if (name.contains("simple voice chat")
                    || name.contains("voicechat")
                    || mainClass.startsWith("de.maxhenkel.voicechat")) {
                return candidate;
            }
        }

        return null;
    }

    private static String pluginVersion(Plugin plugin) {
        try {
            String version = plugin.getDescription().getVersion();
            return (version == null || version.isBlank()) ? "unknown version" : version;
        } catch (Throwable ignored) {
            return "unknown version";
        }
    }
}
