package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.CustomFestivalPaths;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSize;
import com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Applies a festival pick on the plot creator festival step: locks the build box to the shared festival size, clears it,
 * pastes the starting prefab, and fills the draft with the festival's saved settings.
 */
public final class PlotCreatorFestivalDraftSetup {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";
    private static final int INSTANT_BLOCKS_PER_BATCH = 500_000;
    private static final long INSTANT_BATCH_DELAY_MS = 1L;

    private PlotCreatorFestivalDraftSetup() {}

    /**
     * @param existing festival being edited, or null when starting a new one
     * @return plot creator error lang suffix, or null on success
     */
    @Nullable
    public static String applyPick(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable FestivalDefinition existing,
        @Nonnull String startingPrefabPath
    ) {
        PlotCreatorDraft draft = session.getDraft();
        if (draft.getCornerFirst() == null || draft.getCornerSecond() == null) {
            return "needBounds";
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(startingPrefabPath);
        if (buffer == null) {
            return "prefab_missing";
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return "needFestival";
        }

        Vector3i min = new Vector3i(draft.boundsMin());
        Vector3i max = FestivalPrefabSize.maxFromMin(min);
        PlotCreatorBoundsValidation.commitCorners(draft, min, max);
        draft.setBoundsPhase(PlotCreatorBoundsPhase.FACE_ADJUST);
        draft.setBoundsDragStart(null);
        draft.setBoundsDragEnd(null);
        draft.setActiveBoundsFaceDrag(null);
        draft.setHoveredBoundsFace(null);
        // Shipped festival prefabs anchor at the middle of the square on the ground; match that exactly so spots and
        // the centerpiece line up no matter which festival is swapped in.
        draft.setPlotAnchor(
            new Vector3i(min.x + FestivalPrefabSize.SPAN_X / 2, min.y, min.z + FestivalPrefabSize.SPAN_Z / 2)
        );
        draft.setPrefabOriginMin(new Vector3i(min));
        draft.setFestivalSizeLocked(true);
        draft.setFestivalPicked(true);

        applyFestivalFields(draft, existing);

        World world = session.getWorld();
        PlotFootprintRecord volume = new PlotFootprintRecord(min.x, min.y, min.z, max.x, max.y, max.z);
        world.execute(() -> {
            Store<EntityStore> worldStore =
                world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
            if (worldStore != null) {
                PrefabFootprintClearUtil.removeEntitiesInFootprint(worldStore, volume);
            }
            PrefabFootprintClearUtil.clearFootprint(world, volume);
            ConstructionAnimator.start(
                plugin,
                world,
                new Vector3i(min),
                Rotation.None,
                true,
                false,
                buffer,
                worldStore != null ? worldStore : store,
                INSTANT_BLOCKS_PER_BATCH,
                INSTANT_BATCH_DELAY_MS,
                () -> playerRef.sendMessage(
                    Message.translation(MSG + ".hint.festivalPrefabReady").param("size", FestivalPrefabSize.describe())
                )
            );
        });
        PlotCreatorService.refreshBoundsVisuals(session, playerRef);
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        return null;
    }

    private static void applyFestivalFields(@Nonnull PlotCreatorDraft draft, @Nullable FestivalDefinition existing) {
        draft.getSelectedSpots().clear();
        draft.getPois().clear();
        draft.setImportantSpotsConfirmed(false);
        if (existing == null) {
            draft.setEditingFestivalId(null);
            draft.setFestivalId(null);
            draft.setConstructionId(null);
            draft.setConstructionIdUserEdited(false);
            draft.setDisplayName(null);
            draft.setDescription(null);
            draft.setPrefabPath(CustomFestivalPaths.BASE_PREFAB_PATH);
            return;
        }
        draft.setEditingFestivalId(existing.getId());
        draft.setFestivalId(existing.getId());
        draft.setConstructionId(existing.getId());
        draft.setConstructionIdUserEdited(true);
        draft.setDisplayName(existing.getDisplayName());
        draft.setDescription(existing.getDescription());
        draft.setPrefabPath(existing.getPrefabPath());
        draft.setFestivalSeason(existing.getSeason().name());
        draft.setFestivalSeasonInput(existing.getSeason().displayName());
        draft.setFestivalDayOfSeason(existing.getDayOfSeason());
        draft.setFestivalDayInput(String.valueOf(existing.getDayOfSeason()));
        draft.setFestivalAllDay(existing.isAllDay());
        draft.setFestivalStartHour(existing.getStartHour());
        draft.setFestivalStartHourInput(String.valueOf(existing.getStartHour()));
        draft.setFestivalEndHour(existing.getEndHour());
        draft.setFestivalEndHourInput(String.valueOf(existing.getEndHour()));
        for (FestivalDefinition.SpotRow spot : existing.getSpots()) {
            if (spot.getResidentKind().isEmpty()) {
                continue;
            }
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.work(spot.getResidentKind(), 1));
            draft.getPois().add(PlotCreatorFestivalSpots.toPoiDraft(spot));
        }
    }
}
