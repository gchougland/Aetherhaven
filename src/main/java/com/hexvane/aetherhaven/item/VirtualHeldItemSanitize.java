package com.hexvane.aetherhaven.item;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.ChangeActiveSlotInteraction;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps virtual {@link ItemBase} clones usable as held items on the integrated client: scroll-off needs
 * {@link InteractionType#SwapFrom}, and inherited placeable {@code blockId} / Block animations steal clicks in
 * singleplayer prediction.
 */
public final class VirtualHeldItemSanitize {
    private VirtualHeldItemSanitize() {}

    /**
     * Copy interactions from {@code original}, force SwapFrom for hotbar scroll-off, and clear block/tool fields that
     * make the client treat the stack as a placeable block.
     */
    public static void applyHeldItemClone(@Nullable ItemBase original, @Nonnull ItemBase clone) {
        EnumMap<InteractionType, Integer> copy = new EnumMap<>(InteractionType.class);
        Map<InteractionType, Integer> src = original != null ? original.interactions : null;
        if (src != null && !src.isEmpty()) {
            copy.putAll(src);
        } else if (clone.interactions != null && !clone.interactions.isEmpty()) {
            copy.putAll(clone.interactions);
        }
        int swapId = RootInteraction.getAssetMap().getIndex(ChangeActiveSlotInteraction.DEFAULT_ROOT.getId());
        if (swapId < 0) {
            // Generated Default_Swap may not be indexed yet during very early boot; resolve/load it.
            swapId = RootInteraction.getRootInteractionIdOrUnknown(ChangeActiveSlotInteraction.DEFAULT_ROOT.getId());
        }
        copy.put(InteractionType.SwapFrom, swapId);
        clone.interactions = copy;
        // 0 = no block type on the wire (see Item.toPacket leaving blockId unset when null).
        clone.blockId = 0;
        clone.builderToolData = null;
        if (clone.playerAnimationsId == null
            || clone.playerAnimationsId.isBlank()
            || "Block".equalsIgnoreCase(clone.playerAnimationsId)) {
            clone.playerAnimationsId = "Item";
        }
    }
}
