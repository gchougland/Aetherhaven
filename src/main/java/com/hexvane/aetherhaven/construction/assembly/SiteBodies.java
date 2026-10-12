package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The bodies a building site must never place a block into: players, and this mod's villagers and townsfolk (an NPC
 * with a {@link TownVillagerBinding}). Other creatures are not looked at: a site cannot ask a wild animal to move,
 * and must not wait on one for ever.
 *
 * <p>A body is the entity's own {@link BoundingBox} from the engine at its position, not an assumed one block by two.
 * The townsfolk in this mod range from 0.6 wide and 1.5 tall to a full block wide and 2 tall.
 */
public final class SiteBodies {
    private SiteBodies() {}

    /**
     * Room kept beside a body and above its head. None is kept under the feet: the block someone stands on is not in
     * their way, and a floor piece must be allowed under a villager standing on the ground beside it.
     */
    public static final double MARGIN = 0.05;
    /** Boxes that only touch, or share less than this, do not overlap. */
    static final double TOUCH = 1.0e-4;
    /** Used only for an entity with no bounding box component: the player model's box. */
    static final Box FALLBACK = new Box(-0.325, 0, -0.325, 0.325, 1.85, 0.325);

    /** One body in world space, margin included. {@code ref} is null only in tests. */
    public record Body(@Nullable Ref<EntityStore> ref, boolean player,
                       double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        /** The body of an entity whose engine bounding box is {@code local}, with its feet at {@code position}. */
        @Nonnull
        public static Body at(@Nullable Ref<EntityStore> ref, boolean player, @Nonnull Vector3dc position, @Nonnull Box local) {
            return new Body(ref, player,
                position.x() + local.min.x - MARGIN, position.y() + local.min.y, position.z() + local.min.z - MARGIN,
                position.x() + local.max.x + MARGIN, position.y() + local.max.y + MARGIN, position.z() + local.max.z + MARGIN);
        }

        public boolean overlaps(double bMinX, double bMinY, double bMinZ, double bMaxX, double bMaxY, double bMaxZ) {
            return maxX - TOUCH > bMinX && minX + TOUCH < bMaxX
                && maxY - TOUCH > bMinY && minY + TOUCH < bMaxY
                && maxZ - TOUCH > bMinZ && minZ + TOUCH < bMaxZ;
        }

        /** Whether this body is in the space a block would fill. {@code block} is relative to the block's own cell. */
        public boolean overlapsBlock(int x, int y, int z, @Nonnull Box block) {
            return overlaps(x + block.min.x, y + block.min.y, z + block.min.z, x + block.max.x, y + block.max.y, z + block.max.z);
        }
    }

    /** The first body in the space a block would fill at this cell, or null when the space is free. */
    @Nullable
    public static Body blocker(@Nonnull List<Body> bodies, int x, int y, int z, @Nonnull Box block) {
        for (int i = 0; i < bodies.size(); i++) {
            if (bodies.get(i).overlapsBlock(x, y, z, block)) {
                return bodies.get(i);
            }
        }
        return null;
    }

    /**
     * The space a block fills once placed, relative to its cell, or null when nobody can be buried in it: air, and
     * blocks one can walk through such as plants, rugs and torches. A block larger than its cell (a tall door, a wide
     * window) gives its whole box.
     */
    @Nullable
    public static Box solidBox(@Nullable BlockType type, int rotationIndex) {
        if (type == null || type == BlockType.EMPTY || type.getMaterial() != BlockMaterial.Solid) {
            return null;
        }
        BlockBoundingBoxes boxes = BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        return boxes == null ? Box.UNIT : boxes.get(rotationIndex).getBoundingBox();
    }

    /**
     * The space one piece of a site writes a block into when it is placed by itself, or null when it writes none that
     * can bury anyone. The far cells of a large block carry no block of their own: the origin piece gives the whole box.
     */
    @Nullable
    public static Box pieceBox(@Nonnull PendingBlock piece, @Nonnull BlockTypeAssetMap<String, BlockType> types) {
        if (piece.blockId() == 0 || piece.filler() != FillerBlockUtil.NO_FILLER) {
            return null;
        }
        return solidBox(types.getAsset(piece.blockId()), piece.blockRotation());
    }

    /**
     * Players and this mod's villagers within {@code radius} of a point, where they are right now. One look serves a
     * whole step of a site. Safe inside a tick: it only reads.
     */
    @Nonnull
    public static List<Body> near(@Nonnull ComponentAccessor<EntityStore> accessor, double x, double y, double z, double radius) {
        List<Body> out = new ArrayList<>();
        Vector3d centre = new Vector3d(x, y, z);
        // Own lists: the spatial structures hand out shared ones.
        List<Ref<EntityStore>> found = new ArrayList<>();
        accessor.getResource(EntityModule.get().getPlayerSpatialResourceType()).getSpatialStructure().collect(centre, radius, found);
        add(accessor, found, true, out);
        found.clear();
        accessor.getResource(NPCPlugin.get().getNpcSpatialResource()).getSpatialStructure().collect(centre, radius, found);
        add(accessor, found, false, out);
        return out;
    }

    private static void add(ComponentAccessor<EntityStore> accessor, List<Ref<EntityStore>> found, boolean player, List<Body> out) {
        for (int i = 0; i < found.size(); i++) {
            Ref<EntityStore> ref = found.get(i);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            // Only this mod's own villagers and townsfolk count among the NPCs.
            if (!player && accessor.getComponent(ref, TownVillagerBinding.getComponentType()) == null) {
                continue;
            }
            Body body = of(accessor, ref, player);
            if (body != null) {
                out.add(body);
            }
        }
    }

    /** The body of one entity as it stands now, or null when it has no position. */
    @Nullable
    public static Body of(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Ref<EntityStore> ref, boolean player) {
        TransformComponent transform = accessor.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }
        return Body.at(ref, player, transform.getPosition(), localBox(accessor, ref));
    }

    /** The engine's bounding box of an entity, relative to its feet. */
    @Nonnull
    public static Box localBox(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Ref<EntityStore> ref) {
        BoundingBox bounds = accessor.getComponent(ref, BoundingBox.getComponentType());
        Box local = bounds == null ? null : bounds.getBoundingBox();
        return local == null ? FALLBACK : local;
    }
}
