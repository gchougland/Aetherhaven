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
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** When the market stall prefab completes, move Vex to the stall WORK POI. Quest completes when the player talks to Vex. */
public final class MerchantStallCompletion {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private MerchantStallCompletion() {}

    public static void onStallBuilt(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID stallPlotId,
        @Nonnull TownManager tm
    ) {
        if (!town.hasQuestActiveOrCompleted(AetherhavenConstants.QUEST_MERCHANT_STALL)) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PoiEntry work = null;
        for (PoiEntry e : reg.listByTown(town.getTownId())) {
            if (stallPlotId.equals(e.getPlotId()) && e.getTags().contains("WORK")) {
                work = e;
                break;
            }
        }
        if (work == null) {
            LOGGER.atWarning().log("No WORK POI for stall plot %s", stallPlotId);
            return;
        }

        double tx = work.getX() + 0.5;
        double ty = work.getY();
        double tz = work.getZ() + 0.5;

        Ref<EntityStore> merchantRef = findMerchantRef(store, town);
        if (merchantRef == null || !merchantRef.isValid()) {
            return;
        }
        TransformComponent tc = store.getComponent(merchantRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d p = tc.getPosition();
        p.x = tx;
        p.y = ty + 0.02;
        p.z = tz;
        store.putComponent(merchantRef, TransformComponent.getComponentType(), tc);
        UUIDComponent uuidComp = store.getComponent(merchantRef, UUIDComponent.getComponentType());
        UUID merchantUuid = uuidComp != null ? uuidComp.getUuid() : null;
        if (merchantUuid != null) {
            town.getInnPoolNpcIds().removeIf(s -> {
                try {
                    return merchantUuid.equals(UUID.fromString(s.trim()));
                } catch (Exception e) {
                    return false;
                }
            });
        }
        store.putComponent(
            merchantRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_MERCHANT, stallPlotId, stallPlotId)
        );
        town.addInnVisitorPoolExcludedRoleId(AetherhavenConstants.NPC_MERCHANT);
        if (uuidComp != null) {
            ResidentRegistryService.upsert(
                town,
                tm,
                AetherhavenConstants.NPC_MERCHANT,
                TownVillagerBinding.KIND_MERCHANT,
                stallPlotId,
                uuidComp.getUuid()
            );
        }
        tm.updateTown(town);
        LOGGER.atInfo().log("Moved merchant to stall at %s,%s,%s", work.getX(), work.getY(), work.getZ());
    }

    @Nullable
    private static Ref<EntityStore> findMerchantRef(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
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
                if (npc != null && AetherhavenConstants.NPC_MERCHANT.equals(npc.getRoleName())) {
                    return ref;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
