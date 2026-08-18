package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.CustomFestivalPaths;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorFestivalDraftSetup;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Bridges the festival step UI row click to the draft setup and starting prefab paste. */
final class PlotCreatorFestivalPick {
    private PlotCreatorFestivalPick() {}

    /**
     * @param festivalId existing festival to make a look of, or null to start a new one from the base festival square
     * @return plot creator error lang suffix, or null on success
     */
    @Nullable
    static String pick(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String festivalId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return "needFestival";
        }
        FestivalDefinition existing = festivalId != null ? plugin.getFestivalCatalog().get(festivalId) : null;
        if (festivalId != null && (existing == null || existing.isLook())) {
            return "unknownFestival";
        }
        String startingPrefab = existing != null ? existing.getPrefabPath() : CustomFestivalPaths.BASE_PREFAB_PATH;
        return PlotCreatorFestivalDraftSetup.applyPick(
            session,
            playerRef,
            ref,
            store,
            existing,
            startingPrefab
        );
    }
}
