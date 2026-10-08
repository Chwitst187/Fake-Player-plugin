package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BotTickScalingTest {

    @Test
    void adaptivelyStaggersVisualFanOutAndOptionalBehavior() {
        assertEquals(1, FakePlayerManager.visualSyncIntervalForLoad(10, 20));
        assertEquals(2, FakePlayerManager.visualSyncIntervalForLoad(10, 100));
        assertEquals(4, FakePlayerManager.visualSyncIntervalForLoad(30, 100));

        assertEquals(1, FakePlayerManager.behaviorIntervalForLoad(63));
        assertEquals(2, FakePlayerManager.behaviorIntervalForLoad(64));
        assertEquals(4, FakePlayerManager.behaviorIntervalForLoad(256));
    }

    @Test
    void hardBudgetsKeepMassBotPassesBounded() {
        assertEquals(100, FakePlayerManager.botTickBudgetForLoad(100, 256));
        assertEquals(256, FakePlayerManager.botTickBudgetForLoad(100_000, 256));
        assertEquals(2_048, FakePlayerManager.tabListCheckBudgetForLoad(100_000, 2_048));
        assertEquals(256, FakePlayerManager.validationCheckBudgetForLoad(100_000, 256));
    }

    @Test
    void botTickUsesSpatialPlayerQueriesInsteadOfPerBotGlobalArrays() throws Exception {
        String manager =
                Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/fakeplayer/FakePlayerManager.java"));

        assertTrue(manager.contains("world.getNearbyPlayers(center, radius)"));
        assertTrue(manager.contains("!activePlayers.containsKey(viewer.getUniqueId())"));
        assertFalse(manager.contains("new double[onlineCount]"));
        assertFalse(manager.contains("new World[onlineCount]"));
    }

    @Test
    void periodicRegionWorkPiggybacksOnTheExistingBotTick() throws Exception {
        String manager =
                Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/fakeplayer/FakePlayerManager.java"));

        assertTrue(manager.contains("chunkLoader.updateFromEntityTick(fp, before, scheduledTick)"));
        assertTrue(manager.contains("refreshNametagActivity(fp, bot)"));
        assertFalse(manager.contains("Runnable refreshTick = () ->"));
    }

    @Test
    void foliaMassTickAvoidsGlobalMapAndRegionizerNearbyScans() throws Exception {
        String manager =
                Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/fakeplayer/FakePlayerManager.java"));

        assertTrue(manager.contains("FakePlayer fp = botTickQueue.poll()"));
        assertTrue(manager.contains("FakePlayer fp = tabListScanQueue.poll()"));
        assertTrue(manager.contains("if (NmsPlayerSpawner.isFoliaServer()) return cachedOnlinePlayers"));
        assertTrue(manager.contains("realPlayers.put(player.getUniqueId(), player)"));
    }
}
