package com.hexvane.aetherhaven.festival.lettuce;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Pops the festival lettuce NPC when the player uses F and it is full enough. */
public final class ActionBurstFestivalLettuce extends ActionBase {
    public ActionBurstFestivalLettuce(@Nonnull BuilderActionBase builder, @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean canExecute(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Role role,
        @Nullable InfoProvider sensorInfo,
        double dt,
        @Nonnull Store<EntityStore> store
    ) {
        return super.canExecute(ref, role, sensorInfo, dt, store)
            && role.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Role role,
        @Nullable InfoProvider sensorInfo,
        double dt,
        @Nonnull Store<EntityStore> store
    ) {
        super.execute(ref, role, sensorInfo, dt, store);
        FestivalLettuceComponent lettuce = store.getComponent(ref, FestivalLettuceComponent.getComponentType());
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (lettuce == null || tc == null) {
            return false;
        }
        boolean started =
            FestivalLettuceBurstSystem.tryBeginBurst(store, lettuce, new Vector3d(tc.getPosition()));
        if (started) {
            store.putComponent(ref, FestivalLettuceComponent.getComponentType(), lettuce);
        }
        return started;
    }
}
