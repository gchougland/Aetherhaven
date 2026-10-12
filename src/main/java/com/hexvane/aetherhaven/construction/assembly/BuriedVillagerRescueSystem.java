package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3dc;

/**
 * Gets a villager out who is already inside a solid block at a building plot: one buried before the sites learned not
 * to do that, or by anything since. Looks at each of this mod's villagers about once a second; one found inside a
 * block twice running is moved to the nearest safe spot and the move is logged.
 *
 * <p>It can only ever touch an NPC that carries this mod's {@link TownVillagerBinding}, and only while that villager is
 * inside the footprint of a building plot of their own town, being built or finished. Creatures of the game or of
 * other mods are not in its query, and a villager anywhere else is left alone.
 */
public final class BuriedVillagerRescueSystem extends EntityTickingSystem<EntityStore> {
    /** Time between two looks at one villager. */
    static final long LOOK_EVERY_MS = 1_000L;
    /** Looks in a row that must find the villager inside a block before they are moved. */
    static final int LOOKS_NEEDED = 2;
    /** After a move, or when no safe spot was found, the villager is left alone this long. */
    static final long AFTER_MOVE_MS = 10_000L;
    /** Heights up the body, as shares of its own height, that must be in the open. */
    static final double[] PROBES = {0.25, 0.5, 0.9};
    private static final int PRUNE_ABOVE = 512;
    private static final long FORGET_MS = 120_000L;

    /** One villager's record: when to look next, and how many looks in a row found them buried. */
    static final class Watch {
        long nextLookMs, seenMs;
        int buriedLooks;
    }

    /** Whether a point of the world is inside something solid. */
    @FunctionalInterface
    public interface SolidAt {
        boolean at(double x, double y, double z);
    }

    private static final Map<UUID, Watch> WATCHES = new ConcurrentHashMap<>();

    @Nonnull
    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public BuriedVillagerRescueSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // This mod's villagers, townsfolk and guards only. Nothing else in the world matches.
        return Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType(), TransformComponent.getComponentType());
    }

    /**
     * Whether a body is buried: a point on its upright middle line, at a quarter, half or nine tenths of its height, is
     * inside something solid. Leaning on a wall, standing on a step or brushing a fence does not count; having a block
     * placed through the middle of you does.
     */
    public static boolean buried(@Nonnull Vector3dc feet, @Nonnull Box body, @Nonnull SolidAt solid) {
        double height = body.max.y - body.min.y;
        double x = feet.x() + (body.min.x + body.max.x) / 2.0, z = feet.z() + (body.min.z + body.max.z) / 2.0;
        for (double share : PROBES) {
            if (solid.at(x, feet.y() + body.min.y + height * share, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One look at a villager.
     *
     * @return true when this look makes it {@link #LOOKS_NEEDED} in a row and they should be moved now
     */
    static boolean look(@Nonnull Watch watch, boolean buriedNow, long nowMs) {
        watch.nextLookMs = nowMs + LOOK_EVERY_MS;
        if (!buriedNow) {
            watch.buriedLooks = 0;
            return false;
        }
        if (++watch.buriedLooks < LOOKS_NEEDED) {
            return false;
        }
        watch.buriedLooks = 0;
        watch.nextLookMs = nowMs + AFTER_MOVE_MS;
        return true;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        UUIDComponent id = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        if (id == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Watch watch = WATCHES.computeIfAbsent(id.getUuid(), ignored -> new Watch());
        watch.seenMs = now;
        if (now < watch.nextLookMs) {
            return;
        }
        if (WATCHES.size() > PRUNE_ABOVE) {
            WATCHES.values().removeIf(old -> now - old.seenMs > FORGET_MS);
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        World world = store.getExternalData().getWorld();
        if (binding == null || transform == null
            // Seated, asleep, mid move or dead: where they are is not where they stand.
            || store.getComponent(ref, MountedComponent.getComponentType()) != null
            || store.getComponent(ref, Teleport.getComponentType()) != null
            || store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            look(watch, false, now);
            return;
        }
        Vector3dc feet = transform.getPosition();
        Box body = SiteBodies.localBox(store, ref);
        boolean buriedNow = buried(feet, body, (x, y, z) -> VillagerBlockUtil.isInsideSolidBlock(world, x, y, z))
            && atOwnBuildingPlot(world, binding, feet);
        if (!look(watch, buriedNow, now)) {
            return;
        }
        SiteBodies.Body who = SiteBodies.Body.at(ref, false, feet, body);
        SiteRefuge.moveOut(world, store, who, null, "inside a block at a building plot");
    }

    /** Inside the footprint of a plot of the villager's own town that is being built or stands finished. */
    private boolean atOwnBuildingPlot(@Nonnull World world, @Nonnull TownVillagerBinding binding, @Nonnull Vector3dc feet) {
        UUID townId = binding.getTownId();
        if (townId == null) {
            return false;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town == null) {
            return false;
        }
        int x = (int) Math.floor(feet.x()), y = (int) Math.floor(feet.y() + 0.001), z = (int) Math.floor(feet.z());
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.ASSEMBLING && plot.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            var footprint = plot.toFootprint();
            if (footprint != null && (footprint.containsBlock(x, y, z) || footprint.containsBlock(x, y + 1, z))) {
                return true;
            }
        }
        return false;
    }
}
