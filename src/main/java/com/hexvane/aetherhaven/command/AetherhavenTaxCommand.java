package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.TownTaxService;
import com.hexvane.aetherhaven.economy.TownTaxService.TaxMorningBreakdown;
import com.hexvane.aetherhaven.economy.TownTaxService.VillagerTaxLine;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Debug: show dawn-aligned morning tax math for your town (loaded residents only).
 * Normal command permissions apply.
 */
public final class AetherhavenTaxCommand extends AbstractCommandCollection {
    public AetherhavenTaxCommand() {
        super("tax", "aetherhaven_commands_help.commands.aetherhaven.tax.desc");
        this.addSubCommand(new BreakdownCommand());
        this.addSubCommand(new NowCommand());
    }

    @Nullable
    private static TownRecord townForQuestPlayer(
        @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull World world
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        return AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownForPlayerInWorld(uc.getUuid());
    }

    private static final class BreakdownCommand extends AbstractPlayerCommand {
        BreakdownCommand() {
            super("breakdown", "aetherhaven_commands_help.commands.aetherhaven.tax.breakdown.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null || !AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            TownRecord town = townForQuestPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noTownInWorld"));
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noQuestPermission"));
                return;
            }
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(
                () -> {
                    TaxMorningBreakdown b =
                        TownTaxService.computeTaxMorningBreakdown(town, es, plugin.getConfig().get());
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.debug.taxBreakdownIntro")
                    );
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.debug.state2")
                            .param("a", String.valueOf(b.townHallComplete()))
                            .param("b", String.valueOf(b.morningTaxWindow()))
                            .param("c", String.valueOf(b.dawnAlignedEpochDay()))
                            .param("d", b.treasuryLastTaxEpochDay() == null ? "null" : String.valueOf(b.treasuryLastTaxEpochDay()))
                            .param("e", String.valueOf(b.wouldCollectGoldOnNextMorningTick()))
                    );
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.debug.state3")
                            .param("a", b.taxPolicyId() == null ? "(linear)" : b.taxPolicyId())
                            .param("b", String.valueOf(b.maxGoldPerResidentPerDay()))
                            .param("c", String.valueOf(b.loadedResidentCount()))
                    );
                    for (VillagerTaxLine line : b.lines()) {
                        playerRef.sendMessage(
                            Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.debug.villagerLine")
                                .param("name", line.displayName())
                                .param("kind", line.bindingKind())
                                .param("comfort", String.format(Locale.US, "%.0f%%", line.needsRatio() * 100f))
                                .param("gold", String.valueOf(line.contributionGold()))
                        );
                    }
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.debug.state4")
                            .param("a", String.valueOf(b.sumBeforeTownMultipliers()))
                            .param("b", String.valueOf(b.founderMonumentActive()))
                            .param("c", String.valueOf(b.founderMonumentPermille()))
                            .param("d", String.valueOf(b.sumAfterFounderMonument()))
                    );
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.debug.state5")
                            .param("a", String.valueOf(b.stewardsFeastTaxActive()))
                            .param("b", String.valueOf(b.feastTaxBonusPermille()))
                            .param("c", String.valueOf(b.finalTotal()))
                    );
                }
            );
        }
    }

    /**
     * Credits the treasury with the same math as a morning tick, without requiring the morning window. Ignores
     * "already collected this day" so you can recover if automatic tithe was skipped (debug).
     */
    private static final class NowCommand extends AbstractPlayerCommand {
        NowCommand() {
            super("now", "aetherhaven_commands_help.commands.aetherhaven.tax.now.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null || !AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            TownRecord town = townForQuestPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noTownInWorld"));
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.noQuestPermission"));
                return;
            }
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(
                () -> {
                    long r =
                        TownTaxService.forceApplyTitheNow(
                            world, plugin, es, town, true, true
                        );
                    if (r == -1L) {
                        playerRef.sendMessage(
                            Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.debug.titheNoHall")
                        );
                    } else if (r == 0L) {
                        playerRef.sendMessage(
                            Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.debug.titheZero")
                        );
                    } else {
                        playerRef.sendMessage(
                            Message.translation("aetherhaven_ui_shell.aetherhaven.ui.treasury.debug.titheApplied")
                                .param("amount", String.valueOf(r))
                        );
                    }
                }
            );
        }
    }
}
