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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Debug: show dawn-aligned morning tax math for your town (loaded residents only).
 * Requires {@link AetherhavenPlugin#getConfig()}{@code .isDebugCommandsEnabled()}.
 */
public final class AetherhavenTaxCommand extends AbstractCommandCollection {
    public AetherhavenTaxCommand() {
        super("tax", "server.commands.aetherhaven.tax.desc");
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
            super("breakdown", "server.commands.aetherhaven.tax.breakdown.desc");
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
                playerRef.sendMessage(Message.raw("No town for you in this world."));
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.raw("You do not have quest permission in this town."));
                return;
            }
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(
                () -> {
                    TaxMorningBreakdown b =
                        TownTaxService.computeTaxMorningBreakdown(town, es, plugin.getConfig().get());
                    playerRef.sendMessage(
                        Message.raw(
                            "Tax breakdown (loaded chunks only; unloaded residents contribute 0 until simulated):"
                        )
                    );
                    playerRef.sendMessage(
                        Message.raw(
                            "townHallComplete="
                                + b.townHallComplete()
                                + " morningWindow="
                                + b.morningTaxWindow()
                                + " dawnEpochDay="
                                + b.dawnAlignedEpochDay()
                                + " treasuryLastTaxDay="
                                + (b.treasuryLastTaxEpochDay() == null ? "null" : b.treasuryLastTaxEpochDay())
                                + " wouldCollectNextMorningTick="
                                + b.wouldCollectGoldOnNextMorningTick()
                        )
                    );
                    playerRef.sendMessage(
                        Message.raw(
                            "policy="
                                + (b.taxPolicyId() == null ? "(linear)" : b.taxPolicyId())
                                + " maxPerResident="
                                + b.maxGoldPerResidentPerDay()
                                + " loadedResidents="
                                + b.loadedResidentCount()
                        )
                    );
                    for (VillagerTaxLine line : b.lines()) {
                        playerRef.sendMessage(
                            Message.raw(
                                "  "
                                    + line.displayName()
                                    + " ("
                                    + line.bindingKind()
                                    + ") comfort="
                                    + String.format("%.0f%%", line.needsRatio() * 100f)
                                    + " gold="
                                    + line.contributionGold()
                            )
                        );
                    }
                    playerRef.sendMessage(
                        Message.raw(
                            "sumResidents="
                                + b.sumBeforeTownMultipliers()
                                + " founderActive="
                                + b.founderMonumentActive()
                                + " founderPermille="
                                + b.founderMonumentPermille()
                                + " afterFounder="
                                + b.sumAfterFounderMonument()
                        )
                    );
                    playerRef.sendMessage(
                        Message.raw(
                            "stewardsFeastTax="
                                + b.stewardsFeastTaxActive()
                                + " feastPermille="
                                + b.feastTaxBonusPermille()
                                + " finalTotal="
                                + b.finalTotal()
                        )
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
            super("now", "server.commands.aetherhaven.tax.now.desc");
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
                playerRef.sendMessage(Message.raw("No town for you in this world."));
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.raw("You do not have quest permission in this town."));
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
                        playerRef.sendMessage(Message.raw("Tithe: no complete town hall in this town."));
                    } else if (r == 0L) {
                        playerRef.sendMessage(
                            Message.raw(
                                "Tithe: 0 gold (no loaded residents, or 0 from policy math). Unload/needs/breakdown: /aetherhaven tax breakdown"
                            )
                        );
                    } else {
                        playerRef.sendMessage(
                            Message.raw("Tithe applied: +" + r + " gold to treasury (same rules as a morning collection).")
                        );
                    }
                }
            );
        }
    }
}
