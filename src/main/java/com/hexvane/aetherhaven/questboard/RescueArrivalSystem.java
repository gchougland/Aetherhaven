package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.map.RaidQuestCompassCache;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Watches for players reaching a rescue camp.
 *
 * <p>A camp is placed far from the quest board, in terrain nobody is near. Anyone added there at once is taken back
 * out by the game or left standing where the ground has not loaded. So the captive and the captors are placed by
 * {@link RescueCampService#populate} only when a player is close, and checked again every second while they stay.
 * Until then the camp is marked on the compass so it can be found.
 */
public final class RescueArrivalSystem extends EntityTickingSystem<EntityStore> {
    /** Full key of the compass label for a camp. */
    static final String CAMP_MARKER = "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.rescueCampMarker";
    private static final long CHECK_MS = 1000L;

    private final AetherhavenPlugin plugin;
    private final Map<UUID, Long> lastCheckByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPopulateBySlot = new ConcurrentHashMap<>();

    public RescueArrivalSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), TransformComponent.getComponentType(), UUIDComponent.getComponentType());
    }

    /** True when a position is close enough to a camp for its people to be placed. */
    static boolean near(@Nonnull Vector3d position, @Nonnull Vector3d camp) {
        double dx = position.x - camp.x, dz = position.z - camp.z;
        return dx * dx + dz * dz <= RescueCampService.ARRIVAL_RADIUS * RescueCampService.ARRIVAL_RADIUS
            && Math.abs(position.y - camp.y) <= RescueCampService.ARRIVAL_RADIUS;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (!RescueCampService.isAvailable()) {
            return;
        }
        UUIDComponent player = chunk.getComponent(index, UUIDComponent.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (player == null || transform == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastCheckByPlayer.get(player.getUuid());
        if (last != null && now - last < CHECK_MS) {
            return;
        }
        lastCheckByPlayer.put(player.getUuid(), now);
        World world = store.getExternalData().getWorld();
        Vector3d position = transform.getPosition();
        for (TownRecord town : AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            for (QuestBoardSlotRecord slot : town.acceptedBoardQuestsSnapshot()) {
                Vector3d camp = slot.isRescueQuest() ? RescueCampService.campCentre(slot) : null;
                if (camp == null) {
                    continue;
                }
                String instance = slot.instanceIdOrEmpty();
                markCamp(world, town, slot, camp);
                if (!near(position, camp)) {
                    continue;
                }
                Long populated = lastPopulateBySlot.get(instance);
                if (populated != null && now - populated < CHECK_MS) {
                    continue;
                }
                lastPopulateBySlot.put(instance, now);
                UUID townId = town.getTownId();
                // Adding entities is not allowed while the store is being processed.
                world.execute(() -> RescueCampService.populate(world, townId, instance));
            }
        }
    }

    private static void markCamp(@Nonnull World world, @Nonnull TownRecord town, @Nonnull QuestBoardSlotRecord slot, @Nonnull Vector3d camp) {
        String event = slot.getRescueEventId();
        if (event == null) {
            return;
        }
        try {
            RaidQuestCompassCache.upsert(world.getName(), new RaidQuestCompassCache.Entry(
                town.getTownId(), UUID.fromString(event), slot.instanceIdOrEmpty(), CAMP_MARKER, camp.x, camp.y, camp.z));
        } catch (IllegalArgumentException invalid) {
            // no marker for a damaged id
        }
    }
}
