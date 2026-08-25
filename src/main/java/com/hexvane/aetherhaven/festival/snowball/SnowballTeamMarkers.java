package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.marker.EntityHeadMarker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A red or blue orb over every fighter still in the snowball fight, so both sides can be told apart across the square.
 *
 * <p>The particle systems behind it only last a third of a second, so a marker has to be re-sent while its fighter is
 * still standing. Fighters who are knocked out, and everybody once the fight is over, get theirs taken away. What is
 * on screen is tracked here rather than read back off the session, so a fight that ends abruptly still cleans up.
 */
final class SnowballTeamMarkers {
    private static final String RED_PARTICLE = "Aetherhaven_Snowball_Team_Red";
    private static final String BLUE_PARTICLE = "Aetherhaven_Snowball_Team_Blue";
    private static final String RED_NODE = "AetherhavenSnowballTeam_red";
    private static final String BLUE_NODE = "AetherhavenSnowballTeam_blue";
    /** Comfortably inside the 0.35s particle lifespan, so the orb never blinks out between sends. */
    private static final long REFRESH_MS = 200L;
    private static final float HEAD_OFFSET_Y = 2.6f;

    private static final Map<UUID, Map<UUID, Mark>> MARKED_BY_TOWN = new HashMap<>();

    private SnowballTeamMarkers() {}

    static void refresh(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull SnowballSession session,
        @Nonnull List<Ref<EntityStore>> audience,
        long nowMs
    ) {
        if (audience.isEmpty()) {
            return;
        }
        Map<UUID, Mark> marked = MARKED_BY_TOWN.computeIfAbsent(townId, id -> new HashMap<>());
        Set<UUID> wanted = new HashSet<>();
        for (SnowballSession.Fighter fighter : session.fightersView()) {
            if (fighter.isOut()) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(fighter.uuid());
            if (ref == null || !ref.isValid()) {
                continue;
            }
            wanted.add(fighter.uuid());
            Mark mark = marked.get(fighter.uuid());
            if (mark != null && mark.team() == fighter.team() && nowMs - mark.sentAtMs() < REFRESH_MS) {
                continue;
            }
            boolean red = fighter.team() == SnowballIds.Team.A;
            boolean sent =
                EntityHeadMarker.spawn(
                    ref,
                    red ? RED_PARTICLE : BLUE_PARTICLE,
                    red ? RED_NODE : BLUE_NODE,
                    HEAD_OFFSET_Y,
                    audience,
                    store
                );
            if (sent) {
                marked.put(fighter.uuid(), new Mark(fighter.team(), nowMs));
            }
        }
        drop(store, marked, audience, wanted);
    }

    /** Takes the orbs off everyone, for when the fight ends or the festival is torn down. */
    static void clear(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull List<Ref<EntityStore>> audience
    ) {
        Map<UUID, Mark> marked = MARKED_BY_TOWN.get(townId);
        if (marked == null || marked.isEmpty()) {
            return;
        }
        drop(store, marked, audience, Set.of());
        if (marked.isEmpty()) {
            MARKED_BY_TOWN.remove(townId);
        }
    }

    private static void drop(
        @Nonnull Store<EntityStore> store,
        @Nonnull Map<UUID, Mark> marked,
        @Nonnull List<Ref<EntityStore>> audience,
        @Nullable Set<UUID> keep
    ) {
        Iterator<Map.Entry<UUID, Mark>> it = marked.entrySet().iterator();
        while (it.hasNext()) {
            UUID fighterUuid = it.next().getKey();
            if (keep != null && keep.contains(fighterUuid)) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(fighterUuid);
            if (ref != null && ref.isValid()) {
                EntityHeadMarker.clear(ref, audience, store);
            }
            it.remove();
        }
    }

    private record Mark(@Nonnull SnowballIds.Team team, long sentAtMs) {}
}
