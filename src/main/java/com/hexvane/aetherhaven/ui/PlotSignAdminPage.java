package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.bson.BsonDocument;

public final class PlotSignAdminPage extends InteractiveCustomUIPage<PlotSignAdminPage.PageData> {
    public PlotSignAdminPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/PlotSignAdminPage.ui");
        AetherhavenUiLocalization.applyPlotSignAdmin(commandBuilder);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        List<ConstructionDefinition> defs = plugin != null ? plugin.getConstructionCatalog().list() : List.of();

        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        String firstId = "";
        for (ConstructionDefinition d : defs) {
            if (firstId.isEmpty()) {
                firstId = d.getId();
            }
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(d.getDisplayName()), d.getId()));
        }
        if (entries.isEmpty()) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString("(no constructions)"), ""));
        } else {
            firstId = defs.get(0).getId();
        }

        commandBuilder.set("#Construction #Input.Entries", entries);
        commandBuilder.set("#Construction #Input.Value", firstId);

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#GiveButton",
            new EventData().append("Action", "Give").append("@ConstructionId", "#Construction #Input.Value"),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null || !data.action.equalsIgnoreCase("Give")) {
            return;
        }
        String constructionId = data.constructionId != null ? data.constructionId : "";
        if (constructionId.isEmpty()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || plugin.getConstructionCatalog().get(constructionId) == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
        holder.addComponent(PlotSignBlock.getComponentType(), new PlotSignBlock(constructionId, java.util.UUID.randomUUID().toString()));
        BsonDocument blockHolderDoc = ChunkStore.REGISTRY.getEntityCodec().encode(holder, new ExtraInfo()).asDocument();
        ItemStack stack =
            new ItemStack(AetherhavenConstants.PLOT_SIGN_ITEM_ID, 1)
                .withMetadata(AetherhavenConstants.ITEM_METADATA_BLOCK_HOLDER, blockHolderDoc);
        ItemStackTransaction tx = player.getInventory().getCombinedHotbarFirst().addItemStack(stack);
        if (!tx.succeeded()) {
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.plotSign.couldNotAdd"));
            }
            return;
        }
        close();
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@ConstructionId", Codec.STRING), (d, s) -> d.constructionId = s, d -> d.constructionId)
            .add()
            .build();

        private String action;
        private String constructionId;
    }
}
