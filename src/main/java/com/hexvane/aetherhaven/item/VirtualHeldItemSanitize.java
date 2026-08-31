package com.hexvane.aetherhaven.item;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ChangeActiveSlotInteraction;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps virtual {@link ItemBase} clones usable as held items on the integrated client.
 *
 * <p>After Hytale Update 6, Use / hotbar scroll prediction reads the virtual def from {@code UpdateItems}. Clones that
 * still look like placeable blocks (inherited {@code blockId}) or that only have unarmed {@code Block_Primary} with no
 * block steal clicks; clones with no model fail to resolve held interactions at all.
 */
public final class VirtualHeldItemSanitize {
    /** Fallback held model when a clone drops its placeable block id (crate parent / block items). */
    private static final String FALLBACK_HELD_MODEL = "Items/CreativeTools/EditorTool.blockymodel";

    private VirtualHeldItemSanitize() {}

    /**
     * Copy interactions from {@code original}, force SwapFrom for hotbar scroll-off, clear placeable-block fields, drop
     * leftover Block_Primary/Secondary when there is no block, and ensure a held model exists.
     */
    public static void applyHeldItemClone(@Nullable ItemBase original, @Nonnull ItemBase clone) {
        Object2IntOpenHashMap<InteractionType> copy = new Object2IntOpenHashMap<>();
        Map<InteractionType, Integer> src = original != null ? original.interactions : null;
        if (src != null && !src.isEmpty()) {
            copy.putAll(src);
        } else if (clone.interactions != null && !clone.interactions.isEmpty()) {
            copy.putAll(clone.interactions);
        }

        boolean hadBlock = clone.blockId != 0 || (original != null && original.blockId != 0);
        // 0 = no placeable block on the wire (see Item.toPacket leaving blockId unset when null).
        clone.blockId = 0;
        clone.builderToolData = null;
        clone.blockSelectorTool = null;

        // Unarmed Item/Block profiles inject Block_Primary even when the item has no BlockType (plot tokens).
        stripBlockPlacementInteractions(copy);
        ensureHeldModel(original, clone, hadBlock);

        int swapId = RootInteraction.getAssetMap().getIndex(ChangeActiveSlotInteraction.DEFAULT_ROOT.getId());
        if (swapId < 0) {
            swapId = RootInteraction.getRootInteractionIdOrUnknown(ChangeActiveSlotInteraction.DEFAULT_ROOT.getId());
        }
        if (swapId >= 0) {
            copy.put(InteractionType.SwapFrom, swapId);
        }
        clone.interactions = copy;

        if (clone.playerAnimationsId == null
            || clone.playerAnimationsId.isBlank()
            || "Block".equalsIgnoreCase(clone.playerAnimationsId)) {
            clone.playerAnimationsId = "Item";
        }
    }

    /**
     * Unarmed {@code Item}/{@code Block} profiles inject {@code Block_Primary}/{@code Block_Secondary}. Those roots
     * require a real block id; on virtual clones with {@code blockId == 0} they lock the hotbar in singleplayer.
     */
    private static void stripBlockPlacementInteractions(@Nonnull Object2IntOpenHashMap<InteractionType> interactions) {
        int blockPrimary = RootInteraction.getAssetMap().getIndex("Block_Primary");
        int blockSecondary = RootInteraction.getAssetMap().getIndex("Block_Secondary");
        if (blockPrimary >= 0 && interactions.getOrDefault(InteractionType.Primary, Integer.MIN_VALUE) == blockPrimary) {
            interactions.remove(InteractionType.Primary);
        }
        if (blockSecondary >= 0
            && interactions.getOrDefault(InteractionType.Secondary, Integer.MIN_VALUE) == blockSecondary) {
            interactions.remove(InteractionType.Secondary);
        }
    }

    private static void ensureHeldModel(@Nullable ItemBase original, @Nonnull ItemBase clone, boolean hadBlock) {
        if (clone.model != null && !clone.model.isBlank()) {
            return;
        }
        if (original != null && original.model != null && !original.model.isBlank()) {
            clone.model = original.model;
            return;
        }
        // Former placeable-block items (crate parent) used the block mesh; give them a neutral held model.
        if (hadBlock) {
            clone.model = FALLBACK_HELD_MODEL;
        }
    }
}
