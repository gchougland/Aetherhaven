package com.hexvane.aetherhaven.construction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.window.SimpleCraftingWindow;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;

/** Town planning desk crafting window with pinned starter recipes in the misc tab. */
public final class TownPlanningCraftingWindow extends SimpleCraftingWindow {

    private static final String MISC_CATEGORY_ID = "Aetherhaven_TownPlanning_Misc";
    private static final String CHARTER_RECIPE_ID = "Aetherhaven_Charter";
    private static final String PLOT_BENCH_RECIPE_ID = "Aetherhaven_Plot_Crafting_Bench";

    public TownPlanningCraftingWindow(
        int x,
        int y,
        int z,
        int rotationIndex,
        @Nonnull BlockType blockType,
        @Nonnull BenchBlock benchBlock
    ) {
        super(x, y, z, rotationIndex, blockType, benchBlock);
        sortMiscRecipes(windowData);
    }

    private static void sortMiscRecipes(@Nonnull JsonObject windowData) {
        JsonElement categoriesElement = windowData.get("categories");
        if (categoriesElement == null || !categoriesElement.isJsonArray()) {
            return;
        }

        JsonArray categories = categoriesElement.getAsJsonArray();
        for (JsonElement categoryElement : categories) {
            if (!categoryElement.isJsonObject()) {
                continue;
            }
            JsonObject category = categoryElement.getAsJsonObject();
            JsonElement idElement = category.get("id");
            if (idElement == null || !MISC_CATEGORY_ID.equals(idElement.getAsString())) {
                continue;
            }

            JsonElement recipesElement = category.get("craftableRecipes");
            if (recipesElement == null || !recipesElement.isJsonArray()) {
                return;
            }

            List<String> recipeIds = new ObjectArrayList<>();
            for (JsonElement recipeElement : recipesElement.getAsJsonArray()) {
                recipeIds.add(recipeElement.getAsString());
            }
            recipeIds.sort(TownPlanningCraftingWindow::compareMiscRecipeOrder);

            JsonArray sorted = new JsonArray();
            for (String recipeId : recipeIds) {
                sorted.add(recipeId);
            }
            category.add("craftableRecipes", sorted);
            return;
        }
    }

    private static int compareMiscRecipeOrder(@Nonnull String left, @Nonnull String right) {
        int leftPriority = miscRecipePriority(left);
        int rightPriority = miscRecipePriority(right);
        if (leftPriority != rightPriority) {
            return Integer.compare(leftPriority, rightPriority);
        }
        return left.compareTo(right);
    }

    private static int miscRecipePriority(@Nonnull String recipeId) {
        if (CHARTER_RECIPE_ID.equals(recipeId)) {
            return 0;
        }
        if (PLOT_BENCH_RECIPE_ID.equals(recipeId)) {
            return 1;
        }
        return 2;
    }
}
