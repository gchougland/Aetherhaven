package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.town.TownRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The Inn's daily change of guests as a job that is owed until it is finished. Free guests go home once per Inn day,
 * then the empty rooms are filled. A fill that could not finish (a guest who cannot be found right now, a visitor who
 * failed to arrive) stays owed and is tried again on the next visit, so a day is never written off with rooms that
 * could have been filled. Once the day is settled nothing more happens until the next one: a room emptied later in the
 * day waits for the next morning, and no visit ever puts more guests in the Inn than it has rooms.
 * <p>
 * No world access here. {@link InnPoolService} supplies the real Inn through {@link Rooms}.
 */
public final class InnDailyRefill {
    private InnDailyRefill() {}

    public enum Outcome {
        /** Every room that can be filled is filled. The day is settled. */
        DONE,
        /** Something stood in the way that may clear by itself. Try again on the next visit. */
        RETRY
    }

    public enum Arrival {
        ARRIVED,
        /** The visitor could not be placed this time (the Inn is not being simulated, the spawn was refused). */
        NOT_NOW,
        /** This visitor can never arrive as things stand (unknown NPC role, no place to stand). */
        NEVER
    }

    /** What one attempt at filling the Inn needs to know and do. */
    public interface Rooms {
        /** A listed guest who is free to leave cannot be found right now, so their room cannot be judged. */
        boolean guestUnaccountedFor();

        /** Visitors to try, best first. Asked once per attempt, after the check above. */
        @Nonnull
        List<String> candidates();

        /** Guests on the Inn's list. */
        int listed();

        /** Guests standing in the world. */
        int present();

        /** Allowed to visit today and not already in town. */
        boolean wanted(@Nonnull String role);

        @Nonnull
        Arrival arrive(@Nonnull String role);
    }

    /**
     * The Inn's day begins at the morning hour, so the hours between midnight and morning still belong to the day
     * before.
     */
    public static long innDay(long calendarEpochDay, boolean morningHasBegun) {
        return morningHasBegun ? calendarEpochDay : calendarEpochDay - 1L;
    }

    /**
     * Free guests leave once per Inn day. A brand new Inn waits for a real morning. After that the change is owed for
     * any Inn day not yet handled, whatever the hour, so a morning spent away or skipped by a time jump is made up on
     * the next visit.
     */
    public static boolean refreshDue(@Nullable Long lastRefreshDay, long innDay, boolean morningWindow) {
        return lastRefreshDay == null ? morningWindow : lastRefreshDay < innDay;
    }

    /** Rooms are owed guests once this Inn day's guests have been sent home and until one fill has finished. */
    public static boolean fillOwed(@Nullable Long lastRefreshDay, @Nullable Long lastFillDay, long innDay) {
        return lastRefreshDay != null && lastRefreshDay >= innDay && (lastFillDay == null || lastFillDay < innDay);
    }

    private static boolean full(int rooms, @Nonnull Rooms inn) {
        return inn.listed() >= rooms || inn.present() >= rooms;
    }

    /** One attempt. Never asks for more arrivals than there are empty rooms. */
    @Nonnull
    public static Outcome fill(int rooms, @Nonnull Rooms inn) {
        if (inn.guestUnaccountedFor()) {
            return Outcome.RETRY;
        }
        boolean refused = false;
        for (String role : inn.candidates()) {
            if (full(rooms, inn)) {
                break;
            }
            if (role == null || role.isBlank() || !inn.wanted(role)) {
                continue;
            }
            if (inn.arrive(role) == Arrival.NOT_NOW) {
                refused = true;
            }
        }
        if (inn.listed() >= rooms) {
            return Outcome.DONE;
        }
        if (inn.present() >= rooms) {
            // More visitors are standing in the Inn than are on its list: strays that are being cleared away.
            // Wait for that to settle before deciding that a room cannot be filled.
            return Outcome.RETRY;
        }
        return refused ? Outcome.RETRY : Outcome.DONE;
    }

    /**
     * One visit to one Inn that is ready to receive guests. Safe to call as often as wanted.
     *
     * @return true when the town's saved Inn days changed
     */
    public static boolean attend(
        @Nonnull TownRecord town,
        long innDay,
        boolean morningWindow,
        int rooms,
        @Nonnull Runnable sendFreeGuestsHome,
        @Nonnull Runnable tidyGuestList,
        @Nonnull Rooms inn
    ) {
        boolean changed = false;
        if (refreshDue(town.getInnPoolLastMorningEpochDay(), innDay, morningWindow)) {
            sendFreeGuestsHome.run();
            town.setInnPoolLastMorningEpochDay(innDay);
            town.setInnPoolLastMorningGameDate(LocalDate.ofEpochDay(innDay).toString());
            changed = true;
        }
        tidyGuestList.run();
        if (fillOwed(town.getInnPoolLastMorningEpochDay(), town.getInnPoolLastFillEpochDay(), innDay)
            && fill(rooms, inn) == Outcome.DONE) {
            town.setInnPoolLastFillEpochDay(innDay);
            changed = true;
        }
        return changed;
    }

    /**
     * Game minutes to wait after this many refused fills in a row: 1, 2, 4 and so on up to an hour. The first refusal
     * is tried again on the very next update. A refusal that never clears settles into one cheap attempt an hour
     * instead of a full search of the town every minute.
     */
    public static long retryDelayMinutes(int refusalsInARow) {
        int n = Math.max(1, refusalsInARow);
        return n >= 7 ? 60L : 1L << (n - 1);
    }

    /**
     * A rest after a refused fill, for one town on one Inn day. Kept in memory only: after a restart the fill is
     * simply tried.
     */
    public record Pause(long innDay, long fromMinute, long untilMinute, int refusals) {
        /** False once the wait is over, on a new Inn day, or when the clock was turned back. */
        public boolean holds(long currentInnDay, long minute) {
            return innDay == currentInnDay && minute >= fromMinute && minute < untilMinute;
        }

        @Nonnull
        public static Pause after(@Nullable Pause previous, long innDay, long minute) {
            int n = previous != null && previous.innDay == innDay ? previous.refusals + 1 : 1;
            return new Pause(innDay, minute, minute + retryDelayMinutes(n), n);
        }
    }

    /** Runs {@code work} for every item. One failure is reported and does not stop the others. */
    public static <T> void each(
        @Nonnull Iterable<T> items,
        @Nonnull Consumer<T> work,
        @Nonnull BiConsumer<T, RuntimeException> failed
    ) {
        for (T item : items) {
            try {
                work.accept(item);
            } catch (RuntimeException e) {
                failed.accept(item, e);
            }
        }
    }
}
