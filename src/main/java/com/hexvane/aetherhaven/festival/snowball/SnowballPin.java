package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Holds a villager still for a snowball fight via cooperative role stand-still (not Frozen). */
final class SnowballPin {
    private SnowballPin() {}

    static void hold(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc,
        @Nonnull SnowballSession.StartPad pad
    ) {
        NpcStandStill.maintainHold(
            ref,
            npc,
            new Vector3d(pad.x(), pad.y(), pad.z()),
            commandBuffer
        );
    }

    static void start(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull SnowballSession.StartPad pad
    ) {
        NpcStandStill.hold(
            ref,
            store,
            npc,
            new Vector3d(pad.x(), pad.y(), pad.z()),
            commandBuffer
        );
        NpcAnimationPlayback.stop(ref, AnimationSlot.Movement, commandBuffer);
    }

    static void unpin(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        NpcStandStill.release(ref, npc, commandBuffer);
    }
}
