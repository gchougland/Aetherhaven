package com.hexvane.aetherhaven.economy;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Whether a broken block uses a vanilla soft-container droplist eligible for bonus gold. */
public final class BreakableContainerEligibility {
    private static final Set<String> ELIGIBLE_DROP_LIST_IDS = Set.of(
        "Barrels",
        "Container_Pot_Ancient",
        "Container_Pot_Clay",
        "Container_Pot_Clay_Tall",
        "Container_Pot_Jar_Blue",
        "Container_Coffins",
        "Container_Coffins_Rubble"
    );

    private BreakableContainerEligibility() {}

    public static boolean isBreakableContainer(@Nonnull BlockType blockType) {
        if (blockType == BlockType.EMPTY) {
            return false;
        }
        String dropListId = resolveSoftDropListId(blockType);
        return dropListId != null && ELIGIBLE_DROP_LIST_IDS.contains(dropListId);
    }

    @Nullable
    private static String resolveSoftDropListId(@Nonnull BlockType blockType) {
        BlockGathering gathering = blockType.getGathering();
        if (gathering == null) {
            return null;
        }
        SoftBlockDropType soft = gathering.getSoft();
        if (soft == null) {
            return null;
        }
        String id = soft.getDropListId();
        if (id == null || id.isBlank() || "Empty".equals(id)) {
            return null;
        }
        return id.trim();
    }
}
