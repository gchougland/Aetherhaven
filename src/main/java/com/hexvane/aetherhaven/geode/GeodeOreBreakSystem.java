package com.hexvane.aetherhaven.geode;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Rare extra geode drop when the player breaks an ore block; spawns as a world item like normal block loot. */
public final class GeodeOreBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final AetherhavenPlugin plugin;

    public GeodeOreBreakSystem(@Nonnull AetherhavenPlugin plugin) {
        super(BreakBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }
        BlockType bt = event.getBlockType();
        if (bt == null || bt == BlockType.EMPTY) {
            return;
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        if (!OreGeodeEligibility.isOreBlockForGeode(bt, cfg)) {
            return;
        }
        double p = cfg.getGeodeDropChancePerOreBreak();
        if (p <= 0.0 || ThreadLocalRandom.current().nextDouble() >= p) {
            return;
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (store.getComponent(ref, Player.getComponentType()) == null) {
            return;
        }
        Vector3i pos = event.getTargetBlock();
        Vector3d dropPosition = new Vector3d(pos.x + 0.5, pos.y, pos.z + 0.5);
        ItemStack stack = new ItemStack(AetherhavenConstants.ITEM_GEODE, 1);
        // Must queue spawns: BreakBlockEvent runs while the store is processing; direct addEntities throws.
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(commandBuffer, List.of(stack), dropPosition, Vector3f.ZERO);
        if (holders.length > 0) {
            commandBuffer.addEntities(holders, AddReason.SPAWN);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
