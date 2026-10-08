package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class RankIntegrationTest {

    @Test
    void remainsSafeWithoutOptionalRankProviders() {
        UUID uuid = UUID.randomUUID();

        assertFalse(PhoenixIntegration.isAvailable());
        assertFalse(RankIntegration.isAvailable());
        assertFalse(RankIntegration.rankExists("admin"));
        assertTrue(RankIntegration.rankNames().isEmpty());
        assertNull(RankIntegration.chooseRank("admin"));
        assertEquals("bot", RankIntegration.decorateDisplayName("admin", "bot"));
        assertFalse(assertDoesNotThrow(
                () -> RankIntegration.preload(uuid, "bot", "admin").get(1, TimeUnit.SECONDS)));
        assertDoesNotThrow(() -> RankIntegration.cleanup(uuid));
    }
}
