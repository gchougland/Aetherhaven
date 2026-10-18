package com.hexvane.aetherhaven.guide;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceRules;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideCoverageTest {
    private static final List<String> SYSTEMS=List.of("mechanic_backpacks","mechanic_warehouse","mechanic_townsfolk_stats","mechanic_work_priorities",
        "mechanic_alchemy","mechanic_production_buildings","mechanic_farm","mechanic_barn","mechanic_lumbermill",
        "mechanic_core_mining","mechanic_spirit_garden","mechanic_hunting","mechanic_clinic","mechanic_pattern_book");
    private GuideTopicRepository repository(String locale) {
        return GuideTopicRepository.load(getClass().getClassLoader(),locale,GuideTopicPackOverlay.empty(),Map.of());
    }
    @Test void everyCurrentWorkTypeHasAReachableSetupPageAndItsRequiredStation() {
        var repo=repository("en-US");
        var hub=repo.byId("mechanic_apprentice_workplaces");
        assertTrue(repo.byId("welcome").subTopicIds().contains(hub.id()),"Workplaces must have their own main dropdown");
        assertFalse(repo.byId("mechanics").subTopicIds().contains(hub.id()));
        for(var type:TownsfolkWorkType.values()) {
            if(!type.availableForNewWork())continue;
            String id="workplace_"+type.id;
            assertTrue(hub.subTopicIds().contains(id),"Add a guide for new work type: "+type);
            var topic=repo.byId(id);assertNotNull(topic,id);
            assertTrue(topic.markdownBody().contains("## Set it up"),id);
            if(type.requiresWorkplace()) {
                String bench=WorkplaceRules.mainBench(type).replace("Main workplace bench: ","").split("\\.")[0];
                assertTrue(topic.markdownBody().contains(bench),id+" must explain "+bench);
                assertTrue(topic.markdownBody().contains("## Work and output"),id);
                assertTrue(topic.markdownBody().contains("## If work is waiting"),id);
            }
        }
    }
    @Test void newSystemsAreNavigableAndUntranslatedPagesFallBackToEnglish() {
        var english=repository("en-US");var translated=repository("de-DE");
        for(String id:SYSTEMS) {
            var topic=english.byId(id);assertNotNull(topic,id);assertFalse(topic.description().isBlank(),id);
            assertEquals(topic.markdownBody(),translated.byId(id).markdownBody(),id);
        }
        assertNull(english.byId("villager_halloween"));
        assertNull(translated.byId("villager_halloween"));
    }
    @Test void publishedJournalTreeHasNoMissingPagesAndNewArticlesFitTheNativeRenderer() throws Exception {
        var repo=repository("en-US");
        for(var entry:repo.navEntries()) {
            String id=entry.topicId();
            try(var resource=getClass().getClassLoader().getResourceAsStream("Server/Aetherhaven/GuideTopics/en-US/"+id+".md")) {
                assertNotNull(resource,"Missing canonical guide: "+id);
            }
            if(!id.startsWith("workplace_")&&!SYSTEMS.contains(id)
                &&!List.of("mechanic_apprentice_workplaces","mechanic_production_wardrobe").contains(id))continue;
            var topic=repo.byId(id);
            int rows=GuideMarkdownUiAppender.appendMarkdown(new UICommandBuilder(),"#Article",topic.markdownBody(),getClass().getClassLoader(),topic.npcRoleId(),257);
            assertTrue(rows>0&&rows<=256,"Journal article exceeds its display budget: "+id+" ("+rows+")");
        }
    }
}
