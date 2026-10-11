package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.townsfolk.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("town")
class SeedCraftingRecipesTest {
    @Test void allSeedRecipesUseFarmingAtCraftingAndSpendEssence() throws Exception {
        try(var files=Files.list(Path.of("src/main/resources/Server/Aetherhaven/WorkplaceRecipes"))) {
            var recipes=files.filter(p->p.getFileName().toString().startsWith("grow_plant_")).toList();
            assertEquals(108,recipes.size());
            for(var path:recipes) {
                var recipe=new Gson().fromJson(Files.readString(path),WorkplaceRecipe.class);
                assertTrue(recipe.valid(),path.toString()); assertEquals(TownsfolkWorkType.CRAFTING,recipe.type());
                assertEquals(TownsfolkSkill.FARMING,recipe.skill()); assertEquals(1,recipe.inputs().size());
                var alchemy=java.util.regex.Pattern.compile("Plant_Seeds_(Health|Mana|Stamina)([123])").matcher(recipe.output());
                if(alchemy.matches()) {
                    // The base game's Alchemy Bench recipe: 2 Void Essence for 3, 4 for 2, 8 for 1.
                    int tier=Integer.parseInt(alchemy.group(2));
                    assertEquals("Ingredient_Void_Essence",recipe.inputs().getFirst().itemId());
                    assertEquals(tier==1?2:tier==2?4:8,recipe.inputs().getFirst().count()); assertEquals(tier==1?3:tier==2?2:1,recipe.outputCount());
                    continue;
                }
                assertEquals(1,recipe.outputCount()); assertEquals("Ingredient_Life_Essence",recipe.inputs().getFirst().itemId());
                assertTrue(recipe.inputs().getFirst().count()>0);
            }
        }
    }
}
