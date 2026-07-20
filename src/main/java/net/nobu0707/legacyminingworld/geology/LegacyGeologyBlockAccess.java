package net.nobu0707.legacyminingworld.geology;

/** Minimal block boundary used by the geology applicator. */
public interface LegacyGeologyBlockAccess {
    boolean isAccessible(int x, int y, int z);

    LegacyBlockKind getBlockKind(int x, int y, int z);

    void setMaterial(int x, int y, int z, LegacyGeologyMaterial material);
}
