package net.nobu0707.legacyminingworld.integration;

import org.bukkit.Material;

final class ScanChecksum {
    static final long OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    private ScanChecksum() {
    }

    static long mixBlock(long checksum, int x, int y, int z, Material material) {
        long mixed = mix(checksum, x);
        mixed = mix(mixed, y);
        mixed = mix(mixed, z);
        for (char character : material.name().toCharArray()) {
            mixed = mix(mixed, character);
        }
        return mixed;
    }

    private static long mix(long checksum, int value) {
        return (checksum ^ Integer.toUnsignedLong(value)) * PRIME;
    }
}
