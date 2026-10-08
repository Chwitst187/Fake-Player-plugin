package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LuckPermsIntegrationTest {

    @Test
    void remainsSafeWhenLuckPermsIsUnavailable() {
        UUID uuid = UUID.randomUUID();

        assertDoesNotThrow(() -> LuckPermsIntegration.cleanup(uuid));
        assertFalse(LuckPermsIntegration.isAvailable());
        assertTrue(LuckPermsIntegration.groupNames().isEmpty());
        assertFalse(assertDoesNotThrow(
                () -> LuckPermsIntegration.preloadAndAssign(uuid, "bot", null).get(1, TimeUnit.SECONDS)));
    }
}
