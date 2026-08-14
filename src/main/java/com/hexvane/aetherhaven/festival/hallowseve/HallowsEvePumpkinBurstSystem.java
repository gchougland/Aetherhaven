package com.hexvane.aetherhaven.festival.hallowseve;

import com.hexvane.aetherhaven.festival.lettuce.FestivalLettuceEffects;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Pops a ready jack o lantern into autumn tickets and spooky candy, then resets it for the next run. */
public final class HallowsEvePumpkinBurstSystem extends EntityTickingSystem<EntityStore> {
    private static final long BURST_DURATION_MS = 1_400L;
    private static final float LAUNCH_SPEED_MIN = 6.0f;
    private static final float LAUNCH_SPEED_MAX = 10.0f;
    private static final float LAUNCH_PITCH_MIN_DEG = 55.0f;
    private static final float LAUNCH_PITCH_MAX_DEG = 80.0f;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(HallowsEvePumpkinComponent.getComponentType(), TransformComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        HallowsEvePumpkinComponent pumpkin = chunk.getComponent(index, HallowsEvePumpkinComponent.getComponentType());
        TransformComponent tc = chunk.getComponent(index, TransformComponent.getComponentType());
        if (pumpkin == null || tc == null || !pumpkin.isBursting()) {
            return;
        }
        long now = System.currentTimeMillis();
        Vector3d center = new Vector3d(tc.getPosition());
        tickBurst(store, commandBuffer, pumpkin, center, now);
        UUID townId = pumpkin.getTownId();
        if (!pumpkin.isBursting() && townId != null) {
            HallowsEveSession session = HallowsEveSessionIndex.get(townId);
            if (session != null) {
                session.finishBurst();
            }
        }
    }

    public static boolean tryBeginBurst(
        @Nonnull Store<EntityStore> store,
        @Nonnull HallowsEvePumpkinComponent pumpkin,
        @Nonnull Vector3d center
    ) {
        if (!pumpkin.isReady()) {
            return false;
        }
        UUID townId = pumpkin.getTownId();
        HallowsEveSession session = townId != null ? HallowsEveSessionIndex.get(townId) : null;
        int collected = session != null ? session.getCollected() : 0;
        int total = session != null ? session.getTotalOrbs() : 0;
        pumpkin.setBurstTicketTarget(HallowsEveRewards.ticketCount(collected, total));
        pumpkin.setBurstCandyTarget(HallowsEveRewards.candyCount(collected));
        pumpkin.resetTicketsThrown();
        pumpkin.resetCandyThrown();
        pumpkin.setBurstStartEpochMs(System.currentTimeMillis());
        pumpkin.setState(HallowsEvePumpkinComponent.STATE_BURSTING);
        if (session != null) {
            session.beginBurst(System.currentTimeMillis());
        }
        FestivalLettuceEffects.playBurst(store, center);
        return true;
    }

    private static void tickBurst(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull HallowsEvePumpkinComponent pumpkin,
        @Nonnull Vector3d center,
        long now
    ) {
        int ticketTotal = pumpkin.getBurstTicketTarget();
        int candyTotal = pumpkin.getBurstCandyTarget();
        int dropTotal = ticketTotal + candyTotal;
        long elapsed = Math.max(0L, now - pumpkin.getBurstStartEpochMs());
        int wanted =
            dropTotal <= 0
                ? 0
                : (int) Math.min(dropTotal, Math.round((double) dropTotal * elapsed / BURST_DURATION_MS) + 1L);
        int already = pumpkin.getTicketsThrown() + pumpkin.getCandyThrown();
        int toThrow = wanted - already;
        if (toThrow > 0) {
            throwBurstDrops(store, commandBuffer, center, pumpkin, toThrow);
        }
        boolean ticketsDone = pumpkin.getTicketsThrown() >= ticketTotal;
        boolean candyDone = pumpkin.getCandyThrown() >= candyTotal;
        if (elapsed < BURST_DURATION_MS && (!ticketsDone || !candyDone) && dropTotal > 0) {
            return;
        }
        pumpkin.resetForNextRun();
    }

    private static void throwBurstDrops(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d center,
        @Nonnull HallowsEvePumpkinComponent pumpkin,
        int count
    ) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            boolean needTickets = pumpkin.getTicketsThrown() < pumpkin.getBurstTicketTarget();
            boolean needCandy = pumpkin.getCandyThrown() < pumpkin.getBurstCandyTarget();
            if (!needTickets && !needCandy) {
                return;
            }
            String itemId;
            if (needTickets && (!needCandy || rnd.nextBoolean())) {
                itemId = HallowsEveIds.AUTUMN_TICKET_ITEM_ID;
                pumpkin.addTicketsThrown(1);
            } else {
                itemId = HallowsEveIds.CANDY_ITEM_ID;
                pumpkin.addCandyThrown(1);
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
}
