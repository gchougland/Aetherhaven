package com.hexvane.aetherhaven.leveling;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Method;
import java.util.UUID;

/** Public per-entity hooks verified against RPG Leveling 0.3.13 (not its global role overrides). */
final class RpgLevelingBridge implements LevelingBridge {
    private final Object plugin, api;
    private final Method playerInfo, infoLevel, putLevel, removeLevel, multiplier, healthModifier,
        setCachedLevel, worldDisabled, blacklisted, config;
    private final ComponentType<EntityStore, Component<EntityStore>> mobType;
    private final java.lang.reflect.Constructor<?> mobConstructor;
    private final Method xpCost, maxLevel, grantXp;
    private final Object questSource;

    @SuppressWarnings("unchecked")
    RpgLevelingBridge(Object plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        var cls = plugin.getClass();
        var loader = cls.getClassLoader();
        api = cls.getMethod("getAPI").invoke(plugin);
        playerInfo = api.getClass().getMethod("getPlayerLevelInfo", PlayerRef.class, Store.class);
        infoLevel = playerInfo.getReturnType().getMethod("getLevel");
        xpCost = playerInfo.getReturnType().getMethod("getXpNeededForNext");
        maxLevel = playerInfo.getReturnType().getMethod("isMaxLevel");
        var sourceClass = loader.loadClass("org.zuxaw.plugin.api.XPSource");
        questSource = sourceClass.getMethod("create", String.class).invoke(null, "AetherhavenQuest");
        grantXp = api.getClass().getMethod("addXP", PlayerRef.class, double.class, sourceClass);
        putLevel = cls.getMethod("putSpawnLevelForEntity", UUID.class, int.class);
        removeLevel = cls.getMethod("removeSpawnLevelForEntity", UUID.class);
        multiplier = cls.getMethod("calculateMonsterHpMultiplier", int.class);
        healthModifier = cls.getMethod("applyHealthModifier", EntityStatMap.class, float.class);
        mobType = (ComponentType<EntityStore, Component<EntityStore>>) cls.getMethod("getMobLevelDataType").invoke(plugin);
        var mobClass = loader.loadClass("org.zuxaw.plugin.components.MobLevelData");
        mobConstructor = mobClass.getConstructor();
        setCachedLevel = mobClass.getMethod("setCachedLevel", int.class);
        worldDisabled = loader.loadClass("org.zuxaw.plugin.config.InstanceLevelConfig")
            .getMethod("isRPGLevelingDisabled", Store.class);
        config = cls.getMethod("getLevelingConfig");
        blacklisted = loader.loadClass("org.zuxaw.plugin.utils.MobLevelHelper")
            .getMethod("isEntityRoleLevelingBlacklisted", Store.class, Ref.class, config.getReturnType());
        if (mobType == null) throw new IllegalStateException("RPG mob component is not registered");
    }
    public String id() { return "RPG_LEVELING"; }
    public double levelXpCost(PlayerRef player, Store<EntityStore> store) throws ReflectiveOperationException {
        Object info = playerInfo.invoke(api, player, store);
        return info == null || (boolean) maxLevel.invoke(info) ? 0 : ((Number) xpCost.invoke(info)).doubleValue();
    }
    public boolean grantQuestXp(PlayerRef player, double amount) throws ReflectiveOperationException {
        return (boolean) grantXp.invoke(api, player, amount, questSource);
    }
    public int playerLevel(PlayerRef player, Store<EntityStore> store) throws ReflectiveOperationException {
        Object info = playerInfo.invoke(api, player, store);
        return info == null ? 0 : ((Number) infoLevel.invoke(info)).intValue();
    }
    @SuppressWarnings("unchecked")
    public boolean apply(Ref<EntityStore> ref, Store<EntityStore> store, int level) throws ReflectiveOperationException {
        if ((boolean) worldDisabled.invoke(null, store)
            || (boolean) blacklisted.invoke(null, store, ref, config.invoke(plugin))) return false;
        var uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        var stats = store.getComponent(ref, EntityStatMap.getComponentType());
        int health = DefaultEntityStatTypes.getHealth();
        if (uuid == null || stats == null || stats.get(health) == null) return false;
        if (!Float.isFinite(stats.get(health).get()) || !Float.isFinite(stats.get(health).getMax())
            || stats.get(health).getMax() <= 0) return false;
        float fraction = LevelingIntegration.healthFraction(stats.get(health).get(), stats.get(health).getMax());
        putLevel.invoke(plugin, uuid.getUuid(), level);
        // Populate the native cache for nameplates. The UUID override also controls combat/XP lookups.
        var data = store.getComponent(ref, mobType);
        if (data == null) {
            data = (Component<EntityStore>) mobConstructor.newInstance();
            setCachedLevel.invoke(data, level);
            store.putComponent(ref, mobType, data);
        } else {
            setCachedLevel.invoke(data, level);
        }
        // Replaces RPG's own named modifier; never stacks an extra Aetherhaven multiplier.
        healthModifier.invoke(plugin, stats, ((Number) multiplier.invoke(plugin, level)).floatValue());
        stats.setStatValue(health, stats.get(health).getMax() * fraction);
        return true;
    }
    public void removed(Ref<EntityStore> ref, Store<EntityStore> store) throws ReflectiveOperationException {
        var uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid != null) removeLevel.invoke(plugin, uuid.getUuid());
    }
}
