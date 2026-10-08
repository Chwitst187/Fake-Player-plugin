package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotChunkLoadEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public final class ChunkLoader {

    private static final int FOLIA_MAX_CHUNK_LOADING_RADIUS = 1;

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    private final Map<UUID, BotChunkState> states = new HashMap<>();
    private final Map<TicketKey, Integer> ticketReferences = new HashMap<>();
    private final Set<TicketKey> appliedTickets = new HashSet<>();

    /**
     * Folia entity ticks already execute on the owning region. Reuse them instead of enqueueing a
     * second region task for every bot on every chunk-loader sweep.
     */
    private volatile int foliaActiveRadius;

    private volatile long foliaUpdateInterval = 20L;

    public ChunkLoader(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        int configuredRadius = Config.chunkLoadingRadius();
        if (NmsPlayerSpawner.isFoliaServer() && configuredRadius > FOLIA_MAX_CHUNK_LOADING_RADIUS) {
            FppLogger.warn("Folia chunk-loading safety limit active: radius "
                    + configuredRadius
                    + " was capped to "
                    + FOLIA_MAX_CHUNK_LOADING_RADIUS
                    + ".");
        }

        long interval = Math.max(1L, Config.chunkLoadingUpdateInterval());
        foliaUpdateInterval = interval;
        FppScheduler.runSyncRepeating(plugin, this::tick, interval, interval);
    }

    private void tick() {
        foliaUpdateInterval = Math.max(1L, Config.chunkLoadingUpdateInterval());
        if (!Config.chunkLoadingEnabled()) {
            foliaActiveRadius = 0;
            if (hasStates()) releaseAll();
            return;
        }

        boolean folia = NmsPlayerSpawner.isFoliaServer();
        int configuredRadius = Config.chunkLoadingRadius();
        int globalRadius = folia ? Math.min(configuredRadius, FOLIA_MAX_CHUNK_LOADING_RADIUS) : configuredRadius;
        if (globalRadius == 0) {
            foliaActiveRadius = 0;
            if (hasStates()) releaseAll();
            return;
        }

        int activeCount = manager.getActivePlayers().size();
        int massThreshold = Config.chunkLoadingMassDisableThreshold();
        if (massThreshold > 0 && activeCount >= massThreshold) {
            foliaActiveRadius = 0;
            if (hasStates()) releaseAll();
            return;
        }

        foliaActiveRadius = folia ? globalRadius : 0;

        Set<UUID> activeUuids = new HashSet<>();

        for (FakePlayer fp : manager.getActivePlayers()) {
            UUID botId = fp.getUuid();
            activeUuids.add(botId);
            if (manager.isBodySpawnInProgress(botId)) continue;

            if (folia) {
                Player player = fp.getPlayer();
                if (player != null) {
                    // FakePlayerManager feeds body locations from its existing owning-region tick.
                    // Scheduling another entity callback here was pure fan-out overhead.
                    continue;
                } else {
                    Location spawn = fp.getSpawnLocation();
                    if (spawn != null && spawn.getWorld() != null && needsUpdate(fp, spawn, globalRadius)) {
                        Location snapshot = spawn.clone();
                        FppScheduler.runAtLocation(plugin, snapshot, () -> tickBot(fp, globalRadius, snapshot));
                    }
                }
                continue;
            }

            tickBot(fp, globalRadius, resolvePosition(fp));
        }

        synchronized (this) {
            states.entrySet().removeIf(entry -> {
                if (activeUuids.contains(entry.getKey())) return false;
                releaseState(entry.getValue());
                return true;
            });
        }
    }

    /** Called from FakePlayerManager's existing per-entity Folia tick. */
    void updateFromEntityTick(FakePlayer fp, Location position, long tick) {
        int globalRadius = foliaActiveRadius;
        if (globalRadius <= 0 || fp == null || position == null || position.getWorld() == null) return;

        long interval = foliaUpdateInterval;
        long phase = Integer.toUnsignedLong(fp.getUuid().hashCode());
        if (Math.floorMod(tick + phase, interval) != 0L) return;
        if (needsUpdate(fp, position, globalRadius)) {
            tickBot(fp, globalRadius, position);
        }
    }

    private boolean needsUpdate(FakePlayer fp, Location position, int globalRadius) {
        synchronized (this) {
            int radius = effectiveRadius(fp, globalRadius);
            BotChunkState state = states.get(fp.getUuid());
            if (radius == 0) return state != null;
            if (position == null || position.getWorld() == null) return false;
            if (state == null) return true;

            return state.radius != radius
                    || !state.worldName.equals(position.getWorld().getName())
                    || state.cx != (position.getBlockX() >> 4)
                    || state.cz != (position.getBlockZ() >> 4);
        }
    }

    private static int effectiveRadius(FakePlayer fp, int globalRadius) {
        int botRadius = fp.getChunkLoadRadius();
        return botRadius < 0 ? globalRadius : Math.min(botRadius, globalRadius);
    }

    private void tickBot(FakePlayer fp, int globalRadius, Location position) {
        synchronized (this) {
            UUID botId = fp.getUuid();

            int radius = effectiveRadius(fp, globalRadius);

            if (radius == 0) {
                BotChunkState existing = states.remove(botId);
                if (existing != null) releaseState(existing);
                return;
            }

            Location pos = position;
            if (pos == null || pos.getWorld() == null) return;

            World world = pos.getWorld();
            String wName = world.getName();
            int observedCx = pos.getBlockX() >> 4;
            int observedCz = pos.getBlockZ() >> 4;

            int[] clamped = clampToWorldBorder(world, observedCx, observedCz);
            int ticketCx = clamped[0];
            int ticketCz = clamped[1];

            BotChunkState state = states.get(botId);

            if (state != null
                    && state.worldName.equals(wName)
                    && state.cx == observedCx
                    && state.cz == observedCz
                    && state.radius == radius) {
                return;
            }

            if (state != null && !state.worldName.equals(wName)) {
                releaseState(state);
                state = null;
            }

            List<long[]> spiral = buildSpiral(ticketCx, ticketCz, radius, world);
            Set<Long> desired = new HashSet<>(spiral.size());
            for (long[] coord : spiral) desired.add(packKey((int) coord[0], (int) coord[1]));

            if (state == null) {
                state = new BotChunkState(wName, observedCx, observedCz, radius, new HashSet<>());
                states.put(botId, state);
            }

            for (long[] coord : spiral) {
                long key = packKey((int) coord[0], (int) coord[1]);
                if (state.keys.add(key)) {
                    retainTicketAndCallEvent(fp, world, (int) coord[0], (int) coord[1]);
                }
            }

            Iterator<Long> it = state.keys.iterator();
            while (it.hasNext()) {
                long key = it.next();
                if (!desired.contains(key)) {
                    releaseTicket(world, unpackX(key), unpackZ(key));
                    it.remove();
                }
            }

            state.cx = observedCx;
            state.cz = observedCz;
            state.radius = radius;
            state.worldName = wName;
        }
    }

    public void releaseForBot(FakePlayer fp) {
        synchronized (this) {
            BotChunkState state = states.remove(fp.getUuid());
            if (state != null) releaseState(state);
        }
    }

    public void releaseAll() {
        synchronized (this) {
            states.values().forEach(this::releaseState);
            states.clear();
        }
    }

    private boolean hasStates() {
        synchronized (this) {
            return !states.isEmpty();
        }
    }

    @SuppressWarnings("unused")
    public int totalTickets() {
        synchronized (this) {
            return states.values().stream().mapToInt(s -> s.keys.size()).sum();
        }
    }

    private static Location resolvePosition(FakePlayer fp) {
        Location loc = fp.getLiveLocation();
        if (loc != null && loc.getWorld() != null) return loc;

        Player player = fp.getPlayer();
        if (player != null && player.getWorld() != null) return player.getLocation();

        loc = fp.getSpawnLocation();
        if (loc != null && loc.getWorld() != null) return loc;
        return null;
    }

    private static List<long[]> buildSpiral(int cx, int cz, int radius, World world) {
        int diameter = radius * 2 + 1;
        List<long[]> result = new ArrayList<>(diameter * diameter);

        result.add(new long[] {cx, cz});
        for (int r = 1; r <= radius; r++) {

            for (int dx = -r; dx <= r; dx++) addIfInBorder(result, cx + dx, cz - r, world);

            for (int dz = -r + 1; dz <= r; dz++) addIfInBorder(result, cx + r, cz + dz, world);

            for (int dx = r - 1; dx >= -r; dx--) addIfInBorder(result, cx + dx, cz + r, world);

            for (int dz = r - 1; dz >= -r + 1; dz--) addIfInBorder(result, cx - r, cz + dz, world);
        }
        return result;
    }

    private static void addIfInBorder(List<long[]> list, int x, int z, World world) {
        double borderRadius = world.getWorldBorder().getSize() / 2.0;
        double bx = world.getWorldBorder().getCenter().getX();
        double bz = world.getWorldBorder().getCenter().getZ();
        double chunkCenterX = x * 16.0 + 8;
        double chunkCenterZ = z * 16.0 + 8;
        if (Math.abs(chunkCenterX - bx) <= borderRadius && Math.abs(chunkCenterZ - bz) <= borderRadius) {
            list.add(new long[] {x, z});
        }
    }

    private static int[] clampToWorldBorder(World world, int cx, int cz) {
        double borderHalf = world.getWorldBorder().getSize() / 2.0;
        double bx = world.getWorldBorder().getCenter().getX();
        double bz = world.getWorldBorder().getCenter().getZ();
        double minChunkX = Math.floor((bx - borderHalf) / 16.0);
        double maxChunkX = Math.floor((bx + borderHalf) / 16.0);
        double minChunkZ = Math.floor((bz - borderHalf) / 16.0);
        double maxChunkZ = Math.floor((bz + borderHalf) / 16.0);
        return new int[] {
            (int) Math.max(minChunkX, Math.min(maxChunkX, cx)), (int) Math.max(minChunkZ, Math.min(maxChunkZ, cz))
        };
    }

    private void releaseState(BotChunkState state) {
        World world = Bukkit.getWorld(state.worldName);
        if (state.keys.isEmpty()) {
            return;
        }
        if (world == null) {
            for (long key : state.keys) {
                releaseMissingWorldReference(state.worldName, unpackX(key), unpackZ(key));
            }
            state.keys.clear();
            return;
        }
        for (long key : state.keys) {
            releaseTicket(world, unpackX(key), unpackZ(key));
        }
        state.keys.clear();
    }

    private void retainTicketAndCallEvent(FakePlayer fp, World world, int chunkX, int chunkZ) {
        TicketKey key = new TicketKey(world.getName(), chunkX, chunkZ);
        int references = ticketReferences.getOrDefault(key, 0);
        ticketReferences.put(key, references + 1);
        if (references > 0) {
            return;
        }

        FppScheduler.runAtChunk(plugin, world, chunkX, chunkZ, () -> {
            synchronized (ChunkLoader.this) {
                if (ticketReferences.getOrDefault(key, 0) <= 0 || !appliedTickets.add(key)) {
                    return;
                }
            }
            boolean callEvent;
            try {
                world.addPluginChunkTicket(chunkX, chunkZ, plugin);
                callEvent = world.isChunkLoaded(chunkX, chunkZ);
            } catch (RuntimeException | Error error) {
                synchronized (ChunkLoader.this) {
                    appliedTickets.remove(key);
                }
                throw error;
            }
            if (callEvent) {
                var chunkEvt = new FppBotChunkLoadEvent(new FppBotImpl(fp), world.getChunkAt(chunkX, chunkZ));
                Bukkit.getPluginManager().callEvent(chunkEvt);
            }
        });
    }

    /**
     * Pins an asynchronously loaded chunk for an in-progress bot spawn. Applying the ticket before
     * the load completion yields closes the unload window during skin resolution and NMS body
     * creation. Spawn and steady-state tickets share one reference count.
     */
    boolean retainLoadedSpawnTicket(FakePlayer fp, World world, int chunkX, int chunkZ) {
        if (fp == null || world == null || !world.isChunkLoaded(chunkX, chunkZ)) return false;

        TicketKey key = new TicketKey(world.getName(), chunkX, chunkZ);
        boolean callEvent = false;
        synchronized (this) {
            int references = ticketReferences.getOrDefault(key, 0);
            ticketReferences.put(key, references + 1);
            if (appliedTickets.contains(key)) return true;

            try {
                world.addPluginChunkTicket(chunkX, chunkZ, plugin);
                appliedTickets.add(key);
                callEvent = true;
            } catch (RuntimeException | Error error) {
                if (references == 0) ticketReferences.remove(key);
                else ticketReferences.put(key, references);
                FppLogger.debug("Could not pin spawn chunk ["
                        + chunkX
                        + ", "
                        + chunkZ
                        + "] in world '"
                        + world.getName()
                        + "': "
                        + error.getMessage());
                return false;
            }
        }

        if (callEvent) {
            var chunkEvt = new FppBotChunkLoadEvent(new FppBotImpl(fp), world.getChunkAt(chunkX, chunkZ));
            Bukkit.getPluginManager().callEvent(chunkEvt);
        }
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    void releaseSpawnTicket(World world, int chunkX, int chunkZ) {
        if (world == null) return;
        synchronized (this) {
            releaseTicket(world, chunkX, chunkZ);
        }
    }

    private void releaseTicket(World world, int chunkX, int chunkZ) {
        TicketKey key = new TicketKey(world.getName(), chunkX, chunkZ);
        int references = ticketReferences.getOrDefault(key, 0);
        if (references > 1) {
            ticketReferences.put(key, references - 1);
            return;
        }
        ticketReferences.remove(key);

        FppScheduler.runAtChunk(plugin, world, chunkX, chunkZ, () -> {
            synchronized (ChunkLoader.this) {
                if (ticketReferences.getOrDefault(key, 0) > 0 || !appliedTickets.remove(key)) {
                    return;
                }
            }
            world.removePluginChunkTicket(chunkX, chunkZ, plugin);
        });
    }

    private void releaseMissingWorldReference(String worldName, int chunkX, int chunkZ) {
        TicketKey key = new TicketKey(worldName, chunkX, chunkZ);
        int references = ticketReferences.getOrDefault(key, 0);
        if (references > 1) {
            ticketReferences.put(key, references - 1);
        } else {
            ticketReferences.remove(key);
            appliedTickets.remove(key);
        }
    }

    private static long packKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    private static int unpackX(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }

    private static int unpackZ(long key) {
        return (int) ((key >>> 32) & 0xFFFFFFFFL);
    }

    private static final class BotChunkState {
        String worldName;
        int cx, cz;
        int radius;
        Set<Long> keys;

        BotChunkState(String worldName, int cx, int cz, int radius, Set<Long> keys) {
            this.worldName = worldName;
            this.cx = cx;
            this.cz = cz;
            this.radius = radius;
            this.keys = keys;
        }
    }

    private record TicketKey(String worldName, int chunkX, int chunkZ) {}
}
