package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Resolves which town a player is acting in when they may belong to several in one world. */
public final class TownPlayerResolution {
    private TownPlayerResolution() {}

    @Nonnull
    public static List<TownRecord> listAffiliatedTownsInWorld(@Nonnull TownManager tm, @Nonnull UUID playerUuid) {
        return tm.findAllTownsForPlayerInWorld(playerUuid);
    }

    @Nullable
    public static TownRecord resolveOwnedTown(@Nonnull TownManager tm, @Nonnull UUID playerUuid) {
        return tm.findTownForOwnerInWorld(playerUuid);
    }

    @Nullable
    public static TownRecord resolveAffiliatedTownAtBlock(
        @Nonnull TownManager tm,
        @Nonnull String worldName,
        @Nonnull UUID playerUuid,
        int blockX,
        int blockZ
    ) {
        TownRecord claim = tm.findTownContainingBlock(worldName, blockX, blockZ);
        if (claim == null || !claim.hasMemberOrOwner(playerUuid)) {
            return null;
        }
        return claim;
    }

    /**
     * Active town for journal, HUD, and generic UI: persisted choice, then territory, then owned, then any membership.
     */
    @Nullable
    public static TownRecord resolveActiveTown(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull TownManager tm,
        @Nullable PlayerTownJournalState journalState
    ) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        UUID playerUuid = uc.getUuid();
        if (journalState != null) {
            TownRecord persisted = townForActiveId(tm, playerUuid, journalState.getActiveTownId());
            if (persisted != null) {
                return persisted;
            }
        }
        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d pos = tc.getPosition();
            TownRecord atPlayer =
                resolveAffiliatedTownAtBlock(
                    tm, world.getName(), playerUuid, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
            if (atPlayer != null) {
                return atPlayer;
            }
        }
        TownRecord owned = resolveOwnedTown(tm, playerUuid);
        if (owned != null) {
            return owned;
        }
        List<TownRecord> all = listAffiliatedTownsInWorld(tm, playerUuid);
        return all.isEmpty() ? null : all.get(0);
    }

    @Nullable
    public static TownRecord resolveActiveTown(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull TownManager tm
    ) {
        PlayerTownJournalState journal = store.getComponent(playerRef, PlayerTownJournalState.getComponentType());
        return resolveActiveTown(world, store, playerRef, tm, journal);
    }

    /** Plot placement: town claim at anchor when affiliated, otherwise owned town. */
    @Nullable
    public static TownRecord resolveTownForPlotPlacement(
        @Nonnull TownManager tm,
        @Nonnull String worldName,
        @Nonnull UUID playerUuid,
        @Nonnull Vector3i anchorBlock
    ) {
        TownRecord atAnchor = resolveAffiliatedTownAtBlock(tm, worldName, playerUuid, anchorBlock.x, anchorBlock.z);
        if (atAnchor != null) {
            return atAnchor;
        }
        return resolveOwnedTown(tm, playerUuid);
    }

    /** Quest board and similar: claim at player feet, else active town resolution. */
    @Nullable
    public static TownRecord resolveTownAtPlayerOrActive(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull TownManager tm
    ) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d pos = tc.getPosition();
            TownRecord atPlayer =
                resolveAffiliatedTownAtBlock(
                    tm, world.getName(), uc.getUuid(), (int) Math.floor(pos.x), (int) Math.floor(pos.z));
            if (atPlayer != null) {
                return atPlayer;
            }
        }
        return resolveActiveTown(world, store, playerRef, tm);
    }

    /** Clears persisted active town when the player is no longer affiliated with it. */
    public static void reconcileActiveTownId(
        @Nonnull TownManager tm,
        @Nonnull UUID playerUuid,
        @Nonnull PlayerTownJournalState journalState
    ) {
        UUID active = journalState.getActiveTownId();
        if (active == null) {
            return;
        }
        TownRecord t = tm.getTown(active);
        if (t == null || !t.hasMemberOrOwner(playerUuid)) {
            journalState.clearActiveTownId();
        }
    }

    /** Clears journal active town when it matches {@code townId} (online players only). */
    public static void clearActiveTownIdIfMatches(
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull UUID townId
    ) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (!pr.getUuid().equals(playerUuid)) {
                continue;
            }
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
            if (journal == null) {
                return;
            }
            UUID active = journal.getActiveTownId();
            if (active != null && active.equals(townId)) {
                journal.clearActiveTownId();
                store.putComponent(ref, PlayerTownJournalState.getComponentType(), journal);
            }
            return;
        }
    }

    @Nullable
    private static TownRecord townForActiveId(
        @Nonnull TownManager tm,
        @Nonnull UUID playerUuid,
        @Nullable UUID activeTownId
    ) {
        if (activeTownId == null) {
            return null;
        }
        TownRecord t = tm.getTown(activeTownId);
        if (t == null || !t.hasMemberOrOwner(playerUuid)) {
            return null;
        }
        return t;
    }

    /** Owned town, else first affiliated town in stable order (no journal or position). */
    @Nullable
    public static TownRecord resolveFallbackAffiliatedTown(@Nonnull TownManager tm, @Nonnull UUID playerUuid) {
        TownRecord owned = resolveOwnedTown(tm, playerUuid);
        if (owned != null) {
            return owned;
        }
        List<TownRecord> all = listAffiliatedTownsInWorld(tm, playerUuid);
        return all.isEmpty() ? null : all.get(0);
    }

    @Nonnull
    public static Comparator<TownRecord> affiliatedTownDisplayOrder() {
        return Comparator.comparing(
            t -> t.getDisplayName().toLowerCase(Locale.ROOT),
            Comparator.nullsLast(String::compareTo)
        );
    }
}
