package com.hexvane.aetherhaven.monument;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.cosmetics.PlayerSkinModelExporter;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Spawns a frozen NPC whose mesh matches the placer's cosmetics. {@link CosmeticsModule#createModel(PlayerSkin)} only
 * validates the skin and loads the base {@code Player} asset — it does not attach clothing; {@link PlayerSkinModelExporter}
 * resolves the full attachment list from the registry. Stone is applied with textures matching each source texture's
 * native canvas dimensions; one shared attachment texture corrupts pixel-space UV sampling in the entity atlas.
 */
public final class FounderMonumentSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String DETACHED_FACE_MODEL =
        "Characters/Body_Attachments/Faces/Player_Face_Detached.blockymodel";

    private FounderMonumentSpawnService() {}

    /** Registers all required stone texture canvas sizes before any client builds its initial entity atlas. */
    public static void prewarmStoneTextures() {
        FounderMonumentStoneTextures.prewarm();
    }

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

    @Nonnull
    public static PersistentModel toPersistentModel(@Nonnull Model model) {
        return new PersistentModel(
            new Model.ModelReference(
                AetherhavenConstants.FOUNDER_MONUMENT_STATUE_MODEL_ID,
                safePersistScale(model.getScale()),
                model.getRandomAttachmentIds(),
                true
            )
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
                FounderMonumentStoneTextures.ensureIsolatedModel(appearanceAsset.getModel()),
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
            statueAsset.getModel(),
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
            LOGGER.atWarning().withCause(e).log("Founder monument: failed to prepare dimension-matched stone textures");
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
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return null;
        }
        int roleIndex = npc.getIndex(AetherhavenConstants.ELDER_NPC_ROLE_ID);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Founder monument: no NPC role %s", AetherhavenConstants.ELDER_NPC_ROLE_ID);
            return null;
        }
        Model monumentModel = buildMonumentModel(skin);
        if (monumentModel == null) {
            return null;
        }
        Vector3d pos = new Vector3d(blockX + 0.5, blockY + 1.05, blockZ + 0.5);
        var pair = npc.spawnEntity(
            store,
            roleIndex,
            pos,
            rotation,
            monumentModel,
            (npcEntity, holder, st) -> holder.addComponent(FounderMonumentStatueSkin.getComponentType(), FounderMonumentStatueSkin.fromProtocol(skin)),
            null
        );
        if (pair == null) {
            return null;
        }
        Ref<EntityStore> ref = pair.first();
        store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(monumentModel));
        store.putComponent(ref, PersistentModel.getComponentType(), toPersistentModel(monumentModel));
        store.ensureComponent(ref, Frozen.getComponentType());
        store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
        store.putComponent(
            ref,
            PersistentDisplayName.getComponentType(),
            new PersistentDisplayName(Message.raw(displayNameForLabel))
        );
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
