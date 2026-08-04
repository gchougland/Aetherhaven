package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Scrollable guard picker for patrol route assignment. */
public final class PatrolWandAssignGuardPage extends AetherhavenInteractiveCustomUIPage<PatrolWandAssignGuardPage.PageData> {
    private static final String ROWS = "#ListScroll #Rows";

    private final UUID routeId;
    private final UUID townId;
    private boolean templateAppended;
    private boolean onlyUnassignedGuards;

    public PatrolWandAssignGuardPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID routeId,
        @Nonnull UUID townId
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.routeId = routeId;
        this.townId = townId;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PatrolWandAssignGuardPage.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyPatrolWandAssignGuardPage(commandBuilder);
        commandBuilder.clear(ROWS);

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            commandBuilder.set("#Hint.TextSpans", Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            commandBuilder.set("#Hint.TextSpans", Message.translation("aetherhaven_common.aetherhaven.common.townNotFound"));
            return;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        PatrolRouteRecord route = reg.get(routeId);
        if (route == null) {
            commandBuilder.set(
                "#Hint.TextSpans",
                Message.translation("aetherhaven_items.aetherhaven.patrolWand.routeMissing")
            );
            return;
        }

        commandBuilder.set("#OnlyUnassignedCheckbox #CheckBox.Value", onlyUnassignedGuards);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#OnlyUnassignedCheckbox #CheckBox",
            EventData.of("@OnlyUnassignedGuards", "#OnlyUnassignedCheckbox #CheckBox.Value"),
            false
        );

        List<PatrolGuardDirectory.PatrolGuardRow> allGuards = PatrolGuardDirectory.listGuards(store, town, plugin);
        List<PatrolGuardDirectory.PatrolGuardRow> guards = allGuards;
        if (onlyUnassignedGuards) {
            guards = new ArrayList<>();
            for (PatrolGuardDirectory.PatrolGuardRow g : allGuards) {
                if (reg.routesForGuard(g.entityUuid()).isEmpty()) {
                    guards.add(g);
                }
            }
        }

        if (allGuards.isEmpty()) {
            commandBuilder.set(
                "#Hint.TextSpans",
                Message.translation("aetherhaven_items.aetherhaven.patrolWand.assignPageNoGuards")
            );
        } else if (onlyUnassignedGuards && guards.isEmpty()) {
            commandBuilder.set(
                "#Hint.TextSpans",
                Message.translation("aetherhaven_items.aetherhaven.patrolWand.assignPageNoUnassignedGuards")
            );
        } else {
            commandBuilder.set(
                "#Hint.TextSpans",
                Message
                    .translation("aetherhaven_items.aetherhaven.patrolWand.assignPageHint")
                    .param("route", route.safeDisplayName())
            );
        }

        for (int i = 0; i < guards.size(); i++) {
            PatrolGuardDirectory.PatrolGuardRow g = guards.get(i);
            commandBuilder.append(ROWS, "Aetherhaven/PatrolWandAssignGuardRow.ui");
            String row = ROWS + "[" + i + "]";
            AetherhavenUiLocalization.applyPatrolWandAssignGuardRow(commandBuilder, row);
            commandBuilder.set(row + " #Portrait.AssetPath", g.portraitPath());
            commandBuilder.set(row + " #NameLine.TextSpans", Message.raw(g.displayName()));
            commandBuilder.set(row + " #TypeLine.TextSpans", Message.translation(g.guardTypeLangKey()));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #AssignButton",
                new EventData().append("Action", "Assign").append("GuardUuid", g.entityUuid().toString()),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.onlyUnassignedGuards != null) {
            onlyUnassignedGuards = data.onlyUnassignedGuards;
            UICommandBuilder cmd = new UICommandBuilder();
            UIEventBuilder ev = new UIEventBuilder();
            build(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
            return;
        }
        if (data.action == null || !"Assign".equalsIgnoreCase(data.action) || data.guardUuid == null || data.guardUuid.isBlank()) {
            return;
        }
        UUID guardUuid;
        try {
            guardUuid = UUID.fromString(data.guardUuid.trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null) {
            return;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        PatrolRouteRecord route = reg.get(routeId);
        if (route == null) {
            if (pr != null) {
                pr.sendMessage(Message.translation("aetherhaven_items.aetherhaven.patrolWand.routeMissing"));
            }
            close();
            return;
        }
        Ref<EntityStore> guardRef = store.getExternalData().getRefFromUUID(guardUuid);
        if (guardRef == null || !guardRef.isValid() || !PatrolWandInteractions.isValidGuardForRoute(guardRef, store, route)) {
            if (pr != null) {
                pr.sendMessage(Message.translation("aetherhaven_items.aetherhaven.patrolWand.invalidGuard"));
            }
            return;
        }
        PatrolWandInteractions.assignGuardToRoute(world, plugin, store, route, guardRef, guardUuid);
        if (pr != null) {
            pr.sendMessage(
                Message
                    .translation("aetherhaven_items.aetherhaven.patrolWand.assignedGuard")
                    .param("name", PatrolWandInteractions.guardDisplayName(store, guardRef))
                    .param("route", route.safeDisplayName())
            );
        }
        close();
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("GuardUuid", Codec.STRING), (d, v) -> d.guardUuid = v, d -> d.guardUuid)
            .add()
            .append(
                new KeyedCodec<>("@OnlyUnassignedGuards", Codec.BOOLEAN),
                (d, v) -> d.onlyUnassignedGuards = v,
                d -> d.onlyUnassignedGuards
            )
            .add()
            .build();

        @Nullable
        private String action;

        @Nullable
        private String guardUuid;

        @Nullable
        private Boolean onlyUnassignedGuards;
    }
}
