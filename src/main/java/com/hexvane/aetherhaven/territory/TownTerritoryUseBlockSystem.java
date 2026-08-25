package com.hexvane.aetherhaven.territory;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalOpenAccess;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

public final class TownTerritoryUseBlockSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private final AetherhavenPlugin plugin;

    public TownTerritoryUseBlockSystem(@Nonnull AetherhavenPlugin plugin) {
        super(UseBlockEvent.Pre.class);
        this.plugin = plugin;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), UUIDComponent.getComponentType());
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UseBlockEvent.Pre event
    ) {
        if (event.isCancelled()) {
            return;
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        UUID playerUuid = TownTerritoryGuard.resolvePlayerUuid(ref, store);
        if (player == null || playerUuid == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Vector3i pos = event.getTargetBlock();
        TownRecord town = TownTerritoryGuard.findClaimTown(tm, world.getName(), pos);
        if (town == null) {
            return;
        }
        if (!TownTerritoryGuard.isUseProtectionActive(plugin.getConfig().get(), town)) {
            return;
        }
        if (TownTerritoryGuard.shouldBypassPlayer(player, playerUuid, pos, store, ref)) {
            return;
        }
        TownTerritoryGuard.UseKind kind = TownTerritoryGuard.classifyUseBlock(event.getBlockType());
        if (kind == TownTerritoryGuard.UseKind.OTHER
            && FestivalOpenAccess.isInsideRunningFestivalSquare(plugin, town, pos.x, pos.y, pos.z)) {
            return;
        }
        if (!TownTerritoryGuard.playerMayUse(town, playerUuid, kind)) {
            event.setCancelled(true);
        }
    }
}
