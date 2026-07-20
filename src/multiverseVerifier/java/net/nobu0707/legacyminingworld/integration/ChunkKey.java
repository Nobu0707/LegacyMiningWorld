package net.nobu0707.legacyminingworld.integration;

record ChunkKey(int x, int z) {
    @Override
    public String toString() {
        return x + "," + z;
    }
}
