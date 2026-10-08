package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RandomSpawnSafetyTest {

    @Test
    void rejectsBlocksThatCanDamageTrapOrTeleportABot() {
        for (Material material : new Material[] {
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.MAGMA_BLOCK,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.CACTUS,
            Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE,
            Material.POINTED_DRIPSTONE,
            Material.BUBBLE_COLUMN,
            Material.COBWEB,
            Material.END_PORTAL,
            Material.END_GATEWAY,
            Material.NETHER_PORTAL
        }) {
            assertTrue(FakePlayerManager.isDangerousSpawnMaterial(material), material.name());
        }
    }

    @Test
    void acceptsOrdinaryTerrainMaterials() {
        for (Material material :
                new Material[] {Material.GRASS_BLOCK, Material.STONE, Material.SAND, Material.SNOW_BLOCK}) {
            assertFalse(FakePlayerManager.isDangerousSpawnMaterial(material), material.name());
        }
    }

    @Test
    void usesAConfiguredWorldBorderAsTheRandomSpawnArea() {
        assertTrue(FakePlayerManager.usesWorldBorderForRandomSpawn(10_000.0));
        assertTrue(FakePlayerManager.usesWorldBorderForRandomSpawn(32.0));
        assertFalse(FakePlayerManager.usesWorldBorderForRandomSpawn(59_999_968.0));
        assertFalse(FakePlayerManager.usesWorldBorderForRandomSpawn(Double.POSITIVE_INFINITY));
        assertFalse(FakePlayerManager.usesWorldBorderForRandomSpawn(0.0));
    }

    @Test
    void distributesAcrossTheConfiguredBorderOrTheWholeWorld() {
        FakePlayerManager.RandomSpawnBounds configured =
                FakePlayerManager.randomSpawnBounds(10_000.0, 2_000.0, -3_000.0);
        assertEquals(-2_998.0, configured.minX());
        assertEquals(6_998.0, configured.maxX());
        assertEquals(-7_998.0, configured.minZ());
        assertEquals(1_998.0, configured.maxZ());

        FakePlayerManager.RandomSpawnBounds wholeWorld =
                FakePlayerManager.randomSpawnBounds(59_999_968.0, 2_000.0, -3_000.0);
        assertEquals(-29_999_982.0, wholeWorld.minX());
        assertEquals(29_999_982.0, wholeWorld.maxX());
        assertEquals(-29_999_982.0, wholeWorld.minZ());
        assertEquals(29_999_982.0, wholeWorld.maxZ());
    }

    @Test
    void avoidsChunkEdgesBeforeGeneratingARandomChunk() {
        assertTrue(FakePlayerManager.isInteriorChunkColumn(1, 1));
        assertTrue(FakePlayerManager.isInteriorChunkColumn(14, 14));
        assertTrue(FakePlayerManager.isInteriorChunkColumn(-15, -15));
        assertFalse(FakePlayerManager.isInteriorChunkColumn(0, 8));
        assertFalse(FakePlayerManager.isInteriorChunkColumn(15, 8));
        assertFalse(FakePlayerManager.isInteriorChunkColumn(8, 16));
    }

    @Test
    void asynchronouslyLoadsCandidateChunksBeforeInspectingTerrain() throws Exception {
        String manager =
                Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/fakeplayer/FakePlayerManager.java"));

        assertTrue(
                manager.contains("getChunkAtAsync(chunkX, chunkZ, true, true)"),
                "random-position spawn chunks must load asynchronously at urgent priority");
        assertTrue(
                manager.contains("if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;"),
                "surface inspection must refuse unloaded chunks");
        assertTrue(
                manager.contains("FPP does not impose a parallel cap"),
                "all chunk loads must be handed directly to the server chunk system");
        assertFalse(manager.contains("foliaRandomSpawnChunkLoads"), "FPP must not queue or cap Folia chunk loads");
        assertFalse(manager.contains("FOLIA_RANDOM_CHUNK_LOAD_MAX_CONCURRENT"));
        assertFalse(manager.contains("FOLIA_RANDOM_CHUNK_LOAD_EXCLUSION_RADIUS"));
        assertTrue(
                manager.contains("if (world.isChunkLoaded(chunkX, chunkZ))"),
                "loaded Folia regions must bypass the generation limiter");
        assertTrue(
                manager.contains("activePlayers.get(fp.getUuid()) == fp"),
                "stale requests must not attach to a replacement bot with the same UUID");
        assertFalse(manager.contains("enqueueFoliaRandomSpawnChunkLoad"));
        assertFalse(manager.contains("batchSize = folia ? 2 : 6"), "Folia must not throttle dispatch to two bots/tick");
        assertTrue(
                manager.contains("for (int[] probe : RANDOM_SPAWN_COLUMN_PROBES)"),
                "a loaded chunk should probe several columns before generating another chunk");
    }

    @Test
    void reservesInitialBodySpawnBeforePublishingBotToValidators() throws Exception {
        String manager =
                Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/fakeplayer/FakePlayerManager.java"));

        int lifecycleClaim = manager.indexOf("if (!initialSpawnPending.add(uuid)) return false;");
        int registryPublication = manager.indexOf("activePlayers.putIfAbsent(uuid, fp)");
        assertTrue(lifecycleClaim >= 0, "initial spawn lifecycle must be claimed");
        assertTrue(registryPublication >= 0, "bot must be published atomically");
        assertTrue(
                lifecycleClaim < registryPublication,
                "initial spawn lifecycle must be claimed before validators can observe the bot");
        assertTrue(manager.contains("if (!initialSpawnPending.remove(botUuid))"));
        assertTrue(manager.contains("if (bodySpawnInProgress.contains(fp.getUuid())) return;"));
        assertTrue(manager.contains("spawnBodyAndFinishClaimed(fp, safeLocation)"));
    }

    @Test
    void loadsTheTargetChunkBeforeCreatingAnyInitialBotBody() throws Exception {
        String manager =
                Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/fakeplayer/FakePlayerManager.java"));

        int preload = manager.indexOf("runAfterSpawnChunkLoad(fp, spawnLoc.clone(), chunkLoaded ->");
        int loadedBodySpawn = manager.indexOf("private void spawnBodyInLoadedChunkAndFinishClaimed");
        assertTrue(preload >= 0, "unloaded target chunks must be requested asynchronously");
        assertTrue(loadedBodySpawn > preload, "body creation must be isolated behind the chunk-load gate");
        assertTrue(
                manager.contains("if (world.isChunkLoaded(chunkX, chunkZ))"),
                "already loaded target chunks should spawn without an extra asynchronous wait");
        assertTrue(
                manager.contains("spawnBodyInLoadedChunkAndFinishClaimed(fp, spawnLoc);"),
                "the physical body may only be created after the target chunk is confirmed loaded");
        assertTrue(
                manager.contains("world == null || !world.isChunkLoaded(chunkX, chunkZ)"),
                "the target chunk must be checked again immediately before delayed body creation");
        assertTrue(
                manager.contains("finishBodySpawn[0].run();"),
                "a chunk that unloaded during profile preparation must be reloaded before spawning");
        assertTrue(
                manager.contains("retainSpawnChunkLease(fp, world, chunkX, chunkZ)"),
                "an asynchronously loaded chunk must be pinned before its completion yields");
        assertTrue(
                manager.contains("releaseSpawnChunkLease(fp);"),
                "the temporary spawn ticket must be released after body creation or cleanup");

        String chunkLoader =
                Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/fakeplayer/ChunkLoader.java"));
        assertTrue(
                chunkLoader.contains("boolean retainLoadedSpawnTicket("),
                "spawn loads and steady-state chunk tickets must share reference counting");
        assertTrue(
                chunkLoader.contains("world.addPluginChunkTicket(chunkX, chunkZ, plugin);"),
                "the loaded chunk must receive a ticket before delayed skin/body work starts");
    }
}
