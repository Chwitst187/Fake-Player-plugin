package me.bill.fakePlayerPlugin.fakeplayer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Linkage-safe facade for the optional LuckPerms integration.
 *
 * <p>This class deliberately has no static references to LuckPerms API types. The implementation is
 * resolved only when the API is visible, allowing FPP to load and clean up bots on servers without
 * LuckPerms.
 */
public final class LuckPermsIntegration {

    private static final String API_CLASS = "net.luckperms.api.LuckPerms";
    private static final String IMPLEMENTATION_CLASS = "me.bill.fakePlayerPlugin.fakeplayer.LuckPermsBotRanks";

    private LuckPermsIntegration() {}

    public static boolean isAvailable() {
        return Boolean.TRUE.equals(invoke(Bridge.IS_AVAILABLE));
    }

    public static boolean groupExists(String groupName) {
        return Boolean.TRUE.equals(invoke(Bridge.GROUP_EXISTS, groupName));
    }

    public static List<String> groupNames() {
        Object result = invoke(Bridge.GROUP_NAMES);
        if (!(result instanceof List<?> values)) return List.of();
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    public static String chooseRank(String maximumGroup) {
        Object result = invoke(Bridge.CHOOSE_RANK, maximumGroup);
        return result instanceof String rank ? rank : null;
    }

    static CompletableFuture<Boolean> preloadAndAssign(UUID uuid, String username, String groupName) {
        Object result = invoke(Bridge.PRELOAD_AND_ASSIGN, uuid, username, groupName);
        if (result instanceof CompletableFuture<?> future) {
            return future.thenApply(Boolean.TRUE::equals);
        }
        return CompletableFuture.completedFuture(false);
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
        private static final Method GROUP_EXISTS = method("groupExists", String.class);
        private static final Method GROUP_NAMES = method("groupNames");
        private static final Method CHOOSE_RANK = method("chooseRank", String.class);
        private static final Method PRELOAD_AND_ASSIGN =
                method("preloadAndAssign", UUID.class, String.class, String.class);
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
            ClassLoader loader = LuckPermsIntegration.class.getClassLoader();
            try {
                Class.forName(API_CLASS, false, loader);
                return Class.forName(IMPLEMENTATION_CLASS, true, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                return null;
            }
        }
    }
}
