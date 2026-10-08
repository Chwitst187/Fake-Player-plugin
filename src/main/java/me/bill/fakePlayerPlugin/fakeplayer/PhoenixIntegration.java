package me.bill.fakePlayerPlugin.fakeplayer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Linkage-safe facade for the optional Phoenix rank implementation. */
public final class PhoenixIntegration {

    private static final String API_CLASS = "xyz.refinedev.phoenix.PlatformGetter";
    private static final String IMPLEMENTATION_CLASS = "me.bill.fakePlayerPlugin.fakeplayer.PhoenixBotRanks";

    private PhoenixIntegration() {}

    public static boolean isAvailable() {
        return Boolean.TRUE.equals(invoke(Bridge.IS_AVAILABLE));
    }

    public static boolean rankExists(String rankName) {
        return Boolean.TRUE.equals(invoke(Bridge.RANK_EXISTS, rankName));
    }

    public static List<String> rankNames() {
        Object result = invoke(Bridge.RANK_NAMES);
        if (!(result instanceof List<?> values)) return List.of();
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    public static String chooseRank(String maximumRank) {
        Object result = invoke(Bridge.CHOOSE_RANK, maximumRank);
        return result instanceof String rank ? rank : null;
    }

    public static String decorateDisplayName(String rankName, String displayName) {
        Object result = invoke(Bridge.DECORATE_DISPLAY_NAME, rankName, displayName);
        return result instanceof String decorated ? decorated : displayName;
    }

    public static void applyToPlayer(Player player, Plugin plugin, String rankName) {
        invoke(Bridge.APPLY_TO_PLAYER, player, plugin, rankName);
    }

    public static void cleanup(UUID uuid) {
        invoke(Bridge.CLEANUP, uuid);
    }

    private static Object invoke(Method method, Object... arguments) {
        if (method == null) return null;
        try {
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static final class Bridge {

        private static final Method IS_AVAILABLE = method("isAvailable");
        private static final Method RANK_EXISTS = method("rankExists", String.class);
        private static final Method RANK_NAMES = method("rankNames");
        private static final Method CHOOSE_RANK = method("chooseRank", String.class);
        private static final Method DECORATE_DISPLAY_NAME = method("decorateDisplayName", String.class, String.class);
        private static final Method APPLY_TO_PLAYER = method("applyToPlayer", Player.class, Plugin.class, String.class);
        private static final Method CLEANUP = method("cleanup", UUID.class);

        private static Method method(String name, Class<?>... parameterTypes) {
            Class<?> implementation = ImplementationHolder.TYPE;
            if (implementation == null) return null;
            try {
                Method method = implementation.getDeclaredMethod(name, parameterTypes);
                return method.trySetAccessible() ? method : null;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }

    private static final class ImplementationHolder {

        private static final Class<?> TYPE = resolve();

        private static Class<?> resolve() {
            ClassLoader loader = PhoenixIntegration.class.getClassLoader();
            try {
                Class.forName(API_CLASS, false, loader);
                return Class.forName(IMPLEMENTATION_CLASS, true, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                return null;
            }
        }
    }
}
