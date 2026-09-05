package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotBlockStamper;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Opens and closes festivals at each town's festival square. Runs from the game time hub, so the check follows the same
 * clock as villager schedules.
 */
public final class FestivalService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.";

    /** How far from the square a visitor can be and still be told the festival is opening. */
    private static final double ANNOUNCE_RADIUS = 96.0;

    private FestivalService() {}

    /** Evaluates every town in this world and starts or ends festivals as the clock passes their window. */
    public static void applyForWorld(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (!HytaleServer.get().isBooted()) {
            return;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        LocalDateTime gameTime = wtr.getGameDateTime();
        long epochMinute = toEpochMinute(gameTime);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            try {
                applyForTown(world, store, plugin, tm, town, gameTime, epochMinute);
            } catch (RuntimeException e) {
                LOGGER.atWarning().withCause(e).log("Festival check failed for town %s", town.getTownId());
            }
        }
    }

    private static void applyForTown(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull LocalDateTime gameTime,
        long epochMinute
    ) {
        String runningId = town.getActiveFestivalId();
        if (runningId != null) {
            FestivalDefinition running = plugin.getFestivalCatalog().get(runningId);
            FestivalDefinition base = FestivalLookSelection.gameplayBase(plugin.getFestivalCatalog(), running);
            FestivalDefinition layout =
                base != null ? FestivalLookSelection.layoutFor(plugin.getFestivalCatalog(), town, base) : running;
            boolean expired = running == null
                || base == null
                || epochMinute >= town.getActiveFestivalEndEpochMinute()
                || !FestivalWindow.isActive(base, gameTime);
            if (expired) {
                endFestival(world, store, plugin, tm, town);
                return;
            }
            PlotInstance square = findFestivalSquare(plugin, town);
            UUID activePlotId = town.getActiveFestivalPlotId();
            PlotInstance activeSquare = activePlotId != null ? town.findPlotById(activePlotId) : null;
            // Square was destroyed/rebuilt while the festival was still marked active.
            if (activeSquare == null) {
                if (square == null || layout == null) {
                    abandonActiveFestivalWithoutSquare(world, store, plugin, tm, town);
                } else {
                    rebindActiveFestivalToSquare(world, store, plugin, tm, town, square, layout);
                }
                return;
            }
            // Spot POIs are runtime-only. Rebuild them after reloads / plot refresh so villagers keep attending.
            ensureActiveFestivalSpots(world, store, plugin, tm, town, layout);
            return;
        }
        AetherhavenCalendar.CalendarDate date = AetherhavenCalendar.from(gameTime);
        FestivalDefinition today = plugin.getFestivalCatalog().festivalOn(date.season(), date.dayOfSeason());
        if (today == null || !FestivalWindow.isActive(today, gameTime)) {
            return;
        }
        startFestival(world, store, plugin, tm, town, today, gameTime, epochMinute);
    }

    /** Drops festival runtime when the host square is gone and there is nothing to paste onto. */
    private static void abandonActiveFestivalWithoutSquare(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town
    ) {
        FestivalSpotService.clearSpots(world, plugin, town);
        FestivalNpcSpawnService.despawnFestivalNpcs(world, store, plugin, town);
        town.clearActiveFestival();
        tm.updateTown(town);
        FestivalAttendanceService.releaseAttendees(world, store, plugin, town);
        LOGGER.atInfo().log("Abandoned active festival for town %s; festival square is missing", town.getTownId());
    }

    /** Pastes the running festival onto a rebuilt square that replaced the old host plot. */
    private static void rebindActiveFestivalToSquare(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival
    ) {
        String everyday = everydayPrefabPath(plugin, square);
        if (everyday == null) {
            abandonActiveFestivalWithoutSquare(world, store, plugin, tm, town);
            return;
        }
        boolean started = FestivalPrefabSwapService.swap(
            world,
            plugin,
            town,
            square,
            everyday,
            festival.getPrefabPath(),
            () -> onFestivalPasted(world, plugin, town.getTownId(), square.getPlotId(), festival)
        );
        if (!started) {
            LOGGER.atWarning().log(
                "Could not rebind festival %s onto rebuilt square for town %s",
                festival.getId(),
                town.getTownId()
            );
            return;
        }
        town.setActiveFestivalPlotId(square.getPlotId());
        tm.updateTown(town);
        LOGGER.atInfo().log(
            "Rebound festival %s onto rebuilt square %s for town %s",
            festival.getId(),
            square.getPlotId(),
            town.getTownId()
        );
    }

    /** True when this plot is the square currently hosting a festival. */
    public static boolean isLiveFestivalSquare(@Nonnull TownRecord town, @Nullable UUID plotId) {
        return plotId != null
            && town.getActiveFestivalId() != null
            && plotId.equals(town.getActiveFestivalPlotId());
    }

    /** The completed festival square in this town, or null when the town has not built one. */
    @Nullable
    public static PlotInstance findFestivalSquare(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        List<PlotInstance> plots = town.listCompletePlotsWithGameplayConstruction(
            plugin.getConstructionCatalog(),
            AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE
        );
        return plots.isEmpty() ? null : plots.get(0);
    }

    /**
     * Starts {@code festival} at this town's festival square.
     *
     * @return false when the town has no finished square or the prefab could not be swapped
     */
    public static boolean startFestival(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull FestivalDefinition festival,
        @Nonnull LocalDateTime gameTime,
        long epochMinute
    ) {
        PlotInstance square = findFestivalSquare(plugin, town);
        if (square == null) {
            return false;
        }
        String everyday = everydayPrefabPath(plugin, square);
        if (everyday == null) {
            return false;
        }
        FestivalDefinition base = FestivalLookSelection.gameplayBase(plugin.getFestivalCatalog(), festival);
        if (base == null) {
            base = festival;
        }
        FestivalDefinition layout = FestivalLookSelection.layoutFor(plugin.getFestivalCatalog(), town, base);
        long endEpochMinute = resolveEndEpochMinute(base, gameTime, epochMinute);
        FestivalDefinition layoutForPaste = layout;
        boolean started = FestivalPrefabSwapService.swap(
            world,
            plugin,
            town,
            square,
            everyday,
            layout.getPrefabPath(),
            () -> onFestivalPasted(world, plugin, town.getTownId(), square.getPlotId(), layoutForPaste)
        );
        if (!started) {
            return false;
        }
        town.setActiveFestivalId(base.getId());
        town.setActiveFestivalPlotId(square.getPlotId());
        town.setActiveFestivalEndEpochMinute(endEpochMinute);
        tm.updateTown(town);
        announce(store, town, LANG + "started", base, FestivalPrefabSwapService.spotWorldPosition(plugin, square, 0, 0, 0));
        LOGGER.atInfo().log("Festival %s started for town %s", base.getId(), town.getTownId());
        return true;
    }

    /**
     * Sets this town's look for {@code baseFestivalId}. {@code lookId} null restores the original. If that holiday is
     * running, the square swaps to the new prefab on the world thread.
     *
     * @return plot crafting error lang suffix, or null on success
     */
    @Nullable
    public static String applySelectedLook(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull String baseFestivalId,
        @Nullable String lookId
    ) {
        FestivalCatalog catalog = plugin.getFestivalCatalog();
        FestivalDefinition base = catalog.get(baseFestivalId.trim());
        if (base == null || base.isLook()) {
            return "unknownFestivalLook";
        }
        if (lookId != null && !lookId.isBlank()) {
            FestivalDefinition look = catalog.get(lookId.trim());
            if (look == null || !look.isLook() || !base.getId().equals(look.getGameplayFestivalId())) {
                return "unknownFestivalLook";
            }
        }
        FestivalDefinition oldLayout = FestivalLookSelection.layoutFor(catalog, town, base);
        town.setSelectedFestivalLookId(base.getId(), lookId);
        tm.updateTown(town);
        if (!base.getId().equals(town.getActiveFestivalId())) {
            return null;
        }
        PlotInstance square = findFestivalSquare(plugin, town);
        if (square == null) {
            return null;
        }
        FestivalDefinition newLayout = FestivalLookSelection.layoutFor(catalog, town, base);
        if (oldLayout.getPrefabPath().equals(newLayout.getPrefabPath()) && oldLayout.getId().equals(newLayout.getId())) {
            return null;
        }
        FestivalSpotService.clearSpots(world, plugin, town);
        FestivalNpcSpawnService.despawnFestivalNpcs(world, store, plugin, town);
        boolean started = FestivalPrefabSwapService.swap(
            world,
            plugin,
            town,
            square,
            oldLayout.getPrefabPath(),
            newLayout.getPrefabPath(),
            () -> onFestivalPasted(world, plugin, town.getTownId(), square.getPlotId(), newLayout)
        );
        if (!started) {
            return "festivalLookSwapFailed";
        }
        return null;
    }

    /** Swaps the square back to its everyday prefab and clears everything the festival owned. */
    public static void endFestival(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town
    ) {
        String runningId = town.getActiveFestivalId();
        FestivalDefinition running = runningId != null ? plugin.getFestivalCatalog().get(runningId) : null;
        FestivalDefinition base = FestivalLookSelection.gameplayBase(plugin.getFestivalCatalog(), running);
        FestivalDefinition layout =
            base != null ? FestivalLookSelection.layoutFor(plugin.getFestivalCatalog(), town, base) : running;
        PlotInstance square = findFestivalSquare(plugin, town);

        FestivalSpotService.clearSpots(world, plugin, town);
        FestivalNpcSpawnService.despawnFestivalNpcs(world, store, plugin, town);
        FestivalDefinition mechanicSource = layout != null ? layout : running;
        if (mechanicSource != null && square != null) {
            String mechanicId = base != null ? base.getMechanicId() : mechanicSource.getMechanicId();
            FestivalMechanic mechanic = plugin.getFestivalMechanicRegistry().get(mechanicId);
            if (mechanic != null) {
                try {
                    mechanic.onEnd(world, town, square, mechanicSource);
                } catch (RuntimeException e) {
                    LOGGER.atWarning().withCause(e).log("Festival mechanic %s failed to end", mechanicId);
                }
            }
        }

        String everyday = square != null ? everydayPrefabPath(plugin, square) : null;
        String currentPrefab = layout != null ? layout.getPrefabPath() : (running != null ? running.getPrefabPath() : null);
        if (square != null && currentPrefab != null && everyday != null) {
            UUID townId = town.getTownId();
            UUID plotId = square.getPlotId();
            FestivalPrefabSwapService.swap(
                world,
                plugin,
                town,
                square,
                currentPrefab,
                everyday,
                () -> stampEverydayLinkedBlocks(world, plugin, townId, plotId)
            );
        }
        town.clearActiveFestival();
        tm.updateTown(town);
        FestivalAttendanceService.releaseAttendees(world, store, plugin, town);
        if (running != null) {
            announce(store, town, LANG + "ended", running, null);
            LOGGER.atInfo().log("Festival %s ended for town %s", running.getId(), town.getTownId());
        }
    }

    /**
     * Call before removing a festival square plot from the town. Drops active festival state so a rebuilt square can
     * host the next festival day instead of staying stuck on the destroyed plot id.
     */
    public static void onFestivalSquareRemoved(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance removed
    ) {
        if (!plugin.getConstructionCatalog()
            .matchesGameplayConstruction(removed.getConstructionId(), AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE)) {
            return;
        }
        if (town.getActiveFestivalId() == null) {
            return;
        }
        UUID activePlotId = town.getActiveFestivalPlotId();
        if (activePlotId != null && !activePlotId.equals(removed.getPlotId())) {
            return;
        }
        String runningId = town.getActiveFestivalId();
        FestivalDefinition running = runningId != null ? plugin.getFestivalCatalog().get(runningId) : null;
        FestivalSpotService.clearSpots(world, plugin, town);
        FestivalNpcSpawnService.despawnFestivalNpcs(world, store, plugin, town);
        if (running != null) {
            FestivalMechanic mechanic = plugin.getFestivalMechanicRegistry().get(running.getMechanicId());
            if (mechanic != null) {
                try {
                    mechanic.onEnd(world, town, removed, running);
                } catch (RuntimeException e) {
                    LOGGER.atWarning().withCause(e).log("Festival mechanic %s failed to end", running.getMechanicId());
                }
            }
        }
        town.clearActiveFestival();
        tm.updateTown(town);
        FestivalAttendanceService.releaseAttendees(world, store, plugin, town);
        LOGGER.atInfo().log("Cleared active festival for town %s after festival square removal", town.getTownId());
    }

    /**
     * Rebuilds missing stand markers for a festival that is already marked active. Without this, a world reload or
     * festival-square POI refresh leaves {@code activeFestivalSpotPoiIds} pointing at nothing and attendees resume
     * normal work (Elder at the town hall, and so on).
     */
    public static void ensureActiveFestivalSpots(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull FestivalDefinition festival
    ) {
        PlotInstance square = null;
        UUID activePlotId = town.getActiveFestivalPlotId();
        if (activePlotId != null) {
            square = town.findPlotById(activePlotId);
        }
        if (square == null) {
            square = findFestivalSquare(plugin, town);
        }
        if (square == null) {
            return;
        }
        if (FestivalSpotService.ensureSpots(world, plugin, tm, town, square, festival)) {
            FestivalAttendanceService.sendAttendeesToFestival(world, store, plugin, town, square, festival);
        }
    }

    private static void onFestivalPasted(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId,
        @Nonnull UUID plotId,
        @Nonnull FestivalDefinition festival
    ) {
        var entityStore = world.getEntityStore();
        if (entityStore == null) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        PlotInstance square = town != null ? town.findPlotById(plotId) : null;
        if (town == null || square == null) {
            return;
        }
        FestivalSpotService.registerSpots(world, plugin, tm, town, square, festival);
        FestivalNpcSpawnService.spawnFestivalNpcs(world, store, plugin, tm, town, square, festival);
        FestivalAttendanceService.sendAttendeesToFestival(world, store, plugin, town, square, festival);
        FestivalDefinition base = FestivalLookSelection.gameplayBase(plugin.getFestivalCatalog(), festival);
        String mechanicId = base != null ? base.getMechanicId() : festival.getMechanicId();
        FestivalMechanic mechanic = plugin.getFestivalMechanicRegistry().get(mechanicId);
        if (mechanic != null) {
            try {
                mechanic.onStart(world, town, square, festival);
            } catch (RuntimeException e) {
                LOGGER.atWarning().withCause(e).log("Festival mechanic %s failed to start", mechanicId);
            }
        }
    }

    /** Relinks the town records shelf after the everyday square is pasted back (prefab stores empty plot/town ids). */
    private static void stampEverydayLinkedBlocks(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId,
        @Nonnull UUID plotId
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        PlotInstance square = town != null ? town.findPlotById(plotId) : null;
        if (town == null || square == null) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(square.getConstructionId());
        if (def == null) {
            return;
        }
        PlotBlockStamper.stampAllLinkedBlocks(
            world,
            town,
            square,
            def,
            square.resolvePrefabAnchorWorld(def),
            square.resolvePrefabYaw()
        );
    }

    /** Everyday prefab for the square, taken from the plot's construction definition. */
    @Nullable
    public static String everydayPrefabPath(@Nonnull AetherhavenPlugin plugin, @Nonnull PlotInstance square) {
        ConstructionDefinition def = plugin.getConstructionCatalog().get(square.getConstructionId());
        if (def == null) {
            return null;
        }
        String path = def.getPrefabPath();
        return path != null && !path.isBlank() ? path.trim() : null;
    }

    /** Game epoch minute the festival closes, handling both same day and overnight windows. */
    public static long resolveEndEpochMinute(
        @Nonnull FestivalDefinition festival,
        @Nonnull LocalDateTime gameTime,
        long epochMinute
    ) {
        int minuteOfDay = gameTime.getHour() * 60 + gameTime.getMinute();
        int closing = FestivalWindow.closingMinuteOfDay(festival);
        long dayStart = epochMinute - minuteOfDay;
        long end = dayStart + closing;
        if (end <= epochMinute) {
            end += 24L * 60L;
        }
        return end;
    }

    public static long toEpochMinute(@Nonnull LocalDateTime gameTime) {
        return gameTime.toLocalDate().toEpochDay() * 24L * 60L + gameTime.toLocalTime().toSecondOfDay() / 60L;
    }

    /**
     * Tells the town what just happened. Everyone in the town hears about it, and so does anyone standing near the square,
     * so a visitor who walked in for the day is not left out.
     *
     * @param squareCenter where the square is, which also turns the chat line into an opening banner; null for the closing
     *     line, which stays in chat
     */
    private static void announce(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String langKey,
        @Nonnull FestivalDefinition festival,
        @Nullable Vector3d squareCenter
    ) {
        Message name = festivalName(festival);
        Message chat = Message.translation(langKey).param("festival", name);
        Message bannerTitle = Message.translation(LANG + "banner.started.title").param("festival", name);
        Message bannerSubtitle = Message.translation(LANG + "banner.started.subtitle");
        int sting = SoundEvent.getAssetMap().getIndex(AetherhavenConstants.EVENT_TITLE_SHORT_SUCCESS_SOUND_ID);
        Query<EntityStore> query = Query.and(
            PlayerRef.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                if (uc == null || pr == null) {
                    continue;
                }
                TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                boolean nearSquare = squareCenter != null
                    && tc != null
                    && tc.getPosition().distanceSquared(squareCenter) <= ANNOUNCE_RADIUS * ANNOUNCE_RADIUS;
                if (!town.hasMemberOrOwner(uc.getUuid()) && !nearSquare) {
                    continue;
                }
                pr.sendMessage(chat);
                if (squareCenter == null) {
                    continue;
                }
                EventTitleUtil.showEventTitleToPlayer(pr, bannerTitle, bannerSubtitle, true, null, 4.0F, 0.7F, 0.9F);
                if (sting != SoundEvent.EMPTY_ID) {
                    SoundUtil.playSoundEvent2dToPlayer(pr, sting, SoundCategory.UI);
                }
            }
        });
    }

    /** Display name for chat and UI, preferring the festival's lang key. */
    @Nonnull
    public static Message festivalName(@Nonnull FestivalDefinition festival) {
        String key = festival.getDisplayNameLangKey();
        return key != null ? Message.translation(key) : Message.raw(festival.getDisplayName());
    }
}
