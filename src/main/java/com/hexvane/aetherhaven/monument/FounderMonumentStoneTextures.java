package com.hexvane.aetherhaven.monument;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plotcreator.RuntimeCommonIconBroadcast;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;
import com.hypixel.hytale.server.core.universe.Universe;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.imageio.ImageIO;

/**
 * Creates one opaque stone texture per cosmetic canvas size. Blockymodel UVs use pixel coordinates, so each distinct
 * canvas size needs a matching texture. Keeping this set bounded is critical: source-specific duplicates can overflow
 * or destabilize the client's global character atlas and corrupt unrelated NPC textures. Statues reuse the original
 * clothing meshes and only swap those textures.
 */
final class FounderMonumentStoneTextures {
    private static final String PATH_PREFIX = "Characters/Aetherhaven/Founder_Monument_Stone_CanvasV4_";
    private static final Map<String, Dimensions> DIMENSIONS_BY_HASH = new HashMap<>();
    private static String stoneSourceHash;
    private static BufferedImage stoneSourceImage;

    private FounderMonumentStoneTextures() {}

    /**
     * Registers the bounded set of canvas-size variants before clients receive the initial Common asset set.
     */
    static synchronized void prewarm() {
        if (Universe.get().getPlayerCount() > 0) {
            throw new IllegalStateException("Founder monument stone textures must be prewarmed before players join");
        }
        BufferedImage stoneSource = loadStoneSource();
        CosmeticRegistry registry = CosmeticsModule.get().getRegistry();
        Set<Dimensions> dimensions = new HashSet<>();
        for (String texturePath : cosmeticTexturePaths(registry)) {
            collectDimensions(texturePath, dimensions);
        }
        collectConfiguredVillagerDimensions(dimensions);
        for (Dimensions size : dimensions) {
            ensureStoneTexture(size, stoneSource);
        }
        RuntimeCommonIconBroadcast.invalidateRequiredAssetsCache();
    }

    private static void collectConfiguredVillagerDimensions(@Nonnull Set<Dimensions> dimensions) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        Set<String> modelIds = new LinkedHashSet<>();
        plugin.getTownsfolkCharacterCatalog()
            .allById()
            .values()
            .forEach(definition -> modelIds.add(definition.getModelAssetId()));
        for (ModelAsset asset : ModelAsset.getAssetMap().getAssetMap().values()) {
            String icon = asset.getIcon();
            if (
                "Characters/Player.blockymodel".equals(asset.getModel())
                    && icon != null
                    && icon.startsWith("Icons/ModelsGenerated/Aetherhaven_")
            ) {
                modelIds.add(asset.getId());
            }
        }
        for (String modelId : modelIds) {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
            if (asset == null) {
                continue;
            }
            if (!"Characters/Player.blockymodel".equals(asset.getModel())) {
                collectDimensions(asset.getTexture(), dimensions);
            }
            ModelAttachment[] attachments = asset.getDefaultAttachments();
            if (attachments == null) {
                continue;
            }
            for (ModelAttachment attachment : attachments) {
                if (!"Characters/Player.blockymodel".equals(attachment.getModel())) {
                    collectDimensions(attachment.getTexture(), dimensions);
                }
            }
        }
    }

    private static void collectDimensions(String texturePath, @Nonnull Set<Dimensions> dimensions) {
        if (texturePath == null || texturePath.isEmpty()) {
            return;
        }
        CommonAsset source = CommonAssetRegistry.getByName(texturePath);
        if (source != null) {
            dimensions.add(DIMENSIONS_BY_HASH.computeIfAbsent(source.getHash(), ignored -> readDimensions(source)));
        }
    }

    @Nonnull
    static synchronized Prepared prepare(@Nonnull String baseTexture, @Nonnull ModelAttachment[] attachments) {
        Map<String, String> stoneBySource = new HashMap<>();
        BufferedImage stoneSource = loadStoneSource();
        String stoneBase = resolveStoneTexture(baseTexture, stoneSource, stoneBySource);

        ModelAttachment[] stoneAttachments = new ModelAttachment[attachments.length];
        for (int i = 0; i < attachments.length; i++) {
            ModelAttachment source = attachments[i];
            String stoneTexture = resolveStoneTexture(source.getTexture(), stoneSource, stoneBySource);
            stoneAttachments[i] = new ModelAttachment(
                source.getModel(),
                stoneTexture,
                null,
                null,
                source.getWeight()
            );
        }
        return new Prepared(stoneBase, stoneAttachments);
    }

    @Nonnull
    private static String resolveStoneTexture(
        @Nonnull String sourceTexture,
        @Nonnull BufferedImage stoneSource,
        @Nonnull Map<String, String> stoneBySource
    ) {
        String cached = stoneBySource.get(sourceTexture);
        if (cached != null) {
            return cached;
        }
        if (AetherhavenConstants.FOUNDER_MONUMENT_STATUE_BASE_TEXTURE.equals(sourceTexture)) {
            stoneBySource.put(sourceTexture, sourceTexture);
            return sourceTexture;
        }
        CommonAsset source = CommonAssetRegistry.getByName(sourceTexture);
        if (source == null) {
            throw new IllegalArgumentException("Missing source texture " + sourceTexture);
        }
        Dimensions dimensions = DIMENSIONS_BY_HASH.computeIfAbsent(source.getHash(), ignored -> readDimensions(source));
        String stonePath = stonePath(dimensions);
        stoneBySource.put(sourceTexture, stonePath);
        ensureStoneTexture(dimensions, stoneSource);
        return stonePath;
    }

    private static void ensureStoneTexture(@Nonnull Dimensions dimensions, @Nonnull BufferedImage stoneSource) {
        String stonePath = stonePath(dimensions);
        if (CommonAssetRegistry.hasCommonAsset(stonePath)) {
            return;
        }
        if (Universe.get().getPlayerCount() > 0) {
            throw new IllegalStateException("Stone texture canvas was not prewarmed: " + dimensions);
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            throw new IllegalStateException("Aetherhaven plugin is not available");
        }
        byte[] png = generateStonePng(dimensions.width(), dimensions.height(), stoneSource);
        GeneratedCommonAsset generated = new GeneratedCommonAsset(stonePath, png);
        String packId = new PluginIdentifier(plugin.getManifest()).toString();
        CommonAssetRegistry.addCommonAsset(packId, generated);
    }

    @Nonnull
    private static String stonePath(@Nonnull Dimensions dimensions) {
        return PATH_PREFIX + dimensions.width() + "x" + dimensions.height() + ".png";
    }

    @Nonnull
    private static Set<String> cosmeticTexturePaths(@Nonnull CosmeticRegistry registry) {
        Set<String> paths = new HashSet<>();
        for (Map<String, PlayerSkinPart> partMap : cosmeticPartMaps(registry)) {
            for (PlayerSkinPart part : partMap.values()) {
                addTexture(paths, part.getGreyscaleTexture());
                addTextures(paths, part.getTextures());
                if (part.getVariants() != null) {
                    for (PlayerSkinPart.Variant variant : part.getVariants().values()) {
                        addTexture(paths, variant.getGreyscaleTexture());
                        addTextures(paths, variant.getTextures());
                    }
                }
            }
        }
        // PlayerSkinModelExporter substitutes this NPC-safe ear texture for a legacy cosmetics entry.
        paths.add("Characters/Body_Attachments/Ears/Ears1_Textures/Ears1_Greyscale_Texture.png");
        return paths;
    }

    @Nonnull
    private static List<Map<String, PlayerSkinPart>> cosmeticPartMaps(@Nonnull CosmeticRegistry registry) {
        return List.of(
            registry.getBodyCharacteristics(),
            registry.getUnderwear(),
            registry.getEyebrows(),
            registry.getEars(),
            registry.getEyes(),
            registry.getFaces(),
            registry.getMouths(),
            registry.getFacialHairs(),
            registry.getPants(),
            registry.getOverpants(),
            registry.getUndertops(),
            registry.getOvertops(),
            registry.getHaircuts(),
            registry.getShoes(),
            registry.getHeadAccessories(),
            registry.getFaceAccessories(),
            registry.getEarAccessories(),
            registry.getGloves(),
            registry.getSkinFeatures(),
            registry.getCapes()
        );
    }

    private static void addTextures(@Nonnull Set<String> paths, Map<String, PlayerSkinPartTexture> textures) {
        if (textures == null) {
            return;
        }
        for (PlayerSkinPartTexture texture : textures.values()) {
            addTexture(paths, texture.getTexture());
        }
    }

    private static void addTexture(@Nonnull Set<String> paths, String texture) {
        if (texture != null && !texture.isEmpty()) {
            paths.add(texture);
        }
    }

    @Nonnull
    private static BufferedImage loadStoneSource() {
        CommonAsset source = CommonAssetRegistry.getByName(AetherhavenConstants.FOUNDER_MONUMENT_STATUE_TEXTURE);
        if (source == null) {
            throw new IllegalArgumentException(
                "Missing founder monument stone source " + AetherhavenConstants.FOUNDER_MONUMENT_STATUE_TEXTURE
            );
        }
        if (source.getHash().equals(stoneSourceHash) && stoneSourceImage != null) {
            return stoneSourceImage;
        }
        BufferedImage image = readImage(source);
        stoneSourceHash = source.getHash();
        stoneSourceImage = image;
        return image;
    }

    @Nonnull
    private static BufferedImage readImage(@Nonnull CommonAsset source) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source.getBlob().join()));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IllegalArgumentException("Unsupported texture " + source.getName());
            }
            return image;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Could not read texture " + source.getName(), e);
        }
    }

    @Nonnull
    private static Dimensions readDimensions(@Nonnull CommonAsset source) {
        BufferedImage image = readImage(source);
        return new Dimensions(image.getWidth(), image.getHeight());
    }

    @Nonnull
    static byte[] generateStonePng(int width, int height, @Nonnull BufferedImage stoneSource) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Stone texture dimensions must be positive");
        }
        if (stoneSource.getWidth() <= 0 || stoneSource.getHeight() <= 0) {
            throw new IllegalArgumentException("Stone source texture dimensions must be positive");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int stoneRgb = stoneSource.getRGB(x % stoneSource.getWidth(), y % stoneSource.getHeight()) & 0x00FFFFFF;
                image.setRGB(x, y, 0xFF000000 | stoneRgb);
            }
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG encoder is unavailable");
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode founder monument stone texture", e);
        }
    }

    record Prepared(@Nonnull String baseTexture, @Nonnull ModelAttachment[] attachments) {}

    private record Dimensions(int width, int height) {}

    private static final class GeneratedCommonAsset extends CommonAsset {
        private final byte[] bytes;

        private GeneratedCommonAsset(@Nonnull String name, @Nonnull byte[] bytes) {
            super(name, bytes);
            this.bytes = bytes;
        }

        @Nonnull
        @Override
        protected CompletableFuture<byte[]> getBlob0() {
            return CompletableFuture.completedFuture(this.bytes);
        }
    }
}
