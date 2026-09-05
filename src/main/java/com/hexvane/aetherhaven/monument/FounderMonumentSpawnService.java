package com.hexvane.aetherhaven.monument;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.cosmetics.PlayerSkinModelExporter;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Spawns a frozen decorative statue whose mesh matches the placer's cosmetics. Not an NPC, so boot never rebuilds
 * an Elder role for it. {@link CosmeticsModule#createModel(PlayerSkin)} only validates the skin and loads the base
 * {@code Player} asset — it does not attach clothing; {@link PlayerSkinModelExporter} resolves the full attachment
 * list from the registry. Stone uses only the two packaged statue textures so join never has to send generated
 * Common files.
 */
public final class FounderMonumentSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String DETACHED_FACE_MODEL =
        "Characters/Body_Attachments/Faces/Player_Face_Detached.blockymodel";

    private FounderMonumentSpawnService() {}

    /**
     * {@link com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference#toModel()} requires a positive scale;
     * invalid or missing persisted scale breaks chunk load.
     */
    public static float safePersistScale(float scale) {
        if (scale <= 0f || Float.isNaN(scale) || Float.isInfinite(scale)) {
            return 1.0f;
        }
        return scale;
    }

    /** Stone player-body fallback used before dynamic cosmetics are restored, or when persisted skin data is invalid. */
    @Nullable
    public static Model buildFallbackModel() {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(AetherhavenConstants.FOUNDER_MONUMENT_STATUE_MODEL_ID);
        return asset != null ? toStaticModel(Model.createScaledModel(asset, 1.0f, null, null, true)) : null;
    }

    /**
     * Saved model id must stay {@code Player}. Persisting {@code Founder_Monument_Statue} makes the next boot compile
     * a second player mesh in {@code SetRenderedModel} while the world thread holds the asset read lock, which freezes
     * {@code Universe.getUniverseReady()}.
     */
    @Nonnull
    public static PersistentModel toPersistentModel(@Nonnull Model model) {
        return bootSafePersistentModel(model.getScale());
    }

    @Nonnull
    public static PersistentModel bootSafePersistentModel(float scale) {
        return new PersistentModel(
            new Model.ModelReference("Player", safePersistScale(scale), null, true)
        );
    }

    public static boolean isStatuePersistentModel(@Nullable PersistentModel persistentModel) {
        if (persistentModel == null) {
            return false;
        }
        return AetherhavenConstants.FOUNDER_MONUMENT_STATUE_MODEL_ID.equals(
            persistentModel.getModelReference().getModelAssetId()
        );
    }

    /**
     * Rebuilds the monument model from a skin (full cosmetics from registry + stone texture). Used when spawning and
     * when restoring after chunk load.
     *
     * @return null if the skin is invalid or attachments cannot be resolved
     */
    @Nullable
    public static Model buildMonumentModel(@Nonnull PlayerSkin skin) {
        CosmeticsModule cos = CosmeticsModule.get();
        try {
            cos.validateSkin(skin);
        } catch (CosmeticsModule.InvalidSkinException e) {
            LOGGER.atWarning().withCause(e).log("Founder monument: invalid player skin");
            return null;
        } catch (RuntimeException e) {
            // CosmeticsModule.isValidTexture NPEs when a registry part has a null textures map (seen after Update 6
            // for some persisted founder skins). Treat that as unusable cosmetics and let the caller use the stone
            // fallback instead of killing the world task queue.
            LOGGER.atWarning().withCause(e).log("Founder monument: cosmetics validation failed");
            return null;
        }
        ModelAttachment[] skinAttachments;
        try {
            skinAttachments = PlayerSkinModelExporter.toModelAttachments(skin, cos.getRegistry());
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Founder monument: failed to resolve skin attachments");
            return null;
        }
        if (skinAttachments.length == 0) {
            LOGGER.atWarning().log("Founder monument: skin resolved to no attachments");
            return null;
        }
        return buildStoneAttachmentModel(skinAttachments);
    }

    /**
     * Builds a transient stone preview from a configured villager model asset. The base player mesh is supplied by the
     * dedicated statue model, so duplicate Player.blockymodel attachments are omitted.
     */
    @Nullable
    public static Model buildStonePreviewModel(@Nonnull ModelAsset appearanceAsset) {
        ModelAttachment[] attachments = appearanceAsset.getDefaultAttachments();
        if (attachments == null) {
            attachments = new ModelAttachment[0];
        } else {
            attachments = Arrays.stream(attachments)
                .filter(attachment -> !"Characters/Player.blockymodel".equals(attachment.getModel()))
                .toArray(ModelAttachment[]::new);
        }
        if (!"Characters/Player.blockymodel".equals(appearanceAsset.getModel())) {
            return buildStoneModel(
                appearanceAsset,
                appearanceAsset.getModel(),
                appearanceAsset.getTexture(),
                attachments
            );
        }
        return buildStoneAttachmentModel(attachments);
    }

    @Nullable
    private static Model buildStoneAttachmentModel(@Nonnull ModelAttachment[] attachments) {
        ModelAsset statueAsset = ModelAsset.getAssetMap().getAsset(AetherhavenConstants.FOUNDER_MONUMENT_STATUE_MODEL_ID);
        if (statueAsset == null) {
            LOGGER.atWarning()
                .log("Founder monument: missing model asset %s", AetherhavenConstants.FOUNDER_MONUMENT_STATUE_MODEL_ID);
            return null;
        }
        ModelAttachment[] withoutIntegratedFace = Arrays.stream(attachments)
            .filter(attachment -> !DETACHED_FACE_MODEL.equals(attachment.getModel()))
            .toArray(ModelAttachment[]::new);
        return buildStoneModel(
            statueAsset,
            AetherhavenConstants.FOUNDER_MONUMENT_STATUE_BODY_MODEL,
            statueAsset.getTexture(),
            withoutIntegratedFace
        );
    }

    @Nullable
    private static Model buildStoneModel(
        @Nonnull ModelAsset templateAsset,
        @Nonnull String modelPath,
        @Nonnull String baseTexture,
        @Nonnull ModelAttachment[] attachments
    ) {
        Model template = Model.createScaledModel(templateAsset, 1.0f, null, null, true);
        FounderMonumentStoneTextures.Prepared stone;
        try {
            stone = FounderMonumentStoneTextures.prepare(baseTexture, attachments);
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Founder monument: failed to prepare packaged stone textures");
            return null;
        }
        Model merged = new Model(
            template.getModelAssetId(),
            safePersistScale(template.getScale()),
            template.getRandomAttachmentIds(),
            stone.attachments(),
            template.getBoundingBox(),
            modelPath,
            stone.baseTexture(),
            null,
            null,
            template.getEyeHeight(),
            template.getCrouchOffset(),
            template.getSittingOffset(),
            template.getSleepingOffset(),
            null,
            template.getCamera(),
            template.getLight(),
            template.getParticles(),
            template.getTrails(),
            template.getPhysicsValues(),
            template.getDetailBoxes(),
            template.getPhobia(),
            template.getPhobiaModelAssetId()
        );
        return toStaticModel(merged);
    }

    /**
     * @param rotation entity body/head euler (pitch=x, yaw=y, roll=z); placer look + π on yaw from {@link
     *     com.hexvane.aetherhaven.monument.FounderMonumentPlaceSystem}
     * @return spawned entity UUID, or null on failure
     */
    @Nullable
    public static UUID spawnFounderStatue(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        int blockX,
        int blockY,
        int blockZ,
        @Nonnull PlayerSkin skin,
        @Nonnull String displayNameForLabel,
        @Nonnull Rotation3f rotation
    ) {
        Model monumentModel = buildMonumentModel(skin);
        if (monumentModel == null) {
            return null;
        }
        return spawnFounderStatue(
            world,
            store,
            new Vector3d(blockX + 0.5, blockY + 1.05, blockZ + 0.5),
            rotation,
            skin,
            displayNameForLabel,
            monumentModel
        );
    }

    /**
     * Same spawn as placing a monument. Uses the player's saved clothing when the skin is valid, otherwise the stone
     * body so a statue still appears.
     */
    @Nullable
    public static UUID spawnStatueLikePlace(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        int blockX,
        int blockY,
        int blockZ,
        @Nullable PlayerSkin skin,
        @Nonnull String displayNameForLabel,
        @Nonnull Rotation3f rotation
    ) {
        if (skin != null) {
            UUID clothed =
                spawnFounderStatue(world, store, blockX, blockY, blockZ, skin, displayNameForLabel, rotation);
            if (clothed != null) {
                return clothed;
            }
        }
        Model fallback = buildFallbackModel();
        if (fallback == null) {
            return null;
        }
        return spawnFounderStatue(
            world,
            store,
            new Vector3d(blockX + 0.5, blockY + 1.05, blockZ + 0.5),
            rotation,
            skin,
            displayNameForLabel,
            fallback
        );
    }

    /**
     * Spawns a statue at an exact world position using a model built off the world thread. Saved
     * {@link PersistentModel} stays {@code Player} so the next boot does not compile a second mesh.
     */
    @Nullable
    public static UUID spawnFounderStatue(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d pos,
        @Nonnull Rotation3f rotation,
        @Nullable PlayerSkin skin,
        @Nonnull String displayNameForLabel,
        @Nonnull Model monumentModel
    ) {
        FounderMonumentStatueSkin storedSkin =
            skin != null ? FounderMonumentStatueSkin.fromProtocol(skin) : new FounderMonumentStatueSkin();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rotation));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(monumentModel));
        holder.addComponent(PersistentModel.getComponentType(), toPersistentModel(monumentModel));
        holder.ensureComponent(ActiveAnimationComponent.getComponentType());
        holder.addComponent(FounderMonumentStatueSkin.getComponentType(), storedSkin);
        holder.addComponent(
            PersistentDisplayName.getComponentType(),
            new PersistentDisplayName(Message.raw(displayNameForLabel))
        );
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(Box.horizontallyCentered(0.8, 2.0, 0.8)));
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(Frozen.getComponentType());
        holder.putComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        holder.ensureComponent(UUIDComponent.getComponentType());
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    /** Same mesh as {@code dynamic} from cosmetics, but no animation sets (statue). */
    @Nonnull
    private static Model toStaticModel(@Nonnull Model dynamic) {
        return new Model(
            dynamic.getModelAssetId(),
            safePersistScale(dynamic.getScale()),
            dynamic.getRandomAttachmentIds(),
            dynamic.getAttachments(),
            dynamic.getBoundingBox(),
            dynamic.getModel(),
            dynamic.getTexture(),
            dynamic.getGradientSet(),
            dynamic.getGradientId(),
            dynamic.getEyeHeight(),
            dynamic.getCrouchOffset(),
            dynamic.getSittingOffset(),
            dynamic.getSleepingOffset(),
            Collections.emptyMap(),
            dynamic.getCamera(),
            dynamic.getLight(),
            dynamic.getParticles(),
            dynamic.getTrails(),
            dynamic.getPhysicsValues(),
            dynamic.getDetailBoxes(),
            dynamic.getPhobia(),
            dynamic.getPhobiaModelAssetId()
        );
    }

    /** Pedestal block under a statue spawned at {@code (block + 0.5, blockY + 1.05, block + 0.5)}. */
    @Nonnull
    public static Vector3i pedestalBlockFromStatuePosition(double x, double y, double z) {
        return new Vector3i((int) Math.floor(x), (int) Math.round(y - 1.05), (int) Math.floor(z));
    }

    @Nullable
    public static Ref<ChunkStore> findMonumentBlockNear(@Nonnull World world, double statueX, double statueY, double statueZ) {
        Vector3i pedestal = pedestalBlockFromStatuePosition(statueX, statueY, statueZ);
        Ref<ChunkStore> exact = monumentBlockAt(world, pedestal.x, pedestal.y, pedestal.z);
        if (exact != null) {
            return exact;
        }
        for (int y = pedestal.y - 2; y <= pedestal.y + 1; y++) {
            if (y == pedestal.y) {
                continue;
            }
            Ref<ChunkStore> blockRef = monumentBlockAt(world, pedestal.x, y, pedestal.z);
            if (blockRef != null) {
                return blockRef;
            }
        }
        return null;
    }

    @Nullable
    private static Ref<ChunkStore> monumentBlockAt(@Nonnull World world, int x, int y, int z) {
        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        if (blockRef == null || !blockRef.isValid()) {
            return null;
        }
        if (blockRef.getStore().getComponent(blockRef, FounderMonumentBlock.getComponentType()) == null) {
            return null;
        }
        return blockRef;
    }

    public static void persistStatueOnBlock(
        @Nonnull World world,
        double statueX,
        double statueY,
        double statueZ,
        @Nullable UUID statueEntityUuid,
        @Nonnull String skinJson,
        @Nonnull String label,
        @Nonnull Rotation3f rotation
    ) {
        Ref<ChunkStore> blockRef = findMonumentBlockNear(world, statueX, statueY, statueZ);
        if (blockRef == null || !blockRef.isValid()) {
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        FounderMonumentBlock existing = cs.getComponent(blockRef, FounderMonumentBlock.getComponentType());
        if (existing == null) {
            return;
        }
        String mergedSkin = firstNonBlank(skinJson, existing.getSkinJson());
        String mergedLabel = firstNonBlank(label, existing.getLabel());
        Rotation3f mergedRotation =
            isIdentityRotation(rotation)
                ? new Rotation3f(existing.getPitch(), existing.getYaw(), existing.getRoll())
                : rotation;
        String townId = firstNonBlank(existing.getTownId(), lookupTownId(world, statueX, statueZ));
        cs.putComponent(
            blockRef,
            FounderMonumentBlock.getComponentType(),
            new FounderMonumentBlock(
                townId,
                statueEntityUuid != null ? statueEntityUuid.toString() : existing.getStatueEntityUuid(),
                mergedSkin,
                mergedLabel,
                mergedRotation.pitch(),
                mergedRotation.yaw(),
                mergedRotation.roll()
            )
        );
    }

    @Nonnull
    private static String lookupTownId(@Nonnull World world, double statueX, double statueZ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return "";
        }
        TownManager towns = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town =
            towns.findTownContainingBlock(world.getName(), (int) Math.floor(statueX), (int) Math.floor(statueZ));
        return town != null ? town.getTownId().toString() : "";
    }

    static boolean isIdentityRotation(@Nonnull Rotation3f rotation) {
        return Math.abs(rotation.pitch()) < 0.0001f
            && Math.abs(rotation.yaw()) < 0.0001f
            && Math.abs(rotation.roll()) < 0.0001f;
    }

    @Nonnull
    static String firstNonBlank(@Nullable String preferred, @Nullable String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback != null ? fallback : "";
    }

    static void applyStatueFacing(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Rotation3f rotation
    ) {
        if (isIdentityRotation(rotation)) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            transform.setRotation(rotation);
        }
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (head != null) {
            head.setRotation(rotation);
        } else {
            store.putComponent(ref, HeadRotation.getComponentType(), new HeadRotation(rotation));
        }
    }

    /**
     * Queues removal on the world thread so this is safe from {@link com.hypixel.hytale.component.system.EntityEventSystem}
     * handlers (store must not mutate while processing).
     */
    public static void removeStatueEntity(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull UUID entityUuid) {
        final UUID id = entityUuid;
        world.execute(() -> {
            Ref<EntityStore> r = store.getExternalData().getRefFromUUID(id);
            if (r != null && r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
            }
        });
    }
}
