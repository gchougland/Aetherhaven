package com.hexvane.aetherhaven.leveling;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Optional binary compatibility checks; no third-party binaries are bundled or required in CI. */
@Tag("crossmod")
class LevelingJarContractTest {
    private URLClassLoader loader(String jar) throws Exception {
        var path = Path.of("build", "leveling-research", jar);
        assumeTrue(Files.isRegularFile(path), "Place local mod jars in build/leveling-research to check compatibility");
        return new URLClassLoader(new java.net.URL[]{path.toUri().toURL()}, getClass().getClassLoader());
    }
    @Test void endless123HasTargetedLevelAndRescalingContracts() throws Exception {
        assertEndlessContract("EndlessLeveling.jar");
    }
    @Test void endless125HasTargetedLevelAndRescalingContracts() throws Exception {
        assertEndlessContract("EndlessLeveling-12.5.0.jar");
    }
    private void assertEndlessContract(String jar) throws Exception {
        try (var loader = loader(jar)) {
            var api = loader.loadClass("com.airijko.endlessleveling.api.EndlessLevelingAPI");
            assertEquals(int.class, api.getMethod("getPlayerLevel", UUID.class).getReturnType());
            api.getMethod("getXpForNextLevel", UUID.class);
            api.getMethod("grantXp", UUID.class, double.class);
            api.getMethod("setMobEntityLevelOverride", Ref.class, int.class);
            api.getMethod("clearMobEntityLevelOverride", Ref.class);
            assertEquals(boolean.class, api.getMethod("applyMobScalingNow", Ref.class, Store.class, CommandBuffer.class).getReturnType());
            var system = loader.loadClass("com.airijko.endlessleveling.mob.MobLevelingSystem");
            assertTrue(java.util.Map.class.isAssignableFrom(system.getDeclaredField("entityStates").getType()));
            var identity = system.getDeclaredMethod("resolveTrackingIdentity", Ref.class, CommandBuffer.class);
            assertEquals(long.class, identity.getReturnType().getDeclaredMethod("key").getReturnType());
            var state = loader.loadClass(system.getName() + "$EntityRuntimeState");
            for (String field : new String[]{"appliedLevel", "settledHealthLevel", "lastRecordedResolvedLevel"})
                assertEquals(int.class, state.getDeclaredField(field).getType());
            assertEquals(long.class, state.getDeclaredField("nextHealthApplyAttemptMillis").getType());
            for (String component : new String[]{"MobLevelOverrideComponent", "MobSettledComponent"}) {
                var cls = loader.loadClass("com.airijko.endlessleveling.ecs.components." + component);
                cls.getMethod("getComponentType");
                if (component.equals("MobLevelOverrideComponent")) {
                    cls.getConstructor();
                    cls.getMethod("setLevel", int.class);
                }
            }
        }
    }
    @Test void rpg0313HasOwnerReadAndPerEntityCombatLevelContracts() throws Exception {
        try (var loader = loader("RPGLeveling.jar")) {
            var plugin = loader.loadClass("org.zuxaw.plugin.RPGLevelingPlugin");
            var api = plugin.getMethod("getAPI").getReturnType();
            api.getMethod("getPlayerLevelInfo", PlayerRef.class, Store.class).getReturnType().getMethod("getLevel");
            var info = api.getMethod("getPlayerLevelInfo", PlayerRef.class, Store.class).getReturnType();
            info.getMethod("getXpNeededForNext");
            info.getMethod("isMaxLevel");
            var source = loader.loadClass("org.zuxaw.plugin.api.XPSource");
            source.getMethod("create", String.class);
            assertEquals(boolean.class, api.getMethod("addXP", PlayerRef.class, double.class, source).getReturnType());
            plugin.getMethod("putSpawnLevelForEntity", UUID.class, int.class);
            plugin.getMethod("removeSpawnLevelForEntity", UUID.class);
            plugin.getMethod("getMobLevelDataType");
            assertEquals(float.class, plugin.getMethod("calculateMonsterHpMultiplier", int.class).getReturnType());
            plugin.getMethod("applyHealthModifier", EntityStatMap.class, float.class);
            var config = plugin.getMethod("getLevelingConfig").getReturnType();
            loader.loadClass("org.zuxaw.plugin.utils.MobLevelHelper")
                .getMethod("isEntityRoleLevelingBlacklisted", Store.class, Ref.class, config);
            loader.loadClass("org.zuxaw.plugin.config.InstanceLevelConfig").getMethod("isRPGLevelingDisabled", Store.class);
            var data = loader.loadClass("org.zuxaw.plugin.components.MobLevelData");
            data.getConstructor();
            data.getMethod("setCachedLevel", int.class);
        }
    }
}
