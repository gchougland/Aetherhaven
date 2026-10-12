package com.hexvane.aetherhaven.alchemy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.modules.entity.damage.ResistanceModifier;
import com.hypixel.hytale.server.core.modules.entity.damage.ResistanceModifier.ResistanceCalculationType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;

/** The ingredient table, the effect assets and the art they name must stay one connected set. */
@Tag("town")
class AlchemyEffectsTest {
    private static final Path RES=Path.of("src/main/resources");
    private static final Path EFFECTS=RES.resolve("Server/Entity/Effects/Aetherhaven/Alchemy");
    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static JsonObject effect(String id) throws Exception { return read(EFFECTS.resolve(id+".json")); }
    private static AlchemyIngredients.Ingredient ingredient(String item) {
        var found=AlchemyIngredients.find(item);assertNotNull(found,"No alchemy property for "+item);return found;
    }

    @Test void everyIngredientNamesAnEffectAssetAndEveryEffectAssetHasAnIngredient() throws Exception {
        var used=new HashSet<String>();
        for(var ingredient:AlchemyIngredients.all()) {
            assertEquals(AlchemyIngredients.effectId(ingredient.family(),ingredient.strength()),ingredient.effect());
            assertTrue(Files.isRegularFile(EFFECTS.resolve(ingredient.effect()+".json")),"Missing effect asset "+ingredient.effect());
            assertTrue(effect(ingredient.effect()).get("Duration").getAsDouble()>0,ingredient.effect());
            assertFalse(ingredient.name().isBlank());assertFalse(ingredient.description().isBlank());
            assertFalse(ingredient.description().matches(".*( - |–|—).*"),"Dash used as a pause: "+ingredient.description());
            used.add(ingredient.effect());
        }
        assertEquals(AlchemyIngredients.all().size(),AlchemyIngredients.all().stream().map(AlchemyIngredients.Ingredient::item).distinct().count(),
            "One plant cannot carry two properties");
        try(var files=Files.list(EFFECTS)) {
            for(var file:files.toList()) {
                String id=file.getFileName().toString().replace(".json","");
                assertTrue(used.contains(id),"No ingredient brews "+id);
            }
        }
    }

    @Test void regenerationFireWardAndRepellentResolveToTheirEffects() throws Exception {
        assertEquals("Aetherhaven_Alchemy_Regen1",ingredient("Plant_Flower_Common_Blue2").effect());
        assertEquals("Aetherhaven_Alchemy_Regen1",ingredient("Plant_Crop_Mushroom_Flatcap_Green").effect());
        assertEquals("Aetherhaven_Alchemy_Regen2",ingredient("Plant_Crop_Mushroom_Cap_Green").effect());
        assertEquals("Aetherhaven_Alchemy_Regen3",ingredient("Plant_Crop_Mushroom_Glowing_Green").effect());
        assertEquals(List.of(1,2,3),AlchemyIngredients.strengths("Regen"));
        for(int tier=1;tier<=3;tier++) {
            var regen=effect("Aetherhaven_Alchemy_Regen"+tier);
            assertEquals("Percent",regen.get("ValueType").getAsString());
            assertTrue(regen.getAsJsonObject("StatModifiers").get("Health").getAsDouble()>0);
            assertTrue(regen.get("DamageCalculatorCooldown").getAsDouble()>0,"Without a cooldown the heal lands once, not over time");
        }

        assertEquals("Aetherhaven_Alchemy_FireWard1",ingredient("Plant_Flower_Flax_Orange").effect());
        var fire=effect("Aetherhaven_Alchemy_FireWard1").getAsJsonObject("DamageResistance").getAsJsonArray("Fire");
        var modifiers=new ResistanceModifier[fire.size()];
        for(int i=0;i<modifiers.length;i++) {
            var modifier=fire.get(i).getAsJsonObject();
            modifiers[i]=new ResistanceModifier(ResistanceCalculationType.valueOf(modifier.get("CalculationType").getAsString().toUpperCase(Locale.ROOT)),
                modifier.get("Amount").getAsFloat());
        }
        assertTrue(AlchemyBlastWardSystem.fullyResists(modifiers),"The description promises immunity");

        assertEquals(AlchemyRepellentSystem.EFFECT,ingredient("Plant_Flower_Tall_Purple").effect());
        assertEquals(AlchemyRepellentSystem.EFFECT,ingredient("Plant_Flower_Common_Orange").effect());
        var group=read(RES.resolve("Server/NPC/Groups/Aetherhaven/"+AlchemyRepellentSystem.GROUP+".json"));
        var roles=group.getAsJsonArray("IncludeRoles").asList().stream().map(e->e.getAsString()).toList();
        assertTrue(roles.contains("*Mosquito*"),"A mosquito from a later game build or another pack must be covered");
        assertTrue(roles.contains("Scarak_*"),"Scarak Seekers are what the repellent was asked for");
        var spared=group.getAsJsonArray("ExcludeRoles").asList().stream().map(e->e.getAsString()).toList();
        assertTrue(spared.contains("Scarak_Broodmother*"),"A boss is a deliberate fight, like a raid");
        assertTrue(roles.stream().anyMatch(role->!role.contains("*")),"The repellent must work on something that exists today");
        assertTrue(roles.stream().noneMatch(role->role.startsWith("Aetherhaven_")),"Raid and hunt creatures are deliberate fights");
    }

    @Test void regenerationNeverBeatsTheHealingMixtureOfTheSameTier() throws Exception {
        double previous=0;
        for(int tier=1;tier<=3;tier++) {
            var regen=effect("Aetherhaven_Alchemy_Regen"+tier);
            double perTick=regen.getAsJsonObject("StatModifiers").get("Health").getAsDouble();
            double seconds=regen.get("Duration").getAsDouble(),cooldown=regen.get("DamageCalculatorCooldown").getAsDouble();
            // The engine heals once per whole cooldown inside the duration (ActiveEntityEffect.calculateCyclesToRun).
            double total=Math.floor(seconds/cooldown)*perTick;
            double instant=effect("Aetherhaven_Alchemy_Health"+tier).getAsJsonObject("StatModifiers").get("Health").getAsDouble();
            assertTrue(total<=instant,"Regeneration "+tier+" heals "+total+"%, more than the instant "+instant+"%");
            assertTrue(total>previous,"A stronger regeneration must heal more");
            assertTrue(seconds/cooldown-Math.floor(seconds/cooldown)>=0.02,"Leave a margin so the last heal is not lost to rounding");
            previous=total;
            // The cauldron and the flask tooltip quote these numbers, so they must follow the asset.
            for(var source:AlchemyIngredients.all())if(source.effect().equals("Aetherhaven_Alchemy_Regen"+tier)) {
                assertTrue(source.description().contains(Math.round(total)+"% health"),source.description());
                assertTrue(source.description().contains((int)Math.floor(seconds)+" seconds"),source.description());
            }
        }
    }

    @Test void wardAndRepellentTextQuotesTheAssetDuration() throws Exception {
        for(String item:List.of("Plant_Flower_Flax_Orange","Plant_Flower_Tall_Purple","Plant_Flower_Common_Orange")) {
            var source=ingredient(item);
            int seconds=(int)effect(source.effect()).get("Duration").getAsDouble();
            assertTrue(source.description().contains(seconds+" seconds"),source.description());
        }
    }

    @Test void strengthsOfOneFamilyNeverStack() {
        assertFalse(AlchemyIngredients.overlap(1,List.of(3)).apply(),"A weak sip must not cut a strong effect short");
        assertFalse(AlchemyIngredients.overlap(2,List.of(1,3)).apply());
        var stronger=AlchemyIngredients.overlap(3,List.of(2,1));
        assertTrue(stronger.apply());assertEquals(List.of(1,2),stronger.replace());
        var fresh=AlchemyIngredients.overlap(2,List.of());
        assertTrue(fresh.apply());assertTrue(fresh.replace().isEmpty());
        assertEquals(List.of(1,2),AlchemyIngredients.strengths("HealthBoost"));
        assertEquals(List.of(1),AlchemyIngredients.strengths("FireWard"));
        assertTrue(AlchemyIngredients.strengths("Nothing").isEmpty());
    }

    @Test void onlyACompletePercentResistanceCancelsDamage() {
        var percent=ResistanceCalculationType.PERCENT;var flat=ResistanceCalculationType.FLAT;
        assertTrue(AlchemyBlastWardSystem.fullyResists(new ResistanceModifier[]{new ResistanceModifier(percent,1f)}));
        assertTrue(AlchemyBlastWardSystem.fullyResists(new ResistanceModifier[]{new ResistanceModifier(percent,.5f),new ResistanceModifier(percent,.5f)}));
        assertFalse(AlchemyBlastWardSystem.fullyResists(new ResistanceModifier[]{new ResistanceModifier(percent,.5f)}),"Half resistance still hurts");
        assertFalse(AlchemyBlastWardSystem.fullyResists(new ResistanceModifier[]{new ResistanceModifier(flat,100f)}),"A flat amount does not stop a large hit");
        assertFalse(AlchemyBlastWardSystem.fullyResists(new ResistanceModifier[0]));
        assertFalse(AlchemyBlastWardSystem.fullyResists(null));
    }

    @Test void repellentYieldsToACreaturesOwnGrudgeAndOutranksItsUsualAttitude() {
        int own=com.hypixel.hytale.server.npc.blackboard.view.attitude.IAttitudeProvider.OVERRIDE_PRIORITY;
        // Lower runs first. The native lineage and reputation providers sit at +100 and the species table at +200.
        assertTrue(AlchemyRepellentSystem.PRIORITY>own,"A creature that was struck must still be able to fight back");
        assertTrue(AlchemyRepellentSystem.PRIORITY<own+100,"The repellent must be asked before a creature's usual attitude");
    }

    @Test void strikingARepelledCreatureEndsTheRepellent() {
        assertTrue(AlchemyRepellentSystem.endsRepellent(false,4f,true,true));
        assertFalse(AlchemyRepellentSystem.endsRepellent(true,4f,true,true),"A blocked blow did not land");
        assertFalse(AlchemyRepellentSystem.endsRepellent(false,0f,true,true),"A blow worth nothing did not land");
        assertFalse(AlchemyRepellentSystem.endsRepellent(false,4f,false,true),"Fighting anything else keeps the repellent");
        assertFalse(AlchemyRepellentSystem.endsRepellent(false,4f,true,false),"Nothing to end without the repellent");
        assertTrue(AlchemyIngredients.find("Plant_Flower_Tall_Purple").description().contains("until you attack one"));
    }

    @Test void lastingEffectsShowAStatusIconThatIsARealPicture() throws Exception {
        try(var files=Files.list(EFFECTS)) {
            for(var file:files.toList()) {
                var data=read(file);
                if(data.get("Duration").getAsDouble()<1)continue;
                assertTrue(data.has("StatusEffectIcon"),"A lasting effect needs a status icon: "+file.getFileName());
                String icon=data.get("StatusEffectIcon").getAsString();
                if(!icon.startsWith("UI/StatusEffects/Aetherhaven/"))continue;
                var image=ImageIO.read(RES.resolve("Common").resolve(icon).toFile());
                assertNotNull(image,"Unreadable icon "+icon);
                var colours=new HashSet<Integer>();int opaque=0;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                    int argb=image.getRGB(x,y);
                    if((argb>>>24)<128)continue;
                    opaque++;colours.add(argb&0xF0F0F0);
                }
                assertTrue(opaque>image.getWidth()*image.getHeight()/8,"Nearly empty icon "+icon);
                assertTrue(colours.size()>=16,"Flat or blank icon "+icon);
            }
        }
    }

    @Test void ingredientsIconsAndRepelledCreaturesExistInTheInstalledGame() throws Exception {
        String appData=System.getenv("APPDATA");
        Assumptions.assumeTrue(appData!=null);
        var assets=Path.of(appData,"Hytale/install/pre-release/package/game/latest/Assets.zip");
        Assumptions.assumeTrue(Files.isRegularFile(assets));
        try(var zip=new ZipFile(assets.toFile())) {
            var items=new HashMap<String,ZipEntry>();var roles=new HashSet<String>();
            zip.stream().filter(e->e.getName().endsWith(".json")).forEach(e->{
                String id=Path.of(e.getName()).getFileName().toString().replace(".json","");
                if(e.getName().startsWith("Server/Item/Items/"))items.put(id,e);
                else if(e.getName().startsWith("Server/NPC/Roles/"))roles.add(id);
            });
            for(var ingredient:AlchemyIngredients.all()) {
                assertNotNull(items.get(ingredient.item()),"Not a game item: "+ingredient.item());
                var data=JsonParser.parseString(new String(zip.getInputStream(items.get(ingredient.item())).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(data.has("Icon"),"No icon on "+ingredient.item());
                assertNotNull(zip.getEntry("Common/"+data.get("Icon").getAsString()),ingredient.item());
            }
            try(var files=Files.list(EFFECTS)) {
                for(var file:files.toList()) {
                    var data=read(file);
                    if(!data.has("StatusEffectIcon"))continue;
                    String icon=data.get("StatusEffectIcon").getAsString();
                    assertTrue(Files.isRegularFile(RES.resolve("Common").resolve(icon))||zip.getEntry("Common/"+icon)!=null,"Missing status icon "+icon);
                }
            }
            var group=read(RES.resolve("Server/NPC/Groups/Aetherhaven/"+AlchemyRepellentSystem.GROUP+".json"));
            for(var role:group.getAsJsonArray("IncludeRoles"))
                if(!role.getAsString().contains("*"))assertTrue(roles.contains(role.getAsString()),"Not a game creature: "+role.getAsString());
        }
    }
}
