package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FoliaRegionSafetyTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/me/bill/fakePlayerPlugin");

    @Test
    void fakeConnectionCanActuallyClose() throws Exception {
        String connection = read("fakeplayer/network/FakeConnection.java");
        String pipeline = read("fakeplayer/network/FakeChannelPipeline.java");

        assertTrue(connection.contains("return super.isConnected();"));
        assertTrue(connection.contains("super.disconnect(reason);"));
        assertTrue(pipeline.contains("markClosed();"));
        assertTrue(pipeline.contains("markDisconnected();"));
    }

    @Test
    void normalAndPartialRemovalUseConnectionLifecycle() throws Exception {
        String spawner = read("fakeplayer/NmsPlayerSpawner.java");

        assertTrue(spawner.contains("fakeListener.requestSyntheticDisconnect();"));
        assertTrue(spawner.contains("nmsPlayer.connection.disconnect(Component.empty());"));
        assertFalse(spawner.contains("playerListRemoveMethod.invoke("));
    }

    @Test
    void onlyPrePlacementCodeMayUseRawSetPos() throws Exception {
        String spawner = read("fakeplayer/NmsPlayerSpawner.java");
        String joinListener = read("listener/PlayerJoinListener.java");

        assertEquals(1, occurrences(spawner, "setPosMethod.invoke("));
        assertFalse(spawner.contains("player.teleport("));
        assertTrue(spawner.contains("FppScheduler.teleportAsync(player, requested)"));
        assertTrue(joinListener.contains("consumePendingSpawnLocation"));
        assertTrue(joinListener.contains("correctPendingSpawnLocation"));
    }

    @Test
    void chunkTicketsAndPlayerReadsStayRegionOwned() throws Exception {
        String chunkLoader = read("fakeplayer/ChunkLoader.java");
        String manager = read("fakeplayer/FakePlayerManager.java");

        assertFalse(chunkLoader.contains("FppScheduler.runAtEntity(plugin, player"));
        assertTrue(chunkLoader.contains("void updateFromEntityTick("));
        assertTrue(chunkLoader.contains("&& needsUpdate(fp, spawn, globalRadius)"));
        assertTrue(manager.contains("chunkLoader.updateFromEntityTick(fp, before, scheduledTick)"));
        assertEquals(2, occurrences(chunkLoader, "FppScheduler.runAtChunk("));
        assertFalse(chunkLoader.contains("getRegionScheduler().run("));
        assertTrue(chunkLoader.contains("if (manager.isBodySpawnInProgress(botId)) continue;"));
        assertTrue(chunkLoader.contains("FOLIA_MAX_CHUNK_LOADING_RADIUS = 1"));
        assertTrue(chunkLoader.contains("Map<TicketKey, Integer> ticketReferences"));
        assertTrue(chunkLoader.contains("if (references > 0)"));
        assertTrue(chunkLoader.contains("if (references > 1)"));
        assertTrue(manager.contains("isPlayerOwnedByCurrentRegion(viewer, foliaServer)"));
        assertTrue(manager.contains("!Bukkit.isOwnedByCurrentRegion(block)"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(SOURCE_ROOT.resolve(relative));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
