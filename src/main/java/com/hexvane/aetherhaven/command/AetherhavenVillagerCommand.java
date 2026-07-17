package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.VillagerTownResetService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.CoreCitizenVillagerEligibility;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.builtin.teleport.components.TeleportHistory;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Villager helpers: list entity UUIDs, locate NPCs, optional operator-only teleport.
 * Debug helpers; normal command permissions apply.
 */
public final class AetherhavenVillagerCommand extends AbstractCommandCollection {
    public AetherhavenVillagerCommand() {
        super("villager", "aetherhaven_commands_help.commands.aetherhaven.villager.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new LocateSubCommand());
        this.addSubCommand(new FixInnSubCommand());
        this.addSubCommand(new ResetSubCommand());
        this.addSubCommand(new RespawnSubCommand());
        this.addSubCommand(new PurgeSubCommand());
        this.addSubCommand(new AetherhavenStoneStatueTestCommand());
    }

    @Nonnull
    private static String npcRoleIfLoaded(@Nonnull Store<EntityStore> store, @Nonnull UUID npcUuid) {
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(npcUuid);
        if (npcRef == null || !npcRef.isValid()) {
            return "(entity not loaded)";
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        return npc != null && npc.getRoleName() != null ? npc.getRoleName() : "?";
    }

    private static void mergeVillagerNote(@Nonnull Map<UUID, String> notes, @Nullable UUID id, @Nonnull String note) {
        if (id == null) {
            return;
        }
        if (id.getLeastSignificantBits() == 0L && id.getMostSignificantBits() == 0L) {
            return;
        }
        notes.merge(id, note, (a, b) -> a + "; " + b);
    }

    @Nullable
    private static UUID parseUuidString(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final class ListSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        ListSubCommand() {
            super("list", "aetherhaven_commands_help.commands.aetherhaven.villager.list.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
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
            TownCommandResolution res = townTarget.resolve(context, world, store, ref, playerRef, true);
            if (!res.isOk()) {
                playerRef.sendMessage(res.error());
                return;
            }
            TownRecord town = res.townOrThrow();
            Map<UUID, String> notes = new LinkedHashMap<>();
            mergeVillagerNote(notes, town.getElderEntityUuid(), "Elder");
            mergeVillagerNote(notes, town.getInnkeeperEntityUuid(), "Innkeeper");
            for (String poolId : town.getInnPoolNpcIds()) {
                mergeVillagerNote(notes, parseUuidString(poolId), "Inn visitor");
            }
            for (String lockedId : town.getInnLockedEntityUuids()) {
                mergeVillagerNote(notes, parseUuidString(lockedId), "Inn locked quest");
            }
            for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
                String label = "Resident (" + r.getKind() + " / " + r.getNpcRoleId() + ")";
                mergeVillagerNote(notes, r.getLastEntityUuid(), label);
            }
            for (PlotInstance pi : town.getPlotInstances()) {
                String cid = pi.getConstructionId() != null && !pi.getConstructionId().isBlank()
                    ? pi.getConstructionId()
                    : "plot";
                for (UUID home : pi.getHomeResidentEntityUuids()) {
                    mergeVillagerNote(notes, home, "Home (" + cid + ")");
                }
            }
            if (notes.isEmpty()) {
                playerRef.sendMessage(Message.translation("aetherhaven_quests_portals.aetherhaven.villager.noEntityIds"));
                return;
            }
            playerRef.sendMessage(Message.translation("aetherhaven_quests_portals.aetherhaven.villager.listHeader"));
            for (Map.Entry<UUID, String> e : notes.entrySet()) {
                UUID id = e.getKey();
                String live = npcRoleIfLoaded(store, id);
                playerRef.sendMessage(
                    Message.translation("aetherhaven_quests_portals.aetherhaven.villager.listRow")
                        .param("uuid", id.toString())
                        .param("note", e.getValue())
                        .param("role", live)
                );
            }
        }
    }

    private static final class LocateSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> villagerArg =
            this.withRequiredArg("villager", "aetherhaven_commands_help.commands.aetherhaven.villager.target.desc", ArgTypes.STRING);
        @Nonnull
        private final OptionalArg<Boolean> teleportArg =
            this.withOptionalArg("teleport", "aetherhaven_commands_help.commands.aetherhaven.villager.teleport.desc", ArgTypes.BOOLEAN);
        /** Same as {@code teleport true}; easier in chat than a trailing boolean. */
        @Nonnull
        private final FlagArg teleportFlag = this.withFlagArg("tp", "aetherhaven_commands_help.commands.aetherhaven.villager.tp_flag.desc");
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        LocateSubCommand() {
            super("locate", "aetherhaven_commands_help.commands.aetherhaven.villager.locate.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
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
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            TownCommandResolution townRes = townTarget.resolve(context, world, store, ref, playerRef, true);
            if (!townRes.isOk()) {
                playerRef.sendMessage(townRes.error());
                return;
            }
            TownRecord town = townRes.townOrThrow();
            TownVillagerTargetResolver.Outcome target =
                TownVillagerTargetResolver.resolve(town, world, store, context.get(villagerArg));
            if (!target.isOk()) {
                if (target.error() != null) {
                    playerRef.sendMessage(Message.raw(target.error()));
                } else {
                    playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.invalidVillager"));
                }
                return;
            }
            UUID npcUuid = target.villagerUuid();
            Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(npcUuid);
            if (npcRef == null || !npcRef.isValid()) {
                playerRef.sendMessage(Message.translation("aetherhaven_quests_portals.aetherhaven.villager.locateNotLoaded"));
                return;
            }
            TransformComponent npcTc = store.getComponent(npcRef, TransformComponent.getComponentType());
            if (npcTc == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_quests_portals.aetherhaven.villager.locateNoTransform"));
                return;
            }
            Vector3d p = npcTc.getPosition();
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            String role = npc != null && npc.getRoleName() != null ? npc.getRoleName() : "?";
            playerRef.sendMessage(
                Message.translation("aetherhaven_quests_portals.aetherhaven.villager.locatePosition")
                    .param("uuid", npcUuid.toString())
                    .param("role", role)
                    .param("x", String.format(Locale.US, "%.2f", p.x))
                    .param("y", String.format(Locale.US, "%.2f", p.y))
                    .param("z", String.format(Locale.US, "%.2f", p.z))
            );

            boolean doTp =
                context.provided(teleportFlag)
                    || (context.provided(teleportArg) && Boolean.TRUE.equals(context.get(teleportArg)));
            if (!doTp) {
                return;
            }
            if (!PermissionsModule.get().getGroupsForUser(uc.getUuid()).contains(HytalePermissionsProvider.GROUP_ADMIN)) {
                playerRef.sendMessage(Message.translation("aetherhaven_quests_portals.aetherhaven.villager.locateOpRequired"));
                return;
            }
            HeadRotation npcHr = store.getComponent(npcRef, HeadRotation.getComponentType());
            Rotation3f facing =
                new Rotation3f(npcHr != null ? npcHr.getRotation() : npcTc.getRotation());
            Teleport teleportComponent = Teleport.createForPlayer(world, new Vector3d(p), facing);
            TransformComponent playerTc = store.getComponent(ref, TransformComponent.getComponentType());
            HeadRotation playerHr = store.getComponent(ref, HeadRotation.getComponentType());
            if (playerTc != null && playerHr != null) {
                store.ensureAndGetComponent(ref, TeleportHistory.getComponentType())
                    .append(
                        world,
                        new Vector3d(playerTc.getPosition()),
                        new Rotation3f(playerHr.getRotation()),
                        "Aetherhaven villager locate"
                    );
            }
            store.addComponent(ref, Teleport.getComponentType(), teleportComponent);
            playerRef.sendMessage(Message.translation("aetherhaven_quests_portals.aetherhaven.villager.teleported"));
        }
    }

    private static final class ResetSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        ResetSubCommand() {
            super("reset", "aetherhaven_commands_help.commands.aetherhaven.villager.reset.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
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
            TownCommandResolution res = townTarget.resolve(context, world, store, ref, playerRef, true);
            if (!res.isOk()) {
                playerRef.sendMessage(res.error());
                return;
            }
            TownRecord town = res.townOrThrow();
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.villager.resetFailed").param("reason", "No player position."));
                return;
            }
            Vector3d base = new Vector3d(tc.getPosition());
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String err = VillagerTownResetService.resetAllTownVillagersNearPlayer(world, plugin, town, tm, store, base);
            if (err != null) {
                playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.villager.resetFailed").param("reason", err));
                return;
            }
            playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.villager.resetDone"));
        }
    }

    private static final class RespawnSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> villagerArg =
            this.withRequiredArg("villager", "aetherhaven_commands_help.commands.aetherhaven.villager.target.desc", ArgTypes.STRING);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        RespawnSubCommand() {
            super("respawn", "aetherhaven_commands_help.commands.aetherhaven.villager.respawn.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
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
            TownCommandResolution res = townTarget.resolve(context, world, store, ref, playerRef, true);
            if (!res.isOk()) {
                playerRef.sendMessage(res.error());
                return;
            }
            TownRecord town = res.townOrThrow();
            TownVillagerTargetResolver.Outcome target =
                TownVillagerTargetResolver.resolve(town, world, store, context.get(villagerArg));
            if (!target.isOk()) {
                if (target.error() != null) {
                    playerRef.sendMessage(Message.raw(target.error()));
                } else {
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_commands_help.aetherhaven.villager.respawnNotCitizen")
                    );
                }
                return;
            }
            CoreCitizenVillagerEligibility.Outcome citizen =
                CoreCitizenVillagerEligibility.resolveCoreCitizen(town, world, store, target.villagerUuid());
            if (!citizen.isOk()) {
                String reason = citizen.error();
                playerRef.sendMessage(
                    Message.translation("aetherhaven_commands_help.aetherhaven.villager.respawnNotCitizen")
                        .param("reason", reason != null ? reason : "")
                );
                return;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_commands_help.aetherhaven.villager.respawnFailed")
                        .param("reason", "No player position.")
                );
                return;
            }
            Vector3d base = new Vector3d(tc.getPosition());
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String err = VillagerTownResetService.resetOneCoreCitizenNearPlayer(
                world,
                plugin,
                town,
                tm,
                store,
                target.villagerUuid(),
                base
            );
            if (err != null) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_commands_help.aetherhaven.villager.respawnFailed").param("reason", err)
                );
                return;
            }
            playerRef.sendMessage(
                Message.translation("aetherhaven_commands_help.aetherhaven.villager.respawnDone")
                    .param("role", citizen.profile().roleId())
            );
        }
    }

    private static final class FixInnSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        FixInnSubCommand() {
            super("fixinn", "aetherhaven_commands_help.commands.aetherhaven.villager.fixinn.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
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
            TownCommandResolution res = townTarget.resolve(context, world, store, ref, playerRef, true);
            if (!res.isOk()) {
                playerRef.sendMessage(res.error());
                return;
            }
            TownRecord town = res.townOrThrow();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            InnPoolService.RepairReport report = InnPoolService.repairInnPoolForTown(world, plugin, town, tm, store, true);
            playerRef.sendMessage(
                Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.journalSettings.innRepairDone")
                    .param("locked", String.valueOf(report.getLockedQuestVisitors()))
                    .param("promoted", String.valueOf(report.getPromotedResidents()))
                    .param("removed", String.valueOf(report.getRemovedPoolEntries()))
            );
        }
    }

    /**
     * Emergency: delete every loaded NPC of a role in this world (no binding filter). Town save data is
     * unchanged so respawn/reset can restore the tracked villager.
     */
    private static final class PurgeSubCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> roleArg =
            this.withRequiredArg("role", "aetherhaven_commands_help.commands.aetherhaven.villager.purge.role.desc", ArgTypes.STRING);

        PurgeSubCommand() {
            super("purge", "aetherhaven_commands_help.commands.aetherhaven.villager.purge.desc");
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
            String roleId = context.get(roleArg);
            if (roleId == null || roleId.isBlank()) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_commands_help.aetherhaven.villager.purgeFailed")
                        .param("reason", "Role id is required (example Aetherhaven_Florist).")
                );
                return;
            }
            String role = roleId.trim();
            int removed = VillagerTownResetService.purgeAllLoadedNpcsByRole(world, store, role);
            playerRef.sendMessage(
                Message.translation("aetherhaven_commands_help.aetherhaven.villager.purgeDone")
                    .param("role", role)
                    .param("count", String.valueOf(removed))
            );
        }
    }
}
