package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabMaterialsCatalog;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.difficulty.EffectiveBuildingCosts;
import com.hexvane.aetherhaven.difficulty.WorldDifficultyState;
import java.lang.reflect.Field;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class PlotJournalRemovalRefundServiceTest {
    @Test
    void buildGoldWasPaidOnlyAfterBuildStarts() throws ReflectiveOperationException {
        PlotInstance blueprinting = testPlot(PlotInstanceState.BLUEPRINTING);
        PlotInstance assembling = testPlot(PlotInstanceState.ASSEMBLING);
        PlotInstance complete = testPlot(PlotInstanceState.COMPLETE);

        assertFalse(PlotJournalRemovalRefundService.buildGoldWasPaid(blueprinting));
        assertTrue(PlotJournalRemovalRefundService.buildGoldWasPaid(assembling));
        assertTrue(PlotJournalRemovalRefundService.buildGoldWasPaid(complete));
    }

    @Test
    void goldRefundIsEightyPercentFloored() throws ReflectiveOperationException {
        ConstructionDefinition def = testDefinition("plot_flower_shop", 45L);
        WorldDifficultyState difficulty = WorldDifficultyState.normalUntilChosen();
        long paid =
            EffectiveBuildingCosts.forDefinition(def, difficulty, PrefabMaterialsCatalog.empty())
                .getTreasuryGoldCoinCost();
        assertEquals(36L, PlotJournalRemovalRefundService.goldRefundFromEffectiveBuildGold(paid));
    }

    @Test
    void zeroCostBuildingRefundsNoGold() {
        assertEquals(0L, PlotJournalRemovalRefundService.goldRefundFromEffectiveBuildGold(0L));
    }

    @Test
    void difficultyMultiplierAffectsRefundBase() throws ReflectiveOperationException {
        ConstructionDefinition def = testDefinition("plot_house", 48L);
        WorldDifficultyState hard = new WorldDifficultyState();
        hard.setDifficultyChosen(true);
        hard.setGoldCostMultiplier(2.0);
        long paid =
            EffectiveBuildingCosts.forDefinition(def, hard, PrefabMaterialsCatalog.empty()).getTreasuryGoldCoinCost();
        assertEquals(96L, paid);
        assertEquals(76L, PlotJournalRemovalRefundService.goldRefundFromEffectiveBuildGold(paid));
    }

    @Test
    void successLangKeyMatchesRefundParts() {
        assertEquals(
            "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.removePlotSuccessGoldAndToken",
            PlotJournalRemovalRefundService.successLangKey(new PlotJournalRemovalRefundService.RefundResult(10L, true))
        );
        assertEquals(
            "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.removePlotSuccessTokenOnly",
            PlotJournalRemovalRefundService.successLangKey(new PlotJournalRemovalRefundService.RefundResult(0L, true))
        );
        assertEquals(
            "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.removePlotSuccessGoldOnly",
            PlotJournalRemovalRefundService.successLangKey(new PlotJournalRemovalRefundService.RefundResult(10L, false))
        );
    }

    @Nonnull
    private static PlotInstance testPlot(@Nonnull PlotInstanceState state) {
        return new PlotInstance(
            UUID.randomUUID(),
            "plot_test",
            state,
            new PlotFootprintRecord(0, 0, 0, 1, 1, 1),
            0,
            0,
            0,
            0L
        );
    }

    @Nonnull
    private static ConstructionDefinition testDefinition(@Nonnull String id, long treasuryGoldCoinCost)
        throws ReflectiveOperationException {
        ConstructionDefinition def = new ConstructionDefinition();
        setField(def, "id", id);
        setField(def, "treasuryGoldCoinCost", treasuryGoldCoinCost);
        return def;
    }

    private static void setField(@Nonnull Object target, @Nonnull String name, @Nonnull Object value)
        throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
