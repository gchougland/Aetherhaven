package com.hexvane.aetherhaven.mobprobe;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector3d;

/**
 * Spawns creatures the way the game or another plugin would and records what becomes of each one: alive, dead (with
 * the damage that killed it) or removed (with the code that removed it). It uses nothing from Aetherhaven, so the same
 * source builds into the stand-alone control plugin that runs on a server without the mod.
 *
 * <p>The three observing systems are only registered the first time a check runs, and do nothing for a creature the
 * check is not watching.
 */
public final class MobProbe {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, Watch> WATCHED = new ConcurrentHashMap<>();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private MobProbe() {}

    /**
     * One creature to place.
     *
     * @param label where and why, for the report
     * @param role  role id
     * @param how   {@code plain} (the game's own spawn call), {@code hooks} (a plugin spawn that edits the creature
     *              before and after it enters the world), {@code model} (a plugin spawn with its own scaled model),
     *              {@code pet} (what a taming mod's summon does: its own id before the creature enters the world, then
     *              stored health and a rescaled model), {@code reload} (stored and put back, as a capture and release
     *              does), {@code tame:Role} (the game's own taming: a role change), or {@code wild:SpawnRule} (marked
     *              the way the game's own spawner marks a creature it places, with that world spawn rule)
     */
    public record Spawn(String label, String role, String how, Vector3d at, Expect expect) {}

    /** What the check expects of a creature it places or watches. */
    public enum Expect {
        /** Nothing of the town's may strike it, and it must still be in the world, alive, when the watch ends. */
        MUST_LIVE("must live"),
        /** One of the town's creatures may fight it, so alive and dead count the same. It must not vanish or die unfought. */
        MAY_BE_FOUGHT("may be fought");

        final String words;

        Expect(String words) {
            this.words = words;
        }
    }

    public enum Verdict { AS_EXPECTED, WRONG, NOT_JUDGED }

    /**
     * What was seen of one creature, reduced to what the verdict needs.
     *
     * @param placed           it entered the world and was watched
     * @param stored           the game put it away with its land (not a death, not a removal)
     * @param gone             it is no longer in the world and is not dead
     * @param dead             it died during the watch
     * @param killedByCreature the killing blow came from another creature or a player
     * @param struckByCreature some creature landed a blow on it
     * @param struckByTown     one of the town's own creatures landed a blow on it
     * @param struckTownFirst  it landed a blow on one of the town's creatures before any of them touched it
     */
    public record Facts(boolean placed, boolean stored, boolean gone, boolean dead, boolean killedByCreature,
                        boolean struckByCreature, boolean struckByTown, boolean struckTownFirst) {
        /**
         * The whole rule, with nothing of the game in it. A creature that must live fails as soon as one of the town's
         * creatures lands a blow on it, whether or not the kill has finished, so the verdict does not turn on how long the
         * fight takes. A creature that may be fought passes alive or dead, and fails only if it dies with nobody having
         * struck it or is taken out of the world.
         */
        public Judgement judge(Expect expect) {
            Facts facts = this;
            if (!facts.placed()) return new Judgement(Verdict.NOT_JUDGED, "not placed");
            if (expect == Expect.MAY_BE_FOUGHT || facts.struckTownFirst()) {
                if (facts.dead())
                    return facts.struckByCreature() ? new Judgement(Verdict.AS_EXPECTED, "killed in a fight")
                        : new Judgement(Verdict.WRONG, "died with nobody having struck it");
                if (facts.stored()) return new Judgement(Verdict.NOT_JUDGED, "stored by the game");
                if (facts.gone()) return new Judgement(Verdict.WRONG, "taken out of the world");
                return new Judgement(Verdict.AS_EXPECTED, facts.struckByTown() ? "fought, still alive" : "alive");
            }
            if (facts.struckByTown()) return new Judgement(Verdict.WRONG, "struck by the town");
            if (facts.dead())
                return facts.killedByCreature() ? new Judgement(Verdict.NOT_JUDGED, "killed by a creature that is not the town's")
                    : new Judgement(Verdict.WRONG, "died with no creature to blame");
            if (facts.stored()) return new Judgement(Verdict.NOT_JUDGED, "stored by the game");
            if (facts.gone()) return new Judgement(Verdict.WRONG, "taken out of the world");
            return new Judgement(Verdict.AS_EXPECTED, "alive");
        }
    }

    public record Judgement(Verdict verdict, String why) {}

    /**
     * One creature's line and verdict.
     *
     * @param name   where it stood, its role and how it was placed
     * @param fought one of the town's creatures landed a blow on it
     * @param picked it was expected to live and struck the town first, so the town was free to fight back
     */
    public record Result(String line, String name, Expect expect, Verdict verdict, String why, boolean alive, boolean fought, boolean picked) {}

    private static volatile java.util.function.Predicate<String> townRoles = role -> false;

    /**
     * Tells the check which role names are the town's own guards and townsfolk. Left unset on a server without the
     * mod, where no such creature exists.
     */
    public static void townRoles(java.util.function.Predicate<String> roles) {
        townRoles = roles;
    }

    /** What the check saw of one creature. */
    public static final class Watch {
        final Spawn spawn;
        final UUID id;
        final long startedNanos = System.nanoTime();
        final List<String> events = new ArrayList<>();
        volatile String death;
        volatile String removal;
        volatile boolean stored;
        volatile float firstHealth = Float.NaN;
        volatile float firstMax = Float.NaN;
        volatile String failure;
        /** The first of the town's creatures to land a blow on it, and when. */
        volatile String struckByTown;
        /** The first of the town's creatures it landed a blow on, if that came before any of them touched it. */
        volatile String struckTownFirst;
        volatile boolean struckByCreature;
        volatile boolean dead;
        volatile boolean killedByCreature;
        /** The entity slot the game gave it. The game hands a freed slot to the next entity that enters the world. */
        volatile int slot = -1;

        Watch(Spawn spawn, UUID id) {
            this.spawn = spawn;
            this.id = id;
        }

        synchronized void event(String text) {
            if (events.size() < 12) events.add(seconds() + "s " + text);
        }

        String seconds() {
            return String.format(Locale.ROOT, "%.2f", (System.nanoTime() - startedNanos) / 1e9);
        }
    }

    /** Registers the observers once. Safe to call again. */
    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        if (!REGISTERED.compareAndSet(false, true)) return;
        registry.registerSystem(new Removals());
        registry.registerSystem(new Hits());
        registry.registerSystem(new Deaths());
    }

    /**
     * Places every creature on the world thread, watches for the given time, then answers one line per creature and
     * takes the survivors out again. The caller makes sure the ground exists and the land is awake.
     */
    public static CompletableFuture<List<Result>> run(World world, String scenario, List<Spawn> spawns, int seconds) {
        List<Watch> watches = new ArrayList<>();
        return CompletableFuture.runAsync(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            for (Spawn spawn : spawns) watches.add(place(store, spawn));
        }, world).thenCompose(ignored -> later(world, 250, () -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            for (Watch watch : watches) followUp(store, watch);
        })).thenCompose(ignored -> report(world, scenario, watches, seconds, true));
    }

    /**
     * Watches creatures something else has already placed (a raid's attackers) and answers one line for each. They are
     * left in the world for their owner to remove.
     */
    public static CompletableFuture<List<Result>> watch(World world, String scenario, String label, List<UUID> creatures, int seconds,
                                                        Expect expect) {
        List<Watch> watches = new ArrayList<>();
        return CompletableFuture.runAsync(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            for (UUID id : creatures) {
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(id);
                NPCEntity npc = ref == null || !ref.isValid() ? null : store.getComponent(ref, NPCEntity.getComponentType());
                TransformComponent transform = npc == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
                Watch watch = new Watch(new Spawn(label, npc == null ? "?" : npc.getRoleName(), "placed by its owner",
                    transform == null ? new Vector3d() : new Vector3d(transform.getPosition()), expect), id);
                if (npc == null) {
                    watch.failure = "GONE BEFORE THE WATCH BEGAN";
                } else {
                    WATCHED.put(id, watch);
                    watch.slot = ref.getIndex();
                    float[] health = health(store, ref);
                    watch.firstHealth = health[0];
                    watch.firstMax = health[1];
                }
                watches.add(watch);
            }
        }, world).thenCompose(ignored -> report(world, scenario, watches, seconds, false));
    }

    private static CompletableFuture<List<Result>> report(World world, String scenario, List<Watch> watches, int seconds, boolean clear) {
        return later(world, Math.max(1, seconds) * 1000L, () -> { }).thenApplyAsync(ignored -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            List<Result> lines = new ArrayList<>();
            for (Watch watch : watches) {
                String outcome = outcome(store, watch);
                Ref<EntityStore> now = store.getExternalData().getRefFromUUID(watch.id);
                boolean inWorld = now != null && now.isValid();
                boolean dead = watch.dead || outcome.startsWith("DEAD");
                Facts facts = new Facts(watch.failure == null, watch.stored && !inWorld, !inWorld && !dead, dead, watch.killedByCreature,
                    watch.struckByCreature, watch.struckByTown != null, watch.struckTownFirst != null);
                Judgement judgement = facts.judge(watch.spawn.expect());
                String why = judgement.why()
                    + (watch.struckByTown != null ? ": " + watch.struckByTown : "")
                    + (watch.struckTownFirst != null ? " (it struck " + watch.struckTownFirst + " first)" : "");
                String name = watch.spawn.label() + " " + watch.spawn.role() + " (" + watch.spawn.how() + ")";
                String verdict = switch (judgement.verdict()) {
                    case AS_EXPECTED -> "OK";
                    case WRONG -> "WRONG";
                    case NOT_JUDGED -> "NOT JUDGED";
                };
                lines.add(new Result("mobs " + scenario + " | " + watch.spawn.label() + " | " + watch.spawn.role() + " ("
                    + watch.spawn.how() + (watch.slot < 0 ? "" : ", entity slot " + watch.slot) + ") | " + watch.spawn.expect().words
                    + " | " + verdict + ": " + why + " | " + outcome,
                    name, watch.spawn.expect(), judgement.verdict(), why, facts.placed() && !facts.dead() && !facts.gone() && !facts.stored(),
                    watch.struckByTown != null, watch.spawn.expect() == Expect.MUST_LIVE && watch.struckTownFirst != null));
                WATCHED.remove(watch.id);
                if (!clear) continue;
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(watch.id);
                if (ref != null && ref.isValid()) store.removeEntity(ref, RemoveReason.REMOVE);
            }
            return lines;
        }, world);
    }

    private static CompletableFuture<Void> later(World world, long millis, Runnable action) {
        return CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS))
            .thenRunAsync(action, world);
    }

    private static Watch place(Store<EntityStore> store, Spawn spawn) {
        NPCPlugin npcs = NPCPlugin.get();
        int role = npcs.getIndex(spawn.role());
        if (role < 0) {
            Watch missing = new Watch(spawn, UUID.randomUUID());
            missing.failure = "NO SUCH ROLE";
            return missing;
        }
        String how = spawn.how();
        Ref<EntityStore> ref;
        try {
            if (how.equals("hooks")) {
                var pair = npcs.spawnEntity(store, role, spawn.at(), Rotation3f.ZERO, null,
                    (npc, holder, st) -> { },
                    (npc, placed, st) -> {
                        npc.setLeashPoint(new Vector3d(spawn.at()));
                        st.putComponent(placed, NPCEntity.getComponentType(), npc);
                    });
                ref = pair == null ? null : pair.first();
            } else if (how.equals("pet")) {
                UUID own = UUID.randomUUID();
                var pair = npcs.spawnEntity(store, role, spawn.at(), Rotation3f.ZERO, null,
                    (npc, holder, st) -> holder.putComponent(UUIDComponent.getComponentType(), new UUIDComponent(own)),
                    (npc, placed, st) -> {
                        EntityStatMap stats = st.getComponent(placed, EntityStatMap.getComponentType());
                        var health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
                        if (health != null) stats.setStatValue(DefaultEntityStatTypes.getHealth(), health.getMax() * 0.8f);
                        ModelComponent shown = st.getComponent(placed, ModelComponent.getComponentType());
                        ModelAsset asset = shown == null ? null : ModelAsset.getAssetMap().getAsset(shown.getModel().getModelAssetId());
                        if (asset != null) st.putComponent(placed, ModelComponent.getComponentType(),
                            new ModelComponent(Model.createScaledModel(asset, shown.getModel().getScale() * 1.1f)));
                    });
                ref = pair == null ? null : pair.first();
            } else if (how.startsWith("wild:")) {
                int rule = WorldNPCSpawn.getAssetMap().getIndex(how.substring(5));
                if (rule < 0) {
                    Watch unusable = new Watch(spawn, UUID.randomUUID());
                    unusable.failure = "NO SPAWN RULE NAMED " + how.substring(5);
                    return unusable;
                }
                var pair = npcs.spawnEntity(store, role, spawn.at(), Rotation3f.ZERO, null,
                    (npc, holder, st) -> npc.setSpawnConfiguration(rule), null);
                ref = pair == null ? null : pair.first();
            } else if (how.equals("model")) {
                // Every creature this variant is used for has a model named after its role.
                ModelAsset asset = ModelAsset.getAssetMap().getAsset(spawn.role());
                if (asset == null) {
                    Watch unusable = new Watch(spawn, UUID.randomUUID());
                    unusable.failure = "NO MODEL NAMED " + spawn.role();
                    return unusable;
                }
                Model model = Model.createScaledModel(asset, asset.generateRandomScale() * 1.2f);
                var pair = npcs.spawnEntity(store, role, spawn.at(), Rotation3f.ZERO, model, null);
                ref = pair == null ? null : pair.first();
            } else {
                // The call under the game's own spawn command and natural spawning, without the pack it may bring.
                var pair = npcs.spawnEntity(store, role, spawn.at(), Rotation3f.ZERO, null, null);
                ref = pair == null ? null : pair.first();
            }
        } catch (RuntimeException failure) {
            Watch broken = new Watch(spawn, UUID.randomUUID());
            broken.failure = "SPAWN THREW " + failure;
            LOG.atWarning().withCause(failure).log("mobs probe: spawn of %s threw", spawn.role());
            return broken;
        }
        if (ref == null || !ref.isValid()) {
            Watch refused = new Watch(spawn, UUID.randomUUID());
            refused.failure = ref == null ? "NOT SPAWNED (the game returned nothing)"
                : "NOT SPAWNED (the game stored it at once: the land there is asleep)";
            return refused;
        }
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        Watch watch = new Watch(spawn, uuid == null ? UUID.randomUUID() : uuid.getUuid());
        if (uuid == null) {
            watch.failure = "SPAWNED WITHOUT AN ID";
            return watch;
        }
        WATCHED.put(watch.id, watch);
        watch.slot = ref.getIndex();
        float[] health = health(store, ref);
        watch.firstHealth = health[0];
        watch.firstMax = health[1];
        return watch;
    }

    /** The second half of {@code reload} and {@code tame}, one moment after the spawn as a player's action would be. */
    private static void followUp(Store<EntityStore> store, Watch watch) {
        if (watch.failure != null) return;
        String how = watch.spawn.how();
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(watch.id);
        if (ref == null || !ref.isValid()) return;
        try {
            if (how.equals("reload")) {
                var holder = store.removeEntity(ref, RemoveReason.UNLOAD);
                Ref<EntityStore> back = store.addEntity(holder, AddReason.LOAD);
                watch.event("put back by the check: " + (back != null && back.isValid() ? "accepted" : "refused"));
            } else if (how.startsWith("tame:")) {
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                int tamed = NPCPlugin.get().getIndex(how.substring(5));
                if (npc == null || npc.getRole() == null || tamed < 0) {
                    watch.failure = "COULD NOT TAME (no role " + how.substring(5) + ")";
                    return;
                }
                RoleChangeSystem.requestRoleChange(ref, npc.getRole(), tamed, true, store);
                watch.event("role change to " + how.substring(5) + " requested by the check");
            }
        } catch (RuntimeException failure) {
            watch.failure = "FOLLOW UP THREW " + failure;
            LOG.atWarning().withCause(failure).log("mobs probe: follow up for %s threw", watch.spawn.role());
        }
    }

    private static String outcome(Store<EntityStore> store, Watch watch) {
        if (watch.failure != null) return watch.failure;
        String history = watch.events.isEmpty() ? "" : " | seen: " + String.join("; ", watch.events);
        if (watch.death != null) return "DEAD " + watch.death + history;
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(watch.id);
        if (ref == null || !ref.isValid()) {
            if (watch.removal != null) return (watch.stored ? "STORED " : "REMOVED ") + watch.removal + history;
            return "GONE (no removal was seen)" + history;
        }
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) return "DEAD (found dead, death not seen)" + history;
        float[] health = health(store, ref);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        StateSupport state = store.getComponent(ref, StateSupport.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        String where = transform == null ? "?" : String.format(Locale.ROOT, "%.0f, %.0f, %.0f",
            transform.getPosition().x, transform.getPosition().y, transform.getPosition().z);
        return "ALIVE health " + number(health[0]) + " of " + number(health[1]) + " (at spawn " + number(watch.firstHealth) + " of "
            + number(watch.firstMax) + "), role " + (npc == null ? "?" : npc.getRoleName()) + ", state "
            + (state == null ? "?" : state.getStateName()) + ", at " + where + history;
    }

    private static String number(float value) {
        return Float.isNaN(value) ? "?" : String.format(Locale.ROOT, "%.0f", value);
    }

    private static float[] health(Store<EntityStore> store, Ref<EntityStore> ref) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        var value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return value == null ? new float[] {Float.NaN, Float.NaN} : new float[] {value.get(), value.getMax()};
    }

    private static Watch watched(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (WATCHED.isEmpty()) return null;
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuid == null ? null : WATCHED.get(uuid.getUuid());
    }

    /** Who dealt it, in words that mean the same on a server with and without the mod. */
    private static String source(Store<EntityStore> store, Damage damage) {
        Damage.Source source = damage.getSource();
        if (source == Damage.NULL_SOURCE) return "nobody (the game's own health rule)";
        if (source instanceof Damage.EntitySource entity) {
            Ref<EntityStore> by = entity.getRef();
            String who = "an entity that is gone";
            if (by != null && by.isValid()) {
                NPCEntity npc = store.getComponent(by, NPCEntity.getComponentType());
                who = npc == null ? "a player or other entity" : "creature " + npc.getRoleName();
            }
            return (source instanceof Damage.ProjectileSource ? "projectile from " : "") + who;
        }
        if (source instanceof Damage.EnvironmentSource) return "the environment";
        return source.getClass().getSimpleName();
    }

    private static String cause(Damage damage) {
        DamageCause cause = DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        return cause == null ? "unknown" : cause.getId();
    }

    /** The code that asked for the removal, read from the call that is running when the game announces it. */
    static String caller(StackTraceElement[] stack) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (StackTraceElement frame : stack) {
            String type = frame.getClassName();
            if (type.startsWith("java.") || type.startsWith("jdk.") || type.startsWith("com.hypixel.hytale.component.")
                || type.startsWith(MobProbe.class.getName())) continue;
            if (shown++ > 0) out.append(" < ");
            out.append(type.substring(type.lastIndexOf('.') + 1)).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            if (shown == 5) break;
        }
        return out.isEmpty() ? "the game's own tick" : out.toString();
    }

    private static final class Removals extends RefSystem<EntityStore> {
        @Override
        public Query<EntityStore> getQuery() {
            return NPCEntity.getComponentType();
        }

        @Override
        public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commands) {
            Watch watch = watched(store, ref);
            if (watch == null || reason != AddReason.LOAD) return;
            watch.removal = null;
            watch.stored = false;
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            watch.event("back in the world as " + (npc == null ? "?" : npc.getRoleName()));
        }

        @Override
        public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commands) {
            Watch watch = watched(store, ref);
            if (watch == null) return;
            String by = caller(new Throwable().getStackTrace());
            boolean dead = store.getComponent(ref, DeathComponent.getComponentType()) != null;
            if (dead) {
                watch.event("corpse removed");
                return;
            }
            watch.stored = reason == RemoveReason.UNLOAD;
            watch.removal = "after " + watch.seconds() + "s, reason " + reason + ", asked for by " + by;
            LOG.atInfo().log("mobs probe: %s %s left the world, reason %s, asked for by %s", watch.spawn.role(), watch.id, reason, by);
        }
    }

    private static final class Hits extends DamageEventSystem {
        @Override
        public Query<EntityStore> getQuery() {
            return NPCEntity.getComponentType();
        }

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getInspectDamageGroup();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, Damage damage) {
            if (WATCHED.isEmpty()) return;
            UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
            Watch watch = uuid == null ? null : WATCHED.get(uuid.getUuid());
            Ref<EntityStore> by = damage.getSource() instanceof Damage.EntitySource entity ? entity.getRef() : null;
            boolean landed = !damage.isCancelled();
            if (by != null && !by.isValid()) by = null;
            NPCEntity striker = by == null ? null : store.getComponent(by, NPCEntity.getComponentType());
            if (watch != null) {
                watch.event((landed ? "hit " : "blocked hit ") + number(damage.getAmount()) + " " + cause(damage) + " from " + source(store, damage));
                if (landed && by != null) {
                    watch.struckByCreature = true;
                    if (striker != null && townRoles.test(striker.getRoleName()) && watch.struckByTown == null)
                        watch.struckByTown = "struck by " + striker.getRoleName() + " after " + watch.seconds() + "s";
                }
            }
            // The other side of the same blow: a watched creature that hits the town first has picked the fight itself.
            UUIDComponent strikerId = by == null ? null : store.getComponent(by, UUIDComponent.getComponentType());
            Watch attacker = strikerId == null ? null : WATCHED.get(strikerId.getUuid());
            NPCEntity victim = attacker == null ? null : chunk.getComponent(index, NPCEntity.getComponentType());
            if (attacker == null || victim == null || !landed) return;
            attacker.event("it hit " + victim.getRoleName());
            if (townRoles.test(victim.getRoleName()) && attacker.struckByTown == null && attacker.struckTownFirst == null)
                attacker.struckTownFirst = victim.getRoleName() + " after " + attacker.seconds() + "s";
        }
    }

    private static final class Deaths extends DeathSystems.OnDeathSystem {
        @Override
        public Query<EntityStore> getQuery() {
            return NPCEntity.getComponentType();
        }

        @Override
        public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            Watch watch = watched(store, ref);
            if (watch == null) return;
            Damage info = death.getDeathInfo();
            watch.dead = true;
            if (info != null && info.getSource() instanceof Damage.EntitySource killer) {
                watch.killedByCreature = true;
                watch.struckByCreature = true;
                Ref<EntityStore> by = killer.getRef();
                NPCEntity npc = by == null || !by.isValid() ? null : store.getComponent(by, NPCEntity.getComponentType());
                if (npc != null && townRoles.test(npc.getRoleName()) && watch.struckByTown == null)
                    watch.struckByTown = "killed by " + npc.getRoleName() + " after " + watch.seconds() + "s";
            }
            watch.death = "after " + watch.seconds() + "s, " + (info == null ? "no damage record"
                : "killed by " + source(store, info) + ", cause " + cause(info) + ", amount " + number(info.getAmount()))
                + ", announced from " + caller(new Throwable().getStackTrace());
            LOG.atInfo().log("mobs probe: %s %s died: %s", watch.spawn.role(), watch.id, watch.death);
        }
    }
}
