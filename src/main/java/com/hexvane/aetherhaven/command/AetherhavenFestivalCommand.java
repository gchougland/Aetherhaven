package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPlotProtection;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.festival.carnival.CarnivalIds;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWheelPlacementService;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWheelSession;
import com.hexvane.aetherhaven.festival.carnival.CarnivalWheelSessionIndex;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGiftService;
import com.hexvane.aetherhaven.festival.wintertide.WintertideTarget;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.VillagerTownResetService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.audit.VillagerAuditContext;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Admin helpers for testing festivals without waiting for the calendar. */
public final class AetherhavenFestivalCommand extends AbstractCommandCollection {
    private static final String LANG = "aetherhaven_festivals.aetherhaven.festival.command.";

    public AetherhavenFestivalCommand() {
        super("festival", "aetherhaven_commands_help.commands.aetherhaven.festival.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new StartCommand());
        this.addSubCommand(new EndCommand());
        this.addSubCommand(new BuildCommand());
        this.addSubCommand(new ResetClownCommand());
        this.addSubCommand(new WheelForceCommand());
        this.addSubCommand(new WintertideAssignCommand());
    }

    private static final class ListCommand extends AbstractPlayerCommand {
        ListCommand() {
            super("list", "aetherhaven_commands_help.commands.aetherhaven.festival.list.desc");
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
            if (plugin == null) {
                return;
            }
            var all = plugin.getFestivalCatalog().list();
            if (all.isEmpty()) {
                playerRef.sendMessage(Message.translation(LANG + "list.empty"));
                return;
            }
            playerRef.sendMessage(Message.translation(LANG + "list.header"));
            for (FestivalDefinition def : all) {
                playerRef.sendMessage(
                    Message.translation(LANG + "list.row")
                        .param("id", def.getId())
                        .param("name", FestivalService.festivalName(def))
                        .param("season", def.getSeason().displayName())
                        .param("day", String.valueOf(def.getDayOfSeason()))
                );
            }
        }
    }

    private static final class StartCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> idArg =
            this.withRequiredArg(
                "id",
                "aetherhaven_commands_help.commands.aetherhaven.festival.start.id.desc",
                ArgTypes.STRING
            );

        StartCommand() {
            super("start", "aetherhaven_commands_help.commands.aetherhaven.festival.start.desc");
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
            if (plugin == null) {
                return;
            }
            String id = context.get(idArg).trim();
            FestivalDefinition def = plugin.getFestivalCatalog().get(id);
            if (def == null) {
                playerRef.sendMessage(Message.translation(LANG + "unknown").param("id", id));
                return;
            }
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(() -> {
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = resolveTown(world, es, ref, tm, playerRef);
                if (town == null) {
                    return;
                }
                PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
                if (square == null) {
                    playerRef.sendMessage(Message.translation(LANG + "noSquare"));
                    return;
                }
                if (town.getActiveFestivalId() != null) {
                    FestivalService.endFestival(world, es, plugin, tm, town);
                }
                LocalDateTime gameTime = gameTime(es);
                long epochMinute = FestivalService.toEpochMinute(gameTime);
                if (FestivalService.startFestival(world, es, plugin, tm, town, def, gameTime, epochMinute)) {
                    playerRef.sendMessage(
                        Message.translation(LANG + "started").param("name", FestivalService.festivalName(def))
                    );
                } else {
                    playerRef.sendMessage(Message.translation(LANG + "noSquare"));
                }
            });
        }
    }

    private static final class EndCommand extends AbstractPlayerCommand {
        EndCommand() {
            super("end", "aetherhaven_commands_help.commands.aetherhaven.festival.end.desc");
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
            if (plugin == null) {
                return;
            }
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(() -> {
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = resolveTown(world, es, ref, tm, playerRef);
                if (town == null) {
                    return;
                }
                if (town.getActiveFestivalId() == null) {
                    playerRef.sendMessage(Message.translation(LANG + "noneRunning"));
                    return;
                }
                FestivalService.endFestival(world, es, plugin, tm, town);
                playerRef.sendMessage(Message.translation(LANG + "ended"));
            });
        }
    }

    private static final class BuildCommand extends AbstractPlayerCommand {
        BuildCommand() {
            super("build", "aetherhaven_commands_help.commands.aetherhaven.festival.build.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!playerRef.hasPermission(AetherhavenConstants.PERMISSION_FESTIVAL_SQUARE_BUILD, false)) {
                playerRef.sendMessage(Message.translation(LANG + "build.noPermission"));
                return;
            }
            UUID uuid = playerRef.getUuid();
            if (uuid == null) {
                return;
            }
            boolean allowed = FestivalPlotProtection.toggleBuildAllowed(uuid);
            playerRef.sendMessage(
                Message.translation(allowed ? LANG + "build.enabled" : LANG + "build.disabled")
            );
        }
    }

    private static final class ResetClownCommand extends AbstractPlayerCommand {
        ResetClownCommand() {
            super("resetclown", "aetherhaven_commands_help.commands.aetherhaven.festival.resetclown.desc");
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
            if (plugin == null) {
                return;
            }
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(() -> {
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = resolveTown(world, es, ref, tm, playerRef);
                if (town == null) {
                    return;
                }
                clearClownQuestState(town);
                ResidentRegistryService.removeByRole(town, tm, AetherhavenConstants.NPC_CLOWN);
                stripClownFromInnPool(town, es);
                int removed = despawnClownNpcs(world, es, town.getTownId());
                CarnivalWheelSession session = CarnivalWheelSessionIndex.getOrCreate(town.getTownId());
                session.clearGameplay();
                CarnivalWheelPlacementService.refreshFaceModel(world, town.getTownId(), true);
                tm.updateTown(town);
                playerRef.sendMessage(
                    Message.translation(LANG + "resetclown.done").param("removed", String.valueOf(removed))
                );
            });
        }
    }

    private static final class WheelForceCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<Integer> octantArg =
            this.withRequiredArg(
                "octant",
                "aetherhaven_commands_help.commands.aetherhaven.festival.wheelforce.octant.desc",
                ArgTypes.INTEGER
            );

        WheelForceCommand() {
            super("wheelforce", "aetherhaven_commands_help.commands.aetherhaven.festival.wheelforce.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            Integer raw = context.get(octantArg);
            if (raw == null) {
                return;
            }
            int octant = raw;
            if (octant < 0) {
                CarnivalWheelSession.setForceNextOctant(-1);
                playerRef.sendMessage(Message.translation(LANG + "wheelforce.cleared"));
                return;
            }
            int clamped = Math.floorMod(octant, 8);
            CarnivalWheelSession.setForceNextOctant(clamped);
            playerRef.sendMessage(
                Message.translation(LANG + "wheelforce.set")
                    .param("octant", String.valueOf(clamped))
                    .param("clown", String.valueOf(CarnivalIds.WHEEL_CLOWN_OCTANT))
            );
        }
    }

    private static final class WintertideAssignCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> targetArg =
            this.withRequiredArg(
                "target",
                "aetherhaven_commands_help.commands.aetherhaven.festival.wintertideassign.target.desc",
                AetherhavenArgTypes.WINTERTIDE_ASSIGN_TARGET
            );

        WintertideAssignCommand() {
            super(
                "wintertideassign",
                "aetherhaven_commands_help.commands.aetherhaven.festival.wintertideassign.desc"
            );
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
            if (plugin == null) {
                return;
            }
            String raw = context.get(targetArg);
            if (raw == null || raw.isBlank()) {
                return;
            }
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            UUID selfUuid = uc.getUuid();
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(() -> {
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = resolveTown(world, es, ref, tm, playerRef);
                if (town == null) {
                    return;
                }
                if (!WintertideGiftService.isWintertideActive(town)) {
                    playerRef.sendMessage(Message.translation(LANG + "wintertideassign.notWintertide"));
                    return;
                }
                WintertideTarget target =
                    WintertideGiftService.applyForcedOutgoing(town, es, selfUuid, raw.trim());
                if (target == null) {
                    playerRef.sendMessage(Message.translation(LANG + "wintertideassign.unknown"));
                    return;
                }
                playerRef.sendMessage(
                    Message.translation(LANG + "wintertideassign.done").param("name", target.getDisplayName())
                );
            });
        }
    }

    private static void clearClownQuestState(@Nonnull TownRecord town) {
        String[] quests = {
            AetherhavenConstants.QUEST_CLOWN_RESCUE,
            AetherhavenConstants.QUEST_CLOWN_TENT,
            AetherhavenConstants.QUEST_HOUSE_CLOWN
        };
        for (String qid : quests) {
            town.clearActiveQuest(qid);
            town.clearGlobalQuestCompletion(qid);
        }
    }

    private static void stripClownFromInnPool(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        List<String> toDrop = new ArrayList<>();
        for (String sid : town.getInnPoolNpcIds()) {
            if (sid == null || sid.isBlank()) {
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(sid.trim());
            } catch (IllegalArgumentException e) {
                continue;
            }
            Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(uuid);
            if (npcRef == null || !npcRef.isValid()) {
                continue;
            }
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc != null && AetherhavenConstants.NPC_CLOWN.equals(npc.getRoleName())) {
                toDrop.add(sid);
                town.removeInnLockedEntity(uuid);
            }
        }
        for (String sid : toDrop) {
            town.getInnPoolNpcIds().removeIf(s -> sid.equalsIgnoreCase(s != null ? s.trim() : ""));
        }
    }

    private static int despawnClownNpcs(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId
    ) {
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(TownVillagerBinding.getComponentType(), (archetypeChunk, commandBuffer) -> {
            int n = archetypeChunk.size();
            for (int i = 0; i < n; i++) {
                TownVillagerBinding binding =
                    archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                if (binding == null || !townId.equals(binding.getTownId())) {
                    continue;
                }
                String kind = binding.getKind();
                if (!TownVillagerBinding.KIND_RESCUE_CLOWN.equals(kind)
                    && !TownVillagerBinding.KIND_CLOWN.equals(kind)
                    && !TownVillagerBinding.KIND_VISITOR_CLOWN.equals(kind)) {
                    continue;
                }
                Ref<EntityStore> npcRef = archetypeChunk.getReferenceTo(i);
                if (npcRef != null && npcRef.isValid()) {
                    toRemove.add(npcRef);
                }
            }
        });
        int count = 0;
        for (Ref<EntityStore> npcRef : toRemove) {
            if (npcRef.isValid()) {
                VillagerAuditContext.removeEntity(store, npcRef, "festival_resetclown");
                count++;
            }
        }
        // Catch any unbound leftover roles (e.g. rescue already talked through).
        count += VillagerTownResetService.purgeAllLoadedNpcsByRole(world, store, AetherhavenConstants.NPC_CLOWN);
        count += VillagerTownResetService.purgeAllLoadedNpcsByRole(world, store, AetherhavenConstants.NPC_CLOWN_RESCUE);
        return count;
    }

    @Nullable
    private static TownRecord resolveTown(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TownManager tm,
        @Nonnull PlayerRef playerRef
    ) {
        TownRecord town = TownPlayerResolution.resolveActiveTown(world, store, ref, tm);
        if (town == null) {
            playerRef.sendMessage(Message.translation(LANG + "noTown"));
        }
        return town;
    }

    @Nonnull
    private static LocalDateTime gameTime(@Nonnull Store<EntityStore> store) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        return wtr != null ? wtr.getGameDateTime() : LocalDateTime.now();
    }
}
