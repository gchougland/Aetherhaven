package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
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

/** When a shop prefab completes, assign the matching inn visitor to the shop plot. Quest completes on dialogue. */
public final class InnVisitorShopCompletion {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private InnVisitorShopCompletion() {}

    public static void onShopBuilt(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID shopPlotId,
        @Nonnull TownManager tm,
        @Nonnull ShopPromotionConfig config
    ) {
        if (!town.hasQuestActiveOrCompleted(config.shopQuestId())) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();

        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PoiEntry work = null;
        for (PoiEntry e : reg.listByTown(town.getTownId())) {
            if (shopPlotId.equals(e.getPlotId()) && e.getTags().contains("WORK")) {
                work = e;
                break;
            }
        }
        if (work == null) {
            LOGGER.atWarning().log("No WORK POI for %s shop plot %s", config.logLabel(), shopPlotId);
            return;
        }

        Ref<EntityStore> npcRef = findInnPoolNpcRef(store, town, config.npcRoleId());
        if (npcRef == null || !npcRef.isValid()) {
            npcRef = findTownVisitorNpcRef(store, town, config.npcRoleId());
        }
        if (npcRef == null || !npcRef.isValid()) {
            LOGGER.atFine().log(
                "Shop promotion deferred for %s (quest ok, plot %s): visitor not in inn pool yet",
                config.logLabel(),
                shopPlotId
            );
            return;
        }
        UUIDComponent uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
        UUID npcUuid = uuidComp != null ? uuidComp.getUuid() : null;
        if (npcUuid != null) {
            town.getInnPoolNpcIds().removeIf(s -> {
                try {
                    return npcUuid.equals(UUID.fromString(s.trim()));
                } catch (Exception e) {
                    return false;
                }
            });
        }
        store.putComponent(
            npcRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), config.residentKind(), shopPlotId, shopPlotId)
        );
        VillagerAutonomySystem.promptWorkplaceTravel(
            npcRef,
            store,
            VillagerAutonomySystem.resolveAutonomyNowMs(store)
        );
        town.addInnVisitorPoolExcludedRoleId(config.npcRoleId());
        if (uuidComp != null) {
            ResidentRegistryService.upsert(
                town,
                tm,
                config.npcRoleId(),
                config.residentKind(),
                shopPlotId,
                uuidComp.getUuid()
            );
        }
        tm.updateTown(town);
        LOGGER.atInfo().log("Assigned %s to shop plot %s; pathing to work POI", config.logLabel(), shopPlotId);
    }

    @Nullable
    private static Ref<EntityStore> findInnPoolNpcRef(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String npcRoleId
    ) {
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
                if (npc != null && npcRoleId.equals(npc.getRoleName())) {
                    return ref;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Fallback when the NPC left the inn pool list but is still loaded as a town visitor. */
    @Nullable
    private static Ref<EntityStore> findTownVisitorNpcRef(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String npcRoleId
    ) {
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
            if (b == null || !b.getTownId().equals(town.getTownId()) || !TownVillagerBinding.isVisitorKind(b.getKind())) {
                return;
            }
            var npcType = NPCEntity.getComponentType();
            NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
            if (npc != null && npcRoleId.equals(npc.getRoleName())) {
                found.set(ref);
            }
        });
        return found.get();
    }
}
