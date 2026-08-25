package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * A running festival is open to whoever turns up. Town claim rules still guard the rest of the town, but the square
 * itself has to let guests take part or a visiting player can only stand and watch.
 */
public final class FestivalOpenAccess {
    private FestivalOpenAccess() {}

    /** True when a festival is running and this block sits inside the square hosting it. */
    public static boolean isInsideRunningFestivalSquare(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        int x,
        int y,
        int z
    ) {
        if (town.getActiveFestivalId() == null) {
            return false;
        }
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        return square != null
            && FestivalService.isLiveFestivalSquare(town, square.getPlotId())
            && square.containsWorldBlock(x, y, z);
    }

    /**
     * Every online player currently standing in this town, members and visitors alike. This is the set of people
     * taking part in the town's festival.
     */
    @Nonnull
    public static Set<UUID> playersAttending(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store
    ) {
        Set<UUID> out = new LinkedHashSet<>();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return out;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        String worldName = world.getName();
        store.forEachChunk(
            Query.and(
                Player.getComponentType(),
                UUIDComponent.getComponentType(),
                TransformComponent.getComponentType()
            ),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (uc == null || tc == null) {
                        continue;
                    }
                    Vector3d pos = tc.getPosition();
                    TownRecord at =
                        tm.findTownContainingBlock(
                            worldName,
                            (int) Math.floor(pos.x),
                            (int) Math.floor(pos.z)
                        );
                    if (at != null && town.getTownId().equals(at.getTownId())) {
                        out.add(uc.getUuid());
                    }
                }
            }
        );
        return out;
    }
}
