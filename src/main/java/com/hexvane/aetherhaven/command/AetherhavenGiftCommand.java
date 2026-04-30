package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.VillagerGiftLogEntry;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Debug: reset villager gift limits, or seed gift log entries for history UI. Requires
 * Normal command permissions apply.
 */
public final class AetherhavenGiftCommand extends AbstractCommandCollection {
    public AetherhavenGiftCommand() {
        super("gift", "server.commands.aetherhaven.gift.desc");
        this.addSubCommand(new ResetLimitsCommand());
        this.addSubCommand(new FillHistoryCommand());
    }

    @Nullable
    private static TownRecord townForPlayer(
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

    private static int resetGiftLimitsInTown(@Nonnull TownRecord town) {
        town.migrateVillagerReputationIfNeeded();
        int n = 0;
        for (Map<String, VillagerReputationEntry> inner : town.getPlayerVillagerReputation().values()) {
            if (inner == null) {
                continue;
            }
            for (VillagerReputationEntry e : inner.values()) {
                if (e == null) {
                    continue;
                }
                if (e.getLastGiftGameEpochDay() == null
                    && e.getGiftWeekBlockId() == null
                    && e.getGiftsThisWeekBlock() == 0) {
                    continue;
                }
                e.setLastGiftGameEpochDay(null);
                e.setGiftWeekBlockId(null);
                e.setGiftsThisWeekBlock(0);
                n++;
            }
        }
        return n;
    }

    private static final class ResetLimitsCommand extends AbstractPlayerCommand {
        ResetLimitsCommand() {
            super("resetLimits", "server.commands.aetherhaven.gift.resetLimits.desc");
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
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            int totalEntries = 0;
            int townsChanged = 0;
            for (TownRecord t : tm.allTowns()) {
                int n = resetGiftLimitsInTown(t);
                if (n > 0) {
                    totalEntries += n;
                    townsChanged++;
                    tm.updateTown(t);
                }
            }
            playerRef.sendMessage(
                Message
                    .translation("server.aetherhaven.debug.gift.resetDone")
                    .param("entries", String.valueOf(totalEntries))
                    .param("towns", String.valueOf(townsChanged))
            );
        }
    }

    private static final class FillHistoryCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> roleArg =
            this.withRequiredArg("roleId", "server.commands.aetherhaven.gift.roleId.desc", ArgTypes.STRING);

        FillHistoryCommand() {
            super("fillHistory", "server.commands.aetherhaven.gift.fillHistory.desc");
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
            TownRecord town = townForPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noTownInWorld"));
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            String role = context.get(roleArg).trim();
            if (role.isEmpty()) {
                return;
            }
            VillagerDefinition def = plugin.getVillagerDefinitionCatalog().byNpcRoleId(role);
            if (def == null) {
                playerRef.sendMessage(
                    Message.translation("server.aetherhaven.debug.gift.unknownRole").param("role", role)
                );
                return;
            }
            List<String> loves = def.getGiftLoves();
            List<String> likes = def.getGiftLikes();
            List<String> dislikes = def.getGiftDislikes();
            if (loves.isEmpty() && likes.isEmpty() && dislikes.isEmpty()) {
                playerRef.sendMessage(
                    Message.translation("server.aetherhaven.debug.gift.noGiftLists").param("role", role)
                );
                return;
            }
            long day = VillagerReputationService.currentGameEpochDay(store);
            String giver = uc.getUuid().toString();
            List<VillagerGiftLogEntry> log = town.getVillagerGiftLogByRoleId().getOrDefault(role, List.of());
            int added = 0;
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            added += appendIfMissing(town, log, role, giver, day, loves, GiftPreference.LOVE);
            added += appendIfMissing(town, log, role, giver, day, likes, GiftPreference.LIKE);
            added += appendIfMissing(town, log, role, giver, day, dislikes, GiftPreference.DISLIKE);
            tm.updateTown(town);
            playerRef.sendMessage(
                Message
                    .translation("server.aetherhaven.debug.gift.fillDone")
                    .param("role", role)
                    .param("added", String.valueOf(added))
            );
        }

        private static int appendIfMissing(
            @Nonnull TownRecord town,
            @Nonnull List<VillagerGiftLogEntry> log,
            @Nonnull String role,
            @Nonnull String giver,
            long day,
            @Nonnull List<String> ids,
            @Nonnull GiftPreference tier
        ) {
            int a = 0;
            for (String id : ids) {
                if (id == null) {
                    continue;
                }
                String item = id.trim();
                if (item.isEmpty() || hasGiverItemTier(log, giver, item, tier)) {
                    continue;
                }
                town.appendVillagerGiftLog(role, new VillagerGiftLogEntry(item, tier, giver, day));
                a++;
            }
            return a;
        }

        private static boolean hasGiverItemTier(
            @Nonnull List<VillagerGiftLogEntry> log,
            @Nonnull String giver,
            @Nonnull String item,
            @Nonnull GiftPreference tier
        ) {
            for (VillagerGiftLogEntry e : log) {
                if (e == null) {
                    continue;
                }
                if (giver.equals(e.getGiverPlayerUuid()) && item.equals(e.getItemId()) && e.getTier() == tier) {
                    return true;
                }
            }
            return false;
        }
    }
}
