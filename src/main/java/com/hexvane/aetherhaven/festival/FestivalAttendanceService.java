package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Nudges villagers who have a spot at the running festival. Interrupting their current activity is all that is needed:
 * {@link VillagerAutonomySystem} sends anyone with a festival spot straight to it on their next idle tick, and stops
 * doing so once the festival is over.
 */
public final class FestivalAttendanceService {
    private FestivalAttendanceService() {}

    /** Villager binding kinds this festival reserves a spot for. */
    @Nonnull
    public static Set<String> attendingKinds(@Nonnull FestivalDefinition festival) {
        Set<String> kinds = new HashSet<>();
        for (FestivalDefinition.SpotRow spot : festival.getSpots()) {
            String kind = spot.getResidentKind();
            if (!kind.isEmpty()) {
                kinds.add(kind.trim().toLowerCase(Locale.ROOT));
            }
        }
        return kinds;
    }

    /** Interrupts whatever the spot villagers are doing so they head to the square right away. */
    public static void sendAttendeesToFestival(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance square,
        @Nonnull FestivalDefinition festival
    ) {
        Set<String> kinds = attendingKinds(festival);
        if (kinds.isEmpty()) {
            return;
        }
        interruptTownVillagers(store, town, kinds);
    }

    /** Interrupts the spot villagers once more so they pick their normal schedule back up. */
    public static void releaseAttendees(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        interruptTownVillagers(store, town, null);
    }

    private static void interruptTownVillagers(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nullable Set<String> onlyKinds
    ) {
        long now = VillagerAutonomySystem.resolveAutonomyNowMs(store);
        List<Ref<EntityStore>> targets = new ArrayList<>();
        Query<EntityStore> query =
            Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                if (binding == null || !town.getTownId().equals(binding.getTownId())) {
                    continue;
                }
                String kind = binding.getKind();
                if (kind == null || kind.isBlank()) {
                    continue;
                }
                if (onlyKinds != null && !onlyKinds.contains(kind.trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref != null && ref.isValid()) {
                    targets.add(ref);
                }
            }
        });
        for (Ref<EntityStore> ref : targets) {
            if (ref.isValid()) {
                VillagerAutonomySystem.resetAutonomyForRescue(ref, store, now);
            }
        }
    }

    /** True when this villager kind is expected at the running festival. */
    public static boolean hasFestivalSpot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord town,
        @Nullable String residentKind
    ) {
        return findSpot(world, plugin, town, residentKind) != null;
    }

    /** The spot POI reserved for this villager kind while a festival runs, or null. */
    @Nullable
    public static PoiEntry findSpot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord town,
        @Nullable String residentKind
    ) {
        if (town == null || residentKind == null || residentKind.isBlank()) {
            return null;
        }
        if (town.getActiveFestivalId() == null || town.getActiveFestivalSpotPoiIds().isEmpty()) {
            return null;
        }
        return FestivalSpotService.findSpotForKind(world, plugin, town, residentKind);
    }
}
