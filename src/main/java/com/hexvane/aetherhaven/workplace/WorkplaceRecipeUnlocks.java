package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.town.TownRecord;
import java.util.Collection;

/** Town recipes never enter the player's native crafting recipe list. */
public final class WorkplaceRecipeUnlocks {
    public static final String PREFIX = "workplace:";
    private WorkplaceRecipeUnlocks() {}

    public static boolean isWorkplaceOutput(Collection<WorkplaceRecipe> recipes, String itemId) {
        return recipes.stream().anyMatch(r -> r.townOnly() && itemId.equals(r.output()));
    }

    /** Returns whether this output belongs to town crafting, including already known recipes. */
    public static boolean grant(TownRecord town, Collection<WorkplaceRecipe> recipes, String itemId) {
        boolean found = false;
        for (var recipe : recipes) {
            if (!recipe.townOnly() || !itemId.equals(recipe.output())) continue;
            found = true;
            town.addTownSharedCraftRecipeItemId(PREFIX + recipe.id());
        }
        return found;
    }
}
