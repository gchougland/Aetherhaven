package com.hexvane.aetherhaven.questboard;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The one rule for starting a raid: it either starts, with every raider that exists counted by the raid, or it does
 * not start and no raider is left in the world. There is nothing in between.
 */
final class RaidWaveStart {
    private RaidWaveStart() {}

    /** Puts raiders into the world and takes them out again. */
    interface Spawner {
        /**
         * Places one raider.
         *
         * @return its id, or null when it could not be placed. Null must mean the raid cannot count this raider.
         */
        @Nullable
        String spawn(int index, @Nonnull String roleId, @Nonnull RaidSpawnPositionFinder.Result spot);

        void remove(@Nonnull String raiderId);
    }

    /**
     * @param started true when the raid is running and {@code raiders} are in the world
     * @param raiders the raiders the raid counts. Empty whenever the raid did not start
     * @param error what stopped the start part way, if anything did
     */
    record Outcome(boolean started, @Nonnull List<String> raiders, @Nullable RuntimeException error) {}

    /**
     * Places the whole roster on the planned spots.
     *
     * <p>Nothing is placed without a spot for every raider. If placing stops part way with an error, every raider
     * already placed is taken out again before this returns.
     */
    @Nonnull
    static Outcome run(
        @Nonnull List<String> roster,
        @Nonnull List<RaidSpawnPositionFinder.Result> plan,
        @Nonnull Spawner spawner
    ) {
        if (roster.isEmpty() || plan.size() < roster.size()) {
            return new Outcome(false, List.of(), null);
        }
        List<String> raiders = new ArrayList<>(roster.size());
        try {
            for (int i = 0; i < roster.size(); i++) {
                String id = spawner.spawn(i, roster.get(i), plan.get(i));
                if (id != null && !id.isBlank()) {
                    raiders.add(id);
                }
            }
        } catch (RuntimeException e) {
            for (String id : raiders) {
                try {
                    spawner.remove(id);
                } catch (RuntimeException ignored) {
                    // keep taking the others out
                }
            }
            return new Outcome(false, List.of(), e);
        }
        if (raiders.isEmpty()) {
            return new Outcome(false, List.of(), null);
        }
        return new Outcome(true, List.copyOf(raiders), null);
    }

    /**
     * Whether a raider in the world belongs to no running raid and must be taken out. This is what finishes the job
     * for a raider that was asleep in stored terrain when its raid failed to start, was abandoned or ended.
     *
     * @param raidRunning the raid this raider was made for is accepted
     * @param counted the running raid lists this raider
     * @param dying the raider is already dead and playing out its death
     */
    static boolean isLeftover(boolean raidRunning, boolean counted, boolean dying) {
        return !dying && !(raidRunning && counted);
    }
}
