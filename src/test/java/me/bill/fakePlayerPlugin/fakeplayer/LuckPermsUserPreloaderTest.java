package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LuckPermsUserPreloaderTest {

    @Test
    void preservesCompletedPreloadResult() throws Exception {
        boolean loaded = LuckPermsUserPreloader.withTimeout(CompletableFuture.completedFuture(true), 100L)
                .get(1, TimeUnit.SECONDS);

        assertTrue(loaded);
    }

    @Test
    void releasesSpawnWhenPreloadNeverCompletes() throws Exception {
        boolean loaded = LuckPermsUserPreloader.withTimeout(new CompletableFuture<>(), 20L)
                .get(1, TimeUnit.SECONDS);

        assertFalse(loaded);
    }
}
