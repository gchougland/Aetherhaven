package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.patrol.GuardPatrolSystem;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownVillagerDeathNotifier;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Iterator;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Handles town NPC death: guards return to pool; adventurers release checkout; inn visitors free slots. */
public final class VillagerDeathHandlerSystem extends DeathSystems.OnDeathSystem {
    @Nonnull
    private final AetherhavenPlugin plugin;

    public VillagerDeathHandlerSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(TownVillagerBinding.getComponentType(), EntityStatMap.getComponentType());
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull DeathComponent death,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TownVillagerBinding binding = store.getComponent(victimRef, TownVillagerBinding.getComponentType());
        if (binding == null) {
            return;
        }
        UUIDComponent uc = store.getComponent(victimRef, UUIDComponent.getComponentType());
        UUID entityUuid = uc != null ? uc.getUuid() : null;

        World world = store.getExternalData().getWorld();
        var tm = com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null) {
            return;
        }

        NPCEntity npc = store.getComponent(victimRef, NPCEntity.getComponentType());
        String roleId = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
        String kind = binding.getKind();

        if (TownVillagerBinding.KIND_GUARD.equals(kind)) {
            boolean citizen = wasGuardCitizen(town, entityUuid, victimRef, store);
            handleGuardDeath(world, town, tm, victimRef, store, entityUuid);
            TownVillagerDeathNotifier.notifyTownMembers(
                store,
                victimRef,
                plugin,
                town,
                roleId,
                kind,
                citizen ? TownVillagerDeathNotifier.DeathCategory.CITIZEN : TownVillagerDeathNotifier.DeathCategory.GUARD,
                entityUuid,
                death
            );
            return;
        }

        if (entityUuid != null && GuildHallAdventurerPoolService.isGuildHallAdventurer(town, entityUuid)) {
            town.getGuildHallAdventurerNpcIds().removeIf(s -> entityUuid.toString().equalsIgnoreCase(s != null ? s.trim() : ""));
            town.getGuildHallAdventurerSlotByNpcId().remove(entityUuid.toString());
            TownsfolkCharacterBinding tb = store.getComponent(victimRef, TownsfolkCharacterBinding.getComponentType());
            if (tb != null) {
                TownsfolkExistenceService.releaseByEntity(world, plugin, entityUuid);
            }
            tm.updateTown(town);
            TownVillagerDeathNotifier.notifyTownMembers(
                store,
                victimRef,
                plugin,
                town,
                roleId,
                kind,
                TownVillagerDeathNotifier.DeathCategory.VISITOR,
                entityUuid,
                death
            );
            return;
        }

        if (entityUuid != null && TouristPortalTickService.findTouristRecord(town, entityUuid) != null) {
            handleTouristDeath(world, plugin, town, tm, victimRef, store, entityUuid);
            TownVillagerDeathNotifier.notifyTownMembers(
                store,
                victimRef,
                plugin,
                town,
                roleId,
                kind,
                TownVillagerDeathNotifier.DeathCategory.TOURIST,
                entityUuid,
                death
            );
            return;
        }

        if (TownVillagerBinding.isVisitorKind(kind)) {
            if (entityUuid != null) {
                InnPoolService.onVisitorEntityDeath(world, plugin, town, tm, entityUuid);
            }
            clearHomeResident(town, entityUuid);
            if (entityUuid != null) {
                town.getResidentNpcRecords().removeIf(r -> entityUuid.equals(r.getLastEntityUuid()));
            }
            tm.updateTown(town);
            TownVillagerDeathNotifier.notifyTownMembers(
                store,
                victimRef,
                plugin,
                town,
                roleId,
                kind,
                TownVillagerDeathNotifier.DeathCategory.VISITOR,
                entityUuid,
                death
            );
            return;
        }

        handleJobVillagerDeath(town, tm, entityUuid, kind, roleId, binding.getJobPlotId());
        TownVillagerDeathNotifier.DeathCategory category =
            TownVillagerBinding.isRescueKind(kind)
                ? TownVillagerDeathNotifier.DeathCategory.VISITOR
                : deathCategoryForJobVillager(town, entityUuid, kind);
        TownVillagerDeathNotifier.notifyTownMembers(store, victimRef, plugin, town, roleId, kind, category, entityUuid, death);
    }

    private static boolean wasGuardCitizen(
        @Nonnull TownRecord town,
        @Nullable UUID entityUuid,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (entityUuid != null) {
            for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
                if (entityUuid.equals(rec.getEntityUuid()) && rec.isCitizen()) {
                    return true;
                }
            }
        }
        TownsfolkCharacterBinding tb = store.getComponent(victimRef, TownsfolkCharacterBinding.getComponentType());
        if (tb != null) {
            String characterId = tb.getCharacterId();
            for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
                if (characterId.equalsIgnoreCase(rec.getCharacterId()) && rec.isCitizen()) {
                    return true;
                }
            }
        }
        return entityUuid != null && hasTownHouse(town, entityUuid);
    }

    @Nonnull
    private static TownVillagerDeathNotifier.DeathCategory deathCategoryForJobVillager(
        @Nonnull TownRecord town,
        @Nullable UUID entityUuid,
        @Nonnull String kind
    ) {
        if (entityUuid != null && hasTownHouse(town, entityUuid)) {
            return TownVillagerDeathNotifier.DeathCategory.CITIZEN;
        }
        if (TownVillagerBinding.KIND_TOWNSFOLK.equals(kind)) {
            return TownVillagerDeathNotifier.DeathCategory.VILLAGER;
        }
        return TownVillagerDeathNotifier.DeathCategory.VILLAGER;
    }

    private static void handleJobVillagerDeath(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nullable UUID entityUuid,
        @Nonnull String kind,
        @Nonnull String roleId,
        @Nullable UUID jobPlotId
    ) {
        if (entityUuid == null) {
            return;
        }
        ResidentRegistryService.markPendingDawnRevivalOnDeath(town, tm, entityUuid, roleId, kind, jobPlotId);
        if (entityUuid.equals(town.getElderEntityUuid())) {
            town.setElderEntityUuid(null);
        }
        if (entityUuid.equals(town.getInnkeeperEntityUuid())) {
            town.setInnkeeperEntityUuid(null);
        }
        InnPoolService.removeVisitorFromPool(town, entityUuid);
        clearHomeResident(town, entityUuid);
        if (TownVillagerBinding.KIND_TOWNSFOLK.equals(kind)) {
            town.getResidentNpcRecords().removeIf(r -> entityUuid.equals(r.getLastEntityUuid()));
        }
        tm.updateTown(town);
    }

    private static void clearHomeResident(@Nonnull TownRecord town, @Nullable UUID entityUuid) {
        if (entityUuid == null) {
            return;
        }
        for (var plot : town.getPlotInstances()) {
            if (plot.hasHomeResident(entityUuid)) {
                plot.clearHomeResidentUuid(entityUuid);
            }
        }
    }

    private static boolean hasTownHouse(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        for (var plot : town.getPlotInstances()) {
            if (plot.hasHomeResident(entityUuid)) {
                return true;
            }
        }
        return false;
    }

    private static void handleTouristDeath(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid
    ) {
        TownsfolkCharacterBinding tb = store.getComponent(victimRef, TownsfolkCharacterBinding.getComponentType());
        String characterId = tb != null ? tb.getCharacterId() : null;

        Iterator<TouristRecord> it = town.getTouristRecords().iterator();
        while (it.hasNext()) {
            TouristRecord rec = it.next();
            if (entityUuid.equals(rec.getEntityUuid())) {
                it.remove();
                if (characterId == null) {
                    characterId = rec.getCharacterId();
                }
                break;
            }
        }

        if (characterId != null && !characterId.isBlank()) {
            if (entityUuid != null) {
                TownsfolkExistenceService.releaseByEntity(world, plugin, entityUuid);
            } else {
                TownsfolkExistenceService.releaseCharacter(
                    world,
                    plugin,
                    town.getTownId(),
                    characterId,
                    TownsfolkExistenceService.ReleaseReason.DEATH
                );
            }
        }

        clearHomeResident(town, entityUuid);
        town.getResidentNpcRecords().removeIf(r -> entityUuid.equals(r.getLastEntityUuid()));
        UUID questTarget = town.getQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        if (entityUuid.equals(questTarget)
            || (questTarget == null && town.hasQuestActive(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK))) {
            town.clearActiveQuest(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        }
        tm.updateTown(town);
    }

    private static void handleGuardDeath(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull Store<EntityStore> store,
        @Nullable UUID entityUuid
    ) {
        TownsfolkCharacterBinding tb = store.getComponent(victimRef, TownsfolkCharacterBinding.getComponentType());
        String characterId = tb != null ? tb.getCharacterId() : null;
        characterId = GuardHireService.removeHiredGuardFromTown(town, entityUuid, characterId);

        if (characterId != null && !characterId.isBlank()) {
            if (entityUuid != null) {
                TownsfolkExistenceService.releaseByEntity(world, AetherhavenPlugin.get(), entityUuid);
            } else {
                TownsfolkExistenceService.releaseCharacter(
                    world,
                    AetherhavenPlugin.get(),
                    town.getTownId(),
                    characterId,
                    TownsfolkExistenceService.ReleaseReason.DEATH
                );
            }
        }

        if (entityUuid != null) {
            GuardPatrolSystem.clearAssignmentsForGuard(world, AetherhavenPlugin.get(), entityUuid);
        }
        tm.updateTown(town);
    }

    /** Promote a housed guard to a tax paying citizen. */
    public static void promoteGuardToCitizen(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID guardEntityUuid,
        @Nonnull Store<EntityStore> store
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(guardEntityUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(guardEntityUuid)) {
                rec.setCitizen(true);
                break;
            }
        }
        ResidentRegistryService.syncHouseAssignment(town, tm, store, guardEntityUuid);
        tm.updateTown(town);
    }
}
