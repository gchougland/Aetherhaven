package com.hexvane.aetherhaven.leveling;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.*;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Isolated bridge for Endless Leveling 12.3.0. All reflective contracts are checked before enabling. */
final class EndlessLevelingBridge implements LevelingBridge {
    private final Object api, system;
    private final Method playerLevel, setOverride, clearOverride, scaleNow, identity, identityKey, setLevel;
    private final Method xpCost, grantXp;
    private final Field states, appliedLevel, settledLevel, recordedLevel, nextHealthAttempt;
    private final Constructor<?> overrideConstructor;
    private final ComponentType<EntityStore, Component<EntityStore>> overrideType, settledType;

    @SuppressWarnings("unchecked")
    EndlessLevelingBridge(Object plugin) throws ReflectiveOperationException {
        var loader = plugin.getClass().getClassLoader();
        var apiClass = loader.loadClass("com.airijko.endlessleveling.api.EndlessLevelingAPI");
        api = apiClass.getMethod("get").invoke(null);
        playerLevel = apiClass.getMethod("getPlayerLevel", UUID.class);
        xpCost = apiClass.getMethod("getXpForNextLevel", UUID.class);
        grantXp = apiClass.getMethod("grantXp", UUID.class, double.class);
        setOverride = apiClass.getMethod("setMobEntityLevelOverride", Ref.class, int.class);
        clearOverride = apiClass.getMethod("clearMobEntityLevelOverride", Ref.class);
        scaleNow = apiClass.getMethod("applyMobScalingNow", Ref.class, Store.class, CommandBuffer.class);
        system = plugin.getClass().getMethod("getMobLevelingSystem").invoke(plugin);
        if (api == null || system == null) throw new IllegalStateException("Endless Leveling is not started");
        var systemClass = system.getClass();
        states = field(systemClass, "entityStates");
        identity = systemClass.getDeclaredMethod("resolveTrackingIdentity", Ref.class, CommandBuffer.class);
        identity.setAccessible(true);
        identityKey = identity.getReturnType().getDeclaredMethod("key");
        identityKey.setAccessible(true);
        var stateClass = loader.loadClass(systemClass.getName() + "$EntityRuntimeState");
        appliedLevel = field(stateClass, "appliedLevel");
        settledLevel = field(stateClass, "settledHealthLevel");
        recordedLevel = field(stateClass, "lastRecordedResolvedLevel");
        nextHealthAttempt = field(stateClass, "nextHealthApplyAttemptMillis");
        var overrideClass = loader.loadClass("com.airijko.endlessleveling.ecs.components.MobLevelOverrideComponent");
        overrideConstructor = overrideClass.getConstructor();
        setLevel = overrideClass.getMethod("setLevel", int.class);
        overrideType = (ComponentType<EntityStore, Component<EntityStore>>) overrideClass.getMethod("getComponentType").invoke(null);
        settledType = (ComponentType<EntityStore, Component<EntityStore>>) loader
            .loadClass("com.airijko.endlessleveling.ecs.components.MobSettledComponent").getMethod("getComponentType").invoke(null);
        if (overrideType == null || settledType == null) throw new IllegalStateException("Endless mob components are not registered");
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    public String id() { return "ENDLESS_LEVELING"; }
    public double levelXpCost(PlayerRef player, Store<EntityStore> store) throws ReflectiveOperationException {
        return ((Number) xpCost.invoke(api, player.getUuid())).doubleValue();
    }
    public boolean grantQuestXp(PlayerRef player, double amount) throws ReflectiveOperationException {
        grantXp.invoke(api, player.getUuid(), amount);
        return true;
    }
    public int playerLevel(PlayerRef player, Store<EntityStore> store) throws ReflectiveOperationException {
        return ((Number) playerLevel.invoke(api, player.getUuid())).intValue();
    }
    @SuppressWarnings("unchecked")
    public boolean apply(Ref<EntityStore> ref, Store<EntityStore> store, int level) throws ReflectiveOperationException {
        var stats = store.getComponent(ref, EntityStatMap.getComponentType());
        int health = DefaultEntityStatTypes.getHealth();
        if (stats == null || stats.get(health) == null) return false;
        if (!Float.isFinite(stats.get(health).get()) || !Float.isFinite(stats.get(health).getMax())
            || stats.get(health).getMax() <= 0) return false;
        float fraction = LevelingIntegration.healthFraction(stats.get(health).get(), stats.get(health).getMax());
        var override = (Component<EntityStore>) overrideConstructor.newInstance();
        setLevel.invoke(override, level);
        store.putComponent(ref, overrideType, override);
        setOverride.invoke(api, ref, level);
        store.tryRemoveComponent(ref, settledType);
        ReflectiveOperationException[] error = {null};
        boolean[] result = {false};
        store.forEachChunk(Query.any(), (BiPredicate<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>>) (chunk, buffer) -> {
            try {
                Object key = identityKey.invoke(identity.invoke(system, ref, buffer));
                Object state = ((Map<?, ?>) states.get(system)).get(key);
                if (state != null) {
                    // EL's public override setter does not invalidate an already-settled NPC. Invalidate
                    // just this NPC's level cache, retaining true base HP, ranks, augments and other mobs.
                    appliedLevel.setInt(state, 0);
                    settledLevel.setInt(state, 0);
                    recordedLevel.setInt(state, -1);
                    nextHealthAttempt.setLong(state, 0);
                }
                result[0] = (boolean) scaleNow.invoke(api, ref, store, buffer);
            } catch (ReflectiveOperationException e) { error[0] = e; }
            return false;
        });
        // Native scaling may fill health; an update must retain injuries and must not resurrect an NPC.
        var updatedStats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (updatedStats != null && updatedStats.get(health) != null)
            updatedStats.setStatValue(health, updatedStats.get(health).getMax() * fraction);
        if (error[0] != null) throw error[0];
        return result[0];
    }
    public void removed(Ref<EntityStore> ref, Store<EntityStore> store) throws ReflectiveOperationException {
        clearOverride.invoke(api, ref);
    }
}
