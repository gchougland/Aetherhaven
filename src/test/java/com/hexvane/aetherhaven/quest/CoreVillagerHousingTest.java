package com.hexvane.aetherhaven.quest;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreVillagerHousingTest {
    private static final ClassLoader LOADER = CoreVillagerHousingTest.class.getClassLoader();

    @Test void everyShippedCoreVillagerAlwaysHasAPlayableHouseQuest() throws Exception {
        var villagers=VillagerDefinitionCatalog.loadFromAssetPacksOrClasspath(LOADER);
        var dialogue=DialogueCatalog.loadFromAssetPacksOrClasspath(LOADER);
        var original=shippedQuests();
        var quests=CoreVillagerHousing.ensure(villagers,ConstructionCatalog.empty(),original,dialogue);
        assertTrue(villagers.allByNpcRoleId().size()>=26,"Must inspect all shipped core villagers");
        for(var villager:villagers.allByNpcRoleId().values()) {
            var houses=quests.all().values().stream().filter(q->CoreVillagerHousing.isHousingFor(q,villager.getNpcRoleId())).toList();
            assertEquals(1,houses.size(),villager.getNpcRoleId());
            var q=houses.getFirst();
            var tree=dialogue.get(villager.getResidentTreeId());
            assertTrue(CoreVillagerHousing.hasAction(tree,"start_quest",q.idOrEmpty()),villager.getNpcRoleId());
            assertTrue(CoreVillagerHousing.hasAction(tree,"complete_quest",q.idOrEmpty()),villager.getNpcRoleId());
            assertTrue(reachableActions(tree).contains("start_quest:"+q.idOrEmpty()),villager.getNpcRoleId());
            assertTrue(reachableActions(tree).contains("complete_quest:"+q.idOrEmpty()),villager.getNpcRoleId());
            if(original.get(q.idOrEmpty())!=null)assertSame(original.get(q.idOrEmpty()),q,"Keep authored quests and their save ids");
        }
        for(String kind:List.of("alchemist","crafter","doctor","druid","halloween","leatherworker","monster_hunter","warehouse_manager"))
            assertNotNull(quests.get("q_house_"+kind),kind);
        int choices=dialogue.all().values().stream().flatMap(t->t.getNodes().values().stream()).mapToInt(n->n.getChoices().size()).sum();
        var again=CoreVillagerHousing.ensure(villagers,ConstructionCatalog.empty(),quests,dialogue);
        assertEquals(quests.all().keySet(),again.all().keySet());
        assertEquals(choices,dialogue.all().values().stream().flatMap(t->t.getNodes().values().stream()).mapToInt(n->n.getChoices().size()).sum(),"Reload must not duplicate choices");
    }

    @Test void futureCoreVillagersGetHousingWithoutAHandWrittenQuest() throws Exception {
        var v=new Gson().fromJson("""
            {"npcRoleId":"Future_Core","dialogueVillagerKind":"future_core","residentTreeId":"aetherhaven_alchemist",
             "displayName":"Future Resident","workConstructionId":"plot_alchemist"}
            """,VillagerDefinition.class);
        var dialogues=DialogueCatalog.loadFromAssetPacksOrClasspath(LOADER);
        var quests=CoreVillagerHousing.ensure(VillagerDefinitionCatalog.forTests(Map.of(v.getNpcRoleId(),v)),
            ConstructionCatalog.empty(),shippedQuests(),dialogues);
        var house=quests.get("q_house_future_core");
        assertNotNull(house);
        assertTrue(house.prerequisitesOrEmpty().completedQuestIdsOrEmpty().contains("q_alchemist"));
        var town=new TownRecord();
        assertFalse(QuestDialogueEntry.isOfferable(house,town));
        town.completeQuest("q_alchemist");
        assertTrue(QuestDialogueEntry.isOfferable(house,town),"Existing towns get the offer after recruitment");
        town.addActiveQuest(house.idOrEmpty());
        assertFalse(QuestDialogueEntry.isOfferable(house,town));
        town.completeQuest(house.idOrEmpty());
        assertFalse(QuestDialogueEntry.isOfferable(house,town),"A house quest is not repeatedly granted");
    }

    @Test void missingResidentDialogueCannotSilentlyDropHousing() {
        var v=new Gson().fromJson("{\"npcRoleId\":\"Broken_Core\",\"dialogueVillagerKind\":\"broken\"}",VillagerDefinition.class);
        assertThrows(IllegalStateException.class,()->CoreVillagerHousing.ensure(VillagerDefinitionCatalog.forTests(Map.of(v.getNpcRoleId(),v)),
            ConstructionCatalog.empty(),QuestCatalog.empty(),DialogueCatalog.empty()));
    }

    @Test void townsfolkAndGuardsKeepTheirIndividualHousingQuests() throws Exception {
        var quests=shippedQuests();
        for(String id:List.of("q_house_townsfolk","q_house_guard")) {
            var quest=quests.get(id);assertNotNull(quest,id);
            assertTrue(quest.assignByEntity(),id);
            assertTrue(quest.repeatOrDefault().isPerEntity(),id);
            assertTrue(quest.objectivesOrEmpty().stream().anyMatch(o->"assign_house_resident".equals(o.kind())),id);
        }
    }

    private static Set<String> reachableActions(DialogueTreeDefinition tree) {
        var visited=new HashSet<String>();var actions=new HashSet<String>();var pending=new ArrayDeque<String>();pending.add(tree.entryOrDefault());
        while(!pending.isEmpty()) {
            String id=pending.remove();if(!visited.add(id))continue;
            var node=tree.getNode(id);assertNotNull(node,"Dangling dialogue node "+id);
            var all=new ArrayList<>(node.getActions());
            for(var c:node.getChoices()) { all.addAll(c.getActions());if(c.getNext()!=null&&!c.getNext().isBlank())pending.add(c.getNext()); }
            for(var a:all)if(a.has("type")&&a.has("id"))actions.add(a.get("type").getAsString()+":"+a.get("id").getAsString());
        }
        return actions;
    }
    private static QuestCatalog shippedQuests() throws Exception {
        var quests=new LinkedHashMap<String,com.hexvane.aetherhaven.quest.data.QuestDefinition>();
        try(var files=java.nio.file.Files.walk(java.nio.file.Path.of("subplugin-assets/Quests/Server/Aetherhaven/Quests"))) {
            for(var path:files.filter(p->p.toString().endsWith(".json")).toList()) {
                var q=new Gson().fromJson(java.nio.file.Files.readString(path),com.hexvane.aetherhaven.quest.data.QuestDefinition.class);
                quests.put(q.idOrEmpty(),q);
            }
        }
        return QuestCatalog.of(quests);
    }
}
