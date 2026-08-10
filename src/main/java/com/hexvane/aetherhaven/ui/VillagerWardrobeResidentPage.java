package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villagercosmetic.WardrobeResidentDirectory;
import com.hexvane.aetherhaven.villagercosmetic.WardrobeResidentDirectory.WardrobeResidentRow;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Lists town residents for the villager wardrobe. */
public final class VillagerWardrobeResidentPage extends AetherhavenInteractiveCustomUIPage<VillagerWardrobeResidentPage.PageData> {
    private static final String LIST = "#ResidentList";
    private static final String LANG = "aetherhaven_villager_cosmetics.aetherhaven.ui.villagerWardrobe";

    private final UUID townId;
    private final int blockX;
    private final int blockY;
    private final int blockZ;

    public VillagerWardrobeResidentPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        int blockX,
        int blockY,
        int blockZ
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        // rebuild() clears the client tree, so the template must be appended on every build.
        commandBuilder.append("Aetherhaven/VillagerWardrobeResident.ui");
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#WardrobeCloseButton",
            EventData.of("Action", "Close"),
            false
        );
        commandBuilder.set("#WardrobeTitleText.TextSpans", Message.translation(LANG + ".title"));
        commandBuilder.set("#Hint.TextSpans", Message.translation(LANG + ".pickResident"));
        commandBuilder.set("#WardrobeCloseButton.TextSpans", Message.translation(LANG + ".close"));

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        commandBuilder.clear(LIST);
        if (plugin == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return;
        }
        List<WardrobeResidentRow> rows = WardrobeResidentDirectory.list(store, town, plugin);
        int i = 0;
        for (WardrobeResidentRow row : rows) {
            commandBuilder.append(LIST, "Aetherhaven/VillagerWardrobeResidentRow.ui");
            String path = LIST + "[" + i + "]";
            commandBuilder.set(path + " #Portrait.AssetPath", row.portraitPath());
            commandBuilder.set(path + " #NameLine.TextSpans", Message.raw(row.label()));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                path,
                new EventData()
                    .append("Action", "Select")
                    .append("EntityUuid", row.entityUuid().toString())
                    .append("ResidentKey", row.residentKey())
                    .append("ModelAssetId", row.modelAssetId() != null ? row.modelAssetId() : "")
                    .append("DisplayName", row.label()),
                false
            );
            i++;
        }
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
        if ("Close".equals(data.action)) {
            close();
            return;
        }
        if (!"Select".equals(data.action) || data.entityUuid == null || data.residentKey == null) {
            return;
        }
        UUID entityUuid;
        try {
            entityUuid = UUID.fromString(data.entityUuid);
        } catch (IllegalArgumentException e) {
            return;
        }
        String displayName = data.displayName != null ? data.displayName : "";
        String modelAssetId = data.modelAssetId != null ? data.modelAssetId : "";
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player
            .getPageManager()
            .openCustomPage(
                ref,
                store,
                new VillagerWardrobeCustomizePage(
                    playerRef,
                    townId,
                    blockX,
                    blockY,
                    blockZ,
                    entityUuid,
                    data.residentKey,
                    displayName,
                    modelAssetId
                )
            );
    }

    public static final class PageData {
        @Nullable
        public String action;
        @Nullable
        public String entityUuid;
        @Nullable
        public String residentKey;
        @Nullable
        public String modelAssetId;
        @Nullable
        public String displayName;

        @Nonnull
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action)
                .add()
                .append(new KeyedCodec<>("EntityUuid", Codec.STRING), (o, v) -> o.entityUuid = v, o -> o.entityUuid)
                .add()
                .append(new KeyedCodec<>("ResidentKey", Codec.STRING), (o, v) -> o.residentKey = v, o -> o.residentKey)
                .add()
                .append(new KeyedCodec<>("ModelAssetId", Codec.STRING), (o, v) -> o.modelAssetId = v, o -> o.modelAssetId)
                .add()
                .append(new KeyedCodec<>("DisplayName", Codec.STRING), (o, v) -> o.displayName = v, o -> o.displayName)
                .add()
                .build();
    }
}
