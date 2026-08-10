package com.hexvane.aetherhaven.festival.lettuce;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Pops a ready lettuce into a fountain of seeds and spring tickets, then clears the centerpiece. */
public final class FestivalLettuceBurstSystem extends EntityTickingSystem<EntityStore> {
    /** How long the fountain keeps throwing drops. */
    private static final long BURST_DURATION_MS = 1_400L;

    private static final float LAUNCH_SPEED_MIN = 6.0f;
    private static final float LAUNCH_SPEED_MAX = 10.0f;
    private static final float LAUNCH_PITCH_MIN_DEG = 55.0f;
    private static final float LAUNCH_PITCH_MAX_DEG = 80.0f;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(FestivalLettuceComponent.getComponentType(), TransformComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        FestivalLettuceComponent lettuce = chunk.getComponent(index, FestivalLettuceComponent.getComponentType());
        TransformComponent tc = chunk.getComponent(index, TransformComponent.getComponentType());
        if (lettuce == null || tc == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Vector3d center = new Vector3d(tc.getPosition());
        // Only auto-pop at the overcharge cap; otherwise the player presses F.
        if (lettuce.isMaxOvercharge()) {
            beginBurst(store, lettuce, center, now);
            return;
        }
        if (lettuce.isBursting()) {
            tickBurst(store, commandBuffer, chunk, index, lettuce, center, now);
        }
    }

    /**
     * Starts the fountain if the lettuce is ready. Safe to call from absorb (max overcharge) or from the F interaction.
     *
     * @return true when a new burst was started
     */
    public static boolean tryBeginBurst(
        @Nonnull Store<EntityStore> store,
        @Nonnull FestivalLettuceComponent lettuce,
        @Nonnull Vector3d center
    ) {
        if (!lettuce.isReadyToBurst() && !lettuce.isMaxOvercharge()) {
            return false;
        }
        if (!lettuce.isGrowing()) {
            return false;
        }
        beginBurst(store, lettuce, center, System.currentTimeMillis());
        return true;
    }

    static void beginBurst(
        @Nonnull Store<EntityStore> store,
        @Nonnull FestivalLettuceComponent lettuce,
        @Nonnull Vector3d center,
        long now
    ) {
        lettuce.setBurstSeedTarget(lettuce.scaledSeedCount());
        lettuce.setBurstTicketTarget(lettuce.scaledTicketCount());
        lettuce.setState(FestivalLettuceComponent.STATE_BURSTING);
        lettuce.setBurstStartEpochMs(now);
        lettuce.resetSeedsThrown();
        lettuce.resetTicketsThrown();
        FestivalLettuceEffects.playBurst(store, center);
    }

    private static void tickBurst(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull FestivalLettuceComponent lettuce,
        @Nonnull Vector3d center,
        long now
    ) {
        List<String> pool = lettuce.getBurstItemIds();
        int seedTotal = lettuce.getBurstSeedTarget();
        int ticketTotal = lettuce.getBurstTicketTarget();
        int dropTotal = seedTotal + ticketTotal;
        long elapsed = Math.max(0L, now - lettuce.getBurstStartEpochMs());
        int wanted =
            dropTotal <= 0
                ? 0
                : (int) Math.min(dropTotal, Math.round((double) dropTotal * elapsed / BURST_DURATION_MS) + 1L);
        int already = lettuce.getSeedsThrown() + lettuce.getTicketsThrown();
        int toThrow = wanted - already;
        if (toThrow > 0) {
            throwBurstDrops(store, commandBuffer, center, pool, lettuce, toThrow);
        }
        boolean seedsDone = lettuce.getSeedsThrown() >= seedTotal || pool.isEmpty();
        boolean ticketsDone = lettuce.getTicketsThrown() >= ticketTotal;
        if (elapsed < BURST_DURATION_MS && (!seedsDone || !ticketsDone) && dropTotal > 0) {
            return;
        }
        // Mark spent first so a late absorb tick cannot drink again before the remove lands.
        lettuce.setState(FestivalLettuceComponent.STATE_SPENT);
        lettuce.resetEssence();
        Ref<EntityStore> self = chunk.getReferenceTo(index);
        if (self != null && self.isValid()) {
            commandBuffer.removeEntity(self, RemoveReason.REMOVE);
        }
    }

    private static void throwBurstDrops(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d center,
        @Nonnull List<String> pool,
        @Nonnull FestivalLettuceComponent lettuce,
        int count
    ) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            boolean needSeeds = lettuce.getSeedsThrown() < lettuce.getBurstSeedTarget() && !pool.isEmpty();
            boolean needTickets = lettuce.getTicketsThrown() < lettuce.getBurstTicketTarget();
            if (!needSeeds && !needTickets) {
                return;
            }
            String itemId;
            if (needSeeds && (!needTickets || rnd.nextBoolean())) {
                itemId = pool.get(rnd.nextInt(pool.size()));
                lettuce.addSeedsThrown(1);
            } else {
                itemId = FestivalLettuceComponent.SPRING_TICKET_ITEM_ID;
                lettuce.addTicketsThrown(1);
            }
            spawnDrop(store, commandBuffer, center, itemId, rnd);
        }
    }

    private static void spawnDrop(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d center,
        @Nonnull String itemId,
        @Nonnull ThreadLocalRandom rnd
    ) {
        ItemStack stack = new ItemStack(itemId);
        if (!stack.isValid()) {
            return;
        }
        float[] v = launchVelocity(rnd);
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
            store,
            stack,
            new Vector3d(center.x, center.y + 1.0, center.z),
            new Rotation3f(0.0f, rnd.nextFloat() * (float) Math.PI * 2.0f, 0.0f),
            v[0],
            v[1],
            v[2]
        );
        if (holder != null) {
            commandBuffer.addEntity(holder, AddReason.SPAWN);
        }
    }

    /** A steep arc in a random direction, so seeds rain back down around the square. */
    @Nonnull
    static float[] launchVelocity(@Nonnull ThreadLocalRandom rnd) {
        double yaw = rnd.nextDouble(Math.PI * 2.0);
        double pitch = Math.toRadians(rnd.nextDouble(LAUNCH_PITCH_MIN_DEG, LAUNCH_PITCH_MAX_DEG));
        double speed = rnd.nextDouble(LAUNCH_SPEED_MIN, LAUNCH_SPEED_MAX);
        double horizontal = Math.cos(pitch) * speed;
        return new float[] {
            (float) (Math.cos(yaw) * horizontal),
            (float) (Math.sin(pitch) * speed),
            (float) (Math.sin(yaw) * horizontal)
        };
    }

    /** Slowest and fastest horizontal spread a burst seed can get, used to keep the fountain inside the square. */
    @Nonnull
    static double[] horizontalSpeedRange() {
        double minH = Math.cos(Math.toRadians(LAUNCH_PITCH_MAX_DEG)) * LAUNCH_SPEED_MIN;
        double maxH = Math.cos(Math.toRadians(LAUNCH_PITCH_MIN_DEG)) * LAUNCH_SPEED_MAX;
        return new double[] {minH, maxH};
    }
}
