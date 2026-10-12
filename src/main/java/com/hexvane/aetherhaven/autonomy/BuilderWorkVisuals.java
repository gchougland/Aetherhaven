package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.equipment.data.EquipmentProfileCatalog;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What a townsfolk builder holds and how they move while raising a queued project. Which piece is built, how
 * fast and from where is decided by {@link ApprenticeWorkService}; this class only shows it.
 *
 * <p>The tool and the motion come from the {@code work_builder} equipment profile: the native iron hammer with
 * its own model and grip, and the {@code Hammer} clip of the mod's work animation set. The hammer is a temporary
 * hand item like a courier's cargo, so it is never dropped, looted or saved as real equipment.
 */
public final class BuilderWorkVisuals {
    public static final String WORK_PROFILE_ID = "work_builder";
    /** Used only when a pack removes the profile or empties its hotbar. */
    static final String FALLBACK_HAMMER_ITEM_ID = "Tool_Hammer_Iron";
    static final String FALLBACK_ANIMATIONS_ID = "Aetherhaven_Work_Actions";
    static final String FALLBACK_ANIMATION_ID = "Hammer";
    static final long FALLBACK_INTERVAL_MS = 1000L;
    /**
     * {@code nextVisual} value meaning the hammer is already in hand for the walk and the first strike is due on
     * arrival. Zero means nothing is shown yet; real deadlines are clock times far above this.
     */
    public static final long CARRIED_STRIKE_DUE = 1L;

    private BuilderWorkVisuals() {}

    /** The tool a builder shows. Read from data so a pack can change it without code. */
    @Nonnull
    public static String hammerItemId(@Nullable EquipmentProfileCatalog catalog) {
        EquipmentProfileDefinition profile = catalog == null ? null : catalog.byId(WORK_PROFILE_ID);
        String item = profile == null ? null : profile.getHeldToolItemId();
        return item != null ? item : FALLBACK_HAMMER_ITEM_ID;
    }

    @Nonnull
    public static String animationsId(@Nullable EquipmentProfileCatalog catalog) {
        EquipmentProfileDefinition.WorkAnimation work = workAnimation(catalog);
        return work != null ? work.getAnimationsId() : FALLBACK_ANIMATIONS_ID;
    }

    @Nonnull
    public static String animationId(@Nullable EquipmentProfileCatalog catalog) {
        EquipmentProfileDefinition.WorkAnimation work = workAnimation(catalog);
        return work != null ? work.getAnimationId() : FALLBACK_ANIMATION_ID;
    }

    /** Time between the starts of two passes of the clip. Matches the clip length so strikes follow on. */
    public static long strikeIntervalMs(@Nullable EquipmentProfileCatalog catalog) {
        EquipmentProfileDefinition.WorkAnimation work = workAnimation(catalog);
        return work != null ? work.getIntervalMs() : FALLBACK_INTERVAL_MS;
    }

    @Nullable
    private static EquipmentProfileDefinition.WorkAnimation workAnimation(@Nullable EquipmentProfileCatalog catalog) {
        EquipmentProfileDefinition profile = catalog == null ? null : catalog.byId(WORK_PROFILE_ID);
        return profile == null ? null : profile.getWorkAnimation();
    }

    /** Walking to the next piece: hammer in hand, no strike. Runs after the tick, like every held item change. */
    public static void carry(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> buffer,
        @Nullable EquipmentProfileCatalog catalog
    ) {
        String hammer = hammerItemId(catalog);
        buffer.run(store -> {
            if (ref.isValid()) {
                VillagerLifeProps.carry(ref, hammer, store);
            }
        });
    }

    /**
     * Standing at the piece: make sure the hammer is in hand, then play one pass of the hammering clip. Both happen
     * in one deferred step so the client never receives the swing before the item.
     */
    public static void strike(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> buffer,
        @Nullable EquipmentProfileCatalog catalog
    ) {
        String hammer = hammerItemId(catalog);
        String animations = animationsId(catalog);
        String animation = animationId(catalog);
        buffer.run(store -> {
            if (!ref.isValid()) {
                return;
            }
            VillagerLifeProps.carry(ref, hammer, store);
            AnimationUtils.playAnimation(ref, AnimationSlot.Action, animations, animation, true, store);
        });
    }
}
