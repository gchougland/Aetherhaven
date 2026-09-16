package com.hexvane.aetherhaven.leveling;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatureBootstrap;
import com.hexvane.aetherhaven.questboard.RaidQuestMobBinding;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Optional owner-level scaling. All ECS work stays on the NPC/player's own world thread. */
public final class LevelingIntegration {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, OwnerLevelSnapshot> OWNER_LEVELS = new ConcurrentHashMap<>();
    private static volatile LevelingBridge bridge;
    private static volatile boolean warnedFailure;
    private static boolean started;

    private LevelingIntegration() {}

    public static void register(AetherhavenPlugin plugin) {
        NpcLevelState.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new RefreshSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new CleanupSystem());
    }

    public static void start(AetherhavenPlugin plugin) {
        if (started) return;
        started = true;
        AetherhavenFeatureBootstrap.registerShutdownHook(() -> {
            bridge = null;
            started = false;
            warnedFailure = false;
            OWNER_LEVELS.clear();
        });
        String selected = plugin.getConfig().get().getLevelingIntegration();
        if (selected.equals("NONE")) return;
        var manager = PluginManager.get();
        var endless = manager.getPlugin(new PluginIdentifier("Airijko", "EndlessLevelingCore"));
        var rpg = manager.getPlugin(new PluginIdentifier("Zuxaw", "RPGLeveling"));
        boolean hasEndless = endless != null && endless.isEnabled();
        boolean hasRpg = rpg != null && rpg.isEnabled();
        try {
            if (selected.equals("ENDLESS_LEVELING") || (selected.equals("AUTO") && hasEndless)) {
                if (hasEndless) bridge = new EndlessLevelingBridge(endless);
            } else if (selected.equals("RPG_LEVELING") || (selected.equals("AUTO") && hasRpg)) {
                if (hasRpg) bridge = new RpgLevelingBridge(rpg);
            }
            if (bridge != null) LOGGER.atInfo().log("Town-owner NPC scaling enabled: %s", bridge.id());
            else if (!selected.equals("AUTO")) LOGGER.atWarning().log("Requested leveling integration %s is unavailable", selected);
            if (hasEndless && hasRpg) LOGGER.atWarning().log(
                "Both leveling mods are installed. Aetherhaven uses %s only; configure the mods' own mob scaling to avoid their multipliers stacking.",
                bridge == null ? "neither provider" : bridge.id());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            bridge = null;
            LOGGER.atWarning().withCause(e).log("Optional leveling integration is incompatible; Aetherhaven will continue without owner scaling");
        }
    }

    public static boolean isActive() { return bridge != null; }

    /** Completion callers must first transition out of ACCEPTED/active; never called for kills or objective ticks. */
    public static void rewardCompletion(Ref<EntityStore> playerRef, Store<EntityStore> store, boolean raid) {
        var active = bridge;
        var plugin = AetherhavenPlugin.get();
        if (active == null || plugin == null || playerRef == null || !playerRef.isValid() || playerRef.getStore() != store) return;
        var player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) return;
        double percent = plugin.getConfig().get().getCompletionXpPercent(raid);
        if (percent <= 0) return;
        try {
            double amount = completionXp(active.levelXpCost(player, store), percent);
            if (amount > 0 && !active.grantQuestXp(player, amount))
                LOGGER.atWarning().log("%s rejected completion XP for %s", active.id(), player.getUuid());
        } catch (ReflectiveOperationException | RuntimeException e) { failure(e); }
    }

    public static void rewardCompletion(UUID playerUuid, World world, boolean raid) {
        var store = world.getEntityStore().getStore();
        rewardCompletion(store.getExternalData().getRefFromUUID(playerUuid), store, raid);
    }

    static double completionXp(double levelCost, double percent) {
        if (!Double.isFinite(levelCost) || levelCost <= 0 || !Double.isFinite(percent) || percent <= 0) return 0;
        return Math.max(1, levelCost * (Math.min(percent, 100) / 100));
    }

    /** A missing snapshot is unknown, not level one; never use the raid acceptor/nearest player's level. */
    public static int ownerLevel(TownRecord town, Store<EntityStore> store) {
        LevelingBridge active = bridge;
        UUID owner = town.getOwnerUuid();
        if (active == null || owner == null) return 0;
        World world = store.getExternalData().getWorld();
        for (PlayerRef player : world.getPlayerRefs()) {
            if (owner.equals(player.getUuid())) {
                samplePlayer(active, player, store);
                break;
            }
        }
        var online = OWNER_LEVELS.get(owner);
        int level = online == null ? 0 : online.levelFor(owner, active.id());
        if (level > 0) {
            if (!online.equals(town.getOwnerLevelSnapshot())) {
                town.setOwnerLevelSnapshot(online);
                TownSaveCoordinator.requestSave(AetherhavenWorldRegistries.getOrCreateTownManager(world, AetherhavenPlugin.get()));
            }
            return level;
        }
        var saved = town.getOwnerLevelSnapshot();
        return saved == null ? 0 : saved.levelFor(owner, active.id());
    }

    private static void samplePlayer(LevelingBridge active, PlayerRef player, Store<EntityStore> store) {
        var ref = player.getReference();
        if (ref == null || !ref.isValid() || ref.getStore() != store) return;
        try {
            int level = active.playerLevel(player, store);
            if (level > 0) OWNER_LEVELS.put(player.getUuid(), new OwnerLevelSnapshot(player.getUuid(), active.id(), level));
        } catch (ReflectiveOperationException | RuntimeException e) { failure(e); }
    }

    /** Called immediately after spawn, before combat/normal leveling ticks; freezes the whole raid to one snapshot. */
    public static void scaleRaid(Ref<EntityStore> ref, Store<EntityStore> store, int level) {
        var active = bridge;
        if (active == null || level <= 0) return;
        var state = new NpcLevelState();
        state.target(active.id(), level, true);
        store.putComponent(ref, NpcLevelState.getComponentType(), state);
        apply(active, ref, store, state, level);
    }

    private static void apply(LevelingBridge active, Ref<EntityStore> ref, Store<EntityStore> store, NpcLevelState state, int level) {
        if (level <= 0 || state.appliedLevel == level || !ref.isValid()
            || store.getComponent(ref, DeathComponent.getComponentType()) != null) return;
        try {
            if (active.apply(ref, store, level)) state.appliedLevel = level;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure(e); }
    }

    private static void failure(Throwable e) {
        if (warnedFailure) return;
        warnedFailure = true;
        LOGGER.atWarning().withCause(e).log("Optional NPC leveling hook failed; check installed leveling mod versions");
    }

    static float healthFraction(float current, float maximum) {
        if (!Float.isFinite(current) || !Float.isFinite(maximum) || maximum <= 0) return 0;
        return Math.clamp(current / maximum, 0, 1);
    }

    private static final class RefreshSystem extends TickingSystem<EntityStore> {
        private final AetherhavenPlugin plugin;
        private final Map<World, Long> nextRefresh = Collections.synchronizedMap(new WeakHashMap<>());
        RefreshSystem(AetherhavenPlugin plugin) { this.plugin = plugin; }
        @Override public void tick(float dt, int index, Store<EntityStore> store) {
            var active = bridge;
            if (active == null) return;
            World world = store.getExternalData().getWorld();
            long now = System.nanoTime();
            if (now < nextRefresh.getOrDefault(world, 0L)) return;
            nextRefresh.put(world, now + 10_000_000_000L);
            world.execute(() -> {
                if (!world.isAlive() || store.isShutdown() || bridge != active) return;
                for (var player : world.getPlayerRefs()) samplePlayer(active, player, store);
                var towns = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                Map<UUID, Integer> levels = new HashMap<>();
                for (var town : towns.allTowns()) levels.put(town.getTownId(), ownerLevel(town, store));
                var refs = new ArrayList<Ref<EntityStore>>();
                // Only town NPCs/raid mobs, once per ten seconds; wilderness mobs are not scanned.
                Query<EntityStore> bound = TownVillagerBinding.getComponentType();
                if (com.hexvane.aetherhaven.plugin.AetherhavenFeatures.isLoaded(
                    com.hexvane.aetherhaven.plugin.AetherhavenPluginIds.QUESTS)) {
                    bound = Query.or(bound, RaidQuestMobBinding.getComponentType());
                }
                store.forEachChunk(Query.and(NPCEntity.getComponentType(), bound), (chunk, buffer) -> {
                    for (int i = 0; i < chunk.size(); i++) refs.add(chunk.getReferenceTo(i));
                });
                for (var ref : refs) {
                    if (!ref.isValid()) continue;
                    var resident = store.getComponent(ref, TownVillagerBinding.getComponentType());
                    var raid = raidBinding(store, ref);
                    if (resident == null && raid == null) continue;
                    UUID townId = raid != null ? raid.getTownId() : resident.getTownId();
                    var state = store.getComponent(ref, NpcLevelState.getComponentType());
                    if (state == null) {
                        state = new NpcLevelState();
                        store.putComponent(ref, NpcLevelState.getComponentType(), state);
                    }
                    int target = state.target(active.id(), levels.getOrDefault(townId, 0), raid != null);
                    apply(active, ref, store, state, target);
                }
            });
        }
    }

    private static RaidQuestMobBinding raidBinding(Store<EntityStore> store, Ref<EntityStore> ref) {
        // Quests may be disabled independently of villagers.
        if (!com.hexvane.aetherhaven.plugin.AetherhavenFeatures.isLoaded(
            com.hexvane.aetherhaven.plugin.AetherhavenPluginIds.QUESTS)) return null;
        return store.getComponent(ref, RaidQuestMobBinding.getComponentType());
    }

    private static final class CleanupSystem extends RefSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery() { return NpcLevelState.getComponentType(); }
        @Override public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            var active = bridge;
            if (active == null) return;
            World world = store.getExternalData().getWorld();
            // RPG's UUID override is runtime-only; restore a saved raid before waiting for the periodic refresh.
            world.execute(() -> {
                if (!world.isAlive() || store.isShutdown() || !ref.isValid() || bridge != active) return;
                var state = store.getComponent(ref, NpcLevelState.getComponentType());
                if (state != null && state.raidLevel > 0 && state.provider.equals(active.id()))
                    apply(active, ref, store, state, state.raidLevel);
            });
        }
        @Override public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            var active = bridge;
            if (active == null) return;
            try { active.removed(ref, store); }
            catch (ReflectiveOperationException | RuntimeException e) { failure(e); }
        }
    }
}
