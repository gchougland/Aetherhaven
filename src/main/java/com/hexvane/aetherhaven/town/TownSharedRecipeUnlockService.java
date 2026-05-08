package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Town-wide quest recipe unlocks: {@code CraftingPlugin.learnRecipe} requires an in-world player {@link Ref}, so
 * offline roster members get queued on {@link TownRecord} until {@link #tryFlushPendingCraftRecipes} runs (e.g. player
 * tick). Shared recipe ids also seed {@linkplain TownRecord#seedSharedCraftRecipeUnlocksForMember new members}.
 */
public final class TownSharedRecipeUnlockService {
    private TownSharedRecipeUnlockService() {}

    /**
     * Records {@code recipeItemId} as a town-shared unlock and applies it to every owner and member who is currently
     * loaded as a player in {@code store}; others get a pending entry.
     */
    public static void grantTownWideLearnRecipe(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull String recipeItemId
    ) {
        town.migrateInnFieldsIfNeeded();
        String rid = recipeItemId.trim();
        if (rid.isEmpty()) {
            return;
        }
        town.addTownSharedCraftRecipeItemId(rid);
        List<UUID> roster = new ArrayList<>();
        roster.add(town.getOwnerUuid());
        roster.addAll(town.getMemberPlayerUuids());
        for (UUID u : roster) {
            Ref<EntityStore> pref = store.getExternalData().getRefFromUUID(u);
            if (pref != null && pref.isValid() && store.getComponent(pref, Player.getComponentType()) != null) {
                CraftingPlugin.learnRecipe(pref, rid, store);
            } else {
                town.queuePendingCraftRecipeUnlock(u, rid);
            }
        }
        tm.updateTown(town);
    }

    /**
     * Applies pending town-shared recipes for {@code playerUuid} to {@code playerRef} and persists if anything was
     * pending.
     *
     * @return true if the town was saved (pending list was non-empty before flush)
     */
    public static boolean tryFlushPendingCraftRecipes(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID playerUuid
    ) {
        town.migrateInnFieldsIfNeeded();
        if (!town.hasPendingCraftRecipeUnlocks(playerUuid)) {
            return false;
        }
        List<String> pending = town.takeAndClearPendingCraftRecipeUnlocks(playerUuid);
        if (pending.isEmpty()) {
            return false;
        }
        for (String recipeId : pending) {
            if (recipeId == null || recipeId.isBlank()) {
                continue;
            }
            CraftingPlugin.learnRecipe(playerRef, recipeId.trim(), store);
        }
        tm.updateTown(town);
        return true;
    }
}
