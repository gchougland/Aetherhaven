package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class FestivalLookSelectionTest {
    private static final Gson GSON = new Gson();

    @Test
    void layoutUsesTheTownLookThenFallsBackToTheOriginal() {
        FestivalDefinition carnival =
            parse("{\"id\":\"carnival\",\"displayName\":\"Carnival Festival\",\"prefabPath\":\"Festivals/Festival_Carnival.prefab.json\"}");
        FestivalDefinition neon =
            parse(
                "{\"id\":\"carnival_neon\",\"displayName\":\"Neon Carnival\",\"prefabPath\":\"Festivals/Festival_Carnival_Neon.prefab.json\",\"festivalVariant\":true,\"countsAsFestivalId\":\"carnival\"}"
            );
        FestivalCatalog catalog = FestivalCatalog.forTests(List.of(carnival, neon));
        TownRecord town = new TownRecord();

        assertSame(carnival, FestivalLookSelection.layoutFor(catalog, town, carnival));

        town.setSelectedFestivalLookId("carnival", "carnival_neon");
        assertSame(neon, FestivalLookSelection.layoutFor(catalog, town, carnival));

        town.setSelectedFestivalLookId("carnival", null);
        assertSame(carnival, FestivalLookSelection.layoutFor(catalog, town, carnival));
        assertEquals(List.of(neon), FestivalLookSelection.looksOf(catalog, "carnival"));
    }

    private static final FestivalDefinition BASE = parse("{\"id\":\"carnival\",\"prefabPath\":\"core.prefab.json\"}");
    private static FestivalDefinition look(String id, String holiday, String style) {
        return parse("{\"id\":\""+id+"\",\"prefabPath\":\""+id+".prefab.json\",\"countsAsFestivalId\":\""
            +holiday+"\",\"styleId\":\""+style+"\"}");
    }
    private static com.hexvane.aetherhaven.construction.ConstructionCatalog buildings() {
        var definitions = new java.util.LinkedHashMap<String, com.hexvane.aetherhaven.construction.ConstructionDefinition>();
        for (String style : List.of("neon", "stormwind", "core")) {
            String id = "square_"+style;
            definitions.put(id, GSON.fromJson("{\"id\":\""+id+"\",\"styleId\":\""+style
                +"\",\"countsAsConstructionId\":\"plot_festival_square\"}",
                com.hexvane.aetherhaven.construction.ConstructionDefinition.class));
        }
        return com.hexvane.aetherhaven.construction.ConstructionCatalog.forTests(definitions);
    }
    private static com.hexvane.aetherhaven.town.PlotInstance square(String style) {
        return GSON.fromJson("{\"constructionId\":\"square_"+style+"\"}", com.hexvane.aetherhaven.town.PlotInstance.class);
    }

    @Test void automaticallyUsesPlacedSquareStyleEvenWhenTownPreferenceAndManualLookDiffer() {
        var neon=look("neon_carnival","carnival"," NEON ");
        var storm=look("storm_carnival","carnival","stormwind");
        var catalog=FestivalCatalog.forTests(List.of(BASE,storm,neon));
        var town=new TownRecord();town.setPreferredBuildingStyleId("stormwind");
        town.setSelectedFestivalLookId("carnival",storm.getId());
        assertSame(neon,FestivalLookSelection.layoutFor(catalog,buildings(),town,square("neon"),BASE));
        assertSame(storm,FestivalLookSelection.layoutFor(catalog,buildings(),town,square("stormwind"),BASE));
    }

    @Test void missingStyleOrWrongHolidayFallsBackToCoreRatherThanAnotherStyle() {
        var catalog=FestivalCatalog.forTests(List.of(BASE,look("neon_other","wintertide","neon"),
            look("storm_carnival","carnival","stormwind")));
        var town=new TownRecord();
        assertSame(BASE,FestivalLookSelection.layoutFor(catalog,buildings(),town,square("neon"),BASE));
        assertSame(BASE,FestivalLookSelection.layoutFor(catalog,buildings(),town,square("core"),BASE));
        assertSame(BASE,FestivalLookSelection.layoutFor(catalog,buildings(),town,square("missing"),BASE));
    }

    @Test void multipleMatchingDesignsAreStableButAllowASameStylePreference() {
        var a=look("a_neon","carnival","neon");var b=look("b_neon","carnival","neon");
        var town=new TownRecord();
        var catalog=FestivalCatalog.forTests(List.of(BASE,b,a));
        assertSame(a,FestivalLookSelection.layoutFor(catalog,buildings(),town,square("neon"),BASE));
        assertSame(a,FestivalLookSelection.layoutFor(FestivalCatalog.forTests(List.of(a,BASE,b)),buildings(),town,square("neon"),BASE));
        town.setSelectedFestivalLookId("carnival",b.getId());
        assertSame(b,FestivalLookSelection.layoutFor(catalog,buildings(),town,square("neon"),BASE));
        town.setSelectedFestivalLookId("carnival",null);
        assertSame(a,FestivalLookSelection.layoutFor(catalog,buildings(),town,square("neon"),BASE));
    }

    @Test void pastedLayoutSurvivesSaveReloadAndNewDownloadsUntilFestivalEnds() {
        var b=look("b_neon","carnival","neon");
        var town=new TownRecord();town.setActiveFestivalId("carnival");town.setActiveFestivalLayoutId(b.getId());
        town=GSON.fromJson(GSON.toJson(town),TownRecord.class);
        var catalog=FestivalCatalog.forTests(List.of(BASE,b,look("a_new_download","carnival","neon")));
        assertSame(b,FestivalLookSelection.runningLayout(catalog,town,BASE));
        town.clearActiveFestival();
        org.junit.jupiter.api.Assertions.assertNull(town.getActiveFestivalLayoutId());
        assertEquals("a_new_download",FestivalLookSelection.layoutFor(catalog,buildings(),town,square("neon"),BASE).getId());
    }

    @Test void legacyRunningFestivalKeepsItsPastedLookButNextPlacementUsesSquareStyle() {
        var neon=look("neon_carnival","carnival","neon");var storm=look("storm_carnival","carnival","stormwind");
        var catalog=FestivalCatalog.forTests(List.of(BASE,neon,storm));
        var town=new TownRecord();town.setActiveFestivalId("carnival");town.setSelectedFestivalLookId("carnival",neon.getId());
        assertSame(neon,FestivalLookSelection.runningLayout(catalog,town,BASE));
        assertSame(storm,FestivalLookSelection.layoutFor(catalog,buildings(),town,square("stormwind"),BASE));
    }

    private static FestivalDefinition parse(String json) {
        return GSON.fromJson(json, FestivalDefinition.class);
    }
}
