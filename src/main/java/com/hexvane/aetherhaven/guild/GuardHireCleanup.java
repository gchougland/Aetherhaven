package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.autonomy.BlockMountRelease;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Clears guild hall display state when an adventurer is hired as a guard. */
public final class GuardHireCleanup {
    private GuardHireCleanup() {}

    public static void prepareForGuardDuty(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        store.tryRemoveComponent(npcRef, GuildHallDisplayAnchor.getComponentType());
        BlockMountRelease.release(npcRef, store, null);

        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc != null) {
            npc.playAnimation(npcRef, AnimationSlot.Status, null, store);
        }

        VillagerBlockUtil.snapNpcToStandY(npcRef, store);
    }

    @Nonnull
    public static Vector3d patrolLeashPoint(@Nonnull Store<EntityStore> store, @Nonnull PlotInstance guildPlot) {
        PlotFootprintRecord fp = guildPlot.toFootprint();
        double cx = (fp.getMinX() + fp.getMaxX()) * 0.5 + 0.5;
        double cz = (fp.getMinZ() + fp.getMaxZ()) * 0.5 + 0.5;
        World world = store.getExternalData().getWorld();
        int standY = VillagerBlockUtil.findStandY(world, (int) Math.floor(cx), (int) Math.floor(cz), guildPlot.getSignY() + 3);
        double y = standY != Integer.MIN_VALUE ? standY + 0.02 : guildPlot.getSignY() + 0.02;
        return new Vector3d(cx, y, cz);
    }

}
