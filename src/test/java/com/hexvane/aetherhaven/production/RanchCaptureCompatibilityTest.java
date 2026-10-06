package com.hexvane.aetherhaven.production;

import com.hypixel.hytale.builtin.tagset.TagSet;
import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class RanchCaptureCompatibilityTest {
    private record Group(String id, String[] includedTagSets, String[] excludedTagSets,
                         String[] includedTags, String[] excludedTags) implements TagSet {
        public String getId(){return id;}
        public String[] getIncludedTagSets(){return includedTagSets;}
        public String[] getExcludedTagSets(){return excludedTagSets;}
        public String[] getIncludedTags(){return includedTags;}
        public String[] getExcludedTags(){return excludedTags;}
    }
    private Group roles(String id,String... roles){return new Group(id,null,null,roles,null);}
    private Set<String> captured(Map<String,TagSet> groups) {
        var roleNames=List.of("Chicken","Tetrabird","Bluebird","Aetherhaven_Ranch_Chicken",
            "Aetherhaven_Ranch_Bluebird_Young","Mod_OwnedBird","Mod_Rabbit","Cow","Wolf");
        var roleIndexes=new Object2IntOpenHashMap<String>();
        for(int n=0;n<roleNames.size();n++)roleIndexes.put(roleNames.get(n),n);
        var groupIndexes=new Object2IntOpenHashMap<String>();
        for(String name:groups.keySet())groupIndexes.put(name,groupIndexes.size());
        var lookup=new TagSetPlugin.TagSetLookup();lookup.putAssetSets(groups,groupIndexes,roleIndexes);
        var captured=new HashSet<String>();
        for(String role:roleNames)if(lookup.tagInSet(groupIndexes.getInt("Capture_Crate"),roleIndexes.getInt(role)))captured.add(role);
        return captured;
    }
    private Map<String,TagSet> groups(Group capture) {
        var result=new LinkedHashMap<String,TagSet>();result.put(capture.id(),capture);
        result.put(RanchCaptureCompatibility.BIRD_GROUP,roles(RanchCaptureCompatibility.BIRD_GROUP,"Tetrabird","Bluebird"));
        result.put("Mod_Capture",roles("Mod_Capture","Mod_Rabbit","Mod_OwnedBird"));
        result.put("Mod_NoBluebird",roles("Mod_NoBluebird","Bluebird"));
        return result;
    }

    @Test void addsWildBirdsAndPreservesOtherPacksWhileProtectingManagedOffspring() {
        var original=new Group("Capture_Crate",new String[]{"Mod_Capture"},null,new String[]{"*Chicken"},null);
        var assets=groups(original);
        var view=RanchCaptureCompatibility.captureView(assets,List.of("Mod_OwnedBird"));
        assertEquals(Set.of("Chicken","Mod_Rabbit","Tetrabird","Bluebird"),captured(view));
        assertSame(original,assets.get("Capture_Crate"));
        assertArrayEquals(new String[]{"Mod_Capture"},original.getIncludedTagSets());assertNull(original.getExcludedTags());
        assertSame(assets.get("Mod_Capture"),view.get("Mod_Capture"));
        assertEquals(Set.of("Chicken","Aetherhaven_Ranch_Chicken","Mod_Rabbit","Mod_OwnedBird"),captured(assets),
            "Removing the integration restores the original native and add-on asset view");
    }

    @Test void explicitExcludedGroupsAndRolesKeepTheirNativePrecedence() {
        var capture=new Group("Capture_Crate",new String[]{"Mod_Capture"},new String[]{"Mod_NoBluebird"},
            new String[]{"Chicken"},new String[]{"Tetrabird"});
        var view=RanchCaptureCompatibility.captureView(groups(capture),List.of("Mod_OwnedBird"));
        assertEquals(Set.of("Chicken","Mod_Rabbit"),captured(view));
    }

    @Test void reloadUsesCurrentAssetsAndRepeatedApplicationDoesNotStack() {
        var assets=groups(roles("Capture_Crate","Chicken"));
        var first=RanchCaptureCompatibility.captureView(assets,List.of());
        var second=RanchCaptureCompatibility.captureView(first,List.of());
        assertEquals(captured(first),captured(second));
        assertEquals(1,second.get("Capture_Crate").getIncludedTagSets().length);
        assets.put("Capture_Crate",roles("Capture_Crate","Cow"));
        assertEquals(Set.of("Cow","Tetrabird","Bluebird"),captured(RanchCaptureCompatibility.captureView(assets,List.of())));
        assets.remove(RanchCaptureCompatibility.BIRD_GROUP);
        assertEquals(Set.of("Cow"),captured(RanchCaptureCompatibility.captureView(assets,List.of())));
        assets.remove("Capture_Crate");
        assertFalse(RanchCaptureCompatibility.captureView(assets,List.of()).containsKey("Capture_Crate"));
    }
}
