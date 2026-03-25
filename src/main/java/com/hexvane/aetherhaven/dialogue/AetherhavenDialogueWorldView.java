package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves dialogue conditions from the player's Aetherhaven town in this world. */
public final class AetherhavenDialogueWorldView implements DialogueWorldView {
    private final World world;
    private final AetherhavenPlugin plugin;
    private final DialogueWorldView base = new DialogueWorldView.DefaultDialogueWorldView();

    public AetherhavenDialogueWorldView(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        this.world = world;
        this.plugin = plugin;
    }

    @Nullable
    private TownRecord townFor(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        UUIDComponent uuidComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            return null;
        }
        UUID owner = uuidComp.getUuid();
        return AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForOwnerInWorld(owner);
    }

    @Override
    public boolean hasAchievement(@Nonnull String id) {
        return base.hasAchievement(id);
    }

    @Override
    public boolean getFlag(@Nonnull String id) {
        return base.getFlag(id);
    }

    @Override
    public boolean hasItem(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String itemId, int minCount
    ) {
        return base.hasItem(playerRef, store, itemId, minCount);
    }

    @Override
    public boolean isVillagerInTown(@Nonnull String villagerId) {
        return base.isVillagerInTown(villagerId);
    }

    @Override
    public boolean townQuestActive(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        TownRecord t = townFor(playerRef, store);
        return t != null && t.hasQuestActive(questId.trim());
    }

    @Override
    public boolean townQuestCompleted(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String questId
    ) {
        TownRecord t = townFor(playerRef, store);
        return t != null && t.hasQuestCompleted(questId.trim());
    }

    @Override
    public boolean townHasCompletePlot(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull String constructionId
    ) {
        TownRecord t = townFor(playerRef, store);
        return t != null && t.hasCompletePlotWithConstruction(constructionId.trim());
    }

    @Override
    public boolean aetherhavenHasTown(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return townFor(playerRef, store) != null;
    }
}
