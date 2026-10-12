package com.hexvane.aetherhaven.alchemy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;

/**
 * Writes and guards {@code tutorials/ALCHEMY_EFFECTS.md}. The page is built from the ingredient table, the effect assets,
 * the flask data and the drink interaction, so it cannot drift from the game: this test fails when the page and the
 * data disagree. To rewrite the page after changing the data, run this test once with the environment variable
 * {@code AETHERHAVEN_WRITE_DOCS=1}.
 */
@Tag("town")
class AlchemyEffectsDocTest {
    /** Not docs/: this repository ignores /Docs/, and on Windows that swallows docs/ too, so the page would never be committed. */
    private static final Path DOC=Path.of("tutorials/ALCHEMY_EFFECTS.md");
    private static final Path RES=Path.of("src/main/resources");
    private static final Path EFFECTS=RES.resolve("Server/Entity/Effects/Aetherhaven/Alchemy");
    /** What a player would call each family. A new family must be named here before the page can be written. */
    private static final Map<String,String> FAMILY=Map.ofEntries(Map.entry("Health","Healing"),Map.entry("Mana","Mana"),Map.entry("Stamina","Stamina"),
        Map.entry("HealthBoost","Health Boost"),Map.entry("ManaBoost","Mana Boost"),Map.entry("Regen","Regeneration"),
        Map.entry("BlastWard","Blast Ward"),Map.entry("PoisonWard","Poison Ward"),Map.entry("FireWard","Fire Ward"),Map.entry("Repellent","Bug Repellent"));
    /** In game names of materials that are not alchemy ingredients. Checked against the installed game below. */
    private static final Map<String,String> MATERIALS=Map.of("Ingredient_Bar_Iron","Iron Ingot","Ingredient_Leather_Light","Light Leather",
        "Ingredient_Tree_Sap","Tree Sap","Ingredient_Leather_Medium","Medium Leather","Ingredient_Leather_Heavy","Heavy Leather","Ingredient_Leather_Storm","Storm Leather");

    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static String number(double value) { return value==Math.rint(value)?Long.toString((long)value):Double.toString(value); }
    private static String seconds(double value) {
        long whole=(long)Math.floor(value);
        return whole>=120&&whole%60==0?whole+" seconds ("+whole/60+" minutes)":whole+" seconds";
    }
    private static String stat(String id) { return id.toLowerCase(Locale.ROOT); }
    private record Described(String does,String lasts,String good,String great,String perfect) {
        Described(String does,String lasts,double seconds,AlchemyBrew.Rules rules) {
            this(does,lasts,seconds(seconds),seconds(seconds*rules.duration(AlchemyBrew.Quality.GREAT)),seconds(seconds*rules.duration(AlchemyBrew.Quality.PERFECT)));
        }
    }
    private static final AlchemyBrew.Rules RULES=AlchemyBrew.load(AlchemyEffectsDocTest.class.getClassLoader());

    /** Reads what an effect does from its own asset. An effect of a shape not handled here fails the test. */
    private static Described describe(String family,int strength) throws Exception {
        var data=read(EFFECTS.resolve(AlchemyIngredients.effectId(family,strength)+".json"));
        double duration=data.get("Duration").getAsDouble();
        if(data.has("StatModifiers")) {
            assertEquals("Percent",data.get("ValueType").getAsString(),family);
            var stats=data.getAsJsonObject("StatModifiers");assertEquals(1,stats.size(),family);
            String name=stats.keySet().iterator().next();double amount=stats.get(name).getAsDouble();
            double cooldown=data.has("DamageCalculatorCooldown")?data.get("DamageCalculatorCooldown").getAsDouble():0;
            if(cooldown<=0)return new Described("Restores "+number(amount)+"% of your maximum "+stat(name)+" at once","Instant",number(amount)+"%",
                number(Math.round(amount*RULES.restore(AlchemyBrew.Quality.GREAT)*10)/10.0)+"%",number(Math.round(amount*RULES.restore(AlchemyBrew.Quality.PERFECT)*10)/10.0)+"%");
            // The engine applies the amount once for each whole cooldown inside the duration.
            double total=Math.floor(duration/cooldown)*amount;
            var over=new String[3];
            for(var quality:AlchemyBrew.Quality.values()) {
                double lasts=duration*RULES.duration(quality);
                over[quality.ordinal()]=number(Math.floor(lasts/cooldown)*amount)+"% over "+seconds(lasts);
            }
            return new Described("Restores "+number(total)+"% of your maximum "+stat(name)+" in all, "+number(amount)+"% every "+number(cooldown)+" seconds",seconds(duration),over[0],over[1],over[2]);
        }
        if(data.has("RawStatModifiers")) {
            var stats=data.getAsJsonObject("RawStatModifiers");assertEquals(1,stats.size(),family);
            String name=stats.keySet().iterator().next();var modifier=stats.getAsJsonArray(name).get(0).getAsJsonObject();
            assertEquals("Additive",modifier.get("CalculationType").getAsString(),family);assertEquals("Max",modifier.get("Target").getAsString(),family);
            return new Described("Adds "+number(modifier.get("Amount").getAsDouble())+" to your maximum "+stat(name),seconds(duration),duration,RULES);
        }
        if(data.has("DamageResistance")) {
            var causes=data.getAsJsonObject("DamageResistance");assertEquals(1,causes.size(),family);
            String cause=causes.keySet().iterator().next();var modifier=causes.getAsJsonArray(cause).get(0).getAsJsonObject();
            assertEquals("Percent",modifier.get("CalculationType").getAsString(),family);assertEquals(1.0,modifier.get("Amount").getAsDouble(),family);
            return switch(cause) {
                case "Fire" -> new Described("You take no damage from fire, burning or lava",seconds(duration),duration,RULES);
                case "Poison" -> new Described("You take no poison damage, and poison already on you is cleared",seconds(duration),duration,RULES);
                default -> fail("Describe immunity to "+cause+" in AlchemyEffectsDocTest");
            };
        }
        return switch(family) {
            case "BlastWard" -> new Described("You take no damage from explosions",seconds(duration),duration,RULES);
            case "Repellent" -> new Described("Scaraks, spiders and scorpions ignore you. It ends early if you attack one of them. Broodmothers are not fooled",seconds(duration),duration,RULES);
            default -> fail("Describe the "+family+" effect in AlchemyEffectsDocTest");
        };
    }
    private static String material(String item) {
        var ingredient=AlchemyIngredients.find(item);
        return ingredient!=null?ingredient.name():MATERIALS.getOrDefault(item,"`"+item+"`");
    }

    static String render() throws Exception {
        var out=new StringBuilder();
        out.append("# Alchemy effects\n\n");
        out.append("Every potion effect in Aetherhaven, the plants that give it, and the rules of the cauldron.\n\n");
        out.append("This page is written from the game data by the test `AlchemyEffectsDocTest`. Do not edit it by hand. Change the data,\n");
        out.append("then run that test once with the environment variable `AETHERHAVEN_WRITE_DOCS=1` to rewrite the page. The test fails\n");
        out.append("whenever this page and the data disagree.\n\n");

        out.append("## What each effect does\n\n");
        out.append("There are ").append(AlchemyIngredients.all().stream().map(AlchemyIngredients.Ingredient::effect).distinct().count()).append(" effects in ")
            .append(AlchemyIngredients.all().stream().map(AlchemyIngredients.Ingredient::family).distinct().count()).append(" kinds. A higher strength of the same kind is the same effect, only better.\n\n");
        out.append("| Effect | Strength | What it does | Lasts | Ingredients |\n| --- | --- | --- | --- | --- |\n");
        var families=new LinkedHashSet<String>();
        for(var ingredient:AlchemyIngredients.all())families.add(ingredient.family());
        for(String family:families) {
            assertTrue(FAMILY.containsKey(family),"Name the "+family+" family in AlchemyEffectsDocTest.FAMILY");
            for(int strength:AlchemyIngredients.strengths(family)) {
                var described=describe(family,strength);
                var plants=AlchemyIngredients.all().stream().filter(i->i.family().equals(family)&&i.strength()==strength).map(AlchemyIngredients.Ingredient::name).toList();
                out.append("| ").append(FAMILY.get(family)).append(" | ").append(strength).append(" | ").append(described.does()).append(" | ")
                    .append(described.lasts()).append(" | ").append(String.join(", ",plants)).append(" |\n");
            }
        }

        out.append("\n## What each ingredient gives\n\n");
        out.append(AlchemyIngredients.all().size()).append(" plants can be brewed. The cauldron shows a plant's property as unknown until your town has bottled it once.\n\n");
        out.append("| Ingredient | Effect | Strength | Shown at the cauldron as | Item id | Effect id |\n| --- | --- | --- | --- | --- | --- |\n");
        for(var ingredient:AlchemyIngredients.all())
            out.append("| ").append(ingredient.name()).append(" | ").append(FAMILY.get(ingredient.family())).append(" | ").append(ingredient.strength()).append(" | ")
                .append(ingredient.description()).append(" | `").append(ingredient.item()).append("` | `").append(ingredient.effect()).append("` |\n");

        out.append("\n## Mixing\n\n");
        out.append("- A mixture is 1 to 3 different plants. The cauldron starts with 1 ingredient slot. The second slot needs Alchemist Mastery 3 and 150 gold,\n");
        out.append("  the third needs Alchemist Mastery 6 and 350 gold. Buy them under Building upgrades.\n");
        out.append("- Each plant adds its own effect, and one drink gives you all of them at once.\n");
        out.append("- Two plants of the same kind of effect do not add up. Only the stronger one counts and the weaker one is spent for nothing.\n");
        out.append("- The same rule holds between drinks. While a stronger effect of a kind is running, a weaker drink of that kind does nothing for it.\n");
        out.append("  A stronger drink replaces a weaker running effect, and the same strength starts its time again.\n");
        var drink=read(RES.resolve("Server/Item/RootInteractions/Aetherhaven/Aetherhaven_Alchemy_Drink.json")).getAsJsonArray("Interactions").get(0).getAsJsonObject()
            .getAsJsonArray("Interactions").get(1).getAsJsonObject().getAsJsonObject("Next");
        assertEquals(1,drink.size());
        out.append("- To drink, hold the flask's secondary use for ").append(drink.keySet().iterator().next()).append(" seconds. Each drink uses one charge.\n");

        out.append("\n## Brewing: hold the brew steady\n\n");
        out.append("Fill flask always works and gives a Good potion. For a better one, press Start brewing first.\n\n");
        out.append("- A brew lasts ").append(RULES.beats()).append(" beats. Each press of a button is one beat, so there is no clock and no hurry.\n");
        out.append("- Heat runs from 0 to ").append(AlchemyBrew.GAUGE).append(" and starts at ").append(RULES.startHeat()).append(". Every beat it rises by ").append(RULES.rise())
            .append(". Stoke makes that ").append(RULES.rise()+RULES.stoke()).append(". Stir cools it by ").append(RULES.stir()-RULES.rise()).append(" instead.\n");
        out.append("- A glowing band drifts up or down the gauge every beat. The page says which way, and a pale outline shows about where it will be.\n");
        out.append("- A beat counts when the heat ends inside the band. Touching its edge counts too.\n");
        out.append("- Nothing is spent until a flask is filled. Brew again as often as you like, or stop and bottle a Good potion at any moment.\n");
        out.append("- Changing the ingredients starts a new brew. Filling a flask uses the brew up.\n\n");
        out.append("| Brew | Beats inside the band | Lasting effects | Health, Mana and Stamina restores |\n| --- | --- | --- | --- |\n");
        out.append("| Good | ").append(RULES.greatBeats()-1).append(" or fewer, or not brewed | as listed | as listed |\n");
        out.append("| Great | ").append(RULES.greatBeats()).append(" to ").append(RULES.perfectBeats()-1).append(" | ").append(AlchemyBrew.Rules.percent(RULES.duration(AlchemyBrew.Quality.GREAT)))
            .append("% longer | ").append(AlchemyBrew.Rules.percent(RULES.restore(AlchemyBrew.Quality.GREAT))).append("% more |\n");
        out.append("| Perfect | ").append(RULES.perfectBeats()).append(RULES.perfectBeats()<RULES.beats()?" to "+RULES.beats():"").append(" | ").append(AlchemyBrew.Rules.percent(RULES.duration(AlchemyBrew.Quality.PERFECT)))
            .append("% longer | ").append(AlchemyBrew.Rules.percent(RULES.restore(AlchemyBrew.Quality.PERFECT))).append("% more |\n");
        out.append("\nThe quality belongs to the potion. It stays in the flask until the last charge is drunk or poured away, and the flask's tooltip shows it.\n");
        out.append("\nHow wide the band is, out of ").append(AlchemyBrew.GAUGE).append(". Each extra ingredient makes it narrower and makes it move further each beat. Each level of Alchemist Mastery makes it wider.\n\n");
        out.append("| Ingredients | Band moves each beat | Mastery 1 | Mastery 3 | Mastery 6 | Mastery 10 |\n| --- | --- | --- | --- | --- | --- |\n");
        for(int ingredients=1;ingredients<=3;ingredients++) {
            out.append("| ").append(ingredients).append(" | about ").append(RULES.drift(ingredients));
            for(int mastery:new int[]{1,3,6,10})out.append(" | ").append(RULES.width(ingredients,mastery));
            out.append(" |\n");
        }
        out.append("\nWhat each effect becomes with a better brew.\n\n");
        out.append("| Effect | Strength | Good | Great | Perfect |\n| --- | --- | --- | --- | --- |\n");
        for(String family:families)for(int strength:AlchemyIngredients.strengths(family)) {
            var described=describe(family,strength);
            out.append("| ").append(FAMILY.get(family)).append(" | ").append(strength).append(" | ").append(described.good()).append(" | ").append(described.great()).append(" | ").append(described.perfect()).append(" |\n");
        }

        out.append("\n## Filling a flask\n\n");
        out.append("- Filling takes one of each plant in the mixture for every charge the flask holds. A flask of 5 charges with 3 plants takes 5 of each.\n");
        out.append("- Plants are taken from your inventory first. If you may open town storage, the rest comes from the warehouse and then from what\n");
        out.append("  production buildings and grower sites hold. Supplies set aside for a courier are never taken.\n");
        out.append("- Nothing is spent unless the flask is filled. If something is short, the cauldron names the plant and how many more you need.\n");
        out.append("- A flask must be empty to be filled. Empty flask pours away what is left.\n");

        out.append("\n## The flask\n\n");
        var cost=AlchemyFlaskCraft.load(AlchemyEffectsDocTest.class.getClassLoader());assertFalse(cost.isEmpty());
        out.append("Craft a flask at the Laboratory cauldron with Craft flask. It costs ")
            .append(String.join(", ",cost.entrySet().stream().map(part->part.getValue()+" "+material(part.getKey())).toList()))
            .append(". There is no recipe to learn. A new flask is empty and holds 1 charge.\n\n");
        out.append("Upgrade flask at the cauldron adds one charge at a time. It keeps the potion inside and does not refill it.\n\n");
        out.append("| Capacity after the upgrade | Alchemist Mastery needed | Cost |\n| --- | --- | --- |\n");
        for(int capacity=1;capacity<5;capacity++)
            out.append("| ").append(capacity+1).append(" charges | ").append(AlchemyFlask.upgradeMastery(capacity)).append(" | ")
                .append(AlchemyFlask.upgradeLeatherCount(capacity)).append(" ").append(material(AlchemyFlask.upgradeLeather(capacity))).append(" |\n");

        out.append("\n## What makes a potion stronger\n\n");
        out.append("- The plant. Strength 2 and 3 plants give more, or last longer, as the first table shows.\n");
        out.append("- The brew. A Great or Perfect brew lasts longer or restores more, as the brewing tables show.\n");
        out.append("- Flask size does not change the effect. A bigger flask holds more drinks of the same potion.\n");
        out.append("- Alchemist Mastery does not change the effect itself. It opens ingredient slots and bigger flasks, and it widens the band so better brews come easier.\n");
        out.append("- Orin earns Mastery only when you fill a flask, and the same whatever the brew: 7 for each plant in the mixture plus 7 for each charge of the flask, so ")
            .append(AlchemyFlask.bottlingXp(1,1)).append(" for a new flask with one plant and ").append(AlchemyFlask.bottlingXp(3,5)).append(" for the largest flask with three.\n");
        return out.toString();
    }

    /** Rewrites the page first when asked to, so every check below reads the new one. */
    @BeforeAll static void rewriteWhenAsked() throws Exception {
        if("1".equals(System.getenv("AETHERHAVEN_WRITE_DOCS"))) {
            Files.createDirectories(DOC.getParent());Files.writeString(DOC,render(),StandardCharsets.UTF_8);
        }
    }
    @Test void theDocumentIsExactlyWhatTheDataSays() throws Exception {
        String wanted=render();
        assertTrue(Files.isRegularFile(DOC),DOC+" is missing. Run this test with AETHERHAVEN_WRITE_DOCS=1 to write it.");
        var have=Files.readString(DOC,StandardCharsets.UTF_8).replace("\r\n","\n").lines().toList();var want=wanted.lines().toList();
        for(int line=0;line<Math.max(have.size(),want.size());line++) {
            String a=line<have.size()?have.get(line):"(end of page)",b=line<want.size()?want.get(line):"(end of page)";
            assertEquals(b,a,DOC+" is out of date at line "+(line+1)+". Run this test with AETHERHAVEN_WRITE_DOCS=1 to rewrite it.");
        }
    }
    @Test void everyIngredientAndEveryEffectIsOnThePage() throws Exception {
        String page=Files.readString(DOC,StandardCharsets.UTF_8);
        for(var ingredient:AlchemyIngredients.all()) {
            assertTrue(page.contains("`"+ingredient.item()+"`"),"Ingredient missing from "+DOC+": "+ingredient.item());
            assertTrue(page.contains("| "+ingredient.name()+" |"),"Ingredient name missing from "+DOC+": "+ingredient.name());
            assertTrue(page.contains("`"+ingredient.effect()+"`"),"Effect missing from "+DOC+": "+ingredient.effect());
        }
        try(var files=Files.list(EFFECTS)) {
            for(var file:files.toList())assertTrue(page.contains("`"+file.getFileName().toString().replace(".json","")+"`"),"Effect asset missing from "+DOC+": "+file.getFileName());
        }
        assertFalse(page.contains(" - ")||page.contains("—")||page.contains("–"),"No dashes used as pauses");
    }
    @Test void theNumbersQuotedFromOtherPagesAreStillTheOnesInTheCode() throws Exception {
        String upgrades=Files.readString(Path.of("src/main/java/com/hexvane/aetherhaven/workplace/WorkplaceUpgradesPage.java"));
        assertTrue(upgrades.contains("case ALCHEMY -> n.tier()==1?150L:350L;"),"Ingredient slot prices changed: update the Mixing section");
        assertTrue(upgrades.contains("<n.tier()*3)return \"Requires Alchemist Mastery \""),"Ingredient slot Mastery changed: update the Mixing section");
        assertEquals(7L*(2+3),AlchemyFlask.bottlingXp(2,3),"Mastery per fill changed: update the last section");
    }
    @Test void namesOnThePageAreTheNamesTheGameShows() throws Exception {
        String appData=System.getenv("APPDATA");
        Assumptions.assumeTrue(appData!=null);
        var assets=Path.of(appData,"Hytale/install/pre-release/package/game/latest/Assets.zip");
        Assumptions.assumeTrue(Files.isRegularFile(assets));
        var names=new HashMap<String,String>();
        try(var zip=new ZipFile(assets.toFile())) {
            var lang=new String(zip.getInputStream(zip.getEntry("Server/Languages/en-US/server.lang")).readAllBytes(),StandardCharsets.UTF_8);
            for(String line:lang.split("\\R")) {
                int at=line.indexOf(".name = ");
                if(line.startsWith("items.")&&at>0)names.put(line.substring("items.".length(),at),line.substring(at+".name = ".length()).strip());
            }
        }
        for(var ingredient:AlchemyIngredients.all())assertEquals(names.get(ingredient.item()),ingredient.name(),ingredient.item());
        for(var material:MATERIALS.entrySet())assertEquals(names.get(material.getKey()),material.getValue(),material.getKey());
        for(int capacity=1;capacity<5;capacity++)assertEquals(names.get(AlchemyFlask.upgradeLeather(capacity)),AlchemyFlask.upgradeLeatherName(capacity));
    }
}
