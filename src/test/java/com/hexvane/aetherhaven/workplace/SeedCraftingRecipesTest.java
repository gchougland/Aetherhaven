package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.townsfolk.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("town")
class SeedCraftingRecipesTest {
    @Test void allSeedRecipesUseFarmingAtCraftingAndSpendLifeEssence() throws Exception {
        try(var files=Files.list(Path.of("src/main/resources/Server/Aetherhaven/WorkplaceRecipes"))) {
            var recipes=files.filter(p->p.getFileName().toString().startsWith("grow_plant_")).toList();
            assertEquals(108,recipes.size());
            for(var path:recipes) {
                var recipe=new Gson().fromJson(Files.readString(path),WorkplaceRecipe.class);
                assertTrue(recipe.valid(),path.toString()); assertEquals(TownsfolkWorkType.CRAFTING,recipe.type());
                assertEquals(TownsfolkSkill.FARMING,recipe.skill()); assertEquals(1,recipe.outputCount());
                assertEquals(1,recipe.inputs().size()); assertEquals("Ingredient_Life_Essence",recipe.inputs().getFirst().itemId());
                assertTrue(recipe.inputs().getFirst().count()>0);
            }
        }
    }
}
