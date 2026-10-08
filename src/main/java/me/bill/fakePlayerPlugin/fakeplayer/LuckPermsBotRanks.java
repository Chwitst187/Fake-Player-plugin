package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.Track;

/**
 * Assigns real LuckPerms groups to bots without writing bot users or memberships to LuckPerms
 * storage.
 *
 * <p>Rank memberships are added through {@link User#transientData()}. LuckPerms explicitly keeps
 * this map session-only and never saves it to its datastore.
 */
public final class LuckPermsBotRanks {

    private static final ConcurrentMap<UUID, InheritanceNode> ACTIVE_RANKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Object> LOAD_TOKENS = new ConcurrentHashMap<>();

    private LuckPermsBotRanks() {}

    public static boolean isAvailable() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        return plugin != null && plugin.isEnabled() && api() != null;
    }

    public static boolean groupExists(String groupName) {
        LuckPerms luckPerms = api();
        return luckPerms != null && findGroup(luckPerms, groupName) != null;
    }

    /** Returns loaded group names ordered from highest to lowest LuckPerms weight. */
    public static List<String> groupNames() {
        LuckPerms luckPerms = api();
        if (luckPerms == null) return List.of();

        return luckPerms.getGroupManager().getLoadedGroups().stream()
                .sorted(Comparator.comparingInt(
                                (Group group) -> group.getWeight().orElse(0))
                        .reversed()
                        .thenComparing(Group::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Group::getName)
                .toList();
    }

    /**
     * Picks a random rank no higher than {@code maximumGroup}.
     *
     * <p>If the maximum belongs to a LuckPerms track, the track order defines the eligible ranks.
     * Otherwise explicit group weights are used. The maximum itself is always eligible, and the
     * default group is retained as the lowest fallback.
     */
    public static String chooseRank(String maximumGroup) {
        LuckPerms luckPerms = api();
        if (luckPerms == null) return null;

        Group maximum = findGroup(luckPerms, maximumGroup);
        if (maximum == null) return null;

        Set<String> eligibleNames = ranksFromTracks(luckPerms, maximum.getName());
        if (eligibleNames.isEmpty()) {
            eligibleNames.addAll(ranksByWeight(luckPerms, maximum));
        }
        if (eligibleNames.isEmpty()) eligibleNames.add(maximum.getName());

        List<String> eligible = new ArrayList<>(eligibleNames);
        return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
    }

    static CompletableFuture<Boolean> preloadAndAssign(UUID uuid, String username, String groupName) {
        LuckPerms luckPerms = api();
        if (luckPerms == null || uuid == null) return CompletableFuture.completedFuture(false);

        Object loadToken = new Object();
        LOAD_TOKENS.put(uuid, loadToken);
        return luckPerms.getUserManager().loadUser(uuid, username).thenApply(user -> {
            if (LOAD_TOKENS.get(uuid) != loadToken) return false;

            InheritanceNode previous = ACTIVE_RANKS.remove(uuid);
            if (previous != null) user.transientData().remove(previous);

            if (groupName != null && !groupName.isBlank()) {
                InheritanceNode rank = InheritanceNode.builder(groupName).build();
                user.transientData().add(rank);
                ACTIVE_RANKS.put(uuid, rank);
            }

            // Deliberately do not call saveUser/savePlayerData: the user and rank stay in memory.
            return true;
        });
    }

    /** Removes FPP's transient node on every bot removal path. */
    public static void cleanup(UUID uuid) {
        if (uuid == null) return;

        LOAD_TOKENS.remove(uuid);
        InheritanceNode rank = ACTIVE_RANKS.remove(uuid);
        if (rank == null) return;

        LuckPerms luckPerms = api();
        if (luckPerms == null) return;

        User user = luckPerms.getUserManager().getUser(uuid);
        if (user != null) user.transientData().remove(rank);
    }

    private static Set<String> ranksFromTracks(LuckPerms luckPerms, String maximumGroup) {
        Set<String> eligible = new LinkedHashSet<>();
        for (Track track : luckPerms.getTrackManager().getLoadedTracks()) {
            List<String> groups = track.getGroups();
            int maximumIndex = indexOfIgnoreCase(groups, maximumGroup);
            if (maximumIndex < 0) continue;

            for (int index = 0; index <= maximumIndex; index++) {
                Group group = findGroup(luckPerms, groups.get(index));
                if (group != null) eligible.add(group.getName());
            }
        }
        return eligible;
    }

    private static List<String> ranksByWeight(LuckPerms luckPerms, Group maximum) {
        int maximumWeight = maximum.getWeight().orElse(0);
        boolean maximumHasWeight = maximum.getWeight().isPresent();
        List<Group> groups = new ArrayList<>();

        for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
            boolean isMaximum = group.getName().equalsIgnoreCase(maximum.getName());
            boolean isDefault = group.getName().equalsIgnoreCase("default");
            boolean withinWeight = maximumHasWeight
                    && group.getWeight().isPresent()
                    && group.getWeight().getAsInt() <= maximumWeight;
            if (isMaximum || isDefault || withinWeight) groups.add(group);
        }

        groups.sort(Comparator.comparingInt((Group group) -> group.getWeight().orElse(Integer.MIN_VALUE))
                .thenComparing(Group::getName, String.CASE_INSENSITIVE_ORDER));
        return groups.stream().map(Group::getName).distinct().toList();
    }

    private static Group findGroup(LuckPerms luckPerms, String groupName) {
        if (groupName == null || groupName.isBlank()) return null;

        Group exact = luckPerms.getGroupManager().getGroup(groupName);
        if (exact != null) return exact;

        String requested = groupName.toLowerCase(Locale.ROOT);
        return luckPerms.getGroupManager().getLoadedGroups().stream()
                .filter(group -> group.getName().toLowerCase(Locale.ROOT).equals(requested))
                .findFirst()
                .orElse(null);
    }

    private static int indexOfIgnoreCase(List<String> values, String expected) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).equalsIgnoreCase(expected)) return index;
        }
        return -1;
    }

    private static LuckPerms api() {
        try {
            return Bukkit.getServicesManager().load(LuckPerms.class);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
