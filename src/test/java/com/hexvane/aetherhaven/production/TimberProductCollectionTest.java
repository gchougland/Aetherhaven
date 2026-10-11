package com.hexvane.aetherhaven.production;

import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

@Tag("town")
class TimberProductCollectionTest {
    private static final String FIBRE="Ingredient_Fibre", STICK="Ingredient_Stick", SAP="Ingredient_Tree_Sap";
    private static final String WOOD="Wood_Ash_Trunk", SEED="Plant_Sapling_Ash";

    private TimberPlantingReceipt tree() {
        var root=new Position(0,100,0);
        return new TimberPlantingReceipt(UUID.randomUUID(),SEED,WOOD,root,0,List.of(
            new TimberPlantingReceipt.Stage(0,"seed",List.of(new Cell(root,SEED,0,Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1,"mature",List.of(new Cell(root,WOOD,0,Kind.TIMBER)))));
    }

    private ProductionCatalog.Entry catalog() {
        return ProductionCatalog.Entry.create(List.of(WOOD,FIBRE,STICK,SAP,SEED),
            new int[]{1,1,1,1,1},new long[]{100,100,50,20,5});
    }

    @Test void aHugeCanopyCannotTurnLeafDropsIntoAnUnboundedHaulingQueue() {
        var collection=new TimberProductCollection(Map.of(FIBRE,7,STICK,50,SAP,0));
        for(int leaf=0;leaf<16_384;leaf++) {
            collection.collect(FIBRE,2);collection.collect(STICK,1);collection.collect(SAP,1);
            collection.collect("Ingredient_Diamond",1);
        }
        assertEquals(Map.of(FIBRE,7,STICK,50),collection.products());
        assertEquals(List.of(FIBRE,STICK),new ArrayList<>(collection.products().keySet()));
        assertThrows(UnsupportedOperationException.class,()->collection.products().put(FIBRE,999));
    }

    @Test void selectionAndStorageUpgradesDetermineTheCollectionBudgetWithoutChangingStock() {
        var p=new PlotProductionState();p.migrateIfNeeded();p.timber().setGathersFibre(true);
        p.addAmount(FIBRE,93,100);p.addAmount(SAP,20,20);p.addAmount(SEED,4,5);
        var limits=NativeTimberProducts.collectionLimits(p,catalog(),tree());
        assertEquals(Map.of(FIBRE,7,SAP,0,SEED,1,STICK,50),limits,"Sticks, sap and the sapling are always gathered; fibre is the one choice");
        p.setAdamantineLevel(1);
        assertEquals(Map.of(FIBRE,107,SAP,20,SEED,6,STICK,100),NativeTimberProducts.collectionLimits(p,catalog(),tree()));
        assertEquals(93,p.getAmount(FIBRE));assertEquals(20,p.getAmount(SAP));assertEquals(4,p.getAmount(SEED));
        assertEquals(7,limits.get(FIBRE),"An existing harvest budget does not expand after an upgrade");
    }

    @Test void smallNativeDropsAreNotInflatedAndLargeDropQuantitiesCannotOverflow() {
        var collection=new TimberProductCollection(Map.of(FIBRE,100,STICK,Integer.MAX_VALUE));
        collection.collect(FIBRE,3);collection.collect(FIBRE,0);collection.collect(FIBRE,-1);
        collection.collect(STICK,Integer.MAX_VALUE);collection.collect(STICK,Integer.MAX_VALUE);
        assertEquals(Map.of(FIBRE,3,STICK,Integer.MAX_VALUE),collection.products());
    }

    @Test void oneTimberSlotAlwaysCollectsItsOwnSaplingSapAndSticks() {
        var p=new PlotProductionState();p.migrateIfNeeded();p.setSlotCursor(0,0);
        assertEquals(Set.of(SEED,SAP,STICK),NativeTimberProducts.selected(p,catalog(),tree()));
        assertEquals(Map.of(SEED,5,SAP,20,STICK,50),NativeTimberProducts.collectionLimits(p,catalog(),tree()));
        for(String product:List.of(WOOD,SEED,SAP,STICK)) {
            long cap=WorkplaceProductionUpgrades.effectiveMaxStorage(p,catalog(),product);
            p.addAmount(product,cap,cap);
            assertTrue(NativeTimberProducts.hasRoom(p,catalog(),tree()),"One full store alone never holds a tree back: "+product);
            p.removeAmountUpTo(product,cap);
        }
        for(String product:List.of(WOOD,SEED,SAP,STICK)) {
            long cap=WorkplaceProductionUpgrades.effectiveMaxStorage(p,catalog(),product);
            p.addAmount(product,cap,cap);
        }
        assertFalse(NativeTimberProducts.hasRoom(p,catalog(),tree()),"Only completely full stores do");
    }

    @Test void guaranteedProductsFillZeroRollsWithoutDoublingDropsOrExceedingSpace() {
        var collection=new TimberProductCollection(Map.of(SEED,1,SAP,1,STICK,4));
        collection.collect(STICK,3);
        for(int i=0;i<2;i++) {
            collection.ensureMinimum(SEED,1);collection.ensureMinimum(SAP,1);collection.ensureMinimum(STICK,1);
        }
        assertEquals(Map.of(SEED,1,SAP,1,STICK,3),collection.products());
        collection.ensureMinimum(STICK,100);
        assertEquals(4,collection.products().get(STICK));
    }
}
