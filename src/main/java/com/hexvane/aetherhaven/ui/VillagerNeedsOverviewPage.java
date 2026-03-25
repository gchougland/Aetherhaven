package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagerNeedsOverviewPage extends InteractiveCustomUIPage<VillagerNeedsOverviewPage.PageData> {
    private static final int ROW_CAP = 5;

    private final UUID townId;
    private int selectedIndex;
    /** {@code append(ui)} must run only once per page instance; repeating it on every {@link #sendUpdate} duplicates the whole tree. */
    private boolean templateAppended;

    public VillagerNeedsOverviewPage(@Nonnull PlayerRef playerRef, @Nonnull UUID townId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/VillagerNeedsOverview.ui");
            templateAppended = true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("Aetherhaven not loaded."));
            hideRows(commandBuilder);
            return;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town == null) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("Town not found."));
            hideRows(commandBuilder);
            return;
        }

        List<VillagerRow> rows = new ArrayList<>();
        addRowIfPresent(rows, "Elder", town.getElderEntityUuid(), AetherhavenConstants.ELDER_NPC_ROLE_ID);
        addRowIfPresent(rows, "Innkeeper", town.getInnkeeperEntityUuid(), AetherhavenConstants.INNKEEPER_NPC_ROLE_ID);
        if (rows.isEmpty()) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("No tracked villagers in this town yet."));
            hideRows(commandBuilder);
            return;
        }
        if (selectedIndex >= rows.size()) {
            selectedIndex = 0;
        }

        commandBuilder.set("#Hint.TextSpans", Message.raw("Select a villager. Meters show loaded entities only (unloaded chunks show as neutral)."));
        for (int i = 0; i < ROW_CAP; i++) {
            if (i < rows.size()) {
                VillagerRow r = rows.get(i);
                commandBuilder.set("#Row" + i + ".Visible", true);
                commandBuilder.set("#Row" + i + " #Pick" + i + " #Label.TextSpans", Message.raw(r.label()));
            } else {
                commandBuilder.set("#Row" + i + ".Visible", false);
            }
        }

        VillagerRow sel = rows.get(selectedIndex);
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        VillagerNeeds needs = findNeeds(entityStore, sel.entityUuid());
        float hunger = needs != null ? needs.getHunger() / VillagerNeeds.MAX : 0.5f;
        float energy = needs != null ? needs.getEnergy() / VillagerNeeds.MAX : 0.5f;
        float fun = needs != null ? needs.getFun() / VillagerNeeds.MAX : 0.5f;
        commandBuilder.set("#HungerBar.Value", hunger);
        commandBuilder.set("#EnergyBar.Value", energy);
        commandBuilder.set("#FunBar.Value", fun);
        commandBuilder.set("#Portrait.AssetPath", NpcPortraitProvider.portraitPathForRoleId(sel.roleId()));

        for (int i = 0; i < rows.size(); i++) {
            final int idx = i;
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#Pick" + i,
                new EventData().append("Action", "Select").append("Index", Integer.toString(idx)),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null || !data.action.equalsIgnoreCase("Select")) {
            return;
        }
        if (data.index >= 0 && data.index < ROW_CAP) {
            selectedIndex = data.index;
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    private static void hideRows(@Nonnull UICommandBuilder commandBuilder) {
        for (int i = 0; i < ROW_CAP; i++) {
            commandBuilder.set("#Row" + i + ".Visible", false);
        }
    }

    private static void addRowIfPresent(
        @Nonnull List<VillagerRow> rows,
        @Nonnull String label,
        @Nullable UUID entityUuid,
        @Nonnull String roleId
    ) {
        if (entityUuid == null) {
            return;
        }
        rows.add(new VillagerRow(label, entityUuid, roleId));
    }

    @Nullable
    private static VillagerNeeds findNeeds(@Nonnull Store<EntityStore> store, @Nonnull UUID entityUuid) {
        VillagerNeeds[] found = new VillagerNeeds[1];
        store.forEachChunk(
            Query.and(VillagerNeeds.getComponentType(), UUIDComponent.getComponentType()),
            (archetypeChunk, commandBuffer) -> {
                if (found[0] != null) {
                    return;
                }
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    UUIDComponent u = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (u != null && entityUuid.equals(u.getUuid())) {
                        found[0] = archetypeChunk.getComponent(i, VillagerNeeds.getComponentType());
                        return;
                    }
                }
            }
        );
        return found[0];
    }

    private record VillagerRow(@Nonnull String label, @Nonnull UUID entityUuid, @Nonnull String roleId) {}

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("Index", Codec.STRING), (d, s) -> {
                if (s != null && !s.isBlank()) {
                    try {
                        d.index = Integer.parseInt(s.trim());
                    } catch (NumberFormatException ignored) {
                        d.index = 0;
                    }
                }
            }, d -> Integer.toString(d.index))
            .add()
            .build();

        private String action;
        private int index;
    }
}
