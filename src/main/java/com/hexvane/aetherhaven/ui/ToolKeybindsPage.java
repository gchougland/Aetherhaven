package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ToolKeybindsPage extends AetherhavenInteractiveCustomUIPage<ToolKeybindsPage.PageData> {
    private static final String MSG = "aetherhaven_ui_journal_items_tail.aetherhaven.ui.toolKeybinds";
    private static final String ROWS = "#KeybindRows";

    private boolean templateAppended;
    private boolean rowsBuilt;
    @Nullable
    private Message statusMessage;

    public ToolKeybindsPage(@Nonnull PlayerRef playerRef) {
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
            commandBuilder.append("Aetherhaven/ToolKeybindsPage.ui");
            templateAppended = true;
            AetherhavenUiLocalization.applyToolKeybindsPage(commandBuilder);
            wireEvents(eventBuilder);
        }
        applyDynamic(ref, commandBuilder, store);
    }

    private void wireEvents(@Nonnull UIEventBuilder eventBuilder) {
        EventData saveData = new EventData().append("Action", "Save");
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            String field = rowSelector(slot) + " #LabelField";
            saveData.append(slotFieldKey(slot), field + ".Value");
        }
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ToolKeybindsSaveButton", saveData, false);
        bind(eventBuilder, "#ToolKeybindsResetButton", "Reset");
        bind(eventBuilder, "#ToolKeybindsBackButton", "Back");
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            String field = rowSelector(slot) + " #LabelField";
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                field,
                new EventData().append("Action", "Preview").append(slotFieldKey(slot), field + ".Value"),
                false
            );
        }
    }

    private static void bind(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            selector,
            new EventData().append("Action", action),
            false
        );
    }

    private void applyDynamic(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        if (journal == null) {
            journal = new PlayerTownJournalState();
        }
        if (!rowsBuilt) {
            commandBuilder.clear(ROWS);
            for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
                String base = rowSelector(slot);
                commandBuilder.append(ROWS, "Aetherhaven/ToolKeybindRow.ui");
                commandBuilder.set(
                    base + " #SlotName.TextSpans",
                    Message.translation(MSG + ".slot." + slot.langSuffix() + ".name")
                );
                commandBuilder.set(
                    base + " #SlotDesc.TextSpans",
                    Message.translation(MSG + ".slot." + slot.langSuffix() + ".desc")
                );
            }
            rowsBuilt = true;
        }
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            String label = PlayerToolKeybindLabels.resolve(journal, slot);
            String base = rowSelector(slot);
            commandBuilder.set(base + " #LabelField.Value", label);
            commandBuilder.set(base + " #PreviewLabel.TextSpans", Message.raw(label));
        }
        commandBuilder.set(
            "#ToolKeybindsStatus.TextSpans",
            statusMessage != null ? statusMessage : Message.raw("")
        );
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "Preview" -> refreshPreview(ref, store, data);
            case "Save" -> save(ref, store, data);
            case "Reset" -> reset(ref, store);
            case "Back" -> openJournal(ref, store);
            default -> {}
        }
    }

    private void refreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        UICommandBuilder cmd = new UICommandBuilder();
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            String raw = data.labelFor(slot);
            if (raw == null) {
                continue;
            }
            String preview = PlayerToolKeybindLabels.sanitizeInput(raw, slot);
            String base = rowSelector(slot);
            cmd.set(base + " #PreviewLabel.TextSpans", Message.raw(preview));
        }
        sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private void save(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        if (journal == null) {
            journal = new PlayerTownJournalState();
        }
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            String raw = data.labelFor(slot);
            if (raw == null) {
                raw = PlayerToolKeybindLabels.resolve(journal, slot);
            }
            String sanitized = PlayerToolKeybindLabels.sanitizeInput(raw, slot);
            if (sanitized.equals(slot.defaultLabel())) {
                journal.setToolKeyLabel(slot, "");
            } else {
                journal.setToolKeyLabel(slot, sanitized);
            }
        }
        store.putComponent(ref, PlayerTownJournalState.getComponentType(), journal);
        ToolHudRefreshUtil.refreshActiveToolHuds(ref, store, playerRef);
        statusMessage = Message.translation(MSG + ".saved");
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        applyDynamic(ref, cmd, store);
        sendUpdate(cmd, ev, false);
    }

    private void reset(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        if (journal == null) {
            journal = new PlayerTownJournalState();
        }
        journal.resetToolKeyLabels();
        store.putComponent(ref, PlayerTownJournalState.getComponentType(), journal);
        ToolHudRefreshUtil.refreshActiveToolHuds(ref, store, playerRef);
        statusMessage = Message.translation(MSG + ".resetOk");
        rebuild();
    }

    private void openJournal(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        if (journal != null) {
            journal.setLastTab(PlayerTownJournalState.JournalTab.SETTINGS);
            journal.setLastSettingsSubTab(PlayerTownJournalState.SettingsSubTab.PERSONAL);
            store.putComponent(ref, PlayerTownJournalState.getComponentType(), journal);
        }
        player.getPageManager().openCustomPage(ref, store, new QuestJournalPage(playerRef));
    }

    @Nonnull
    private static String rowSelector(@Nonnull ToolKeybindSlot slot) {
        return ROWS + "[" + slot.ordinal() + "]";
    }

    @Nonnull
    private static String slotFieldKey(@Nonnull ToolKeybindSlot slot) {
        return "@" + slot.name();
    }

    public static final class PageData {
        static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("@PRIMARY", Codec.STRING), (d, v) -> d.primary = v, d -> d.primary)
                .add()
                .append(new KeyedCodec<>("@SECONDARY", Codec.STRING), (d, v) -> d.secondary = v, d -> d.secondary)
                .add()
                .append(new KeyedCodec<>("@USE", Codec.STRING), (d, v) -> d.use = v, d -> d.use)
                .add()
                .append(new KeyedCodec<>("@ABILITY1", Codec.STRING), (d, v) -> d.ability1 = v, d -> d.ability1)
                .add()
                .append(new KeyedCodec<>("@ABILITY2", Codec.STRING), (d, v) -> d.ability2 = v, d -> d.ability2)
                .add()
                .append(new KeyedCodec<>("@ABILITY3", Codec.STRING), (d, v) -> d.ability3 = v, d -> d.ability3)
                .add()
                .append(new KeyedCodec<>("@ESCAPE", Codec.STRING), (d, v) -> d.escape = v, d -> d.escape)
                .add()
                .append(new KeyedCodec<>("@SHIFT", Codec.STRING), (d, v) -> d.shift = v, d -> d.shift)
                .add()
                .append(new KeyedCodec<>("@CTRL", Codec.STRING), (d, v) -> d.ctrl = v, d -> d.ctrl)
                .add()
                .append(new KeyedCodec<>("@SPACE", Codec.STRING), (d, v) -> d.space = v, d -> d.space)
                .add()
                .append(new KeyedCodec<>("@MOVEMENT", Codec.STRING), (d, v) -> d.movement = v, d -> d.movement)
                .add()
                .build();

        private String action;
        private String primary;
        private String secondary;
        private String use;
        private String ability1;
        private String ability2;
        private String ability3;
        private String escape;
        private String shift;
        private String ctrl;
        private String space;
        private String movement;

        @Nullable
        String labelFor(@Nonnull ToolKeybindSlot slot) {
            return switch (slot) {
                case PRIMARY -> primary;
                case SECONDARY -> secondary;
                case USE -> use;
                case ABILITY1 -> ability1;
                case ABILITY2 -> ability2;
                case ABILITY3 -> ability3;
                case ESCAPE -> escape;
                case SHIFT -> shift;
                case CTRL -> ctrl;
                case SPACE -> space;
                case MOVEMENT -> movement;
            };
        }
    }
}
