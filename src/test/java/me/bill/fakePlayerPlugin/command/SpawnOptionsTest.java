package me.bill.fakePlayerPlugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpawnOptionsTest {

    @Test
    void defaultsToOneSequentialBot() {
        SpawnOptions options = SpawnOptions.parse(new String[0]);

        assertTrue(options.isValid());
        assertEquals(1, options.count());
        assertFalse(options.randomNames());
        assertNull(options.customName());
        assertEquals(0, options.spreadRadius());
        assertNull(options.worldName());
        assertFalse(options.randomPositions());
        assertNull(options.maximumRank());
        assertFalse(options.godMode());
        assertFalse(options.donutChat());
    }

    @Test
    void parsesCountAndRandomInEitherOrder() {
        SpawnOptions first = SpawnOptions.parse(new String[] {"--random", "--count", "5"});
        SpawnOptions second = SpawnOptions.parse(new String[] {"--count", "5", "--random"});

        for (SpawnOptions options : new SpawnOptions[] {first, second}) {
            assertTrue(options.isValid());
            assertEquals(5, options.count());
            assertTrue(options.randomNames());
            assertNull(options.customName());
        }
    }

    @Test
    void parsesSingleCustomName() {
        SpawnOptions options = SpawnOptions.parse(new String[] {"--name", "Miner"});

        assertTrue(options.isValid());
        assertEquals("Miner", options.customName());
    }

    @Test
    void parsesSpreadWithBulkAndRandomFlagsInAnyOrder() {
        SpawnOptions first = SpawnOptions.parse(new String[] {"--count", "5", "--spread", "8", "--random"});
        SpawnOptions second = SpawnOptions.parse(new String[] {"--spread", "8", "--random", "--count", "5"});

        for (SpawnOptions options : new SpawnOptions[] {first, second}) {
            assertTrue(options.isValid());
            assertEquals(5, options.count());
            assertEquals(8, options.spreadRadius());
            assertTrue(options.randomNames());
        }
    }

    @Test
    void parsesWorldAndRandomPositions() {
        SpawnOptions options = SpawnOptions.parse(
                new String[] {"--world", "world_nether", "--randompos", "--count", "15", "--random"});

        assertTrue(options.isValid());
        assertEquals("world_nether", options.worldName());
        assertTrue(options.randomPositions());
        assertEquals(15, options.count());
        assertTrue(options.randomNames());
    }

    @Test
    void allowsCustomNameInAnotherWorldAtRandomPosition() {
        SpawnOptions options = SpawnOptions.parse(new String[] {"--name", "Miner", "--world", "world", "--randompos"});

        assertTrue(options.isValid());
        assertEquals("Miner", options.customName());
        assertEquals("world", options.worldName());
        assertTrue(options.randomPositions());
    }

    @Test
    void parsesMaximumRankWithOtherFlags() {
        SpawnOptions options = SpawnOptions.parse(
                new String[] {"--random", "--count", "8", "--rank", "vip", "--god", "--donutchat", "--world", "world"});

        assertTrue(options.isValid());
        assertEquals("vip", options.maximumRank());
        assertEquals(8, options.count());
        assertTrue(options.randomNames());
        assertTrue(options.godMode());
        assertTrue(options.donutChat());
    }

    @Test
    void rejectsInvalidCounts() {
        for (String[] args : new String[][] {
            {"--count"},
            {"--count", "0"},
            {"--count", "-2"},
            {"--count", "many"},
            {"--count", String.valueOf(SpawnOptions.MAX_SPAWN_COUNT + 1)}
        }) {
            SpawnOptions options = SpawnOptions.parse(args);
            assertFalse(options.isValid());
            assertEquals(SpawnOptions.Error.INVALID_COUNT, options.error());
        }
    }

    @Test
    void rejectsNameCombinedWithBulkOrRandomFlags() {
        for (String[] args : new String[][] {
            {"--name", "Miner", "--count", "2"},
            {"--random", "--name", "Miner"},
            {"--name", "Miner", "--spread", "5"}
        }) {
            SpawnOptions options = SpawnOptions.parse(args);
            assertFalse(options.isValid());
            assertEquals(SpawnOptions.Error.INVALID_ARGUMENTS, options.error());
        }
    }

    @Test
    void rejectsInvalidSpreadRadii() {
        for (String[] args : new String[][] {
            {"--spread"},
            {"--spread", "0"},
            {"--spread", "-2"},
            {"--spread", "wide"},
            {"--spread", String.valueOf(SpawnOptions.MAX_SPREAD_RADIUS + 1)}
        }) {
            SpawnOptions options = SpawnOptions.parse(args);
            assertFalse(options.isValid());
            assertEquals(SpawnOptions.Error.INVALID_SPREAD, options.error());
        }
    }

    @Test
    void rejectsSpreadCombinedWithRandomPositions() {
        SpawnOptions options = SpawnOptions.parse(new String[] {"--count", "5", "--spread", "8", "--randompos"});

        assertFalse(options.isValid());
        assertEquals(SpawnOptions.Error.INVALID_ARGUMENTS, options.error());
    }

    @Test
    void rejectsUnknownPositionalAndDuplicateFlags() {
        for (String[] args : new String[][] {
            {"5"},
            {"--unknown"},
            {"--random", "--random"},
            {"--name"},
            {"--world"},
            {"--rank"},
            {"--rank", "--random"},
            {"--rank", "vip", "--rank", "admin"},
            {"--god", "--god"},
            {"--donutchat", "--donutchat"},
            {"--randompos", "--randompos"}
        }) {
            SpawnOptions options = SpawnOptions.parse(args);
            assertFalse(options.isValid());
            assertEquals(SpawnOptions.Error.INVALID_ARGUMENTS, options.error());
        }
    }
}
