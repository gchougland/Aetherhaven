package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Turns a festival's {@code spots} rows into runtime POIs while the festival runs. The POIs are ephemeral, so they are
 * gone from the town's POI file the moment the festival ends and never linger in a save.
 */
public final class FestivalSpotService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private FestivalSpotService() {}

    /** Registers one standing POI per spot and records the ids on the town. */
    public static void registerSpots(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival
    ) {
        clearSpots(world, plugin, town);
        List<FestivalDefinition.SpotRow> spots = festival.getSpots();
        if (spots.isEmpty()) {
            return;
        }
        PoiRegistry registry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        List<String> poiIds = new ArrayList<>(spots.size() + festival.getTouristSpots().size());
        for (int i = 0; i < spots.size(); i++) {
            FestivalDefinition.SpotRow spot = spots.get(i);
            if (spot.getResidentKind().isEmpty()) {
                continue;
            }
            Vector3d target =
                FestivalPrefabSwapService.spotWorldPosition(plugin, square, spot.getLocalX(), spot.getLocalY(), spot.getLocalZ());
            UUID poiId = UUID.nameUUIDFromBytes(
                ("festival:" + town.getTownId() + ":" + festival.getId() + ":" + i).getBytes(StandardCharsets.UTF_8)
            );
            Set<String> tags = new HashSet<>();
            tags.add(AetherhavenConstants.POI_TAG_FESTIVAL);
            tags.add(AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL);
            PoiEntry entry = new PoiEntry(
                poiId,
                town.getTownId(),
                (int) Math.floor(target.x),
                (int) Math.floor(target.y),
                (int) Math.floor(target.z),
                tags,
                1,
                square.getPlotId(),
                null,
                PoiInteractionKind.NONE,
                false,
                null,
                target.x,
                target.y,
                target.z,
                (float) Math.toRadians(spot.getYawDegrees()),
                spot.getResidentKind()
            );
            registry.registerEphemeral(entry);
            poiIds.add(poiId.toString());
        }
        List<FestivalDefinition.TouristSpotRow> touristSpots = festival.getTouristSpots();
        for (int i = 0; i < touristSpots.size(); i++) {
            FestivalDefinition.TouristSpotRow spot = touristSpots.get(i);
            Vector3d target =
                FestivalPrefabSwapService.spotWorldPosition(plugin, square, spot.getLocalX(), spot.getLocalY(), spot.getLocalZ());
            UUID poiId = UUID.nameUUIDFromBytes(
                ("festival-tourist:" + town.getTownId() + ":" + festival.getId() + ":" + i)
                    .getBytes(StandardCharsets.UTF_8)
            );
            Set<String> tags = new HashSet<>();
            tags.add(AetherhavenConstants.POI_TAG_FESTIVAL);
            tags.add(AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL);
            tags.add(AetherhavenConstants.POI_TAG_TOURIST_VISIT);
            tags.add("FUN");
            PoiEntry entry = new PoiEntry(
                poiId,
                town.getTownId(),
                (int) Math.floor(target.x),
                (int) Math.floor(target.y),
                (int) Math.floor(target.z),
                tags,
                1,
                square.getPlotId(),
                null,
                PoiInteractionKind.NONE,
                false,
                null,
                target.x,
                target.y,
                target.z,
                (float) Math.toRadians(spot.getYawDegrees()),
                null
            );
            registry.registerEphemeral(entry);
            poiIds.add(poiId.toString());
        }
        List<FestivalDefinition.RaceStartSpotRow> marketStands = festival.getMarketStands();
        int standLimit = Math.min(marketStands.size(), com.hexvane.aetherhaven.festival.market.MarketIds.STAND_COUNT);
        for (int i = 0; i < standLimit; i++) {
            FestivalDefinition.RaceStartSpotRow stand = marketStands.get(i);
            Vector3d target =
                FestivalPrefabSwapService.spotWorldPosition(
                    plugin, square, stand.getLocalX(), stand.getLocalY(), stand.getLocalZ()
                );
            UUID poiId = UUID.nameUUIDFromBytes(
                ("festival-market-stand:" + town.getTownId() + ":" + festival.getId() + ":" + i)
                    .getBytes(StandardCharsets.UTF_8)
            );
            Set<String> tags = new HashSet<>();
            tags.add(AetherhavenConstants.POI_TAG_FESTIVAL);
            tags.add(AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL);
            PoiEntry entry = new PoiEntry(
                poiId,
                town.getTownId(),
                (int) Math.floor(target.x),
                (int) Math.floor(target.y),
                (int) Math.floor(target.z),
                tags,
                1,
                square.getPlotId(),
                null,
                PoiInteractionKind.NONE,
                false,
                null,
                target.x,
                target.y,
                target.z,
                (float) Math.toRadians(stand.getYawDegrees()),
                com.hexvane.aetherhaven.festival.market.MarketIds.standKind(i)
            );
            registry.registerEphemeral(entry);
            poiIds.add(poiId.toString());
        }
        town.setActiveFestivalSpotPoiIds(poiIds);
        tm.updateTown(town);
        LOGGER.atInfo().log("Registered %s festival spot(s) for %s", poiIds.size(), festival.getId());
    }

    /** Removes every spot POI the running festival registered. */
    public static void clearSpots(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        List<String> ids = town.getActiveFestivalSpotPoiIds();
        if (ids.isEmpty()) {
            return;
        }
        PoiRegistry registry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        for (String id : ids) {
            try {
                registry.unregisterEphemeral(UUID.fromString(id.trim()));
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("Invalid festival spot POI id %s for town %s", id, town.getTownId());
            }
        }
        town.setActiveFestivalSpotPoiIds(null);
    }

    /** The festival spot POI reserved for {@code residentKind}, or null when the festival has no spot for it. */
    @javax.annotation.Nullable
    public static PoiEntry findSpotForKind(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String residentKind
    ) {
        List<String> ids = town.getActiveFestivalSpotPoiIds();
        if (ids.isEmpty() || residentKind.isBlank()) {
            return null;
        }
        PoiRegistry registry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        String kind = residentKind.trim();
        for (String id : ids) {
            PoiEntry entry;
            try {
                entry = registry.get(UUID.fromString(id.trim()));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (entry != null
                && entry.getWorkResidentKind() != null
                && kind.equalsIgnoreCase(entry.getWorkResidentKind())) {
                return entry;
            }
        }
        return null;
    }

    /** The Nth festival spot with this villager kind, or null. */
    @javax.annotation.Nullable
    public static PoiEntry findNthSpotForKind(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String residentKind,
        int index
    ) {
        if (index < 0) {
            return null;
        }
        List<String> ids = town.getActiveFestivalSpotPoiIds();
        if (ids.isEmpty() || residentKind.isBlank()) {
            return null;
        }
        PoiRegistry registry = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        String kind = residentKind.trim();
        int seen = 0;
        for (String id : ids) {
            PoiEntry entry;
            try {
                entry = registry.get(UUID.fromString(id.trim()));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (entry != null
                && entry.getWorkResidentKind() != null
                && kind.equalsIgnoreCase(entry.getWorkResidentKind())) {
                if (seen == index) {
                    return entry;
                }
                seen++;
            }
        }
        return null;
    }

    /**
     * True when every resident spot for {@code festival} resolves in the live POI registry. Spot markers are
     * runtime-only, so a world reload or plot POI refresh can leave the town pointing at missing ids.
     */
    public static boolean spotsIntact(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull FestivalDefinition festival
    ) {
        List<FestivalDefinition.SpotRow> spots = festival.getSpots();
        if (spots.isEmpty()) {
            return true;
        }
        if (town.getActiveFestivalSpotPoiIds().isEmpty()) {
            return false;
        }
        for (FestivalDefinition.SpotRow spot : spots) {
            String kind = spot.getResidentKind();
            if (kind.isEmpty()) {
                continue;
            }
            if (findSpotForKind(world, plugin, town, kind) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Re-registers festival stand POIs when any are missing. Returns true when a rebuild happened so callers can
     * nudge attendees back to the square.
     */
    public static boolean ensureSpots(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival
    ) {
        if (spotsIntact(world, plugin, town, festival)) {
            return false;
        }
        LOGGER.atInfo().log(
            "Rebuilding missing festival spots for %s in town %s",
            festival.getId(),
            town.getTownId()
        );
        registerSpots(world, plugin, tm, town, square, festival);
        return true;
    }
}
