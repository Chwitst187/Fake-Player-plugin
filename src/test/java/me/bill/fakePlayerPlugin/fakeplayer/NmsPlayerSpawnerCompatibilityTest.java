package me.bill.fakePlayerPlugin.fakeplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

class NmsPlayerSpawnerCompatibilityTest {

    @Test
    void constructorResolverAcceptsAssignableRuntimeTypes() {
        Constructor<?> constructor = NmsPlayerSpawner.findCompatibleConstructor(
                CompatibleConstructor.class, Integer.valueOf(7), new StringBuilder("profile"));

        assertNotNull(constructor);
        assertEquals(Number.class, constructor.getParameterTypes()[0]);
        assertEquals(CharSequence.class, constructor.getParameterTypes()[1]);
    }

    @Test
    void constructorResolverRejectsIncompatibleAndPrimitiveNullArguments() {
        assertNull(NmsPlayerSpawner.findCompatibleConstructor(CompatibleConstructor.class, "wrong", "profile"));
        assertNull(NmsPlayerSpawner.findCompatibleConstructor(PrimitiveConstructor.class, (Object) null));
    }

    @Test
    void floorToChunkHandlesNegativeCoordinatesAtBoundaries() {
        assertEquals(0, NmsPlayerSpawner.floorToChunk(0.0));
        assertEquals(0, NmsPlayerSpawner.floorToChunk(15.999));
        assertEquals(1, NmsPlayerSpawner.floorToChunk(16.0));
        assertEquals(-1, NmsPlayerSpawner.floorToChunk(-0.001));
        assertEquals(-1, NmsPlayerSpawner.floorToChunk(-16.0));
        assertEquals(-2, NmsPlayerSpawner.floorToChunk(-16.001));
    }

    public static final class CompatibleConstructor {
        public CompatibleConstructor(Number number, CharSequence text) {}
    }

    public static final class PrimitiveConstructor {
        public PrimitiveConstructor(int value) {}
    }
}
