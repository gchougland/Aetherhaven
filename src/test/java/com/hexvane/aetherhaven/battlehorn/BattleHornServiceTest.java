package com.hexvane.aetherhaven.battlehorn;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerState;
import com.hexvane.aetherhaven.patrol.GuardFollowPlayerState;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class BattleHornServiceTest {
    private static final ComponentRegistry<EntityStore> REGISTRY = new ComponentRegistry<>();

    @BeforeAll
    static void registerComponents() {
        var proxy = new ComponentRegistryProxy<>(new ArrayList<>(), REGISTRY);
        GuardFollowPlayerState.register(proxy);
        VillagerFollowPlayerState.register(proxy);
    }

    @Test
    void selectsEveryGuardAndVillagerForThisPlayerAcrossArchetypes() {
        var store = REGISTRY.addStore(null, EmptyResourceStorage.get());
        try {
            UUID player = UUID.randomUUID();
            UUID otherPlayer = UUID.randomUUID();
            var expected = new HashSet<Ref<EntityStore>>();
            for (int i = 0; i < 300; i++) {
                expected.add(add(store, player, null));
                expected.add(add(store, null, player));
                expected.add(add(store, player, player));
                add(store, otherPlayer, null);
                add(store, null, otherPlayer);
            }
            var selected = BattleHornService.followersOf(store, player);
            assertEquals(expected.size(), selected.size(), "Every follower should appear exactly once");
            assertEquals(expected, new HashSet<>(selected));
            assertEquals(600, BattleHornService.followersOf(store, otherPlayer).size());
        } finally {
            store.shutdown();
        }
    }

    @Test
    void ignoresInactiveFollowersAndHandlesNoFollowers() {
        var store = REGISTRY.addStore(null, EmptyResourceStorage.get());
        try {
            UUID player = UUID.randomUUID();
            assertTrue(BattleHornService.followersOf(store, player).isEmpty());
            var ref = add(store, player, player);
            var guard = new GuardFollowPlayerState();
            guard.startFollowing(player);
            guard.clear();
            store.putComponent(ref, GuardFollowPlayerState.getComponentType(), guard);
            // Still following as a villager until both follow states are cleared.
            assertEquals(java.util.List.of(ref), BattleHornService.followersOf(store, player));
            var villager = new VillagerFollowPlayerState();
            villager.startFollowing(player);
            villager.clear();
            store.putComponent(ref, VillagerFollowPlayerState.getComponentType(), villager);
            assertTrue(BattleHornService.followersOf(store, player).isEmpty());
        } finally {
            store.shutdown();
        }
    }

    private static Ref<EntityStore> add(Store<EntityStore> store, UUID guardOwner, UUID villagerOwner) {
        var holder = REGISTRY.newHolder();
        if (guardOwner != null) {
            var state = new GuardFollowPlayerState();
            state.startFollowing(guardOwner);
            holder.putComponent(GuardFollowPlayerState.getComponentType(), state);
        }
        if (villagerOwner != null) {
            var state = new VillagerFollowPlayerState();
            state.startFollowing(villagerOwner);
            holder.putComponent(VillagerFollowPlayerState.getComponentType(), state);
        }
        return store.addEntity(holder, AddReason.SPAWN);
    }
}
