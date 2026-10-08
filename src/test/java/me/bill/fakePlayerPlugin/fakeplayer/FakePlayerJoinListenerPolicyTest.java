package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FakePlayerJoinListenerPolicyTest {

    @Test
    void suppressesPluginsThatRequireRealLoginState() {
        for (String plugin : new String[] {
            "WorldGuard",
            "WorldEdit",
            "FastAsyncWorldEdit",
            "FAWE",
            "ViaVersion",
            "Phoenix",
            "pxBasic",
            "PolarLoader",
            "packetevents",
            "voicechat",
            "nLogin"
        }) {
            assertTrue(FakePlayerJoinListenerPolicy.shouldSuppress(plugin, "example.Listener"), plugin);
        }
    }

    @Test
    void suppressesKnownListenerPackagesWhenPluginNamesDiffer() {
        for (String listener : new String[] {
            "com.sk89q.worldguard.bukkit.listener.WorldGuardPlayerListener",
            "com.sk89q.worldedit.bukkit.WorldEditListener",
            "com.viaversion.viaversion.bukkit.listeners.JoinListener",
            "xyz.refinedev.phoenix.bukkit.listener.PlayerListener",
            "xyz.refinedev.basic.listener.PlayerListener",
            "xyz.refinedev.practice.profile.listener.ProfileListener",
            "polar.dku",
            "io.github.retrooper.packetevents.bukkit.InternalGlobalBukkitListener",
            "ac.grim.grimac.shaded.io.github.retrooper.packetevents.bukkit.InternalBukkitListener",
            "de.maxhenkel.voicechat.BukkitVoicechatPlugin",
            "com.nickuc.login.bukkit.listener.PlayerListener"
        }) {
            assertTrue(FakePlayerJoinListenerPolicy.shouldSuppress("renamed-plugin", listener), listener);
        }
    }

    @Test
    void keepsNormalAndPluginOwnedListenersRegistered() {
        assertFalse(FakePlayerJoinListenerPolicy.shouldSuppress("FakePlayerPlugin", NmsPlayerSpawner.class.getName()));
        assertFalse(FakePlayerJoinListenerPolicy.shouldSuppress(
                "TAB", "me.neznamy.tab.platforms.bukkit.BukkitEventListener"));
        assertFalse(FakePlayerJoinListenerPolicy.shouldSuppress("LuckPerms", "me.lucko.luckperms.bukkit.LPListener"));
        assertFalse(FakePlayerJoinListenerPolicy.shouldSuppress(null, null));
    }

    @Test
    void identifiesOnlyPhoenixForQuitSuppression() {
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPhoenix(
                "Phoenix", "xyz.refinedev.phoenix.bukkit.listener.PlayerListener"));
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPhoenix(
                "renamed-plugin", "xyz.refinedev.phoenix.bukkit.listener.PlayerListener"));
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPhoenix(
                "pxBasic", "xyz.refinedev.basic.listener.PlayerListener"));
        assertFalse(FakePlayerJoinListenerPolicy.shouldSuppressPhoenix(
                "TAB", "me.neznamy.tab.platforms.bukkit.BukkitEventListener"));
    }

    @Test
    void identifiesBoltProfileListenersForJoinAndQuitSuppression() {
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressBolt(
                "Bolt", "xyz.refinedev.practice.profile.listener.ProfileListener"));
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressBolt(
                "Retention", "xyz.refinedev.practice.profile.listener.ProfileListener"));
        assertFalse(FakePlayerJoinListenerPolicy.shouldSuppressBolt(
                "TAB", "me.neznamy.tab.platforms.bukkit.BukkitEventListener"));
    }

    @Test
    void identifiesPolarLifecycleListeners() {
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPolar("Polar", "polar.dku"));
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPolar("PolarLoader", "example.Listener"));
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPolar("renamed-plugin", "polar.avy"));
        assertFalse(FakePlayerJoinListenerPolicy.shouldSuppressPolar("TAB", "me.neznamy.tab.Listener"));
    }

    @Test
    void identifiesPacketEventsJoinListenersWithoutSuppressingOtherGrimListeners() {
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPacketEvents(
                "packetevents", "io.github.retrooper.packetevents.bukkit.InternalPaperJoinListener"));
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPacketEvents(
                "GrimAC", "ac.grim.grimac.shaded.io.github.retrooper.packetevents.bukkit.InternalBukkitListener"));
        assertFalse(FakePlayerJoinListenerPolicy.shouldSuppressPacketEvents(
                "GrimAC", "ac.grim.grimac.manager.init.start.StartEvent"));
    }

    @Test
    void permissionFallbackTargetsOnlyTabAndLuckPerms() {
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPermissionFallback(
                "TAB", "me.neznamy.tab.platforms.bukkit.BukkitEventListener"));
        assertTrue(FakePlayerJoinListenerPolicy.shouldSuppressPermissionFallback(
                "LuckPerms", "me.lucko.luckperms.bukkit.LPListener"));
        assertFalse(FakePlayerJoinListenerPolicy.shouldSuppressPermissionFallback(
                "FakePlayerPlugin", "me.bill.fakePlayerPlugin.listener.PlayerJoinListener"));
    }
}
