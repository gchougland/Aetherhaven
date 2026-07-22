package com.hexvane.aetherhaven.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hexvane.aetherhaven.jewelry.JewelryRarity;
import org.junit.jupiter.api.Test;

class WorldZoneIndexTest {
    @Test
    void parseZoneName_extractsTier() {
        assertEquals(1, WorldZoneIndex.parseZoneName("Zone1_Plains"));
        assertEquals(3, WorldZoneIndex.parseZoneName("Zone3_Taiga1"));
        assertEquals(4, WorldZoneIndex.parseZoneName("zone4_volcanic"));
    }

    @Test
    void parseZoneName_unknownUsesDefault() {
        assertEquals(WorldZoneIndex.UNKNOWN_DEFAULT, WorldZoneIndex.parseZoneName("CreativeHub"));
    }

    @Test
    void adventureZoneCaps_zone1OnlyCommonUncommon() {
        double[] w = {50, 30, 12, 4, 1};
        JewelryRarity.applyAdventureZoneCapsToWeights(1, w);
        assertEquals(0.0, w[2]);
        assertEquals(0.0, w[3]);
        assertEquals(0.0, w[4]);
        assertEquals(50.0, w[0]);
    }

    @Test
    void adventureZoneCaps_zone3NoLegendary() {
        double[] w = {50, 30, 12, 4, 1};
        JewelryRarity.applyAdventureZoneCapsToWeights(3, w);
        assertEquals(12.0, w[2]);
        assertEquals(4.0, w[3]);
        assertEquals(0.0, w[4]);
    }

    @Test
    void resolveFromGeneratorZone_usesIdPlusOne() {
        assertEquals(4, WorldZoneIndex.resolveFromGeneratorZone(3, "Zone4_Volcanic1"));
        assertEquals(1, WorldZoneIndex.resolveFromGeneratorZone(0, "Zone1_Plains1"));
    }

    @Test
    void adventureZoneCaps_zone4NoCommonAndBoostsHighTiers() {
        double[] w = {50, 30, 12, 4, 1};
        JewelryRarity.applyAdventureZoneCapsToWeights(4, w);
        assertEquals(0.0, w[0]);
        assertEquals(30.0, w[1]);
        assertEquals(18.0, w[2]);
        assertEquals(7.0, w[3]);
        assertEquals(2.0, w[4]);
    }
}
