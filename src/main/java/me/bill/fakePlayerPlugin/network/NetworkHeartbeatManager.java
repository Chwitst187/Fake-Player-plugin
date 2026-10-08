package me.bill.fakePlayerPlugin.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.database.NetworkDatabase;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.RemoteBotEntry;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

/**
 * Publishes this server's live bot count + real player count into the shared
 * {@code fpp_server_heartbeat} table and periodically refreshes the cross-server
 * bot list from {@code fpp_network_bots}.
 *
 * <p>Runs only in {@code NETWORK} mode with MySQL enabled.
 */
public final class NetworkHeartbeatManager {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final NetworkDatabase netDb;

    private static final long HEARTBEAT_INTERVAL_TICKS = 100L; // 5 s
    private static final long PRUNE_INTERVAL_TICKS = 1200L; // 60 s
    private static final long STALE_THRESHOLD_MS = 60000L; // 60 s

    private volatile boolean started = false;
    private volatile int hbTask = -1;
    private volatile int pruneTask = -1;
    private final AtomicBoolean heartbeatInProgress = new AtomicBoolean(false);
    private final AtomicBoolean pruneInProgress = new AtomicBoolean(false);
    private final AtomicBoolean bootstrapRefreshInProgress = new AtomicBoolean(false);

    public NetworkHeartbeatManager(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null && db.isMysql()) {
            var conn = db.getConnection();
            Config.debugDbConn("NetworkHeartbeatManager: creating NetworkDatabase with connection=" + conn + " (valid="
                    + db.isConnectionValid() + ")");
            this.netDb = new NetworkDatabase(conn, true);
        } else {
            Config.debugDbConn("NetworkHeartbeatManager: skipping NetworkDatabase (db=" + db + ", mysql="
                    + (db != null && db.isMysql()) + ")");
            this.netDb = null;
        }
    }

    public void start() {
        if (started) return;
        if (netDb == null) {
            Config.debugNetwork("[NetworkHeartbeat] disabled (no MySQL DB).");
            return;
        }
        if (!Config.isNetworkMode()) {
            Config.debugNetwork("[NetworkHeartbeat] disabled (not in NETWORK mode).");
            return;
        }
        started = true;
        Config.debugNetwork("[NetworkHeartbeat] started — interval=" + HEARTBEAT_INTERVAL_TICKS + "t.");

        hbTask = FppScheduler.runSyncRepeatingWithId(
                plugin, this::tick, HEARTBEAT_INTERVAL_TICKS, HEARTBEAT_INTERVAL_TICKS);

        pruneTask =
                FppScheduler.runSyncRepeatingWithId(plugin, this::prune, PRUNE_INTERVAL_TICKS, PRUNE_INTERVAL_TICKS);

        // Initial refresh from DB (runs async to avoid startup thread stalls)
        refreshRemoteCacheAsync();
    }

    public void stop() {
        if (!started) return;
        started = false;
        if (hbTask != -1) FppScheduler.cancelTask(hbTask);
        if (pruneTask != -1) FppScheduler.cancelTask(pruneTask);
        if (netDb != null) netDb.shutdown();
        Config.debugNetwork("[NetworkHeartbeat] stopped.");
    }

    private void tick() {
        if (!heartbeatInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            DatabaseManager db = plugin.getDatabaseManager();
            if (db == null || !db.isConnectionValid()) {
                FppLogger.error("[NetworkHeartbeat] tick: Database connection is invalid/closed - skipping heartbeat");
                heartbeatInProgress.set(false);
                return;
            }

            int botCount = manager.getCount();
            int realPlayers = manager.getRealPlayers().size();
            String serverId = Config.serverId();
            Config.debugDbOps(
                    "NetworkHeartbeat tick: server=" + serverId + " players=" + realPlayers + " bots=" + botCount);

            List<NetworkDatabase.NetworkBotRow> rows = collectActiveBotRows(serverId);

            FppScheduler.runAsync(plugin, () -> {
                try {
                    netDb.heartbeat(serverId, realPlayers, botCount);
                    for (var row : rows) {
                        netDb.upsertNetworkBot(row);
                    }
                    Config.debugDbOps("NetworkHeartbeat: published " + rows.size() + " bot(s)");

                    // Refresh from DB into local RemoteBotCache
                    var snapshot = fetchRemoteSnapshot(serverId);
                    applyRemoteSnapshot(snapshot, serverId);
                } catch (Exception e) {
                    FppLogger.error("[NetworkHeartbeat] tick async error: " + e.getMessage() + " (connection valid? "
                            + (plugin.getDatabaseManager() != null
                                    && plugin.getDatabaseManager().isConnectionValid()) + ")");
                } finally {
                    heartbeatInProgress.set(false);
                }
            });
        } catch (Exception e) {
            FppLogger.error("[NetworkHeartbeat] tick error: " + e.getMessage() + " (connection valid? "
                    + (plugin.getDatabaseManager() != null
                            && plugin.getDatabaseManager().isConnectionValid()) + ")");
            heartbeatInProgress.set(false);
        }
    }

    private List<NetworkDatabase.NetworkBotRow> collectActiveBotRows(String serverId) {
        List<NetworkDatabase.NetworkBotRow> rows = new ArrayList<>(manager.getCount());
        for (var fp : manager.getActivePlayers()) {
            try {
                Location loc = fp.getLiveLocation();
                rows.add(new NetworkDatabase.NetworkBotRow(
                        fp.getUuid().toString(),
                        fp.getName(),
                        fp.getDisplayName(),
                        serverId,
                        fp.getSpawnedBy(),
                        loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                        loc != null ? loc.getX() : 0,
                        loc != null ? loc.getY() : 0,
                        loc != null ? loc.getZ() : 0,
                        fp.getEffectivePing(),
                        fp.isFrozen(),
                        System.currentTimeMillis()));
            } catch (Exception e) {
                FppLogger.debug("NetworkHeartbeat: failed to snapshot bot row for uuid=" + fp.getUuid() + " - "
                        + e.getMessage());
            }
        }

        Config.debugDbOps("NetworkHeartbeat: collected " + rows.size() + " bot row(s)");
        return rows;
    }

    private void prune() {
        if (!pruneInProgress.compareAndSet(false, true)) {
            return;
        }

        FppScheduler.runAsync(plugin, () -> {
            try {
                netDb.pruneStaleServers(STALE_THRESHOLD_MS);
            } catch (Exception e) {
                FppLogger.warn("[NetworkHeartbeat] prune error: " + e.getMessage());
            } finally {
                pruneInProgress.set(false);
            }
        });
    }

    private void refreshRemoteCacheAsync() {
        if (!bootstrapRefreshInProgress.compareAndSet(false, true)) {
            return;
        }

        String serverId = Config.serverId();
        FppScheduler.runAsync(plugin, () -> {
            try {
                var snapshot = fetchRemoteSnapshot(serverId);
                applyRemoteSnapshot(snapshot, serverId);
            } catch (Exception e) {
                FppLogger.error("[NetworkHeartbeat] bootstrap refresh error: " + e.getMessage());
            } finally {
                bootstrapRefreshInProgress.set(false);
            }
        });
    }

    private NetworkHeartbeatSnapshot fetchRemoteSnapshot(String myId) {
        List<NetworkDatabase.NetworkBotRow> remoteBots = new ArrayList<>();

        // Load all network bots except those on THIS server (local bots are already in FakePlayerManager)
        Config.debugDbOps("refreshRemoteCache: fetching network bots");
        List<NetworkDatabase.NetworkBotRow> netBots = netDb.getNetworkBots();
        for (var row : netBots) {
            if (row.serverId().equals(myId)) {
                continue;
            }
            remoteBots.add(row);
        }

        int totalPlayers = netDb.getTotalNetworkPlayers();
        int totalBots = netDb.getTotalNetworkBots();
        Config.debugDbOps("refreshRemoteCache: added " + remoteBots.size() + "/" + netBots.size() + " remote bot(s)");
        Config.debugDbOps("refreshRemoteCache: network stats - players=" + totalPlayers + ", bots=" + totalBots);

        return new NetworkHeartbeatSnapshot(remoteBots, totalPlayers, totalBots);
    }

    private void applyRemoteSnapshot(NetworkHeartbeatSnapshot snapshot, String myId) {
        var cache = plugin.getRemoteBotCache();
        if (cache == null) {
            Config.debugDbOps("refreshRemoteCache: RemoteBotCache is null");
            return;
        }

        // Remove stale remote cache entries, then add fresh data from DB
        for (var existing : cache.getAll()) {
            if (!myId.equals(existing.serverId())) {
                cache.remove(existing.uuid());
            }
        }

        int addedCount = 0;
        for (var row : snapshot.remoteBots()) {
            try {
                UUID uuid = UUID.fromString(row.botUuid());
                cache.add(new RemoteBotEntry(
                        row.serverId(), uuid, row.botName(), row.botDisplay(), row.botName(), "", "", row.ping()));
                addedCount++;
            } catch (IllegalArgumentException ignored) {
                Config.debugDbOps("refreshRemoteCache: invalid UUID format - " + row.botUuid());
            }
        }

        Config.debugDbOps("refreshRemoteCache: added " + addedCount + "/"
                + snapshot.remoteBots().size() + " remote bot(s)");

        cache.setNetworkTotalPlayers(snapshot.totalPlayers());
        cache.setNetworkTotalBots(snapshot.totalBots());
    }

    private record NetworkHeartbeatSnapshot(
            List<NetworkDatabase.NetworkBotRow> remoteBots, int totalPlayers, int totalBots) {}
}
