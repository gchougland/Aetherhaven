package com.hexvane.aetherhaven.npc;

import com.hexvane.aetherhaven.questboard.RescueCampService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Makes a townsfolk interactable, with a prompt that says what the interaction will do. It is the game's own
 * SetInteractable for everyone except a townsfolk tied up at a rescue camp, whose prompt is to cut the ropes.
 *
 * <p>One action has to choose the prompt. Two SetInteractable actions with different hints would each resend theirs
 * every tick.
 */
public final class ActionSetAetherhavenInteractable extends ActionBase {
    /** Full key of the prompt shown on a bound captive. */
    public static final String CUT_LOOSE_HINT = "aetherhaven_misc.aetherhaven.interaction.cutLoose";

    @Nullable
    private final String hint;

    public ActionSetAetherhavenInteractable(@Nonnull BuilderActionSetAetherhavenInteractable builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.hint = builder.hint;
    }

    @Override
    public boolean canExecute(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ExecutionSupport executionSupport,
        InfoProvider sensorInfo,
        double dt,
        @Nonnull Store<EntityStore> store
    ) {
        return super.canExecute(ref, executionSupport, sensorInfo, dt, store)
            && executionSupport.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ExecutionSupport executionSupport,
        InfoProvider sensorInfo,
        double dt,
        @Nonnull Store<EntityStore> store
    ) {
        super.execute(ref, executionSupport, sensorInfo, dt, store);
        var state = executionSupport.getStateSupport();
        UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
        boolean bound = id != null && RescueCampService.isTied(id.getUuid());
        state.setInteractable(ref, state.getInteractionIterationTarget(), true, bound ? CUT_LOOSE_HINT : hint, true, store);
        return true;
    }
}
