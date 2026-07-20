package net.nobu0707.legacyminingworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.nobu0707.legacyminingworld.geology.LegacyUndergroundPopulator;
import org.bukkit.generator.BlockPopulator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ore-adapter")
class UndergroundPopulatorRegistrationTest {
    @Test
    void exposesExactlyOneImmutableReusableIntegratedPopulator() {
        LegacyMiningChunkGenerator generator = new LegacyMiningChunkGenerator();
        List<BlockPopulator> first = generator.getDefaultPopulators(null);
        List<BlockPopulator> second = generator.getDefaultPopulators(null);
        assertEquals(1, first.size());
        assertTrue(first.getFirst() instanceof LegacyUndergroundPopulator);
        assertSame(first, second);
        assertSame(first.getFirst(), second.getFirst());
        assertThrows(UnsupportedOperationException.class,
                () -> first.add(new LegacyUndergroundPopulator()));
    }
}
