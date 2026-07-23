package com.hexvane.aetherhaven.poi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

final class PoiEffectTableTest {
    @Test
    void noneEatTagRestoresHungerOnly() {
        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(0f);
        needs.setEnergy(0f);
        needs.setFun(0f);
        PoiEntry poi = poi(Set.of("EAT"), PoiInteractionKind.NONE);
        PoiEffectTable.applyUseComplete(needs, poi);
        assertEquals(30f, needs.getHunger(), 0.01f);
        assertEquals(0f, needs.getEnergy(), 0.01f);
        assertEquals(0f, needs.getFun(), 0.01f);
    }

    @Test
    void eatOnChairRestoresHungerOnly() {
        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(5f);
        needs.setEnergy(5f);
        needs.setFun(5f);
        PoiEntry poi = poi(Set.of("EAT", AetherhavenConstants.POI_TAG_RESTAURANT), PoiInteractionKind.USE_BENCH);
        PoiEffectTable.applyUseComplete(needs, poi);
        assertEquals(35f, needs.getHunger(), 0.01f);
        assertEquals(5f, needs.getEnergy(), 0.01f);
        assertEquals(5f, needs.getFun(), 0.01f);
    }

    @Test
    void sleepRestoresEnergyOnly() {
        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(10f);
        needs.setEnergy(0f);
        needs.setFun(10f);
        PoiEntry poi = poi(Set.of("SLEEP", "ENERGY"), PoiInteractionKind.SLEEP);
        PoiEffectTable.applyUseComplete(needs, poi);
        assertEquals(10f, needs.getHunger(), 0.01f);
        assertEquals(28f, needs.getEnergy(), 0.01f);
        assertEquals(10f, needs.getFun(), 0.01f);
    }

    @Test
    void funSpotRestoresFunOnly() {
        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(10f);
        needs.setEnergy(10f);
        needs.setFun(0f);
        PoiEntry poi = poi(Set.of("FUN", "SIT"), PoiInteractionKind.SIT);
        PoiEffectTable.applyUseComplete(needs, poi);
        assertEquals(10f, needs.getHunger(), 0.01f);
        assertEquals(10f, needs.getEnergy(), 0.01f);
        assertEquals(22f, needs.getFun(), 0.01f);
    }

    @Test
    void restaurantSitWithoutEatRestoresNothing() {
        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(0f);
        needs.setEnergy(0f);
        needs.setFun(0f);
        PoiEntry poi = poi(Set.of(AetherhavenConstants.POI_TAG_TOURIST_VISIT), PoiInteractionKind.SIT);
        PoiEffectTable.applyUseComplete(needs, poi);
        assertEquals(0f, needs.getHunger(), 0.01f);
        assertEquals(0f, needs.getEnergy(), 0.01f);
        assertEquals(0f, needs.getFun(), 0.01f);
    }

    @Test
    void workSurfaceDoesNotRestoreNeeds() {
        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(10f);
        needs.setEnergy(10f);
        needs.setFun(10f);
        PoiEntry poi = poi(Set.of("WORK"), PoiInteractionKind.WORK_SURFACE);
        PoiEffectTable.applyUseComplete(needs, poi);
        assertEquals(10f, needs.getHunger(), 0.01f);
        assertEquals(10f, needs.getEnergy(), 0.01f);
        assertEquals(10f, needs.getFun(), 0.01f);
    }

    @Test
    void feastRestoresHungerOnly() {
        VillagerNeeds needs = VillagerNeeds.full();
        needs.setHunger(0f);
        needs.setEnergy(0f);
        needs.setFun(0f);
        PoiEntry poi = poi(Set.of(AetherhavenConstants.POI_TAG_FEAST), PoiInteractionKind.USE_BENCH);
        PoiEffectTable.applyUseComplete(needs, poi);
        assertEquals(VillagerNeeds.MAX, needs.getHunger(), 0.01f);
        assertEquals(0f, needs.getEnergy(), 0.01f);
        assertEquals(0f, needs.getFun(), 0.01f);
    }

    private static PoiEntry poi(@Nonnull Set<String> tags, @Nonnull PoiInteractionKind kind) {
        return new PoiEntry(
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            64,
            0,
            tags,
            1,
            UUID.randomUUID(),
            null,
            kind
        );
    }
}
