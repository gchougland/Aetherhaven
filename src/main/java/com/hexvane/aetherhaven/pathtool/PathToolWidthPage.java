package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.PathToolStyleDefinition;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.ui.AetherhavenUiLocalization;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Sets path width with a slider and style preview grid. */
public final class PathToolWidthPage extends AetherhavenInteractiveCustomUIPage<PathToolWidthPage.PageData> {
    private static final String PREVIEW_ROWS = "#PreviewRows";
    private static final int PREVIEW_SAMPLE_ROWS = 3;

    private boolean templateAppended;
    private int widthBlocks = 3;
    private int styleIndex;

    public PathToolWidthPage(@Nonnull PlayerRef playerRef) {
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
            commandBuilder.append("Aetherhaven/PathToolWidthPage.ui");
            AetherhavenUiLocalization.applyPathToolWidthPage(commandBuilder);
            templateAppended = true;
            PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
            if (st != null) {
                widthBlocks = st.getPathWidthBlocks();
                styleIndex = st.getPathStyleIndex();
            }
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#WidthSlider",
                EventData.of("@Width", "#WidthSlider.Value"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ApplyButton",
                EventData.of("Action", "Apply"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CancelButton",
                EventData.of("Action", "Cancel"),
                false
            );
        }
        applyDynamic(ref, store, commandBuilder);
    }

    private void applyDynamic(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder b
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
        if (st != null) {
            styleIndex = st.getPathStyleIndex();
            st.clampPathStyleIndex(cfg.getPathToolStyleDefinitions().size());
        }
        widthBlocks = Math.max(1, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, widthBlocks));
        b.set("#WidthSlider.Value", widthBlocks);
        b.set(
            "#WidthValue.TextSpans",
            Message.translation("aetherhaven_items.aetherhaven.pathTool.widthPageValue").param("w", String.valueOf(widthBlocks))
        );
        b.clear(PREVIEW_ROWS);
        for (int row = 0; row < PREVIEW_SAMPLE_ROWS; row++) {
            b.append(PREVIEW_ROWS, "Aetherhaven/PathToolWidthPreviewRow.ui");
            String cells = PREVIEW_ROWS + "[" + row + "] #Cells";
            Random random = new Random(PathToolWidthPreviewHelper.previewSeed(styleIndex, widthBlocks, row));
            for (int lat = 0; lat < widthBlocks; lat++) {
                b.append(cells, "Aetherhaven/PathToolWidthPreviewCell.ui");
                String cell = cells + "[" + lat + "]";
                String blockId =
                    PathToolWidthPreviewHelper.blockIdForPreviewCell(cfg, styleIndex, widthBlocks, lat, random);
                @Nullable
                String icon = PathToolWidthPreviewHelper.assetPathForBlockId(blockId);
                b.set(cell + ".Visible", icon != null && !icon.isBlank());
                if (icon != null && !icon.isBlank()) {
                    b.set(cell + " #Icon.AssetPath", icon);
                }
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.width != null) {
            widthBlocks = Math.max(1, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, (int) Math.round(data.width)));
            UICommandBuilder cmd = new UICommandBuilder();
            applyDynamic(ref, store, cmd);
            sendUpdate(cmd, null, false);
            return;
        }
        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "Cancel" -> close();
            case "Apply" -> {
                PathToolPlayerComponent st = store.getComponent(ref, PathToolPlayerComponent.getComponentType());
                if (st != null) {
                    st.setPathWidthBlocks(widthBlocks);
                }
                close();
            }
            default -> {}
        }
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PathToolWidthPage(playerRef));
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@Width", Codec.DOUBLE), (d, v) -> d.width = v, d -> d.width)
            .add()
            .build();

        @Nullable
        String action;
        @Nullable
        Double width;
    }
}

