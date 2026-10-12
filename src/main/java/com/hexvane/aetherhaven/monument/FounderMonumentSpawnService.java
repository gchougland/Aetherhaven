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
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Spawns a frozen decorative entity using Hytale's native player skin rendering. The normal Player model
 * supplies the bounds; PlayerSkinComponent sends the saved appearance directly to the client, exactly as for players.
 * No cosmetic meshes or textures are generated, substituted or assembled by Aetherhaven.
 */
public final class FounderMonumentSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Opens the statue screen when a player uses the statue itself rather than its pedestal. */
    public static final String STATUE_USE_ROOT_INTERACTION = "Aetherhaven_Founder_Statue_Use";
    /** Key prompt shown on the statue and on its pedestal. */
    public static final String STATUE_USE_HINT = "aetherhaven_monument.aetherhaven.monument.statueHint";
    /** Animation set the client plays on an entity that stands still. A statue's pose is bound to it. */
    static final String POSE_ANIMATION_SET = "Idle";

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
        return buildStatueModel(FounderStatuePose.STANDING);
    }

    /**
     * Normal Player model that holds one still pose. The pose is the model's only animation set, bound as the
     * animation of standing still, the same way the game's own posed showcase mannequins are built. The model id
     * stays {@code Player}.
     */
    @Nullable
    public static Model buildStatueModel(@Nonnull FounderStatuePose pose) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset("Player");
        if (asset == null) {
            return null;
        }
        ModelAsset poses =
            pose.animationSetId() != null ? ModelAsset.getAssetMap().getAsset(FounderStatuePose.POSES_MODEL_ID) : null;
        return toStaticModel(
            Model.createScaledModel(asset, 1.0f, null, null, true),
            poseAnimations(pose, poses != null ? poses.getAnimationSetMap() : null)
        );
    }

    /** The animation sets of a posed statue: the pose's still clip as the standing animation, or none at all. */
    @Nonnull
    static Map<String, ModelAsset.AnimationSet> poseAnimations(
        @Nonnull FounderStatuePose pose,
        @Nullable Map<String, ModelAsset.AnimationSet> poseSets
    ) {
        String setId = pose.animationSetId();
        ModelAsset.AnimationSet set = setId != null && poseSets != null ? poseSets.get(setId) : null;
        return set != null ? Map.of(POSE_ANIMATION_SET, set) : Collections.emptyMap();
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
        return toStaticModel(Model.createScaledModel(appearanceAsset, 1.0f, null, null, true), Collections.emptyMap());
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
        return spawnFounderStatue(world, store, blockX, blockY, blockZ, skin, displayNameForLabel, rotation, null);
    }

    /**
     * @param loadout the pedestal's armor and pose, or null for a bare standing statue. Only read: the statue is
     *     given display copies, the real pieces stay on the pedestal block.
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
        @Nonnull Rotation3f rotation,
        @Nullable FounderStatueLoadout loadout
    ) {
        Model monumentModel = buildStatueModel(FounderStatuePose.byId(loadout != null ? loadout.getPoseId() : null));
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
            monumentModel,
            loadout
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
        return spawnStatueLikePlace(world, store, blockX, blockY, blockZ, skin, displayNameForLabel, rotation, null);
    }

    /** As above, wearing the pedestal's armor and holding its pose. */
    @Nullable
    public static UUID spawnStatueLikePlace(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        int blockX,
        int blockY,
        int blockZ,
        @Nullable PlayerSkin skin,
        @Nonnull String displayNameForLabel,
        @Nonnull Rotation3f rotation,
        @Nullable FounderStatueLoadout loadout
    ) {
        if (skin != null) {
            UUID clothed =
                spawnFounderStatue(world, store, blockX, blockY, blockZ, skin, displayNameForLabel, rotation, loadout);
            if (clothed != null) {
                return clothed;
            }
        }
        Model fallback = buildStatueModel(FounderStatuePose.byId(loadout != null ? loadout.getPoseId() : null));
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
            fallback,
            loadout
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
        return spawnFounderStatue(world, store, pos, rotation, skin, displayNameForLabel, monumentModel, null);
    }

    /**
     * @param loadout the pedestal's armor, or null. The statue wears display copies; it never holds the real pieces.
     */
    @Nullable
    public static UUID spawnFounderStatue(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d pos,
        @Nonnull Rotation3f rotation,
        @Nullable PlayerSkin skin,
        @Nonnull String displayNameForLabel,
        @Nonnull Model monumentModel,
        @Nullable FounderStatueLoadout loadout
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
        // A frozen entity never has its movement flags worked out, and a client that is told nothing about them may
        // pick a moving animation. The game marks frozen living entities idle for the same reason.
        holder.putComponent(MovementStatesComponent.getComponentType(), standingStill());
        // Armor the client draws on the statue, exactly as it does for a player or a villager. A statue that cannot
        // be given armor slots still spawns; its pieces stay safe on the pedestal either way.
        try {
            InventoryComponent.Armor worn = new InventoryComponent.Armor(InventoryComponent.DEFAULT_ARMOR_CAPACITY);
            showArmor(worn, loadout);
            holder.putComponent(InventoryComponent.Armor.getComponentType(), worn);
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Founder monument: statue spawned without armor slots");
        }
        holder.putComponent(Interactable.getComponentType(), Interactable.INSTANCE);
        holder.putComponent(Interactions.getComponentType(), statueInteractions());
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    @Nonnull
    static MovementStatesComponent standingStill() {
        MovementStates states = new MovementStates();
        states.idle = true;
        states.horizontalIdle = true;
        states.onGround = true;
        MovementStatesComponent component = new MovementStatesComponent();
        component.setMovementStates(states);
        return component;
    }

    @Nonnull
    static Interactions statueInteractions() {
        Interactions interactions = new Interactions(Map.of(InteractionType.Use, STATUE_USE_ROOT_INTERACTION));
        interactions.setInteractionHint(STATUE_USE_HINT);
        return interactions;
    }

    /**
     * Makes the statue's armor slots show what the pedestal holds. The stacks placed here are fresh single items made
     * from the item id alone; the real pieces, with their wear and any extra data, never leave the pedestal block.
     */
    static void showArmor(@Nonnull InventoryComponent.Armor worn, @Nullable FounderStatueLoadout loadout) {
        ItemContainer shown = worn.getInventory();
        for (FounderStatueLoadout.Slot slot : FounderStatueLoadout.Slot.values()) {
            short index = (short) slot.ordinal();
            if (index >= shown.getCapacity()) {
                continue;
            }
            ItemStack real = loadout != null ? loadout.get(slot) : null;
            ItemStack current = shown.getItemStack(index);
            String want = real != null ? real.getItemId() : null;
            String have = ItemStack.isEmpty(current) ? null : current.getItemId();
            if (Objects.equals(want, have)) {
                continue;
            }
            try {
                if (want == null) {
                    shown.removeItemStackFromSlot(index);
                } else {
                    shown.setItemStackForSlot(index, new ItemStack(want, 1));
                }
            } catch (RuntimeException e) {
                // An item another mod no longer provides cannot be drawn. It is still safe on the pedestal.
            }
        }
        worn.setOutdatedEquipment(true);
    }

    /**
     * Redraws the armor on a statue that is already standing.
     *
     * @return false when the statue is not in the world right now or was spawned without armor slots, in which case
     *     the caller should respawn it
     */
    public static boolean refreshStatueArmor(
        @Nonnull Store<EntityStore> store,
        @Nullable String statueEntityUuid,
        @Nonnull FounderStatueLoadout loadout
    ) {
        if (statueEntityUuid == null || statueEntityUuid.isBlank()) {
            return false;
        }
        UUID id;
        try {
            id = UUID.fromString(statueEntityUuid.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(id);
        if (ref == null || !ref.isValid()) {
            return false;
        }
        InventoryComponent.Armor worn = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        if (worn == null) {
            return false;
        }
        showArmor(worn, loadout);
        return true;
    }

    /** Same mesh as {@code dynamic} from cosmetics, with only the given animation sets (none for a plain statue). */
    @Nonnull
    private static Model toStaticModel(
        @Nonnull Model dynamic,
        @Nonnull Map<String, ModelAsset.AnimationSet> animationSets
    ) {
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
            animationSets,
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
        FounderMonumentBlock next =
            new FounderMonumentBlock(
                townId,
                statueEntityUuid != null ? statueEntityUuid.toString() : existing.getStatueEntityUuid(),
                mergedSkin,
                mergedLabel,
                mergedRotation.pitch(),
                mergedRotation.yaw(),
                mergedRotation.roll()
            );
        // The armor and pose stay with the pedestal: the new record takes them from the one it replaces.
        existing.moveLoadoutTo(next);
        cs.putComponent(blockRef, FounderMonumentBlock.getComponentType(), next);
    }

    /** Flags the pedestal's saved data as changed so its armor and pose reach the disk with the chunk. */
    public static void markPedestalNeedsSaving(@Nonnull Ref<ChunkStore> blockRef) {
        if (!blockRef.isValid()) {
            return;
        }
        BlockModule.BlockStateInfo info =
            blockRef.getStore().getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) {
            info.markNeedsSaving();
        }
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
