package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.VillagerTownResetService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.builtin.teleport.components.TeleportHistory;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
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
        super("villager", "server.commands.aetherhaven.villager.desc");
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new LocateSubCommand());
        this.addSubCommand(new FixInnSubCommand());
        this.addSubCommand(new ResetSubCommand());
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
        ListSubCommand() {
            super("list", "server.commands.aetherhaven.villager.list.desc");
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
            TownRecord town = townForQuestPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noTownInWorld"));
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noQuestPermission"));
                return;
            }
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
                UUID home = pi.getHomeResidentEntityUuid();
                if (home != null) {
                    String cid = pi.getConstructionId() != null && !pi.getConstructionId().isBlank()
                        ? pi.getConstructionId()
                        : "plot";
                    mergeVillagerNote(notes, home, "Home (" + cid + ")");
                }
            }
            if (notes.isEmpty()) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.villager.noEntityIds"));
                return;
            }
            playerRef.sendMessage(Message.translation("server.aetherhaven.villager.listHeader"));
            for (Map.Entry<UUID, String> e : notes.entrySet()) {
                UUID id = e.getKey();
                String live = npcRoleIfLoaded(store, id);
                playerRef.sendMessage(
                    Message.translation("server.aetherhaven.villager.listRow")
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
            this.withRequiredArg("villager", "server.commands.aetherhaven.villager.target.desc", ArgTypes.STRING);
        @Nonnull
        private final OptionalArg<Boolean> teleportArg =
            this.withOptionalArg("teleport", "server.commands.aetherhaven.villager.teleport.desc", ArgTypes.BOOLEAN);
        /** Same as {@code teleport true}; easier in chat than a trailing boolean. */
        @Nonnull
        private final FlagArg teleportFlag = this.withFlagArg("tp", "server.commands.aetherhaven.villager.tp_flag.desc");

        LocateSubCommand() {
            super("locate", "server.commands.aetherhaven.villager.locate.desc");
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
            TownRecord town = townForQuestPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noTownInWorld"));
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noQuestPermission"));
                return;
            }
            TownVillagerTargetResolver.Outcome target =
                TownVillagerTargetResolver.resolve(town, world, store, context.get(villagerArg));
            if (!target.isOk()) {
                if (target.error() != null) {
                    playerRef.sendMessage(Message.raw(target.error()));
                } else {
                    playerRef.sendMessage(Message.translation("server.aetherhaven.common.invalidVillager"));
                }
                return;
            }
            UUID npcUuid = target.villagerUuid();
            Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(npcUuid);
            if (npcRef == null || !npcRef.isValid()) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.villager.locateNotLoaded"));
                return;
            }
            TransformComponent npcTc = store.getComponent(npcRef, TransformComponent.getComponentType());
            if (npcTc == null) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.villager.locateNoTransform"));
                return;
            }
            Vector3d p = npcTc.getPosition();
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            String role = npc != null && npc.getRoleName() != null ? npc.getRoleName() : "?";
            playerRef.sendMessage(
                Message.translation("server.aetherhaven.villager.locatePosition")
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
            if (!PermissionsModule.get().getGroupsForUser(uc.getUuid()).contains(HytalePermissionsProvider.OP_GROUP)) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.villager.locateOpRequired"));
                return;
            }
            HeadRotation npcHr = store.getComponent(npcRef, HeadRotation.getComponentType());
            Vector3f facing = npcHr != null ? npcHr.getRotation().clone() : npcTc.getRotation().clone();
            Teleport teleportComponent = Teleport.createForPlayer(world, p.clone(), facing);
            TransformComponent playerTc = store.getComponent(ref, TransformComponent.getComponentType());
            HeadRotation playerHr = store.getComponent(ref, HeadRotation.getComponentType());
            if (playerTc != null && playerHr != null) {
                store.ensureAndGetComponent(ref, TeleportHistory.getComponentType())
                    .append(
                        world,
                        playerTc.getPosition().clone(),
                        playerHr.getRotation().clone(),
                        "Aetherhaven villager locate"
                    );
            }
            store.addComponent(ref, Teleport.getComponentType(), teleportComponent);
            playerRef.sendMessage(Message.translation("server.aetherhaven.villager.teleported"));
        }
    }

    private static final class ResetSubCommand extends AbstractPlayerCommand {
        ResetSubCommand() {
            super("reset", "server.commands.aetherhaven.villager.reset.desc");
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
            TownRecord town = townForQuestPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noTownInWorld"));
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noQuestPermission"));
                return;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.villager.resetFailed").param("reason", "No player position."));
                return;
            }
            Vector3d base = tc.getPosition().clone();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            String err = VillagerTownResetService.resetAllTownVillagersNearPlayer(world, plugin, town, tm, store, base);
            if (err != null) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.villager.resetFailed").param("reason", err));
                return;
            }
            playerRef.sendMessage(Message.translation("server.aetherhaven.villager.resetDone"));
        }
    }

    private static final class FixInnSubCommand extends AbstractPlayerCommand {
        FixInnSubCommand() {
            super("fixinn", "server.commands.aetherhaven.villager.desc");
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
            TownRecord town = townForQuestPlayer(store, ref, world);
            if (town == null) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noTownInWorld"));
                return;
            }
            if (!town.playerHasQuestPermission(uc.getUuid())) {
                playerRef.sendMessage(Message.translation("server.aetherhaven.common.noQuestPermission"));
                return;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            InnPoolService.RepairReport report = InnPoolService.repairInnPoolForTown(world, plugin, town, tm, store);
            playerRef.sendMessage(
                Message.raw(
                    "Inn repair complete: locked quest visitors="
                        + report.getLockedQuestVisitors()
                        + ", promoted to residents="
                        + report.getPromotedResidents()
                        + ", removed non-visitor entries="
                        + report.getRemovedPoolEntries()
                )
            );
        }
    }
}
