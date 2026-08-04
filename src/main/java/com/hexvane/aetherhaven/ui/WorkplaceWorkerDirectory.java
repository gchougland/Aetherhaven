package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
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

/** Town NPCs eligible for workplace assignment on a completed production plot. */
public final class WorkplaceWorkerDirectory {
    private WorkplaceWorkerDirectory() {}

    public record WorkplaceWorkerRow(
        @Nonnull String displayName,
        @Nonnull UUID entityUuid,
        @Nonnull String portraitPath,
        @Nonnull Message roleLine
    ) {}

    @Nonnull
    public static List<WorkplaceWorkerRow> listEligible(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String gameplayWorkplaceId,
        @Nullable String filterNpcRoleId
    ) {
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) {
            return List.of();
        }
        UUID tid = town.getTownId();
        Map<UUID, WorkplaceWorkerRow> byUuid = new LinkedHashMap<>();
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), npcType);
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, npcType);
                    if (uc == null || npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                        continue;
                    }
                    String roleName = npc.getRoleName().trim();
                    if (filterNpcRoleId != null && !roleName.equalsIgnoreCase(filterNpcRoleId)) {
                        continue;
                    }
                    VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(roleName);
                    if (vdef == null) {
                        continue;
                    }
                    String w = vdef.getWorkConstructionId();
                    if (w == null || !matchesWorkplace(plugin.getConstructionCatalog(), w, gameplayWorkplaceId)) {
                        continue;
                    }
                    UUID u = uc.getUuid();
                    TownResidentDisplay.Resolved display =
                        TownResidentDisplay.resolveFromChunk(archetypeChunk, i, roleName, plugin);
                    byUuid.put(
                        u,
                        new WorkplaceWorkerRow(
                            display.displayName(),
                            u,
                            display.portraitPath(),
                            HouseResidentDirectory.roleLineMessage(b.getKind(), roleName)
                        )
                    );
                }
            }
        );
        addStoryFallbackIfMissing(
            byUuid,
            town,
            plugin,
            gameplayWorkplaceId,
            filterNpcRoleId,
            town.getElderEntityUuid(),
            AetherhavenConstants.ELDER_NPC_ROLE_ID,
            TownVillagerBinding.KIND_ELDER
        );
        addStoryFallbackIfMissing(
            byUuid,
            town,
            plugin,
            gameplayWorkplaceId,
            filterNpcRoleId,
            town.getInnkeeperEntityUuid(),
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            TownVillagerBinding.KIND_INNKEEPER
        );
        List<WorkplaceWorkerRow> out = new ArrayList<>(byUuid.values());
        out.sort(Comparator.comparing(WorkplaceWorkerRow::displayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Nullable
    public static WorkplaceWorkerRow findRow(@Nonnull List<WorkplaceWorkerRow> rows, @Nonnull UUID entityUuid) {
        for (WorkplaceWorkerRow row : rows) {
            if (entityUuid.equals(row.entityUuid())) {
                return row;
            }
        }
        return null;
    }

    /**
     * True when another villager (not {@code currentlyAssignedUuid}) could work at this plot — clearing the current
     * worker would leave the building empty while a replacement is available.
     */
    public static boolean hasAlternateEligibleWorker(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String gameplayWorkplaceId,
        @Nullable String filterNpcRoleId,
        @Nullable UUID currentlyAssignedUuid
    ) {
        for (WorkplaceWorkerRow row : listEligible(store, town, plugin, gameplayWorkplaceId, filterNpcRoleId)) {
            if (currentlyAssignedUuid == null || !currentlyAssignedUuid.equals(row.entityUuid())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static WorkplaceWorkerRow resolvePreviewRow(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String gameplayWorkplaceId,
        @Nullable String filterNpcRoleId,
        @Nonnull UUID entityUuid
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref != null && ref.isValid()) {
            TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (b != null && npc != null && npc.getRoleName() != null) {
                String roleId = npc.getRoleName().trim();
                if (filterNpcRoleId == null || roleId.equalsIgnoreCase(filterNpcRoleId)) {
                    TownResidentDisplay.Resolved display = TownResidentDisplay.resolveFromEntity(store, ref, roleId, plugin);
                    return new WorkplaceWorkerRow(
                        display.displayName(),
                        entityUuid,
                        display.portraitPath(),
                        HouseResidentDirectory.roleLineMessage(b.getKind(), roleId)
                    );
                }
            }
        }
        if (entityUuid.equals(town.getElderEntityUuid())
            && AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL.equals(gameplayWorkplaceId)
            && (filterNpcRoleId == null || AetherhavenConstants.ELDER_NPC_ROLE_ID.equalsIgnoreCase(filterNpcRoleId))) {
            TownResidentDisplay.Resolved display =
                TownResidentDisplay.resolveOffline(plugin, AetherhavenConstants.ELDER_NPC_ROLE_ID, null, null);
            return new WorkplaceWorkerRow(
                display.displayName(),
                entityUuid,
                display.portraitPath(),
                HouseResidentDirectory.roleLineMessage(TownVillagerBinding.KIND_ELDER, AetherhavenConstants.ELDER_NPC_ROLE_ID)
            );
        }
        if (entityUuid.equals(town.getInnkeeperEntityUuid())
            && AetherhavenConstants.CONSTRUCTION_PLOT_INN.equals(gameplayWorkplaceId)
            && (filterNpcRoleId == null || AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equalsIgnoreCase(filterNpcRoleId))) {
            TownResidentDisplay.Resolved display =
                TownResidentDisplay.resolveOffline(plugin, AetherhavenConstants.INNKEEPER_NPC_ROLE_ID, null, null);
            return new WorkplaceWorkerRow(
                display.displayName(),
                entityUuid,
                display.portraitPath(),
                HouseResidentDirectory.roleLineMessage(TownVillagerBinding.KIND_INNKEEPER, AetherhavenConstants.INNKEEPER_NPC_ROLE_ID)
            );
        }
        return null;
    }

    static boolean matchesWorkplace(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String workConstructionId,
        @Nonnull String gameplayWorkplaceId
    ) {
        if (workConstructionId.equals(gameplayWorkplaceId)) {
            return true;
        }
        return catalog.matchesGameplayConstruction(workConstructionId, gameplayWorkplaceId);
    }

    private static void addStoryFallbackIfMissing(
        @Nonnull Map<UUID, WorkplaceWorkerRow> byUuid,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String gameplayWorkplaceId,
        @Nullable String filterNpcRoleId,
        @Nullable UUID entityUuid,
        @Nonnull String roleId,
        @Nonnull String kind
    ) {
        if (entityUuid == null || byUuid.containsKey(entityUuid)) {
            return;
        }
        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(roleId);
        if (vdef == null || vdef.getWorkConstructionId() == null
            || !matchesWorkplace(plugin.getConstructionCatalog(), vdef.getWorkConstructionId(), gameplayWorkplaceId)) {
            return;
        }
        if (filterNpcRoleId != null && !roleId.equalsIgnoreCase(filterNpcRoleId)) {
            return;
        }
        TownResidentDisplay.Resolved display = TownResidentDisplay.resolveOffline(plugin, roleId, null, null);
        byUuid.put(
            entityUuid,
            new WorkplaceWorkerRow(
                display.displayName(),
                entityUuid,
                display.portraitPath(),
                HouseResidentDirectory.roleLineMessage(kind, roleId)
            )
        );
    }
}
