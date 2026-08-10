package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Spawns / removes transient plot creator important-spot villager previews on the world thread. */
public final class PlotCreatorSpotPreviewSpawner {
    private PlotCreatorSpotPreviewSpawner() {}

    @Nullable
    public static UUID spawnPreview(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired
    ) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            return null;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        float yaw = desired.facingYawWorldRadians() != null ? desired.facingYawWorldRadians() : 0.0F;
        Vector3d feet =
            VillagerBlockUtil.snapNpcFeetToStand(
                world,
                new Vector3d(desired.standX() + 0.5, desired.standY(), desired.standZ() + 0.5)
            );
        var pair =
            npcPlugin.spawnNPC(store, desired.npcRoleId(), null, feet, new Rotation3f(0.0F, yaw, 0.0F));
        if (pair == null) {
            if (!AetherhavenConstants.NPC_TOWNSFOLK.equals(desired.npcRoleId())) {
                pair =
                    npcPlugin.spawnNPC(
                        store,
                        AetherhavenConstants.NPC_TOWNSFOLK,
                        null,
                        feet,
                        new Rotation3f(0.0F, yaw, 0.0F)
                    );
            }
            if (pair == null) {
                return null;
            }
        }
        Ref<EntityStore> ref = pair.first();
        store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        store.putComponent(ref, Intangible.getComponentType(), Intangible.INSTANCE);
        store.putComponent(
            ref,
            EntityStore.REGISTRY.getNonSerializedComponentType(),
            NonSerialized.get()
        );
        store.putComponent(
            ref,
            PlotCreatorSpotPreview.getComponentType(),
            new PlotCreatorSpotPreview(ownerPlayerEntityUuid, desired.key())
        );
        NpcSpawnOriginUtil.attach(store, ref, "PLOT_CREATOR_SPOT_PREVIEW", "spot=" + desired.type().name(), world, feet);
        // Detach from ambient spawn-marker despawn and freeze motion before the first pose tick.
        PlotCreatorSpotPreviewSanitize.applyOnSpawn(ref, store);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    public static void removePreviewByUuid(
        @Nonnull World world,
        @Nullable UUID previewUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        if (previewUuid == null) {
            return;
        }
        Ref<EntityStore> ref = world.getEntityRef(previewUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        if (commandBuffer != null) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        } else {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    public static void removeAllForOwner(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        ArrayList<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(
            Query.and(PlotCreatorSpotPreview.getComponentType()),
            (chunk, chunkCommandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    PlotCreatorSpotPreview preview = chunk.getComponent(i, PlotCreatorSpotPreview.getComponentType());
                    if (preview == null || !ownerPlayerEntityUuid.equals(preview.getOwnerPlayerUuid())) {
                        continue;
                    }
                    Ref<EntityStore> previewRef = chunk.getReferenceTo(i);
                    if (previewRef.isValid()) {
                        toRemove.add(previewRef);
                    }
                }
            }
        );
        removePreviewRefs(toRemove, commandBuffer, store);
    }

    public static void purgeAllInWorld(@Nonnull World world) {
        if (!world.isAlive()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        ArrayList<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(
            Query.and(PlotCreatorSpotPreview.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> previewRef = chunk.getReferenceTo(i);
                    if (previewRef.isValid()) {
                        toRemove.add(previewRef);
                    }
                }
            }
        );
        removePreviewRefs(toRemove, null, store);
    }

    private static void removePreviewRefs(
        @Nonnull Iterable<Ref<EntityStore>> refs,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store
    ) {
        Set<Ref<EntityStore>> seen = new HashSet<>();
        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !ref.isValid() || !seen.add(ref)) {
                continue;
            }
            if (commandBuffer != null) {
                commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            } else {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
    }
}
