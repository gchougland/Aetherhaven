package com.hexvane.aetherhaven.quest;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.TownRecord;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingWorkplaceQuestTest {
    private static QuestDefinition quest() throws Exception {
        return new Gson().fromJson(Files.readString(Path.of("subplugin-assets/Quests/Server/Aetherhaven/Quests/Town/q_farming_workplace.json")),QuestDefinition.class);
    }
    @Test void firstTendingIsRequiredAndExistingVisitsCountWithoutSkippingSetup() throws Exception {
        var q=quest();var town=new Gson().fromJson("""
            {"apprenticeWorkplaces":{"sites":[{"id":"11111111-1111-1111-1111-111111111111","name":"Farm","type":"farming",
            "area":{"minX":0,"floorY":116,"minZ":0,"maxX":5,"maxZ":5},"food":{"completedVisits":0}}]}}
            """,TownRecord.class);
        var tending=q.objectivesOrEmpty().stream().filter(o->"tend".equals(o.id())).findFirst().orElseThrow();
        QuestProgressionService.reconcile(q,ConstructionCatalog.empty(),town,q.idOrEmpty());
        assertFalse(town.isQuestObjectiveComplete(q.idOrEmpty(),"tend"));
        var food=town.getApprenticeWorkplaces().sites().getFirst().food();
        for(int i=0;i<48;i++)food.work(.25,true,true);
        assertTrue(food.completePlanting(()->true));
        assertFalse(QuestProgressionService.isStateComplete(ConstructionCatalog.empty(),town,q,tending));
        for(int i=0;i<48;i++)food.work(.25,true,true);
        assertTrue(food.completeCare(()->true));
        QuestProgressionService.reconcile(q,ConstructionCatalog.empty(),town,q.idOrEmpty());
        assertTrue(town.isQuestObjectiveComplete(q.idOrEmpty(),"tend"));
        assertFalse(town.isQuestObjectiveComplete(q.idOrEmpty(),"bench"),"A later objective cannot invent proof of a bench");
        assertFalse(town.isQuestObjectiveComplete(q.idOrEmpty(),"talk"));
    }
    @Test void tutorialRequiresFarmerRecruitmentAndExplainsFoodOnlyWork() throws Exception {
        var q=quest();assertTrue(q.prerequisitesOrEmpty().completedQuestIdsOrEmpty().contains("q_farm_plot"));
        assertTrue(QuestAutoStartService.isKnownCondition(q.autoStart().whenOrEmpty()));
        assertTrue(q.descriptionOrDefault().contains("not items"));
        var text=Files.readString(Path.of("src/main/resources/Server/Languages/en-US/aetherhaven_food_tutorial.lang"));
        assertTrue(text.contains("10 food points"));assertTrue(text.contains("five residents"));
        assertTrue(text.contains("No seeds")||text.contains("do not need seeds"));
        assertTrue(text.contains("Inn, core Farm, core Barn and Restaurant"));
        assertTrue(text.contains("Town provisions"));
    }
}
