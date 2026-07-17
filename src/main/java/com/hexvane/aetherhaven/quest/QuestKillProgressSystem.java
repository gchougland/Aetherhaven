package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.questboard.HuntQuestBoardHandler;
import com.hexvane.aetherhaven.map.RaidQuestCompassCache;
import com.hexvane.aetherhaven.questboard.RaidQuestBoardHandler;
import com.hexvane.aetherhaven.questboard.RaidQuestMobBinding;
import com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * When an entity dies, if a player caused the death and their town has an active quest with {@code entity_kills}
 * objectives, increments matching kill counters on the town record.
 */
public final class QuestKillProgressSystem extends DeathSystems.OnDeathSystem {
    @Nonnull
    private final AetherhavenPlugin plugin;

    public QuestKillProgressSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return EntityStatMap.getComponentType();
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull DeathComponent death,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (store.getComponent(victimRef, Player.getComponentType()) != null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);

        RaidQuestMobBinding raidBinding = store.getComponent(victimRef, RaidQuestMobBinding.getComponentType());
        if (raidBinding != null) {
            UUIDComponent victimUuid = store.getComponent(victimRef, UUIDComponent.getComponentType());
            if (victimUuid != null) {
                RaidQuestCompassCache.removeMob(world.getName(), victimUuid.getUuid());
            }
            TownRecord raidTown = tm.getTown(raidBinding.getTownId());
            if (raidTown != null && processBoardRaidKills(victimRef, store, raidTown, victimUuid)) {
                tm.updateTown(raidTown);
            }
        }

        Damage info = death.getDeathInfo();
        if (info == null) {
            return;
        }
        Ref<EntityStore> killerRef = resolveKillerRef(info);
        if (killerRef == null || !killerRef.isValid()) {
            return;
        }
        if (store.getComponent(killerRef, Player.getComponentType()) == null) {
            return;
        }
        UUIDComponent ku = store.getComponent(killerRef, UUIDComponent.getComponentType());
        if (ku == null) {
            return;
        }
        TownRecord town = tm.findTownForPlayerInWorld(ku.getUuid());
        if (town == null) {
            return;
        }
        QuestCatalog cat = plugin.getQuestCatalog();
        boolean changed = false;
        for (String qid : town.getActiveQuestIdsSnapshot()) {
            QuestDefinition def = cat.get(qid);
            if (def == null) {
                continue;
            }
            changed |= QuestProgressionService.reconcile(plugin, town, qid);
            QuestObjective currentObjective = QuestProgressionService.currentObjective(plugin, town, qid);
            for (QuestObjective obj : def.objectivesOrEmpty()) {
                if (obj != currentObjective) {
                    continue;
                }
                if (obj.id() == null || obj.kind() == null) {
                    continue;
                }
                if (!"entity_kills".equalsIgnoreCase(obj.kind().trim())) {
                    continue;
                }
                if (!QuestEntityKillMatcher.matches(victimRef, store, obj)) {
                    continue;
                }
                int need = Math.max(1, obj.killCount());
                int cur = town.getQuestKillCount(qid, obj.id().trim());
                if (cur >= need) {
                    continue;
                }
                int next = cur + 1;
                town.setQuestKillCount(qid, obj.id().trim(), next);
                changed = true;
                sendKillProgressNotification(killerRef, store, next, need, obj);
            }
        }
        changed |= processBoardHuntKills(victimRef, store, town, killerRef);

        if (changed) {
            tm.updateTown(town);
        }
    }

    private static boolean processBoardRaidKills(
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nullable UUIDComponent victimUuid
    ) {
        RaidQuestMobBinding binding = store.getComponent(victimRef, RaidQuestMobBinding.getComponentType());
        if (binding == null || victimUuid == null) {
            return false;
        }
        String victimId = victimUuid.getUuid().toString();
        String instanceId = binding.getBoardInstanceId();
        boolean changed = false;
        for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
            if (!slot.isAccepted() || !slot.isRaidQuest()) {
                continue;
            }
            if (!instanceId.equals(slot.instanceIdOrEmpty())) {
                continue;
            }
            if (!slot.raidSpawnedEntityUuidsOrEmpty().contains(victimId)) {
                continue;
            }
            java.util.List<String> remaining = new java.util.ArrayList<>(slot.raidSpawnedEntityUuidsOrEmpty());
            remaining.remove(victimId);
            slot.setRaidSpawnedEntityUuids(remaining);

            int need = slot.getRaidKillRequired();
            int cur = slot.getRaidKillProgress();
            if (cur < need) {
                slot.setRaidKillProgress(cur + 1);
                changed = true;
                notifyRaidProgress(store, town, slot, cur + 1, need);
            }
            break;
        }
        return changed;
    }

    private static void notifyRaidProgress(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull QuestBoardSlotRecord slot,
        int current,
        int need
    ) {
        String acceptorRaw = slot.getAcceptedByPlayerUuid();
        if (acceptorRaw == null || acceptorRaw.isBlank()) {
            return;
        }
        try {
            Ref<EntityStore> acceptorRef = store.getExternalData().getRefFromUUID(UUID.fromString(acceptorRaw.trim()));
            if (acceptorRef == null || !acceptorRef.isValid()) {
                return;
            }
            PlayerRef pr = store.getComponent(acceptorRef, PlayerRef.getComponentType());
            if (pr == null) {
                return;
            }
            Message label = RaidQuestBoardHandler.raidTargetLabel(slot);
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_quests_portals.aetherhaven.quest.entityKillProgress")
                    .param("current", current)
                    .param("need", need)
                    .param("label", label),
                NotificationStyle.Default
            );
        } catch (IllegalArgumentException ignored) {
            // invalid acceptor uuid
        }
    }

    private static boolean processBoardHuntKills(
        @Nonnull Ref<EntityStore> victimRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Ref<EntityStore> killerRef
    ) {
        boolean changed = false;
        for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
            if (!slot.isAccepted() || !slot.isHuntQuest()) {
                continue;
            }
            if (!QuestEntityKillMatcher.matchesTags(victimRef, store, slot.huntEntityTagsAnyOrEmpty())) {
                continue;
            }
            int need = slot.getHuntKillRequired();
            int cur = slot.getHuntKillProgress();
            if (cur >= need) {
                continue;
            }
            int next = cur + 1;
            slot.setHuntKillProgress(next);
            changed = true;
            sendBoardHuntProgressNotification(killerRef, store, next, need, slot);
        }
        return changed;
    }

    private static void sendBoardHuntProgressNotification(
        @Nonnull Ref<EntityStore> killerRef,
        @Nonnull Store<EntityStore> store,
        int current,
        int need,
        @Nonnull QuestBoardSlotRecord slot
    ) {
        PlayerRef pr = store.getComponent(killerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        Message label = HuntQuestBoardHandler.huntTargetLabel(slot);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_quests_portals.aetherhaven.quest.entityKillProgress")
                .param("current", current)
                .param("need", need)
                .param("label", label),
            NotificationStyle.Default
        );
    }

    private static void sendKillProgressNotification(
        @Nonnull Ref<EntityStore> killerRef,
        @Nonnull Store<EntityStore> store,
        int current,
        int need,
        @Nonnull QuestObjective obj
    ) {
        PlayerRef pr = store.getComponent(killerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        String label = buildKillObjectiveShortLabel(obj);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_quests_portals.aetherhaven.quest.entityKillProgress")
                .param("current", current)
                .param("need", need)
                .param("label", label),
            NotificationStyle.Default
        );
    }

    @Nonnull
    private static String buildKillObjectiveShortLabel(@Nonnull QuestObjective obj) {
        List<String> tags = obj.entityTagsAnyOrEmpty();
        if (!tags.isEmpty()) {
            return String.join(" / ", tags);
        }
        List<String> ids = obj.entityIdsAnyOrEmpty();
        if (!ids.isEmpty()) {
            return String.join(" / ", ids);
        }
        return "Targets";
    }

    @Nullable
    private static Ref<EntityStore> resolveKillerRef(@Nonnull Damage damage) {
        Damage.Source src = damage.getSource();
        if (src instanceof Damage.EntitySource es) {
            return es.getRef();
        }
        return null;
    }
}
