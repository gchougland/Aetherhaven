package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plotcreator.PlotBuildingKind;
import com.hexvane.aetherhaven.plotcreator.PlotBuildingKindRequirements;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorDraft;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorFestivalMechanicDefaults;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorFestivalNpcRoles;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorInteractions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorService;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSpotEntry;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSubstepType;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Multi-select important spots chooser for non-decoration plot creator builds. */
public final class PlotCreatorImportantSpotsPage
    extends AetherhavenInteractiveCustomUIPage<PlotCreatorImportantSpotsPage.PageData> {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";
    private static final String SPOT_ROWS = "#SpotRows";

    @Nonnull
    private final PlotCreatorSession session;
    @Nonnull
    private final List<PlotCreatorSpotEntry> working = new ArrayList<>();
    private boolean workingInitialized;
    private boolean templateAppended;

    public PlotCreatorImportantSpotsPage(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.session = session;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PlotCreatorImportantSpotsPage.ui");
            templateAppended = true;
        }
        bindFooterButtons(eventBuilder);
        ensureWorking();
        applyContent(commandBuilder, eventBuilder);
    }

    private void bindFooterButtons(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ConfirmButton",
            EventData.of("Action", "Confirm"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            EventData.of("Action", "Close"),
            false
        );
    }

    private void ensureWorking() {
        if (workingInitialized) {
            return;
        }
        workingInitialized = true;
        PlotCreatorDraft draft = session.getDraft();
        PlotCreatorService.seedImportantSpotsIfEmpty(draft);
        working.clear();
        working.addAll(draft.getSelectedSpots());
        ensureManagementInWorking();
    }

    private void ensureManagementInWorking() {
        if (session.getDraft().isFestivalMode()) {
            return;
        }
        for (PlotCreatorSpotEntry entry : working) {
            if (entry.type() == PlotCreatorSubstepType.MANAGEMENT_BLOCK) {
                return;
            }
        }
        working.add(0, PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1));
    }

    private void applyContent(@Nonnull UICommandBuilder b, @Nonnull UIEventBuilder eventBuilder) {
        b.set("#ImportantSpotsTitleText.TextSpans", Message.translation(MSG + ".step.IMPORTANT_SPOTS.title"));
        b.set("#StepHint.TextSpans", Message.translation(MSG + ".step.IMPORTANT_SPOTS.hint"));
        b.set("#ConfirmButton.TextSpans", Message.translation(MSG + ".button.done"));
        b.set("#CloseButton.TextSpans", Message.translation(MSG + ".button.close"));

        List<PlotCreatorSpotEntry> choosable = choosableSpots();
        b.clear(SPOT_ROWS);
        for (int i = 0; i < choosable.size(); i++) {
            PlotCreatorSpotEntry spot = choosable.get(i);
            b.append(SPOT_ROWS, "Aetherhaven/PlotCreatorToggleRow.ui");
            String row = SPOT_ROWS + "[" + i + "]";
            boolean locked = isLockedSpot(spot);
            boolean checked = locked || containsSpot(working, spot);
            b.set(row + " #Label.TextSpans", Message.translation(labelKey(spot)));
            b.set(row + " #Toggle.Value", checked);
            b.set(row + " #Toggle.Disabled", locked);
            if (!locked) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    row + " #Toggle",
                    new EventData()
                        .append("Action", "ToggleSpot")
                        .append("SpotKey", spotKey(spot))
                        .append("@Checked", row + " #Toggle.Value"),
                    false
                );
            }
        }
        b.set("#ConfirmButton.Disabled", false);
    }

    @Nonnull
    private List<PlotCreatorSpotEntry> choosableSpots() {
        LinkedHashSet<PlotCreatorSpotEntry> out = new LinkedHashSet<>();
        if (session.getDraft().isFestivalMode()) {
            for (String role :
                PlotBuildingKindRequirements.workplaceRolesForDraft(session.getDraft(), AetherhavenPlugin.get())) {
                out.add(PlotCreatorSpotEntry.work(role, 1));
            }
            for (String npcRole : PlotCreatorFestivalNpcRoles.mergeWithDraft(session.getDraft())) {
                out.add(PlotCreatorSpotEntry.festivalNpc(npcRole, 1));
            }
            out.add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT,
                    resolveMinCount(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT, 1))
                )
            );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_CENTERPIECE, 1));
            out.add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_RACE_LANE,
                    resolveMinCount(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_RACE_LANE, 1))
                )
            );
            out.add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_BALLOON_SPAWN,
                    resolveMinCount(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_BALLOON_SPAWN, 1))
                )
            );
            out.add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_WHACK_SPAWN,
                    resolveMinCount(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_WHACK_SPAWN, 1))
                )
            );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_WHEEL, 1));
            out.add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_TREE_CLIMB_START,
                    resolveMinCount(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_TREE_CLIMB_START, 1))
                )
            );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_TREE_CLIMB_FINISH, 1));
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_MAZE_START, 1));
            out.add(
                PlotCreatorSpotEntry.of(
                    PlotCreatorSubstepType.FESTIVAL_MAZE_ORB_SPAWN,
                    resolveMinCount(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_MAZE_ORB_SPAWN, 1))
                )
            );
            List<PlotCreatorSpotEntry> ranked = new ArrayList<>(out);
            for (int i = 0; i < ranked.size(); i++) {
                PlotCreatorSpotEntry spot = ranked.get(i);
                int min = resolveMinCount(spot);
                if (min != spot.minCount()) {
                    ranked.set(i, new PlotCreatorSpotEntry(spot.type(), min, spot.workResidentKind()));
                }
            }
            return ranked;
        }
        out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1));
        for (PlotCreatorSubstepType type : PlotCreatorSubstepType.values()) {
            if (type == PlotCreatorSubstepType.MANAGEMENT_BLOCK) {
                continue;
            }
            if (type == PlotCreatorSubstepType.WORK_POI) {
                // Role-specific work spots are added below from workplaceRolesForDraft.
                continue;
            }
            if (type == PlotCreatorSubstepType.BARD_WORK_POI) {
                continue;
            }
            // Older drafts could mark a planning desk POI; town halls now use elder work instead.
            if (type == PlotCreatorSubstepType.PLANNING_DESK_POI) {
                continue;
            }
            if (type.name().startsWith("FESTIVAL_")) {
                continue;
            }
            if (type == PlotCreatorSubstepType.GUILD_MASTER_SPAWN
                && !includesInnWithGuildHall(session.getDraft())) {
                continue;
            }
            out.add(PlotCreatorSpotEntry.of(type, 1));
        }
        List<String> roles =
            PlotBuildingKindRequirements.workplaceRolesForDraft(session.getDraft(), AetherhavenPlugin.get());
        if (roles.isEmpty()) {
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.WORK_POI, 1));
        }
        for (String role : roles) {
            if (TownVillagerBinding.KIND_BARD.equals(role)) {
                out.add(PlotCreatorSpotEntry.bard(1));
                continue;
            }
            out.add(PlotCreatorSpotEntry.work(role, 1));
        }
        List<PlotCreatorSpotEntry> ranked = new ArrayList<>(out);
        for (int i = 0; i < ranked.size(); i++) {
            PlotCreatorSpotEntry spot = ranked.get(i);
            int min = resolveMinCount(spot);
            if (min != spot.minCount()) {
                ranked.set(i, new PlotCreatorSpotEntry(spot.type(), min, spot.workResidentKind()));
            }
        }
        return ranked;
    }

    private int resolveMinCount(@Nonnull PlotCreatorSpotEntry spot) {
        for (PlotCreatorSpotEntry existing : working) {
            if (existing.equals(spot)) {
                return existing.minCount();
            }
        }
        for (PlotBuildingKindRequirements.SubstepRequirement req :
            PlotBuildingKindRequirements.defaultRequirements(session.getDraft(), AetherhavenPlugin.get())) {
            if (req.toSpotEntry().equals(spot)) {
                return req.minCount();
            }
        }
        return Math.max(1, spot.minCount());
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if ("ToggleSpot".equals(data.action) && data.spotKey != null) {
            PlotCreatorSpotEntry spot = parseSpotKey(data.spotKey);
            if (spot != null && !isLockedSpot(spot)) {
                boolean checked = Boolean.TRUE.equals(data.checked);
                if (checked) {
                    if (!containsSpot(working, spot)) {
                        working.add(new PlotCreatorSpotEntry(spot.type(), resolveMinCount(spot), spot.workResidentKind()));
                    }
                } else {
                    working.removeIf(e -> e.equals(spot));
                }
                ensureManagementInWorking();
                refreshIfOpen(ref, store);
            }
            return;
        }
        if ("Confirm".equals(data.action)) {
            confirmAndClose(ref, store);
            return;
        }
        if ("Close".equals(data.action)) {
            close(ref, store);
        }
    }

    private void confirmAndClose(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ensureManagementInWorking();
        PlotCreatorDraft draft = session.getDraft();
        draft.getSelectedSpots().clear();
        draft.getSelectedSpots().addAll(working);
        PlotCreatorService.ensureRequiredSpots(draft);
        draft.setImportantSpotsConfirmed(true);
        PlotCreatorService.advance(session, ref, store);
        if (!PlotCreatorService.stepAutoOpensPanel(session.getDraft().getStep())) {
            close(ref, store);
        }
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
    }

    private boolean isLockedSpot(@Nonnull PlotCreatorSpotEntry spot) {
        if (session.getDraft().isFestivalMode()) {
            for (PlotCreatorSpotEntry required :
                PlotCreatorFestivalMechanicDefaults.requiredSpotsForMechanic(session.getDraft())) {
                if (required.equals(spot)) {
                    return true;
                }
            }
            return false;
        }
        if (spot.type() == PlotCreatorSubstepType.MANAGEMENT_BLOCK) {
            return true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        List<PlotBuildingKind> kinds = PlotBuildingKindRequirements.effectiveKinds(session.getDraft(), plugin);
        boolean isShop = kinds.contains(PlotBuildingKind.SHOP) || kinds.contains(PlotBuildingKind.PLAYER_SHOP);
        boolean isPlayerShop = kinds.contains(PlotBuildingKind.PLAYER_SHOP)
            || PlotBuildingKindRequirements.requiresShopSafe(session.getDraft(), plugin);
        List<String> shopWorkRoles = isShop
            ? PlotBuildingKindRequirements.workplaceRolesForDraft(session.getDraft(), plugin)
            : List.of();
        String shopWorkRole = shopWorkRoles.isEmpty() ? null : shopWorkRoles.get(0);
        boolean isRequiredShopWork =
            isShop
                && spot.type() == PlotCreatorSubstepType.WORK_POI
                && (shopWorkRole == null || shopWorkRole.isBlank()
                    ? spot.workResidentKind() == null || spot.workResidentKind().isBlank()
                    : shopWorkRole.equals(spot.workResidentKind()));
        return (spot.type() == PlotCreatorSubstepType.INN_BELL_BLOCK && kinds.contains(PlotBuildingKind.INN))
            || (spot.type() == PlotCreatorSubstepType.GAIA_STATUE_BLOCK
                && PlotBuildingKindRequirements.requiresGaiaStatue(session.getDraft(), plugin))
            || (spot.isWorkRoleSpot()
                && TownVillagerBinding.KIND_PRIESTESS.equals(spot.workResidentKind())
                && PlotBuildingKindRequirements.requiresGaiaStatue(session.getDraft(), plugin))
            || isRequiredShopWork
            || (isShop
                && (spot.type() == PlotCreatorSubstepType.SHOP_POI
                    || spot.type() == PlotCreatorSubstepType.SHOP_SPOT
                    || spot.type() == PlotCreatorSubstepType.TOURIST_VISIT_POI))
            || (isPlayerShop && spot.type() == PlotCreatorSubstepType.SHOP_SAFE_BLOCK);
    }

    private void refreshIfOpen(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getPageManager().getCustomPage() != this) {
            return;
        }
        UICommandBuilder b = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        bindFooterButtons(events);
        applyContent(b, events);
        sendUpdate(b, events, false);
    }

    /** In-place refresh when the important spots step is re-entered without replacing the page. */
    public void refreshOpenPanel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        refreshIfOpen(ref, store);
    }

    private void close(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    @Nonnull
    private static String labelKey(@Nonnull PlotCreatorSpotEntry spot) {
        if (spot.isWorkRoleSpot() && spot.workResidentKind() != null) {
            return MSG + ".spot.workRole." + spot.workResidentKind().toLowerCase(Locale.ROOT);
        }
        if (spot.isFestivalNpcSpot() && spot.workResidentKind() != null) {
            return MSG
                + ".spot.festivalNpc."
                + PlotCreatorFestivalNpcRoles.labelLangSuffix(spot.workResidentKind());
        }
        return MSG + ".spot." + spot.type().name();
    }

    @Nonnull
    private static String spotKey(@Nonnull PlotCreatorSpotEntry spot) {
        if (spot.isWorkRoleSpot() && spot.workResidentKind() != null) {
            return "WORK_ROLE:" + spot.workResidentKind();
        }
        if (spot.isFestivalNpcSpot() && spot.workResidentKind() != null) {
            return "FESTIVAL_NPC:" + spot.workResidentKind();
        }
        return spot.type().name();
    }

    @Nullable
    private static PlotCreatorSpotEntry parseSpotKey(@Nonnull String key) {
        if (key.startsWith("FESTIVAL_NPC:")) {
            String role = key.substring("FESTIVAL_NPC:".length()).trim();
            return role.isEmpty() ? null : PlotCreatorSpotEntry.festivalNpc(role, 1);
        }
        if (key.startsWith("WORK_ROLE:")) {
            String role = key.substring("WORK_ROLE:".length()).trim();
            if (role.isEmpty()) {
                return null;
            }
            return PlotCreatorSpotEntry.work(role, 1);
        }
        if ("BARD_WORK_POI".equals(key)) {
            return PlotCreatorSpotEntry.bard(1);
        }
        PlotCreatorSubstepType type;
        try {
            type = PlotCreatorSubstepType.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return PlotCreatorSpotEntry.of(type, 1);
    }

    private static boolean containsSpot(@Nonnull List<PlotCreatorSpotEntry> list, @Nonnull PlotCreatorSpotEntry spot) {
        for (PlotCreatorSpotEntry entry : list) {
            if (entry.equals(spot)) {
                return true;
            }
        }
        return false;
    }

    private static boolean includesInnWithGuildHall(@Nonnull PlotCreatorDraft draft) {
        List<PlotBuildingKind> kinds =
            PlotBuildingKindRequirements.effectiveKinds(draft, AetherhavenPlugin.get());
        return kinds.contains(PlotBuildingKind.INN) && kinds.contains(PlotBuildingKind.GUILD_HALL);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("SpotKey", Codec.STRING), (d, v) -> d.spotKey = v, d -> d.spotKey)
            .add()
            .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (d, v) -> d.checked = v, d -> d.checked)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String spotKey;
        @Nullable
        private Boolean checked;
    }
}
