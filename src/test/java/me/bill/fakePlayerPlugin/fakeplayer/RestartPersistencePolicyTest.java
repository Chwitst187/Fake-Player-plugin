package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RestartPersistencePolicyTest {

    @Test
    void restartRestorationDefaultsOffAndMigratesExistingConfigs() throws Exception {
        String config =
                Files.readString(Path.of("src/main/resources/config.yml")).replace("\r\n", "\n");
        String configCode = Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/config/Config.java"));
        String migrator = Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/util/ConfigMigrator.java"));

        assertTrue(config.contains("config-version: 82"));
        assertTrue(config.contains("persistence:\n  enabled: false"));
        assertTrue(configCode.contains("getBoolean(\"persistence.enabled\", false)"));
        assertTrue(migrator.contains("CURRENT_VERSION = 82"));
        assertTrue(migrator.contains("if (stored < 79) anyChange |= v78to79(cfg);"));
        assertTrue(migrator.contains("cfg.set(\"persistence.enabled\", false);"));
    }

    @Test
    void disabledPersistenceClearsOldRestoreInputs() throws Exception {
        String persistence =
                Files.readString(Path.of("src/main/java/me/bill/fakePlayerPlugin/fakeplayer/BotPersistence.java"));

        assertTrue(persistence.contains("clearUnifiedSection(ROOT_BOTS);"));
        assertTrue(persistence.contains("clearUnifiedSection(ROOT_TASKS);"));
        assertTrue(persistence.contains("db.clearActiveBots();"));
        assertTrue(persistence.contains("db.clearBotTasks();"));
    }
}
