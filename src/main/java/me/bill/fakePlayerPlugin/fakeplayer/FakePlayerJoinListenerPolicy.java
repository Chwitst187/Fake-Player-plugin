package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.Locale;

/** Identifies third-party join listeners that assume every Bukkit player completed a real login. */
final class FakePlayerJoinListenerPolicy {

    private FakePlayerJoinListenerPolicy() {}

    static boolean shouldSuppress(String pluginName, String listenerClass) {
        String plugin = pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT);
        String listener = listenerClass == null ? "" : listenerClass.toLowerCase(Locale.ROOT);

        if (shouldSuppressPhoenix(plugin, listener)
                || shouldSuppressBolt(plugin, listener)
                || shouldSuppressPolar(plugin, listener)
                || shouldSuppressPacketEvents(plugin, listener)) {
            return true;
        }

        return switch (plugin) {
            case "voicechat",
                    "simplevoicechat",
                    "nlogin",
                    "worldguard",
                    "worldedit",
                    "fastasyncworldedit",
                    "fawe",
                    "viaversion" -> true;
            default -> listener.startsWith("de.maxhenkel.voicechat.")
                    || listener.startsWith("com.nickuc.")
                    || listener.startsWith("com.sk89q.worldguard.")
                    || listener.startsWith("com.sk89q.worldedit.")
                    || listener.startsWith("com.viaversion.viaversion.");
        };
    }

    static boolean shouldSuppressPermissionFallback(String pluginName, String listenerClass) {
        String plugin = pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT);
        String listener = listenerClass == null ? "" : listenerClass.toLowerCase(Locale.ROOT);
        return plugin.equals("tab")
                || plugin.equals("luckperms")
                || listener.startsWith("me.neznamy.tab.")
                || listener.startsWith("me.lucko.luckperms.")
                || listener.startsWith("net.luckperms.");
    }

    static boolean shouldSuppressPhoenix(String pluginName, String listenerClass) {
        String plugin = pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT);
        String listener = listenerClass == null ? "" : listenerClass.toLowerCase(Locale.ROOT);
        return plugin.equals("phoenix")
                || plugin.equals("phoenixcore")
                || plugin.equals("pxbasic")
                || listener.startsWith("xyz.refinedev.phoenix.")
                || listener.startsWith("xyz.refinedev.basic.");
    }

    static boolean shouldSuppressBolt(String pluginName, String listenerClass) {
        String listener = listenerClass == null ? "" : listenerClass.toLowerCase(Locale.ROOT);
        return listener.startsWith("xyz.refinedev.practice.");
    }

    static boolean shouldSuppressPolar(String pluginName, String listenerClass) {
        String plugin = pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT);
        String listener = listenerClass == null ? "" : listenerClass.toLowerCase(Locale.ROOT);
        return plugin.equals("polar") || plugin.equals("polarloader") || listener.startsWith("polar.");
    }

    static boolean shouldSuppressPacketEvents(String pluginName, String listenerClass) {
        String plugin = pluginName == null ? "" : pluginName.toLowerCase(Locale.ROOT);
        String listener = listenerClass == null ? "" : listenerClass.toLowerCase(Locale.ROOT);
        return plugin.equals("packetevents")
                || plugin.equals("packeteventsapi")
                || listener.startsWith("io.github.retrooper.packetevents.")
                || listener.startsWith("com.github.retrooper.packetevents.")
                || listener.contains(".io.github.retrooper.packetevents.")
                || listener.contains(".com.github.retrooper.packetevents.");
    }
}
