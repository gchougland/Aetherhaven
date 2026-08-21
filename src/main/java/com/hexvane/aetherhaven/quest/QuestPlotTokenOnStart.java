package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Grants the building's plot token item when a quest with {@link QuestDefinition#grantPlotTokenConstructionId()} starts. */
public final class QuestPlotTokenOnStart {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private QuestPlotTokenOnStart() {}

    public static boolean grantIfConfigured(
        @Nullable AetherhavenPlugin plugin,
        @Nullable QuestDefinition def,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        return grantIfConfigured(plugin, def, null, playerRef, store);
    }

    public static boolean grantIfConfigured(
        @Nullable AetherhavenPlugin plugin,
        @Nullable QuestDefinition def,
        @Nullable TownRecord town,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (plugin == null || def == null) {
            return false;
        }
        String baseCid = def.grantPlotTokenConstructionId();
        if (baseCid == null || baseCid.isBlank()) {
            return false;
        }
        String cid = QuestPlotTokenStyleResolver.resolveConstructionId(plugin.getConstructionCatalog(), baseCid, town);
        ConstructionDefinition cdef = plugin.getConstructionCatalog().get(cid.trim());
        if (cdef == null) {
            LOGGER.atWarning().log(
                "Unknown construction id for grantPlotTokenConstructionId: %s (quest %s, resolved %s)",
                baseCid,
                def.idOrEmpty(),
                cid
            );
            return false;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        String legacyTokenId = cdef.getPlotTokenItemId();
        if (legacyTokenId != null
            && !legacyTokenId.isBlank()
            && !AetherhavenConstants.PLOT_TOKEN_UNIFIED.equals(legacyTokenId.trim())) {
            player.giveItem(new ItemStack(legacyTokenId.trim(), 1), playerRef, store);
            return true;
        }
        PlotTokenInventory.giveToPlayer(player, cdef.getId(), 1, cdef.getDisplayName(), playerRef, store);
        return true;
    }
}
