package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.autonomy.PoiAutonomyVisuals;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.autonomy.VillagerWorkActivity;
import com.hexvane.aetherhaven.autonomy.VillagerWorkVisuals;
import com.hexvane.aetherhaven.entity.EntityRotationUtil;
import com.hexvane.aetherhaven.entity.TransformComponentUtil;
import com.hexvane.aetherhaven.equipment.VillagerEquipmentService;
import com.hexvane.aetherhaven.guild.GuildHallAdventurerChairMount;
import com.hexvane.aetherhaven.guild.GuildHallDisplayAnchor;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Applies stand/sit/equip visuals to plot-creator spot preview NPCs. */
public final class PlotCreatorSpotPreviewPose {
    private static final double HOLD_SNAP_DISTANCE_SQ = 0.35 * 0.35;

    private PlotCreatorSpotPreviewPose() {}

    public static void applyInitialPose(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired
    ) {
        World world = store.getExternalData().getWorld();
        float yaw = desired.facingYawWorldRadians() != null ? desired.facingYawWorldRadians() : 0.0F;
        Vector3d feet =
            VillagerBlockUtil.snapNpcFeetToStand(
                world,
                new Vector3d(desired.standX() + 0.5, desired.standY(), desired.standZ() + 0.5)
            );
        TransformComponentUtil.replacePreservingChunk(
            npcRef,
            store,
            commandBuffer,
            feet,
            new Rotation3f(0.0F, yaw, 0.0F)
        );
        // Frozen + cleared Movement only. Do not force GuildHallDisplay / Autonomy states most roles lack.
        PlotCreatorSpotPreviewSanitize.applyEachTick(npcRef, store, commandBuffer);
        PlotCreatorSpotPreviewSanitize.clearMovementAnim(npcRef, store, commandBuffer);

        if (desired.poiBlockX() != null && desired.poiBlockY() != null && desired.poiBlockZ() != null) {
            PoiEntry poi = toPoiEntry(desired, feet, yaw);
            PoiAutonomyVisuals.beginPoiUse(npcRef, store, commandBuffer, poi);
            applyPreviewEquipment(npcRef, store, commandBuffer, desired);
            if (!ensureSitting(npcRef, store, commandBuffer, desired, poi, yaw)) {
                // beginPoiUse already faced the block when not seated.
            }
            PlotCreatorSpotPreviewSanitize.clearMovementAnim(npcRef, store, commandBuffer);
            // Honor the authored activity tag (e.g. read) instead of role defaults like blacksmith smithing.
            tickWorkBeat(npcRef, store, commandBuffer, desired, poi, System.currentTimeMillis(), 0L);
            return;
        }

        if (desired.adventurerSeat()) {
            GuildHallDisplayAnchor anchor = new GuildHallDisplayAnchor(feet, yaw);
            anchor.setSpawnMarkerPosition(new Vector3d(desired.standX() + 0.5, desired.standY(), desired.standZ() + 0.5));
            if (GuildHallAdventurerChairMount.hasSeatNearSpawn(store, anchor)) {
                GuildHallAdventurerChairMount.tryMountChairBelowSpawn(npcRef, store, commandBuffer, anchor);
                if (!GuildHallAdventurerChairMount.isBlockMounted(store, commandBuffer, npcRef)) {
                    GuildHallAdventurerChairMount.applySeatPoseFallback(npcRef, store, commandBuffer, anchor);
                }
                GuildHallAdventurerChairMount.ensureSitVisuals(npcRef, store, anchor);
                PlotCreatorSpotPreviewSanitize.clearMovementAnim(npcRef, store, commandBuffer);
                return;
            }
        }

        faceYaw(npcRef, store, commandBuffer, yaw);
        PlotCreatorSpotPreviewSanitize.clearMovementAnim(npcRef, store, commandBuffer);
    }

    /**
     * Keep the preview from wandering without re-entering NPC states every tick (that resets Sit / work anims).
     */
    public static void holdPosition(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired
    ) {
        // Keep ambient spawn-marker despawn off every tick (same idea as town villager sanitize).
        PlotCreatorSpotPreviewSanitize.applyEachTick(npcRef, store, commandBuffer);
        // Idle/wander can keep a walk Movement clip playing while Frozen (looks like running in place).
        PlotCreatorSpotPreviewSanitize.clearMovementAnim(npcRef, store, commandBuffer);
        if (isBlockMounted(store, commandBuffer, npcRef)) {
            return;
        }
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3d home =
            VillagerBlockUtil.snapNpcFeetToStand(
                world,
                new Vector3d(desired.standX() + 0.5, desired.standY(), desired.standZ() + 0.5)
            );
        Vector3d pos = tc.getPosition();
        double dx = pos.x - home.x;
        double dy = pos.y - home.y;
        double dz = pos.z - home.z;
        if (dx * dx + dy * dy + dz * dz <= HOLD_SNAP_DISTANCE_SQ) {
            return;
        }
        float yaw = desired.facingYawWorldRadians() != null ? desired.facingYawWorldRadians() : tc.getRotation().yaw();
        TransformComponentUtil.replacePreservingChunk(
            npcRef,
            store,
            commandBuffer,
            home,
            new Rotation3f(0.0F, yaw, 0.0F)
        );
    }

    public static boolean tickWorkBeat(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired,
        @Nullable PoiEntry poiOrNull,
        long nowMs,
        long lastHitEpochMs
    ) {
        if (desired.poiBlockX() == null) {
            return false;
        }
        // Fun / sleep spots should stay seated, not fidget-emote over Sit.
        if (desired.type() == PlotCreatorSubstepType.FUN_POI
            || desired.type() == PlotCreatorSubstepType.SLEEP_POI
            || desired.type() == PlotCreatorSubstepType.EAT_POI) {
            return false;
        }
        PoiEntry poi = poiOrNull != null ? poiOrNull : toPoiEntry(desired, null, desired.facingYawWorldRadians());
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return false;
        }
        // Null binding so role overrides (blacksmith always smith) do not ignore authored workActivity tags.
        return VillagerWorkVisuals.tickHit(
            npcRef,
            store,
            commandBuffer,
            npc,
            poi,
            null,
            nowMs,
            lastHitEpochMs
        );
    }

    @Nonnull
    public static PoiEntry toPoiEntry(
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired,
        @Nullable Vector3d standFeet,
        @Nullable Float yawRadians
    ) {
        int poiX = desired.poiBlockX() != null ? desired.poiBlockX() : desired.standX();
        int poiY = desired.poiBlockY() != null ? desired.poiBlockY() : desired.standY();
        int poiZ = desired.poiBlockZ() != null ? desired.poiBlockZ() : desired.standZ();
        Double tx = standFeet != null ? standFeet.x : desired.standX() + 0.5;
        Double ty = standFeet != null ? standFeet.y : (double) desired.standY();
        Double tz = standFeet != null ? standFeet.z : desired.standZ() + 0.5;
        Float yaw = yawRadians != null ? yawRadians : desired.facingYawWorldRadians();
        return new PoiEntry(
            UUID.nameUUIDFromBytes(("plot-creator-preview-" + desired.key()).getBytes()),
            UUID.nameUUIDFromBytes("plot-creator-preview-town".getBytes()),
            poiX,
            poiY,
            poiZ,
            new HashSet<>(desired.tags()),
            1,
            null,
            desired.blockTypeId(),
            PoiInteractionKind.fromJson(desired.interactionKind()),
            desired.mountOnUse(),
            desired.equipmentProfileId(),
            tx,
            ty,
            tz,
            yaw,
            desired.workResidentKind()
        );
    }

    private static void applyPreviewEquipment(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired
    ) {
        if (isLeisureActivity(desired)) {
            // beginPoiUse may have applied the role work tool when no equipmentProfileId is set.
            VillagerEquipmentService.clearHotbar(npcRef, store, commandBuffer);
        }
    }

    private static boolean isLeisureActivity(@Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired) {
        String activityId = desired.activityId();
        if (activityId != null) {
            String id = activityId.trim().toLowerCase(Locale.ROOT);
            if ("read".equals(id) || "craft".equals(id) || "leisure".equals(id) || "fun".equals(id)) {
                return true;
            }
        }
        for (String tag : desired.tags()) {
            if (tag == null) {
                continue;
            }
            String t = tag.trim().toLowerCase(Locale.ROOT);
            if (t.equals(VillagerWorkActivity.TAG_PREFIX + "read")
                || t.equals(VillagerWorkActivity.TAG_PREFIX + "craft")
                || t.equals(VillagerWorkActivity.TAG_PREFIX + "leisure")
                || t.equals(VillagerWorkActivity.TAG_PREFIX + "fun")) {
                return true;
            }
        }
        return desired.type() == PlotCreatorSubstepType.FUN_POI
            || desired.type() == PlotCreatorSubstepType.SLEEP_POI
            || desired.type() == PlotCreatorSubstepType.EAT_POI
            || desired.type() == PlotCreatorSubstepType.SHOP_POI
            || desired.type() == PlotCreatorSubstepType.TOURIST_VISIT_POI;
    }

    /**
     * @return true when the preview is seated afterward
     */
    private static boolean ensureSitting(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired,
        @Nonnull PoiEntry poi,
        float yaw
    ) {
        if (!wantsSit(desired, poi)) {
            return isBlockMounted(store, commandBuffer, npcRef);
        }
        if (isBlockMounted(store, commandBuffer, npcRef)) {
            return true;
        }
        World world = store.getExternalData().getWorld();
        Vector3i seatBlock = resolveSeatBlock(world, desired);
        if (seatBlock != null) {
            GuildHallDisplayAnchor anchor =
                new GuildHallDisplayAnchor(
                    new Vector3d(desired.standX() + 0.5, desired.standY(), desired.standZ() + 0.5),
                    yaw
                );
            // Mount helper looks for a seat under the marker; place marker above the seat cell.
            anchor.setSpawnMarkerPosition(
                new Vector3d(seatBlock.x + 0.5, seatBlock.y + 1.0, seatBlock.z + 0.5)
            );
            // First call aligns feet; second call mounts.
            GuildHallAdventurerChairMount.tryMountChairBelowSpawn(npcRef, store, commandBuffer, anchor);
            if (GuildHallAdventurerChairMount.tryMountChairBelowSpawn(npcRef, store, commandBuffer, anchor)) {
                GuildHallAdventurerChairMount.ensureSitVisuals(npcRef, store, anchor);
                return true;
            }
            if (GuildHallAdventurerChairMount.isBlockMounted(store, commandBuffer, npcRef)) {
                GuildHallAdventurerChairMount.ensureSitVisuals(npcRef, store, anchor);
                return true;
            }
            GuildHallAdventurerChairMount.applySeatPoseFallback(npcRef, store, commandBuffer, anchor);
            Vector3d seatPos = VillagerBlockUtil.seatWorldPosition(world, seatBlock);
            if (seatPos != null) {
                TransformComponentUtil.replacePreservingChunk(
                    npcRef,
                    store,
                    commandBuffer,
                    seatPos,
                    new Rotation3f(0.0F, yaw, 0.0F)
                );
            }
        }
        playSitVisual(npcRef, store, commandBuffer);
        return true;
    }

    private static boolean wantsSit(
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired,
        @Nonnull PoiEntry poi
    ) {
        if (desired.type() == PlotCreatorSubstepType.FUN_POI
            || desired.type() == PlotCreatorSubstepType.TOURIST_VISIT_POI) {
            return true;
        }
        if (poi.getInteractionKind() == PoiInteractionKind.SIT) {
            return true;
        }
        return desired.tags().stream().anyMatch(t -> t != null && "SIT".equalsIgnoreCase(t.trim()));
    }

    @Nullable
    private static Vector3i resolveSeatBlock(
        @Nonnull World world,
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired
    ) {
        if (desired.poiBlockX() != null && desired.poiBlockY() != null && desired.poiBlockZ() != null) {
            Vector3i base =
                VillagerBlockUtil.resolveMountBaseBlock(
                    world,
                    desired.poiBlockX(),
                    desired.poiBlockY(),
                    desired.poiBlockZ()
                );
            if (VillagerBlockUtil.isBlockMountSeat(world, base.x, base.y, base.z)) {
                return base;
            }
            for (int dy = 0; dy <= 2; dy++) {
                int y = desired.poiBlockY() - dy;
                if (y < 0) {
                    break;
                }
                Vector3i candidate =
                    VillagerBlockUtil.resolveMountBaseBlock(world, desired.poiBlockX(), y, desired.poiBlockZ());
                if (VillagerBlockUtil.isBlockMountSeat(world, candidate.x, candidate.y, candidate.z)) {
                    return candidate;
                }
            }
        }
        return VillagerBlockUtil.findGuildHallSeatBelowSpawn(
            world,
            new Vector3d(desired.standX() + 0.5, desired.standY() + 0.5, desired.standZ() + 0.5)
        );
    }

    private static void playSitVisual(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Status, "Sit", commandBuffer);
        NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Movement, null, commandBuffer);
    }

    private static boolean isBlockMounted(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> npcRef
    ) {
        MountedComponent mounted =
            commandBuffer != null ? commandBuffer.getComponent(npcRef, MountedComponent.getComponentType()) : null;
        if (mounted == null) {
            mounted = store.getComponent(npcRef, MountedComponent.getComponentType());
        }
        return mounted != null && mounted.getControllerType() == MountController.BlockMount;
    }

    private static void faceYaw(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        float yaw
    ) {
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Rotation3f rot = new Rotation3f(tc.getRotation());
        EntityRotationUtil.setBodyYaw(rot, yaw);
        TransformComponentUtil.replacePreservingChunk(npcRef, store, commandBuffer, tc.getPosition(), rot);
    }

}
