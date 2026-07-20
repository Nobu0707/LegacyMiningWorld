package net.nobu0707.legacyminingworld.integration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

record VerificationSummary(
        String world,
        UUID uid,
        long seed,
        String environment,
        String generator,
        int minimumY,
        int maximumY,
        String spawn,
        String fixedSpawn,
        int geologyAnchors,
        int oreAnchors,
        String yElevenCounts,
        String yFiveToSixtySevenCounts,
        long yFiveToSixtySevenChecksum,
        int forbidden,
        int biomeChecks) {
    String toMarker() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("world", world);
        values.put("uid", uid);
        values.put("seed", seed);
        values.put("environment", environment);
        values.put("generator", generator);
        values.put("min", minimumY);
        values.put("max", maximumY);
        values.put("spawn", spawn);
        values.put("fixedSpawn", fixedSpawn);
        values.put("geology", geologyAnchors);
        values.put("ore", oreAnchors);
        values.put("y11", yElevenCounts);
        values.put("y5_67", yFiveToSixtySevenCounts);
        values.put("checksum", yFiveToSixtySevenChecksum);
        values.put("forbidden", forbidden);
        values.put("biomes", biomeChecks);
        return VerifierSupport.marker("LMW_MV_VERIFY_PASS", values);
    }
}
