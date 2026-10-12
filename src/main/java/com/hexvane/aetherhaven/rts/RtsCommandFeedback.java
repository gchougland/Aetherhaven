package com.hexvane.aetherhaven.rts;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.SoundCategory;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

final class RtsCommandFeedback {
    private static final String FOCUS_COMMANDER_SOUND = "SFX_Creative_Play_Eyedropper_Select";
    private static final String FOCUS_TARGET_SOUND = "SFX_Sword_T2_Impact";
    private static final String MOVE_ORDER_COMMANDER_SOUND = "SFX_Creative_Play_Add_Mask";
    private static final String MOVE_ORDER_WORLD_SOUND = "SFX_Tool_Watering_Can_Water";
    private static final String BOX_SELECT_SOUND = "SFX_Creative_Play_Eyedropper_Select";
    private static final String PROFILE_SELECT_SOUND = "SFX_UI_Click";
    private static final String STANCE_SOUND = "SFX_Workbench_Upgrade_Complete_Default";
    private static final String FREE_SOUND = "SFX_Creative_Play_Remove_Mask";
    private     static final String TOOL_EQUIP_SOUND = "SFX_UI_Click";
    /** Played with the reason whenever a press cannot do anything, so no press is silent. */
    private static final String DENIED_SOUND = "SFX_Creative_Play_Error";

    private RtsCommandFeedback() {}

    static void playDenied(@Nonnull Ref<EntityStore> commanderRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        play2dToPlayer(commanderRef, accessor, DENIED_SOUND);
    }

    static void playFocusAttack(
        @Nonnull Ref<EntityStore> commanderRef,
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        play2dToPlayer(commanderRef, accessor, FOCUS_COMMANDER_SOUND);
        TransformComponent tc = accessor.getComponent(targetRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d pos = tc.getPosition();
        play3d(null, FOCUS_TARGET_SOUND, pos, accessor);
    }

    static void playMoveOrder(
        @Nonnull Ref<EntityStore> commanderRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        double x,
        double y,
        double z
    ) {
        play2dToPlayer(commanderRef, accessor, MOVE_ORDER_COMMANDER_SOUND);
        play3d(null, MOVE_ORDER_WORLD_SOUND, new Vector3d(x, y, z), accessor);
    }

    static void playBoxSelect(@Nonnull Ref<EntityStore> commanderRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        play2dToPlayer(commanderRef, accessor, BOX_SELECT_SOUND);
    }

    static void playProfileSelect(@Nonnull Ref<EntityStore> commanderRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        play2dToPlayer(commanderRef, accessor, PROFILE_SELECT_SOUND);
    }

    static void playStanceChange(@Nonnull Ref<EntityStore> commanderRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        play2dToPlayer(commanderRef, accessor, STANCE_SOUND);
    }

    static void playFreeGuards(@Nonnull Ref<EntityStore> commanderRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        play2dToPlayer(commanderRef, accessor, FREE_SOUND);
    }

    static void playToolEquip(@Nonnull Ref<EntityStore> commanderRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        play2dToPlayer(commanderRef, accessor, TOOL_EQUIP_SOUND);
    }

    private static void play2dToPlayer(
        @Nonnull Ref<EntityStore> commanderRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull String soundId
    ) {
        PlayerRef pr = accessor.getComponent(commanderRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        int uiIdx = SoundEvent.getAssetMap().getIndex(soundId);
        if (uiIdx != Integer.MIN_VALUE && uiIdx != SoundEvent.EMPTY_ID) {
            SoundUtil.playSoundEvent2dToPlayer(pr, uiIdx, SoundCategory.SFX);
        }
    }

    private static void play3d(
        @Nullable Ref<EntityStore> sourceRef,
        @Nonnull String soundId,
        @Nonnull Vector3d pos,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        int worldIdx = SoundEvent.getAssetMap().getIndex(soundId);
        if (worldIdx != Integer.MIN_VALUE && worldIdx != SoundEvent.EMPTY_ID) {
            SoundUtil.playSoundEvent3d(sourceRef, worldIdx, pos, accessor);
        }
    }
}
