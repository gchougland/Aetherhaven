package com.hexvane.aetherhaven.wall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3i;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Walks every direction and piece pairing and checks the joined pieces are exactly adjacent. Uses authored connection
 * data only, so no prefab loading is needed.
 */
@Tag("wall-placement")
class WallConnectionAdjacencyTest {
    private WallStyle style;

    @BeforeEach
    void setUp() {
        WallStyleCatalog catalog = WallStyleFixtures.coreCatalog();
        WallStyleCatalog.setForTests(catalog);
        style = catalog.style(WallStyleCatalog.DEFAULT_STYLE_ID);
        assertNotNull(style);
    }

    @AfterEach
    void tearDown() {
        WallStyleCatalog.setForTests(null);
    }

    @Test
    void everyDirectionAndPairingJoinsFlush() {
        for (WallCardinal startDir : WallCardinal.values()) {
            for (WallPlacementChainPlanner.PieceKind firstKind : firstKinds()) {
                Chain chain = start(new Vector3i(100, 64, 200), firstKind, startDir);
                for (WallPlacementChainPlanner.PieceKind nextKind : allKinds()) {
                    for (WallCardinal joinDir : chain.allowed()) {
                        if (nextKind == WallPlacementChainPlanner.PieceKind.TOWER && chain.lastIsTower()) {
                            continue;
                        }
                        Chain forked = chain.copy();
                        forked.place(nextKind, joinDir);
                    }
                }
            }
        }
    }

    @Test
    void straightRunsHaveNoGapOrOverlap() {
        for (WallCardinal dir : WallCardinal.values()) {
            Chain chain = start(new Vector3i(-40, 70, 15), WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
            for (int i = 0; i < 4; i++) {
                chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
            }
            chain.place(WallPlacementChainPlanner.PieceKind.GATE, dir);
            chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
        }
    }

    @Test
    void wallThenTowerThenWallStaysFlushInEveryDirection() {
        for (WallCardinal dir : WallCardinal.values()) {
            Chain chain = start(new Vector3i(8, 64, -8), WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
            chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
            chain.place(WallPlacementChainPlanner.PieceKind.TOWER, dir);
            chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
            chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
        }
    }

    @Test
    void cornersTurnFlushInEveryDirection() {
        for (WallCardinal dir : WallCardinal.values()) {
            for (WallCardinal turn : List.of(dir.rotateCw90(), dir.rotateCcw90())) {
                Chain chain = start(new Vector3i(0, 64, 0), WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
                chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
                chain.place(WallPlacementChainPlanner.PieceKind.TOWER, dir);
                chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, turn);
                chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, turn);
            }
        }
    }

    @Test
    void raisingAPieceDoesNotChangeTheHorizontalJoin() {
        Chain low = start(new Vector3i(5, 40, 5), WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.EAST);
        low.place(WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.EAST);
        Chain high = start(new Vector3i(5, 120, 5), WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.EAST);
        high.place(WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.EAST);
        assertEquals(low.last().signAnchor().x, high.last().signAnchor().x);
        assertEquals(low.last().signAnchor().z, high.last().signAnchor().z);
    }

    @Test
    void towerPlacedOnItsOwnIsAnEndTowerWithOneDoor() {
        Chain chain = start(new Vector3i(0, 64, 0), WallPlacementChainPlanner.PieceKind.TOWER, WallCardinal.EAST);
        assertEquals("plot_wall_tower_endcap_s", chain.last().constructionId());
        assertEquals(EnumSet.of(WallCardinal.EAST), chain.last().towerConnectionDirs());
    }

    @Test
    void towerAtTheEndOfARunKeepsASingleDoor() {
        Chain chain = start(new Vector3i(0, 64, 0), WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.EAST);
        chain.place(WallPlacementChainPlanner.PieceKind.TOWER, WallCardinal.EAST);
        assertEquals("plot_wall_tower_endcap_s", chain.last().constructionId());
        assertEquals(EnumSet.of(WallCardinal.WEST), chain.last().towerConnectionDirs());
    }

    @Test
    void towerBecomesARunThroughTowerOnlyWhenTheRunCarriesOn() {
        Chain chain = start(new Vector3i(0, 64, 0), WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.EAST);
        chain.place(WallPlacementChainPlanner.PieceKind.TOWER, WallCardinal.EAST);
        chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.EAST);
        WallPlacementChainPlanner.ChainCommittedPiece tower = chain.at(chain.size() - 2);
        assertEquals("plot_wall_tower_eastdoor_ns", tower.constructionId());
        assertEquals(EnumSet.of(WallCardinal.WEST, WallCardinal.EAST), tower.towerConnectionDirs());
    }

    /**
     * A tower is a junction: once it is down the run may carry straight on or turn either way. Only the side the wall
     * came in on is closed off. Stopping there is what leaves it an end cap.
     */
    @Test
    void aTowerOpensStraightOnAndBothTurns() {
        for (WallCardinal dir : WallCardinal.values()) {
            Chain chain = start(new Vector3i(24, 64, 24), WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
            chain.place(WallPlacementChainPlanner.PieceKind.TOWER, dir);
            assertEquals(
                EnumSet.of(dir, dir.rotateCw90(), dir.rotateCcw90()),
                chain.allowed(),
                "pads open after a tower placed going " + dir
            );
        }
    }

    /**
     * Picking Tower opens all three onward directions straight away: the tower can only sit on the open face of the
     * wall behind it, so the arrows say where the run leaves the tower instead of where the tower goes.
     */
    @Test
    void aTowerBeingLinedUpAlreadyOffersStraightOnAndBothTurns() {
        for (WallCardinal dir : WallCardinal.values()) {
            assertEquals(
                EnumSet.of(dir, dir.rotateCw90(), dir.rotateCcw90()),
                WallPlacementChainPlanner.allowedExpandDirectionsForNewTower(style, dir),
                "pads open while a tower is lined up going " + dir
            );
        }
    }

    /**
     * The turn happens at the tower. Once the run has been sent one way the tower has that door, so the straight wall
     * being lined up after it only offers straight on and the turn does not carry over.
     */
    @Test
    void onceTheRunLeavesATowerItOnlyCarriesStraightOn() {
        for (WallCardinal dir : WallCardinal.values()) {
            for (WallCardinal outgoing : List.of(dir, dir.rotateCw90(), dir.rotateCcw90())) {
                Chain chain = start(new Vector3i(60, 64, -60), WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
                chain.place(WallPlacementChainPlanner.PieceKind.TOWER, dir);
                chain.aim(outgoing);
                assertEquals(
                    EnumSet.of(outgoing),
                    chain.allowed(),
                    "pads after a tower sent the run " + outgoing
                );
                WallPlacementChainPlanner.ChainCommittedPiece tower = chain.last();
                assertEquals(EnumSet.of(dir.opposite(), outgoing), tower.towerConnectionDirs());
                chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, outgoing);
                assertEquals(
                    EnumSet.of(outgoing),
                    chain.allowed(),
                    "pads on the wall after a tower that sent the run " + outgoing
                );
            }
        }
    }

    @Test
    void towerBecomesACornerTowerWhenTheRunTurns() {
        Chain chain = start(new Vector3i(0, 64, 0), WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.EAST);
        chain.place(WallPlacementChainPlanner.PieceKind.TOWER, WallCardinal.EAST);
        chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.SOUTH);
        WallPlacementChainPlanner.ChainCommittedPiece tower = chain.at(chain.size() - 2);
        assertEquals("plot_wall_tower_outercorner_se", tower.constructionId());
        assertEquals(EnumSet.of(WallCardinal.WEST, WallCardinal.SOUTH), tower.towerConnectionDirs());
    }

    /**
     * Undoing the piece after a tower puts the tower back on the doors it was placed with, at the anchor it had before
     * the upgrade moved it. This is the math the wand replays when a step is undone.
     */
    @Test
    void undoingThePieceAfterATowerPutsItBackToAnEndTower() {
        for (WallCardinal dir : WallCardinal.values()) {
            Chain chain = start(new Vector3i(0, 64, 0), WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
            chain.place(WallPlacementChainPlanner.PieceKind.TOWER, dir);
            WallPlacementChainPlanner.ChainCommittedPiece endTower = chain.last();
            EnumSet<WallCardinal> placedDoors = EnumSet.copyOf(endTower.towerConnectionDirs());

            chain.place(WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
            WallPlacementChainPlanner.ChainCommittedPiece upgraded = chain.at(chain.size() - 2);
            assertEquals(2, upgraded.towerConnectionDirs().size());

            WallStyle.ResolvedPiece reverted = style.resolveTower(placedDoors);
            assertNotNull(reverted);
            assertEquals(endTower.constructionId(), reverted.constructionId());
            Vector3i reseated =
                WallPlacementChainPlanner.reseatUpgradedTower(
                    style, chain.at(chain.size() - 3), placedDoors.iterator().next(), reverted
                );
            assertNotNull(reseated);
            assertEquals(endTower.signAnchor().x, reseated.x);
            assertEquals(endTower.signAnchor().z, reseated.z);
        }
    }

    @Test
    void straightPiecesOnlyOfferTheirOwnRunAxis() {
        Chain chain = start(new Vector3i(0, 64, 0), WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.EAST);
        assertEquals(EnumSet.of(WallCardinal.EAST), chain.allowed());
        Chain northSouth = start(new Vector3i(0, 64, 0), WallPlacementChainPlanner.PieceKind.SEGMENT, WallCardinal.NORTH);
        assertEquals(EnumSet.of(WallCardinal.NORTH), northSouth.allowed());
    }

    @Nonnull
    private static List<WallPlacementChainPlanner.PieceKind> firstKinds() {
        return List.of(
            WallPlacementChainPlanner.PieceKind.SEGMENT,
            WallPlacementChainPlanner.PieceKind.GATE,
            WallPlacementChainPlanner.PieceKind.TOWER
        );
    }

    @Nonnull
    private static List<WallPlacementChainPlanner.PieceKind> allKinds() {
        return firstKinds();
    }

    @Nonnull
    private Chain start(
        @Nonnull Vector3i anchor,
        @Nonnull WallPlacementChainPlanner.PieceKind kind,
        @Nonnull WallCardinal dir
    ) {
        Chain chain = new Chain(style);
        chain.placeFirst(anchor, kind, dir);
        return chain;
    }

    /** Minimal stand in for the wand session: places pieces through the planner and checks each join. */
    private static final class Chain {
        @Nonnull
        private final WallStyle style;
        @Nonnull
        private final List<WallPlacementChainPlanner.ChainCommittedPiece> committed = new ArrayList<>();

        Chain(@Nonnull WallStyle style) {
            this.style = style;
        }

        @Nonnull
        Chain copy() {
            Chain out = new Chain(style);
            out.committed.addAll(committed);
            return out;
        }

        void placeFirst(
            @Nonnull Vector3i anchor,
            @Nonnull WallPlacementChainPlanner.PieceKind kind,
            @Nonnull WallCardinal dir
        ) {
            WallPlacementChainPlanner.ExpandPreviewPlan plan =
                WallPlacementChainPlanner.planExpandPreview(style, anchor, kind, List.of(), dir);
            assertNotNull(plan, "no plan for first piece " + kind + " facing " + dir);
            committed.add(
                new WallPlacementChainPlanner.ChainCommittedPiece(
                    plan.resolvedConstructionId(),
                    new Vector3i(plan.anchor()),
                    plan.rotationSteps(),
                    plan.towerConnections(),
                    dir
                )
            );
        }

        void place(@Nonnull WallPlacementChainPlanner.PieceKind kind, @Nonnull WallCardinal dir) {
            assertTrue(allowed().contains(dir), "pad " + dir + " should be open after " + last().constructionId());
            upgradeTowerIfNeeded(kind, dir);
            WallPlacementChainPlanner.ChainCommittedPiece from = last();
            WallPlacementChainPlanner.ExpandPreviewPlan plan =
                WallPlacementChainPlanner.planExpandPreview(
                    style, new Vector3i(from.signAnchor()), kind, List.copyOf(committed), dir
                );
            assertNotNull(plan, "no plan for " + kind + " going " + dir + " after " + from.constructionId());
            WallPieceDefinition fromDef = definition(from.constructionId());
            WallPieceDefinition toDef = definition(plan.resolvedConstructionId());
            WallJoinAssert.assertFlush(
                fromDef,
                from.signAnchor(),
                from.rotationSteps(),
                dir,
                toDef,
                plan.anchor(),
                plan.rotationSteps(),
                from.constructionId() + " -> " + plan.resolvedConstructionId() + " going " + dir
            );
            committed.add(
                new WallPlacementChainPlanner.ChainCommittedPiece(
                    plan.resolvedConstructionId(),
                    new Vector3i(plan.anchor()),
                    plan.rotationSteps(),
                    plan.towerConnections(),
                    dir
                )
            );
        }

        /** Mirrors the session lining the next piece up: the tower opens the door the run leaves by. */
        void aim(@Nonnull WallCardinal dir) {
            upgradeTowerIfNeeded(WallPlacementChainPlanner.PieceKind.SEGMENT, dir);
        }

        /**
         * Mirrors the session: the tower behind the next piece is always reshaped from the doors it was placed with,
         * so picking a different direction swaps a straight tower for a corner one instead of stacking doors.
         */
        private void upgradeTowerIfNeeded(
            @Nonnull WallPlacementChainPlanner.PieceKind kind, @Nonnull WallCardinal dir
        ) {
            if (kind == WallPlacementChainPlanner.PieceKind.TOWER) {
                return;
            }
            WallPlacementChainPlanner.ChainCommittedPiece tower = last();
            EnumSet<WallCardinal> placedDoors = tower.reshapeFromDirs();
            if (!tower.isTower() || placedDoors == null || placedDoors.size() != 1) {
                return;
            }
            EnumSet<WallCardinal> pair = EnumSet.copyOf(placedDoors);
            pair.add(dir);
            if (pair.equals(tower.towerConnectionDirs())) {
                return;
            }
            WallStyle.ResolvedPiece resolved = style.resolveTower(pair);
            assertNotNull(resolved, "no tower for faces " + pair);
            Vector3i anchor = new Vector3i(tower.signAnchor());
            if (committed.size() >= 2) {
                WallCardinal incoming = placedDoors.iterator().next();
                Vector3i reseated =
                    WallPlacementChainPlanner.reseatUpgradedTower(
                        style, committed.get(committed.size() - 2), incoming, resolved
                    );
                if (reseated != null) {
                    anchor = new Vector3i(reseated.x, tower.signAnchor().y, reseated.z);
                }
            }
            committed.set(
                committed.size() - 1,
                new WallPlacementChainPlanner.ChainCommittedPiece(
                    resolved.constructionId(),
                    anchor,
                    resolved.rotationSteps(),
                    pair,
                    tower.chainExpandDir(),
                    placedDoors
                )
            );
            if (committed.size() >= 2) {
                WallPlacementChainPlanner.ChainCommittedPiece before = committed.get(committed.size() - 2);
                WallCardinal joinDir = before.chainExpandDir() == null ? null : tower.chainExpandDir();
                if (joinDir != null) {
                    WallJoinAssert.assertFlush(
                        definition(before.constructionId()),
                        before.signAnchor(),
                        before.rotationSteps(),
                        joinDir,
                        definition(resolved.constructionId()),
                        anchor,
                        resolved.rotationSteps(),
                        "upgraded tower " + resolved.constructionId() + " behind " + before.constructionId()
                    );
                }
            }
        }

        @Nonnull
        EnumSet<WallCardinal> allowed() {
            WallCardinal arrival =
                last().chainExpandDir() == null ? null : last().chainExpandDir().opposite();
            return WallPlacementChainPlanner.allowedExpandDirections(
                style, WallPlacementChainPlanner.PieceKind.SEGMENT, List.copyOf(committed), arrival
            );
        }

        boolean lastIsTower() {
            return last().isTower();
        }

        int size() {
            return committed.size();
        }

        @Nonnull
        WallPlacementChainPlanner.ChainCommittedPiece at(int index) {
            return committed.get(index);
        }

        @Nonnull
        WallPlacementChainPlanner.ChainCommittedPiece last() {
            return committed.get(committed.size() - 1);
        }

        @Nonnull
        private WallPieceDefinition definition(@Nonnull String constructionId) {
            WallStyle.Piece piece = style.pieceByConstructionId(constructionId);
            assertNotNull(piece, "style has no piece " + constructionId);
            return piece.definition();
        }
    }
}
