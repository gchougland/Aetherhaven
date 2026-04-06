package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.dialogue.DialogueActionExecutor;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestJournalPage extends InteractiveCustomUIPage<QuestJournalPage.PageData> {
    private static final String ROWS = "#QuestList #QuestRows";
    private static final int MAX_ROWS = 12;

    private boolean templateAppended;
    @Nullable
    private String selectedQuestId;

    public QuestJournalPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/QuestJournal.ui");
            templateAppended = true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("Aetherhaven not loaded."));
            commandBuilder.set("#DetailTitle.TextSpans", Message.raw(""));
            commandBuilder.set("#DetailBody.TextSpans", Message.raw(""));
            commandBuilder.clear(ROWS);
            return;
        }
        var quests = plugin.getQuestCatalog();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("No player id."));
            commandBuilder.set("#DetailTitle.TextSpans", Message.raw(""));
            commandBuilder.set("#DetailBody.TextSpans", Message.raw(""));
            commandBuilder.clear(ROWS);
            return;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForOwnerInWorld(uc.getUuid());
        if (town == null) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("You need a town in this world to track quests."));
            commandBuilder.set("#DetailTitle.TextSpans", Message.raw(""));
            commandBuilder.set("#DetailBody.TextSpans", Message.raw(""));
            commandBuilder.clear(ROWS);
            selectedQuestId = null;
            return;
        }

        List<String> active = new ArrayList<>(town.getActiveQuestIdsSnapshot());
        if (active.isEmpty()) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("No active quests."));
            commandBuilder.set("#DetailTitle.TextSpans", Message.raw(""));
            commandBuilder.set("#DetailBody.TextSpans", Message.raw(""));
            commandBuilder.clear(ROWS);
            selectedQuestId = null;
            return;
        }

        if (selectedQuestId == null || !active.contains(selectedQuestId)) {
            selectedQuestId = active.get(0);
        }

        commandBuilder.set("#Hint.TextSpans", Message.raw("Tap a quest for details. × removes it from your log (you can take it again from the giver)."));
        commandBuilder.clear(ROWS);
        int n = Math.min(active.size(), MAX_ROWS);
        for (int i = 0; i < n; i++) {
            String qid = active.get(i);
            commandBuilder.append(ROWS, "Aetherhaven/QuestJournalRow.ui");
            String row = ROWS + "[" + i + "]";
            commandBuilder.set(row + " #Select #QuestTitle.TextSpans", Message.raw(quests.displayName(qid)));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Select",
                new EventData().append("Action", "Select").append("QuestId", qid),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Abandon",
                new EventData().append("Action", "Abandon").append("QuestId", qid),
                false
            );
        }

        String sel = selectedQuestId != null ? selectedQuestId : active.get(0);
        commandBuilder.set("#DetailTitle.TextSpans", Message.raw(quests.displayName(sel)));
        commandBuilder.set("#DetailBody.TextSpans", Message.raw(quests.detailBody(sel)));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        String action = data.action;
        if (action == null) {
            return;
        }
        if (action.equalsIgnoreCase("Select")) {
            String qid = data.questId;
            if (qid != null && !qid.isBlank()) {
                selectedQuestId = qid.trim();
            }
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (!action.equalsIgnoreCase("Abandon")) {
            return;
        }
        String qid = data.questId;
        if (qid == null || qid.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForOwnerInWorld(uc.getUuid());
        if (town == null) {
            return;
        }
        JsonObject a = new JsonObject();
        a.addProperty("type", "abandon_quest");
        a.addProperty("id", qid.trim());
        DialogueActionExecutor ex = new DialogueActionExecutor();
        DialogueActionBatchResult batch = new DialogueActionBatchResult();
        ex.runBatch(List.of(a), ref, store, batch);
        if (qid.trim().equals(selectedQuestId)) {
            selectedQuestId = null;
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("QuestId", Codec.STRING), (d, v) -> d.questId = v, d -> d.questId)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String questId;
    }
}
