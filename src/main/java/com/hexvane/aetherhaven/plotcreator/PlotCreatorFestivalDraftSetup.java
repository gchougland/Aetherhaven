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
        // Shipped festival prefabs paste from the middle of the square (same as festival square swaps). Bounds stay
        // the min/max box; the paste origin is the center so the build lines up with the wireframe.
        Vector3i center =
            new Vector3i(min.x + FestivalPrefabSize.SPAN_X / 2, min.y, min.z + FestivalPrefabSize.SPAN_Z / 2);
        draft.setPlotAnchor(new Vector3i(center));
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
            PrefabFootprintClearUtil.clearFootprint(world, volume, true);
            ConstructionAnimator.start(
                plugin,
                world,
                new Vector3i(center),
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

    /** Fills festival settings and important-spot selections from an existing definition or clears for a new one. */
    public static void applyFestivalFields(@Nonnull PlotCreatorDraft draft, @Nullable FestivalDefinition existing) {
        draft.getSelectedSpots().clear();
        draft.getPois().clear();
        draft.getFestivalNpcs().clear();
        draft.getFestivalTouristSpots().clear();
        draft.getFestivalRaceLanes().clear();
        draft.getFestivalBalloonSpawns().clear();
        draft.getFestivalWhackSpawns().clear();
        draft.setFestivalWheelLocal(null);
        draft.getFestivalRaceStartSpots().clear();
        draft.setFestivalRaceFinishLocal(null);
        draft.setFestivalMazeStartLocal(null);
        draft.getFestivalOrbSpawns().clear();
        draft.setFestivalCenterpieceLocal(null);
        draft.setImportantSpotsConfirmed(false);
        if (existing == null) {
            draft.setEditingFestivalId(null);
            draft.setFestivalId(null);
            draft.setConstructionId(null);
            draft.setConstructionIdUserEdited(false);
            draft.setDisplayName(null);
            draft.setDescription(null);
            draft.setPrefabPath(CustomFestivalPaths.BASE_PREFAB_PATH);
            draft.setFestivalMechanicId(null);
            draft.setFestivalMechanicInput(PlotCreatorFestivalMechanicDefaults.displayLabel(null));
            return;
        }
        draft.setEditingFestivalId(existing.getId());
        draft.setFestivalId(existing.getId());
        draft.setConstructionId(existing.getId());
        draft.setConstructionIdUserEdited(true);
        draft.setDisplayName(existing.getDisplayName());
        draft.setDescription(existing.getDescription());
        draft.setPrefabPath(existing.getPrefabPath());
        draft.setLockedPrefabPathKey(existing.getPrefabPath());
        draft.setFestivalSeason(existing.getSeason().name());
        draft.setFestivalSeasonInput(existing.getSeason().displayName());
        draft.setFestivalDayOfSeason(existing.getDayOfSeason());
        draft.setFestivalDayInput(String.valueOf(existing.getDayOfSeason()));
        draft.setFestivalAllDay(existing.isAllDay());
        draft.setFestivalStartHour(existing.getStartHour());
        draft.setFestivalStartHourInput(String.valueOf(existing.getStartHour()));
        draft.setFestivalEndHour(existing.getEndHour());
        draft.setFestivalEndHourInput(String.valueOf(existing.getEndHour()));
        draft.setFestivalMechanicId(existing.getMechanicId());
        draft.setFestivalMechanicInput(PlotCreatorFestivalMechanicDefaults.displayLabel(existing.getMechanicId()));

        for (FestivalDefinition.SpotRow spot : existing.getSpots()) {
            if (spot.getResidentKind().isEmpty()) {
                continue;
            }
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.workOrBard(spot.getResidentKind(), 1));
            draft.getPois().add(PlotCreatorFestivalSpots.toPoiDraft(spot));
        }
        for (FestivalDefinition.NpcRow npc : existing.getNpcs()) {
            if (npc.getNpcRoleId().isEmpty()) {
                continue;
            }
            draft.getFestivalNpcs().add(
                FestivalDefinition.NpcRow.of(
                    npc.getNpcRoleId(),
                    npc.getLocalX(),
                    npc.getLocalY(),
                    npc.getLocalZ(),
                    npc.getYawDegrees()
                )
            );
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.festivalNpc(npc.getNpcRoleId(), 1));
        }
        if (!existing.getTouristSpots().isEmpty()) {
            for (FestivalDefinition.TouristSpotRow spot : existing.getTouristSpots()) {
                draft.getFestivalTouristSpots().add(
                    FestivalDefinition.TouristSpotRow.of(
                        spot.getLocalX(),
                        spot.getLocalY(),
                        spot.getLocalZ(),
                        spot.getYawDegrees()
                    )
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT,
                    existing.getTouristSpots().size()
                )
            );
        }
        if (existing.getCenterpieceLocal() != null) {
            draft.setFestivalCenterpieceLocal(existing.getCenterpieceLocal());
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_CENTERPIECE, 1));
        }
        if (!existing.getRaceLanes().isEmpty()) {
            for (FestivalDefinition.RaceLaneRow lane : existing.getRaceLanes()) {
                draft.getFestivalRaceLanes().add(
                    FestivalDefinition.RaceLaneRow.of(
                        lane.getNpcRoleId(),
                        lane.getStartLocalX(),
                        lane.getStartLocalY(),
                        lane.getStartLocalZ(),
                        lane.getFinishLocalX(),
                        lane.getFinishLocalY(),
                        lane.getFinishLocalZ()
                    )
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_RACE_LANE, existing.getRaceLanes().size())
            );
        }
        if (!existing.getBalloonSpawns().isEmpty()) {
            for (FestivalDefinition.BalloonSpawnRow spot : existing.getBalloonSpawns()) {
                draft.getFestivalBalloonSpawns().add(
                    FestivalDefinition.BalloonSpawnRow.of(spot.getLocalX(), spot.getLocalY(), spot.getLocalZ())
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_BALLOON_SPAWN,
                    existing.getBalloonSpawns().size()
                )
            );
        }
        if (!existing.getWhackSpawns().isEmpty()) {
            for (FestivalDefinition.WhackSpawnRow spot : existing.getWhackSpawns()) {
                draft.getFestivalWhackSpawns().add(
                    FestivalDefinition.WhackSpawnRow.of(spot.getLocalX(), spot.getLocalY(), spot.getLocalZ())
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_WHACK_SPAWN,
                    existing.getWhackSpawns().size()
                )
            );
        }
        if (existing.getWheelLocal() != null) {
            FestivalDefinition.WheelLocalRow wheel = existing.getWheelLocal();
            draft.setFestivalWheelLocal(
                FestivalDefinition.WheelLocalRow.of(
                    wheel.getLocalX(),
                    wheel.getLocalY(),
                    wheel.getLocalZ(),
                    wheel.getYawDegrees()
                )
            );
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_WHEEL, 1));
        }
        if (!existing.getRaceStartSpots().isEmpty()) {
            for (FestivalDefinition.RaceStartSpotRow spot : existing.getRaceStartSpots()) {
                draft.getFestivalRaceStartSpots().add(
                    FestivalDefinition.RaceStartSpotRow.of(
                        spot.getLocalX(),
                        spot.getLocalY(),
                        spot.getLocalZ(),
                        spot.getYawDegrees()
                    )
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_TREE_CLIMB_START,
                    existing.getRaceStartSpots().size()
                )
            );
        }
        if (existing.getRaceFinishLocal() != null) {
            FestivalDefinition.RaceFinishLocalRow finish = existing.getRaceFinishLocal();
            draft.setFestivalRaceFinishLocal(
                FestivalDefinition.RaceFinishLocalRow.of(
                    finish.getLocalX(),
                    finish.getLocalY(),
                    finish.getLocalZ()
                )
            );
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_TREE_CLIMB_FINISH, 1)
            );
        }
        if (existing.getMazeStartLocal() != null) {
            FestivalDefinition.MazeStartLocalRow start = existing.getMazeStartLocal();
            draft.setFestivalMazeStartLocal(
                FestivalDefinition.MazeStartLocalRow.of(
                    start.getLocalX(),
                    start.getLocalY(),
                    start.getLocalZ(),
                    start.getYawDegrees()
                )
            );
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_MAZE_START, 1));
        }
        if (!existing.getOrbSpawns().isEmpty()) {
            for (FestivalDefinition.OrbSpawnRow spot : existing.getOrbSpawns()) {
                draft.getFestivalOrbSpawns().add(
                    FestivalDefinition.OrbSpawnRow.of(spot.getLocalX(), spot.getLocalY(), spot.getLocalZ())
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_MAZE_ORB_SPAWN,
                    existing.getOrbSpawns().size()
                )
            );
        }
        if (!existing.getMarketStands().isEmpty()) {
            for (FestivalDefinition.RaceStartSpotRow spot : existing.getMarketStands()) {
                draft.getFestivalMarketStands().add(
                    FestivalDefinition.RaceStartSpotRow.of(
                        spot.getLocalX(),
                        spot.getLocalY(),
                        spot.getLocalZ(),
                        spot.getYawDegrees()
                    )
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_MARKET_STAND,
                    com.hexvane.aetherhaven.festival.market.MarketIds.STAND_COUNT
                )
            );
        }
        if (!existing.getMarketDisplaySlots().isEmpty()) {
            for (FestivalDefinition.OrbSpawnRow spot : existing.getMarketDisplaySlots()) {
                draft.getFestivalMarketDisplaySlots().add(
                    FestivalDefinition.OrbSpawnRow.of(spot.getLocalX(), spot.getLocalY(), spot.getLocalZ())
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_MARKET_DISPLAY,
                    com.hexvane.aetherhaven.festival.market.MarketIds.SLOT_COUNT
                )
            );
        }
        if (!existing.getSnowballPileSpots().isEmpty()) {
            for (FestivalDefinition.OrbSpawnRow spot : existing.getSnowballPileSpots()) {
                draft.getFestivalSnowballPileSpots().add(
                    FestivalDefinition.OrbSpawnRow.of(spot.getLocalX(), spot.getLocalY(), spot.getLocalZ())
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_SNOWBALL_PILE,
                    existing.getSnowballPileSpots().size()
                )
            );
        }
        if (!existing.getSnowballTeamASpots().isEmpty()) {
            for (FestivalDefinition.RaceStartSpotRow spot : existing.getSnowballTeamASpots()) {
                draft.getFestivalSnowballTeamASpots().add(
                    FestivalDefinition.RaceStartSpotRow.of(
                        spot.getLocalX(),
                        spot.getLocalY(),
                        spot.getLocalZ(),
                        spot.getYawDegrees()
                    )
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_SNOWBALL_TEAM_A,
                    existing.getSnowballTeamASpots().size()
                )
            );
        }
        if (!existing.getSnowballTeamBSpots().isEmpty()) {
            for (FestivalDefinition.RaceStartSpotRow spot : existing.getSnowballTeamBSpots()) {
                draft.getFestivalSnowballTeamBSpots().add(
                    FestivalDefinition.RaceStartSpotRow.of(
                        spot.getLocalX(),
                        spot.getLocalY(),
                        spot.getLocalZ(),
                        spot.getYawDegrees()
                    )
                );
            }
            draft.getSelectedSpots().add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_SNOWBALL_TEAM_B,
                    existing.getSnowballTeamBSpots().size()
                )
            );
        }
        if (existing.getSnowballOutLocal() != null) {
            FestivalDefinition.MazeStartLocalRow out = existing.getSnowballOutLocal();
            draft.setFestivalSnowballOutLocal(
                FestivalDefinition.MazeStartLocalRow.of(
                    out.getLocalX(),
                    out.getLocalY(),
                    out.getLocalZ(),
                    out.getYawDegrees()
                )
            );
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_SNOWBALL_OUT, 1));
        }
        PlotCreatorFestivalMechanicDefaults.ensureRequiredSelectedSpots(draft);
    }
}
