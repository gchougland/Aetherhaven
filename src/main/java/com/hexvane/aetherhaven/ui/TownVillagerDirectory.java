package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hexvane.aetherhaven.town.TownResidentEligibility;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
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
 * Town residents for UI lists: story villagers always; townsfolk and guards only when assigned a house.
 */
public final class TownVillagerDirectory {
    private TownVillagerDirectory() {}

    @Nonnull
    public static List<TownVillagerRow> listResidents(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return List.of();
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        UUID tid = town.getTownId();
        Map<UUID, TownVillagerRow> byUuid = new LinkedHashMap<>();
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
                    String roleId = npc.getRoleName().trim();
                    if (!TownResidentEligibility.includeInResidentList(town, u, b, roleId, plugin, catalog)) {
                        continue;
                    }
                    TownResidentDisplay.Resolved display = TownResidentDisplay.resolveFromChunk(archetypeChunk, i, roleId, plugin);
                    int ko = kindOrderForBindingKind(b.getKind());
                    boolean needs = TownResidentEligibility.usesVillagerNeeds(b.getKind(), roleId, plugin);
                    byUuid.put(
                        u,
                        new TownVillagerRow(display.displayName(), u, roleId, b.getKind(), ko, display.portraitPath(), needs)
                    );
                }
            }
        );

        addStoryFallbackIfMissing(byUuid, town, plugin, town.getElderEntityUuid(), AetherhavenConstants.ELDER_NPC_ROLE_ID, TownVillagerBinding.KIND_ELDER);
        addStoryFallbackIfMissing(
            byUuid,
            town,
            plugin,
            town.getInnkeeperEntityUuid(),
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            TownVillagerBinding.KIND_INNKEEPER
        );
        addResidentsFromRegistry(byUuid, town, plugin, catalog);
        addHousedTownsfolkFallbacks(byUuid, town, store, plugin, catalog);

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

    private static void addStoryFallbackIfMissing(
        @Nonnull Map<UUID, TownVillagerRow> byUuid,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable UUID entityUuid,
        @Nonnull String roleId,
        @Nonnull String kind
    ) {
        if (entityUuid == null || byUuid.containsKey(entityUuid)) {
            return;
        }
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        if (!TownResidentEligibility.includeInResidentList(town, entityUuid, kind, roleId, plugin, catalog)) {
            return;
        }
        TownResidentDisplay.Resolved display = TownResidentDisplay.resolveOffline(plugin, roleId, null, null);
        byUuid.put(
            entityUuid,
            new TownVillagerRow(
                display.displayName(),
                entityUuid,
                roleId,
                kind,
                kindOrderForBindingKind(kind),
                display.portraitPath(),
                TownResidentEligibility.usesVillagerNeeds(kind, roleId, plugin)
            )
        );
    }

    /**
     * Story villagers only from registry (one row per role). Townsfolk and guards use per entity fallbacks instead.
     */
    private static void addResidentsFromRegistry(
        @Nonnull Map<UUID, TownVillagerRow> byUuid,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog catalog
    ) {
        UUID nil = new UUID(0L, 0L);
        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (TownVillagerBinding.isVisitorKind(r.getKind())) {
                continue;
            }
            if (TownResidentEligibility.isTownsfolkPoolKind(r.getKind(), r.getNpcRoleId(), plugin)) {
                continue;
            }
            UUID u = r.getLastEntityUuid();
            if (u.equals(nil) || byUuid.containsKey(u)) {
                continue;
            }
            String roleId = r.getNpcRoleId().trim();
            if (roleId.isEmpty()) {
                continue;
            }
            if (!TownResidentEligibility.includeInResidentList(town, u, r.getKind(), roleId, plugin, catalog)) {
                continue;
            }
            String kind = r.getKind();
            int ko = kind != null && !kind.isBlank() ? kindOrderForBindingKind(kind) : kindOrderForRoleId(roleId);
            TownResidentDisplay.Resolved display = TownResidentDisplay.resolveOffline(plugin, roleId, null, null);
            byUuid.put(
                u,
                new TownVillagerRow(
                    display.displayName(),
                    u,
                    roleId,
                    kind != null ? kind : "",
                    ko,
                    display.portraitPath(),
                    TownResidentEligibility.usesVillagerNeeds(kind != null ? kind : "", roleId, plugin)
                )
            );
        }
    }

    private static void addHousedTownsfolkFallbacks(
        @Nonnull Map<UUID, TownVillagerRow> byUuid,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog catalog
    ) {
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            if (!AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(catalog.resolveGameplayConstructionId(plot.getConstructionId()))) {
                continue;
            }
            for (UUID home : plot.getHomeResidentEntityUuids()) {
                if (byUuid.containsKey(home)) {
                    continue;
                }
                addHousedEntityFallback(byUuid, town, store, plugin, catalog, home);
            }
        }
        for (HiredGuardRecord guard : town.getHiredGuardRecords()) {
            UUID guardUuid = guard.getEntityUuid();
            if (guardUuid == null || byUuid.containsKey(guardUuid)) {
                continue;
            }
            if (!town.isNpcHomeResidentOnHousePlot(guardUuid, catalog)) {
                continue;
            }
            addHousedEntityFallback(byUuid, town, store, plugin, catalog, guardUuid);
        }
    }

    private static void addHousedEntityFallback(
        @Nonnull Map<UUID, TownVillagerRow> byUuid,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID entityUuid
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref != null && ref.isValid()) {
            TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (b != null && npc != null && npc.getRoleName() != null) {
                String roleId = npc.getRoleName().trim();
                if (TownResidentEligibility.includeInResidentList(town, entityUuid, b, roleId, plugin, catalog)) {
                    TownResidentDisplay.Resolved display = TownResidentDisplay.resolveFromEntity(store, ref, roleId, plugin);
                    byUuid.put(
                        entityUuid,
                        new TownVillagerRow(
                            display.displayName(),
                            entityUuid,
                            roleId,
                            b.getKind(),
                            kindOrderForBindingKind(b.getKind()),
                            display.portraitPath(),
                            TownResidentEligibility.usesVillagerNeeds(b.getKind(), roleId, plugin)
                        )
                    );
                }
                return;
            }
        }
        String roleId = AetherhavenConstants.NPC_GUARD_KNIGHT;
        String kind = TownVillagerBinding.KIND_GUARD;
        String characterId = null;
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(entityUuid)) {
                characterId = rec.getCharacterId();
                break;
            }
        }
        if (characterId == null) {
            roleId = AetherhavenConstants.NPC_TOWNSFOLK;
            kind = TownVillagerBinding.KIND_TOWNSFOLK;
        }
        if (!TownResidentEligibility.includeInResidentList(town, entityUuid, kind, roleId, plugin, catalog)) {
            return;
        }
        TownResidentDisplay.Resolved display = TownResidentDisplay.resolveOffline(plugin, roleId, characterId, null);
        byUuid.put(
            entityUuid,
            new TownVillagerRow(
                display.displayName(),
                entityUuid,
                roleId,
                kind,
                kindOrderForBindingKind(kind),
                display.portraitPath(),
                false
            )
        );
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
