package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.worldnpc.WorldNpcPlacementRecord;
import com.hexvane.aetherhaven.worldnpc.WorldNpcService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Spawn helpers for town independent world NPCs. Configuration belongs in assets / {@link WorldNpcService}. */
public final class AetherhavenWorldNpcCommand extends AbstractCommandCollection {
    public AetherhavenWorldNpcCommand() {
        super("worldnpc", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new PlaceCommand());
        this.addSubCommand(new EnsureCommand());
        this.addSubCommand(new RemoveCommand());
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new TpCommand());
        this.addSubCommand(new SetPoseCommand());
    }

    @Nonnull
    private static WorldNpcService service(@Nonnull AetherhavenPlugin plugin) {
        return plugin.getWorldNpcService();
    }

    private static boolean denyUnlessEditor(@Nonnull PlayerRef playerRef) {
        if (playerRef.hasPermission(AetherhavenConstants.PERMISSION_WORLD_NPC)
            || playerRef.hasPermission(AetherhavenConstants.PERMISSION_WORLD_EDITOR)) {
            return false;
        }
        playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.worldnpc.noPermission"));
        return true;
    }

    private static final class PlaceCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> idArg =
            this.withRequiredArg("id", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.id.desc", ArgTypes.STRING);
        @Nonnull
        private final RequiredArg<String> roleArg =
            this.withRequiredArg("role", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.role.desc", ArgTypes.STRING);

        PlaceCommand() {
            super("place", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.place.desc");
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
            if (plugin == null || denyUnlessEditor(playerRef)) {
                return;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                return;
            }
            Vector3d pos = tc.getPosition();
            float yaw = (float) Math.toDegrees(tc.getRotation().yaw());
            String id = context.get(idArg).trim();
            String role = context.get(roleArg).trim();
            service(plugin).place(world, id, role, pos.x, pos.y, pos.z, yaw);
            playerRef.sendMessage(
                Message.translation("aetherhaven_commands_help.aetherhaven.worldnpc.placed")
                    .param("id", id)
                    .param("role", role)
            );
        }
    }

    private static final class EnsureCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> idArg =
            this.withRequiredArg("id", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.id.desc", ArgTypes.STRING);

        EnsureCommand() {
            super("ensure", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.ensure.desc");
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
            if (plugin == null || denyUnlessEditor(playerRef)) {
                return;
            }
            String id = context.get(idArg).trim();
            UUID uuid = service(plugin).ensure(world, id);
            playerRef.sendMessage(
                Message.translation("aetherhaven_commands_help.aetherhaven.worldnpc.ensured")
                    .param("id", id)
                    .param("uuid", uuid != null ? uuid.toString() : "(failed)")
            );
        }
    }

    private static final class RemoveCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> idArg =
            this.withRequiredArg("id", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.id.desc", ArgTypes.STRING);

        RemoveCommand() {
            super("remove", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.remove.desc");
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
            if (plugin == null || denyUnlessEditor(playerRef)) {
                return;
            }
            String id = context.get(idArg).trim();
            service(plugin).remove(world, id);
            playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.worldnpc.removed").param("id", id));
        }
    }

    private static final class ListCommand extends AbstractPlayerCommand {
        ListCommand() {
            super("list", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.list.desc");
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
            if (plugin == null || denyUnlessEditor(playerRef)) {
                return;
            }
            var list = service(plugin).listPlacements(world);
            playerRef.sendMessage(
                Message.translation("aetherhaven_commands_help.aetherhaven.worldnpc.listHeader")
                    .param("count", String.valueOf(list.size()))
            );
            for (WorldNpcPlacementRecord p : list) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_commands_help.aetherhaven.worldnpc.listLine")
                        .param("id", p.placementIdOrEmpty())
                        .param("role", p.npcRoleIdOrEmpty())
                        .param("mode", p.scheduleModeOrDefault().wireName())
                        .param(
                            "xyz",
                            String.format(Locale.US, "%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ())
                        )
                );
            }
        }
    }

    private static final class TpCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> idArg =
            this.withRequiredArg("id", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.id.desc", ArgTypes.STRING);

        TpCommand() {
            super("tp", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.tp.desc");
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
            if (plugin == null || denyUnlessEditor(playerRef)) {
                return;
            }
            WorldNpcPlacementRecord p = service(plugin).getPlacement(world, context.get(idArg).trim());
            if (p == null) {
                playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.worldnpc.missing"));
                return;
            }
            Vector3d pos = new Vector3d(p.getX(), p.getY(), p.getZ());
            Rotation3f facing = new Rotation3f(0f, (float) Math.toRadians(p.getYawDegrees()), 0f);
            store.addComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(world, pos, facing));
            playerRef.sendMessage(
                Message.translation("aetherhaven_commands_help.aetherhaven.worldnpc.teleported").param("id", p.placementIdOrEmpty())
            );
        }
    }

    private static final class SetPoseCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> idArg =
            this.withRequiredArg("id", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.id.desc", ArgTypes.STRING);

        SetPoseCommand() {
            super("setpose", "aetherhaven_commands_help.commands.aetherhaven.worldnpc.setpose.desc");
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
            if (plugin == null || denyUnlessEditor(playerRef)) {
                return;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                return;
            }
            String id = context.get(idArg).trim();
            service(plugin).setPoseFromPlayer(world, id, tc);
            playerRef.sendMessage(Message.translation("aetherhaven_commands_help.aetherhaven.worldnpc.poseSet").param("id", id));
        }
    }
}
