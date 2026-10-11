package com.hexvane.aetherhaven.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.InstructionType;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import java.util.EnumSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class BuilderActionSetAetherhavenInteractable extends BuilderActionBase {
    @Nullable
    protected String hint;

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Let the iterated player interact with this townsfolk, with a prompt that fits what the interaction does";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return this.getShortDescription();
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport builderSupport) {
        return new ActionSetAetherhavenInteractable(this, builderSupport);
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    @Override
    public BuilderActionSetAetherhavenInteractable readConfig(@Nonnull JsonElement data) {
        this.getString(
            data,
            "Hint",
            s -> this.hint = s,
            null,
            null,
            BuilderDescriptorState.Stable,
            "The interaction hint translation key shown when the townsfolk is free to talk",
            null
        );
        this.requireInstructionType(EnumSet.of(InstructionType.Interaction));
        return this;
    }
}
