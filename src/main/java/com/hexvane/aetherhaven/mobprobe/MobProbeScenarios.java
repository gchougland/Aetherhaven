package com.hexvane.aetherhaven.mobprobe;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3d;

/**
 * The spread of creatures the check places, the run that needs no town, and the count that ends in a verdict. The
 * stand-alone control plugin can run exactly this on a server without the mod, so the two results can be laid side by
 * side.
 */
public final class MobProbeScenarios {
    /** Base game spawn rules that place a grizzly and a skeleton fighter and never despawn them. */
    public static final String BEAR_RULE = "Spawns_Zone1_Forests_Predator";
    public static final String SKELETON_RULE = "Wander_Zone1_Tier1";

    /** Animals that fight nobody, and the game's own taming (a role change to the tamed role). */
    public static final List<String[]> PEACEFUL = List.of(
        new String[] {"Sheep", "plain"}, new String[] {"Cow", "hooks"}, new String[] {"Chicken", "model"},
        new String[] {"Rabbit", "plain"}, new String[] {"Sheep", "tame:Tamed_Sheep"}, new String[] {"Cow", "tame:Tamed_Cow"},
        new String[] {"Horse", "tame:Tamed_Horse"}, new String[] {"Sheep", "reload"});
    /** Hunters, placed every way another plugin might place a pet, and once marked as the game's own spawner marks them. */
    public static final List<String[]> HUNTERS = List.of(
        new String[] {"Bear_Grizzly", "plain"}, new String[] {"Bear_Grizzly", "hooks"}, new String[] {"Bear_Grizzly", "pet"},
        new String[] {"Bear_Grizzly", "reload"}, new String[] {"Bear_Grizzly", "wild:" + BEAR_RULE}, new String[] {"Wolf_Black", "plain"},
        new String[] {"Fox", "plain"}, new String[] {"Boar", "plain"});
    /** Monsters. */
    public static final List<String[]> MONSTERS = List.of(
        new String[] {"Trork_Warrior", "plain"}, new String[] {"Trork_Warrior", "hooks"}, new String[] {"Skeleton_Fighter", "plain"},
        new String[] {"Skeleton_Fighter", "wild:" + SKELETON_RULE}, new String[] {"Zombie", "plain"}, new String[] {"Goblin_Scrapper", "plain"},
        new String[] {"Spider", "plain"}, new String[] {"Rat", "plain"});

    /** Far enough apart that no creature can see another. */
    private static final int SPACING = 96;

    private MobProbeScenarios() {}

    /**
     * Every creature above at once in a throwaway flat world with no town, by day and then by night. With no town there
     * is nothing that may fight any of them, so every one must live.
     *
     * @param tag     how the report names this run, such as "no mod" or "mod, no town"
     * @param seconds how long the creatures are watched each time
     */
    public static CompletableFuture<List<MobProbe.Result>> flat(String tag, int seconds) {
        return MobProbeWorld.create("mob_probe").thenCompose(world -> {
            List<MobProbe.Result> results = Collections.synchronizedList(new ArrayList<>());
            List<MobProbe.Spawn> spawns = new ArrayList<>();
            List<Vector3d> places = new ArrayList<>();
            int row = 0;
            for (List<String[]> batch : List.of(PEACEFUL, HUNTERS, MONSTERS)) {
                for (int i = 0; i < batch.size(); i++) {
                    Vector3d at = new Vector3d(16.5 + SPACING * (i % 4), MobProbeWorld.SURFACE + 1, 16.5 + SPACING * (row * 2 + i / 4));
                    places.add(at);
                    spawns.add(new MobProbe.Spawn("open ground", batch.get(i)[0], batch.get(i)[1], at, MobProbe.Expect.MUST_LIVE));
                }
                row++;
            }
            CompletableFuture<Void> chain = MobProbeWorld.wake(world, places, 24);
            for (boolean night : new boolean[] {false, true}) {
                chain = chain.thenRunAsync(() -> MobProbeWorld.setTime(world, night), world)
                    .thenCompose(ignored -> MobProbe.run(world, tag + ", " + (night ? "night" : "day"), spawns, seconds))
                    .thenAccept(results::addAll);
            }
            return chain.handle((ignored, failure) -> {
                MobProbeWorld.remove(world);
                if (failure != null) throw new IllegalStateException("the open ground part did not finish: " + failure, failure);
                return (List<MobProbe.Result>) new ArrayList<>(results);
            });
        });
    }

    /**
     * The count for one part of the check.
     *
     * @param wrong     creatures the check expected better for, each with what happened
     * @param notJudged creatures the check could say nothing about, each with why
     */
    public record Tally(String tag, int creatures, int mustLive, int unharmed, int pickedAFight, int mayBeFought, int fought, int killed,
                        List<String> wrong, List<String> notJudged) {
        public boolean passed() {
            return wrong.isEmpty() && creatures > notJudged.size();
        }

        /** One sentence group, in the same words with and without the mod. */
        public String text() {
            StringBuilder out = new StringBuilder(tag).append(": ").append(creatures).append(" creatures. Must live ").append(mustLive)
                .append(": ").append(unharmed).append(" unharmed");
            if (pickedAFight > 0) out.append(", ").append(pickedAFight).append(" struck the town first");
            out.append(". May be fought ").append(mayBeFought).append(": ").append(fought).append(" fought, ").append(killed)
                .append(" killed. Not judged ").append(notJudged.size());
            if (!notJudged.isEmpty()) out.append(" (").append(some(notJudged)).append(")");
            out.append(". Wrong ").append(wrong.size());
            if (!wrong.isEmpty()) out.append(" (").append(some(wrong)).append(")");
            if (creatures == notJudged.size()) out.append(". NOTHING COULD BE JUDGED");
            return out.append('.').toString();
        }

        private static String some(List<String> items) {
            int shown = Math.min(items.size(), 8);
            return String.join("; ", items.subList(0, shown)) + (items.size() > shown ? "; and " + (items.size() - shown) + " more" : "");
        }
    }

    public static Tally tally(String tag, List<MobProbe.Result> results) {
        int mustLive = 0, unharmed = 0, picked = 0, mayBeFought = 0, fought = 0, killed = 0;
        List<String> wrong = new ArrayList<>(), notJudged = new ArrayList<>();
        for (MobProbe.Result result : results) {
            boolean must = result.expect() == MobProbe.Expect.MUST_LIVE;
            if (must) mustLive++;
            else mayBeFought++;
            switch (result.verdict()) {
                case WRONG -> wrong.add(result.name() + ": " + result.why());
                case NOT_JUDGED -> notJudged.add(result.name() + ": " + result.why());
                case AS_EXPECTED -> {
                    if (must && result.picked()) picked++;
                    else if (must) unharmed++;
                    else {
                        if (result.fought()) fought++;
                        if (!result.alive()) killed++;
                    }
                }
            }
        }
        return new Tally(tag, results.size(), mustLive, unharmed, picked, mayBeFought, fought, killed, wrong, notJudged);
    }

    /**
     * The last line of the check. It ends in PASSED only when every part ran, judged something, and found nothing wrong.
     *
     * @param problems parts that did not run to the end, in words
     */
    public static String verdictLine(List<Tally> tallies, List<String> problems, long seconds) {
        boolean passed = problems.isEmpty() && !tallies.isEmpty();
        StringBuilder out = new StringBuilder("Mobs check:");
        for (Tally tally : tallies) {
            out.append(' ').append(tally.text());
            passed &= tally.passed();
        }
        for (String problem : problems) out.append(" The check itself failed: ").append(problem).append('.');
        return out.append(" Took ").append(seconds).append(" seconds. ").append(passed ? "PASSED" : "FAILED").toString();
    }
}
