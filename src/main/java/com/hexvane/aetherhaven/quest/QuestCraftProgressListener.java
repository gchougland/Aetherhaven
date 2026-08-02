package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.hud.AetherhavenHudRefreshSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.event.events.player.PlayerCraftEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Advances save wide player quest craft objectives when a player finishes a bench recipe. */
public final class QuestCraftProgressListener {
    private QuestCraftProgressListener() {}

    public static void register(@Nonnull AetherhavenPlugin plugin) {
        plugin
            .getEventRegistry()
            .registerGlobal(
                PlayerCraftEvent.class,
                event -> {
                    Ref<EntityStore> playerRef = event.getPlayerRef();
                    if (playerRef == null || !playerRef.isValid()) {
                        return;
                    }
                    Store<EntityStore> store = playerRef.getStore();
                    World world = store.getExternalData().getWorld();
                    if (world == null) {
                        return;
                    }
                    CraftingRecipe recipe = event.getCraftedRecipe();
                    if (recipe == null) {
                        return;
                    }
                    world.execute(
                        () -> {
                            PlayerQuestProgress progress =
                                store.getComponent(playerRef, PlayerQuestProgress.getComponentType());
                            if (progress == null) {
                                return;
                            }
                            boolean changed = false;
                            for (var output : recipe.getOutputs()) {
                                if (output == null || output.getItemId() == null || output.getItemId().isBlank()) {
                                    continue;
                                }
                                changed |=
                                    PlayerQuestProgressionService.onItemCrafted(
                                        plugin,
                                        progress,
                                        output.getItemId().trim()
                                    );
                            }
                            if (changed) {
                                changed |= PlayerQuestProgressionService.tryCompleteActiveQuests(plugin, progress);
                                store.putComponent(playerRef, PlayerQuestProgress.getComponentType(), progress);
                                AetherhavenHudRefreshSystem.requestRefresh(world);
                            }
                        });
                });
    }
}
