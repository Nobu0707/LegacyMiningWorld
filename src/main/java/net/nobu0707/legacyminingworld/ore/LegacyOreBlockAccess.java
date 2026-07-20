package net.nobu0707.legacyminingworld.ore;

import net.nobu0707.legacyminingworld.geology.LegacyBlockKind;

/** Minimal read-before-write boundary used by the Paper ore applicator. */
public interface LegacyOreBlockAccess {
    boolean isAccessible(int x, int y, int z);

    LegacyBlockKind getBlockKind(int x, int y, int z);

    void setMaterial(int x, int y, int z, LegacyOreMaterial material);
}
