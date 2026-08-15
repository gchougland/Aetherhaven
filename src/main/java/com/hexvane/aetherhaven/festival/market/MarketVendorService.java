package com.hexvane.aetherhaven.festival.market;

import com.hexvane.aetherhaven.festival.FestivalAttendanceService;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Year-seeded resident vendors for the three market stall spots. */
public final class MarketVendorService {
    private MarketVendorService() {}

    public static void assignAndSend(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        long year
    ) {
        MarketSession session = MarketSessionIndex.getOrCreate(town.getTownId());
        session.setYear(year);
        List<Candidate> candidates = new ArrayList<>();
        store.forEachChunk(
            Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, ignored) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (binding == null || uc == null || !town.getTownId().equals(binding.getTownId())) {
                        continue;
                    }
                    String kind = binding.getKind();
                    if (!isEligibleKind(kind)) {
                        continue;
                    }
                    candidates.add(new Candidate(uc.getUuid(), kind.trim().toLowerCase()));
                }
            }
        );
        long seed = year * 31L + town.getTownId().getMostSignificantBits() + town.getTownId().getLeastSignificantBits();
        Collections.shuffle(candidates, new Random(seed));
        List<UUID> chosen = new ArrayList<>();
        for (Candidate c : candidates) {
            if (chosen.size() >= MarketIds.SHOP_SPOT_COUNT) {
                break;
            }
            boolean duplicateKind = false;
            for (int i = 0; i < chosen.size(); i++) {
                Candidate already = find(candidates, chosen.get(i));
                if (already != null && already.kind.equals(c.kind)) {
                    duplicateKind = true;
                    break;
                }
            }
            if (duplicateKind) {
                continue;
            }
            chosen.add(c.uuid);
        }
        if (chosen.size() < MarketIds.SHOP_SPOT_COUNT) {
            for (Candidate c : candidates) {
                if (chosen.size() >= MarketIds.SHOP_SPOT_COUNT) {
                    break;
                }
                if (!chosen.contains(c.uuid)) {
                    chosen.add(c.uuid);
                }
            }
        }
        session.setVendorUuids(chosen);
        FestivalAttendanceService.interruptVillagerUuids(store, chosen);
    }

    @Nullable
    public static String kindForVendor(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID villagerUuid
    ) {
        final String[] found = {null};
        store.forEachChunk(
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, ignored) -> {
                if (found[0] != null) {
                    return;
                }
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !villagerUuid.equals(uc.getUuid())) {
                        continue;
                    }
                    TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (binding != null && binding.getKind() != null) {
                        found[0] = binding.getKind().trim().toLowerCase();
                        return;
                    }
                }
            }
        );
        return found[0];
    }

    public static boolean isEligibleKind(@Nullable String kind) {
        if (kind == null || kind.isBlank()) {
            return false;
        }
        String key = kind.trim().toLowerCase();
        if (TownVillagerBinding.isVisitorKind(key)
            || TownVillagerBinding.isRescueKind(key)
            || TownVillagerBinding.KIND_ELDER.equals(key)
            || TownVillagerBinding.KIND_GUARD.equals(key)
            || TownVillagerBinding.KIND_TOWNSFOLK.equals(key)) {
            return false;
        }
        return MarketIds.hasShopTable(key);
    }

    @Nullable
    private static Candidate find(@Nonnull List<Candidate> all, @Nonnull UUID uuid) {
        for (Candidate c : all) {
            if (uuid.equals(c.uuid)) {
                return c;
            }
        }
        return null;
    }

    private record Candidate(@Nonnull UUID uuid, @Nonnull String kind) {}
}
