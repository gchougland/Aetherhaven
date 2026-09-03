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
 * Virtual {@link ItemBase} clones are icon/id overlays of a real item. Keep {@code blockId}, {@code model}, and
 * {@code texture} intact so the held mesh resolves (crate parent uses the block mesh; plot tokens use their model).
 *
 * <p>Interactions are copied from the real item verbatim so the client predicts exactly the root interactions the
 * server will run. A {@link InteractionType#SwapFrom} fallback is only filled in when the real item declares none, and
 * unarmed {@code Block_Primary}/{@code Block_Secondary} are dropped when the clone has no placeable block.
 */
public final class VirtualHeldItemSanitize {
    private VirtualHeldItemSanitize() {}

    /**
     * Deep-copies interactions onto {@code clone}, fills in a scroll-off {@code SwapFrom} when the real item has none,
     * and strips block-place roots when {@code blockId == 0}. Does not mutate model, texture, or block id.
     */
    public static void applyHeldItemClone(@Nullable ItemBase original, @Nonnull ItemBase clone) {
        // Item.toPacket() uses Object2IntOpenHashMap; keep that wire type (EnumMap breaks client interaction lookup).
        Object2IntOpenHashMap<InteractionType> copy = new Object2IntOpenHashMap<>();
        Map<InteractionType, Integer> src = original != null ? original.interactions : null;
        if (src != null && !src.isEmpty()) {
            copy.putAll(src);
        } else if (clone.interactions != null && !clone.interactions.isEmpty()) {
            copy.putAll(clone.interactions);
        }

        if (clone.blockId == 0) {
            stripBlockPlacementInteractions(copy);
        }

        // Overriding an existing SwapFrom would make the client run a different root than the server, which desyncs
        // the chain, so only supply one when the real item declares none.
        if (!copy.containsKey(InteractionType.SwapFrom)) {
            int swapId = RootInteraction.getAssetMap().getIndex(ChangeActiveSlotInteraction.DEFAULT_ROOT.getId());
            if (swapId < 0) {
                // Same fallback Item.toPacket() uses when the generated Default_Swap is not indexed yet.
                swapId = RootInteraction.getRootInteractionIdOrUnknown(ChangeActiveSlotInteraction.DEFAULT_ROOT.getId());
            }
            if (swapId >= 0) {
                copy.put(InteractionType.SwapFrom, swapId);
            }
        }
        clone.interactions = copy;
    }

    /**
     * Unarmed {@code Item}/{@code Block} profiles inject {@code Block_Primary}/{@code Block_Secondary}. Those roots
     * expect a real placeable block; on model-only virtuals ({@code blockId == 0}) they lock hotbar scroll in
     * singleplayer prediction.
     */
    private static void stripBlockPlacementInteractions(@Nonnull Object2IntOpenHashMap<InteractionType> interactions) {
        int blockPrimary = RootInteraction.getAssetMap().getIndex("Block_Primary");
        int blockSecondary = RootInteraction.getAssetMap().getIndex("Block_Secondary");
        if (blockPrimary >= 0 && interactions.getOrDefault(InteractionType.Primary, Integer.MIN_VALUE) == blockPrimary) {
            interactions.removeInt(InteractionType.Primary);
        }
        if (blockSecondary >= 0
            && interactions.getOrDefault(InteractionType.Secondary, Integer.MIN_VALUE) == blockSecondary) {
            interactions.removeInt(InteractionType.Secondary);
        }
    }
}
