package com.hexvane.aetherhaven.loot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.config.BreakableContainersConfig;
import com.hexvane.aetherhaven.config.BreakableContainersGoldConfig;
import com.hexvane.aetherhaven.economy.BreakableContainerEligibility;
import com.hexvane.aetherhaven.geode.OreGeodeEligibility;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Extra loot when a player breaks certain blocks: rare geodes from ore, and weighted gold from smashable world
 * containers (crates, barrels, pots, sacks, coffins).
 */
public final class PlayerBlockBreakBonusSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final AetherhavenPlugin plugin;

    public PlayerBlockBreakBonusSystem(@Nonnull AetherhavenPlugin plugin) {
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
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<ItemStack> bonus = new ArrayList<>(2);

        if (OreGeodeEligibility.isOreBlockForGeode(bt, cfg)) {
            double geodeChance = cfg.getGeodeDropChancePerOreBreak();
            if (geodeChance > 0.0 && rnd.nextDouble() < geodeChance) {
                bonus.add(new ItemStack(AetherhavenConstants.ITEM_GEODE, 1));
            }
        }

        BreakableContainersConfig containers = cfg.getBreakableContainers();
        if (containers.isEnabled() && BreakableContainerEligibility.isBreakableContainer(bt)) {
            World world = store.getExternalData().getWorld();
            boolean creative = world.getWorldConfig().getGameMode() == GameMode.Creative;
            if (!creative || containers.isApplyInCreative()) {
                BreakableContainersGoldConfig gold = containers.getGold();
                if (gold.isItemRegistered()) {
                    int coins = gold.rollQuantity(rnd);
                    if (coins > 0) {
                        bonus.add(new ItemStack(gold.getItemId(), coins));
                    }
                }
            }
        }

        if (bonus.isEmpty()) {
            return;
        }
        Vector3i pos = event.getTargetBlock();
        Vector3d dropPosition = new Vector3d(pos.x + 0.5, pos.y, pos.z + 0.5);
        // Must queue spawns: BreakBlockEvent runs while the store is processing; direct addEntities throws.
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(commandBuffer, bonus, dropPosition, Vector3f.ZERO);
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
