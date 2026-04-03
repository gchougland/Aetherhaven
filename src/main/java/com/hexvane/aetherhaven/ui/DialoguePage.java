package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.dialogue.DialogueActionExecutor;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueConditionEvaluator;
import com.hexvane.aetherhaven.dialogue.DialogueWorldView;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueNodeDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import com.hexvane.aetherhaven.npc.NpcDialogueCleanup;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterPage;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Custom dialogue UI: full node text and choices in one build (no progressive reveal). */
public final class DialoguePage extends InteractiveCustomUIPage<DialoguePage.DialogueEventData> {
    private static final String DIALOGUE_PANEL = "#DialoguePanel";
    private static final String DIALOGUE_COLUMN = DIALOGUE_PANEL + " #DialogueFill #DialogueColumn";
    /** Full path so command targets match the appended layout tree. */
    private static final String PORTRAIT_ASSET = DIALOGUE_COLUMN + " #SpeakerFrame #Portrait.AssetPath";
    private static final String SPEAKER_SPANS = DIALOGUE_COLUMN + " #SpeakerFrame #Speaker.TextSpans";
    private static final String BODY_TEXT_SPANS = DIALOGUE_COLUMN + " #TextBlock #BodyText.TextSpans";
    private static final String CHOICES_FRAME = DIALOGUE_COLUMN + " #ChoicesFrame";
    /** Inner list inside {@link #CHOICES_FRAME} scroll; append/clear/indexed selectors need full path. */
    private static final String CHOICES_ROOT = DIALOGUE_COLUMN + " #ChoicesFrame #ChoicesScroll #ChoicesRoot";

    @Nonnull
    private static String choiceRowSelector(int slot) {
        return CHOICES_ROOT + "[" + slot + "]";
    }

    private final DialogueCatalog catalog;
    private final DialogueConditionEvaluator conditions;
    private final DialogueActionExecutor actions = new DialogueActionExecutor();

    private final String treeId;
    @Nullable
    private final Ref<EntityStore> npcRef;

    @Nullable
    private DialogueTreeDefinition tree;
    @Nonnull
    private String nodeId;

    /** Prevents re-running node-enter actions on rebuild. */
    @Nullable
    private String nodeEnterAppliedForNodeId;

    public DialoguePage(
        @Nonnull PlayerRef playerRef,
        @Nonnull DialogueCatalog catalog,
        @Nonnull DialogueWorldView worldView,
        @Nonnull String treeId,
        @Nonnull String entryNodeId,
        @Nullable Ref<EntityStore> npcRef
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, DialogueEventData.CODEC);
        this.catalog = catalog;
        this.conditions = new DialogueConditionEvaluator(worldView);
        this.treeId = treeId;
        this.npcRef = npcRef;
        this.tree = catalog.get(treeId);
        this.nodeId = entryNodeId;
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        NpcDialogueCleanup.scheduleReturnToIdle(npcRef, store);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/DialoguePage.ui");
        if (tree == null) {
            tree = catalog.get(treeId);
        }
        if (tree == null) {
            applyPortrait(commandBuilder, store);
            commandBuilder.set(SPEAKER_SPANS, Message.raw("Dialogue"));
            commandBuilder.set(BODY_TEXT_SPANS, Message.raw("Unknown dialogue: " + treeId));
            setChoicesFrameVisible(commandBuilder, false);
            return;
        }

        DialogueNodeDefinition node = tree.getNode(nodeId);
        if (node == null) {
            applyPortrait(commandBuilder, store);
            commandBuilder.set(SPEAKER_SPANS, Message.raw("Dialogue"));
            commandBuilder.set(BODY_TEXT_SPANS, Message.raw("Missing node: " + nodeId));
            setChoicesFrameVisible(commandBuilder, false);
            return;
        }

        if (!java.util.Objects.equals(nodeEnterAppliedForNodeId, nodeId)) {
            for (int guard = 0; guard < 32; guard++) {
                DialogueNodeDefinition enterNode = tree.getNode(nodeId);
                if (enterNode == null) {
                    break;
                }
                DialogueActionBatchResult nodeEnter = new DialogueActionBatchResult();
                actions.runBatch(enterNode.getActions(), ref, store, nodeEnter, npcRef);
                String enterGoto = nodeEnter.getGotoNodeId();
                if (enterGoto != null && !enterGoto.isBlank()) {
                    nodeId = enterGoto.trim();
                } else {
                    break;
                }
            }
            nodeEnterAppliedForNodeId = nodeId;
        }

        node = tree.getNode(nodeId);
        if (node == null) {
            applyPortrait(commandBuilder, store);
            commandBuilder.set(BODY_TEXT_SPANS, Message.raw("Missing node: " + nodeId));
            setChoicesFrameVisible(commandBuilder, false);
            return;
        }

        applyPortrait(commandBuilder, store);
        String speaker = node.getSpeaker() != null ? node.getSpeaker() : "";
        String body = node.getText() != null ? node.getText() : "";

        commandBuilder.set(SPEAKER_SPANS, Message.raw(speaker));
        commandBuilder.set(BODY_TEXT_SPANS, Message.raw(body));
        setChoicesFrameVisible(commandBuilder, true);
        appendChoices(ref, store, commandBuilder, eventBuilder, node);
    }

    private static void setChoicesFrameVisible(@Nonnull UICommandBuilder cmd, boolean visible) {
        cmd.set(CHOICES_FRAME + ".Visible", visible);
    }

    private void applyPortrait(@Nonnull UICommandBuilder commandBuilder, @Nonnull Store<EntityStore> store) {
        if (npcRef == null || !npcRef.isValid()) {
            commandBuilder.set(PORTRAIT_ASSET, NpcPortraitProvider.portraitPathForRoleId(""));
            return;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            commandBuilder.set(PORTRAIT_ASSET, NpcPortraitProvider.portraitPathForRoleId(""));
            return;
        }
        String roleName = npc.getRoleName();
        commandBuilder.set(PORTRAIT_ASSET, NpcPortraitProvider.portraitPathForRoleId(roleName != null ? roleName : ""));
    }

    private void appendChoices(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull DialogueNodeDefinition node
    ) {
        commandBuilder.clear(CHOICES_ROOT);
        int uiSlot = 0;
        for (int i = 0; i < node.getChoices().size(); i++) {
            DialogueChoiceDefinition ch = node.getChoices().get(i);
            boolean ok = conditions.evaluate(ch.getCondition(), ref, store, npcRef);
            String wf = ch.whenFalseOrDefault();
            if (!ok && "hide".equalsIgnoreCase(wf)) {
                continue;
            }
            boolean disabled = !ok && "disabled".equalsIgnoreCase(wf);
            String text = ch.getText() != null ? ch.getText() : "";
            if (disabled && ch.getDisabledReason() != null && !ch.getDisabledReason().isBlank()) {
                text = text + "  " + ch.getDisabledReason();
            }
            commandBuilder.append(CHOICES_ROOT, "Aetherhaven/DialogueChoiceRow.ui");
            String sel = choiceRowSelector(uiSlot);
            commandBuilder.set(sel + " #Text.TextSpans", Message.raw(text));
            commandBuilder.set(sel + ".Disabled", disabled);
            commandBuilder.set(sel + " #Text.Style.TextColor", disabled ? "#6d6658" : "#f0e6d2");
            if (!disabled) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel,
                    new EventData().append("Action", "Choice").append("ChoiceIndex", String.valueOf(i)),
                    false
                );
            }
            uiSlot++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull DialogueEventData data) {
        if (data.action == null || !data.action.equalsIgnoreCase("Choice") || data.choiceIndex == null) {
            return;
        }
        int choiceIndex;
        try {
            choiceIndex = Integer.parseInt(data.choiceIndex.trim());
        } catch (NumberFormatException e) {
            return;
        }
        DialogueNodeDefinition node = tree != null ? tree.getNode(nodeId) : null;
        if (node == null || choiceIndex < 0 || choiceIndex >= node.getChoices().size()) {
            return;
        }
        DialogueChoiceDefinition choice = node.getChoices().get(choiceIndex);
        if (!conditions.evaluate(choice.getCondition(), ref, store, npcRef)) {
            String wf = choice.whenFalseOrDefault();
            if ("disabled".equalsIgnoreCase(wf)) {
                return;
            }
        }
        DialogueActionBatchResult batch = new DialogueActionBatchResult();
        actions.runBatch(choice.getActions(), ref, store, batch, npcRef);
        applyBatchNavigation(ref, store, batch, choice.getNext());
    }

    private void applyBatchNavigation(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueActionBatchResult batch,
        @Nullable String choiceNext
    ) {
        World world = store.getExternalData().getWorld();
        String gotoId = batch.getGotoNodeId();
        if (gotoId != null && !gotoId.isBlank()) {
            nodeId = gotoId.trim();
            if (batch.isCloseDialogue() || batch.getOpenBarterShopAfterClose() != null) {
                finishClose(ref, store, world, batch);
                return;
            }
            rebuild();
            return;
        }
        if (batch.isCloseDialogue() || batch.getOpenBarterShopAfterClose() != null) {
            finishClose(ref, store, world, batch);
            return;
        }
        String next = choiceNext;
        if (next == null || next.isBlank()) {
            close();
            return;
        }
        nodeId = next.trim();
        rebuild();
    }

    private void finishClose(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable World world,
        @Nonnull DialogueActionBatchResult batch
    ) {
        String shop = batch.getOpenBarterShopAfterClose();
        if (world != null && shop != null && !shop.isBlank()) {
            String sid = shop.trim();
            world.execute(() -> {
                Ref<EntityStore> pref = playerRef.getReference();
                if (pref == null || !pref.isValid()) {
                    return;
                }
                Store<EntityStore> st = pref.getStore();
                Player player = st.getComponent(pref, Player.getComponentType());
                PlayerRef pr = st.getComponent(pref, PlayerRef.getComponentType());
                if (player != null && pr != null) {
                    // Do not call close() before openCustomPage: setPage(None) increments
                    // PageManager's custom-page ack counter, and openCustomPage increments again,
                    // leaving Data events (trade clicks) ignored until multiple client ACKs arrive.
                    player.getPageManager().openCustomPage(pref, st, new BarterPage(pr, sid));
                }
            });
        } else {
            close();
        }
    }

    public static final class DialogueEventData {
        public static final BuilderCodec<DialogueEventData> CODEC = BuilderCodec.builder(DialogueEventData.class, DialogueEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("ChoiceIndex", Codec.STRING), (d, v) -> d.choiceIndex = v, d -> d.choiceIndex)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String choiceIndex;
    }
}
