package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.builtin.crafting.system.BenchSystems;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;

/**
 * Plot blueprints and unified plot tokens carry per-building metadata for their name and unlock target.
 * The salvaging bench accepts them by item id, but Hytale only completes a recipe when the stack metadata
 * matches exactly. Strip instance metadata in the bench input so the normal salvage recipe can run.
 */
public final class PlotBlueprintSalvageBenchSystem extends EntityTickingSystem<ChunkStore> {
    private static final String SALVAGE_BENCH_ID = "Salvagebench";

    @Nonnull
    private final Set<Dependency<ChunkStore>> dependencies =
        Set.of(new SystemDependency<>(Order.BEFORE, BenchSystems.ProcessingBenchTick.class));

    @Nonnull
    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<ChunkStore> getQuery() {
        return ProcessingBenchBlock.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
        @Nonnull Store<ChunkStore> store,
        @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        ProcessingBenchBlock benchBlock = archetypeChunk.getComponent(index, ProcessingBenchBlock.getComponentType());
        if (benchBlock == null) {
            return;
        }

        Bench bench = benchBlock.getBench();
        if (bench == null || !SALVAGE_BENCH_ID.equals(bench.getId())) {
            return;
        }

        normalizeSalvageInputs(benchBlock.getInputContainer());
    }

    static void normalizeSalvageInputs(@Nullable ItemContainer input) {
        if (input == null) {
            return;
        }
        for (short slot = 0; slot < input.getCapacity(); slot++) {
            ItemStack stack = input.getItemStack(slot);
            ItemStack normalized = normalizedForSalvage(stack);
            if (normalized != null) {
                input.setItemStackForSlot(slot, normalized);
            }
        }
    }

    @Nullable
    static ItemStack normalizedForSalvage(@Nullable ItemStack stack) {
        if (!shouldStripSalvageMetadata(stack)) {
            return null;
        }
        return new ItemStack(stack.getItemId(), stack.getQuantity());
    }

    static boolean shouldStripSalvageMetadata(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack) || !hasSalvageMetadata(stack)) {
            return false;
        }
        String itemId = stack.getItemId();
        return AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE.equals(itemId)
            || AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(itemId);
    }

    private static boolean hasSalvageMetadata(@Nonnull ItemStack stack) {
        BsonDocument meta = stack.getMetadata();
        return meta != null && !meta.isEmpty();
    }
}
