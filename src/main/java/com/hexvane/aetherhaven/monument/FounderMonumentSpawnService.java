package com.hexvane.aetherhaven.monument;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.FounderMonumentBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
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
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Spawns a frozen decorative entity using Hytale's native player skin rendering. The normal Player model
 * supplies the bounds; PlayerSkinComponent sends the saved appearance directly to the client, exactly as for players.
 * No cosmetic meshes or textures are generated, substituted or assembled by Aetherhaven.
 */
public final class FounderMonumentSpawnService {
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

    /** Normal Player model for bounds and for the fallback when saved appearance data is missing. */
    @Nullable
    public static Model buildFallbackModel() {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset("Player");
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

    /** Copies the appearance without NPC export rules, texture changes or cosmetic substitutions. */
    @Nonnull
    public static PlayerSkinComponent createStatueSkin(@Nonnull PlayerSkin skin) {
        return new PlayerSkinComponent(new PlayerSkin(skin));
    }

    /** Builds a transient preview using the configured villager's unchanged appearance. */
    @Nonnull
    public static Model buildStatuePreviewModel(@Nonnull ModelAsset appearanceAsset) {
        return toStaticModel(Model.createScaledModel(appearanceAsset, 1.0f, null, null, true));
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
        Model monumentModel = buildFallbackModel();
        if (monumentModel == null) {
            return null;
        }
        return spawnFounderStatue(
            world,
            store,
            new Vector3d(blockX + 0.5, blockY + 1.0, blockZ + 0.5),
            rotation,
            skin,
            displayNameForLabel,
            monumentModel
        );
    }

    /**
     * Same spawn as placing a monument. Restores the saved native player skin, or the normal Player fallback
     * if appearance data is missing.
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
            new Vector3d(blockX + 0.5, blockY + 1.0, blockZ + 0.5),
            rotation,
            skin,
            displayNameForLabel,
            fallback
        );
    }

    /**
     * Spawns a statue at an exact world position using the normal player model and native skin component. Saved
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
        if (skin != null) {
            holder.addComponent(PlayerSkinComponent.getComponentType(), createStatueSkin(skin));
        }
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

    /** Pedestal block under a statue spawned at {@code (block + 0.5, blockY + 1.0, block + 0.5)}. */
    @Nonnull
    public static Vector3i pedestalBlockFromStatuePosition(double x, double y, double z) {
        return new Vector3i((int) Math.floor(x), (int) Math.round(y - 1.0), (int) Math.floor(z));
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
