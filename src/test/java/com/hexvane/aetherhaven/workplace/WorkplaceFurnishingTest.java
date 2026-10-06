package com.hexvane.aetherhaven.workplace;

import com.google.gson.*;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class WorkplaceFurnishingTest {
    private final Gson gson=new Gson();
    private final WorkplaceFurnishing cabinet=new WorkplaceFurnishing("cabinet","Cabinet","crafting",.1);
    private final WorkplaceFurnishing frame=new WorkplaceFurnishing("frame","Frame","leatherworking",.1);
    @Test void duplicatesAndOtherProfessionsDoNotIncreaseBonus() {
        var equipment=new WorkplaceEquipment.Snapshot(true,List.of(),List.of(cabinet,cabinet,frame));
        assertEquals(1.1,equipment.workMultiplier(TownsfolkWorkType.CRAFTING),.000001);
        assertEquals(1.1,equipment.workMultiplier(TownsfolkWorkType.LEATHERWORKING),.000001);
        assertEquals(1,equipment.workMultiplier(TownsfolkWorkType.BLACKSMITHING));
        assertNotNull(equipment.requirement("Workbench",1),"Furnishings never replace a bench");
        assertEquals(1,new WorkplaceEquipment.Snapshot(false,List.of(),List.of(cabinet)).workMultiplier(TownsfolkWorkType.CRAFTING));
        assertEquals("Cabinet",equipment.furnishingSummary(TownsfolkWorkType.CRAFTING));
    }
    @Test void packOverridesAreValidatedAndDifferentFurnishingsDoNotCompound() {
        var improved=new WorkplaceFurnishing("cabinet","Improved Cabinet","crafting",.2);
        var tooStrong=new WorkplaceFurnishing("bad","Bad","crafting",1);
        var catalog=new WorkplaceFurnishingCatalog(List.of(cabinet,improved,tooStrong));
        assertEquals(improved,catalog.find("cabinet"));assertNull(catalog.find("bad"));
        assertEquals(1.2,new WorkplaceEquipment.Snapshot(true,List.of(),List.of(cabinet,improved)).workMultiplier(TownsfolkWorkType.CRAFTING),.000001);
        for(String type:List.of("smelting","logistics","farming","ranching","cultivation","mining","unknown"))
            assertFalse(new WorkplaceFurnishing("block","Invalid",type,.1).valid(),type);
        for(double bonus:new double[]{0,-.1,Double.NaN,Double.POSITIVE_INFINITY,.26})
            assertFalse(new WorkplaceFurnishing("block","Invalid","crafting",bonus).valid());
    }
    @Test void shippedFurnishingsHaveTownOnlyPlansOriginalAssetsAndIndependentPersistentUnlocks() throws Exception {
        Path root=Path.of("src/main/resources");var recipes=new ArrayList<WorkplaceRecipe>();
        for(String kind:List.of("crafter","leatherworker")) {
            var definition=gson.fromJson(Files.readString(root.resolve("Server/Aetherhaven/WorkplaceFurnishings/"+kind+".json")),WorkplaceFurnishing.class);
            assertTrue(definition.valid());assertEquals(.1,definition.workSpeedBonus());
            var recipe=gson.fromJson(Files.readString(root.resolve("Server/Aetherhaven/WorkplaceRecipes/craft_"+kind+"_furnishing.json")),WorkplaceRecipe.class);
            assertTrue(recipe.valid());assertTrue(recipe.townOnly());assertTrue(recipe.requiresKnowledge());
            assertEquals(5,recipe.skillLevel());assertEquals(definition.blockId(),recipe.output());recipes.add(recipe);
            var item=JsonParser.parseString(Files.readString(root.resolve("Server/Item/Items/Aetherhaven/"+recipe.output()+".json"))).getAsJsonObject();
            assertFalse(item.has("Recipe"));
            var block=item.getAsJsonObject("BlockType");
            for(String path:List.of(block.get("CustomModel").getAsString(),block.getAsJsonArray("CustomModelTexture").get(0).getAsJsonObject().get("Texture").getAsString(),item.get("Icon").getAsString()))
                assertTrue(Files.isRegularFile(root.resolve("Common/"+path)),path);
            assertTrue(Files.isRegularFile(root.resolve("Server/Item/Block/Hitboxes/"+block.get("HitboxType").getAsString()+".json")));
            var icon=ImageIO.read(root.resolve("Common/"+item.get("Icon").getAsString()).toFile());assertEquals(64,icon.getWidth());assertEquals(64,icon.getHeight());
        }
        var town=new TownRecord();assertFalse(recipes.get(0).unlocked(town));assertFalse(recipes.get(1).unlocked(town));
        assertTrue(WorkplaceRecipeUnlocks.grant(town,recipes,recipes.get(0).output()));
        var reloaded=gson.fromJson(gson.toJson(town),TownRecord.class);
        assertTrue(recipes.get(0).unlocked(reloaded));assertFalse(recipes.get(1).unlocked(reloaded));
    }
}
