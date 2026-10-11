package com.hexvane.aetherhaven.hunter;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class HuntAssetsTest {
    private static final Path RES=Path.of("src/main/resources"),SERVER=RES.resolve("Server");
    private static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    @ParameterizedTest @ValueSource(strings={"Hunter_Lodge.prefab.json"})
    void arenaHasOneBeaconAndContinuousClearFloorConnectedToLodge(String name)throws Exception {
        var json=read(SERVER.resolve("Prefabs").resolve(name));var blocks=new HashMap<String,String>();
        int beacons=0;
        for(var element:json.getAsJsonArray("blocks")) {
            var b=element.getAsJsonObject();String key=b.get("x")+","+b.get("y")+","+b.get("z");
            assertNull(blocks.put(key,b.get("name").getAsString()),"Duplicate cell "+key);
            if(HuntService.BEACON.equals(b.get("name").getAsString()))beacons++;
        }
        assertEquals(1,beacons);
        // Storage placement is pending in the author's design. Editor requirements
        // are covered by HunterStorageRequirementsTest, independently of this arena.
        assertEquals("Empty",blocks.get("-7,0,1"),"The lodge entrance must have a clear approach");
        for(int x=-1;x<=7;x++)for(int z=-4;z<=4;z++) {
            assertNotNull(blocks.get(x+",-1,"+z));assertNotEquals("Empty",blocks.get(x+",-1,"+z));
            for(int y=0;y<=2;y++)assertEquals("Empty",blocks.get(x+","+y+","+z),"Arena obstacle "+x+","+y+","+z);
        }
        // Follow clear standing cells through the authored side entrance.
        var reached=new HashSet<String>();var queue=new ArrayDeque<int[]>();queue.add(new int[]{-7,1});
        while(!queue.isEmpty()){
            var p=queue.removeFirst();String key=p[0]+","+p[1];
            if(!reached.add(key))continue;
            for(var delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                int x=p[0]+delta[0],z=p[1]+delta[1];
                if(!reached.contains(x+","+z)&&blocks.containsKey(x+",-1,"+z)&&!"Empty".equals(blocks.get(x+",-1,"+z))
                    &&"Empty".equals(blocks.get(x+",0,"+z))&&"Empty".equals(blocks.get(x+",1,"+z)))queue.add(new int[]{x,z});
            }
        }
        assertTrue(reached.contains("3,0"),"The lodge entrance must connect to the arena beacon");
        assertFalse(Files.exists(SERVER.resolve("Prefabs/Stormwind/Hunter_Lodge.prefab.json")));
    }
    @Test void opponentsHaveNoNativeDropsAndAllUnlockSamplesAreTheirLoot()throws Exception {
        for(var family:HuntFamily.all()) {
            var role=read(SERVER.resolve("NPC/Roles/Aetherhaven/"+family.role()+".json"));
            assertEquals("",role.getAsJsonObject("Modify").get("DropList").getAsString());
            assertTrue(family.drops().containsKey(family.icon()),"Unlock sample must be actual loot");
        }
    }
    @Test void hunterHasCivilianTravelAndExplicitCombatWithoutUnrestrictedAggro()throws Exception {
        String role=Files.readString(SERVER.resolve("NPC/Roles/Aetherhaven/Aetherhaven_Monster_Hunter.json"));
        assertTrue(role.contains("AetherhavenAutonomy"));assertTrue(role.contains("LockedTarget"));assertTrue(role.contains("Guard_Melee_Combat"));
        assertFalse(role.contains("Guard_Idle_Aggro"));assertFalse(role.contains("\"Flee\""));
        var vars=read(SERVER.resolve("NPC/Roles/Aetherhaven/Aetherhaven_Monster_Hunter.json")).getAsJsonObject("InteractionVars");
        assertEquals("NPC_Attack_Selector_Left",vars.getAsJsonObject("Melee_Selector").getAsJsonArray("Interactions").get(0).getAsString());
    }
    @Test void summonsCannotMatchTheTownFriendlyFireProtectionGroup()throws Exception {
        var group=read(SERVER.resolve("NPC/Groups/Aetherhaven/Aetherhaven_Townsfolk.json"));
        assertTrue(group.getAsJsonArray("ExcludeRoles").asList().stream().anyMatch(e->"Aetherhaven_Hunt_*".equals(e.getAsString())));
    }
    @Test void arenaScarakCannotRecruitWildFlocksAndHasExplicitBiteDamage()throws Exception {
        var role=read(SERVER.resolve("NPC/Roles/Aetherhaven/Aetherhaven_Hunt_Scarak.json")).getAsJsonObject("Modify");
        assertTrue(role.getAsJsonArray("FlockArray").isEmpty());
        String attack=role.get("Attack").getAsString();
        assertTrue(Files.isRegularFile(SERVER.resolve("Item/RootInteractions/Aetherhaven/"+attack+".json")));
        String bite=Files.readString(SERVER.resolve("Item/Interactions/Aetherhaven/"+attack+".json"));
        assertTrue(bite.contains("NPC_Attack_Melee_Damage"));assertFalse(bite.contains("\"Replace\""));
    }
    @Test void unlockAndRewardItemsExistWithIconsInTheInstalledGame()throws Exception {
        String appData=System.getenv("APPDATA");
        Assumptions.assumeTrue(appData!=null);
        var assets=Path.of(appData,"Hytale/install/pre-release/package/game/latest/Assets.zip");
        Assumptions.assumeTrue(Files.isRegularFile(assets));
        try(var zip=new java.util.zip.ZipFile(assets.toFile())) {
            var items=new HashMap<String,java.util.zip.ZipEntry>();
            zip.stream().filter(e->e.getName().startsWith("Server/Item/Items/")&&e.getName().endsWith(".json"))
                .forEach(e->items.put(Path.of(e.getName()).getFileName().toString().replace(".json",""),e));
            for(var family:HuntFamily.all())for(String id:family.drops().keySet()) {
                assertNotNull(items.get(id),"Missing native loot "+id);
                var data=JsonParser.parseString(new String(zip.getInputStream(items.get(id)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(data.has("Icon"),"Missing loot icon "+id);
                assertNotNull(zip.getEntry("Common/"+data.get("Icon").getAsString()),id);
            }
        }
    }
    @Test void rewardsHaveOriginalModelsTexturesAndIcons()throws Exception {
        var resident=read(SERVER.resolve("Aetherhaven/Villagers/Aetherhaven_Monster_Hunter.json"));
        assertEquals(3,resident.getAsJsonArray("reputationMilestones").size());
        for(var reward:resident.getAsJsonArray("reputationMilestones")) {
            String id=reward.getAsJsonObject().get("itemId").getAsString();
            var item=read(SERVER.resolve("Item/Items/Aetherhaven/"+id+".json"));
            assertTrue(Files.isRegularFile(RES.resolve("Common").resolve(item.get("Icon").getAsString())));
            var block=item.getAsJsonObject("BlockType");
            assertTrue(Files.isRegularFile(RES.resolve("Common").resolve(block.get("CustomModel").getAsString())));
            assertTrue(Files.isRegularFile(RES.resolve("Common").resolve(block.getAsJsonArray("CustomModelTexture").get(0).getAsJsonObject().get("Texture").getAsString())));
        }
    }
}
