package com.hexvane.aetherhaven.leveling;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

interface LevelingBridge {
    String id();
    int playerLevel(PlayerRef player, Store<EntityStore> store) throws ReflectiveOperationException;
    double levelXpCost(PlayerRef player, Store<EntityStore> store) throws ReflectiveOperationException;
    boolean grantQuestXp(PlayerRef player, double amount) throws ReflectiveOperationException;
    /** Runs on the entity's world thread, outside ECS processing. */
    boolean apply(Ref<EntityStore> ref, Store<EntityStore> store, int level) throws ReflectiveOperationException;
    default void removed(Ref<EntityStore> ref, Store<EntityStore> store) throws ReflectiveOperationException {}
}
