package com.hexvane.aetherhaven.patrol;

import com.google.gson.JsonElement;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.rts.RtsGuardCombatSupport;
import com.hexvane.aetherhaven.rts.RtsHostileQuery;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;

/** Native role acquisition and attack instructions must obey the same escort policy as Java targeting. */
public final class BuilderSensorGuardEscortGate extends BuilderSensorBase {
    private final boolean combat;

    public BuilderSensorGuardEscortGate(boolean combat) { this.combat = combat; }

    @Override @Nonnull public String getShortDescription() { return "Respect the guard escort's combat orders"; }
    @Override @Nonnull public String getLongDescription() { return getShortDescription(); }
    @Override @Nonnull public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.Stable; }
    @Override @Nonnull public BuilderSensorGuardEscortGate readConfig(JsonElement data) { return this; }
    @Override @Nonnull public Sensor build(BuilderSupport support) { return new Gate(this); }

    private static final class Gate extends SensorBase {
        private final boolean combat;
        Gate(BuilderSensorGuardEscortGate builder) { super(builder); combat = builder.combat; }
        @Override public InfoProvider getSensorInfo() { return null; }

        @Override public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport execution,
                                         double dt, @Nonnull Store<EntityStore> store) {
            if (!super.matches(ref, execution, dt, store)) return false;
            GuardFollowPlayerState escort = store.getComponent(ref, GuardFollowPlayerState.getComponentType());
            if (escort == null || !escort.isActive()) return true;
            if (!combat || escort.isWaiting()) return false;
            var target = NpcSupportUtil.markedEntitySupport(ref, store)
                .getMarkedEntityRef(RtsGuardCombatSupport.LOCKED_TARGET_SLOT);
            return target != null && target.isValid() && RtsHostileQuery.isGuardThreatTarget(ref, target, store);
        }
    }
}
