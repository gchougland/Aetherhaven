package com.hexvane.aetherhaven.inn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.inn.InnDailyRefill.Arrival;
import com.hexvane.aetherhaven.inn.InnDailyRefill.Outcome;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Each test is one way a new day used to pass with empty rooms at the Inn, or a promise the fix must keep. */
@Tag("town")
class InnDailyRefillTest {
    private static final int ROOMS = InnPoolService.MAX_VISITORS;

    /** A stand-in Inn: who is on the list, who can be found, who may visit and whether a spawn is accepted. */
    private static final class Inn implements InnDailyRefill.Rooms {
        private static final class Guest {
            final String role;
            boolean found = true;
            boolean questLocked;

            Guest(String role) {
                this.role = role;
            }
        }

        final List<Guest> guests = new ArrayList<>();
        final List<String> pool = new ArrayList<>(List.of("Farmer", "Rancher", "Miner", "Logger"));
        final Set<String> eligible = new HashSet<>(pool);
        final Set<String> unknownRoles = new HashSet<>();
        /** How many more spawns the world accepts. A world that is not simulating the Inn accepts none. */
        int spawnsAccepted = Integer.MAX_VALUE;
        /** Visitors standing in the Inn who are not on its list, such as a duplicate that is about to be removed. */
        int strays;
        int arrivals;
        int attempts;
        int sentHome;
        int mostGuestsEverListed;

        Guest add(String role) {
            Guest g = new Guest(role);
            guests.add(g);
            mostGuestsEverListed = Math.max(mostGuestsEverListed, guests.size());
            return g;
        }

        /** Free guests who can be found leave. The others keep their room, as in the real Inn. */
        void sendFreeGuestsHome() {
            sentHome++;
            guests.removeIf(g -> g.found && !g.questLocked);
        }

        List<String> roles() {
            return guests.stream().map(g -> g.role).toList();
        }

        @Override
        public boolean guestUnaccountedFor() {
            return guests.stream().anyMatch(g -> !g.found && !g.questLocked);
        }

        @Override
        public List<String> candidates() {
            return List.copyOf(pool);
        }

        @Override
        public int listed() {
            return guests.size();
        }

        @Override
        public int present() {
            return (int) guests.stream().filter(g -> g.found).count() + strays;
        }

        @Override
        public boolean wanted(String role) {
            return eligible.contains(role) && guests.stream().noneMatch(g -> g.role.equals(role));
        }

        @Override
        public Arrival arrive(String role) {
            attempts++;
            if (unknownRoles.contains(role)) {
                return Arrival.NEVER;
            }
            if (spawnsAccepted <= 0) {
                return Arrival.NOT_NOW;
            }
            spawnsAccepted--;
            arrivals++;
            add(role);
            return Arrival.ARRIVED;
        }
    }

    private static TownRecord townSettledOn(long day) {
        TownRecord town = new TownRecord();
        town.setInnPoolLastMorningEpochDay(day);
        town.setInnPoolLastFillEpochDay(day);
        return town;
    }

    private static boolean attend(TownRecord town, long innDay, boolean morningWindow, Inn inn) {
        return InnDailyRefill.attend(town, innDay, morningWindow, ROOMS, inn::sendFreeGuestsHome, () -> {}, inn);
    }

    @Test
    void aNormalMorningChangesTheGuestsOnceAndThenLeavesTheInnAlone() {
        TownRecord town = townSettledOn(9);
        Inn inn = new Inn();
        inn.add("Miner");
        inn.add("Logger");

        assertTrue(attend(town, 10, true, inn));
        assertEquals(1, inn.sentHome);
        assertEquals(List.of("Farmer", "Rancher"), inn.roles());
        assertEquals(10L, town.getInnPoolLastMorningEpochDay());
        assertEquals(10L, town.getInnPoolLastFillEpochDay());

        for (int minute = 0; minute < 500; minute++) {
            assertFalse(attend(town, 10, minute < 300, inn), "A settled day must not be touched again");
        }
        assertEquals(1, inn.sentHome);
        assertEquals(2, inn.arrivals);
        assertEquals(List.of("Farmer", "Rancher"), inn.roles());
    }

    @Test
    void aGuestWhoCannotBeFoundHoldsTheFillOpenInsteadOfWritingOffTheDay() {
        // One room is empty and yesterday's other guest is in a part of town that is not loaded at dawn.
        TownRecord town = townSettledOn(9);
        Inn inn = new Inn();
        Inn.Guest away = inn.add("Miner");
        away.found = false;

        assertTrue(attend(town, 10, true, inn));
        assertEquals(0, inn.attempts, "Nobody is invited while a room cannot be judged");
        assertEquals(10L, town.getInnPoolLastMorningEpochDay());
        assertEquals(9L, town.getInnPoolLastFillEpochDay(), "The day's fill is still owed");
        for (int minute = 0; minute < 50; minute++) {
            assertFalse(attend(town, 10, true, inn));
        }
        assertEquals(0, inn.attempts);

        // The guest's chunk loads. The empty room is filled on that very update.
        away.found = true;
        assertTrue(attend(town, 10, false, inn));
        assertEquals(List.of("Miner", "Farmer"), inn.roles());
        assertEquals(10L, town.getInnPoolLastFillEpochDay());
        assertEquals(1, inn.sentHome, "Guests are only sent home once a day");
        assertEquals(ROOMS, inn.mostGuestsEverListed);
    }

    @Test
    void aRefusedSpawnIsTriedAgainUntilTheRoomsAreFull() {
        // The Inn's chunk is in memory but not simulated: every spawn is refused.
        TownRecord town = townSettledOn(9);
        Inn inn = new Inn();
        inn.spawnsAccepted = 0;

        assertTrue(attend(town, 10, true, inn));
        assertEquals(9L, town.getInnPoolLastFillEpochDay());
        assertTrue(inn.guests.isEmpty());
        for (int minute = 0; minute < 20; minute++) {
            assertFalse(attend(town, 10, true, inn));
        }
        assertTrue(inn.attempts > 20, "Every update tries again");

        inn.spawnsAccepted = Integer.MAX_VALUE;
        assertTrue(attend(town, 10, true, inn));
        assertEquals(List.of("Farmer", "Rancher"), inn.roles());
        assertEquals(10L, town.getInnPoolLastFillEpochDay());
        int attemptsWhenFull = inn.attempts;
        assertFalse(attend(town, 10, true, inn));
        assertEquals(attemptsWhenFull, inn.attempts);
    }

    @Test
    void aHalfFinishedFillOnlyBringsTheMissingGuest() {
        TownRecord town = townSettledOn(9);
        Inn inn = new Inn();
        inn.spawnsAccepted = 1;

        assertEquals(Outcome.RETRY, InnDailyRefill.fill(ROOMS, inn));
        assertEquals(List.of("Farmer"), inn.roles());

        town.setInnPoolLastMorningEpochDay(10L);
        inn.spawnsAccepted = Integer.MAX_VALUE;
        for (int minute = 0; minute < 30; minute++) {
            attend(town, 10, true, inn);
        }
        assertEquals(2, inn.arrivals);
        assertEquals(ROOMS, inn.listed());
        assertEquals(ROOMS, inn.mostGuestsEverListed, "No update ever puts more guests in the Inn than it has rooms");
        assertEquals(Set.of("Farmer", "Rancher"), new HashSet<>(inn.roles()), "Never the same visitor twice");
    }

    @Test
    void aMorningSpentAwayFromTheInnIsMadeUpOnReturn() {
        // Nobody was near the Inn during the morning window. The player comes home in the evening.
        TownRecord town = townSettledOn(9);
        Inn inn = new Inn();
        inn.add("Miner");

        assertTrue(attend(town, 10, false, inn));
        assertEquals(1, inn.sentHome);
        assertEquals(List.of("Farmer", "Rancher"), inn.roles());
        assertEquals(10L, town.getInnPoolLastMorningEpochDay());
        assertEquals(10L, town.getInnPoolLastFillEpochDay());
    }

    @Test
    void aTimeJumpThatSentGuestsHomeStillOwesTheFill() {
        // Sleeping or a time command skipped the morning: the catch up emptied the rooms and recorded the day,
        // and the clock now reads evening.
        TownRecord town = townSettledOn(9);
        town.setInnPoolLastMorningEpochDay(10L);
        Inn inn = new Inn();

        assertTrue(InnDailyRefill.fillOwed(town.getInnPoolLastMorningEpochDay(), town.getInnPoolLastFillEpochDay(), 10));
        assertTrue(attend(town, 10, false, inn));
        assertEquals(0, inn.sentHome, "The guests already left during the jump");
        assertEquals(ROOMS, inn.listed());
        assertEquals(10L, town.getInnPoolLastFillEpochDay());
    }

    @Test
    void aRoomEmptiedAfterTheDailyFillWaitsForTheNextDay() {
        TownRecord town = townSettledOn(9);
        Inn inn = new Inn();
        attend(town, 10, true, inn);
        assertEquals(ROOMS, inn.listed());

        // The Farmer is recruited and moves out at noon.
        inn.guests.removeFirst();
        inn.eligible.remove("Farmer");
        for (int minute = 0; minute < 200; minute++) {
            assertFalse(attend(town, 10, minute < 100, inn));
        }
        assertEquals(List.of("Rancher"), inn.roles());

        assertTrue(attend(town, 11, true, inn));
        assertEquals(ROOMS, inn.listed());
        assertFalse(inn.roles().contains("Farmer"));
    }

    @Test
    void anInnWithNobodyLeftToInviteSettlesTheDayAndFillsWhenSomeoneQualifies() {
        // Every candidate is held back by Town Level or a quest. That is not a failure to retry all day.
        TownRecord town = townSettledOn(9);
        Inn inn = new Inn();
        inn.eligible.clear();

        assertTrue(attend(town, 10, true, inn));
        assertEquals(0, inn.attempts);
        assertEquals(10L, town.getInnPoolLastFillEpochDay());
        assertTrue(inn.guests.isEmpty());

        // The town grows during the day. Which visitors that allows comes from data, not from this code.
        inn.eligible.add("Logger");
        assertFalse(attend(town, 10, false, inn));
        assertTrue(inn.guests.isEmpty(), "New visitors arrive with the next day's fill");

        assertTrue(attend(town, 11, true, inn));
        assertEquals(List.of("Logger"), inn.roles());
        assertEquals(11L, town.getInnPoolLastFillEpochDay());
    }

    @Test
    void anUnknownVisitorNeitherBlocksTheOthersNorRetriesForever() {
        Inn inn = new Inn();
        inn.unknownRoles.add("Farmer");
        assertEquals(Outcome.DONE, InnDailyRefill.fill(ROOMS, inn));
        assertEquals(List.of("Rancher", "Miner"), inn.roles());

        Inn onlyUnknown = new Inn();
        onlyUnknown.unknownRoles.add("Farmer");
        onlyUnknown.eligible.retainAll(Set.of("Farmer"));
        assertEquals(Outcome.DONE, InnDailyRefill.fill(ROOMS, onlyUnknown), "Waiting cannot make an unknown visitor appear");
        assertTrue(onlyUnknown.guests.isEmpty());
    }

    @Test
    void aStrayVisitorBeingClearedAwayDelaysTheFillInsteadOfCancellingIt() {
        // A duplicate visitor is still standing in the Inn for one more update, so the Inn looks full when it is not.
        TownRecord town = townSettledOn(9);
        town.setInnPoolLastMorningEpochDay(10L);
        Inn inn = new Inn();
        inn.add("Miner");
        inn.strays = 1;

        assertFalse(attend(town, 10, true, inn));
        assertEquals(0, inn.attempts, "Nobody is invited into a room that still looks taken");
        assertEquals(9L, town.getInnPoolLastFillEpochDay());

        inn.strays = 0;
        assertTrue(attend(town, 10, true, inn));
        assertEquals(List.of("Miner", "Farmer"), inn.roles());
        assertEquals(10L, town.getInnPoolLastFillEpochDay());
    }

    @Test
    void aQuestGuestWhoCannotBeFoundKeepsTheirRoomAndDoesNotBlockTheOther() {
        TownRecord town = townSettledOn(9);
        Inn inn = new Inn();
        Inn.Guest quest = inn.add("Miner");
        quest.questLocked = true;
        quest.found = false;

        assertTrue(attend(town, 10, true, inn));
        assertEquals(List.of("Miner", "Farmer"), inn.roles());
        assertEquals(10L, town.getInnPoolLastFillEpochDay());
    }

    @Test
    void theInnDayStartsAtTheMorningHourAndANewInnWaitsForARealMorning() {
        assertEquals(10, InnDailyRefill.innDay(10, true));
        assertEquals(9, InnDailyRefill.innDay(10, false), "The hours after midnight belong to the day before");

        // Between midnight and morning nothing new is due, but a fill left over from yesterday still is.
        assertFalse(InnDailyRefill.refreshDue(9L, InnDailyRefill.innDay(10, false), false));
        assertTrue(InnDailyRefill.fillOwed(9L, 8L, InnDailyRefill.innDay(10, false)));
        assertFalse(InnDailyRefill.fillOwed(9L, 9L, InnDailyRefill.innDay(10, false)));
        assertTrue(InnDailyRefill.refreshDue(9L, InnDailyRefill.innDay(10, true), false));
        assertFalse(InnDailyRefill.fillOwed(9L, 9L, 10), "Guests go home before new ones are invited");

        // A brand new Inn has never had a morning.
        TownRecord fresh = new TownRecord();
        assertNull(fresh.getInnPoolLastMorningEpochDay());
        Inn inn = new Inn();
        assertFalse(attend(fresh, 10, false, inn));
        assertTrue(inn.guests.isEmpty());
        assertTrue(attend(fresh, 10, true, inn));
        assertEquals(ROOMS, inn.listed());
    }

    @Test
    void severalMissedDaysAreSettledInOneVisitWithoutExtraGuests() {
        TownRecord town = townSettledOn(3);
        Inn inn = new Inn();
        inn.add("Miner");
        inn.add("Logger");
        assertTrue(attend(town, 10, false, inn));
        assertEquals(1, inn.sentHome);
        assertEquals(2, inn.arrivals);
        assertEquals(ROOMS, inn.mostGuestsEverListed);
        assertFalse(attend(town, 10, false, inn));
    }

    @Test
    void theSettledDaySurvivesASaveSoAReloadDoesNotFillAgain() {
        TownRecord town = townSettledOn(9);
        Inn inn = new Inn();
        attend(town, 10, true, inn);
        Gson gson = new Gson();
        TownRecord reloaded = gson.fromJson(gson.toJson(town), TownRecord.class);
        assertEquals(10L, reloaded.getInnPoolLastMorningEpochDay());
        assertEquals(10L, reloaded.getInnPoolLastFillEpochDay());
        assertEquals("1970-01-11", reloaded.getInnPoolLastMorningGameDate());
        assertFalse(attend(reloaded, 10, true, inn));
        assertEquals(2, inn.arrivals);

        // An unfinished fill is saved as unfinished and is picked up after the reload.
        TownRecord owing = townSettledOn(9);
        Inn closed = new Inn();
        closed.spawnsAccepted = 0;
        attend(owing, 10, true, closed);
        TownRecord owingReloaded = gson.fromJson(gson.toJson(owing), TownRecord.class);
        closed.spawnsAccepted = Integer.MAX_VALUE;
        assertTrue(attend(owingReloaded, 10, false, closed));
        assertEquals(ROOMS, closed.listed());
        assertEquals(1, closed.sentHome, "The reload must not send the day's guests home a second time");
    }

    @Test
    void aRefusalIsRetriedAtOnceAndThenLessOftenUntilANewDayOrSuccess() {
        // The first refusal is tried again on the very next update. One that never clears must not search the
        // whole town every minute for the rest of the day.
        assertEquals(1, InnDailyRefill.retryDelayMinutes(1));
        assertEquals(2, InnDailyRefill.retryDelayMinutes(2));
        assertEquals(32, InnDailyRefill.retryDelayMinutes(6));
        assertEquals(60, InnDailyRefill.retryDelayMinutes(7));
        assertEquals(60, InnDailyRefill.retryDelayMinutes(1000));
        assertEquals(1, InnDailyRefill.retryDelayMinutes(0));

        InnDailyRefill.Pause first = InnDailyRefill.Pause.after(null, 10, 600);
        assertTrue(first.holds(10, 600));
        assertFalse(first.holds(10, 601), "The next update tries again");
        InnDailyRefill.Pause second = InnDailyRefill.Pause.after(first, 10, 601);
        assertEquals(2, second.refusals());
        assertTrue(second.holds(10, 602));
        assertFalse(second.holds(10, 603));

        int attempts = 0;
        InnDailyRefill.Pause pause = null;
        for (long minute = 0; minute < 24 * 60; minute++) {
            if (pause != null && pause.holds(10, minute)) {
                continue;
            }
            attempts++;
            pause = InnDailyRefill.Pause.after(pause, 10, minute);
        }
        assertTrue(attempts >= 20 && attempts <= 40, "A whole day of refusals costs a handful of attempts, got " + attempts);

        InnDailyRefill.Pause late = InnDailyRefill.Pause.after(pause, 10, 1439);
        assertFalse(late.holds(11, 1440), "A new Inn day starts fresh");
        assertEquals(1, InnDailyRefill.Pause.after(late, 11, 1440).refusals());
        assertFalse(late.holds(10, 100), "A clock turned back does not leave the Inn waiting");
    }

    @Test
    void oneTownInTroubleDoesNotCostTheOtherTownsTheirGuests() {
        List<String> served = new ArrayList<>();
        List<String> reported = new ArrayList<>();
        InnDailyRefill.each(
            List.of("first", "broken", "last"),
            town -> {
                if (town.equals("broken")) {
                    throw new IllegalStateException("spawn failed half way");
                }
                served.add(town);
            },
            (town, failure) -> reported.add(town + ": " + failure.getMessage())
        );
        assertEquals(List.of("first", "last"), served);
        assertEquals(List.of("broken: spawn failed half way"), reported);
    }
}
