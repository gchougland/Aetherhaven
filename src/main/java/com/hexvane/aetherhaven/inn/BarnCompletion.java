package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** When the barn prefab completes, move Thalen from the inn pool to the WORK POI. Quest completes on dialogue. */
public final class BarnCompletion {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private BarnCompletion() {}

    public static void onBarnBuilt(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull TownManager tm
    ) {
        if (!town.hasQuestActiveOrCompleted(AetherhavenConstants.QUEST_BARN)) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PoiEntry work = null;
        for (PoiEntry e : reg.listByTown(town.getTownId())) {
            if (plotId.equals(e.getPlotId()) && e.getTags().contains("WORK")) {
                work = e;
                break;
            }
        }
        if (work == null) {
            LOGGER.atWarning().log("No WORK POI for barn plot %s", plotId);
            return;
        }

        double tx = work.getX() + 0.5;
        double ty = work.getY();
        double tz = work.getZ() + 0.5;

        Ref<EntityStore> npcRef = findRancherRef(store, town);
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d p = tc.getPosition();
        p.x = tx;
        p.y = ty + 0.02;
        p.z = tz;
        store.putComponent(npcRef, TransformComponent.getComponentType(), tc);
        UUIDComponent uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
        UUID entityUuid = uuidComp != null ? uuidComp.getUuid() : null;
        if (entityUuid != null) {
            town.getInnPoolNpcIds().removeIf(s -> {
                try {
                    return entityUuid.equals(UUID.fromString(s.trim()));
                } catch (Exception e) {
                    return false;
                }
            });
        }
        store.putComponent(
            npcRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_RANCHER, plotId, plotId)
        );
        town.addInnVisitorPoolExcludedRoleId(AetherhavenConstants.NPC_RANCHER);
        if (uuidComp != null) {
            ResidentRegistryService.upsert(
                town,
                tm,
                AetherhavenConstants.NPC_RANCHER,
                TownVillagerBinding.KIND_RANCHER,
                plotId,
                uuidComp.getUuid()
            );
        }
        tm.updateTown(town);
        LOGGER.atInfo().log("Moved rancher to barn at %s,%s,%s", work.getX(), work.getY(), work.getZ());
    }

    @Nullable
    private static Ref<EntityStore> findRancherRef(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        List<String> ids = town.getInnPoolNpcIds();
        for (String sid : ids) {
            try {
                UUID u = UUID.fromString(sid.trim());
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                var npcType = NPCEntity.getComponentType();
                NPCEntity npc = npcType != null ? store.getComponent(ref, npcType) : null;
                if (npc != null && AetherhavenConstants.NPC_RANCHER.equals(npc.getRoleName())) {
                    return ref;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
