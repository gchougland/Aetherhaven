package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Visitor portal network: discover portals, validate travel, teleport players. */
public final class TouristPortalTravelService {
    public record Destination(
        @Nonnull UUID portalId,
        @Nonnull UUID townId,
        @Nonnull String townDisplayName,
        @Nonnull String ownerDisplayName,
        @Nonnull String townColorHex,
        boolean sourcePortal,
        boolean acceptsVisitors,
        boolean canTravelHere
    ) {}

    private TouristPortalTravelService() {}

    @Nullable
    public static TouristPortalRecord resolvePortalAtPlayerFeet(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3d feet
    ) {
        TouristPortalRegistrySync.refreshTravelNetwork(world, plugin);
        int fx = (int) Math.floor(feet.x);
        int fy = (int) Math.floor(feet.y);
        int fz = (int) Math.floor(feet.z);
        for (int dy = -2; dy <= 1; dy++) {
            Vector3i probe = new Vector3i(fx, fy + dy, fz);
            Vector3i base = TouristPortalBlockUtil.resolvePortalBaseBlock(world, probe);
            TouristPortalRecord record = TouristPortalRegistrySync.resolveAtBlock(world, plugin, base);
            if (record == null) {
                continue;
            }
            if (!isActivePortal(world, plugin, record)) {
                continue;
            }
            if (TouristPortalBlockUtil.isNearPortalDespawn(world, record.getBlockPosition(), feet)) {
                return record;
            }
        }
        return null;
    }

    public static boolean isActivePortal(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TouristPortalRecord portal
    ) {
        if (!world.getName().equals(portal.getWorldName())) {
            return false;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(portal.getTownId());
        if (town == null || !world.getName().equals(town.getWorldName())) {
            return false;
        }
        PlotInstance plot = town.findPlotById(portal.getPlotId());
        return plot != null && plot.getState() == PlotInstanceState.COMPLETE;
    }

    @Nonnull
    public static List<Destination> listNetworkDestinations(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID sourcePortalId,
        @Nullable UUID viewerPlayerUuid
    ) {
        TouristPortalRegistrySync.refreshTravelNetwork(world, plugin);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (world.getName().equals(town.getWorldName())) {
                TownPlayerLookup.refreshOwnerUsernameIfOnline(world, town, tm);
            }
        }
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        List<Destination> out = new ArrayList<>();
        for (TouristPortalRecord portal : registry.allRecords()) {
            if (!world.getName().equals(portal.getWorldName())) {
                continue;
            }
            if (!isActivePortal(world, plugin, portal)) {
                continue;
            }
            TownRecord town = tm.getTown(portal.getTownId());
            if (town == null) {
                continue;
            }
            boolean source = sourcePortalId.equals(portal.getPortalId());
            boolean viewerIsMember = viewerPlayerUuid != null && town.hasMemberOrOwner(viewerPlayerUuid);
            if (!source && town.isVisitorPortalMembersOnly() && !viewerIsMember) {
                continue;
            }
            boolean accepts = town.isAllowVisitorPortalTravel();
            boolean canTravel = !source && (accepts || (town.isVisitorPortalMembersOnly() && viewerIsMember));
            String ownerName = TownPlayerLookup.ownerDisplayName(world, town);
            out.add(
                new Destination(
                    portal.getPortalId(),
                    portal.getTownId(),
                    town.getDisplayName(),
                    ownerName,
                    TownPortalTravelColor.resolveHex(town),
                    source,
                    accepts,
                    canTravel
                )
            );
        }
        out.sort(Comparator.comparing(Destination::townDisplayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static boolean canPlayerTravelToPortalTown(
        @Nonnull TownRecord town,
        @Nullable UUID playerUuid
    ) {
        if (playerUuid != null && town.isVisitorPortalMembersOnly() && !town.hasMemberOrOwner(playerUuid)) {
            return false;
        }
        if (playerUuid != null && town.isVisitorPortalMembersOnly() && town.hasMemberOrOwner(playerUuid)) {
            return true;
        }
        return town.isAllowVisitorPortalTravel();
    }

    @Nullable
    public static TouristPortalRecord findPortal(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID portalId
    ) {
        TouristPortalRegistrySync.refreshTravelNetwork(world, plugin);
        TouristPortalRecord portal =
            AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin).get(portalId);
        if (portal == null || !isActivePortal(world, plugin, portal)) {
            return null;
        }
        return portal;
    }

    public static boolean canPlayerManageTownPortalSettings(
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid
    ) {
        return town.playerCanPlacePlots(playerUuid);
    }

    public static void teleportPlayerToPortal(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull TouristPortalRecord portal
    ) {
        Vector3d dest = TouristPortalBlockUtil.returnStandPosition(world, portal.getBlockPosition());
        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Rotation3f rot = tc.getRotation();
        store.addComponent(playerRef, Teleport.getComponentType(), Teleport.createForPlayer(dest, rot));
        Velocity vel = store.getComponent(playerRef, Velocity.getComponentType());
        if (vel != null) {
            vel.setZero();
            store.putComponent(playerRef, Velocity.getComponentType(), vel);
        }
    }
}
