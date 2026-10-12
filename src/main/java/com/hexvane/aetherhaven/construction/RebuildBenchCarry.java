package com.hexvane.aetherhaven.construction;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.shopspot.ShopSpotItemDelivery;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Carries workbench upgrades across a rebuild. Pasting a building again replaces every bench with a new tier one
 * bench, so the tiers and the materials paid for them are lifted off the old benches first and set on the new ones.
 *
 * <p>Matching, in order: the same kind of bench in the same prefab cell keeps its own tier. A bench left over is given
 * to the nearest unclaimed bench of the same kind, best tiers first. When the new building has fewer benches of a
 * kind, the lowest tiers have nowhere to go: their upgrade materials are handed back and that tier is not kept.
 * Benches are never matched across kinds, and a bench with no saved partner stays as pasted.
 */
public final class RebuildBenchCarry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private RebuildBenchCarry() {}

    /** An upgraded bench before the rebuild. Cells are prefab-local, so they survive a move or a turn. */
    public record Saved<T>(@Nonnull String benchId, int x, int y, int z, int tier, T materials) {}

    /** A bench standing after the rebuild. */
    public record Slot(@Nonnull String benchId, int x, int y, int z) {}

    public record Plan<T>(@Nonnull Map<Slot, Saved<T>> restore, @Nonnull List<Saved<T>> surplus) {}

    @Nonnull
    public static <T> Plan<T> plan(@Nonnull Collection<Saved<T>> saved, @Nonnull Collection<Slot> rebuilt) {
        Map<Slot, Saved<T>> restore = new LinkedHashMap<>();
        List<Slot> open = new ArrayList<>(rebuilt);
        List<Saved<T>> waiting = new ArrayList<>();
        for (Saved<T> bench : saved) {
            Slot same = null;
            for (Slot slot : open) {
                if (slot.benchId().equals(bench.benchId()) && distanceSquared(slot, bench) == 0L) {
                    same = slot;
                    break;
                }
            }
            if (same == null) {
                waiting.add(bench);
            } else {
                open.remove(same);
                restore.put(same, bench);
            }
        }
        waiting.sort(
            Comparator.comparingInt((Saved<T> b) -> -b.tier())
                .thenComparingInt(Saved::y)
                .thenComparingInt(Saved::x)
                .thenComparingInt(Saved::z)
        );
        open.sort(Comparator.comparingInt(Slot::y).thenComparingInt(Slot::x).thenComparingInt(Slot::z));
        List<Saved<T>> surplus = new ArrayList<>();
        for (Saved<T> bench : waiting) {
            Slot nearest = null;
            long best = Long.MAX_VALUE;
            for (Slot slot : open) {
                long distance = distanceSquared(slot, bench);
                if (slot.benchId().equals(bench.benchId()) && distance < best) {
                    nearest = slot;
                    best = distance;
                }
            }
            if (nearest == null) {
                surplus.add(bench);
            } else {
                open.remove(nearest);
                restore.put(nearest, bench);
            }
        }
        return new Plan<>(restore, surplus);
    }

    private static long distanceSquared(@Nonnull Slot slot, @Nonnull Saved<?> bench) {
        long dx = slot.x() - bench.x();
        long dy = slot.y() - bench.y();
        long dz = slot.z() - bench.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Lifts the upgrades off the benches that are about to be replaced. The old benches are left with no stored
     * materials, so the engine has nothing to drop when it removes them and the materials cannot be gained twice.
     * Benches still at tier one with nothing stored are skipped.
     */
    @Nonnull
    public static List<Saved<ItemStack[]>> take(@Nonnull Map<Slot, BenchBlock> benches) {
        List<Saved<ItemStack[]>> out = new ArrayList<>();
        for (Map.Entry<Slot, BenchBlock> entry : benches.entrySet()) {
            BenchBlock bench = entry.getValue();
            ItemStack[] materials = bench.getUpgradeItems() != null ? bench.getUpgradeItems() : ItemStack.EMPTY_ARRAY;
            if (bench.getTierLevel() <= 1 && materials.length == 0) {
                continue;
            }
            Slot slot = entry.getKey();
            out.add(
                new Saved<>(
                    slot.benchId(),
                    slot.x(),
                    slot.y(),
                    slot.z(),
                    bench.getTierLevel(),
                    Arrays.copyOf(materials, materials.length)
                )
            );
            bench.setUpgradeItems(ItemStack.EMPTY_ARRAY);
        }
        return out;
    }

    /**
     * Sets the saved tiers and materials on the rebuilt benches.
     *
     * @param tierExists whether the rebuilt bench at a slot has that tier; a bench without it gets its best lower tier
     * @param onRestored called for each bench that changed, with the tier it ended on
     * @return benches that found no partner; their materials go back to the player
     */
    @Nonnull
    public static List<Saved<ItemStack[]>> give(
        @Nonnull List<Saved<ItemStack[]>> saved,
        @Nonnull Map<Slot, BenchBlock> rebuilt,
        @Nonnull BiPredicate<Slot, Integer> tierExists,
        @Nonnull BiConsumer<Slot, Integer> onRestored
    ) {
        Plan<ItemStack[]> plan = plan(saved, rebuilt.keySet());
        for (Map.Entry<Slot, Saved<ItemStack[]>> entry : plan.restore().entrySet()) {
            Slot slot = entry.getKey();
            Saved<ItemStack[]> from = entry.getValue();
            BenchBlock bench = rebuilt.get(slot);
            if (bench == null) {
                continue;
            }
            int tier = from.tier();
            while (tier > 1 && !tierExists.test(slot, tier)) {
                tier--;
            }
            if (from.materials().length > 0) {
                List<ItemStack> materials = new ArrayList<>(Arrays.asList(from.materials()));
                bench.addUpgradeItems(materials);
            }
            if (tier > bench.getTierLevel()) {
                bench.setTierLevel(tier);
            }
            onRestored.accept(slot, bench.getTierLevel());
        }
        return plan.surplus();
    }

    /** World thread. Reads the benches standing on the given prefab cells and lifts their upgrades. */
    @Nonnull
    public static List<Saved<ItemStack[]>> capture(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull List<PendingBlock> prefabCells
    ) {
        Map<Slot, Vector3i> cells = new LinkedHashMap<>();
        Map<Slot, BenchBlock> benches = benchesOn(world, anchor, yaw, prefabCells, cells);
        List<Saved<ItemStack[]>> saved = take(benches);
        for (Saved<ItemStack[]> bench : saved) {
            markNeedsSaving(world, cells.get(new Slot(bench.benchId(), bench.x(), bench.y(), bench.z())));
        }
        return saved;
    }

    /**
     * World thread, after the paste. Puts the saved upgrades on the new benches and hands the materials of any bench
     * without a partner to {@code actor}, or drops them at the building when that player is gone.
     */
    public static void restore(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<Saved<ItemStack[]>> saved,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull List<PendingBlock> prefabCells,
        @Nullable Ref<EntityStore> actor
    ) {
        if (saved.isEmpty()) {
            return;
        }
        Map<Slot, Vector3i> cells = new LinkedHashMap<>();
        Map<Slot, BenchBlock> rebuilt = benchesOn(world, anchor, yaw, prefabCells, cells);
        List<Saved<ItemStack[]>> surplus =
            give(
                saved,
                rebuilt,
                (slot, tier) -> tierExists(world, cells.get(slot), tier),
                (slot, tier) -> applyTierState(world, cells.get(slot), rebuilt.get(slot))
            );
        List<ItemStack> returned = new ArrayList<>();
        for (Saved<ItemStack[]> bench : surplus) {
            returned.addAll(Arrays.asList(bench.materials()));
        }
        LOGGER.atInfo().log(
            "Rebuild at %s kept %s bench upgrade(s); %s had no matching bench (%s material stack(s) returned)",
            anchor,
            saved.size() - surplus.size(),
            surplus.size(),
            returned.size()
        );
        if (returned.isEmpty()) {
            return;
        }
        Player player = actor != null && actor.isValid() ? store.getComponent(actor, Player.getComponentType()) : null;
        if (player != null) {
            for (ItemStack stack : returned) {
                ShopSpotItemDelivery.grantAtShop(player, actor, store, null, stack, anchor);
            }
        } else {
            ShopSpotItemDelivery.dropOnGround(store, returned, new Vector3d(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5));
        }
    }

    @Nonnull
    private static Map<Slot, BenchBlock> benchesOn(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull List<PendingBlock> prefabCells,
        @Nonnull Map<Slot, Vector3i> worldCells
    ) {
        Map<Slot, BenchBlock> out = new LinkedHashMap<>();
        Store<ChunkStore> chunks = world.getChunkStore().getStore();
        for (PendingBlock cell : prefabCells) {
            int x = anchor.x + cell.x();
            int y = anchor.y + cell.y();
            int z = anchor.z + cell.z();
            BlockSection section = ChunkSectionBlockUtil.blockSectionAt(world, x, y, z);
            // Only a bench's own cell holds its state; the rest of a wide bench is filler.
            if (section == null || section.get(x, y, z) == BlockType.EMPTY_ID || section.getFiller(x, y, z) != 0) {
                continue;
            }
            Ref<ChunkStore> ref = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
            BenchBlock bench = ref != null && ref.isValid() ? chunks.getComponent(ref, BenchBlock.getComponentType()) : null;
            String benchId = bench != null ? benchId(ChunkSectionBlockUtil.blockType(world, x, y, z)) : null;
            if (benchId == null) {
                continue;
            }
            Vector3i local = PrefabLocalOffset.inverseRotateWorldDelta(yaw, cell.x(), cell.y(), cell.z());
            Slot slot = new Slot(benchId, local.x, local.y, local.z);
            out.put(slot, bench);
            worldCells.put(slot, new Vector3i(x, y, z));
        }
        return out;
    }

    /** An upgraded bench is a tier variant of its block; the bench kind comes from the base block. */
    @Nullable
    private static String benchId(@Nullable BlockType block) {
        Bench bench = benchOf(block);
        return bench != null ? bench.getId() : null;
    }

    @Nullable
    private static Bench benchOf(@Nullable BlockType block) {
        if (block == null) {
            return null;
        }
        Bench base = BenchBlock.getBaseBlockType(block).getBench();
        return base != null ? base : block.getBench();
    }

    private static boolean tierExists(@Nonnull World world, @Nullable Vector3i cell, int tier) {
        Bench bench = cell != null ? benchOf(ChunkSectionBlockUtil.blockType(world, cell.x, cell.y, cell.z)) : null;
        return bench != null && bench.getTierLevel(tier) != null;
    }

    /** Same steps as a normal bench upgrade: save, show the tier's block state, and resize a processing bench. */
    private static void applyTierState(@Nonnull World world, @Nullable Vector3i cell, @Nullable BenchBlock bench) {
        if (cell == null || bench == null) {
            return;
        }
        Store<ChunkStore> chunks = world.getChunkStore().getStore();
        Ref<ChunkStore> ref = ChunkSectionBlockUtil.blockEntityRefAt(world, cell.x, cell.y, cell.z);
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(cell.x, cell.y, cell.z);
        BlockSection section = ChunkSectionBlockUtil.blockSectionAt(world, cell.x, cell.y, cell.z);
        BlockType block = ChunkSectionBlockUtil.blockType(world, cell.x, cell.y, cell.z);
        if (ref == null || !ref.isValid() || sectionRef == null || !sectionRef.isValid() || section == null || block == null) {
            return;
        }
        BlockModule.BlockStateInfo info = chunks.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) {
            info.markNeedsSaving();
        }
        if (bench.getTierLevel() <= 1) {
            return;
        }
        BlockOperations.setBlockInteractionState(
            world.getChunkStore(),
            sectionRef,
            cell.x,
            cell.y,
            cell.z,
            BenchBlock.getBaseBlockType(block),
            bench.getTierStateName(),
            true
        );
        ProcessingBenchBlock processing = chunks.getComponent(ref, ProcessingBenchBlock.getComponentType());
        if (processing != null && info != null) {
            processing.setupSlots(
                world,
                bench,
                info,
                cell.x,
                cell.y,
                cell.z,
                block,
                section.getRotationIndex(cell.x, cell.y, cell.z)
            );
        }
    }

    private static void markNeedsSaving(@Nonnull World world, @Nullable Vector3i cell) {
        if (cell == null) {
            return;
        }
        Ref<ChunkStore> ref = ChunkSectionBlockUtil.blockEntityRefAt(world, cell.x, cell.y, cell.z);
        if (ref == null || !ref.isValid()) {
            return;
        }
        BlockModule.BlockStateInfo info =
            world.getChunkStore().getStore().getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) {
            info.markNeedsSaving();
        }
    }
}
