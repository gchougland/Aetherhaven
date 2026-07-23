package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.RaidHealthBarHud;
import com.hexvane.aetherhaven.ui.RaidHealthBarHudSupport;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shows a top-center raid health bar while the player's town has an active raid quest. */
public final class RaidHealthBarHudRefreshSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final AetherhavenPlugin plugin;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public RaidHealthBarHudRefreshSystem(@Nonnull AetherhavenPlugin plugin) {
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
        return Query.and(Player.getComponentType(), PlayerRef.getComponentType(), UUIDComponent.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Player player = chunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        UUIDComponent uuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (player == null || playerRef == null || uuidComponent == null) {
            return;
        }

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        TownRecord town = TownPlayerResolution.resolveActiveTown(store.getExternalData().getWorld(), store, entityRef, tm);
        QuestBoardSlotRecord activeRaid = town != null ? findOngoingRaid(town, uuidComponent.getUuid()) : null;

        if (activeRaid == null) {
            if (RaidHealthBarHudSupport.isActive(player)) {
                RaidHealthBarHudSupport.removeHud(player, playerRef);
            }
            return;
        }

        Message title = QuestBoardService.displayTitle(activeRaid, town, store, plugin.getQuestBoardCatalog());
        RaidHealthBarHud hud = RaidHealthBarHudSupport.obtainHud(player, playerRef);
        hud.refresh(title, activeRaid);
    }

    @Nullable
    static QuestBoardSlotRecord findOngoingRaid(@Nonnull TownRecord town, @Nonnull UUID playerUuid) {
        QuestBoardSlotRecord playerRaid = null;
        QuestBoardSlotRecord townRaid = null;
        String playerId = playerUuid.toString();
        for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
            if (!isOngoingRaid(slot)) {
                continue;
            }
            if (playerId.equals(slot.getAcceptedByPlayerUuid())) {
                playerRaid = slot;
                break;
            }
            if (townRaid == null) {
                townRaid = slot;
            }
        }
        return playerRaid != null ? playerRaid : townRaid;
    }

    static boolean isOngoingRaid(@Nonnull QuestBoardSlotRecord slot) {
        if (!slot.isAccepted() || !slot.isRaidQuest()) {
            return false;
        }
        int need = slot.getRaidKillRequired();
        return need > 0 && slot.getRaidKillProgress() < need;
    }
}
