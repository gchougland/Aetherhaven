package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotTokenUnlockPageMetadata;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Grants a plot blueprint page when a quest with {@link QuestDefinition#grantPlotBlueprintConstructionId()} starts. */
public final class QuestPlotBlueprintOnStart {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private QuestPlotBlueprintOnStart() {}

    public static boolean grantIfConfigured(
        @Nullable AetherhavenPlugin plugin,
        @Nullable QuestDefinition def,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (plugin == null || def == null) {
            return false;
        }
        String cid = def.grantPlotBlueprintConstructionId();
        if (cid == null || cid.isBlank()) {
            return false;
        }
        ConstructionDefinition cdef = plugin.getConstructionCatalog().get(cid.trim());
        if (cdef == null) {
            LOGGER.atWarning().log(
                "Unknown construction id for grantPlotBlueprintConstructionId: %s (quest %s)",
                cid,
                def.idOrEmpty()
            );
            return false;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        ItemStack stack = PlotTokenUnlockPageMetadata.createGenericStack();
        player.giveItem(stack, playerRef, store);
        return true;
    }
}
