package com.hexvane.aetherhaven.rts;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.setup.ClientFeature;
import com.hypixel.hytale.protocol.packets.setup.UpdateFeatures;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** RTS commander uses vanilla creative flight — no custom pan or locomotion stripping. */
public final class RtsMovementSupport {
    private static final float HORIZONTAL_FLY_SPEED = 24f;
    private static final float VERTICAL_FLY_SPEED = 16f;

    private RtsMovementSupport() {}

    public static void applyCommandModeProfile(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        disableSprintForce(playerRef);
        enableCreativeFlight(playerRef, entityRef, accessor);
        RtsDiagnostics.movementProfile(playerRef, true, true);
    }

    public static void restore(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        enableSprintForce(playerRef);
        restoreDefaultMovement(playerRef, entityRef, accessor);
        restoreFlyingState(playerRef, entityRef, accessor);
        RtsDiagnostics.movementProfile(playerRef, false, false);
    }

    public static void ensureFlying(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        MovementStatesComponent statesComponent =
            accessor.getComponent(entityRef, MovementStatesComponent.getComponentType());
        if (statesComponent == null) {
            return;
        }
        var states = statesComponent.getMovementStates();
        if (!states.flying || states.onGround) {
            Player.applyMovementStates(entityRef, new SavedMovementStates(true), states, accessor);
        }
    }

    private static void disableSprintForce(@Nonnull PlayerRef playerRef) {
        Map<ClientFeature, Boolean> features = new EnumMap<>(ClientFeature.class);
        features.put(ClientFeature.SprintForce, false);
        playerRef.getPacketHandler().writeNoCache(new UpdateFeatures(features));
    }

    private static void enableSprintForce(@Nonnull PlayerRef playerRef) {
        Map<ClientFeature, Boolean> features = new EnumMap<>(ClientFeature.class);
        features.put(ClientFeature.SprintForce, true);
        playerRef.getPacketHandler().writeNoCache(new UpdateFeatures(features));
    }

    private static void enableCreativeFlight(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        MovementManager movementManager = getMovementManager(entityRef, accessor);
        if (movementManager == null) {
            return;
        }
        movementManager.getDefaultSettings().fly = FlyMode.Allowed;
        MovementSettings settings = movementManager.getSettings();
        settings.fly = FlyMode.Allowed;
        settings.horizontalFlySpeed = HORIZONTAL_FLY_SPEED;
        settings.verticalFlySpeed = VERTICAL_FLY_SPEED;
        movementManager.update(playerRef.getPacketHandler());
        ensureFlying(entityRef, accessor);
    }

    private static void restoreDefaultMovement(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        MovementManager movementManager = getMovementManager(entityRef, accessor);
        if (movementManager == null) {
            return;
        }
        movementManager.resetDefaultsAndUpdate(entityRef, accessor);
    }

    private static void restoreFlyingState(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        Player player = accessor.getComponent(entityRef, Player.getComponentType());
        if (player != null && player.getGameMode() == GameMode.Creative) {
            return;
        }
        MovementStatesComponent statesComponent =
            accessor.getComponent(entityRef, MovementStatesComponent.getComponentType());
        if (statesComponent == null) {
            return;
        }
        var states = statesComponent.getMovementStates();
        if (states.flying) {
            Player.applyMovementStates(entityRef, new SavedMovementStates(false), states, accessor);
        }
    }

    @Nullable
    public static MovementManager getMovementManager(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        if (!entityRef.isValid()) {
            return null;
        }
        return accessor.getComponent(entityRef, MovementManager.getComponentType());
    }

    public static boolean isCanFlyEnabled(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        MovementManager movementManager = getMovementManager(entityRef, accessor);
        if (movementManager == null || movementManager.getSettings() == null) {
            return false;
        }
        return movementManager.getSettings().fly != FlyMode.Disabled;
    }
}
