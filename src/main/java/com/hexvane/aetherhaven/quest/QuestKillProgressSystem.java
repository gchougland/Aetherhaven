package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
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
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
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
            for (QuestObjective obj : def.objectivesOrEmpty()) {
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
        if (changed) {
            tm.updateTown(town);
        }
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
            Message.translation("server.aetherhaven.quest.entityKillProgress")
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
