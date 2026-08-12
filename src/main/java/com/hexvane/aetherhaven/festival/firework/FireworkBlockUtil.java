package com.hexvane.aetherhaven.festival.firework;

import com.hexvane.aetherhaven.entity.EntityChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Block probes for firework spawn clearance and mid-flight hits. */
public final class FireworkBlockUtil {
    private FireworkBlockUtil() {}

    public static boolean isSolidAt(@Nonnull World world, int x, int y, int z) {
        Vector3d probe = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
        if (!EntityChunkUtil.isPositionChunkInMemory(world, probe)) {
            return false;
        }
        BlockType type = world.getBlockType(x, y, z);
        if (type == null || type == BlockType.EMPTY) {
            return false;
        }
        return type.getMaterial() == BlockMaterial.Solid;
    }

    public static boolean isSolidAt(@Nonnull World world, double x, double y, double z) {
        return isSolidAt(world, floor(x), floor(y), floor(z));
    }

    /**
     * Prefer open air directly above the targeted block. If that column is blocked, try neighboring
     * columns at the same height so the rocket does not spawn inside the placement block.
     */
    @Nonnull
    public static Vector3d resolveSpawnPosition(@Nonnull World world, @Nonnull Vector3i targetBlock) {
        int baseY = targetBlock.y + 1;
        for (int up = 0; up < FireworkIds.SPAWN_AIR_SEARCH_UP; up++) {
            int y = baseY + up;
            if (!isSolidAt(world, targetBlock.x, y, targetBlock.z)) {
                return new Vector3d(
                    targetBlock.x + 0.5,
                    y + FireworkIds.SPAWN_ABOVE_TOP,
                    targetBlock.z + 0.5
                );
            }
        }
        int y = baseY;
        int[][] offsets = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        for (int[] o : offsets) {
            int x = targetBlock.x + o[0];
            int z = targetBlock.z + o[1];
            if (!isSolidAt(world, x, y, z)) {
                return new Vector3d(x + 0.5, y + FireworkIds.SPAWN_ABOVE_TOP, z + 0.5);
            }
        }
        // Last resort: sit clearly above the target block top even if the cell reads solid.
        return new Vector3d(
            targetBlock.x + 0.5,
            targetBlock.y + 1.0 + FireworkIds.SPAWN_ABOVE_TOP + FireworkIds.ROCKET_TIP_HEIGHT,
            targetBlock.z + 0.5
        );
    }

    /** True when the rocket body or tip has entered a solid block. */
    public static boolean hitsSolid(@Nonnull World world, @Nonnull Vector3d origin) {
        if (isSolidAt(world, origin.x, origin.y, origin.z)) {
            return true;
        }
        return isSolidAt(world, origin.x, origin.y + FireworkIds.ROCKET_TIP_HEIGHT, origin.z);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
