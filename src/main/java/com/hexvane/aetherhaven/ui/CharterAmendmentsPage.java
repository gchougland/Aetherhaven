package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.CharterSpecialization;
import com.hexvane.aetherhaven.town.CharterTaxPolicy;
import com.hexvane.aetherhaven.town.TownCharterService;
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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Skill-tree style charter amendments: tax policy (tier 1) and specialization (tier 2). */
public final class CharterAmendmentsPage extends InteractiveCustomUIPage<CharterAmendmentsPage.PageData> {
    private boolean templateAppended;

    public CharterAmendmentsPage(@Nonnull PlayerRef playerRef) {
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
            commandBuilder.append("Aetherhaven/CharterAmendments.ui");
            templateAppended = true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || uc == null || pr == null) {
            commandBuilder.set("#PopHint.TextSpans", Message.translation("server.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        var tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
        if (town == null) {
            commandBuilder.set("#PopHint.TextSpans", Message.translation("server.aetherhaven.ui.charter.noTown"));
            return;
        }
        if (!town.playerCanManageConstructions(uc.getUuid())) {
            commandBuilder.set("#PopHint.TextSpans", Message.translation("server.aetherhaven.ui.charter.noPermission"));
            return;
        }
        int pop = TownCharterService.countResidents(town, store);
        int tierUnlock = TownCharterService.unlockedAmendmentTier(pop);
        commandBuilder.set(
            "#PopHint.TextSpans",
            Message.translation("server.aetherhaven.ui.charter.popHint").param("count", pop)
        );

        boolean t1Done = town.getCharterTaxPolicyEnum() != null;
        boolean t2Done = town.getCharterSpecializationEnum() != null;

        bindTier1(commandBuilder, eventBuilder, tierUnlock, town);
        bindTier2(commandBuilder, eventBuilder, tierUnlock, t1Done, town);

        if (t1Done && t2Done) {
            commandBuilder.set("#Status.TextSpans", Message.translation("server.aetherhaven.ui.charter.allChosen"));
        } else {
            commandBuilder.set("#Status.TextSpans", Message.translation("server.aetherhaven.ui.charter.hint"));
        }
    }

    private static void bindTier1(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        int tierUnlock,
        @Nonnull TownRecord town
    ) {
        commandBuilder.set("#T1PerCapitaText.TextSpans", Message.translation("server.aetherhaven.ui.charter.t1.perCapitaShort"));
        commandBuilder.set("#T1HappinessText.TextSpans", Message.translation("server.aetherhaven.ui.charter.t1.happinessShort"));
        commandBuilder.set("#T1PerCapita.TooltipTextSpans", Message.translation("server.aetherhaven.ui.charter.t1.perCapitaTooltip"));
        commandBuilder.set("#T1Happiness.TooltipTextSpans", Message.translation("server.aetherhaven.ui.charter.t1.happinessTooltip"));

        CharterTaxPolicy policy = town.getCharterTaxPolicyEnum();
        boolean tier1Unlocked = tierUnlock >= 1;
        boolean perCapitaDisabled;
        boolean happinessDisabled;
        if (!tier1Unlocked) {
            perCapitaDisabled = true;
            happinessDisabled = true;
        } else if (policy == null) {
            perCapitaDisabled = false;
            happinessDisabled = false;
        } else {
            perCapitaDisabled = policy != CharterTaxPolicy.PER_CAPITA;
            happinessDisabled = policy != CharterTaxPolicy.HAPPINESS_WEIGHTED;
        }
        commandBuilder.set("#T1PerCapita.Disabled", perCapitaDisabled);
        commandBuilder.set("#T1Happiness.Disabled", happinessDisabled);
        commandBuilder.set("#T1PerCapitaDim.Visible", perCapitaDisabled);
        commandBuilder.set("#T1HappinessDim.Visible", happinessDisabled);
        commandBuilder.set(
            "#T1PerCapitaText.Style.TextColor",
            perCapitaDisabled ? "#8a8698" : "#e4dfd4"
        );
        commandBuilder.set(
            "#T1HappinessText.Style.TextColor",
            happinessDisabled ? "#8a8698" : "#e4dfd4"
        );

        if (tier1Unlocked && policy == null) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#T1PerCapita",
                new EventData().append("Action", "Pick").append("Choice", "t1_per_capita"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#T1Happiness",
                new EventData().append("Action", "Pick").append("Choice", "t1_happiness"),
                false
            );
        }
    }

    private static void bindTier2(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        int tierUnlock,
        boolean t1Done,
        @Nonnull TownRecord town
    ) {
        commandBuilder.set("#T2MiningText.TextSpans", Message.translation("server.aetherhaven.ui.charter.t2.miningShort"));
        commandBuilder.set("#T2LoggingText.TextSpans", Message.translation("server.aetherhaven.ui.charter.t2.loggingShort"));
        commandBuilder.set("#T2FarmingText.TextSpans", Message.translation("server.aetherhaven.ui.charter.t2.farmingShort"));
        commandBuilder.set("#T2SmithingText.TextSpans", Message.translation("server.aetherhaven.ui.charter.t2.smithingShort"));
        commandBuilder.set("#T2Mining.TooltipTextSpans", Message.translation("server.aetherhaven.ui.charter.t2.miningTooltip"));
        commandBuilder.set("#T2Logging.TooltipTextSpans", Message.translation("server.aetherhaven.ui.charter.t2.loggingTooltip"));
        commandBuilder.set("#T2Farming.TooltipTextSpans", Message.translation("server.aetherhaven.ui.charter.t2.farmingTooltip"));
        commandBuilder.set("#T2Smithing.TooltipTextSpans", Message.translation("server.aetherhaven.ui.charter.t2.smithingTooltip"));

        CharterSpecialization spec = town.getCharterSpecializationEnum();
        boolean tier2Unlocked = tierUnlock >= 2 && t1Done;
        boolean miningD;
        boolean loggingD;
        boolean farmingD;
        boolean smithingD;
        if (!tier2Unlocked) {
            miningD = true;
            loggingD = true;
            farmingD = true;
            smithingD = true;
        } else if (spec == null) {
            miningD = false;
            loggingD = false;
            farmingD = false;
            smithingD = false;
        } else {
            miningD = spec != CharterSpecialization.MINING;
            loggingD = spec != CharterSpecialization.LOGGING;
            farmingD = spec != CharterSpecialization.FARMING;
            smithingD = spec != CharterSpecialization.SMITHING;
        }
        commandBuilder.set("#T2Mining.Disabled", miningD);
        commandBuilder.set("#T2Logging.Disabled", loggingD);
        commandBuilder.set("#T2Farming.Disabled", farmingD);
        commandBuilder.set("#T2Smithing.Disabled", smithingD);
        commandBuilder.set("#T2MiningDim.Visible", miningD);
        commandBuilder.set("#T2LoggingDim.Visible", loggingD);
        commandBuilder.set("#T2FarmingDim.Visible", farmingD);
        commandBuilder.set("#T2SmithingDim.Visible", smithingD);
        commandBuilder.set("#T2MiningText.Style.TextColor", miningD ? "#8a8698" : "#e4dfd4");
        commandBuilder.set("#T2LoggingText.Style.TextColor", loggingD ? "#8a8698" : "#e4dfd4");
        commandBuilder.set("#T2FarmingText.Style.TextColor", farmingD ? "#8a8698" : "#e4dfd4");
        commandBuilder.set("#T2SmithingText.Style.TextColor", smithingD ? "#8a8698" : "#e4dfd4");

        if (tier2Unlocked && spec == null) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#T2Mining",
                new EventData().append("Action", "Pick").append("Choice", "t2_mining"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#T2Logging",
                new EventData().append("Action", "Pick").append("Choice", "t2_logging"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#T2Farming",
                new EventData().append("Action", "Pick").append("Choice", "t2_farming"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#T2Smithing",
                new EventData().append("Action", "Pick").append("Choice", "t2_smithing"),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null || !data.action.equalsIgnoreCase("Pick") || data.choice == null || data.choice.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || uc == null || pr == null) {
            return;
        }
        var tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
            if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            return;
        }
        int pop = TownCharterService.countResidents(town, store);
        int tierUnlock = TownCharterService.unlockedAmendmentTier(pop);
        String c = data.choice.trim();
        String err = null;
        switch (c) {
            case "t1_per_capita" -> {
                if (tierUnlock < 1 || town.getCharterTaxPolicyEnum() != null) {
                    err = "server.aetherhaven.ui.charter.err.locked";
                } else {
                    town.setCharterTaxPolicy(CharterTaxPolicy.PER_CAPITA.id());
                }
            }
            case "t1_happiness" -> {
                if (tierUnlock < 1 || town.getCharterTaxPolicyEnum() != null) {
                    err = "server.aetherhaven.ui.charter.err.locked";
                } else {
                    town.setCharterTaxPolicy(CharterTaxPolicy.HAPPINESS_WEIGHTED.id());
                }
            }
            case "t2_mining", "t2_logging", "t2_farming", "t2_smithing" -> {
                if (tierUnlock < 2 || town.getCharterTaxPolicyEnum() == null || town.getCharterSpecializationEnum() != null) {
                    err = "server.aetherhaven.ui.charter.err.locked";
                } else {
                    CharterSpecialization spec =
                        switch (c) {
                            case "t2_mining" -> CharterSpecialization.MINING;
                            case "t2_logging" -> CharterSpecialization.LOGGING;
                            case "t2_farming" -> CharterSpecialization.FARMING;
                            default -> CharterSpecialization.SMITHING;
                        };
                    town.setCharterSpecialization(spec.id());
                }
            }
            default -> err = "server.aetherhaven.ui.charter.err.invalid";
        }
        if (err != null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(err),
                NotificationStyle.Danger
            );
            refresh(ref, store);
            return;
        }
        tm.updateTown(town);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("server.aetherhaven.ui.charter.saved"),
            NotificationStyle.Success
        );
        refresh(ref, store);
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("Choice", Codec.STRING), (d, v) -> d.choice = v, d -> d.choice)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String choice;
    }
}
