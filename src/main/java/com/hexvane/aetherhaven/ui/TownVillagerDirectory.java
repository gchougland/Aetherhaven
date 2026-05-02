package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Town residents (non-visitor) with {@link TownVillagerBinding}, in the same order as the needs overview: elder,
 * innkeeper, then other roles by label.
 */
public final class TownVillagerDirectory {
    private TownVillagerDirectory() {}

    @Nonnull
    public static List<TownVillagerRow> listResidents(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        UUID tid = town.getTownId();
        Map<UUID, TownVillagerRow> byUuid = new LinkedHashMap<>();
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    if (uc == null || npc == null || npc.getRoleName() == null) {
                        continue;
                    }
                    UUID u = uc.getUuid();
                    String roleId = npc.getRoleName();
                    String label = NpcPortraitProvider.displayLabelForRoleId(roleId);
                    int ko = kindOrderForBindingKind(b.getKind());
                    byUuid.put(u, new TownVillagerRow(label, u, roleId, ko));
                }
            }
        );

        addFallbackIfMissing(byUuid, town.getElderEntityUuid(), AetherhavenConstants.ELDER_NPC_ROLE_ID);
        addFallbackIfMissing(byUuid, town.getInnkeeperEntityUuid(), AetherhavenConstants.INNKEEPER_NPC_ROLE_ID);
        addResidentsFromRegistry(byUuid, town);

        List<TownVillagerRow> out = new ArrayList<>(byUuid.values());
        out.sort(
            Comparator.comparingInt(TownVillagerRow::kindOrder).thenComparing(TownVillagerRow::label, String.CASE_INSENSITIVE_ORDER)
        );
        return out;
    }

    public static int indexOfEntity(@Nonnull List<TownVillagerRow> rows, @Nonnull UUID entityUuid) {
        for (int i = 0; i < rows.size(); i++) {
            if (entityUuid.equals(rows.get(i).entityUuid())) {
                return i;
            }
        }
        return -1;
    }

    private static void addFallbackIfMissing(
        @Nonnull Map<UUID, TownVillagerRow> byUuid,
        @Nullable UUID entityUuid,
        @Nonnull String roleId
    ) {
        if (entityUuid == null || byUuid.containsKey(entityUuid)) {
            return;
        }
        byUuid.put(
            entityUuid,
            new TownVillagerRow(
                NpcPortraitProvider.displayLabelForRoleId(roleId),
                entityUuid,
                roleId,
                kindOrderForRoleId(roleId)
            )
        );
    }

    /**
     * Residents whose entities are not in any loaded chunk still appear in town management (same idea as elder /
     * innkeeper fallbacks).
     */
    private static void addResidentsFromRegistry(@Nonnull Map<UUID, TownVillagerRow> byUuid, @Nonnull TownRecord town) {
        UUID nil = new UUID(0L, 0L);
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (TownVillagerBinding.isVisitorKind(r.getKind())) {
                continue;
            }
            UUID u = r.getLastEntityUuid();
            if (u.equals(nil)) {
                continue;
            }
            if (byUuid.containsKey(u)) {
                continue;
            }
            String roleId = r.getNpcRoleId().trim();
            if (roleId.isEmpty()) {
                continue;
            }
            String kind = r.getKind();
            int ko =
                kind != null && !kind.isBlank() ? kindOrderForBindingKind(kind) : kindOrderForRoleId(roleId);
            byUuid.put(u, new TownVillagerRow(NpcPortraitProvider.displayLabelForRoleId(roleId), u, roleId, ko));
        }
    }

    private static int kindOrderForBindingKind(@Nonnull String kind) {
        if (TownVillagerBinding.KIND_ELDER.equals(kind)) {
            return 0;
        }
        if (TownVillagerBinding.KIND_INNKEEPER.equals(kind)) {
            return 1;
        }
        if (TownVillagerBinding.KIND_MERCHANT.equals(kind)) {
            return 2;
        }
        if (TownVillagerBinding.KIND_FARMER.equals(kind)) {
            return 2;
        }
        if (TownVillagerBinding.KIND_BLACKSMITH.equals(kind)) {
            return 2;
        }
        if (TownVillagerBinding.KIND_PRIESTESS.equals(kind)) {
            return 2;
        }
        if (TownVillagerBinding.KIND_MINER.equals(kind)) {
            return 2;
        }
        if (TownVillagerBinding.KIND_LOGGER.equals(kind)) {
            return 2;
        }
        if (TownVillagerBinding.KIND_RANCHER.equals(kind)) {
            return 2;
        }
        return 3;
    }

    private static int kindOrderForRoleId(@Nonnull String roleId) {
        if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equals(roleId)) {
            return 0;
        }
        if (AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equals(roleId)) {
            return 1;
        }
        if (AetherhavenConstants.NPC_MERCHANT.equals(roleId)) {
            return 2;
        }
        if (AetherhavenConstants.NPC_FARMER.equals(roleId)) {
            return 2;
        }
        if (AetherhavenConstants.NPC_BLACKSMITH.equals(roleId)) {
            return 2;
        }
        if (AetherhavenConstants.NPC_PRIESTESS.equals(roleId)) {
            return 2;
        }
        if (AetherhavenConstants.NPC_MINER.equals(roleId)) {
            return 2;
        }
        if (AetherhavenConstants.NPC_LOGGER.equals(roleId)) {
            return 2;
        }
        if (AetherhavenConstants.NPC_RANCHER.equals(roleId)) {
            return 2;
        }
        return 3;
    }
}
