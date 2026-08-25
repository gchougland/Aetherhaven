package com.hexvane.aetherhaven.festival;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Collects the prizes a festival hands out so {@link FestivalRewardWindowSystem} can show them in one window instead
 * of a stream of corner toasts. Grants that land in the same moment (a ticket payout plus a cosmetic, say) are merged,
 * and the window waits until the player has no other page open so it never fights a dialogue for the screen.
 */
public final class FestivalRewardQueue {
    /** Grants keep arriving for a few frames, so wait for the batch to settle before opening the window. */
    private static final long SETTLE_MS = 400L;
    /** Give up on a batch nobody ever saw, for example because the player logged out right after winning. */
    private static final long EXPIRY_MS = 120_000L;

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private FestivalRewardQueue() {}

    /** How the window frames the batch above the item list. */
    public enum Outcome {
        NEUTRAL,
        WON,
        LOST,
        /** Handed over in person rather than won, so the window says so. */
        GIFTED
    }

    /** One line in the reward window. */
    public record Entry(@Nonnull String itemId, int amount) {}

    /** A settled batch ready to be shown. */
    public record Payload(@Nonnull List<Entry> entries, @Nonnull Outcome outcome) {}

    public static void queueItem(@Nonnull UUID playerUuid, @Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return;
        }
        String itemId = stack.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        int amount = Math.max(1, stack.getQuantity());
        Pending pending = PENDING.computeIfAbsent(playerUuid, id -> new Pending());
        synchronized (pending) {
            pending.items.merge(itemId.trim(), amount, Integer::sum);
            if (pending.outcome == Outcome.NEUTRAL) {
                pending.outcome = Outcome.WON;
            }
            pending.lastChangeMs = System.currentTimeMillis();
        }
    }

    /**
     * Frames the batch. A losing headline only sticks when there is nothing to show, so an activity that pays out and
     * reports a loss in the same breath still reads as a win; every other outcome is taken at its word.
     */
    public static void queueOutcome(@Nonnull UUID playerUuid, @Nonnull Outcome outcome) {
        Pending pending = PENDING.computeIfAbsent(playerUuid, id -> new Pending());
        synchronized (pending) {
            if (outcome != Outcome.LOST || pending.items.isEmpty()) {
                pending.outcome = outcome;
            }
            pending.lastChangeMs = System.currentTimeMillis();
        }
    }

    @Nonnull
    public static List<UUID> waitingPlayers() {
        return new ArrayList<>(PENDING.keySet());
    }

    /** Removes and returns the batch once it has stopped growing; null while it is still settling. */
    @Nullable
    public static Payload takeIfSettled(@Nonnull UUID playerUuid, long nowMs) {
        Pending pending = PENDING.get(playerUuid);
        if (pending == null || nowMs - lastChange(pending) < SETTLE_MS) {
            return null;
        }
        return take(playerUuid);
    }

    /**
     * Removes and returns whatever is queued right now. Used when a dialogue is closing: everything that dialogue was
     * going to hand over has already been queued, and the window has to replace the dialogue in the same step.
     */
    @Nullable
    public static Payload take(@Nonnull UUID playerUuid) {
        Pending pending = PENDING.remove(playerUuid);
        if (pending == null) {
            return null;
        }
        synchronized (pending) {
            List<Entry> entries = new ArrayList<>(pending.items.size());
            for (Map.Entry<String, Integer> item : pending.items.entrySet()) {
                entries.add(new Entry(item.getKey(), item.getValue()));
            }
            return new Payload(List.copyOf(entries), pending.outcome);
        }
    }

    private static long lastChange(@Nonnull Pending pending) {
        synchronized (pending) {
            return pending.lastChangeMs;
        }
    }

    public static void dropExpired(long nowMs) {
        PENDING.values().removeIf(pending -> {
            synchronized (pending) {
                return nowMs - pending.lastChangeMs >= EXPIRY_MS;
            }
        });
    }

    public static void clear(@Nonnull UUID playerUuid) {
        PENDING.remove(playerUuid);
    }

    private static final class Pending {
        private final Map<String, Integer> items = new LinkedHashMap<>();
        private Outcome outcome = Outcome.NEUTRAL;
        private long lastChangeMs = System.currentTimeMillis();
    }
}
