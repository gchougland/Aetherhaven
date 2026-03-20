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

public final class BuilderActionOpenAetherhavenDialogue extends BuilderActionBase {
    @Nullable
    protected String dialogueId;
    @Nullable
    protected String villagerKind;

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Open Aetherhaven dialogue for the interacting player";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return this.getShortDescription();
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport builderSupport) {
        return new ActionOpenAetherhavenDialogue(this, builderSupport);
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    @Override
    public BuilderActionOpenAetherhavenDialogue readConfig(@Nonnull JsonElement data) {
        // readCommonConfig is already invoked by BuilderBase.readConfig before readConfig.
        this.getString(
            data,
            "DialogueId",
            s -> this.dialogueId = s,
            null,
            null,
            BuilderDescriptorState.Stable,
            "Fixed dialogue tree id (optional if VillagerKind resolves one)",
            null
        );
        this.getString(
            data,
            "VillagerKind",
            s -> this.villagerKind = s,
            "test_villager",
            null,
            BuilderDescriptorState.Stable,
            "Resolver key for which dialogue tree to open",
            null
        );
        this.requireInstructionType(EnumSet.of(InstructionType.Interaction));
        return this;
    }
}
