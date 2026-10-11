package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.questboard.HuntQuestBoardHandler;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.hud.AetherhavenHudRefreshSystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Counts ore a player mines toward their town's accepted quest board mining contracts. */
public final class QuestMineProgressSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private final AetherhavenPlugin plugin;

    public QuestMineProgressSystem(@Nonnull AetherhavenPlugin plugin) {
        super(BreakBlockEvent.class);
        this.plugin = plugin;
    }

    /** Pure matching rule, shared with tests. */
    public static boolean matches(@Nullable String blockId, @Nonnull List<String> prefixes) {
        if (blockId == null || blockId.isBlank()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.isBlank() && blockId.startsWith(prefix.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }
        BlockType block = event.getBlockType();
        if (block == null || block == BlockType.EMPTY || block.getId() == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        // Ore conjured and broken in Creative is not mining.
        if (world.getWorldConfig().getGameMode() == GameMode.Creative) {
            return;
        }
        Ref<EntityStore> miner = archetypeChunk.getReferenceTo(index);
        TownManager towns = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, miner, towns);
        if (town == null) {
            return;
        }
        boolean changed = false;
        for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
            if (!slot.isAccepted() || !slot.isMineQuest() || !matches(block.getId(), slot.mineBlockIdPrefixesAnyOrEmpty())) {
                continue;
            }
            int need = slot.getHuntKillRequired();
            int current = slot.getHuntKillProgress();
            if (current >= need) {
                continue;
            }
            slot.setHuntKillProgress(current + 1);
            changed = true;
            PlayerRef player = store.getComponent(miner, PlayerRef.getComponentType());
            if (player != null) {
                NotificationUtil.sendNotification(
                    player.getPacketHandler(),
                    Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.mineProgress")
                        .param("current", current + 1)
                        .param("need", need)
                        .param("label", HuntQuestBoardHandler.huntTargetLabel(slot)),
                    NotificationStyle.Default
                );
            }
        }
        if (changed) {
            towns.updateTown(town);
            AetherhavenHudRefreshSystem.requestRefresh(world);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
