package com.hexvane.aetherhaven.ui;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.dialogue.DialogueActionExecutor;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueConditionEvaluator;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.bard.BardDialogueSongs;
import com.hexvane.aetherhaven.dialogue.DialogueWorldView;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueNodeDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import com.hexvane.aetherhaven.npc.NpcDialogueCleanup;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkGreetingPicker;
import com.hexvane.aetherhaven.villager.VillagerBefriendableResolver;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerGreetingPicker;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterPage;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Custom dialogue UI: full node text and choices in one build (no progressive reveal). */
public final class DialoguePage extends AetherhavenInteractiveCustomUIPage<DialoguePage.DialogueEventData> {
    private static final String GREETING_FALLBACK_LANG =
        "aetherhaven_dialogue_tourist.aetherhaven.dialogue.aetherhaven_tourist.greeting_fallback";

    private static final String DIALOGUE_PANEL = "#DialoguePanel";
    private static final String DIALOGUE_FILL = DIALOGUE_PANEL + " #DialogueFill";
    private static final String HEADER_ROW = DIALOGUE_FILL + " #HeaderRow";
    private static final String DIALOGUE_LEFT = DIALOGUE_FILL + " #DialogueColumns #DialogueLeftColumn";
    private static final String DIALOGUE_RIGHT = DIALOGUE_FILL + " #DialogueColumns #DialogueRightColumn";
    /** Full path so command targets match the appended layout tree. */
    private static final String PORTRAIT_ASSET = HEADER_ROW + " #PortraitFrame #Portrait.AssetPath";
    private static final String SPEAKER_SPANS = HEADER_ROW + " #SpeakerNameFrame #Speaker.TextSpans";
    private static final String BODY_TEXT_SPANS = DIALOGUE_LEFT + " #TextBlock #BodyText.TextSpans";
    private static final String CHOICES_FRAME = DIALOGUE_RIGHT + " #ChoicesFrame";
    /** Inner list inside {@link #CHOICES_FRAME} scroll; append/clear/indexed selectors need full path. */
    private static final String CHOICES_ROOT = CHOICES_FRAME + " #ChoicesScroll #ChoicesRoot";
    private static final String ICON_QUEST = "UI/Custom/exclamation.png";
    private static final String ICON_QUEST_PROGRESS = "UI/Custom/placeholder_icon_256.png";
    private static final String ICON_EXIT = "UI/Custom/exit.png";
    private static final String ICON_GIFT = "UI/Custom/gift-box.png";
    private static final String ICON_JEWELRY_APPRAISAL = "UI/Custom/Aetherhaven_jewelry_tab_ring.png";
    private static final String ICON_BLACKSMITH_REPAIR = "UI/Custom/hammer.png";
    private static final String ICON_GEODE_OPEN = "UI/Custom/broken-egg.png";
    private static final String ICON_MUSICAL_NOTE = "UI/Custom/musical-note.png";
    private static final String LANG_GUILD_ADVENTURER_HIRE =
        "aetherhaven_dialogue_guild_adventurer.aetherhaven.dialogue.aetherhaven_guild_adventurer.main_hub.hire";
    private static final String LANG_PRIESTESS_DRAUGHT_SHARD =
        "aetherhaven_dialogue_priestess.aetherhaven.dialogue.aetherhaven_priestess.draught_hub.c_shard";
    private static final String LANG_PRIESTESS_DRAUGHT_CATALYST =
        "aetherhaven_dialogue_priestess.aetherhaven.dialogue.aetherhaven_priestess.draught_hub.c_catalyst";
    private static final String REPUTATION_LABEL = HEADER_ROW + " #ReputationBlock #ReputationLabel";
    private static final String HEART_SLOTS = HEADER_ROW + " #ReputationBlock #HeartSlots";

    @Nonnull
    private static String choiceRowSelector(int slot) {
        return CHOICES_ROOT + "[" + slot + "]";
    }

    private static void setReputationVisible(@Nonnull UICommandBuilder cmd, boolean visible) {
        cmd.set(REPUTATION_LABEL + ".Visible", visible);
        cmd.set(HEART_SLOTS + ".Visible", visible);
    }

    private final DialogueCatalog catalog;
    private final DialogueWorldView dialogueWorldView;
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
        this.dialogueWorldView = worldView;
        this.conditions = new DialogueConditionEvaluator(worldView);
        this.treeId = treeId;
        this.npcRef = npcRef;
        this.tree = catalog.get(treeId);
        this.nodeId = entryNodeId;
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
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
        AetherhavenUiLocalization.applyDialoguePage(commandBuilder);
        commandBuilder.clear(HEART_SLOTS);
        for (int h = 0; h < 10; h++) {
            commandBuilder.append(HEART_SLOTS, "Aetherhaven/HeartSlot.ui");
        }
        if (tree == null) {
            tree = catalog.get(treeId);
        }
        if (tree == null) {
            setReputationVisible(commandBuilder, false);
            applyPortrait(commandBuilder, store);
            commandBuilder.set(SPEAKER_SPANS, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.dialogue.title"));
            commandBuilder.set(
                BODY_TEXT_SPANS,
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.dialogue.unknown").param("id", treeId)
            );
            setChoicesFrameVisible(commandBuilder, false);
            return;
        }

        DialogueNodeDefinition node = tree.getNode(nodeId);
        if (node == null) {
            setReputationVisible(commandBuilder, false);
            applyPortrait(commandBuilder, store);
            commandBuilder.set(SPEAKER_SPANS, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.dialogue.title"));
            commandBuilder.set(
                BODY_TEXT_SPANS,
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.dialogue.missingNode").param("id", nodeId)
            );
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
            setReputationVisible(commandBuilder, false);
            applyPortrait(commandBuilder, store);
            commandBuilder.set(
                BODY_TEXT_SPANS,
                Message.translation("aetherhaven_ui_shell.aetherhaven.ui.dialogue.missingNode").param("id", nodeId)
            );
            setChoicesFrameVisible(commandBuilder, false);
            return;
        }

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        boolean showRep =
            npcRef != null
                && npcRef.isValid()
                && plugin != null
                && VillagerBefriendableResolver.isBefriendable(store, npcRef, plugin);
        setReputationVisible(commandBuilder, showRep);
        boolean firstEverTalk = false;
        if (npcRef != null && npcRef.isValid() && plugin != null) {
            World world = store.getExternalData().getWorld();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = VillagerReputationService.findTownForPlayer(ref, store, tm);
            UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
            UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
            if (town != null && pu != null && nu != null) {
                firstEverTalk = VillagerReputationService.isFirstEverTalk(town, pu.getUuid(), nu.getUuid());
                if (showRep) {
                    long day = VillagerReputationService.currentGameEpochDay(store);
                    int dailyGain = VillagerReputationService.applyDailyTalkBonus(world, town, tm, pu.getUuid(), nu.getUuid(), day);
                    if (dailyGain > 0) {
                        NotificationUtil.sendNotification(
                            playerRef.getPacketHandler(),
                            Message.translation("aetherhaven_ui_town.aetherhaven.reputation.dailyTalkGain").param("amount", dailyGain),
                            NotificationStyle.Success
                        );
                    }
                    int rep = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), nu.getUuid()).getReputation();
                    ReputationHeartUi.applyHearts(commandBuilder, HEART_SLOTS, rep);
                    commandBuilder.set(
                        HEART_SLOTS + ".TooltipText",
                        rep + "/" + VillagerReputationService.MAX_REPUTATION
                    );
                }
            }
        }

        applyPortrait(commandBuilder, store);

        Message bodyMsg = resolveDialogueBody(ref, store, node, firstEverTalk);
        if ("main_hub".equals(nodeId) && npcRef != null && npcRef.isValid()) {
            AetherhavenPlugin openerPlugin = AetherhavenPlugin.get();
            if (openerPlugin != null) {
                World hubWorld = store.getExternalData().getWorld();
                TownManager hubTm = AetherhavenWorldRegistries.getOrCreateTownManager(hubWorld, openerPlugin);
                TownRecord hubTown = VillagerReputationService.findTownForPlayer(ref, store, hubTm);
                UUIDComponent hubPu = store.getComponent(ref, UUIDComponent.getComponentType());
                UUIDComponent hubNu = store.getComponent(npcRef, UUIDComponent.getComponentType());
                if (hubTown != null && hubPu != null && hubNu != null) {
                    String openerKey = VillagerReputationService.takeAndClearPendingMainHubBodyLangKey(
                        hubTown,
                        hubTm,
                        hubPu.getUuid(),
                        hubNu.getUuid()
                    );
                    if (openerKey != null && !openerKey.isBlank()) {
                        bodyMsg = dialogueMessage(openerKey).insert(Message.raw("\n\n")).insert(bodyMsg);
                    }
                }
            }
        }

        commandBuilder.set(SPEAKER_SPANS, resolveSpeakerMessage(store, node));
        commandBuilder.set(BODY_TEXT_SPANS, bodyMsg);
        setChoicesFrameVisible(commandBuilder, true);
        appendChoices(ref, store, commandBuilder, eventBuilder, node);
    }

    @Nonnull
    private Message resolveSpeakerMessage(@Nonnull Store<EntityStore> store, @Nonnull DialogueNodeDefinition node) {
        String speaker = node.getSpeaker() != null ? node.getSpeaker() : "";
        if (!speaker.isBlank()) {
            return dialogueMessage(speaker);
        }
        if (npcRef == null || !npcRef.isValid()) {
            return Message.raw("");
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            TownsfolkCharacterBinding binding = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
            if (binding != null) {
                TownsfolkCharacterDefinition character =
                    plugin.getTownsfolkCharacterCatalog().byId(binding.getCharacterId());
                String name = character != null ? character.getDisplayName() : null;
                if (name != null && !name.isBlank()) {
                    return Message.raw(name);
                }
            }
        }
        PersistentDisplayName displayName = store.getComponent(npcRef, PersistentDisplayName.getComponentType());
        if (displayName != null && displayName.getDisplayName() != null) {
            String raw = displayName.getDisplayName().getRawText();
            if (raw != null && !raw.isBlank()) {
                return Message.raw(raw.trim());
            }
        }
        if (plugin != null) {
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            String roleId = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
            if (!roleId.isBlank()) {
                String resolved = TownResidentDisplay.resolveFromEntity(store, npcRef, roleId, plugin).displayName();
                if (resolved != null && !resolved.isBlank()) {
                    return Message.raw(resolved);
                }
            }
        }
        return Message.raw("");
    }

    @Nonnull
    private Message resolveDialogueBody(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueNodeDefinition node,
        boolean firstEverTalk
    ) {
        String mode = node.getBodyMode();
        if (mode != null && "villager_greeting".equalsIgnoreCase(mode.trim())) {
            if (firstEverTalk) {
                String intro = node.getIntroText();
                if (intro != null && !intro.isBlank()) {
                    return dialogueMessage(intro.trim());
                }
            }
            if (npcRef != null && npcRef.isValid()) {
                NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
                UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
                if (npc != null && npc.getRoleName() != null && nu != null && pu != null) {
                    AetherhavenPlugin plugin = AetherhavenPlugin.get();
                    if (plugin != null) {
                        Message townsfolkGreeting = TownsfolkGreetingPicker.pickMessage(store, npcRef, plugin, pu.getUuid(), nu.getUuid());
                        if (townsfolkGreeting != null) {
                            return townsfolkGreeting;
                        }
                        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(npc.getRoleName().trim());
                        if (vdef != null) {
                            long day = VillagerReputationService.currentGameEpochDay(store);
                            Message picked = VillagerGreetingPicker.pickMessage(vdef, pu.getUuid(), nu.getUuid(), day);
                            if (picked != null) {
                                return picked;
                            }
                        }
                    }
                }
            }
            String body = node.getText() != null ? node.getText() : "";
            if (!body.isBlank()) {
                return dialogueMessage(body);
            }
            return Message.translation(GREETING_FALLBACK_LANG);
        }
        String body = node.getText() != null ? node.getText() : "";
        return dialogueMessage(body);
    }

    /**
     * True for Hytale lang keys of the form {@code bundle.key} (any mod bundle).
     * Rejects plain dialogue prose (spaces / no dotted path).
     */
    private static boolean isTranslationKey(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int dot = s.indexOf('.');
        if (dot <= 0 || dot >= s.length() - 1) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                continue;
            }
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-') {
                continue;
            }
            return false;
        }
        return true;
    }

    @Nonnull
    private static Message dialogueMessage(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return Message.raw("");
        }
        if (isTranslationKey(s)) {
            return Message.translation(s);
        }
        return Message.raw(s);
    }

    @Nonnull
    private Message choiceTranslationMessage(
        @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nullable String text
    ) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }
        if (!isTranslationKey(text)) {
            return Message.raw(text);
        }
        Message m = Message.translation(text);
        if (LANG_PRIESTESS_DRAUGHT_SHARD.equals(text)) {
            long gold = dialogueWorldView.nextGaiaDraughtShardUpgradeGoldCost(ref, store, npcRef);
            return m.param("gold", Long.toString(gold));
        }
        if (LANG_PRIESTESS_DRAUGHT_CATALYST.equals(text)) {
            long gold = dialogueWorldView.nextGaiaDraughtCatalystUpgradeGoldCost(ref, store, npcRef);
            return m.param("gold", Long.toString(gold));
        }
        if (LANG_GUILD_ADVENTURER_HIRE.equals(text)) {
            long gold = dialogueWorldView.guardHireGoldCost(ref, store, npcRef);
            String typeKey = dialogueWorldView.guardHireGuardTypeLangKey(ref, store, npcRef);
            return m
                .param("gold", Long.toString(gold))
                .param("type", Message.translation(typeKey));
        }
        return m;
    }

    private static void setChoicesFrameVisible(@Nonnull UICommandBuilder cmd, boolean visible) {
        cmd.set(CHOICES_FRAME + ".Visible", visible);
    }

    private void applyPortrait(@Nonnull UICommandBuilder commandBuilder, @Nonnull Store<EntityStore> store) {
        String portrait = NpcPortraitProvider.portraitPathForRoleId("");
        if (npcRef != null && npcRef.isValid()) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            String roleId = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
            if (plugin != null && !roleId.isBlank()) {
                portrait = TownResidentDisplay.resolveFromEntity(store, npcRef, roleId, plugin).portraitPath();
            } else if (!roleId.isBlank()) {
                portrait = NpcPortraitProvider.portraitPathForRoleId(roleId);
            }
        }
        commandBuilder.set(PORTRAIT_ASSET, portrait);
    }

    private void appendChoices(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull DialogueNodeDefinition node
    ) {
        commandBuilder.clear(CHOICES_ROOT);
        QuestBoardTurnInRow turnIn = resolveQuestBoardTurnIn(ref, store);
        int uiSlot = 0;
        List<DialogueChoiceDefinition> choices = buildChoiceList(node);
        int lastChoiceIndex = choices.isEmpty() ? -1 : choices.size() - 1;
        for (int i = 0; i < choices.size(); i++) {
            if (i == lastChoiceIndex && turnIn != null) {
                uiSlot = appendQuestBoardTurnInRow(commandBuilder, eventBuilder, uiSlot, turnIn);
            }
            uiSlot =
                appendDialogueChoiceRow(ref, store, commandBuilder, eventBuilder, choices.get(i), i, uiSlot);
        }
        if (turnIn != null && lastChoiceIndex < 0) {
            appendQuestBoardTurnInRow(commandBuilder, eventBuilder, uiSlot, turnIn);
        }
    }

    private int appendDialogueChoiceRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull DialogueChoiceDefinition ch,
        int choiceIndex,
        int uiSlot
    ) {
        boolean baseOk = conditions.evaluate(ch.getCondition(), ref, store, npcRef);
        String wf = ch.whenFalseOrDefault();
        JsonObject visOnly = ch.getVisibilityCondition();
        if (visOnly != null) {
            if (!conditions.evaluate(visOnly, ref, store, npcRef)) {
                return uiSlot;
            }
        } else if (!baseOk && "hide".equalsIgnoreCase(wf)) {
            return uiSlot;
        }
        boolean disabled =
            visOnly != null ? !baseOk : !baseOk && "disabled".equalsIgnoreCase(wf);
        if (ch.isGiftDisableWhenNotAllowed()) {
            AetherhavenPlugin giftPlugin = AetherhavenPlugin.get();
            if (giftPlugin == null
                || !VillagerBefriendableResolver.isBefriendable(store, npcRef, giftPlugin)) {
                return uiSlot;
            }
            if (baseOk && !dialogueWorldView.villagerGiftAllowed(ref, store, npcRef)) {
                disabled = true;
            }
        }
        String text = ch.getText() != null ? ch.getText() : "";
        Message choiceLine;
        if (disabled) {
            Message reasonMsg = ch.isGiftDisableWhenNotAllowed()
                ? dialogueWorldView.villagerGiftBlockMessage(ref, store, npcRef)
                : null;
            if (reasonMsg == null) {
                String reason = ch.getDisabledReason();
                if (reason != null && !reason.isBlank()) {
                    reasonMsg = dialogueMessage(reason);
                }
            }
            choiceLine =
                reasonMsg != null
                    ? choiceTranslationMessage(ref, store, text).insert(Message.raw("  ")).insert(reasonMsg)
                    : choiceTranslationMessage(ref, store, text);
        } else {
            choiceLine = choiceTranslationMessage(ref, store, text);
        }
        commandBuilder.append(CHOICES_ROOT, "Aetherhaven/DialogueChoiceRow.ui");
        String sel = choiceRowSelector(uiSlot);
        commandBuilder.set(sel + " #Text.TextSpans", choiceLine);
        commandBuilder.set(sel + ".Disabled", disabled);
        commandBuilder.set(sel + " #Text.Style.TextColor", disabled ? "#6d6658" : "#f0e6d2");
        applyChoiceIcon(commandBuilder, sel, ch);
        if (!disabled) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                sel,
                new EventData().append("Action", "Choice").append("ChoiceIndex", String.valueOf(choiceIndex)),
                false
            );
        }
        return uiSlot + 1;
    }

    @Nullable
    private QuestBoardTurnInRow resolveQuestBoardTurnIn(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (nu == null || pu == null) {
            return null;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(pu.getUuid());
        if (town == null || !town.playerCanCompleteQuests(pu.getUuid())) {
            return null;
        }
        com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord slot =
            com.hexvane.aetherhaven.questboard.QuestBoardService.findAcceptedForGiver(
                town, nu.getUuid(), npcRoleId(store, npcRef), store
            );
        if (slot == null) {
            return null;
        }
        com.hexvane.aetherhaven.questboard.QuestBoardQuestTypeHandler handler =
            com.hexvane.aetherhaven.questboard.QuestBoardService.handlerFor(slot.getQuestType());
        boolean ready = handler != null && handler.hasRequiredItems(ref, store, slot);
        return new QuestBoardTurnInRow(ready, slot.isHuntQuest(), slot.isRaidQuest());
    }

    private int appendQuestBoardTurnInRow(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        int uiSlot,
        @Nonnull QuestBoardTurnInRow turnIn
    ) {
        Message choiceLine =
            Message.translation(
                turnIn.raidQuest()
                    ? "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.dialogue.turnInRaid"
                    : turnIn.huntQuest()
                        ? "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.dialogue.turnInHunt"
                        : "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.dialogue.turnIn"
            );
        if (!turnIn.ready()) {
            choiceLine =
                choiceLine
                    .insert(Message.raw("  "))
                    .insert(
                        Message.translation(
                            turnIn.raidQuest()
                                ? "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.dialogue.turnInRaidMissing"
                                : turnIn.huntQuest()
                                    ? "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.dialogue.turnInHuntMissing"
                                    : "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.dialogue.turnInMissing"
                        )
                    );
        }
        commandBuilder.append(CHOICES_ROOT, "Aetherhaven/DialogueChoiceRow.ui");
        String sel = choiceRowSelector(uiSlot);
        commandBuilder.set(sel + " #Text.TextSpans", choiceLine);
        commandBuilder.set(sel + ".Disabled", !turnIn.ready());
        commandBuilder.set(sel + " #Text.Style.TextColor", turnIn.ready() ? "#f0e6d2" : "#6d6658");
        applyChoiceIconPath(commandBuilder, sel, ICON_QUEST);
        if (turnIn.ready()) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                sel,
                new EventData().append("Action", "QuestBoardTurnIn"),
                false
            );
        }
        return uiSlot + 1;
    }

    private record QuestBoardTurnInRow(boolean ready, boolean huntQuest, boolean raidQuest) {}

    @Nullable
    private static String npcRoleId(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return null;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
            return null;
        }
        return npc.getRoleName().trim();
    }

    @Nullable
    private static boolean isBardSongListNode(@Nonnull DialogueNodeDefinition node) {
        String mode = node.getBodyMode();
        return mode != null && "bard_song_list".equalsIgnoreCase(mode.trim());
    }

    @Nonnull
    private static List<DialogueChoiceDefinition> buildChoiceList(@Nonnull DialogueNodeDefinition node) {
        List<DialogueChoiceDefinition> choices = new ArrayList<>();
        if (isBardSongListNode(node)) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin != null) {
                choices.addAll(BardDialogueSongs.buildSongChoices(plugin));
            }
        }
        choices.addAll(node.getChoices());
        return choices;
    }

    private static String choiceIconPath(@Nonnull DialogueChoiceDefinition ch) {
        if (ch.isGiftChoice()) {
            return ICON_GIFT;
        }
        if (ch.hasAction("open_jewelry_appraisal")) {
            return ICON_JEWELRY_APPRAISAL;
        }
        if (ch.hasAction("open_blacksmith_repair")) {
            return ICON_BLACKSMITH_REPAIR;
        }
        if (ch.hasAction("open_geode_ui")) {
            return ICON_GEODE_OPEN;
        }
        if (ch.hasAction("play_bard_song") || ch.hasAction("stop_bard_song")) {
            return ICON_MUSICAL_NOTE;
        }
        if (ch.getNext() != null && "music_pick".equalsIgnoreCase(ch.getNext().trim())) {
            return ICON_MUSICAL_NOTE;
        }
        if (ch.isQuestProgressChoice()) {
            return ICON_QUEST_PROGRESS;
        }
        if (ch.isQuestOfferChoice()) {
            return ICON_QUEST;
        }
        if (ch.endsConversation()) {
            return ICON_EXIT;
        }
        return null;
    }

    private static void applyChoiceIcon(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String rowSelector,
        @Nonnull DialogueChoiceDefinition ch
    ) {
        applyChoiceIconPath(commandBuilder, rowSelector, choiceIconPath(ch));
    }

    private static void applyChoiceIconPath(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String rowSelector,
        @Nullable String iconPath
    ) {
        String iconSel = rowSelector + " #ChoiceIcon";
        if (iconPath == null || iconPath.isBlank()) {
            commandBuilder.set(iconSel + ".Visible", false);
            return;
        }
        commandBuilder.set(iconSel + ".AssetPath", iconPath);
        commandBuilder.set(iconSel + ".Visible", true);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull DialogueEventData data) {
        if (data.action != null && data.action.equalsIgnoreCase("QuestBoardTurnIn")) {
            handleQuestBoardTurnIn(ref, store);
            return;
        }
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
        List<DialogueChoiceDefinition> choices = node != null ? buildChoiceList(node) : List.of();
        if (node == null || choiceIndex < 0 || choiceIndex >= choices.size()) {
            return;
        }
        DialogueChoiceDefinition choice = choices.get(choiceIndex);
        JsonObject visOnly = choice.getVisibilityCondition();
        if (visOnly != null && !conditions.evaluate(visOnly, ref, store, npcRef)) {
            return;
        }
        if (!conditions.evaluate(choice.getCondition(), ref, store, npcRef)) {
            String wf = choice.whenFalseOrDefault();
            if ("hide".equalsIgnoreCase(wf) || "disabled".equalsIgnoreCase(wf)) {
                return;
            }
        } else if (choice.isGiftDisableWhenNotAllowed() && !dialogueWorldView.villagerGiftAllowed(ref, store, npcRef)) {
            return;
        }
        DialogueActionBatchResult batch = new DialogueActionBatchResult();
        actions.runBatch(choice.getActions(), ref, store, batch, npcRef);
        if (npcRef != null && npcRef.isValid()) {
            NpcFaceVisuals.playTalkBurst(npcRef, store);
        }
        applyBatchNavigation(ref, store, batch, choice.getNext());
    }

    private void handleQuestBoardTurnIn(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (nu == null || pu == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(pu.getUuid());
        if (town == null || !town.playerCanCompleteQuests(pu.getUuid())) {
            return;
        }
        java.util.Random rng = new java.util.Random(nu.getUuid().getMostSignificantBits() ^ System.nanoTime());
        if (com.hexvane.aetherhaven.questboard.QuestBoardService.completeBoardQuest(
            town, tm, ref, store, nu.getUuid(), npcRoleId(store, npcRef), plugin.getQuestBoardCatalog(), rng
        )) {
            close();
        }
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
            if (batch.isCloseDialogue()
                || batch.getOpenBarterShopAfterClose() != null
                || batch.isOpenBlacksmithRepairAfterClose()
                || batch.isOpenGeodePageAfterClose()
                || batch.isOpenJewelryAppraisalAfterClose()
                || batch.hasAfterClose()) {
                finishClose(ref, store, world, batch);
                return;
            }
            rebuild();
            return;
        }
        if (batch.isCloseDialogue()
            || batch.getOpenBarterShopAfterClose() != null
            || batch.isOpenBlacksmithRepairAfterClose()
            || batch.isOpenGeodePageAfterClose()
            || batch.isOpenJewelryAppraisalAfterClose()
            || batch.hasAfterClose()) {
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
        } else if (world != null && batch.isOpenBlacksmithRepairAfterClose()) {
            world.execute(() -> {
                Ref<EntityStore> pref = playerRef.getReference();
                if (pref == null || !pref.isValid()) {
                    return;
                }
                Store<EntityStore> st = pref.getStore();
                Player player = st.getComponent(pref, Player.getComponentType());
                PlayerRef pr = st.getComponent(pref, PlayerRef.getComponentType());
                if (player == null || pr == null) {
                    return;
                }
                CombinedItemContainer inv =
                    InventoryComponent.getCombined(st, pref, InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE);
                player.getPageManager().openCustomPage(pref, st, new BlacksmithRepairPage(pr, inv));
            });
        } else if (world != null && batch.isOpenGeodePageAfterClose()) {
            world.execute(() -> {
                Ref<EntityStore> pref = playerRef.getReference();
                if (pref == null || !pref.isValid()) {
                    return;
                }
                Store<EntityStore> st = pref.getStore();
                Player player = st.getComponent(pref, Player.getComponentType());
                PlayerRef pr = st.getComponent(pref, PlayerRef.getComponentType());
                if (player == null || pr == null) {
                    return;
                }
                player.getPageManager().openCustomPage(pref, st, new GeodeOpenPage(pr));
            });
        } else if (world != null && batch.isOpenJewelryAppraisalAfterClose()) {
            boolean chargeGold = batch.isJewelryAppraisalChargeGold();
            world.execute(() -> {
                Ref<EntityStore> pref = playerRef.getReference();
                if (pref == null || !pref.isValid()) {
                    return;
                }
                Store<EntityStore> st = pref.getStore();
                Player player = st.getComponent(pref, Player.getComponentType());
                PlayerRef pr = st.getComponent(pref, PlayerRef.getComponentType());
                if (player == null || pr == null) {
                    return;
                }
                player.getPageManager().openCustomPage(pref, st, new JewelryAppraisalPage(pr, chargeGold));
            });
        } else if (world != null && batch.hasAfterClose()) {
            Runnable after = batch.getAfterClose();
            world.execute(() -> {
                if (after != null) {
                    after.run();
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
