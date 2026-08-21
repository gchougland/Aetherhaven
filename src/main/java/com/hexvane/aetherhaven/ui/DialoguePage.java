package com.hexvane.aetherhaven.ui;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.dialogue.DialogueActionExecutor;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueChoiceItemRequirements;
import com.hexvane.aetherhaven.dialogue.DialogueConditionEvaluator;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerSystem;
import com.hexvane.aetherhaven.patrol.GuardFollowPlayerSystem;
import com.hexvane.aetherhaven.questboard.TownRankCapacity;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.bard.BardDialogueSongs;
import com.hexvane.aetherhaven.dialogue.DialogueWorldView;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueNodeDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import com.hexvane.aetherhaven.npc.NpcDialogueCleanup;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.speech.DialogueMessagePlainText;
import com.hexvane.aetherhaven.speech.NpcDialogueSpeech;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkGreetingPicker;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.tourist.TouristMoveInRequirements;
import com.hexvane.aetherhaven.villager.VillagerBefriendableResolver;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.calendar.PlayerBirthdayGiftService;
import com.hexvane.aetherhaven.calendar.PlayerBirthdayIds;
import com.hexvane.aetherhaven.calendar.VillagerBirthdayGreetingPicker;
import com.hexvane.aetherhaven.calendar.VillagerBirthdayService;
import com.hexvane.aetherhaven.festival.FestivalDialogueChoiceOrder;
import com.hexvane.aetherhaven.festival.FestivalDialogueGreetings;
import com.hexvane.aetherhaven.festival.market.MarketIds;
import com.hexvane.aetherhaven.festival.market.MarketSession;
import com.hexvane.aetherhaven.festival.market.MarketSessionIndex;
import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbSession;
import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbSessionIndex;
import com.hexvane.aetherhaven.festival.snowball.SnowballSession;
import com.hexvane.aetherhaven.festival.snowball.SnowballSessionIndex;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftService;
import com.hexvane.aetherhaven.festival.wintertide.WintertideIds;
import com.hexvane.aetherhaven.festival.wintertide.WintertideSession;
import com.hexvane.aetherhaven.festival.wintertide.WintertideSessionIndex;
import com.hexvane.aetherhaven.festival.wintertide.WintertideTarget;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.villager.data.VillagerGreetingPicker;
import com.hexvane.aetherhaven.villager.data.VillagerNeedsDialoguePicker;
import com.hexvane.aetherhaven.worldnpc.WorldNpcBinding;
import com.hexvane.aetherhaven.worldnpc.WorldNpcDialogueChoiceFilter;
import com.hexvane.aetherhaven.worldnpc.WorldNpcDisplay;
import com.hexvane.aetherhaven.worldnpc.WorldNpcPlacementRecord;
import com.hexvane.aetherhaven.worldnpc.WorldNpcSpawnRoles;
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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

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
    private static final String ICON_FOLLOW = "UI/Custom/man-walking.png";
    private static final String ICON_TELEPORT = "UI/Custom/teleport.png";
    private static final String ICON_MARKET = "UI/Custom/market.png";
    private static final String LANG_FOLLOW_START =
        "aetherhaven_dialogue_follow.aetherhaven.dialogue.follow.start";
    private static final String LANG_FOLLOW_STOP =
        "aetherhaven_dialogue_follow.aetherhaven.dialogue.follow.stop";
    private static final String LANG_GUILD_ADVENTURER_HIRE =
        "aetherhaven_dialogue_guild_adventurer.aetherhaven.dialogue.aetherhaven_guild_adventurer.main_hub.hire";
    private static final String LANG_GUILD_ADVENTURER_HIRE_YES =
        "aetherhaven_dialogue_guild_adventurer.aetherhaven.dialogue.aetherhaven_guild_adventurer.hire_confirm.yes";
    private static final String LANG_GUILD_ADVENTURER_ROSTER =
        "aetherhaven_dialogue_guild_adventurer.aetherhaven.dialogue.aetherhaven_guild_adventurer.hire_roster";
    private static final String LANG_GUILD_ADVENTURER_LIMIT =
        "aetherhaven_dialogue_guild_adventurer.aetherhaven.dialogue.aetherhaven_guild_adventurer.hire_limitReached";
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

    /** Last node id that started letter speech (avoid restarting on unrelated rebuilds). */
    @Nullable
    private String speechStartedForNodeId;

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
        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (pu != null) {
            NpcDialogueSpeech.cancelForPlayer(pu.getUuid());
            WintertideGiftService.onDialogueDismissed(treeId, pu.getUuid(), store, npcRef);
            PlayerBirthdayGiftService.onDialogueDismissed(treeId, pu.getUuid(), store, npcRef);
        }
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
        bodyMsg = withWintertideBodies(ref, store, bodyMsg);
        bodyMsg = withPlayerBirthdayBodies(store, bodyMsg);
        bodyMsg = withGuildAdventurerHireBody(ref, store, node, bodyMsg);
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
        maybeStartLetterSpeech(ref, store, bodyMsg);
    }

    private void maybeStartLetterSpeech(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Message bodyMsg
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }
        if (java.util.Objects.equals(speechStartedForNodeId, nodeId)) {
            return;
        }
        speechStartedForNodeId = nodeId;
        String plain = DialogueMessagePlainText.resolve(bodyMsg, playerRef.getLanguage());
        NpcDialogueSpeech.startTalkSpeech(ref, npcRef, store, plain);
    }

    @Nonnull
    private Message resolveSpeakerMessage(@Nonnull Store<EntityStore> store, @Nonnull DialogueNodeDefinition node) {
        if (WintertideIds.DIALOGUE_PLAYER_RATE.equals(treeId)) {
            String giverName = wintertidePendingGiverName(store);
            if (giverName != null && !giverName.isBlank()) {
                return Message.raw(giverName);
            }
        }
        if (npcRef != null && npcRef.isValid()) {
            WorldNpcBinding worldBinding = store.getComponent(npcRef, WorldNpcBinding.getComponentType());
            if (worldBinding != null) {
                Message override = resolveWorldNpcSpeakerOverride(store, worldBinding);
                if (override != null) {
                    return override;
                }
            }
        }
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

    @Nullable
    private Message resolveWorldNpcSpeakerOverride(
        @Nonnull Store<EntityStore> store,
        @Nonnull WorldNpcBinding worldBinding
    ) {
        PersistentDisplayName displayName = store.getComponent(npcRef, PersistentDisplayName.getComponentType());
        if (displayName != null && displayName.getDisplayName() != null) {
            String raw = displayName.getDisplayName().getRawText();
            if (raw != null && !raw.isBlank()) {
                return Message.raw(raw.trim());
            }
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        WorldNpcPlacementRecord placement =
            AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin)
                .findPlacement(worldBinding.getPlacementId());
        if (placement != null) {
            String override = placement.displayNameOrEmpty();
            if (!override.isEmpty()) {
                return Message.raw(override);
            }
        }
        return null;
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
                        String greetingRole = npc.getRoleName().trim();
                        WorldNpcBinding worldBinding =
                            store.getComponent(npcRef, WorldNpcBinding.getComponentType());
                        if (worldBinding != null) {
                            String logical = WorldNpcSpawnRoles.toLogicalRoleId(worldBinding.getNpcRoleId());
                            if (!logical.isEmpty()) {
                                greetingRole = logical;
                            } else {
                                greetingRole = WorldNpcSpawnRoles.toLogicalRoleId(greetingRole);
                            }
                        }
                        VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(greetingRole);
                        long day = VillagerReputationService.currentGameEpochDay(store);
                        var wtr = store.getResource(com.hypixel.hytale.server.core.modules.time.WorldTimeResource.getResourceType());
                        // Birthday first, then festival chatter (even over low needs), then needs, then everyday lines.
                        if (vdef != null && wtr != null && VillagerBirthdayService.isBirthdayToday(vdef, wtr.getGameDateTime())) {
                            Message birthday = VillagerBirthdayGreetingPicker.pickMessage(vdef, pu.getUuid(), nu.getUuid(), day);
                            if (birthday != null) {
                                return birthday;
                            }
                        }
                        Message festival =
                            FestivalDialogueGreetings.pickGreeting(store, npcRef, pu.getUuid(), nu.getUuid(), day);
                        if (festival != null) {
                            return festival;
                        }
                        Message needGreeting =
                            VillagerNeedsDialoguePicker.pickMessage(store, npcRef, plugin, pu.getUuid(), nu.getUuid());
                        if (needGreeting != null) {
                            return needGreeting;
                        }
                        Message townsfolkGreeting = TownsfolkGreetingPicker.pickMessage(store, npcRef, plugin, pu.getUuid(), nu.getUuid());
                        if (townsfolkGreeting != null) {
                            return townsfolkGreeting;
                        }
                        if (vdef != null) {
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
        return withSnowballCollectParams(
            ref,
            store,
            withTreeClimbCollectParams(
                ref,
                store,
                withTouristMoveInParams(ref, store, dialogueMessage(body), body),
                body
            ),
            body
        );
    }

    @Nonnull
    private Message withTreeClimbCollectParams(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Message message,
        @Nullable String translationKey
    ) {
        if (translationKey == null
            || !isTranslationKey(translationKey)
            || !translationKey.contains("festival_tree_climb_attendant.collect_win")) {
            return message;
        }
        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (pu == null) {
            return message;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return message;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = resolvePlayerTown(store, ref);
        if (town == null) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc != null) {
                Vector3d pos = tc.getPosition();
                town = tm.findTownContainingBlock(world.getName(), (int) Math.floor(pos.x), (int) Math.floor(pos.z));
            }
        }
        if (town == null) {
            return message;
        }
        TreeClimbSession session = TreeClimbSessionIndex.get(town.getTownId());
        if (session == null) {
            return message;
        }
        int count = session.peekLastCollectedTickets(pu.getUuid());
        if (count <= 0) {
            return message;
        }
        return message.param("count", Integer.toString(count));
    }

    @Nonnull
    private Message withSnowballCollectParams(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Message message,
        @Nonnull String translationKey
    ) {
        if (translationKey.isBlank()
            || !translationKey.contains("festival_snowball_merchant.collect_win")) {
            return message;
        }
        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (pu == null) {
            return message;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return message;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = resolvePlayerTown(store, ref);
        if (town == null) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc != null) {
                Vector3d pos = tc.getPosition();
                town = tm.findTownContainingBlock(world.getName(), (int) Math.floor(pos.x), (int) Math.floor(pos.z));
            }
        }
        if (town == null) {
            return message;
        }
        SnowballSession session = SnowballSessionIndex.get(town.getTownId());
        if (session == null) {
            return message;
        }
        int count = session.peekLastCollectedTickets(pu.getUuid());
        if (count <= 0) {
            return message;
        }
        return message.param("count", Integer.toString(count));
    }

    @Nonnull
    private Message withGuildAdventurerHireBody(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull DialogueNodeDefinition node,
        @Nonnull Message bodyMsg
    ) {
        if (!"aetherhaven_guild_adventurer".equals(treeId)) {
            return bodyMsg;
        }
        String nodeText = node.getText();
        if (nodeText != null && isTranslationKey(nodeText) && nodeText.contains("hire_confirm.body")) {
            return withGuardHireCountParams(ref, store, bodyMsg);
        }
        if (!"main_hub".equals(nodeId)) {
            return bodyMsg;
        }
        Message roster = withGuardHireCountParams(ref, store, Message.translation(LANG_GUILD_ADVENTURER_ROSTER));
        bodyMsg = bodyMsg.insert(Message.raw("\n\n")).insert(roster);
        if (dialogueWorldView.guardHireAtLimit(ref, store)) {
            bodyMsg = bodyMsg.insert(Message.raw(" ")).insert(Message.translation(LANG_GUILD_ADVENTURER_LIMIT));
        }
        return bodyMsg;
    }

    @Nonnull
    private Message withGuardHireCountParams(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Message message
    ) {
        return message
            .param("current", Integer.toString(dialogueWorldView.hiredGuardCount(ref, store)))
            .param("max", Integer.toString(dialogueWorldView.maxHiredGuards(ref, store)));
    }

    private static boolean isGuildAdventurerHireChoice(@Nullable String text) {
        return LANG_GUILD_ADVENTURER_HIRE.equals(text) || LANG_GUILD_ADVENTURER_HIRE_YES.equals(text);
    }

    @Nonnull
    private Message withTouristMoveInParams(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Message message,
        @Nullable String translationKey
    ) {
        if (translationKey == null || !isTranslationKey(translationKey) || !usesTouristMoveInParams(translationKey)) {
            return message;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || npcRef == null || !npcRef.isValid()) {
            return message;
        }
        var requirements = TouristMoveInRequirements.forNpc(plugin, store, npcRef);
        return message
            .param("item", TouristMoveInRequirements.primaryItemLabelMessage(requirements))
            .param("items", TouristMoveInRequirements.itemsLabelMessage(requirements));
    }

    private static boolean usesTouristMoveInParams(@Nonnull String translationKey) {
        return translationKey.contains("aetherhaven_dialogue_tourist")
            && (translationKey.contains("_offer.")
                || translationKey.contains("_accepted.")
                || translationKey.contains("_remind_gift.")
                || translationKey.contains("c_house_give"));
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
            return withGuardHireCountParams(
                ref,
                store,
                m.param("gold", Long.toString(gold)).param("type", Message.translation(typeKey))
            );
        }
        if (usesTouristMoveInParams(text)) {
            return withTouristMoveInParams(ref, store, m, text);
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
            WorldNpcBinding worldBinding = store.getComponent(npcRef, WorldNpcBinding.getComponentType());
            if (plugin != null && worldBinding != null) {
                World world = store.getExternalData().getWorld();
                WorldNpcPlacementRecord placement =
                    AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin)
                        .findPlacement(worldBinding.getPlacementId());
                if (placement != null) {
                    portrait = WorldNpcDisplay.portraitPath(placement);
                } else {
                    String logical = WorldNpcSpawnRoles.toLogicalRoleId(worldBinding.getNpcRoleId());
                    if (logical.isEmpty()) {
                        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                        logical =
                            npc != null && npc.getRoleName() != null
                                ? WorldNpcSpawnRoles.toLogicalRoleId(npc.getRoleName())
                                : "";
                    }
                    portrait = NpcPortraitProvider.portraitPathForRoleId(logical);
                }
            } else {
                NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                String roleId = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
                if (plugin != null && !roleId.isBlank()) {
                    portrait = TownResidentDisplay.resolveFromEntity(store, npcRef, roleId, plugin).portraitPath();
                } else if (!roleId.isBlank()) {
                    portrait = NpcPortraitProvider.portraitPathForRoleId(roleId);
                }
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
        List<DialogueChoiceDefinition> choices = buildChoiceList(node, ref, store);
        int lastChoiceIndex = choices.isEmpty() ? -1 : choices.size() - 1;
        for (int i = 0; i < choices.size(); i++) {
            if (i == lastChoiceIndex && turnIn != null) {
                uiSlot = appendQuestBoardTurnInRow(ref, store, commandBuilder, eventBuilder, uiSlot, turnIn);
            }
            uiSlot =
                appendDialogueChoiceRow(ref, store, commandBuilder, eventBuilder, choices.get(i), i, uiSlot);
        }
        if (turnIn != null && lastChoiceIndex < 0) {
            appendQuestBoardTurnInRow(ref, store, commandBuilder, eventBuilder, uiSlot, turnIn);
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
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null
            && npcRef != null
            && npcRef.isValid()
            && store.getComponent(npcRef, WorldNpcBinding.getComponentType()) != null
            && WorldNpcDialogueChoiceFilter.shouldHideForWorldNpc(ch, plugin)) {
            return uiSlot;
        }
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
        List<MaterialRequirement> itemRequirements =
            DialogueChoiceItemRequirements.resolve(ch, plugin, ref, store, npcRef);
        boolean hasRequiredItems =
            itemRequirements.isEmpty() || DialogueChoiceItemRequirements.playerHasAll(ref, store, itemRequirements);
        if (!itemRequirements.isEmpty() && !hasRequiredItems) {
            disabled = true;
        }
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
            if (reasonMsg == null && itemRequirements.isEmpty()) {
                String reason = ch.getDisabledReason();
                if (reason != null && !reason.isBlank()) {
                    reasonMsg = dialogueMessage(reason);
                }
            }
            if (reasonMsg == null && isGuildAdventurerHireChoice(text) && dialogueWorldView.guardHireAtLimit(ref, store)) {
                reasonMsg = Message.translation(LANG_GUILD_ADVENTURER_LIMIT);
            }
            choiceLine =
                reasonMsg != null
                    ? choiceTranslationMessage(ref, store, text).insert(Message.raw("  ")).insert(reasonMsg)
                    : choiceTranslationMessage(ref, store, text);
        } else {
            choiceLine = choiceTranslationMessage(ref, store, text);
        }
        commandBuilder.append(CHOICES_ROOT, DialogueChoiceRequirementsUi.rowDocument(itemRequirements));
        String sel = choiceRowSelector(uiSlot);
        commandBuilder.set(sel + " #Text.TextSpans", choiceLine);
        commandBuilder.set(sel + ".Disabled", disabled);
        commandBuilder.set(sel + " #Text.Style.TextColor", disabled ? "#6d6658" : "#f0e6d2");
        applyChoiceIcon(commandBuilder, sel, ch, itemRequirements);
        DialogueChoiceRequirementsUi.applyItemGrid(commandBuilder, sel, itemRequirements);
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
        TownRecord town = resolvePlayerTown(store, ref);
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
        List<MaterialRequirement> turnInItems = List.of();
        if (com.hexvane.aetherhaven.questboard.FetchQuestBoardHandler.TYPE_ID.equalsIgnoreCase(slot.getQuestType())) {
            turnInItems = fetchQuestTurnInMaterials(slot);
        }
        return new QuestBoardTurnInRow(ready, slot.isHuntQuest(), slot.isRaidQuest(), turnInItems);
    }

    @Nonnull
    private static List<MaterialRequirement> fetchQuestTurnInMaterials(
        @Nonnull com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord slot
    ) {
        List<MaterialRequirement> out = new ArrayList<>();
        for (com.hexvane.aetherhaven.questboard.QuestBoardItemRequirement req : slot.requiredItemsOrEmpty()) {
            String itemId = req.itemIdOrEmpty();
            if (itemId.isBlank()) {
                continue;
            }
            out.add(MaterialRequirement.ofItem(itemId, Math.max(1, req.count())));
        }
        return List.copyOf(out);
    }

    private int appendQuestBoardTurnInRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
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
        if (!turnIn.ready() && turnIn.turnInItems().isEmpty()) {
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
        List<MaterialRequirement> itemRequirements = DialogueChoiceItemRequirements.resolve(turnIn.turnInItems());
        commandBuilder.append(CHOICES_ROOT, DialogueChoiceRequirementsUi.rowDocument(itemRequirements));
        String sel = choiceRowSelector(uiSlot);
        commandBuilder.set(sel + " #Text.TextSpans", choiceLine);
        commandBuilder.set(sel + ".Disabled", !turnIn.ready());
        commandBuilder.set(sel + " #Text.Style.TextColor", turnIn.ready() ? "#f0e6d2" : "#6d6658");
        applyChoiceIconPath(commandBuilder, sel, ICON_QUEST);
        DialogueChoiceRequirementsUi.applyItemGrid(commandBuilder, sel, itemRequirements);
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

    private record QuestBoardTurnInRow(
        boolean ready,
        boolean huntQuest,
        boolean raidQuest,
        @Nonnull List<MaterialRequirement> turnInItems
    ) {}

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
        return mode != null
            && ("bard_song_list".equalsIgnoreCase(mode.trim())
                || "bard_song_list_loop".equalsIgnoreCase(mode.trim()));
    }

    private static boolean isBardLoopSongList(@Nonnull DialogueNodeDefinition node) {
        String mode = node.getBodyMode();
        return mode != null && "bard_song_list_loop".equalsIgnoreCase(mode.trim());
    }

    @Nonnull
    private List<DialogueChoiceDefinition> buildChoiceList(
        @Nonnull DialogueNodeDefinition node,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        List<DialogueChoiceDefinition> choices = new ArrayList<>();
        if (isBardSongListNode(node)) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin != null) {
                choices.addAll(
                    isBardLoopSongList(node)
                        ? BardDialogueSongs.buildLoopSongChoices(plugin)
                        : BardDialogueSongs.buildSongChoices(plugin)
                );
            }
        }
        choices.addAll(node.getChoices());
        injectFollowChoice(choices, playerRef, store);
        injectMarketChoices(choices, playerRef, store);
        injectWintertideGiftChoice(choices, playerRef, store);
        FestivalDialogueChoiceOrder.promoteToTop(choices);
        return choices;
    }

    private void injectFollowChoice(
        @Nonnull List<DialogueChoiceDefinition> choices,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (!"main_hub".equals(nodeId)) {
            return;
        }
        UUIDComponent playerUuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (playerUuid == null) {
            return;
        }
        boolean villagerEligible = VillagerFollowPlayerSystem.isEligibleCitizen(store, npcRef);
        boolean guardEligible = false;
        if (!villagerEligible && npcRef != null && npcRef.isValid()) {
            TownRecord town = resolvePlayerTown(store, playerRef);
            if (town != null) {
                guardEligible = GuardFollowPlayerSystem.isEligibleGuard(store, npcRef, town.getTownId());
            }
        }
        if (!villagerEligible && !guardEligible) {
            return;
        }
        boolean following =
            villagerEligible
                ? VillagerFollowPlayerSystem.isFollowingPlayer(store, npcRef, playerUuid.getUuid())
                : GuardFollowPlayerSystem.isFollowingPlayer(store, npcRef, playerUuid.getUuid());
        if (!following) {
            TownRecord town = resolvePlayerTown(store, playerRef);
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (town == null
                || plugin == null
                || !TownRankCapacity.canStartFollow(
                    store,
                    playerUuid.getUuid(),
                    town,
                    plugin.getQuestBoardCatalog(),
                    npcRef
                )) {
                return;
            }
        }
        DialogueChoiceDefinition follow = new DialogueChoiceDefinition();
        follow.setId(following ? "follow_stop" : "follow_start");
        follow.setText(following ? LANG_FOLLOW_STOP : LANG_FOLLOW_START);
        follow.setNext(null);
        JsonObject action = new JsonObject();
        action.addProperty("type", following ? "stop_follow_player" : "start_follow_player");
        JsonObject close = new JsonObject();
        close.addProperty("type", "close");
        follow.setActions(List.of(action, close));
        int insertAt = choices.size();
        for (int i = 0; i < choices.size(); i++) {
            DialogueChoiceDefinition ch = choices.get(i);
            if (ch.closesDialogue() || ch.endsConversation()) {
                insertAt = i;
                break;
            }
        }
        choices.add(insertAt, follow);
    }

    private void injectMarketChoices(
        @Nonnull List<DialogueChoiceDefinition> choices,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (!"main_hub".equals(nodeId) || npcRef == null || !npcRef.isValid()) {
            return;
        }
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        UUIDComponent npcUuid = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (binding == null || npcUuid == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        if (town == null || !MarketIds.FESTIVAL_ID.equals(town.getActiveFestivalId())) {
            return;
        }
        MarketSession session = MarketSessionIndex.getOrCreate(town.getTownId());
        if (TownVillagerBinding.KIND_ELDER.equalsIgnoreCase(binding.getKind())) {
            insertMarketHowChoice(choices);
            if (session.isJudging()) {
                insertMarketChoice(
                    choices,
                    "market_walking",
                    "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.walking",
                    null,
                    null
                );
            } else if (session.isJudged()) {
                insertMarketChoice(
                    choices,
                    "market_results",
                    "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.results",
                    "market_claim_results",
                    ICON_MARKET
                );
                insertMarketChoice(
                    choices,
                    "market_scoreboard",
                    "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.scoreboard",
                    "market_open_scoreboard",
                    ICON_MARKET
                );
            } else {
                insertMarketChoice(
                    choices,
                    "market_fill",
                    "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.fill",
                    "market_open_stall",
                    ICON_MARKET
                );
                insertMarketChoice(
                    choices,
                    "market_start",
                    "aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.start",
                    "market_start_judging",
                    ICON_MARKET
                );
            }
        }
        if (session.isVendor(npcUuid.getUuid())) {
            String shopId = MarketIds.shopIdForKind(binding.getKind());
            if (!shopId.isEmpty()) {
                insertMarketShopChoice(choices, shopId);
            }
        }
    }

    private void injectWintertideGiftChoice(
        @Nonnull List<DialogueChoiceDefinition> choices,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (!"main_hub".equals(nodeId) || npcRef == null || !npcRef.isValid()) {
            return;
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null || !WintertideGiftService.holdingItem(store, playerRef)) {
            return;
        }
        TownRecord town = WintertideGiftService.resolveTown(playerRef, store, npcRef);
        if (town == null || !WintertideGiftService.canGiveToVillager(town, store, pu.getUuid(), npcRef)) {
            return;
        }
        DialogueChoiceDefinition choice = new DialogueChoiceDefinition();
        choice.setId("wintertide_gift");
        choice.setText(
            "aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.choice.give"
        );
        choice.setIcon(ICON_GIFT);
        choice.setNext(null);
        JsonObject action = new JsonObject();
        action.addProperty("type", "wintertide_gift_villager");
        choice.setActions(List.of(action));
        FestivalDialogueChoiceOrder.insertAtTop(choices, choice);
    }

    @Nonnull
    private Message withWintertideBodies(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Message bodyMsg
    ) {
        if (WintertideIds.DIALOGUE_GIFT_REACTION.equals(treeId)) {
            String kind = wintertideNpcKind(store);
            String tier =
                switch (nodeId) {
                    case "gift_love" -> "love";
                    case "gift_like" -> "like";
                    case "gift_neutral" -> "neutral";
                    case "gift_dislike" -> "dislike";
                    default -> "";
                };
            if (!tier.isEmpty()) {
                return dialogueMessage(wintertideGiftLineKey(kind, tier));
            }
        }
        if (WintertideIds.DIALOGUE_INCOMING.equals(treeId) && "gift".equals(nodeId)) {
            return dialogueMessage(wintertideIncomingLineKey(wintertideNpcKind(store)));
        }
        if (WintertideIds.DIALOGUE_MERCHANT.equals(treeId) && "assigned".equals(nodeId)) {
            WintertideTarget target = wintertideOutgoingTarget(ref, store);
            if (target != null) {
                return bodyMsg.param("name", target.getDisplayName());
            }
        }
        if (WintertideIds.DIALOGUE_PLAYER_RATE.equals(treeId) && "rate".equals(nodeId)) {
            String giverName = wintertidePendingGiverName(store);
            if (giverName != null && !giverName.isBlank()) {
                return bodyMsg.param("name", giverName);
            }
        }
        return bodyMsg;
    }

    @Nonnull
    private Message withPlayerBirthdayBodies(@Nonnull Store<EntityStore> store, @Nonnull Message bodyMsg) {
        if (PlayerBirthdayIds.DIALOGUE_INCOMING.equals(treeId) && "gift".equals(nodeId)) {
            return dialogueMessage(playerBirthdayIncomingLineKey(wintertideNpcKind(store)));
        }
        return bodyMsg;
    }

    @Nonnull
    private static String playerBirthdayIncomingLineKey(@Nonnull String kind) {
        if ("default".equals(kind)) {
            return "aetherhaven_dialogue_player_birthday.aetherhaven.dialogue.player.birthday.incoming.body";
        }
        return "aetherhaven_dialogue_player_birthday.aetherhaven.dialogue.player.birthday.incoming." + kind;
    }

    @Nullable
    private WintertideTarget wintertideOutgoingTarget(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        TownRecord town = WintertideGiftService.resolveTown(playerRef, store, npcRef);
        if (pu == null || town == null) {
            return null;
        }
        return WintertideGiftService.sessionFor(town, store).getOutgoing(pu.getUuid());
    }

    @Nullable
    private String wintertidePendingGiverName(@Nonnull Store<EntityStore> store) {
        UUID playerUuid = playerRef.getUuid();
        TownRecord town = WintertideGiftService.resolveTownByPlayerUuid(store, playerUuid);
        if (town == null) {
            return null;
        }
        WintertideSession session = WintertideSessionIndex.get(town.getTownId());
        if (session == null) {
            return null;
        }
        WintertideSession.PendingPlayerGift pending = session.getPendingPlayerGift();
        if (pending == null || !pending.receiverUuid().equals(playerUuid)) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return pending.giverUuid().toString();
        }
        return TownPlayerLookup.displayNameForUuid(world, pending.giverUuid());
    }

    @Nonnull
    private String wintertideNpcKind(@Nonnull Store<EntityStore> store) {
        if (npcRef == null || !npcRef.isValid()) {
            return "default";
        }
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        String kind = binding != null ? binding.getKind() : null;
        if (kind == null || kind.isBlank()) {
            return "default";
        }
        return kind.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    private static String wintertideGiftLineKey(@Nonnull String kind, @Nonnull String tier) {
        if ("default".equals(kind)) {
            return "aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.gift."
                + tier
                + ".body";
        }
        return "aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.gift."
            + kind
            + "."
            + tier;
    }

    @Nonnull
    private static String wintertideIncomingLineKey(@Nonnull String kind) {
        if ("default".equals(kind)) {
            return "aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.incoming.body";
        }
        return "aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.incoming." + kind;
    }

    private void insertMarketChoice(
        @Nonnull List<DialogueChoiceDefinition> choices,
        @Nonnull String id,
        @Nonnull String textKey,
        @Nullable String actionType,
        @Nullable String icon
    ) {
        DialogueChoiceDefinition choice = new DialogueChoiceDefinition();
        choice.setId(id);
        choice.setText(textKey);
        choice.setNext(null);
        if (icon != null && !icon.isBlank()) {
            choice.setIcon(icon);
        }
        List<JsonObject> actions = new ArrayList<>();
        if (actionType != null) {
            JsonObject action = new JsonObject();
            action.addProperty("type", actionType);
            actions.add(action);
        }
        JsonObject close = new JsonObject();
        close.addProperty("type", "close");
        actions.add(close);
        choice.setActions(actions);
        FestivalDialogueChoiceOrder.insertAtTop(choices, choice);
    }

    private void insertMarketHowChoice(@Nonnull List<DialogueChoiceDefinition> choices) {
        DialogueChoiceDefinition choice = new DialogueChoiceDefinition();
        choice.setId("market_how");
        choice.setText("aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.how");
        choice.setNext("market_how");
        choice.setIcon(ICON_MARKET);
        FestivalDialogueChoiceOrder.insertAtTop(choices, choice);
    }

    private void insertMarketShopChoice(@Nonnull List<DialogueChoiceDefinition> choices, @Nonnull String shopId) {
        DialogueChoiceDefinition choice = new DialogueChoiceDefinition();
        choice.setId("market_shop");
        choice.setText("aetherhaven_dialogue_festival_market.aetherhaven.dialogue.festival.market.choice.shop");
        choice.setNext(null);
        choice.setIcon(ICON_MARKET);
        JsonObject action = new JsonObject();
        action.addProperty("type", "open_barter_shop");
        action.addProperty("shop", shopId);
        choice.setActions(List.of(action));
        FestivalDialogueChoiceOrder.insertAtTop(choices, choice);
    }

    private static int insertBeforeClose(@Nonnull List<DialogueChoiceDefinition> choices) {
        for (int i = 0; i < choices.size(); i++) {
            DialogueChoiceDefinition ch = choices.get(i);
            if (ch.closesDialogue() || ch.endsConversation()) {
                return i;
            }
        }
        return choices.size();
    }

    @Nullable
    private TownRecord resolvePlayerTown(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef
    ) {
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return null;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return null;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        if (npcRef != null && npcRef.isValid()) {
            TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            if (binding != null) {
                TownRecord npcTown = tm.getTown(binding.getTownId());
                if (npcTown != null && npcTown.hasMemberOrOwner(pu.getUuid())) {
                    return npcTown;
                }
            }
        }
        return TownPlayerResolution.resolveActiveTown(world, store, playerRef, tm);
    }

    @Nullable
    private static String choiceIconPath(@Nonnull DialogueChoiceDefinition ch) {
        String explicit = resolveChoiceIcon(ch.getIcon());
        if (explicit != null) {
            return explicit;
        }
        if (ch.isGiftChoice()) {
            return ICON_GIFT;
        }
        if (ch.hasAction("start_follow_player") || ch.hasAction("stop_follow_player")) {
            return ICON_FOLLOW;
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
        if (ch.hasAction("open_prop_shop") || ch.hasAction("open_barter_shop")) {
            return ICON_MARKET;
        }
        if (ch.hasAction("play_bard_song")
            || ch.hasAction("stop_bard_song")
            || ch.hasAction("start_bard_shuffle")
            || ch.hasAction("loop_current_bard_song")) {
            return ICON_MUSICAL_NOTE;
        }
        if (ch.getNext() != null
            && ("music_pick".equalsIgnoreCase(ch.getNext().trim())
                || "music_loop_pick".equalsIgnoreCase(ch.getNext().trim()))) {
            return ICON_MUSICAL_NOTE;
        }
        if (ch.hasAction("teleport_world")) {
            return ICON_TELEPORT;
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

    /**
     * Resolves an authored choice {@code icon} value: known aliases map to bundled UI assets; paths containing
     * {@code /} or ending in {@code .png} are used as-is.
     */
    @Nullable
    private static String resolveChoiceIcon(@Nullable String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }
        String trimmed = icon.trim();
        if (trimmed.contains("/") || trimmed.toLowerCase().endsWith(".png")) {
            return trimmed;
        }
        return switch (trimmed.toLowerCase()) {
            case "teleport" -> ICON_TELEPORT;
            case "gift" -> ICON_GIFT;
            case "quest" -> ICON_QUEST;
            case "quest_progress" -> ICON_QUEST_PROGRESS;
            case "exit" -> ICON_EXIT;
            case "follow" -> ICON_FOLLOW;
            case "jewelry" -> ICON_JEWELRY_APPRAISAL;
            case "repair" -> ICON_BLACKSMITH_REPAIR;
            case "geode" -> ICON_GEODE_OPEN;
            case "music" -> ICON_MUSICAL_NOTE;
            case "market" -> ICON_MARKET;
            default -> trimmed;
        };
    }

    private void applyChoiceIcon(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String rowSelector,
        @Nonnull DialogueChoiceDefinition ch,
        @Nonnull List<MaterialRequirement> itemRequirements
    ) {
        if (!itemRequirements.isEmpty() && DialogueChoiceItemRequirements.isTouristMoveInChoice(ch)) {
            applyChoiceIconPath(commandBuilder, rowSelector, ICON_QUEST);
            return;
        }
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
        List<DialogueChoiceDefinition> choices = node != null ? buildChoiceList(node, ref, store) : List.of();
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
            NpcFaceVisuals.playTalkBurst(npcRef, ref, store);
        }
        speechStartedForNodeId = null;
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
        TownRecord town = resolvePlayerTown(store, ref);
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
                || batch.isOpenTreeClimbLeaderboardAfterClose()
                || batch.isOpenHallowsEveLeaderboardAfterClose()
                || batch.isOpenMarketLeaderboardAfterClose()
                || batch.isOpenSnowballLeaderboardAfterClose()
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
            || batch.isOpenTreeClimbLeaderboardAfterClose()
            || batch.isOpenHallowsEveLeaderboardAfterClose()
            || batch.isOpenMarketLeaderboardAfterClose()
            || batch.isOpenSnowballLeaderboardAfterClose()
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
        } else if (world != null && batch.isOpenPropShopAfterClose() && batch.getOpenPropShopTownId() != null) {
            UUID townId = batch.getOpenPropShopTownId();
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
                player.getPageManager().openCustomPage(pref, st, new PropShopPage(pr, townId));
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
        } else if (world != null && batch.isOpenTreeClimbLeaderboardAfterClose()) {
            // Do not call close() before openCustomPage: setPage(None) increments
            // PageManager's custom-page ack counter, and openCustomPage increments again,
            // leaving Data events (Close) ignored until multiple client ACKs arrive.
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
                player.getPageManager().openCustomPage(pref, st, new TreeClimbLeaderboardPage(pr));
            });
        } else if (world != null && batch.isOpenHallowsEveLeaderboardAfterClose()) {
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
                player.getPageManager().openCustomPage(pref, st, new HallowsEveLeaderboardPage(pr));
            });
        } else if (world != null && batch.isOpenMarketLeaderboardAfterClose()) {
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
                player.getPageManager().openCustomPage(pref, st, new MarketLeaderboardPage(pr));
            });
        } else if (world != null && batch.isOpenSnowballLeaderboardAfterClose()) {
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
                player.getPageManager().openCustomPage(pref, st, new SnowballLeaderboardPage(pr));
            });
        } else if (world != null && batch.hasAfterClose()) {
            Runnable after = batch.getAfterClose();
            // Do not call close() before the follow-up CustomUI opens. setPage(None) increments
            // PageManager's custom-page ack counter, and openCustomPage increments again,
            // leaving Data events (choice clicks) ignored until multiple client ACKs arrive.
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
