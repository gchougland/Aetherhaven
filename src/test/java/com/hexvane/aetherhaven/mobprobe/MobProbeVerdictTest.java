package com.hexvane.aetherhaven.mobprobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.mobprobe.MobProbe.Expect;
import com.hexvane.aetherhaven.mobprobe.MobProbe.Facts;
import com.hexvane.aetherhaven.mobprobe.MobProbe.Result;
import com.hexvane.aetherhaven.mobprobe.MobProbe.Verdict;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** How {@code /ah test mobs} turns what it saw of each creature into PASSED or FAILED. */
@Tag("entity")
class MobProbeVerdictTest {
    /** placed, stored, gone, dead, killedByCreature, struckByCreature, struckByTown, struckTownFirst */
    private static final Facts ALIVE = new Facts(true, false, false, false, false, false, false, false);
    private static final Facts STRUCK_BY_GUARD_STILL_ALIVE = new Facts(true, false, false, false, false, true, true, false);
    private static final Facts KILLED_BY_GUARD = new Facts(true, false, false, true, true, true, true, false);
    private static final Facts KILLED_BY_ANOTHER_CREATURE = new Facts(true, false, false, true, true, true, false, false);
    private static final Facts DIED_UNTOUCHED = new Facts(true, false, false, true, false, false, false, false);
    private static final Facts TAKEN_OUT = new Facts(true, false, true, false, false, false, false, false);
    private static final Facts STORED = new Facts(true, true, true, false, false, false, false, false);
    private static final Facts NOT_PLACED = new Facts(false, false, false, false, false, false, false, false);
    private static final Facts STRUCK_THE_TOWN_FIRST_THEN_KILLED = new Facts(true, false, false, true, true, true, true, true);

    @Test
    void aCreatureThatMustLivePassesOnlyUntouchedAndAlive() {
        assertEquals(Verdict.AS_EXPECTED, ALIVE.judge(Expect.MUST_LIVE).verdict());
        assertEquals(Verdict.WRONG, KILLED_BY_GUARD.judge(Expect.MUST_LIVE).verdict());
        assertEquals(Verdict.WRONG, DIED_UNTOUCHED.judge(Expect.MUST_LIVE).verdict());
        assertEquals(Verdict.WRONG, TAKEN_OUT.judge(Expect.MUST_LIVE).verdict());
    }

    @Test
    void oneBlowFromTheTownFailsItBeforeTheKillHasFinished() {
        assertEquals(Verdict.WRONG, STRUCK_BY_GUARD_STILL_ALIVE.judge(Expect.MUST_LIVE).verdict());
    }

    @Test
    void aCreatureTheGuardsMayFightCountsTheSameAliveOrDead() {
        assertEquals(Verdict.AS_EXPECTED, ALIVE.judge(Expect.MAY_BE_FOUGHT).verdict());
        assertEquals(Verdict.AS_EXPECTED, STRUCK_BY_GUARD_STILL_ALIVE.judge(Expect.MAY_BE_FOUGHT).verdict());
        assertEquals(Verdict.AS_EXPECTED, KILLED_BY_GUARD.judge(Expect.MAY_BE_FOUGHT).verdict());
    }

    @Test
    void aCreatureTheGuardsMayFightMustStillNotVanishOrDieUnfought() {
        assertEquals(Verdict.WRONG, DIED_UNTOUCHED.judge(Expect.MAY_BE_FOUGHT).verdict());
        assertEquals(Verdict.WRONG, TAKEN_OUT.judge(Expect.MAY_BE_FOUGHT).verdict());
    }

    @Test
    void aCreatureThatStruckTheTownFirstHasPickedItsOwnFight() {
        assertEquals(Verdict.AS_EXPECTED, STRUCK_THE_TOWN_FIRST_THEN_KILLED.judge(Expect.MUST_LIVE).verdict());
    }

    @Test
    void whatTheCheckCouldNotSeeIsNotJudgedEitherWay() {
        for (Expect expect : Expect.values()) {
            assertEquals(Verdict.NOT_JUDGED, NOT_PLACED.judge(expect).verdict(), expect.name());
            assertEquals(Verdict.NOT_JUDGED, STORED.judge(expect).verdict(), expect.name());
        }
        assertEquals(Verdict.NOT_JUDGED, KILLED_BY_ANOTHER_CREATURE.judge(Expect.MUST_LIVE).verdict());
    }

    private static Result result(Expect expect, Facts facts) {
        var judgement = facts.judge(expect);
        return new Result("line", "plaza Sheep (plain)", expect, judgement.verdict(), judgement.why(),
            facts.placed() && !facts.dead() && !facts.gone(), facts.struckByTown(), expect == Expect.MUST_LIVE && facts.struckTownFirst());
    }

    @Test
    void theLastLineEndsInPassedWhenAGuardKillsOnlyWhatItMayFight() {
        var tally = MobProbeScenarios.tally("In a loaded town", List.of(result(Expect.MUST_LIVE, ALIVE), result(Expect.MUST_LIVE, ALIVE),
            result(Expect.MAY_BE_FOUGHT, KILLED_BY_GUARD), result(Expect.MAY_BE_FOUGHT, STRUCK_BY_GUARD_STILL_ALIVE),
            result(Expect.MAY_BE_FOUGHT, ALIVE)));
        assertEquals("In a loaded town: 5 creatures. Must live 2: 2 unharmed. May be fought 3: 2 fought, 1 killed. Not judged 0. Wrong 0.",
            tally.text());
        String line = MobProbeScenarios.verdictLine(List.of(tally), List.of(), 120);
        assertTrue(line.startsWith("Mobs check: In a loaded town: 5 creatures."), line);
        assertTrue(line.endsWith(" Took 120 seconds. PASSED"), line);
    }

    @Test
    void theSameKillCountsTheSameWhetherOrNotItFinishedInTime() {
        var finished = MobProbeScenarios.tally("Town", List.of(result(Expect.MUST_LIVE, ALIVE), result(Expect.MAY_BE_FOUGHT, KILLED_BY_GUARD)));
        var unfinished = MobProbeScenarios.tally("Town", List.of(result(Expect.MUST_LIVE, ALIVE), result(Expect.MAY_BE_FOUGHT, STRUCK_BY_GUARD_STILL_ALIVE)));
        assertTrue(finished.passed());
        assertTrue(unfinished.passed());
        assertEquals(finished.fought(), unfinished.fought());
    }

    @Test
    void theLastLineEndsInFailedAndNamesTheCreatureWhenOneThatMustLiveIsStruck() {
        var tally = MobProbeScenarios.tally("Town", List.of(result(Expect.MUST_LIVE, ALIVE), result(Expect.MUST_LIVE, STRUCK_BY_GUARD_STILL_ALIVE)));
        assertFalse(tally.passed());
        String line = MobProbeScenarios.verdictLine(List.of(tally), List.of(), 120);
        assertTrue(line.contains("Wrong 1 (plaza Sheep (plain): struck by the town)"), line);
        assertTrue(line.endsWith(" FAILED"), line);
    }

    @Test
    void aPartThatDidNotRunOrJudgedNothingFails() {
        var fine = MobProbeScenarios.tally("Open", List.of(result(Expect.MUST_LIVE, ALIVE)));
        assertTrue(MobProbeScenarios.verdictLine(List.of(fine), List.of("the raids part: IllegalStateException: no town"), 9).endsWith(" FAILED"));
        var blind = MobProbeScenarios.tally("Town", List.of(result(Expect.MUST_LIVE, STORED), result(Expect.MUST_LIVE, NOT_PLACED)));
        assertFalse(blind.passed());
        assertTrue(MobProbeScenarios.verdictLine(List.of(fine, blind), List.of(), 9).endsWith(" FAILED"));
        assertTrue(MobProbeScenarios.verdictLine(List.of(), List.of(), 9).endsWith(" FAILED"));
    }

    @Test
    void aFewCreaturesTheGameStoredAreNamedButDoNotFailTheCheck() {
        var tally = MobProbeScenarios.tally("Town", List.of(result(Expect.MUST_LIVE, ALIVE), result(Expect.MUST_LIVE, STORED)));
        assertTrue(tally.passed());
        assertTrue(tally.text().contains("Not judged 1 (plaza Sheep (plain): stored by the game)"), tally.text());
    }
}
