package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.MaterialRequirement;import com.hexvane.aetherhaven.feast.FeastCatalog;
import com.hexvane.aetherhaven.feast.FeastDefinition;
import com.hexvane.aetherhaven.feast.FeastEffectKind;
import com.hexvane.aetherhaven.feast.FeastService;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
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
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FeastPage extends InteractiveCustomUIPage<FeastPage.PageData> {
    private static final List<FeastDefinition> ORDERED = FeastCatalog.ALL;

    private final int tableBlockX;
    private final int tableBlockY;
    private final int tableBlockZ;
    private boolean templateAppended;
    @Nullable
    private String selectedFeastId;

    public FeastPage(@Nonnull PlayerRef playerRef, int tableBlockX, int tableBlockY, int tableBlockZ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.tableBlockX = tableBlockX;
        this.tableBlockY = tableBlockY;
        this.tableBlockZ = tableBlockZ;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/Feasts.ui");
            templateAppended = true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || uc == null || pr == null) {
            commandBuilder.set("#RightTitle.TextSpans", Message.translation("server.aetherhaven.ui.feast.err.pluginNotLoaded"));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
        if (town == null) {
            commandBuilder.set("#RightTitle.TextSpans", Message.translation("server.aetherhaven.ui.feast.noTown"));
            commandBuilder.set("#ConfirmFeast.Disabled", true);
            return;
        }
        if (!town.playerCanManageConstructions(uc.getUuid())) {
            commandBuilder.set("#RightTitle.TextSpans", Message.translation("server.aetherhaven.ui.feast.noPermission"));
            commandBuilder.set("#ConfirmFeast.Disabled", true);
            return;
        }
        if (!tm.isInsideTerritory(town, tableBlockX, tableBlockZ)) {
            commandBuilder.set("#RightTitle.TextSpans", Message.translation("server.aetherhaven.ui.feast.outsideTerritory"));
            commandBuilder.set("#ConfirmFeast.Disabled", true);
            return;
        }

        long dawn = VillagerReputationService.currentGameEpochDay(store);
        FeastService.pruneExpiredActiveFeast(town, dawn);

        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        String language = pr.getLanguage();

        for (int i = 0; i < ORDERED.size(); i++) {
            FeastDefinition d = ORDERED.get(i);
            String pick = "#FeastPick" + i;
            boolean locked = !FeastCatalog.isFeastUnlocked(town, uc.getUuid(), store, d);
            if (locked) {
                commandBuilder.set(
                    pick + ".TooltipTextSpans",
                    Message.join(
                        Message.translation(d.titleTranslationKey()),
                        Message.raw("\n"),
                        Message.translation("server.aetherhaven.ui.feast.tooltipLocked")
                    )
                );
            } else {
                commandBuilder.set(pick + ".TooltipTextSpans", Message.translation(d.titleTranslationKey()));
            }
            commandBuilder.set("#Feast" + i + "Dim.Visible", locked);
            EventData sel = new EventData().append("Action", "Select").append("FeastId", d.id());
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, pick, sel, false);
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ConfirmFeast",
            new EventData().append("Action", "Confirm"),
            false
        );

        boolean timedActive = FeastService.hasActiveTimedFeast(town, dawn);
        int daysLeft = FeastService.timedFeastDaysRemaining(town, dawn);
        int berryCd = FeastService.berrycircleCooldownDaysRemaining(town, dawn);

        FeastDefinition selDef = selectedFeastId != null ? FeastCatalog.findById(selectedFeastId) : null;
        if (selDef == null) {
            commandBuilder.set("#RightTitle.TextSpans", Message.translation("server.aetherhaven.ui.feast.activeTitle"));
            if (timedActive) {
                String kid = town.getActiveFeastKind();
                FeastDefinition activeDef = kid != null ? FeastCatalog.findById(kid) : null;
                if (activeDef != null) {
                    commandBuilder.set(
                        "#RightBody.TextSpans",
                        Message.translation(activeDef.descriptionTranslationKey())
                    );
                    commandBuilder.set(
                        "#RightCosts.TextSpans",
                        Message.translation("server.aetherhaven.ui.feast.daysLeft").param("days", daysLeft)
                    );
                } else {
                    commandBuilder.set("#RightBody.TextSpans", Message.translation("server.aetherhaven.ui.feast.noneActive"));
                    commandBuilder.set("#RightCosts.TextSpans", Message.raw(""));
                }
            } else {
                commandBuilder.set("#RightBody.TextSpans", Message.translation("server.aetherhaven.ui.feast.noneActive"));
                if (berryCd > 0) {
                    commandBuilder.set(
                        "#RightCosts.TextSpans",
                        Message.translation("server.aetherhaven.ui.feast.cooldownBerrycircle").param("days", berryCd)
                    );
                } else {
                    commandBuilder.set("#RightCosts.TextSpans", Message.raw(""));
                }
            }
            commandBuilder.set("#ConfirmFeast.Disabled", true);
        } else {
            commandBuilder.set("#RightTitle.TextSpans", Message.translation(selDef.titleTranslationKey()));
            commandBuilder.set("#RightBody.TextSpans", Message.translation(selDef.descriptionTranslationKey()));
            boolean locked = !FeastCatalog.isFeastUnlocked(town, uc.getUuid(), store, selDef);
            commandBuilder.set(
                "#RightCosts.TextSpans",
                locked
                    ? Message.join(
                        Message.translation("server.aetherhaven.ui.feast.locked"),
                        Message.raw("\n\n"),
                        costMessage(language, inv, selDef)
                    )
                    : costMessage(language, inv, selDef)
            );
            boolean ingredients = inv != null && InventoryMaterials.hasAll(inv, selDef.costs());
            boolean conflict =
                (selDef.effectKind() != FeastEffectKind.BERRYCIRCLE_REP && timedActive)
                    || (selDef.effectKind() == FeastEffectKind.BERRYCIRCLE_REP && timedActive)
                    || (selDef.effectKind() == FeastEffectKind.BERRYCIRCLE_REP && FeastService.isBerrycircleOnCooldown(town, dawn));
            commandBuilder.set("#ConfirmFeast.Disabled", locked || !ingredients || conflict);
        }
    }

    @Nonnull
    private static Message costMessage(
        @Nullable String language,
        @Nullable CombinedItemContainer inv,
        @Nonnull FeastDefinition def
    ) {
        StringBuilder sb = new StringBuilder();
        for (MaterialRequirement m : def.costs()) {
            if (m.getCount() <= 0) {
                continue;
            }
            String label = UiMaterialLabels.materialLabelForUi(language, m);
            int have = inv != null ? InventoryMaterials.count(inv, m) : 0;
            sb.append(label).append(": ").append(have).append(" / ").append(m.getCount()).append("\n");
        }
        String lines = sb.toString().trim();
        if (lines.isEmpty()) {
            return Message.translation("server.aetherhaven.ui.feast.ingredientsLabel");
        }
        return Message.join(
            Message.translation("server.aetherhaven.ui.feast.ingredientsLabel"),
            Message.raw("\n"),
            Message.raw(lines)
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        String action = data.action;
        if (action == null) {
            return;
        }
        if (action.equalsIgnoreCase("Select")) {
            String fid = data.feastId;
            if (fid != null && !fid.isBlank()) {
                this.selectedFeastId = fid.trim();
            }
            refresh(ref, store);
            return;
        }
        if (!action.equalsIgnoreCase("Confirm")) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (uc == null || pr == null) {
            return;
        }
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
            if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            return;
        }
        FeastDefinition def = selectedFeastId != null ? FeastCatalog.findById(selectedFeastId) : null;
        if (def == null) {
            return;
        }
        String err =
            FeastService.tryBeginFeast(world, plugin, tm, town, ref, store, def.id(), tableBlockX, tableBlockY, tableBlockZ);
        if (err != null) {
            NotificationUtil.sendNotification(pr.getPacketHandler(), Message.translation(err), NotificationStyle.Danger);
        } else {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("server.aetherhaven.ui.feast.began"),
                NotificationStyle.Success
            );
            this.selectedFeastId = null;
        }
        refresh(ref, store);
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("FeastId", Codec.STRING), (d, v) -> d.feastId = v, d -> d.feastId)
                .add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String feastId;
    }
}
