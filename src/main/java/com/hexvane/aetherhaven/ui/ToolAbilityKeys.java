package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Which game inputs carry the extra actions of Aetherhaven's held tools.
 *
 * <p>Up to Hytale 0.6 a held item could bind three extra keys: {@code Ability1} (Q), {@code Ability2} (E) and
 * {@code Ability3} (R). Since 0.7.0-pre.5 the game gives {@code Ability2} and {@code Ability3} to the two rune ability
 * slots whenever any item is held ({@code InteractionContext.forInteraction} builds the context from the ability slots,
 * so the held item's own entries for those two types are never looked up). A held item keeps {@code Ability1} and gets
 * the new {@code Ability4} (vanilla moved the crossbow reload from {@code Ability3} to {@code Ability4}).
 *
 * <p>Tools therefore use: first extra action on {@link #FIRST}, second on {@link #SECOND}, and the third on
 * {@link #THIRD} while crouching. Item JSON binds {@code Ability1} and {@code Ability4}; the interaction bound to
 * {@code Ability1} runs the tool's third action instead when {@link #thirdActionHeld} is true.
 */
public final class ToolAbilityKeys {
    /** First extra tool action. Client binding {@code Ability1ItemAction}, Q by default. */
    @Nonnull
    public static final InteractionType FIRST = InteractionType.Ability1;

    /** Second extra tool action. Client binding {@code Ability4ItemAction}, H by default. */
    @Nonnull
    public static final InteractionType SECOND = InteractionType.Ability4;

    /** Third extra tool action: the {@link #FIRST} input pressed while crouching. */
    @Nonnull
    public static final InteractionType THIRD = FIRST;

    /** Inputs the game routes to the rune ability slots while an item is held. A held item must not rely on them. */
    @Nonnull
    public static final Set<InteractionType> RUNE_OWNED =
        Collections.unmodifiableSet(EnumSet.of(InteractionType.Ability2, InteractionType.Ability3));

    private ToolAbilityKeys() {}

    /** True when the player is crouching, which turns the first extra key into the tool's third action. */
    public static boolean thirdActionHeld(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        MovementStatesComponent movement = accessor.getComponent(playerRef, MovementStatesComponent.getComponentType());
        return thirdActionHeld(movement != null ? movement.getMovementStates() : null);
    }

    static boolean thirdActionHeld(@Nullable MovementStates states) {
        return states != null && (states.crouching || states.forcedCrouching);
    }
}
