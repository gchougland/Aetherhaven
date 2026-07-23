package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plotcreator.PlotBuildingKind;
import com.hexvane.aetherhaven.plotcreator.PlotBuildingKindRequirements;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorDraft;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorMainConstructions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorMaterialsActions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorService;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorStep;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorInteractions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSessions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorValidator;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotCreatorWizardPage extends AetherhavenInteractiveCustomUIPage<PlotCreatorWizardPage.PageData> {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";

    @Nonnull
    private final PlotCreatorSession session;
    private final boolean configPanelOnly;
    private final boolean kindPanelOnly;
    private final boolean configurePanelOnly;
    private boolean templateAppended;

    public PlotCreatorWizardPage(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
        this(playerRef, session, false, false, false);
    }

    public PlotCreatorWizardPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorSession session,
        boolean configPanelOnly
    ) {
        this(playerRef, session, configPanelOnly, false, false);
    }

    public PlotCreatorWizardPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorSession session,
        boolean configPanelOnly,
        boolean kindPanelOnly
    ) {
        this(playerRef, session, configPanelOnly, kindPanelOnly, false);
    }

    public PlotCreatorWizardPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorSession session,
        boolean configPanelOnly,
        boolean kindPanelOnly,
        boolean configurePanelOnly
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.session = session;
        this.configPanelOnly = configPanelOnly;
        this.kindPanelOnly = kindPanelOnly;
        this.configurePanelOnly = configurePanelOnly;
    }

    @Nonnull
    public static PlotCreatorWizardPage kindPanel(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
        return new PlotCreatorWizardPage(playerRef, session, false, true, false);
    }

    @Nonnull
    public static PlotCreatorWizardPage configurePanel(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
        return new PlotCreatorWizardPage(playerRef, session, false, false, true);
    }

    public boolean isConfigPanelOnly() {
        return configPanelOnly;
    }

    public boolean isKindPanelOnly() {
        return kindPanelOnly;
    }

    public boolean isConfigurePanelOnly() {
        return configurePanelOnly;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/PlotCreatorWizardPage.ui");
            templateAppended = true;
            wireEvents(eventBuilder);
        }
        applyLabels(commandBuilder);
        applyVisibility(commandBuilder);
        applyFields(commandBuilder);
        applyCheckLists(commandBuilder, eventBuilder);
        PlotCreatorService.refreshWireframe(session, playerRef);
    }

    private void wireEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#NextButton",
            new EventData()
                .append("Action", "Next")
                .append("@GoldCost", "#GoldCostField.Value")
                .append("@SelfBuildDays", "#SelfBuildDaysField.Value")
                .append("@MaxHomeResidents", "#MaxHomeResidentsField.Value")
                .append("@SaveEmptySpaces", "#SaveEmptySpacesToggle.Value")
                .append("@PreserveWater", "#PreserveWaterToggle.Value")
                .append("@TouristDestination", "#TouristDestinationToggle.Value")
                .append("@PlotTokenLocked", "#PlotTokenLockedToggle.Value")
                .append("@FloatingGiftBlueprint", "#FloatingGiftBlueprintToggle.Value")
                .append("@SubmitToCommunity", "#SubmitToCommunityToggle.Value")
                .append("@StyleId", "#StyleIdField.Value"),
            false
        );
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", EventData.of("Action", "Back"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", EventData.of("Action", "Cancel"), false);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#OpenMaterialsButton",
            EventData.of("Action", "OpenMaterials"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#FillFromBuildShapeButton",
            EventData.of("Action", "FillFromBuildShape"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#MaterialsPrevPageButton",
            EventData.of("Action", "MaterialsPrevPage"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#MaterialsNextPageButton",
            EventData.of("Action", "MaterialsNextPage"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#DisplayNameField",
            EventData.of("@DisplayName", "#DisplayNameField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#DescriptionField",
            EventData.of("@Description", "#DescriptionField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ConstructionIdField",
            EventData.of("@ConstructionId", "#ConstructionIdField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#PrefabNameField",
            EventData.of("@PrefabName", "#PrefabNameField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#TagsField",
            EventData.of("@Tags", "#TagsField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#GoldCostField",
            EventData.of("@GoldCost", "#GoldCostField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SelfBuildDaysField",
            EventData.of("@SelfBuildDays", "#SelfBuildDaysField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#MaxHomeResidentsField",
            EventData.of("@MaxHomeResidents", "#MaxHomeResidentsField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SaveEmptySpacesToggle",
            EventData.of("@SaveEmptySpaces", "#SaveEmptySpacesToggle.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#PreserveWaterToggle",
            EventData.of("@PreserveWater", "#PreserveWaterToggle.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#TouristDestinationToggle",
            EventData.of("@TouristDestination", "#TouristDestinationToggle.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#PlotTokenLockedToggle",
            EventData.of("@PlotTokenLocked", "#PlotTokenLockedToggle.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#FloatingGiftBlueprintToggle",
            EventData.of("@FloatingGiftBlueprint", "#FloatingGiftBlueprintToggle.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SubmitToCommunityToggle",
            EventData.of("@SubmitToCommunity", "#SubmitToCommunityToggle.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#StyleIdField",
            EventData.of("@StyleId", "#StyleIdField.Value"),
            false
        );
    }

    private void applyLabels(@Nonnull UICommandBuilder b) {
        PlotCreatorStep step = session.getDraft().getStep();
        PlotCreatorStep labelStep =
            kindPanelOnly ? PlotCreatorStep.KIND : configurePanelOnly ? PlotCreatorStep.CONFIGURE : step;
        b.set("#PlotCreatorTitleText.TextSpans", Message.translation(MSG + ".step." + labelStep.name() + ".title"));
        b.set("#StepHint.TextSpans", Message.translation(MSG + ".step." + labelStep.name() + ".hint"));
        boolean subPanel = configPanelOnly || kindPanelOnly || configurePanelOnly;
        b.set("#BackButton.Visible", !subPanel);
        b.set("#BackButton.TextSpans", Message.translation(MSG + ".button.back"));
        b.set(
            "#CancelButton.TextSpans",
            Message.translation(subPanel ? MSG + ".button.close" : MSG + ".button.cancel")
        );
        String nextKey =
            subPanel ? "done" : step == PlotCreatorStep.REVIEW ? "save" : step == PlotCreatorStep.DONE ? "close" : "next";
        b.set("#NextButton.TextSpans", Message.translation(MSG + ".button." + nextKey));
        b.set("#DisplayNameField.PlaceholderText", Message.translation(MSG + ".field.displayName"));
        b.set("#DescriptionField.PlaceholderText", Message.translation(MSG + ".field.description"));
        b.set("#ConstructionIdField.PlaceholderText", Message.translation(MSG + ".field.constructionId"));
        b.set("#PrefabNameField.PlaceholderText", Message.translation(MSG + ".field.prefabName"));
        b.set("#TagsField.PlaceholderText", Message.translation(MSG + ".field.tags"));
        b.set("#GoldCostLabel.TextSpans", Message.translation(MSG + ".field.goldCost"));
        b.set("#GoldCostField.PlaceholderText", Message.translation(MSG + ".field.goldCost"));
        b.set("#SelfBuildDaysLabel.TextSpans", Message.translation(MSG + ".field.selfBuildDays"));
        b.set("#SelfBuildDaysField.PlaceholderText", Message.translation(MSG + ".field.selfBuildDays"));
        b.set("#MaxHomeResidentsLabel.TextSpans", Message.translation(MSG + ".field.maxHomeResidents"));
        b.set("#MaxHomeResidentsField.PlaceholderText", Message.translation(MSG + ".field.maxHomeResidents"));
        b.set("#SaveEmptySpacesLabel.TextSpans", Message.translation(MSG + ".field.saveEmptySpaces"));
        b.set("#SaveEmptySpacesHint.TextSpans", Message.translation(MSG + ".field.saveEmptySpaces.hint"));
        b.set("#PreserveWaterLabel.TextSpans", Message.translation(MSG + ".field.preserveWater"));
        b.set("#PreserveWaterHint.TextSpans", Message.translation(MSG + ".field.preserveWater.hint"));
        b.set("#TouristDestinationLabel.TextSpans", Message.translation(MSG + ".field.touristDestination"));
        b.set("#TouristDestinationHint.TextSpans", Message.translation(MSG + ".field.touristDestination.hint"));
        b.set("#PlotTokenLockedLabel.TextSpans", Message.translation(MSG + ".field.plotTokenLocked"));
        b.set("#PlotTokenLockedHint.TextSpans", Message.translation(MSG + ".field.plotTokenLocked.hint"));
        b.set("#FloatingGiftBlueprintLabel.TextSpans", Message.translation(MSG + ".field.floatingGiftBlueprint"));
        b.set("#FloatingGiftBlueprintHint.TextSpans", Message.translation(MSG + ".field.floatingGiftBlueprint.hint"));
        b.set("#SubmitToCommunityLabel.TextSpans", Message.translation(MSG + ".field.submitToCommunity"));
        b.set("#StyleIdLabel.TextSpans", Message.translation(MSG + ".field.styleId"));
        b.set("#StyleIdField.PlaceholderText", Message.translation(MSG + ".field.styleId.hint"));
    }

    private void applyVisibility(@Nonnull UICommandBuilder b) {
        PlotCreatorStep step = session.getDraft().getStep();
        if (kindPanelOnly) {
            b.set("#DisplayNameField.Visible", false);
            b.set("#DescriptionField.Visible", false);
            b.set("#ConstructionIdField.Visible", false);
            b.set("#PrefabNameField.Visible", false);
            b.set("#KindDropdown.Visible", false);
            b.set("#KindCheckScroll.Visible", true);
            b.set("#TagsField.Visible", false);
            b.set("#VariantOfDropdown.Visible", false);
            b.set("#VariantCheckScroll.Visible", false);
            b.set("#GoldCostLabel.Visible", false);
            b.set("#GoldCostField.Visible", false);
            b.set("#SelfBuildDaysLabel.Visible", false);
            b.set("#SelfBuildDaysField.Visible", false);
            b.set("#MaxHomeResidentsLabel.Visible", false);
            b.set("#MaxHomeResidentsField.Visible", false);
            b.set("#SaveEmptySpacesRow.Visible", false);
            b.set("#SaveEmptySpacesHint.Visible", false);
            b.set("#PreserveWaterRow.Visible", false);
            b.set("#PreserveWaterHint.Visible", false);
            b.set("#TouristDestinationRow.Visible", false);
            b.set("#TouristDestinationHint.Visible", false);
            b.set("#PlotTokenLockedRow.Visible", false);
            b.set("#PlotTokenLockedHint.Visible", false);
            b.set("#FloatingGiftBlueprintRow.Visible", false);
            b.set("#FloatingGiftBlueprintHint.Visible", false);
            b.set("#StyleIdLabel.Visible", false);
            b.set("#StyleIdField.Visible", false);
            b.set("#OpenMaterialsButton.Visible", false);
            b.set("#FillFromBuildShapeButton.Visible", false);
            b.set("#MaterialsPageLabel.Visible", false);
            b.set("#MaterialsPageRow.Visible", false);
            b.set("#ReviewSummary.Visible", false);
            b.set("#DetailHint.Visible", false);
            b.set("#SubmitToCommunityRow.Visible", false);
            return;
        }
        if (configurePanelOnly) {
            boolean homeKind =
                PlotBuildingKindRequirements.effectiveKinds(session.getDraft(), AetherhavenPlugin.get())
                    .contains(PlotBuildingKind.HOME);
            b.set("#DisplayNameField.Visible", false);
            b.set("#DescriptionField.Visible", false);
            b.set("#ConstructionIdField.Visible", false);
            b.set("#PrefabNameField.Visible", false);
            b.set("#KindDropdown.Visible", false);
            b.set("#KindCheckScroll.Visible", false);
            b.set("#TagsField.Visible", false);
            b.set("#VariantOfDropdown.Visible", false);
            b.set("#VariantCheckScroll.Visible", false);
            b.set("#GoldCostLabel.Visible", true);
            b.set("#GoldCostField.Visible", true);
            b.set("#SelfBuildDaysLabel.Visible", true);
            b.set("#SelfBuildDaysField.Visible", true);
            b.set("#MaxHomeResidentsLabel.Visible", homeKind);
            b.set("#MaxHomeResidentsField.Visible", homeKind);
            b.set("#SaveEmptySpacesRow.Visible", true);
            b.set("#SaveEmptySpacesHint.Visible", true);
            b.set("#PreserveWaterRow.Visible", true);
            b.set("#PreserveWaterHint.Visible", true);
            b.set("#TouristDestinationRow.Visible", true);
            b.set("#TouristDestinationHint.Visible", true);
            b.set("#PlotTokenLockedRow.Visible", true);
            b.set("#PlotTokenLockedHint.Visible", true);
            b.set("#FloatingGiftBlueprintRow.Visible", true);
            b.set("#FloatingGiftBlueprintHint.Visible", true);
            b.set("#StyleIdLabel.Visible", true);
            b.set("#StyleIdField.Visible", true);
            b.set("#OpenMaterialsButton.Visible", false);
            b.set("#FillFromBuildShapeButton.Visible", false);
            b.set("#MaterialsPageLabel.Visible", false);
            b.set("#MaterialsPageRow.Visible", false);
            b.set("#ReviewSummary.Visible", false);
            b.set("#DetailHint.Visible", false);
            b.set("#SubmitToCommunityRow.Visible", isCommunityMarketplaceEnabled());
            return;
        }
        b.set("#DisplayNameField.Visible", step == PlotCreatorStep.IDENTITY);
        b.set("#DescriptionField.Visible", step == PlotCreatorStep.IDENTITY);
        b.set("#ConstructionIdField.Visible", step == PlotCreatorStep.IDENTITY);
        b.set("#PrefabNameField.Visible", false);
        b.set("#KindDropdown.Visible", false);
        b.set("#KindCheckScroll.Visible", step == PlotCreatorStep.KIND);
        b.set("#TagsField.Visible", step == PlotCreatorStep.TAGS);
        b.set("#VariantOfDropdown.Visible", false);
        b.set("#VariantCheckScroll.Visible", step == PlotCreatorStep.VARIANT);
        b.set("#GoldCostLabel.Visible", false);
        b.set("#GoldCostField.Visible", false);
        b.set("#SelfBuildDaysLabel.Visible", false);
        b.set("#SelfBuildDaysField.Visible", false);
        b.set("#MaxHomeResidentsLabel.Visible", false);
        b.set("#MaxHomeResidentsField.Visible", false);
        b.set("#SaveEmptySpacesRow.Visible", false);
        b.set("#SaveEmptySpacesHint.Visible", false);
        b.set("#PreserveWaterRow.Visible", false);
        b.set("#PreserveWaterHint.Visible", false);
        b.set("#TouristDestinationRow.Visible", false);
        b.set("#TouristDestinationHint.Visible", false);
        b.set("#PlotTokenLockedRow.Visible", false);
        b.set("#PlotTokenLockedHint.Visible", false);
        b.set("#FloatingGiftBlueprintRow.Visible", false);
        b.set("#FloatingGiftBlueprintHint.Visible", false);
        b.set("#StyleIdLabel.Visible", false);
        b.set("#StyleIdField.Visible", false);
        b.set("#OpenMaterialsButton.Visible", false);
        b.set("#FillFromBuildShapeButton.Visible", step == PlotCreatorStep.MATERIALS);
        b.set("#MaterialsPageLabel.Visible", false);
        b.set("#MaterialsPageRow.Visible", false);
        b.set("#MaterialsPrevPageButton.Visible", false);
        b.set("#MaterialsNextPageButton.Visible", false);
        b.set("#ReviewSummary.Visible", step == PlotCreatorStep.REVIEW || step == PlotCreatorStep.DONE);
        b.set("#DetailHint.Visible", step == PlotCreatorStep.SUBSTEP || step == PlotCreatorStep.MATERIALS);
        if (step == PlotCreatorStep.SUBSTEP) {
            PlotBuildingKindRequirements.SubstepRequirement sub = PlotCreatorService.currentSubstep(session.getDraft());
            if (sub != null) {
                b.set("#DetailHint.TextSpans", Message.translation(MSG + ".substep." + sub.type().name()));
            }
        }
        if (step == PlotCreatorStep.MATERIALS) {
            b.set("#StepHint.TextSpans", Message.translation(MSG + ".step.MATERIALS.hint"));
            b.set("#DetailHint.TextSpans", Message.translation(MSG + ".step.MATERIALS.detail"));
            b.set("#FillFromBuildShapeButton.TextSpans", Message.translation(MSG + ".button.useBuildShape"));
        }
        if (step == PlotCreatorStep.REVIEW || step == PlotCreatorStep.DONE) {
            b.set("#ReviewSummary.TextSpans", Message.raw(buildReviewText()));
        }
        b.set("#SubmitToCommunityRow.Visible", false);
    }

    private static boolean isCommunityMarketplaceEnabled() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        return plugin != null && plugin.getConfig().get().getCommunityMarketplace().isEnabled();
    }

    private void applyFields(@Nonnull UICommandBuilder b) {
        PlotCreatorDraft d = session.getDraft();
        if (d.getDisplayName() != null) {
            b.set("#DisplayNameField.Value", d.getDisplayName());
        }
        if (d.getDescription() != null) {
            b.set("#DescriptionField.Value", d.getDescription());
        }
        if (d.getConstructionId() != null) {
            b.set("#ConstructionIdField.Value", d.getConstructionId());
        }
        // Building editor keeps the id locked in Java; TextField has no Disabled markup property.
        if (d.getPrefabFileName() != null) {
            b.set("#PrefabNameField.Value", d.getPrefabFileName());
        }
        if (d.getBuildingTagsInput() != null) {
            b.set("#TagsField.Value", d.getBuildingTagsInput());
        } else if (!d.getBuildingTags().isEmpty()) {
            b.set("#TagsField.Value", String.join(", ", d.getBuildingTags()));
        }
        b.set("#GoldCostField.Value", String.valueOf(d.getTreasuryGoldCoinCost()));
        if (d.getSelfBuildDaysInput() != null) {
            b.set("#SelfBuildDaysField.Value", d.getSelfBuildDaysInput());
        } else {
            b.set("#SelfBuildDaysField.Value", PlotCreatorService.formatSelfBuildDaysForField(d.getSelfBuildGameDays()));
        }
        if (d.getMaxHomeResidentsInput() != null) {
            b.set("#MaxHomeResidentsField.Value", d.getMaxHomeResidentsInput());
        } else {
            b.set("#MaxHomeResidentsField.Value", String.valueOf(d.getMaxHomeResidents()));
        }
        b.set("#SaveEmptySpacesToggle.Value", d.isSaveEmptySpaces());
        b.set("#PreserveWaterToggle.Value", d.isPreserveWater());
        b.set("#TouristDestinationToggle.Value", d.isTouristDestination());
        b.set("#PlotTokenLockedToggle.Value", d.isPlotTokenLockedByDefault());
        b.set("#FloatingGiftBlueprintToggle.Value", d.isFloatingGiftBlueprint());
        b.set("#SubmitToCommunityToggle.Value", d.isSubmitToCommunity());
        if (d.getStyleId() != null) {
            b.set("#StyleIdField.Value", d.getStyleId());
        } else {
            b.set("#StyleIdField.Value", "");
        }
    }

    @Nonnull
    private String buildReviewText() {
        PlotCreatorDraft d = session.getDraft();
        StringBuilder sb = new StringBuilder();
        sb.append(d.getDisplayName()).append("\n");
        sb.append(d.getConstructionId()).append("\n");
        sb.append(d.getPrefabPath()).append("\n");
        if (!d.getKinds().isEmpty()) {
            for (int i = 0; i < d.getKinds().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(d.getKinds().get(i).name());
            }
            sb.append('\n');
        }
        if (!d.getBuildingTags().isEmpty()) {
            sb.append(String.join(", ", d.getBuildingTags()));
        }
        return sb.toString();
    }

    /**
     * Partial UI refresh only. {@link CustomUIPage#rebuild()} sends {@code clear=true} without re-appending the
     * template, which breaks every selector (including {@code #PlotCreatorTitleText}).
     */
    private void refreshPartial() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getPageManager().getCustomPage() != this) {
            return;
        }
        UICommandBuilder b = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        applyLabels(b);
        applyVisibility(b);
        applyFields(b);
        applyCheckLists(b, events);
        sendUpdate(b, events, false);
    }

    private void applyCheckLists(@Nonnull UICommandBuilder b, @Nonnull UIEventBuilder eventBuilder) {
        PlotCreatorStep step = session.getDraft().getStep();
        boolean showKind = kindPanelOnly || (!configurePanelOnly && step == PlotCreatorStep.KIND);
        boolean showVariant = !kindPanelOnly && !configurePanelOnly && step == PlotCreatorStep.VARIANT;
        b.clear("#KindCheckList");
        b.clear("#VariantCheckList");
        if (showKind) {
            appendKindRows(b, eventBuilder);
        }
        if (showVariant) {
            appendVariantRows(b, eventBuilder);
        }
    }

    private void appendKindRows(@Nonnull UICommandBuilder b, @Nonnull UIEventBuilder eventBuilder) {
        boolean playerTypesOnly = PlotCreatorService.limitBuildingTypesToPlayerKinds();
        List<PlotBuildingKind> selectable =
            PlotBuildingKind.selectableKinds(playerTypesOnly, session.getDraft().getKinds());
        List<PlotBuildingKind> selected = session.getDraft().getKinds();
        for (int i = 0; i < selectable.size(); i++) {
            PlotBuildingKind kind = selectable.get(i);
            b.append("#KindCheckList", "Aetherhaven/PlotCreatorToggleRow.ui");
            String row = "#KindCheckList[" + i + "]";
            b.set(row + " #Label.TextSpans", Message.translation(MSG + ".kind." + kind.name()));
            b.set(row + " #Toggle.Value", selected.contains(kind));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                row + " #Toggle",
                new EventData()
                    .append("Action", "ToggleKind")
                    .append("Kind", kind.name())
                    .append("@Checked", row + " #Toggle.Value"),
                false
            );
        }
    }

    private void appendVariantRows(@Nonnull UICommandBuilder b, @Nonnull UIEventBuilder eventBuilder) {
        List<String> selected = session.getDraft().getCountsAsConstructionIds();
        List<PlotCreatorMainConstructions.Entry> entries =
            PlotCreatorMainConstructions.variantBaseEntries(AetherhavenPlugin.get());
        for (int i = 0; i < entries.size(); i++) {
            PlotCreatorMainConstructions.Entry entry = entries.get(i);
            b.append("#VariantCheckList", "Aetherhaven/PlotCreatorToggleRow.ui");
            String row = "#VariantCheckList[" + i + "]";
            if (entry.labelLang() != null) {
                b.set(row + " #Label.TextSpans", Message.translation(entry.labelLang()));
            } else {
                b.set(row + " #Label.TextSpans", Message.raw(entry.fallbackLabel()));
            }
            b.set(row + " #Toggle.Value", selected.contains(entry.id()));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                row + " #Toggle",
                new EventData()
                    .append("Action", "ToggleVariant")
                    .append("VariantOf", entry.id())
                    .append("@Checked", row + " #Toggle.Value"),
                false
            );
        }
    }

    private void applyToggleKind(@Nonnull String kindName, boolean checked) {
        PlotBuildingKind kind = PlotBuildingKind.fromSerialized(kindName);
        if (kind == null) {
            return;
        }
        List<PlotBuildingKind> kinds = new ArrayList<>(session.getDraft().getKinds());
        if (checked) {
            if (kind == PlotBuildingKind.DECORATION) {
                kinds.clear();
                kinds.add(PlotBuildingKind.DECORATION);
            } else {
                kinds.remove(PlotBuildingKind.DECORATION);
                if (!kinds.contains(kind)) {
                    kinds.add(kind);
                }
            }
        } else {
            kinds.remove(kind);
        }
        session.getDraft().setKinds(kinds);
        PlotCreatorService.applyDefaultTagsForKind(session.getDraft());
    }

    private void applyToggleVariant(@Nonnull String constructionId, boolean checked) {
        String id = constructionId.trim();
        if (id.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(session.getDraft().getCountsAsConstructionIds());
        if (checked) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        } else {
            ids.remove(id);
        }
        session.getDraft().setCountsAsConstructionIds(ids);
    }

    @Override
    protected void rebuild() {
        refreshPartial();
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if ("ToggleKind".equals(data.action) && data.kind != null && !data.kind.isBlank()) {
            applyToggleKind(data.kind, Boolean.TRUE.equals(data.checked));
            refreshPartial();
            return;
        }
        if ("ToggleVariant".equals(data.action) && data.variantOf != null && !data.variantOf.isBlank()) {
            applyToggleVariant(data.variantOf, Boolean.TRUE.equals(data.checked));
            refreshPartial();
            return;
        }
        applyIncomingFields(data);
        if ("FillFromBuildShape".equals(data.action)) {
            PlotCreatorMaterialsActions.requestFillFromBuildShape(session, playerRef);
            refreshPartial();
            return;
        }
        if ("Cancel".equals(data.action)) {
            if (configPanelOnly || kindPanelOnly || configurePanelOnly) {
                closePanel(ref, store);
            } else {
                PlotCreatorService.cancelSession(playerRef, ref, store);
            }
            return;
        }
        if ("Back".equals(data.action)) {
            if (!configPanelOnly && !kindPanelOnly && !configurePanelOnly) {
                PlotCreatorService.back(session, ref, store);
                refreshPartial();
            }
            return;
        }
        if ("Next".equals(data.action)) {
            if (kindPanelOnly) {
                if (applyKindPanelAndClose(ref, store)) {
                    closePanel(ref, store);
                } else {
                    refreshPartial();
                }
            } else if (configurePanelOnly) {
                if (applyConfigurePanelAndClose(ref, store)) {
                    closePanel(ref, store);
                } else {
                    refreshPartial();
                }
            } else if (configPanelOnly) {
                if (applyConfigPanelAndClose(ref, store)) {
                    closePanel(ref, store);
                } else {
                    refreshPartial();
                }
            } else {
                handleNext(ref, store);
                refreshPartial();
            }
            return;
        }
        // ValueChanged only: keep draft in sync without re-pushing the field being typed.
        refreshDerivedFields(data);
    }

    /**
     * After a live field edit, push only values that were mutated as side effects (never the field
     * the user just typed). Avoids caret jumps / character resets from re-setting {@code .Value}.
     */
    private void refreshDerivedFields(@Nonnull PageData data) {
        PlotCreatorDraft d = session.getDraft();
        UICommandBuilder b = new UICommandBuilder();
        boolean any = false;
        if (data.displayName != null && !d.isConstructionIdUserEdited()) {
            if (d.getConstructionId() != null) {
                b.set("#ConstructionIdField.Value", d.getConstructionId());
                any = true;
            }
            if (d.getPrefabFileName() != null) {
                b.set("#PrefabNameField.Value", d.getPrefabFileName());
                any = true;
            }
        }
        if (data.constructionId != null && d.getPrefabFileName() != null) {
            b.set("#PrefabNameField.Value", d.getPrefabFileName());
            any = true;
        }
        if (any) {
            sendUpdate(b, null, false);
        }
    }

    private boolean applyConfigurePanelAndClose(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        String err = PlotCreatorService.applyConfigureInput(session.getDraft());
        if (err != null) {
            playerRef.sendMessage(Message.translation(MSG + ".error." + err));
            return false;
        }
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        return true;
    }

    private boolean applyKindPanelAndClose(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        String kindErr = PlotCreatorService.validateKindSelection(session.getDraft());
        if (kindErr != null) {
            playerRef.sendMessage(Message.translation(MSG + ".error." + kindErr));
            return false;
        }
        PlotCreatorService.applyDefaultTagsForKind(session.getDraft());
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        return true;
    }

    private boolean applyConfigPanelAndClose(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlotCreatorStep step = session.getDraft().getStep();
        if (step == PlotCreatorStep.IDENTITY) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return false;
            }
            String err =
                PlotCreatorValidator.validateId(
                    session.getDraft().getConstructionId(),
                    plugin.getConstructionCatalog(),
                    session.getDraft().getEditingConstructionId()
                );
            if (err != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + err));
                return false;
            }
            if (session.getDraft().getDisplayName() == null || session.getDraft().getDisplayName().isBlank()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.id_empty"));
                return false;
            }
            PlotCreatorService.syncPrefabFileNameFromConstructionId(session.getDraft());
        }
        if (step == PlotCreatorStep.TAGS) {
            PlotCreatorService.applyTagsInput(session.getDraft());
        }
        if (step == PlotCreatorStep.VARIANT) {
            if (session.getDraft().getCountsAsConstructionIds().isEmpty()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.needVariantOf"));
                return false;
            }
            for (String base : session.getDraft().getCountsAsConstructionIds()) {
                if (!PlotCreatorMainConstructions.isKnownMainConstruction(AetherhavenPlugin.get(), base)) {
                    playerRef.sendMessage(Message.translation(MSG + ".error.invalidVariantOf"));
                    return false;
                }
            }
        }
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        return true;
    }

    private void closePanel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, com.hypixel.hytale.protocol.packets.interface_.Page.None);
        }
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
    }

    private void applyIncomingFields(@Nonnull PageData data) {
        PlotCreatorDraft d = session.getDraft();
        if (data.displayName != null) {
            d.setDisplayName(data.displayName);
            if (!d.isConstructionIdUserEdited()) {
                PlotCreatorService.suggestIdFromDisplayName(d);
            }
        }
        if (data.description != null) {
            d.setDescription(data.description);
        }
        if (data.constructionId != null) {
            if (!d.isBuildingEditorMode()) {
                d.setConstructionId(data.constructionId.trim().toLowerCase(Locale.ROOT));
                d.setConstructionIdUserEdited(true);
                PlotCreatorService.syncPrefabFileNameFromConstructionId(d);
            }
        }
        if (data.prefabName != null) {
            d.setPrefabFileName(data.prefabName);
        }
        if (data.tags != null) {
            d.setBuildingTagsInput(data.tags);
        }
        if (data.goldCost != null) {
            try {
                d.setTreasuryGoldCoinCost(Long.parseLong(data.goldCost.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (data.selfBuildDays != null) {
            d.setSelfBuildDaysInput(data.selfBuildDays);
        }
        if (data.maxHomeResidents != null) {
            d.setMaxHomeResidentsInput(data.maxHomeResidents);
        }
        if (data.saveEmptySpaces != null) {
            d.setSaveEmptySpaces(data.saveEmptySpaces);
        }
        if (data.preserveWater != null) {
            d.setPreserveWater(data.preserveWater);
        }
        if (data.touristDestination != null) {
            d.setTouristDestination(data.touristDestination);
        }
        if (data.plotTokenLocked != null) {
            d.setPlotTokenLockedByDefault(data.plotTokenLocked);
        }
        if (data.floatingGiftBlueprint != null) {
            d.setFloatingGiftBlueprint(data.floatingGiftBlueprint);
        }
        if (data.submitToCommunity != null) {
            d.setSubmitToCommunity(data.submitToCommunity);
        }
        if (data.styleId != null) {
            d.setStyleId(data.styleId);
        }
    }

    private void handleNext(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlotCreatorDraft d = session.getDraft();
        PlotCreatorStep step = d.getStep();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        if (step == PlotCreatorStep.WELCOME) {
            PlotCreatorService.advance(session, ref, store);
            return;
        }
        if (step == PlotCreatorStep.PREFAB_SAVE) {
            PlotCreatorInteractions.exportPrefab(session, playerRef);
            return;
        }
        if (step == PlotCreatorStep.IDENTITY) {
            String err = PlotCreatorValidator.validateId(d.getConstructionId(), plugin.getConstructionCatalog(), d.getEditingConstructionId());
            if (err != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + err));
                return;
            }
            if (d.getDisplayName() == null || d.getDisplayName().isBlank()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.id_empty"));
                return;
            }
            PlotCreatorService.syncPrefabFileNameFromConstructionId(d);
            PlotCreatorService.advance(session, ref, store);
            return;
        }
        if (step == PlotCreatorStep.TAGS) {
            PlotCreatorService.applyTagsInput(d);
            PlotCreatorService.advance(session, ref, store);
            return;
        }
        if (step == PlotCreatorStep.KIND) {
            String kindErr = PlotCreatorService.validateKindSelection(d);
            if (kindErr != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + kindErr));
                return;
            }
            PlotCreatorService.applyDefaultTagsForKind(d);
            PlotCreatorService.advance(session, ref, store);
            return;
        }
        if (step == PlotCreatorStep.VARIANT) {
            if (d.getCountsAsConstructionIds().isEmpty()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.needVariantOf"));
                return;
            }
            for (String base : d.getCountsAsConstructionIds()) {
                if (!PlotCreatorMainConstructions.isKnownMainConstruction(plugin, base)) {
                    playerRef.sendMessage(Message.translation(MSG + ".error.invalidVariantOf"));
                    return;
                }
            }
            PlotCreatorService.advance(session, ref, store);
            return;
        }
        if (step == PlotCreatorStep.IMPORTANT_SPOTS) {
            PlotCreatorService.confirmImportantSpots(d);
            PlotCreatorService.advance(session, ref, store);
            return;
        }
        if (step == PlotCreatorStep.SUBSTEP) {
            PlotCreatorService.advanceSubstepOrStep(session, ref, store);
            return;
        }
        if (step == PlotCreatorStep.MATERIALS) {
            PlotCreatorService.advance(session, ref, store);
            return;
        }
        if (step == PlotCreatorStep.CONFIGURE) {
            String configureErr = PlotCreatorService.applyConfigureInput(d);
            if (configureErr != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + configureErr));
                return;
            }
            PlotCreatorService.advance(session, ref, store);
            return;
        }
        if (step == PlotCreatorStep.REVIEW) {
            PlotCreatorService.saveAndFinish(plugin, session, playerRef, ref, store);
            return;
        }
        if (step == PlotCreatorStep.DONE) {
            PlotCreatorService.cancelSession(playerRef, ref, store);
            return;
        }
        PlotCreatorService.advance(session, ref, store);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("Kind", Codec.STRING), (d, v) -> d.kind = v, d -> d.kind)
            .add()
            .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (d, v) -> d.checked = v, d -> d.checked)
            .add()
            .append(new KeyedCodec<>("@DisplayName", Codec.STRING), (d, v) -> d.displayName = v, d -> d.displayName)
            .add()
            .append(new KeyedCodec<>("@Description", Codec.STRING), (d, v) -> d.description = v, d -> d.description)
            .add()
            .append(new KeyedCodec<>("@ConstructionId", Codec.STRING), (d, v) -> d.constructionId = v, d -> d.constructionId)
            .add()
            .append(new KeyedCodec<>("@PrefabName", Codec.STRING), (d, v) -> d.prefabName = v, d -> d.prefabName)
            .add()
            .append(new KeyedCodec<>("@Tags", Codec.STRING), (d, v) -> d.tags = v, d -> d.tags)
            .add()
            .append(new KeyedCodec<>("VariantOf", Codec.STRING), (d, v) -> d.variantOf = v, d -> d.variantOf)
            .add()
            .append(new KeyedCodec<>("@GoldCost", Codec.STRING), (d, v) -> d.goldCost = v, d -> d.goldCost)
            .add()
            .append(new KeyedCodec<>("@SelfBuildDays", Codec.STRING), (d, v) -> d.selfBuildDays = v, d -> d.selfBuildDays)
            .add()
            .append(
                new KeyedCodec<>("@MaxHomeResidents", Codec.STRING),
                (d, v) -> d.maxHomeResidents = v,
                d -> d.maxHomeResidents
            )
            .add()
            .append(new KeyedCodec<>("@SaveEmptySpaces", Codec.BOOLEAN), (d, v) -> d.saveEmptySpaces = v, d -> d.saveEmptySpaces)
            .add()
            .append(new KeyedCodec<>("@PreserveWater", Codec.BOOLEAN), (d, v) -> d.preserveWater = v, d -> d.preserveWater)
            .add()
            .append(new KeyedCodec<>("@TouristDestination", Codec.BOOLEAN), (d, v) -> d.touristDestination = v, d -> d.touristDestination)
            .add()
            .append(new KeyedCodec<>("@PlotTokenLocked", Codec.BOOLEAN), (d, v) -> d.plotTokenLocked = v, d -> d.plotTokenLocked)
            .add()
            .append(
                new KeyedCodec<>("@FloatingGiftBlueprint", Codec.BOOLEAN),
                (d, v) -> d.floatingGiftBlueprint = v,
                d -> d.floatingGiftBlueprint
            )
            .add()
            .append(
                new KeyedCodec<>("@SubmitToCommunity", Codec.BOOLEAN),
                (d, v) -> d.submitToCommunity = v,
                d -> d.submitToCommunity
            )
            .add()
            .append(new KeyedCodec<>("@StyleId", Codec.STRING), (d, v) -> d.styleId = v, d -> d.styleId)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String kind;
        @Nullable
        private Boolean checked;
        @Nullable
        private String displayName;
        @Nullable
        private String description;
        @Nullable
        private String constructionId;
        @Nullable
        private String prefabName;
        @Nullable
        private String tags;
        @Nullable
        private String variantOf;
        @Nullable
        private String goldCost;
        @Nullable
        private String selfBuildDays;
        @Nullable
        private String maxHomeResidents;
        @Nullable
        private Boolean saveEmptySpaces;
        @Nullable
        private Boolean preserveWater;
        @Nullable
        private Boolean touristDestination;
        @Nullable
        private Boolean plotTokenLocked;
        @Nullable
        private Boolean floatingGiftBlueprint;
        @Nullable
        private Boolean submitToCommunity;
        @Nullable
        private String styleId;
    }
}
