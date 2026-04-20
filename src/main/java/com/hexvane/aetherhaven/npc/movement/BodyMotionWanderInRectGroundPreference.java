package com.hexvane.aetherhaven.npc.movement;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.mountpoints.RotatedMountPointsArray;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.movement.BodyMotionWanderInRect;
import com.hypixel.hytale.server.npc.role.Role;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * After each successful wander probe, scales the candidate step length. Normal terrain uses {@link
 * #defaultGroundWeight}; bench / seats / beds, thin fences/windows, and {@link #isCounterObstacleBlock shop counters}
 * use a low {@link #obstacleWeight}. Optional per-block-type overrides remain for edge cases.
 */
public final class BodyMotionWanderInRectGroundPreference extends BodyMotionWanderInRect {
    private final double defaultGroundWeight;
    private final double obstacleWeight;
    private final Map<String, Double> groundWeightsByBlockTypeId;

    public BodyMotionWanderInRectGroundPreference(
        @Nonnull BuilderBodyMotionWanderInRectGroundPreference builder,
        @Nonnull BuilderSupport support
    ) {
        super(builder, support);
        this.defaultGroundWeight = builder.getDefaultGroundWeight();
        this.obstacleWeight = builder.getObstacleWeight();
        this.groundWeightsByBlockTypeId = builder.getGroundWeightsByBlockTypeId();
    }

    @Override
    protected boolean probeDirection(
        @Nonnull Ref<EntityStore> ref,
        int dirIndex,
        @Nonnull Role role,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor
    ) {
        boolean ok = super.probeDirection(ref, dirIndex, role, componentAccessor);
        if (ok) {
            double w = resolveGroundWeight(ref);
            this.walkDistances[dirIndex] *= w;
        }
        return ok;
    }

    private double resolveGroundWeight(@Nonnull Ref<EntityStore> ref) {
        World world = ref.getStore().getExternalData().getWorld();
        double x = this.targetPosition.x;
        double z = this.targetPosition.z;
        double yHint = this.targetPosition.y;
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int topY = Math.min(319, (int) Math.ceil(yHint) + 3);
        int standY = VillagerBlockUtil.findStandY(world, bx, bz, topY);
        if (standY == Integer.MIN_VALUE) {
            return clampWeight(1.0);
        }
        BlockType feet = world.getBlockType(bx, standY, bz);
        if (feet == null || feet == BlockType.EMPTY) {
            return clampWeight(1.0);
        }
        String blockId = feet.getId();
        Double override = blockId != null ? this.groundWeightsByBlockTypeId.get(blockId) : null;
        if (override != null) {
            return clampWeight(override);
        }
        if (blockId != null && isThinBarrierFenceOrWindowFooting(blockId)) {
            return clampWeight(this.obstacleWeight);
        }
        if (blockId != null && isCounterObstacleBlock(blockId)) {
            return clampWeight(this.obstacleWeight);
        }
        if (isBenchSeatOrBedSurface(feet)) {
            return clampWeight(this.obstacleWeight);
        }
        return clampWeight(this.defaultGroundWeight);
    }

    /**
     * Thin colliders (fences, windows) often read as walkable ground; treat like obstacles so wander probes do not end
     * on them. Fence gates are excluded so paths can still favor valid gate cells.
     */
    static boolean isThinBarrierFenceOrWindowFooting(@Nonnull String blockTypeId) {
        String u = blockTypeId.toUpperCase(Locale.ROOT);
        if (u.contains("WINDOW")) {
            return true;
        }
        return u.contains("FENCE") && !u.contains("GATE");
    }

    /** Shop counters and similar: walkable tops but NPCs should not idle-wander standing on them. */
    static boolean isCounterObstacleBlock(@Nonnull String blockTypeId) {
        return "Furniture_Village_Counter".equals(blockTypeId);
    }

    /**
     * Blocks that are clearly “furniture” to stand on: crafting benches, seats, beds. Thin decor / tables without
     * these markers may still be chosen; add {@code GroundWeights} overrides for those ids if needed.
     */
    static boolean isBenchSeatOrBedSurface(@Nonnull BlockType feet) {
        if (feet.getBench() != null) {
            return true;
        }
        RotatedMountPointsArray seats = feet.getSeats();
        if (seats != null && seats.size() > 0) {
            return true;
        }
        RotatedMountPointsArray beds = feet.getBeds();
        return beds != null && beds.size() > 0;
    }

    private static double clampWeight(double w) {
        if (Double.isNaN(w)) {
            return 1.0;
        }
        if (w < 0.05) {
            return 0.05;
        }
        return Math.min(w, 10.0);
    }
}
