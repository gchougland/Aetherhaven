package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.guild.BardWorkPoiResolver;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** When the guild hall prefab completes, assign Lyra from the inn pool to the hall plot. */
public final class GuildHallCompletion {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private GuildHallCompletion() {}

    public static void onGuildHallBuilt(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull TownManager tm
    ) {
        town.setGuildHallActive(true);
        tm.updateTown(town);

        if (!town.hasQuestActiveOrCompleted(AetherhavenConstants.QUEST_BUILD_GUILD_HALL)) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PoiEntry work = null;
        for (PoiEntry e : reg.listByTown(town.getTownId())) {
            if (plotId.equals(e.getPlotId()) && e.getTags().contains("WORK")) {
                if (e.getTags().contains(AetherhavenConstants.POI_TAG_BARD)) {
                    continue;
                }
                work = e;
                break;
            }
        }
        if (work == null) {
            LOGGER.atWarning().log("No WORK POI for guild hall plot %s", plotId);
            return;
        }

        Ref<EntityStore> masterRef = findGuildMasterRef(store, town);
        if (masterRef == null || !masterRef.isValid()) {
            return;
        }
        UUIDComponent uuidComp = store.getComponent(masterRef, UUIDComponent.getComponentType());
        UUID masterUuid = uuidComp != null ? uuidComp.getUuid() : null;
        if (masterUuid != null) {
            town.getInnPoolNpcIds().removeIf(s -> {
                try {
                    return masterUuid.equals(UUID.fromString(s.trim()));
                } catch (Exception e) {
                    return false;
                }
            });
        }
        if (masterUuid != null) {
            final UUID masterEntityUuid = masterUuid;
            final UUID townId = town.getTownId();
            world.execute(
                () -> applyGuildMasterEntityComponents(world, masterEntityUuid, townId, plotId)
            );
        }
        town.addInnVisitorPoolExcludedRoleId(AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID);
        if (masterUuid != null) {
            ResidentRegistryService.upsert(
                town,
                tm,
                AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID,
                TownVillagerBinding.KIND_GUILD_MASTER,
                plotId,
                masterUuid
            );
        }
        LOGGER.atInfo().log("Assigned guild master to guild hall plot %s; pathing to work POI", plotId);

        promoteBardIfInPool(world, plugin, town, plotId, tm, store, reg);
    }

    private static void promoteBardIfInPool(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PoiRegistry reg
    ) {
        if (!town.hasQuestActiveOrCompleted("q_bard")) return;
        Ref<EntityStore> bardRef = findBardRef(store, town);
        if (bardRef == null || !bardRef.isValid()) {
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null) {
            return;
        }
        BardWorkPoiResolver.BardPlacementTarget target =
            BardWorkPoiResolver.resolvePlacement(plugin, town, plot, reg);
        if (target == null) {
            LOGGER.atWarning().log("No bard work POI for guild hall plot %s", plotId);
            return;
        }
        UUIDComponent uuidComp = store.getComponent(bardRef, UUIDComponent.getComponentType());
        UUID bardUuid = uuidComp != null ? uuidComp.getUuid() : null;
        if (bardUuid != null) {
            town.getInnPoolNpcIds().removeIf(s -> {
                try {
                    return bardUuid.equals(UUID.fromString(s.trim()));
                } catch (Exception e) {
                    return false;
                }
            });
        }
        if (bardUuid != null) {
            final UUID bardEntityUuid = bardUuid;
            final UUID townId = town.getTownId();
            world.execute(
                () -> applyBardEntityComponents(world, bardEntityUuid, townId, plotId)
            );
        }
        town.addInnVisitorPoolExcludedRoleId(AetherhavenConstants.BARD_NPC_ROLE_ID);
        if (bardUuid != null) {
            ResidentRegistryService.upsert(
                town,
                tm,
                AetherhavenConstants.BARD_NPC_ROLE_ID,
                TownVillagerBinding.KIND_BARD,
                plotId,
                bardUuid
            );
        }
        tm.updateTown(town);
        LOGGER.atInfo().log("Assigned bard to guild hall plot %s; pathing to work POI", plotId);
    }

    private static void applyBardEntityComponents(
        @Nonnull World world,
        @Nonnull UUID bardEntityUuid,
        @Nonnull UUID townId,
        @Nonnull UUID plotId
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }
        Ref<EntityStore> bardRef = store.getExternalData().getRefFromUUID(bardEntityUuid);
        if (bardRef == null || !bardRef.isValid()) {
            return;
        }
        store.putComponent(
            bardRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(townId, TownVillagerBinding.KIND_BARD, plotId, plotId)
        );
        VillagerAutonomySystem.promptWorkplaceTravel(
            bardRef,
            store,
            VillagerAutonomySystem.resolveAutonomyNowMs(store)
        );
    }

    @Nullable
    private static Ref<EntityStore> findBardRef(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        for (String sid : town.getInnPoolNpcIds()) {
            Ref<EntityStore> ref = refForPoolEntry(store, sid, AetherhavenConstants.BARD_NPC_ROLE_ID);
            if (ref != null) {
                return ref;
            }
        }
        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            if (found.get() != null) {
                return;
            }
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !b.getTownId().equals(town.getTownId())) {
                return;
            }
            if (!TownVillagerBinding.KIND_BARD.equals(b.getKind())
                && !TownVillagerBinding.KIND_VISITOR_BARD.equals(b.getKind())) {
                return;
            }
            var npcType = NPCEntity.getComponentType();
            NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
            if (npc != null && AetherhavenConstants.BARD_NPC_ROLE_ID.equals(npc.getRoleName())) {
                found.set(ref);
            }
        });
        return found.get();
    }

    private static void applyGuildMasterEntityComponents(
        @Nonnull World world,
        @Nonnull UUID masterEntityUuid,
        @Nonnull UUID townId,
        @Nonnull UUID plotId
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) {
            return;
        }
        Ref<EntityStore> masterRef = store.getExternalData().getRefFromUUID(masterEntityUuid);
        if (masterRef == null || !masterRef.isValid()) {
            return;
        }
        store.putComponent(
            masterRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(townId, TownVillagerBinding.KIND_GUILD_MASTER, plotId, plotId)
        );
        VillagerAutonomySystem.promptWorkplaceTravel(
            masterRef,
            store,
            VillagerAutonomySystem.resolveAutonomyNowMs(store)
        );
    }

    @Nullable
    private static Ref<EntityStore> findGuildMasterRef(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        List<String> ids = town.getInnPoolNpcIds();
        for (String sid : ids) {
            Ref<EntityStore> ref = refForPoolEntry(store, sid, AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID);
            if (ref != null) {
                return ref;
            }
        }
        Ref<EntityStore> resident = findResidentGuildMasterRef(store, town);
        if (resident != null) {
            return resident;
        }
        UUID legacy = town.getGuildMasterEntityUuid();
        if (legacy != null) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(legacy);
            if (ref != null && ref.isValid()) {
                return ref;
            }
        }
        return null;
    }

    @Nullable
    private static Ref<EntityStore> refForPoolEntry(
        @Nonnull Store<EntityStore> store,
        @Nonnull String sid,
        @Nonnull String roleId
    ) {
        try {
            UUID u = UUID.fromString(sid.trim());
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref == null || !ref.isValid()) {
                return null;
            }
            var npcType = NPCEntity.getComponentType();
            NPCEntity npc = npcType != null ? store.getComponent(ref, npcType) : null;
            if (npc != null && roleId.equals(npc.getRoleName())) {
                return ref;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static Ref<EntityStore> findResidentGuildMasterRef(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        AtomicReference<Ref<EntityStore>> found = new AtomicReference<>();
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            if (found.get() != null) {
                return;
            }
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !b.getTownId().equals(town.getTownId())) {
                return;
            }
            if (!TownVillagerBinding.KIND_GUILD_MASTER.equals(b.getKind())
                && !TownVillagerBinding.KIND_VISITOR_GUILD_MASTER.equals(b.getKind())) {
                return;
            }
            var npcType = NPCEntity.getComponentType();
            NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
            if (npc != null && AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID.equals(npc.getRoleName())) {
                found.set(ref);
            }
        });
        return found.get();
    }
}
