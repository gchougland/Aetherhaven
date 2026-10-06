package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

@Tag("town")
class PhysicalProductionOutputPolicyTest {
    @Test void adoptionRestrictsNewChoicesWithoutRemovingLegacyStockOrPurchasedUnlocks() {
        var state=new PlotProductionState();
        state.addAmount("Ingredient_Tree_Sap",35,100);
        state.addWorkplaceOutputUnlock("Plant_Sapling_Ash");
        assertTrue(PhysicalProductionOutputPolicy.available(state,"Ingredient_Tree_Sap"));
        var root=new Position(0,100,0);
        var tree=new TimberPlantingReceipt(UUID.randomUUID(),"Plant_Sapling_Beech","Wood_Beech_Trunk",root,0,List.of(
            new TimberPlantingReceipt.Stage(0,"sapling",List.of(new Cell(root,"Plant_Sapling_Beech",0,Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1,"mature",List.of(new Cell(root,"Wood_Beech_Trunk",0,Kind.TIMBER)))));
        java.util.function.Function<Position,TimberProductionState.Ground> ground=p->new TimberProductionState.Ground(
            true,p.y()<100?"Soil_Dirt":"Empty",0,0,p.y()>=100,p.y()<100,p.y()<100,false);
        var timber=state.timber();
        assertNull(timber.plan(0,1,0,tree,p->true,ground));
        assertNull(timber.activate(1));
        assertTrue(PhysicalProductionOutputPolicy.available(state,"Wood_Maple_Trunk"));
        assertTrue(PhysicalProductionOutputPolicy.available(state,"Ingredient_Tree_Sap"));
        assertTrue(PhysicalProductionOutputPolicy.available(state,"Wood_Burnt_Trunk"));
        assertTrue(PhysicalProductionOutputPolicy.available(state,"Plant_Sapling_Ash"));
        assertNotNull(PhysicalProductionOutputPolicy.selectionIssue(state,-1,tree.output()));
        assertNotNull(PhysicalProductionOutputPolicy.selectionIssue(state,5,tree.output()));
        var prepared=timber.prepare(0,1,tree,p->true,ground);
        assertNull(timber.plant(0,1,prepared,p->true,ground,()->true));
        assertNull(PhysicalProductionOutputPolicy.selectionIssue(state,0,tree.output()));
        assertNotNull(PhysicalProductionOutputPolicy.selectionIssue(state,0,"Wood_Maple_Trunk"));
        var copy=new Gson().fromJson(new Gson().toJson(state),PlotProductionState.class);
        assertEquals(35,copy.getAmount("Ingredient_Tree_Sap"));
        assertTrue(copy.isWorkplaceOutputUnlocked("Plant_Sapling_Ash"));
        assertTrue(PhysicalProductionOutputPolicy.available(copy,"Ingredient_Tree_Sap"));
        assertNotNull(PhysicalProductionOutputPolicy.selectionIssue(copy,0,"Wood_Maple_Trunk"));
    }

    @Test void palmStemsAndUnusualCanopiesHaveExplicitHarvestKinds() {
        var palm=NativeTimberGrowth.species("Wood_Palm_Trunk");
        assertEquals(Kind.TIMBER,palm.kind("Wood_Palm_Branch_Long"));
        assertEquals(Kind.FOLIAGE,palm.kind("Plant_Fruit_Coconut"));
        assertNull(palm.kind("Wood_Oak_Trunk"));
        var crystal=NativeTimberGrowth.species("Wood_Crystal_Trunk");
        assertEquals(Kind.FOLIAGE,crystal.kind("Rock_Crystal_Pink_Block"));
        assertEquals(Kind.TIMBER,crystal.kind("Wood_Crystal_Trunk_Full"));
        assertThrows(UnsupportedOperationException.class,()->NativeTimberGrowth.species().clear());
    }
}
