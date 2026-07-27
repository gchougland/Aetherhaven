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

/**
 * Plot blueprints carry per-building metadata for their name and unlock target. The salvaging bench
 * accepts them by item id, but Hytale only completes a recipe when the stack metadata matches exactly.
 * Strip instance metadata in the bench input so the normal salvage recipe can run.
 */
public final class PlotBlueprintSalvageBenchSystem extends EntityTickingSystem<ChunkStore> {
    private static final String SALVAGE_BENCH_ID = "Salvagebench";

    @Nonnull
    private static final ItemStack BARE_BLUEPRINT = new ItemStack(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, 1);

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

        normalizeBlueprintStacks(benchBlock.getInputContainer());
    }

    static void normalizeBlueprintStacks(@Nullable ItemContainer input) {
        if (input == null) {
            return;
        }
        for (short slot = 0; slot < input.getCapacity(); slot++) {
            ItemStack stack = input.getItemStack(slot);
            if (needsSalvageNormalization(stack)) {
                input.setItemStackForSlot(slot, bareBlueprint(stack.getQuantity()));
            }
        }
    }

    private static boolean needsSalvageNormalization(@Nullable ItemStack stack) {
        return !ItemStack.isEmpty(stack)
            && AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE.equals(stack.getItemId())
            && !BARE_BLUEPRINT.isEquivalentType(stack);
    }

    @Nonnull
    private static ItemStack bareBlueprint(int quantity) {
        return new ItemStack(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, quantity);
    }
}
