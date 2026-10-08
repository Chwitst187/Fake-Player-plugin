package me.bill.fakePlayerPlugin.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FoliaCompatibilityTest {

    @Test
    void pluginYmlDeclaresFoliaSupport() throws Exception {
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));

        assertTrue(
                pluginYml.lines().anyMatch(line -> line.trim().equals("folia-supported: true")),
                "plugin.yml must declare folia-supported: true for Folia compatibility");
    }

    @Test
    void centralSchedulerDoesNotUseLegacyBukkitScheduler() throws Exception {
        String scheduler = Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/util/FppScheduler.java"));

        assertFalse(
                scheduler.contains("Bukkit.getScheduler()"),
                "FppScheduler must route tasks through Folia-compatible schedulers");
    }

    @Test
    void immediateSchedulerPathsDoNotAllocateConsumerAdapters() throws Exception {
        String scheduler = Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/util/FppScheduler.java"));

        assertTrue(scheduler.contains("entity.getScheduler().execute(plugin, runnable, null, 1L)"));
        assertTrue(scheduler.contains("Bukkit.getRegionScheduler().execute(plugin, location, runnable)"));
        assertFalse(scheduler.contains("scheduler.runAtEntity\" : null"));
        assertFalse(scheduler.contains("scheduler.runAtLocation\" : null"));
    }
}
