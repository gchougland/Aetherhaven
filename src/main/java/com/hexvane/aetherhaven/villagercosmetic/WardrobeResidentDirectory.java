package com.hexvane.aetherhaven.villagercosmetic;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.ui.TownVillagerDirectory;
import com.hexvane.aetherhaven.ui.TownVillagerRow;
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
 * Residents for the villager wardrobe: journal residents plus citizen/invited tourists and hired guards.
 */
public final class WardrobeResidentDirectory {
    private WardrobeResidentDirectory() {}

    public record WardrobeResidentRow(
        @Nonnull String label,
        @Nonnull UUID entityUuid,
        @Nonnull String residentKey,
        @Nonnull String roleId,
        @Nonnull String bindingKind,
        @Nonnull String portraitPath,
        @Nullable String modelAssetId
    ) {}

    @Nonnull
    public static List<WardrobeResidentRow> list(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin
    ) {
        Map<UUID, WardrobeResidentRow> byUuid = new LinkedHashMap<>();
        for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
            String key = resolveKeyFromLive(store, town, row.entityUuid(), row.roleId());
            if (key == null) {
                continue;
            }
            byUuid.put(
                row.entityUuid(),
                new WardrobeResidentRow(
                    row.label(),
                    row.entityUuid(),
                    key,
                    row.roleId(),
                    row.bindingKind(),
                    row.portraitPath(),
                    resolveModelAssetId(store, row.entityUuid(), null)
                )
            );
        }

        UUID tid = town.getTownId();
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId())) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    if (uc == null || npc == null || npc.getRoleName() == null) {
                        continue;
                    }
                    UUID u = uc.getUuid();
                    if (byUuid.containsKey(u)) {
                        continue;
                    }
                    boolean include =
                        TownVillagerBinding.KIND_GUARD.equals(b.getKind())
                            || isResidentTourist(town, u);
                    if (!include) {
                        continue;
                    }
                    String roleId = npc.getRoleName().trim();
                    TownResidentDisplay.Resolved display =
                        TownResidentDisplay.resolveFromChunk(archetypeChunk, i, roleId, plugin);
                    TownsfolkCharacterBinding tb =
                        archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                    String key =
                        tb != null && tb.getCharacterId() != null && !tb.getCharacterId().isBlank()
                            ? VillagerCosmeticKeys.characterKey(tb.getCharacterId())
                            : VillagerCosmeticKeys.roleKey(roleId);
                    String modelId = tb != null ? tb.getModelAssetId() : null;
                    byUuid.put(
                        u,
                        new WardrobeResidentRow(
                            display.displayName(),
                            u,
                            key,
                            roleId,
                            b.getKind(),
                            display.portraitPath(),
                            modelId
                        )
                    );
                }
            }
        );

        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID guardUuid = rec.getEntityUuid();
            if (guardUuid == null || byUuid.containsKey(guardUuid)) {
                continue;
            }
            String characterId = rec.getCharacterId();
            if (characterId == null || characterId.isBlank()) {
                continue;
            }
            TownResidentDisplay.Resolved display =
                TownResidentDisplay.resolveOffline(plugin, "", characterId, null);
            String modelId = null;
            var character = plugin.getTownsfolkCharacterCatalog().byId(characterId);
            if (character != null) {
                modelId = character.getModelAssetId();
            }
            byUuid.put(
                guardUuid,
                new WardrobeResidentRow(
                    display.displayName(),
                    guardUuid,
                    VillagerCosmeticKeys.characterKey(characterId),
                    "",
                    TownVillagerBinding.KIND_GUARD,
                    display.portraitPath(),
                    modelId
                )
            );
        }

        for (TouristRecord rec : town.getTouristRecords()) {
            if (rec == null || (!rec.isCitizen() && !rec.isInvitedToStay())) {
                continue;
            }
            UUID touristUuid = rec.getEntityUuid();
            if (touristUuid == null || byUuid.containsKey(touristUuid)) {
                continue;
            }
            String characterId = rec.getCharacterId();
            if (characterId == null || characterId.isBlank()) {
                continue;
            }
            TownResidentDisplay.Resolved display =
                TownResidentDisplay.resolveOffline(plugin, "", characterId, null);
            String modelId = null;
            var character = plugin.getTownsfolkCharacterCatalog().byId(characterId);
            if (character != null) {
                modelId = character.getModelAssetId();
            }
            byUuid.put(
                touristUuid,
                new WardrobeResidentRow(
                    display.displayName(),
                    touristUuid,
                    VillagerCosmeticKeys.characterKey(characterId),
                    "",
                    TownVillagerBinding.KIND_TOWNSFOLK,
                    display.portraitPath(),
                    modelId
                )
            );
        }

        List<WardrobeResidentRow> out = new ArrayList<>(byUuid.values());
        out.sort(Comparator.comparing(WardrobeResidentRow::label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static boolean isResidentTourist(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        for (TouristRecord rec : town.getTouristRecords()) {
            if (rec == null) {
                continue;
            }
            UUID u = rec.getEntityUuid();
            if (entityUuid.equals(u) && (rec.isCitizen() || rec.isInvitedToStay())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String resolveKeyFromLive(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull String roleId
    ) {
        final String[] found = {null};
        Query<EntityStore> q =
            Query.and(UUIDComponent.getComponentType(), TownVillagerBinding.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !entityUuid.equals(uc.getUuid())) {
                        continue;
                    }
                    TownsfolkCharacterBinding tb =
                        archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                    if (tb != null && tb.getCharacterId() != null && !tb.getCharacterId().isBlank()) {
                        found[0] = VillagerCosmeticKeys.characterKey(tb.getCharacterId());
                        return;
                    }
                    if (roleId != null && !roleId.isBlank()) {
                        found[0] = VillagerCosmeticKeys.roleKey(roleId);
                    }
                    return;
                }
            }
        );
        if (found[0] != null) {
            return found[0];
        }
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            if (entityUuid.equals(rec.getEntityUuid())
                && rec.getCharacterId() != null
                && !rec.getCharacterId().isBlank()) {
                return VillagerCosmeticKeys.characterKey(rec.getCharacterId());
            }
        }
        for (TouristRecord rec : town.getTouristRecords()) {
            if (entityUuid.equals(rec.getEntityUuid())
                && rec.getCharacterId() != null
                && !rec.getCharacterId().isBlank()) {
                return VillagerCosmeticKeys.characterKey(rec.getCharacterId());
            }
        }
        if (roleId != null && !roleId.isBlank()) {
            return VillagerCosmeticKeys.roleKey(roleId);
        }
        return null;
    }

    @Nullable
    private static String resolveModelAssetId(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid,
        @Nullable String fallback
    ) {
        final String[] found = {fallback};
        Query<EntityStore> q = Query.and(UUIDComponent.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc == null || !entityUuid.equals(uc.getUuid())) {
                        continue;
                    }
                    TownsfolkCharacterBinding tb =
                        archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                    if (tb != null && tb.getModelAssetId() != null && !tb.getModelAssetId().isBlank()) {
                        found[0] = tb.getModelAssetId();
                    }
                    return;
                }
            }
        );
        return found[0];
    }
}
