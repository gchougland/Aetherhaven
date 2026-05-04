package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Re-syncs Gaia's Draught stack durability with town charges and keeps the ammo HUD aligned when the flask is held.
 *
 * <p>Vanilla {@code LegacyHotbarChangeStatSystem} clears Ammo when the active slot changes (weapon {@code
 * EntityStatsToClear}), and {@link EntityStatsSystems.Recalculate} reapplies held-item ammo modifiers every tick. We
 * run <strong>after</strong> that recalculation and refresh our draught HUD every tick so dots never briefly stack with
 * a bow/crossbow or stay empty until a slow inventory sync.
 */
public final class GaiaDraughtInventorySyncSystem extends EntityTickingSystem<EntityStore> {
    private static final int SYNC_INTERVAL_TICKS = 40;

    @Nonnull
    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Set.of(new SystemDependency<>(Order.AFTER, EntityStatsSystems.Recalculate.class));

    public GaiaDraughtInventorySyncSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
        EntityStatMap statMap = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            if (town == null) {
                GaiaDraughtAmmoHudSupport.clearAmmoHudModifier(statMap);
            } else {
                GaiaDraughtAmmoHudSupport.syncHeldDraughtAmmoHud(store, playerRef, town, uc.getUuid());
            }
        }

        if ((world.getTick() + index) % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        if (town == null) {
            return;
        }
        GaiaDraughtState st = town.findGaiaDraughtState(uc.getUuid());
        if (st == null || !st.isUnlocked()) {
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return;
        }
        String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
        if (!GaiaDraughtService.playerHasDraughtStack(inv) && st.getCharges() > 0) {
            GaiaDraughtService.ensureDraughtStacksOrGrantFirst(playerRef, store, town, uc.getUuid());
            inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
            if (inv == null) {
                return;
            }
        }
        if (InventoryMaterials.count(inv, id) <= 0 && st.getCharges() <= 0) {
            return;
        }
        if (!draughtStacksMatchTown(inv, id, st)) {
            GaiaDraughtService.syncDraughtStacksInInventory(playerRef, store, st);
        }
    }

    private static boolean draughtStacksMatchTown(
        @Nonnull CombinedItemContainer inv,
        @Nonnull String id,
        @Nonnull GaiaDraughtState st
    ) {
        int expectedCharges = st.getCharges();
        int expectedCap = st.getCapacity();
        int stackCount = 0;
        for (short slot = 0; slot < inv.getCapacity(); slot++) {
            ItemStack s = inv.getItemStack(slot);
            if (s == null || s.isEmpty() || !id.equals(s.getItemId())) {
                continue;
            }
            stackCount++;
            if (s.getQuantity() != 1) {
                return false;
            }
            if (Math.round(s.getDurability()) != expectedCharges || Math.round(s.getMaxDurability()) != expectedCap) {
                return false;
            }
        }
        if (stackCount == 0) {
            return expectedCharges <= 0;
        }
        return stackCount == 1;
    }
}
