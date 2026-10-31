package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class GroveSelectionTest {
    private static final Position FIRST = new Position(1, 80, 1);
    private static final Position LAST = new Position(90, 82, 12);
    private static final Position NEW = new Position(40, 79, 60);

    @Test void uprootConfirmationCannotDeleteAReplacementTreeAtTheSamePositionAndSlot() {
        var selection = new GroveSelection(FIRST);
        selection.refresh(Map.of(0, FIRST));
        var original = java.util.UUID.randomUUID();
        var replacement = java.util.UUID.randomUUID();
        selection.confirmUproot(original);
        selection.refresh(Map.of(0, FIRST));
        assertFalse(selection.takeUprootConfirmation(replacement));
        selection.confirmUproot(replacement);
        assertTrue(selection.takeUprootConfirmation(replacement));
        assertFalse(selection.takeUprootConfirmation(replacement), "A confirmation can only be used once");
    }

    @Test void switchingAwayAndBackCancelsUprootConfirmation() {
        var selection = new GroveSelection(FIRST);
        selection.refresh(Map.of(0, FIRST, 5, LAST));
        var tree = java.util.UUID.randomUUID();
        selection.confirmUproot(tree);
        selection.move(1);
        selection.move(-1);
        assertFalse(selection.takeUprootConfirmation(tree));
        selection.confirmUproot(tree);
        selection.selectSlot(5);
        selection.selectSlot(0);
        assertFalse(selection.takeUprootConfirmation(tree));
    }

    @Test void storageStartsAtFirstOccupiedSlotAndNavigationSkipsEmptySlotsAndWraps() {
        var selection = new GroveSelection(null);
        selection.refresh(Map.of(5, LAST, 1, FIRST));
        assertEquals(FIRST, selection.block());
        assertEquals(1, selection.slot());
        selection.move(-1);
        assertEquals(LAST, selection.block());
        assertEquals(5, selection.slot());
        selection.move(1);
        assertEquals(FIRST, selection.block());
        assertEquals(2, selection.count());
    }

    @Test void openingAStoneAndSelectingAnotherChangesBothPhysicalAndManagementTargets() {
        var selection = new GroveSelection(FIRST);
        selection.refresh(Map.of(0, FIRST, 5, LAST));
        selection.selectSlot(5);
        assertEquals(LAST, selection.block(), "Sapling capture and binding must use the newly selected position");
        assertEquals(5, selection.slot(), "Blessing and uprooting must use the newly selected grove");
        selection.selectSlot(4);
        assertEquals(LAST, selection.block(), "Empty upgrade slots are not placed stones");
    }

    @Test void uprootedRemoteStoneRemainsSelectedAndCanBeReboundWithoutReopening() {
        var selection = new GroveSelection(FIRST);
        selection.refresh(Map.of(0, FIRST, 5, LAST));
        selection.selectSlot(5);
        selection.rememberSelected();
        selection.refresh(Map.of(0, FIRST));
        assertEquals(LAST, selection.block());
        assertEquals(-1, selection.slot());
        selection.refresh(Map.of(0, FIRST, 1, LAST));
        assertEquals(LAST, selection.block());
        assertEquals(1, selection.slot(), "Binding can reuse a different available slot without moving selection");
        assertEquals(2, selection.count());
    }

    @Test void anUnboundOpeningStoneCanBeRevisitedAfterCheckingOtherTrees() {
        var selection = new GroveSelection(NEW);
        selection.refresh(Map.of(0, FIRST, 5, LAST));
        assertEquals(NEW, selection.block());
        assertEquals(-1, selection.slot());
        selection.move(1);
        assertEquals(FIRST, selection.block());
        selection.move(-1);
        assertEquals(NEW, selection.block());
        assertEquals(3, selection.number());
    }

    @Test void anotherPlayersSlotReuseDoesNotRetargetActionsToTheirNewTree() {
        var selection = new GroveSelection(LAST);
        selection.refresh(Map.of(0, FIRST, 5, LAST));
        selection.refresh(Map.of(0, FIRST, 5, NEW));
        assertEquals(LAST, selection.block());
        assertEquals(-1, selection.slot(), "An old confirmation must never act on another stone in the reused slot");
        selection.selectSlot(5);
        assertEquals(NEW, selection.block());
    }

    @Test void allSixStonesAreReachableAndRefreshDoesNotResetSelection() {
        var selection = new GroveSelection(null);
        var stones = new java.util.TreeMap<Integer, Position>();
        for (int i = 0; i < 6; i++) stones.put(i, new Position(i * 20, 80, 0));
        selection.refresh(stones);
        for (int i = 0; i < 6; i++) {
            selection.refresh(stones);
            assertEquals(i, selection.slot());
            assertEquals(stones.get(i), selection.block());
            selection.move(1);
        }
        assertEquals(0, selection.slot());
        assertEquals(6, selection.count());
    }

    @Test void anEmptyMillHasNoTargetAndFindsItsFirstNewStoneOnRefresh() {
        var selection = new GroveSelection(null);
        selection.refresh(Map.of());
        selection.move(1);
        selection.move(-1);
        assertNull(selection.block());
        assertEquals(-1, selection.slot());
        assertEquals(0, selection.number());
        selection.refresh(Map.of(3, LAST));
        assertEquals(LAST, selection.block());
    }
}
