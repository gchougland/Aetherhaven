package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Pure helpers for NPC-scoped dialogue conditions. */
public final class DialogueNpcConditionUtil {
    public static final float DEFAULT_NEARBY_RADIUS = 8f;

    private DialogueNpcConditionUtil() {}

    /** Full hearts shown in dialogue UI (0–10). */
    public static int reputationHearts(int reputation) {
        return Math.max(0, Math.min(10, reputation / 10));
    }

    /** Reputation threshold for at least {@code hearts} full hearts (0–10). */
    public static int reputationForHearts(int hearts) {
        return Math.max(0, Math.min(100, hearts * 10));
    }

    public static int needPercent(float value) {
        return Math.round(Math.max(0f, Math.min(VillagerNeeds.MAX, value)) / VillagerNeeds.MAX * 100f);
    }

    public static int minNeedPercent(@Nullable VillagerNeeds needs) {
        if (needs == null) {
            return 0;
        }
        float min = Math.min(needs.getHunger(), Math.min(needs.getEnergy(), needs.getFun()));
        return needPercent(min);
    }

    public static int hungerPercent(@Nullable VillagerNeeds needs) {
        return needs == null ? 0 : needPercent(needs.getHunger());
    }

    @Nullable
    public static VillagerNeedsSnapshot resolveSpeakerNeeds(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable TownRecord town
    ) {
        VillagerNeeds live = store.getComponent(npcRef, VillagerNeeds.getComponentType());
        if (live != null) {
            return new VillagerNeedsSnapshot(live.getHunger(), live.getEnergy(), live.getFun());
        }
        UUIDComponent uc = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (uc == null || town == null) {
            return null;
        }
        return residentNeedsSnapshot(town, uc.getUuid());
    }

    @Nullable
    private static VillagerNeedsSnapshot residentNeedsSnapshot(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        for (ResidentNpcRecord record : town.getResidentNpcRecords()) {
            if (!entityUuid.equals(record.getLastEntityUuid())) {
                continue;
            }
            if (!record.hasLastKnownNeeds()) {
                return null;
            }
            return new VillagerNeedsSnapshot(
                record.getLastKnownHunger(),
                record.getLastKnownEnergy(),
                record.getLastKnownFun()
            );
        }
        return null;
    }

    public static int minNeedPercent(@Nullable VillagerNeedsSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        float min = Math.min(snapshot.hunger(), Math.min(snapshot.energy(), snapshot.fun()));
        return needPercent(min);
    }

    public static int hungerPercent(@Nullable VillagerNeedsSnapshot snapshot) {
        return snapshot == null ? 0 : needPercent(snapshot.hunger());
    }

    public static boolean isOtherTownVillagerNearby(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> speakerRef,
        @Nonnull UUID townId,
        float radius,
        @Nullable String kindFilter
    ) {
        if (radius <= 0f) {
            return false;
        }
        TransformComponent speakerTransform = store.getComponent(speakerRef, TransformComponent.getComponentType());
        UUIDComponent speakerUuid = store.getComponent(speakerRef, UUIDComponent.getComponentType());
        if (speakerTransform == null || speakerUuid == null) {
            return false;
        }
        Vector3d position = speakerTransform.getPosition();
        UUID speakerEntityUuid = speakerUuid.getUuid();

        var npcSpatialResource = store.getResource(NPCPlugin.get().getNpcSpatialResource());
        if (npcSpatialResource == null) {
            return false;
        }
        List<Ref<EntityStore>> results = SpatialResource.getThreadLocalReferenceList();
        npcSpatialResource.getSpatialStructure().collect(position, radius, results);
        for (Ref<EntityStore> candidate : results) {
            if (candidate == null || !candidate.isValid() || candidate.equals(speakerRef)) {
                continue;
            }
            UUIDComponent candidateUuid = store.getComponent(candidate, UUIDComponent.getComponentType());
            if (candidateUuid != null && speakerEntityUuid.equals(candidateUuid.getUuid())) {
                continue;
            }
            if (store.getComponent(candidate, NPCEntity.getComponentType()) == null) {
                continue;
            }
            TownVillagerBinding binding = store.getComponent(candidate, TownVillagerBinding.getComponentType());
            if (binding == null || !townId.equals(binding.getTownId())) {
                continue;
            }
            if (kindFilter != null && !kindFilter.isBlank() && !kindFilter.equalsIgnoreCase(binding.getKind())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** True when {@code recordedDawnDay} falls within the last {@code withinDays} dawn-aligned days. */
    public static boolean dawnDayWithin(long recordedDawnDay, long currentDawnDay, int withinDays) {
        if (withinDays <= 0) {
            return false;
        }
        long delta = currentDawnDay - recordedDawnDay;
        return delta >= 0L && delta < withinDays;
    }

    public record VillagerNeedsSnapshot(float hunger, float energy, float fun) {}
}
