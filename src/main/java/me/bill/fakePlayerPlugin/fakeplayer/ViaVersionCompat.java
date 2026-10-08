package me.bill.fakePlayerPlugin.fakeplayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.network.FakeConnection;

import io.netty.channel.Channel;

/**
 * Soft integration with ViaVersion so a spawned bot is a <em>known</em> connection to it, instead of
 * an unrecognized UUID.
 *
 * <p>A bot's {@link FakeConnection} never performs a real handshake/login — {@code placeNewPlayer} is
 * called directly on it — so ViaVersion, which learns a connecting client's protocol version by
 * injecting into the real Netty pipeline at that handshake, never sees it happen and has no record of
 * the bot at all. Anything that then asks Via for the bot's protocol version (Via itself, or another
 * plugin's version-gated logic) finds nothing, which is what produces console warnings about not
 * being able to determine the bot's client version.
 *
 * <p>The fix is to register the bot with Via ourselves, pinned to the server's own native protocol
 * version — i.e. "this client speaks exactly what the server speaks, no translation needed" — using
 * the same {@code UserConnectionImpl} + {@code ProtocolPipelineImpl} construction Via's own channel
 * initializer uses for a real connection (see {@code ViaChannelInitializer#createUserConnection}).
 * Only {@link com.viaversion.viaversion.api.connection.ProtocolInfo} and friends are stable public
 * API; {@code UserConnectionImpl}/{@code ProtocolPipelineImpl} are the concrete classes every
 * first-party Via integration (ViaBackwards, proxy adapters) already constructs the same way to build
 * a connection outside of a real network handshake.
 *
 * <p>Everything here is reflective and fails silently — if ViaVersion isn't installed, isn't loaded
 * yet, or its internals ever change shape, bots simply go back to being unregistered with it.
 */
public final class ViaVersionCompat {

    private ViaVersionCompat() {}

    public enum RegistrationResult {
        REGISTERED,
        UNAVAILABLE,
        UUID_CONFLICT
    }

    private static volatile boolean ready = false;
    private static volatile boolean broken = false;
    private static final Map<UUID, Object> REGISTERED_BOT_CONNECTIONS = new ConcurrentHashMap<>();

    private static Method isLoadedMethod;
    private static Method getManagerMethod;
    private static Method getAPIMethod;
    private static Method getServerVersionMethod;
    private static Method highestSupportedProtocolVersionMethod;
    private static Method getConnectionManagerMethod;
    private static Method onLoginSuccessMethod;
    private static Method onDisconnectMethod;
    private static Method getServerConnectionMethod;
    private static Method getProtocolInfoMethod;
    private static Method setActiveMethod;
    private static Method setProtocolVersionMethod;
    private static Method setServerProtocolVersionMethod;
    private static Method setUuidMethod;
    private static Method setUsernameMethod;
    private static Method setStateMethod;

    private static Constructor<?> userConnectionCtor;
    private static Constructor<?> protocolPipelineCtor;

    private static Object playState;

    private static synchronized void init() {
        if (ready || broken) return;
        try {
            if (Bukkit.getPluginManager().getPlugin("ViaVersion") == null) {
                broken = true;
                return;
            }

            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
            isLoadedMethod = viaClass.getMethod("isLoaded");
            getManagerMethod = viaClass.getMethod("getManager");
            getAPIMethod = viaClass.getMethod("getAPI");

            Class<?> viaManagerClass = Class.forName("com.viaversion.viaversion.api.ViaManager");
            Class<?> connectionManagerClass =
                    Class.forName("com.viaversion.viaversion.api.connection.ConnectionManager");
            getConnectionManagerMethod = viaManagerClass.getMethod("getConnectionManager");

            Class<?> viaAPIClass = Class.forName("com.viaversion.viaversion.api.ViaAPI");
            getServerVersionMethod = viaAPIClass.getMethod("getServerVersion");

            Class<?> serverProtocolVersionClass =
                    Class.forName("com.viaversion.viaversion.api.protocol.version.ServerProtocolVersion");
            highestSupportedProtocolVersionMethod =
                    serverProtocolVersionClass.getMethod("highestSupportedProtocolVersion");

            Class<?> userConnectionInterfaceClass =
                    Class.forName("com.viaversion.viaversion.api.connection.UserConnection");
            onLoginSuccessMethod = connectionManagerClass.getMethod("onLoginSuccess", userConnectionInterfaceClass);
            onDisconnectMethod = connectionManagerClass.getMethod("onDisconnect", userConnectionInterfaceClass);
            getServerConnectionMethod = connectionManagerClass.getMethod("getServerConnection", UUID.class);
            getProtocolInfoMethod = userConnectionInterfaceClass.getMethod("getProtocolInfo");
            setActiveMethod = userConnectionInterfaceClass.getMethod("setActive", boolean.class);

            Class<?> userConnectionImplClass = Class.forName("com.viaversion.viaversion.connection.UserConnectionImpl");
            userConnectionCtor = userConnectionImplClass.getConstructor(Channel.class, boolean.class);

            Class<?> protocolPipelineImplClass =
                    Class.forName("com.viaversion.viaversion.protocol.ProtocolPipelineImpl");
            protocolPipelineCtor = protocolPipelineImplClass.getConstructor(userConnectionInterfaceClass);

            Class<?> protocolInfoClass = Class.forName("com.viaversion.viaversion.api.connection.ProtocolInfo");
            Class<?> protocolVersionClass =
                    Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            setProtocolVersionMethod = protocolInfoClass.getMethod("setProtocolVersion", protocolVersionClass);
            setServerProtocolVersionMethod =
                    protocolInfoClass.getMethod("setServerProtocolVersion", protocolVersionClass);
            setUuidMethod = protocolInfoClass.getMethod("setUuid", UUID.class);
            setUsernameMethod = protocolInfoClass.getMethod("setUsername", String.class);

            Class<?> stateClass = Class.forName("com.viaversion.viaversion.api.protocol.packet.State");
            setStateMethod = protocolInfoClass.getMethod("setState", stateClass);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object play = Enum.valueOf((Class<? extends Enum>) stateClass, "PLAY");
            playState = play;

            ready = true;
            Config.debugNmsConn("ViaVersionCompat: ViaVersion detected, bot registration enabled.");
        } catch (Throwable t) {
            broken = true;
            Config.debugNmsConn("ViaVersionCompat: unavailable - " + t);
        }
    }

    private static boolean ensureReady() {
        if (ready) return true;
        if (broken) return false;
        init();
        return ready;
    }

    /**
     * Registers a freshly-spawned bot with ViaVersion as running the server's own native protocol
     * version, so Via (and anything querying it) treats the bot exactly like a same-version client
     * that needs no packet translation — instead of an unrecognized connection.
     */
    public static synchronized RegistrationResult registerBot(FakeConnection conn, UUID uuid, String name) {
        if (conn == null || uuid == null) return RegistrationResult.UNAVAILABLE;
        if (!ensureReady()) return RegistrationResult.UNAVAILABLE;
        try {
            if (!(Boolean) isLoadedMethod.invoke(null)) return RegistrationResult.UNAVAILABLE;

            Object viaAPI = getAPIMethod.invoke(null);
            Object serverProtocolVersion = getServerVersionMethod.invoke(viaAPI);
            Object protocolVersion = highestSupportedProtocolVersionMethod.invoke(serverProtocolVersion);

            Object viaManager = getManagerMethod.invoke(null);
            Object connectionManager = getConnectionManagerMethod.invoke(viaManager);

            Object existing = getServerConnectionMethod.invoke(connectionManager, uuid);
            Object tracked = REGISTERED_BOT_CONNECTIONS.get(uuid);
            if (existing != null) {
                if (existing == tracked) {
                    Config.debugNmsConn(
                            "ViaVersionCompat: refused duplicate live registration for '" + name + "' uuid=" + uuid);
                    return RegistrationResult.UUID_CONFLICT;
                }
                if (!BotIdentityCache.isBotUuid(uuid)) {
                    Config.debugNmsConn(
                            "ViaVersionCompat: refused to replace non-FPP connection for '" + name + "' uuid=" + uuid);
                    return RegistrationResult.UUID_CONFLICT;
                }

                // A failed shutdown or plugin reload can leave an old FPP connection in Via's
                // registry. Remove it before onLoginSuccess; otherwise ViaVersion logs and
                // force-adds a duplicate frontend UUID.
                onDisconnectMethod.invoke(connectionManager, existing);
                if (getServerConnectionMethod.invoke(connectionManager, uuid) != null) {
                    Config.debugNmsConn("ViaVersionCompat: stale registration could not be removed for '"
                            + name
                            + "' uuid="
                            + uuid);
                    return RegistrationResult.UUID_CONFLICT;
                }
            }

            Channel channel = conn.getFakeChannel();
            Object userConnection = userConnectionCtor.newInstance(channel, false);
            // Wires itself into the connection's ProtocolInfo as a side effect of construction.
            protocolPipelineCtor.newInstance(userConnection);

            Object protocolInfo = getProtocolInfoMethod.invoke(userConnection);
            setProtocolVersionMethod.invoke(protocolInfo, protocolVersion);
            setServerProtocolVersionMethod.invoke(protocolInfo, protocolVersion);
            setUuidMethod.invoke(protocolInfo, uuid);
            setUsernameMethod.invoke(protocolInfo, name);
            setStateMethod.invoke(protocolInfo, playState);
            setActiveMethod.invoke(userConnection, true);

            onLoginSuccessMethod.invoke(connectionManager, userConnection);
            REGISTERED_BOT_CONNECTIONS.put(uuid, userConnection);
            Config.debugNmsConn("ViaVersionCompat: registered '" + name + "' at the server's native protocol version.");
            return RegistrationResult.REGISTERED;
        } catch (Throwable t) {
            Config.debugNmsConn("ViaVersionCompat.registerBot failed for '" + name + "': " + t);
            return RegistrationResult.UNAVAILABLE;
        }
    }

    /** Unregisters a despawned bot so Via's connection registry doesn't accumulate stale entries. */
    public static synchronized void unregisterBot(UUID uuid) {
        if (uuid == null) return;
        if (!ensureReady()) return;
        try {
            if (!(Boolean) isLoadedMethod.invoke(null)) return;

            Object viaManager = getManagerMethod.invoke(null);
            Object connectionManager = getConnectionManagerMethod.invoke(viaManager);
            Object tracked = REGISTERED_BOT_CONNECTIONS.remove(uuid);
            Object existing = tracked != null ? tracked : getServerConnectionMethod.invoke(connectionManager, uuid);
            if (existing != null) {
                onDisconnectMethod.invoke(connectionManager, existing);
            }
        } catch (Throwable t) {
            Config.debugNmsConn("ViaVersionCompat.unregisterBot failed for " + uuid + ": " + t);
        }
    }
}
