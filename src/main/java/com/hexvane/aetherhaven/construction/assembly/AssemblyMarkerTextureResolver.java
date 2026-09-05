package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockTypeTextures;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves block type keys and texture paths for building staff placement preview markers. */
public final class AssemblyMarkerTextureResolver {
    /** Entity {@link com.hypixel.hytale.server.core.asset.type.model.config.Model} textures must use these roots. */
    private static final String ENTITY_TEXTURE_FALLBACK = "Items/Aetherhaven/Building_Marker/Building_Marker.png";

    /** Fallback when a pending block id cannot be resolved to a BlockType key. */
    private static final String FALLBACK_BLOCK_TYPE_KEY = "Rock_Stone";

    @Nullable
    private static volatile String cachedFallbackTexture;

    private AssemblyMarkerTextureResolver() {}

    /**
     * @return pending prefab block index for a placing frontier cell, or {@code Integer.MIN_VALUE}.
     */
    public static int resolvePlacingBlockId(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3i cellWorld
    ) {
        PlotAssemblyJob job = PlotAssemblyService.findJobContainingPreview(world, plugin, cellWorld);
        if (job == null || AssemblyWorldRegistry.phase(world, job.plotId()) != PlotAssemblyPhase.PLACING) {
            return Integer.MIN_VALUE;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownOwningPlot(job.plotId());
        if (town == null) {
            return Integer.MIN_VALUE;
        }
        PlotInstance plot = town.findPlotById(job.plotId());
        if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
            return Integer.MIN_VALUE;
        }
        int idx = PlotAssemblyService.resolveFrontierPlacementIndex(world, job, plot, cellWorld);
        if (idx < 0) {
            return Integer.MIN_VALUE;
        }
        java.util.List<PendingBlock> pending = job.pendingBlocks();
        if (idx >= pending.size()) {
            return Integer.MIN_VALUE;
        }
        return pending.get(idx).blockId();
    }

    /** Block type asset key for a placing {@link com.hypixel.hytale.server.core.entity.entities.BlockEntity} preview. */
    @Nonnull
    public static String blockTypeKeyForPlacingBlockId(int blockId) {
        if (blockId == BlockType.EMPTY_ID || blockId == Integer.MIN_VALUE) {
            return FALLBACK_BLOCK_TYPE_KEY;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            return FALLBACK_BLOCK_TYPE_KEY;
        }
        String id = blockType.getId();
        if (id == null || id.isBlank()) {
            return FALLBACK_BLOCK_TYPE_KEY;
        }
        return id.trim();
    }

    @Nonnull
    public static String textureForPlacingBlockId(int blockId) {
        return entitySafeTexture(texturePathForPlacingBlockId(blockId));
    }

    /** Raw block texture path (may be {@code BlockTextures/} — not valid on entity models). */
    @Nonnull
    public static String texturePathForPlacingBlockId(int blockId) {
        if (blockId == BlockType.EMPTY_ID || blockId == Integer.MIN_VALUE) {
            return fallbackPlankTexture();
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            return fallbackPlankTexture();
        }
        if (useFurnitureFallbackTexture(blockType)) {
            return fallbackPlankTexture();
        }
        String fromBlock = firstTexturePath(blockType);
        if (fromBlock != null && !fromBlock.isBlank()) {
            return fromBlock.trim();
        }
        return fallbackPlankTexture();
    }

    /**
     * Model entities only render textures under {@code Items/}, {@code Characters/}, {@code NPC/}, or {@code VFX/}.
     * {@code BlockTextures/} paths work for blocks but not runtime entity model overrides.
     */
    @Nonnull
    public static String entitySafeTexture(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return fallbackPlankTexture();
        }
        String p = path.trim();
        if (p.startsWith("Items/")
            || p.startsWith("Characters/")
            || p.startsWith("NPC/")
            || p.startsWith("VFX/")) {
            return p;
        }
        return fallbackPlankTexture();
    }

    private static boolean useFurnitureFallbackTexture(@Nonnull BlockType blockType) {
        DrawType drawType = blockType.getDrawType();
        if (drawType == DrawType.Model || drawType == DrawType.CubeWithModel) {
            return true;
        }
        String id = blockType.getId();
        if (id != null) {
            if (id.startsWith("Furniture_")) {
                return true;
            }
            if (id.contains("Plant_") && blockType.getMaterial() == BlockMaterial.Empty) {
                return true;
            }
        }
        if (blockType.getMaterial() == BlockMaterial.Empty) {
            BlockGathering gathering = blockType.getGathering();
            if (gathering != null && gathering.getSoft() != null) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String firstTexturePath(@Nonnull BlockType blockType) {
        CustomModelTexture[] modelTextures = blockType.getCustomModelTexture();
        if (modelTextures != null && modelTextures.length > 0) {
            for (CustomModelTexture entry : modelTextures) {
                String path = entry.getTexture();
                if (path != null && !path.isBlank()) {
                    return path.trim();
                }
            }
        }
        BlockTypeTextures[] textures = blockType.getTextures();
        if (textures != null && textures.length > 0) {
            BlockTypeTextures t = textures[0];
            String up = t.getUp();
            if (up != null && !up.isBlank() && !up.contains("Unknown")) {
                return up.trim();
            }
            String north = t.getNorth();
            if (north != null && !north.isBlank() && !north.contains("Unknown")) {
                return north.trim();
            }
        }
        return null;
    }

    @Nonnull
    private static String fallbackPlankTexture() {
        String cached = cachedFallbackTexture;
        if (cached != null) {
            return cached;
        }
        cachedFallbackTexture = ENTITY_TEXTURE_FALLBACK;
        return cachedFallbackTexture;
    }
}
