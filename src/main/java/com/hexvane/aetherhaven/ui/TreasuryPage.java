package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.economy.TownTaxService;
import com.hexvane.aetherhaven.economy.TownTaxService.TaxMorningBreakdown;
import com.hexvane.aetherhaven.economy.TownTaxService.VillagerTaxLine;
import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hexvane.aetherhaven.economy.api.Transfer;
import com.hexvane.aetherhaven.plot.TreasuryBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.villager.AetherhavenRoleLabels;
import com.hexvane.aetherhaven.town.CharterTaxPolicy;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TreasuryPage extends AetherhavenInteractiveCustomUIPage<TreasuryPage.PageData> {
    private static final String TAX_ROWS = "#TaxResidentRows";
    private static final int MAX_TAX_ROWS = 36;

    private final Ref<ChunkStore> treasuryBlockRef;
    private boolean templateAppended;
    /** 0 = coins, 1 = morning tithe breakdown. */
    private int treasuryTab;

    public TreasuryPage(@Nonnull PlayerRef playerRef, @Nonnull Ref<ChunkStore> treasuryBlockRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.treasuryBlockRef = treasuryBlockRef;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/TreasuryPage.ui");
            templateAppended = true;
        }
        AetherhavenUiLocalization.applyTreasuryPage(commandBuilder);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            applyBrokenTreasuryUi(commandBuilder);
            commandBuilder.set("#Balance.TextSpans", Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        Store<ChunkStore> cs = treasuryBlockRef.getStore();
        TreasuryBlock tb = cs.getComponent(treasuryBlockRef, TreasuryBlock.getComponentType());
        if (tb == null || tb.getTownId().isBlank()) {
            applyBrokenTreasuryUi(commandBuilder);
            commandBuilder.set("#Balance.TextSpans", Message.translation("aetherhaven_common.aetherhaven.common.treasuryNotLinked"));
            return;
        }
        UUID townUuid;
        try {
            townUuid = UUID.fromString(tb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            applyBrokenTreasuryUi(commandBuilder);
            commandBuilder.set("#Balance.TextSpans", Message.translation("aetherhaven_common.aetherhaven.common.invalidTownLink"));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null) {
            applyBrokenTreasuryUi(commandBuilder);
            commandBuilder.set("#Balance.TextSpans", Message.translation("aetherhaven_common.aetherhaven.common.townNotFound"));
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null || !town.playerCanOpenTreasuryPanel(uc.getUuid())) {
            applyBrokenTreasuryUi(commandBuilder);
            commandBuilder.set(
                "#Balance.TextSpans",
                Message.translation("aetherhaven_common.aetherhaven.common.noTreasuryPanelPermission")
            );
            return;
        }

        commandBuilder.set("#TreasuryTabStrip.Visible", true);
        boolean coinsTab = treasuryTab == 0;
        commandBuilder.set("#CoinsTabContent.Visible", coinsTab);
        commandBuilder.set("#TaxTabContent.Visible", !coinsTab);
        commandBuilder.set("#TabCoinsButton.Disabled", coinsTab);
        commandBuilder.set("#TabTitheButton.Disabled", !coinsTab);

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabCoinsButton",
            new EventData().append("Action", "SwitchTabTreasuryCoins"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#TabTitheButton",
            new EventData().append("Action", "SwitchTabTreasuryTithe"),
            false
        );

        GoldAccount treasury = AetherhavenEconomy.townAccount(town);
        commandBuilder.set(
            "#Balance.TextSpans",
            Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.coinsLine")
                .param("count", AetherhavenEconomy.provider().amount(treasury))
        );
        commandBuilder.set(
            "#TreasuryAmountField.PlaceholderText",
            Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.amountPlaceholder")
        );
        commandBuilder.set("#DepositButton.Disabled", false);
        commandBuilder.set("#WithdrawButton.Disabled", treasury.balance() <= 0L);

        if (coinsTab) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DepositButton",
                new EventData().append("Action", "Deposit").append("@Amount", "#TreasuryAmountField.Value"),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#WithdrawButton",
                new EventData().append("Action", "Withdraw").append("@Amount", "#TreasuryAmountField.Value"),
                false
            );
        } else {
            buildTaxTab(store, commandBuilder, town, plugin);
        }
    }

    private static void applyBrokenTreasuryUi(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set("#TreasuryTabStrip.Visible", false);
        commandBuilder.set("#CoinsTabContent.Visible", true);
        commandBuilder.set("#TaxTabContent.Visible", false);
        commandBuilder.clear(TAX_ROWS);
        commandBuilder.set("#TaxResidentsFooter.Visible", false);
        commandBuilder.set("#TaxHallMissing.Visible", false);
        commandBuilder.set("#TitheRowFounder.Visible", false);
        commandBuilder.set("#TitheRowFeast.Visible", false);
        commandBuilder.set("#DepositButton.Disabled", true);
        commandBuilder.set("#WithdrawButton.Disabled", true);
    }

    private void buildTaxTab(
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin
    ) {
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        TaxMorningBreakdown b = TownTaxService.computeTaxMorningBreakdown(town, store, cfg, plugin.getConstructionCatalog());
        CharterTaxPolicy policyEnum = town.getCharterTaxPolicyEnum();

        commandBuilder.set(
            "#TaxIntro.TextSpans",
            Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.intro")
                .param("live", String.valueOf(b.simulatedResidentEntityCount()))
                .param("count", String.valueOf(b.taxResidentRowCount()))
        );
        commandBuilder.set("#TaxPolicyShort.TextSpans", taxPolicyShortMessage(policyEnum, b.maxGoldPerResidentPerDay(), cfg));
        boolean hall = b.townHallComplete();
        commandBuilder.set("#TaxHallMissing.Visible", !hall);

        commandBuilder.set(
            "#TitheSubLabel.TextSpans",
            Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.sheetSubLabel")
                .param("count", String.valueOf(b.taxResidentRowCount()))
        );
        commandBuilder.set("#TitheSubGold.TextSpans", Message.raw(padGoldColumn(b.sumBeforeTownMultipliers())));

        boolean founder = b.founderMonumentActive();
        commandBuilder.set("#TitheRowFounder.Visible", founder);
        if (founder) {
            commandBuilder.set(
                "#TitheFounderLabel.TextSpans",
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.sheetFounderLabel")
                    .param("mult", formatPermilleMultiplier(b.founderMonumentPermille()))
            );
            commandBuilder.set("#TitheFounderGold.TextSpans", Message.raw(padGoldColumn(b.sumAfterFounderMonument())));
        }

        boolean feast = b.stewardsFeastTaxActive();
        commandBuilder.set("#TitheRowFeast.Visible", feast);
        if (feast) {
            commandBuilder.set(
                "#TitheFeastLabel.TextSpans",
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.sheetFeastLabel")
                    .param("mult", formatPermilleMultiplier(b.feastTaxBonusPermille()))
            );
            commandBuilder.set("#TitheFeastGold.TextSpans", Message.raw(padGoldColumn(b.finalTotal())));
        }

        commandBuilder.set("#TitheTotalGold.TextSpans", Message.raw(padGoldColumn(b.finalTotal())));

        commandBuilder.clear(TAX_ROWS);
        List<VillagerTaxLine> sorted = new ArrayList<>(b.lines());
        sorted.sort(
            Comparator.comparing(VillagerTaxLine::displayName, String.CASE_INSENSITIVE_ORDER).thenComparing(VillagerTaxLine::entityUuid)
        );
        int n = Math.min(sorted.size(), MAX_TAX_ROWS);
        for (int i = 0; i < n; i++) {
            commandBuilder.append(TAX_ROWS, "Aetherhaven/TreasuryTaxResidentRow.ui");
            String row = TAX_ROWS + "[" + i + "]";
            VillagerTaxLine line = sorted.get(i);
            String roleId = line.npcRole() != null ? line.npcRole() : "";
            String profKey = AetherhavenRoleLabels.professionTranslationKey(roleId, line.bindingKind());
            Message nameLine = Message.raw(line.displayName());
            commandBuilder.set(
                row + " #Name.TextSpans",
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.residentLine")
                    .param("name", nameLine)
                    .param("job", Message.translation(profKey))
            );
            if (line.townsfolkFlatTax()) {
                commandBuilder.set(
                    row + " #Comfort.TextSpans",
                    Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.townsfolkFlatGold")
                        .param("amount", Integer.toString(AetherhavenConstants.TOWNSFOLK_TAX_GOLD_PER_DAY))
                );
            } else {
                int comfortPct = Math.round(line.needsRatio() * 100f);
                commandBuilder.set(
                    row + " #Comfort.TextSpans",
                    Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.tax.comfortPercent").param("pct", String.valueOf(comfortPct))
                );
            }
            commandBuilder.set(row + " #Gold.TextSpans", Message.raw(padGoldColumn(line.contributionGold())));
        }
        if (sorted.size() > MAX_TAX_ROWS) {
            commandBuilder.set("#TaxResidentsFooter.Visible", true);
            commandBuilder.set(
                "#TaxResidentsFooter.TextSpans",
                Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.footerMore")
                    .param("shown", n)
                    .param("total", sorted.size())
            );
        } else {
            commandBuilder.set("#TaxResidentsFooter.Visible", false);
        }
    }

    @Nonnull
    private static String padGoldColumn(long amount) {
        return String.format(Locale.US, "%6d  g", amount);
    }

    @Nonnull
    private static String formatPermilleMultiplier(int permille) {
        double m = permille / 1000.0;
        return String.format(Locale.US, "%.2f×", m);
    }

    @Nonnull
    private static Message taxPolicyShortMessage(
        @Nullable CharterTaxPolicy policy,
        int maxPer,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        if (policy == null) {
            return Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.policyShort.linear").param("max", maxPer);
        }
        if (policy == CharterTaxPolicy.PER_CAPITA) {
            return Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.policyShort.perCapita")
                .param("min", String.valueOf(cfg.getCharterPerCapitaMinGoldPerResidentPerDay()))
                .param("max", String.valueOf(cfg.getCharterPerCapitaMaxGoldPerResidentPerDay()));
        }
        int minComfortPct = (int) Math.round(cfg.getCharterHappinessTaxMinComfortRatio() * 100.0);
        int peakGold = (int) Math.floor(maxPer * (cfg.getCharterHappinessTaxPeakPermille() / 1000.0));
        return Message.translation("aetherhaven_jewelry_geode.aetherhaven.ui.treasury.tax.policyShort.happiness")
            .param("base", String.valueOf(maxPer))
            .param("minComfortPct", String.valueOf(minComfortPct))
            .param("peak", String.valueOf(peakGold));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        String action = data.action;
        if (action == null) {
            return;
        }
        if (action.equalsIgnoreCase("SwitchTabTreasuryCoins")) {
            treasuryTab = 0;
            refresh(ref, store);
            return;
        }
        if (action.equalsIgnoreCase("SwitchTabTreasuryTithe")) {
            treasuryTab = 1;
            refresh(ref, store);
            return;
        }

        if (!action.equalsIgnoreCase("Deposit") && !action.equalsIgnoreCase("Withdraw")) {
            return;
        }
        if (treasuryTab != 0) {
            return;
        }

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            return;
        }
        Store<ChunkStore> cs = treasuryBlockRef.getStore();
        TreasuryBlock tb = cs.getComponent(treasuryBlockRef, TreasuryBlock.getComponentType());
        if (tb == null || tb.getTownId().isBlank()) {
            return;
        }
        UUID townUuid;
        try {
            townUuid = UUID.fromString(tb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        GoldAccount account = AetherhavenEconomy.account(ref, store);
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (uc == null || account == null || !town.playerCanOpenTreasuryPanel(uc.getUuid())) {
            return;
        }
        boolean deposit = action.equalsIgnoreCase("Deposit");
        GoldAccount treasury = AetherhavenEconomy.townAccount(town);
        // Empty field = all, as before the field existed. The provider reads and moves in its own unit.
        String text = data.amount == null ? "" : data.amount.trim();
        Transfer transfer = deposit
            ? AetherhavenEconomy.provider().transfer(account, treasury, text)
            : AetherhavenEconomy.provider().transfer(treasury, account, text);
        switch (transfer.outcome()) {
            case MOVED -> {
                tm.updateTown(town);
                tell(pr, Message.translation(deposit
                    ? "aetherhaven_ui_shell.aetherhaven.ui.treasury.deposited"
                    : "aetherhaven_ui_shell.aetherhaven.ui.treasury.withdrew").param("count", transfer.moved()));
            }
            case NOT_AN_AMOUNT -> tell(pr, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.notAnAmount"));
            case NOT_AVAILABLE -> {
                if (deposit) {
                    tell(pr, Message.translation(text.isEmpty()
                        ? "aetherhaven_ui_shell.aetherhaven.ui.treasury.depositNoCoins"
                        : "aetherhaven_ui_shell.aetherhaven.ui.treasury.depositNotEnough"));
                } else if (!text.isEmpty()) {
                    tell(pr, Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.withdrawNotEnough"));
                }
            }
            case NO_ROOM -> tell(pr, Message.translation(deposit
                ? "aetherhaven_ui_shell.aetherhaven.ui.treasury.depositRemoveFailed"
                : "aetherhaven_ui_shell.aetherhaven.ui.treasury.makeRoom"));
        }
        refresh(ref, store);
    }

    private static void tell(@Nullable PlayerRef pr, @Nonnull Message message) {
        if (pr != null) {
            pr.sendMessage(message);
        }
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
            .append(new KeyedCodec<>("@Amount", Codec.STRING), (d, v) -> d.amount = v, d -> d.amount)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String amount;
    }
}
