package com.hexvane.aetherhaven.plotcreator.icon;

import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.ShaderType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockTypeTextures;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves prefab block indices to face colors for isometric thumbnail rasterization. */
public final class BlockColorResolver {
    private static final int FALLBACK_GRAY = 0xFF808080;
    private static final int EDITOR_BLOCK_INDEX = BlockType.getAssetMap().getIndex("Editor_Block");
    private static final int EDITOR_EMPTY_INDEX = BlockType.getAssetMap().getIndex("Editor_Empty");

    /** Non-greyscale reference colors for biome-tinted grass (Soil_Grass_Sunny / Soil_Dirt). */
    private static final int GRASS_TOP_ARGB = referenceArgb("Soil_Grass_Sunny", 0xFF709D2C);
    private static final int GRASS_SIDE_ARGB = referenceArgb("Soil_Dirt", 0xFF88632B);

    private static final String FLUID_WATER_TEXTURE = "BlockTextures/Fluid_Water.png";
    private static final String FLUID_LAVA_TEXTURE = "BlockTextures/Fluid_Lava.png";
    private static final int WATER_TOP_FALLBACK = 0xFF3B7FB8;
    private static final int LAVA_TOP_FALLBACK = 0xFFF94E11;

    private BlockColorResolver() {}

    public static boolean isRenderableFluid(int fluidId) {
        return fluidId != Fluid.EMPTY_ID;
    }

    @Nonnull
    public static FaceColors resolveFluidFaceColors(int fluidId) {
        if (!isRenderableFluid(fluidId)) {
            return FaceColors.EMPTY;
        }
        int top = resolveFluidTopArgb(fluidId);
        return new FaceColors(top, darken(top, 0.68f), darken(top, 0.84f));
    }

    private static int resolveFluidTopArgb(int fluidId) {
        Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
        if (fluid != null) {
            if (isWaterFluid(fluid)) {
                return fluidTextureTop(FLUID_WATER_TEXTURE, WATER_TOP_FALLBACK, fluid);
            }
            if (isLavaFluid(fluid)) {
                return fluidTextureTop(FLUID_LAVA_TEXTURE, LAVA_TOP_FALLBACK, fluid);
            }
        }
        return FALLBACK_GRAY;
    }

    private static boolean isWaterFluid(@Nonnull Fluid fluid) {
        if (fluid.hasEffect(ShaderType.Water)) {
            return true;
        }
        String id = fluid.getId();
        return id != null && id.contains("Water");
    }

    private static boolean isLavaFluid(@Nonnull Fluid fluid) {
        if (fluid.hasEffect(ShaderType.Lava)) {
            return true;
        }
        String id = fluid.getId();
        return id != null && id.contains("Lava");
    }

    private static int fluidTextureTop(@Nonnull String texturePath, int fallback, @Nullable Fluid fluid) {
        int fromTexture = BlockTextureColorSampler.averageArgb(texturePath);
        if (BlockTextureColorSampler.isResolved(fromTexture)) {
            return fromTexture;
        }
        if (fluid != null) {
            Color particleColor = fluid.getParticleColor();
            if (particleColor != null) {
                return toOpaqueArgb(particleColor);
            }
        }
        return fallback;
    }

    public static boolean isRenderable(int blockId) {
        if (blockId == BlockType.EMPTY_ID) {
            return false;
        }
        if (EDITOR_BLOCK_INDEX != Integer.MIN_VALUE && blockId == EDITOR_BLOCK_INDEX) {
            return false;
        }
        if (EDITOR_EMPTY_INDEX != Integer.MIN_VALUE && blockId == EDITOR_EMPTY_INDEX) {
            return false;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        return blockType == null || !isIconSkippedBlock(blockType);
    }

    /**
     * Soft/decorative blocks that rasterize as white cubes in the thumbnail pass. Leaves are kept — they have usable
     * green metadata even though gathering marks them soft like rubble and tall grass.
     */
    private static boolean isIconSkippedBlock(@Nonnull BlockType blockType) {
        if (isLeavesBlock(blockType)) {
            return false;
        }
        String id = blockType.getId();
        if (id != null && id.contains("Plant_Grass")) {
            return true;
        }
        if (blockType.getMaterial() == BlockMaterial.Empty) {
            return true;
        }
        BlockGathering gathering = blockType.getGathering();
        return gathering != null && gathering.getSoft() != null;
    }

    private static boolean isLeavesBlock(@Nonnull BlockType blockType) {
        String group = blockType.getGroup();
        if (group != null && "Leaves".equalsIgnoreCase(group)) {
            return true;
        }
        String id = blockType.getId();
        return id != null && (id.contains("Plant_Leaves") || id.contains("_Leaves"));
    }

    @Nonnull
    public static FaceColors resolveFaceColors(int blockId) {
        if (!isRenderable(blockId)) {
            return FaceColors.EMPTY;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            return FaceColors.uniform(FALLBACK_GRAY);
        }
        if (isBiomeTintedGrass(blockType)) {
            return new FaceColors(GRASS_TOP_ARGB, darken(GRASS_SIDE_ARGB, 0.78f), darken(GRASS_SIDE_ARGB, 0.9f));
        }
        int top = resolveTopArgb(blockType);
        int side = resolveSideArgb(blockType, top);
        return new FaceColors(top, darken(side, 0.68f), darken(side, 0.84f));
    }

    private static int resolveTopArgb(@Nonnull BlockType blockType) {
        int fromTexture = resolveTextureTopArgb(blockType);
        if (BlockTextureColorSampler.isResolved(fromTexture)) {
            return fromTexture;
        }
        return resolveMetadataArgb(blockType);
    }

    private static int resolveSideArgb(@Nonnull BlockType blockType, int topFallback) {
        int fromTexture = resolveTextureSideArgb(blockType);
        if (BlockTextureColorSampler.isResolved(fromTexture)) {
            return fromTexture;
        }
        return topFallback;
    }

    private static int resolveTextureTopArgb(@Nonnull BlockType blockType) {
        CustomModelTexture[] modelTextures = blockType.getCustomModelTexture();
        if (modelTextures != null && modelTextures.length > 0) {
            if (isRoofBlock(blockType)) {
                return weightedRoofTopFromModelTextures(modelTextures);
            }
            return weightedAverageFromPaths(modelTextures, CustomModelTexture::getTexture, CustomModelTexture::getWeight);
        }
        BlockTypeTextures[] textures = blockType.getTextures();
        if (textures != null && textures.length > 0) {
            return weightedAverageFromPaths(textures, BlockTypeTextures::getUp, t -> Math.round(t.getWeight()));
        }
        return BlockTextureColorSampler.averageArgb(null);
    }

    private static int resolveTextureSideArgb(@Nonnull BlockType blockType) {
        CustomModelTexture[] modelTextures = blockType.getCustomModelTexture();
        if (modelTextures != null && modelTextures.length > 0 && isRoofBlock(blockType)) {
            int roofSide = weightedRoofSideFromModelTextures(modelTextures);
            if (BlockTextureColorSampler.isResolved(roofSide)) {
                return roofSide;
            }
        }
        BlockTypeTextures[] textures = blockType.getTextures();
        if (textures == null || textures.length == 0) {
            return BlockTextureColorSampler.averageArgb(null);
        }
        float totalWeight = 0f;
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        for (BlockTypeTextures texture : textures) {
            float weight = Math.max(1f, texture.getWeight());
            int side = averageSideTexture(texture);
            if (!BlockTextureColorSampler.isResolved(side)) {
                continue;
            }
            totalWeight += weight;
            sumR += (long) (((side >> 16) & 0xFF) * weight);
            sumG += (long) (((side >> 8) & 0xFF) * weight);
            sumB += (long) ((side & 0xFF) * weight);
        }
        if (totalWeight <= 0f) {
            return BlockTextureColorSampler.averageArgb(null);
        }
        int r = clampByte(Math.round(sumR / totalWeight));
        int g = clampByte(Math.round(sumG / totalWeight));
        int b = clampByte(Math.round(sumB / totalWeight));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int averageSideTexture(@Nonnull BlockTypeTextures texture) {
        int north = BlockTextureColorSampler.averageArgb(texture.getNorth());
        int south = BlockTextureColorSampler.averageArgb(texture.getSouth());
        int east = BlockTextureColorSampler.averageArgb(texture.getEast());
        int west = BlockTextureColorSampler.averageArgb(texture.getWest());
        return averageResolved(north, south, east, west);
    }

    @SafeVarargs
    private static int averageResolved(int... samples) {
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        int count = 0;
        for (int sample : samples) {
            if (!BlockTextureColorSampler.isResolved(sample)) {
                continue;
            }
            sumR += (sample >> 16) & 0xFF;
            sumG += (sample >> 8) & 0xFF;
            sumB += sample & 0xFF;
            count++;
        }
        if (count == 0) {
            return BlockTextureColorSampler.averageArgb(null);
        }
        int r = (int) (sumR / count);
        int g = (int) (sumG / count);
        int b = (int) (sumB / count);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static boolean isRoofBlock(@Nonnull BlockType blockType) {
        String group = blockType.getGroup();
        if (group != null && "Roof".equalsIgnoreCase(group)) {
            return true;
        }
        String id = blockType.getId();
        return id != null && (id.contains("_Roof") || id.contains("_Stairs"));
    }

    private static int weightedRoofTopFromModelTextures(@Nonnull CustomModelTexture[] entries) {
        return weightedAverageFromRoofPaths(entries, true);
    }

    private static int weightedRoofSideFromModelTextures(@Nonnull CustomModelTexture[] entries) {
        return weightedAverageFromRoofPaths(entries, false);
    }

    private static int weightedAverageFromRoofPaths(@Nonnull CustomModelTexture[] entries, boolean top) {
        float totalWeight = 0f;
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        for (CustomModelTexture entry : entries) {
            String path = entry.getTexture();
            int argb = roofSample(path, top);
            if (!BlockTextureColorSampler.isResolved(argb)) {
                continue;
            }
            float weight = Math.max(1f, entry.getWeight());
            totalWeight += weight;
            sumR += (long) (((argb >> 16) & 0xFF) * weight);
            sumG += (long) (((argb >> 8) & 0xFF) * weight);
            sumB += (long) ((argb & 0xFF) * weight);
        }
        if (totalWeight <= 0f) {
            return BlockTextureColorSampler.averageArgb(null);
        }
        int r = clampByte(Math.round(sumR / totalWeight));
        int g = clampByte(Math.round(sumG / totalWeight));
        int b = clampByte(Math.round(sumB / totalWeight));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int roofSample(@Nullable String path, boolean top) {
        if (path == null || path.isBlank()) {
            return BlockTextureColorSampler.averageArgb(null);
        }
        if (BlockTextureColorSampler.isHayRoofAtlas(path)) {
            return top
                ? BlockTextureColorSampler.averageRoofTopArgb(path)
                : BlockTextureColorSampler.averageRoofSideArgb(path);
        }
        return BlockTextureColorSampler.averageArgb(path);
    }

    private static <T> int weightedAverageFromPaths(
        @Nonnull T[] entries,
        @Nonnull java.util.function.Function<T, String> pathFn,
        @Nonnull java.util.function.ToIntFunction<T> weightFn
    ) {
        float totalWeight = 0f;
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        for (T entry : entries) {
            String path = pathFn.apply(entry);
            int argb = BlockTextureColorSampler.averageArgb(path);
            if (!BlockTextureColorSampler.isResolved(argb)) {
                continue;
            }
            float weight = Math.max(1f, weightFn.applyAsInt(entry));
            totalWeight += weight;
            sumR += (long) (((argb >> 16) & 0xFF) * weight);
            sumG += (long) (((argb >> 8) & 0xFF) * weight);
            sumB += (long) ((argb & 0xFF) * weight);
        }
        if (totalWeight <= 0f) {
            return BlockTextureColorSampler.averageArgb(null);
        }
        int r = clampByte(Math.round(sumR / totalWeight));
        int g = clampByte(Math.round(sumG / totalWeight));
        int b = clampByte(Math.round(sumB / totalWeight));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static boolean isBiomeTintedGrass(@Nonnull BlockType blockType) {
        String group = blockType.getGroup();
        if (group == null || !"Grass".equalsIgnoreCase(group)) {
            return false;
        }
        if (blockType.getBiomeTintUp() > 0
            || blockType.getBiomeTintDown() > 0
            || blockType.getBiomeTintNorth() > 0
            || blockType.getBiomeTintSouth() > 0
            || blockType.getBiomeTintEast() > 0
            || blockType.getBiomeTintWest() > 0) {
            return true;
        }
        Color computed = blockType.getTextureComputedColor();
        return computed != null && isNearWhite(computed);
    }

    private static boolean isNearWhite(@Nonnull Color color) {
        int r = color.red & 0xFF;
        int g = color.green & 0xFF;
        int b = color.blue & 0xFF;
        return r >= 220 && g >= 220 && b >= 220;
    }

    private static int resolveMetadataArgb(@Nonnull BlockType blockType) {
        Color[] tintUp = blockType.getTintUp();
        if (tintUp != null && tintUp.length > 0 && tintUp[0] != null) {
            return toOpaqueArgb(tintUp[0]);
        }
        Color color = blockType.getTextureComputedColor();
        if (color == null) {
            color = blockType.getParticleColor();
        }
        return color != null ? toOpaqueArgb(color) : FALLBACK_GRAY;
    }

    private static int referenceArgb(@Nonnull String blockId, int fallback) {
        int index = BlockType.getAssetMap().getIndex(blockId);
        if (index == Integer.MIN_VALUE) {
            return fallback;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(index);
        if (blockType == null) {
            return fallback;
        }
        int fromTexture = resolveTextureTopArgb(blockType);
        if (BlockTextureColorSampler.isResolved(fromTexture)) {
            return fromTexture;
        }
        Color color = blockType.getTextureComputedColor();
        if (color == null) {
            color = blockType.getParticleColor();
        }
        return color != null ? toOpaqueArgb(color) : fallback;
    }

    private static int darken(int argb, float factor) {
        int r = Math.round(((argb >> 16) & 0xFF) * factor);
        int g = Math.round(((argb >> 8) & 0xFF) * factor);
        int b = Math.round((argb & 0xFF) * factor);
        return 0xFF000000 | (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int toOpaqueArgb(@Nonnull Color color) {
        int r = color.red & 0xFF;
        int g = color.green & 0xFF;
        int b = color.blue & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public record FaceColors(int top, int left, int right) {
        public static final FaceColors EMPTY = new FaceColors(0, 0, 0);

        public static FaceColors uniform(int argb) {
            return new FaceColors(argb, argb, argb);
        }
    }
}
