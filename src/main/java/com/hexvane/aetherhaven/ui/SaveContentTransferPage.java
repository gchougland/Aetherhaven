package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.command.AetherhavenTransferCommand;
import com.hexvane.aetherhaven.transfer.SaveContentTransfer;
import com.hexvane.aetherhaven.transfer.SaveContentTransfer.Category;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public final class SaveContentTransferPage extends AetherhavenInteractiveCustomUIPage<SaveContentTransferPage.Data> {
    private static final String MSG = "aetherhaven_transfer.aetherhaven.transfer.";
    private final Path source;
    private final List<SaveContentTransfer.Target> targets;
    private final List<SaveContentTransfer.Bundle> inventory;
    private final EnumSet<Category> selected = EnumSet.of(Category.PROPS, Category.BUILDINGS);
    private boolean overwrite, confirming, busy, completed;
    private int targetIndex;
    private Message status = Message.empty();

    public SaveContentTransferPage(PlayerRef player, Path source, List<SaveContentTransfer.Target> targets,
                                   List<SaveContentTransfer.Bundle> inventory) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.source = source;
        this.targets = List.copyOf(targets);
        this.inventory = List.copyOf(inventory);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder b, @Nonnull UIEventBuilder e,
                      @Nonnull Store<EntityStore> store) {
        {
            b.append("Aetherhaven/SaveContentTransferPage.ui");
            for (String action : List.of("Review", "Confirm", "Back", "Close"))
                e.addEventBinding(CustomUIEventBindingType.Activating, "#" + action,
                    EventData.of("Action", action), false);
            e.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Destination", EventData.of("@Target", "#Destination.Value"), false);
            for (String field : List.of("Props", "Buildings", "Config", "Overwrite"))
                e.addEventBinding(CustomUIEventBindingType.ValueChanged, "#" + field,
                    EventData.of("@" + field, "#" + field + ".Value"), false);
            ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
            for (int i = 0; i < targets.size(); i++)
                entries.add(new DropdownEntryInfo(LocalizableString.fromString(targets.get(i).name()), Integer.toString(i)));
            b.set("#Destination.Entries", entries);
            if (!targets.isEmpty()) b.set("#Destination.Value", Integer.toString(targetIndex));
            b.set("#TransferTitle.TextSpans", Message.translation(MSG + "title"));
            for (String id : List.of("Intro", "DestinationLabel", "OverwriteLabel", "Review", "Confirm", "Back", "Close"))
                b.set("#" + id + ".TextSpans", Message.translation(MSG + id.toLowerCase(Locale.ROOT)));
            for (Category category : Category.values()) {
                String name = switch (category) { case PROPS -> "Props"; case BUILDINGS -> "Buildings"; case CONFIG -> "Config"; };
                long count = inventory.stream().filter(bundle -> bundle.category() == category).count();
                b.set("#" + name + "Label.TextSpans", Message.translation(MSG + name.toLowerCase(Locale.ROOT)).param("count", count));
                b.set("#" + name + ".Value", selected.contains(category));
            }
            b.set("#Overwrite.Value", overwrite);
        }
        b.set("#Options.Visible", !confirming && !completed);
        b.set("#Review.Visible", !confirming && !completed);
        b.set("#Review.Disabled", targets.isEmpty() || selected.isEmpty() || busy);
        b.set("#Confirmation.Visible", confirming);
        b.set("#Confirm.Disabled", busy);
        b.set("#Back.Disabled", busy);
        if (confirming) {
            long count = inventory.stream().filter(bundle -> selected.contains(bundle.category())).count();
            b.set("#ConfirmationText.TextSpans", Message.translation(MSG + (overwrite ? "confirmOverwrite" : "confirmKeep"))
                .param("save", targets.get(targetIndex).name()).param("count", count));
        }
        b.set("#Status.TextSpans", targets.isEmpty() ? Message.translation(MSG + "noSaves") : status);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if ("Close".equals(data.action)) { close(); return; }
        if (busy || completed || !AetherhavenTransferCommand.isLocalOwner(playerRef)) return;
        if (!confirming) {
            if (data.target != null) {
                try { int i = Integer.parseInt(data.target); if (i >= 0 && i < targets.size()) targetIndex = i; }
                catch (NumberFormatException ignored) { }
            }
            update(Category.PROPS, data.props); update(Category.BUILDINGS, data.buildings); update(Category.CONFIG, data.config);
            if (data.overwrite != null) overwrite = data.overwrite;
        }
        if ("Review".equals(data.action) && !targets.isEmpty() && !selected.isEmpty()) confirming = true;
        else if ("Back".equals(data.action)) confirming = false;
        else if ("Confirm".equals(data.action) && confirming) {
            busy = true;
            status = Message.translation(MSG + "copying");
            var categories = EnumSet.copyOf(selected);
            var target = targets.get(targetIndex);
            boolean replace = overwrite;
            var world = store.getExternalData().getWorld();
            CompletableFuture.supplyAsync(() -> {
                try {
                    if (!AetherhavenTransferCommand.isLocalOwner(playerRef)) return Message.translation(MSG + "localOnly");
                    var result = SaveContentTransfer.transfer(source, target, categories, replace);
                    return Message.translation(MSG + "result").param("copied", result.copied())
                        .param("skipped", result.skipped()).param("failed", result.failed());
                } catch (Exception ex) { return Message.translation(MSG + "failed").param("reason", ex.getMessage()); }
            }).thenAccept(message -> world.execute(() -> {
                busy = false; confirming = false; completed = true; status = message;
                playerRef.sendMessage(message);
                if (ref.isValid()) rebuild();
            }));
        }
        rebuild();
    }

    private void update(Category category, Boolean enabled) {
        if (enabled == null) return;
        if (enabled) selected.add(category); else selected.remove(category);
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d,v) -> d.action=v, d -> d.action).add()
            .append(new KeyedCodec<>("@Target", Codec.STRING), (d,v) -> d.target=v, d -> d.target).add()
            .append(new KeyedCodec<>("@Props", Codec.BOOLEAN), (d,v) -> d.props=v, d -> d.props).add()
            .append(new KeyedCodec<>("@Buildings", Codec.BOOLEAN), (d,v) -> d.buildings=v, d -> d.buildings).add()
            .append(new KeyedCodec<>("@Config", Codec.BOOLEAN), (d,v) -> d.config=v, d -> d.config).add()
            .append(new KeyedCodec<>("@Overwrite", Codec.BOOLEAN), (d,v) -> d.overwrite=v, d -> d.overwrite).add().build();
        private String action, target;
        private Boolean props, buildings, config, overwrite;
    }
}
