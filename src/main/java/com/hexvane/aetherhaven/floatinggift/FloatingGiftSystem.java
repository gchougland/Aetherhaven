package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.jewelry.LootChestBonusApplier;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FloatingGiftSystem extends EntityTickingSystem<EntityStore> {
    public static final String CHEST_BLOCK_ID = "Furniture_Christmas_Chest_Small_White";

    /** Same semantics as PathCementService silent clears: replace block without drops/particles when removing tall grass etc. */
    private static final int SET_BLOCK_SILENT = 10;

    /** Decorative plant grasses only — does not touch {@code Soil_Grass*} terrain blocks. */
    private static final int CLEAR_PLANT_GRASS_COLUMN_MAX_UP = 8;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            FloatingGiftComponent.getComponentType(),
            TransformComponent.getComponentType(),
            Velocity.getComponentType(),
            HeadRotation.getComponentType(),
            BoundingBox.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        FloatingGiftComponent gift = archetypeChunk.getComponent(index, FloatingGiftComponent.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        Velocity velocity = archetypeChunk.getComponent(index, Velocity.getComponentType());
        HeadRotation head = archetypeChunk.getComponent(index, HeadRotation.getComponentType());
        BoundingBox boundingBox = archetypeChunk.getComponent(index, BoundingBox.getComponentType());
        if (gift == null || transform == null || velocity == null || head == null || boundingBox == null || ref == null) {
            return;
        }
        gift.addLifeSeconds(dt);
        if (gift.getLifeSeconds() >= cfg.getFloatingGiftMaxLifeSeconds()) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }

        switch (gift.getState()) {
            case FLOATING -> tickFloating(dt, store, commandBuffer, ref, gift, transform, velocity, boundingBox);
            case POPPING, FALLING -> tickFalling(dt, store, commandBuffer, ref, gift, transform, velocity);
        }
    }

    /**
     * World-space vertical bob (blocks). Float.blockyanim stays rotation-only so this is the only Y motion while cruising.
     */
    private static final double FLOAT_BOB_AMPLITUDE_BLOCKS = 0.65;

    private static final double FLOAT_BOB_RAD_PER_SEC = Math.PI * 2.0 / 4.25;

    /** Re-send Emote float with stop+play so the client restarts the procedural loop reliably. */
    private static final double FLOAT_CLIP_RETRIGGER_SEC = 2.25;

    /**
     * Vanilla projectile impacts only queue {@link com.hypixel.hytale.server.core.modules.entity.damage.Damage} on
     * {@link com.hypixel.hytale.server.core.entity.LivingEntity} targets ({@code ProjectileComponent.onProjectileHitEvent}).
     * Floating gifts are model entities, so we overlap-test {@link Projectile} / {@link ProjectileComponent} entities
     * against the gift AABB (plus {@link FloatingGiftComponent#getProjectileHitRadius()}).
     * <p>
     * Combat staff orbs use {@link com.hypixel.hytale.server.core.modules.entity.component.Intangible}
     * ({@code LaunchProjectileInteraction}) and are omitted from the tangible KD-tree — those are collected via
     * {@link EntityModule#getNetworkSendableSpatialResourceType()} instead.
     */
    private static void tickFloating(
        float dt,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull FloatingGiftComponent gift,
        @Nonnull TransformComponent transform,
        @Nonnull Velocity velocity,
        @Nonnull BoundingBox giftBoundingBox
    ) {
        if (gift.consumeDeferredSpawnFloatAnimation()) {
            FloatingGiftAnimationHelper.playAnimation(store, ref, AnimationSlot.Emote, FloatingGiftSpawnService.FLOAT_ANIMATION);
        }
        gift.addFloatClipRetriggerAccum(dt);
        if (gift.consumeFloatClipRetriggerAccum(FLOAT_CLIP_RETRIGGER_SEC)) {
            FloatingGiftAnimationHelper.restartAnimation(store, ref, AnimationSlot.Emote, FloatingGiftSpawnService.FLOAT_ANIMATION);
        }
        gift.addAmbientCueAccum(dt);
        if (gift.consumeAmbientCueAccum(FloatingGiftSounds.NEARBY_AMBIENT_INTERVAL_SEC)) {
            FloatingGiftSounds.playNearbyAmbient3d(transform.getPosition(), store);
        }
        double speed = gift.getSpeedBlocksPerSec();
        double dx = gift.getDirX();
        double dz = gift.getDirZ();
        velocity.set(dx * speed, 0.0, dz * speed);
        Vector3d p = transform.getPosition();
        double nx = p.x + dx * speed * dt;
        double nz = p.z + dz * speed * dt;
        double bob = FLOAT_BOB_AMPLITUDE_BLOCKS * Math.sin(FLOAT_BOB_RAD_PER_SEC * gift.getLifeSeconds());
        double ny = gift.getAnchorY() + bob;
        transform.setPosition(new Vector3d(nx, ny, nz));
        // Do not refresh head rotation every tick — client animation drivers can glitch procedural clips.
        tryProjectileProximityPop(commandBuffer, store, ref, gift, transform, velocity, giftBoundingBox);
    }

    private static void tryProjectileProximityPop(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> giftRef,
        @Nonnull FloatingGiftComponent gift,
        @Nonnull TransformComponent giftTransform,
        @Nonnull Velocity giftVelocity,
        @Nonnull BoundingBox giftBb
    ) {
        Box hitVolume = expandUniform(worldBounds(giftTransform.getPosition(), giftBb), gift.getProjectileHitRadius());
        double mx = (hitVolume.min.x + hitVolume.max.x) * 0.5;
        double my = (hitVolume.min.y + hitVolume.max.y) * 0.5;
        double mz = (hitVolume.min.z + hitVolume.max.z) * 0.5;
        double dx = hitVolume.max.x - hitVolume.min.x;
        double dy = hitVolume.max.y - hitVolume.min.y;
        double dz = hitVolume.max.z - hitVolume.min.z;
        double collectRadius = 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz) + 4.0;

        SpatialResource<Ref<EntityStore>, EntityStore> tangible =
            store.getResource(CollisionModule.get().getTangibleEntitySpatialResourceType());
        SpatialResource<Ref<EntityStore>, EntityStore> networkSendable =
            store.getResource(EntityModule.get().getNetworkSendableSpatialResourceType());
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        Set<Ref<EntityStore>> candidates = new HashSet<>();
        nearby.clear();
        tangible.getSpatialStructure().collect(new Vector3d(mx, my, mz), collectRadius, nearby);
        addProjectileCandidates(store, candidates, nearby);
        nearby.clear();
        networkSendable.getSpatialStructure().collect(new Vector3d(mx, my, mz), collectRadius, nearby);
        addProjectileCandidates(store, candidates, nearby);

        for (Ref<EntityStore> other : candidates) {
            if (other == null || !other.isValid() || other.equals(giftRef)) {
                continue;
            }
            Archetype<EntityStore> arch = store.getArchetype(other);
            if (!isProjectileEntity(arch)) {
                continue;
            }
            TransformComponent pTransform = store.getComponent(other, TransformComponent.getComponentType());
            BoundingBox pBb = store.getComponent(other, BoundingBox.getComponentType());
            if (pTransform == null || pBb == null) {
                continue;
            }
            Box pWorld = worldBounds(pTransform.getPosition(), pBb);
            if (aabbOverlap(hitVolume, pWorld)) {
                FloatingGiftTriggers.beginPop(commandBuffer, giftRef, gift, giftTransform, giftVelocity);
                return;
            }
        }
    }

    private static void addProjectileCandidates(
        @Nonnull Store<EntityStore> store,
        @Nonnull Set<Ref<EntityStore>> out,
        @Nonnull List<Ref<EntityStore>> spatialHits
    ) {
        for (int i = 0; i < spatialHits.size(); i++) {
            Ref<EntityStore> r = spatialHits.get(i);
            if (r == null || !r.isValid()) {
                continue;
            }
            Archetype<EntityStore> arch = store.getArchetype(r);
            if (isProjectileEntity(arch)) {
                out.add(r);
            }
        }
    }

    private static boolean isProjectileEntity(@Nonnull Archetype<EntityStore> archetype) {
        return archetype.contains(Projectile.getComponentType()) || archetype.contains(ProjectileComponent.getComponentType());
    }

    @Nonnull
    private static Box worldBounds(@Nonnull Vector3d origin, @Nonnull BoundingBox bb) {
        Box local = bb.getBoundingBox();
        return new Box(
            origin.x + local.min.x,
            origin.y + local.min.y,
            origin.z + local.min.z,
            origin.x + local.max.x,
            origin.y + local.max.y,
            origin.z + local.max.z
        );
    }

    @Nonnull
    private static Box expandUniform(@Nonnull Box b, double pad) {
        return new Box(
            b.min.x - pad,
            b.min.y - pad,
            b.min.z - pad,
            b.max.x + pad,
            b.max.y + pad,
            b.max.z + pad
        );
    }

    private static boolean aabbOverlap(@Nonnull Box a, @Nonnull Box b) {
        return a.min.x <= b.max.x
            && a.max.x >= b.min.x
            && a.min.y <= b.max.y
            && a.max.y >= b.min.y
            && a.min.z <= b.max.z
            && a.max.z >= b.min.z;
    }

    private static void tickFalling(
        float dt,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull FloatingGiftComponent gift,
        @Nonnull TransformComponent transform,
        @Nonnull Velocity velocity
    ) {
        gift.addPopSeconds(dt);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            AetherhavenPluginConfig cfg = plugin.getConfig().get();
            if (!gift.isPopHoldClipApplied() && gift.getPopSeconds() >= cfg.getFloatingGiftPopHoldLatchSeconds()) {
                FloatingGiftAnimationHelper.playAnimation(store, ref, AnimationSlot.Action, FloatingGiftSpawnService.POP_HOLD_ANIMATION);
                gift.markPopHoldClipApplied();
            }
        }
        double fall = gift.getFallBlocksPerSec();
        velocity.set(0.0, -fall, 0.0);
        Vector3d p = transform.getPosition();
        Vector3d next = new Vector3d(p.x, p.y - fall * dt, p.z);
        transform.setPosition(next);
        World world = store.getExternalData().getWorld();
        int bx = (int) Math.floor(next.x);
        int by = (int) Math.floor(next.y);
        int bz = (int) Math.floor(next.z);
        BlockType below = world.getBlockType(bx, by - 1, bz);
        if (below != null && below != BlockType.EMPTY) {
            spawnRewardChest(world, bx, by, bz);
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    private static void spawnRewardChest(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }
        // Decorative plants in the footprint make placeBlock fail or drop loot as items — silent-clear scatter plants only.
        clearPlantGrassDecorationColumn(world, chunk, x, y, z);
        // Use placeBlock + settings 10 (same as player placement): direct setBlock(settings=3) sets bit 2 and skips
        // cloning BlockEntity components, so containers never get ItemContainerBlock and cannot open.
        RotationTuple rot = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);
        if (!chunk.placeBlock(x, y, z, CHEST_BLOCK_ID, rot, 10, false)) {
            return;
        }
        if (world.getBlockType(x, y, z) == BlockType.EMPTY) {
            return;
        }
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(x, y, z);
        if (blockRef == null || !blockRef.isValid()) {
            return;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        ItemContainerBlock chest = cs.getComponent(blockRef, ItemContainerBlock.getComponentType());
        if (chest == null || chest.getItemContainer() == null) {
            return;
        }
        fillChestLoot(chest.getItemContainer());
    }

    private static boolean isPlantGrassDecoration(@Nullable BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        String id = blockType.getId();
        if (id == null) {
            return false;
        }
        return id.contains("Plant_Grass")
            || id.contains("Plant_Seaweed_Grass")
            || id.contains("Plant_Bush");
    }

    /**
     * Clears decorative grass in the chest column. Skips air voxels so stacked grass above an empty chest cell is still
     * removed (old logic broke on the first air block and left grass that caused {@code placeBlock} to break/drop).
     * Uses {@link WorldChunk#setBlock} with silent settings so grasses do not break into dropped items.
     */
    private static void clearPlantGrassDecorationColumn(
        @Nonnull World world,
        @Nonnull WorldChunk initialChunk,
        int x,
        int startY,
        int z
    ) {
        for (int py = startY; py < startY + CLEAR_PLANT_GRASS_COLUMN_MAX_UP && py < 320; py++) {
            WorldChunk ch =
                py == startY ? initialChunk : world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (ch == null) {
                break;
            }
            BlockType bt = BlockType.getAssetMap().getAsset(ch.getBlock(x, py, z));
            if (bt == null || bt == BlockType.EMPTY) {
                continue;
            }
            if (!isPlantGrassDecoration(bt)) {
                break;
            }
            ch.setBlock(x, py, z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, SET_BLOCK_SILENT);
        }
    }

    private static void fillChestLoot(@Nonnull SimpleItemContainer inv) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        ItemStack token = FloatingGiftLootFiles.loadTable(plugin).rollStack();
        if (token != null && !ItemStack.isEmpty(token)) {
            inv.addItemStackToSlot((short) 4, token);
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LootChestBonusApplier.tryInjectGoldCoinsToContainer(inv, plugin.getConfig().get(), rnd, true);
    }

    public static int countActiveGifts(@Nonnull Store<EntityStore> store) {
        final int[] count = new int[] {0};
        store.forEachChunk(FloatingGiftComponent.getComponentType(), (chunk, cb) -> {
            count[0] += chunk.size();
        });
        return count[0];
    }
}
