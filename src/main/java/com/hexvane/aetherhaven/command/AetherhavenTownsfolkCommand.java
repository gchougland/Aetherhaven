package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolCheckoutRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolPersistence;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolState;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class AetherhavenTownsfolkCommand extends AbstractCommandCollection {
    public AetherhavenTownsfolkCommand() {
        super("townsfolk", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addSubCommand(new SpawnSubCommand());
        this.addSubCommand(new ReleaseSubCommand());
        this.addSubCommand(new ListSubCommand());
        this.addSubCommand(new ClearSubCommand());
    }

    private static void executeSpawn(
        @Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull World world,
        @Nonnull PlayerRef playerRef,
        @Nonnull DebugTownTargetArgs townTarget,
        @Nullable String characterId,
        @Nonnull String assignmentKind
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            ctx.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        TownCommandResolution townRes = townTarget.resolve(ctx, world, store, ref, playerRef, false);
        if (!townRes.isOk()) {
            ctx.sendMessage(townRes.error());
            return;
        }
        TownRecord town = townRes.townOrThrow();
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d pos = new Vector3d(transform.getPosition());
        Optional<TownsfolkSpawnService.SpawnedTownsfolk> spawned =
            TownsfolkSpawnService.trySpawn(
                world,
                plugin,
                town,
                store,
                pos,
                assignmentKind,
                characterId,
                new Random()
            );
        if (spawned.isEmpty()) {
            ctx.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.townsfolk.spawn.failed"));
            return;
        }
        TownsfolkSpawnService.SpawnedTownsfolk s = spawned.get();
        ctx.sendMessage(
            Message.translation("aetherhaven_commands_help.commands.aetherhaven.townsfolk.spawn.ok")
                .param("id", s.characterId())
                .param("assignment", s.assignmentKind())
        );
    }

    private static final class SpawnSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> assignmentArg =
            this.withOptionalArg("assignmentKind", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.assignmentKind.desc", ArgTypes.STRING);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        SpawnSubCommand() {
            super("spawn", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.spawn.desc");
            townTarget = DebugTownTargetArgs.registerOn(this);
            this.addUsageVariant(new SpawnWithIdCommand());
            this.addUsageVariant(new SpawnWithIdAndAssignmentCommand());
        }

        @Override
        protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            String assignment = assignmentArg.provided(ctx) ? assignmentArg.get(ctx) : TownsfolkAssignmentKinds.IDLE;
            executeSpawn(ctx, store, ref, world, playerRef, townTarget, null, assignment);
        }
    }

    /** {@code /aetherhaven townsfolk spawn female_elf_01} */
    private static final class SpawnWithIdCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> idArg =
            this.withRequiredArg("id", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.id.desc", ArgTypes.STRING);
        private final OptionalArg<String> assignmentArg =
            this.withOptionalArg("assignmentKind", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.assignmentKind.desc", ArgTypes.STRING);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        SpawnWithIdCommand() {
            super("aetherhaven_commands_help.commands.aetherhaven.townsfolk.spawn.desc");
            this.setPermissionGroups("hytale:WorldEditor");
            townTarget = DebugTownTargetArgs.registerOn(this);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            String assignment = assignmentArg.provided(ctx) ? assignmentArg.get(ctx) : TownsfolkAssignmentKinds.IDLE;
            executeSpawn(ctx, store, ref, world, playerRef, townTarget, idArg.get(ctx).trim(), assignment);
        }
    }

    /** {@code /aetherhaven townsfolk spawn female_elf_01 tourist} */
    private static final class SpawnWithIdAndAssignmentCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> idArg =
            this.withRequiredArg("id", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.id.desc", ArgTypes.STRING);
        private final RequiredArg<String> assignmentArg =
            this.withRequiredArg("assignmentKind", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.assignmentKind.desc", ArgTypes.STRING);
        @Nonnull
        private final DebugTownTargetArgs townTarget;

        SpawnWithIdAndAssignmentCommand() {
            super("aetherhaven_commands_help.commands.aetherhaven.townsfolk.spawn.desc");
            this.setPermissionGroups("hytale:WorldEditor");
            townTarget = DebugTownTargetArgs.registerOn(this);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            executeSpawn(ctx, store, ref, world, playerRef, townTarget, idArg.get(ctx).trim(), assignmentArg.get(ctx).trim());
        }
    }

    private static final class ReleaseSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> characterIdArg =
            this.withRequiredArg("characterId", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.characterId.desc", ArgTypes.STRING);
        private final FlagArg despawnFlag = this.withFlagArg("despawn", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.despawn.desc");

        ReleaseSubCommand() {
            super("release", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.release.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            String characterId = characterIdArg.get(ctx);
            TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
            TownsfolkPoolCheckoutRecord rec = pool.checkoutForCharacter(characterId);
            if (rec == null) {
                ctx.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.townsfolk.release.notCheckedOut"));
                return;
            }
            if (despawnFlag.provided(ctx)) {
                try {
                    java.util.UUID entityId = java.util.UUID.fromString(rec.getEntityUuid());
                    Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(entityId);
                    if (npcRef != null && npcRef.isValid()) {
                        store.removeEntity(npcRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                    }
                } catch (IllegalArgumentException ignored) {
                    // fall through
                }
            }
            TownsfolkSpawnService.release(world, plugin, characterId);
            ctx.sendMessage(
                Message.translation("aetherhaven_commands_help.commands.aetherhaven.townsfolk.release.ok").param("id", characterId)
            );
        }
    }

    private static final class ListSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> assignmentArg =
            this.withOptionalArg("assignmentKind", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.assignmentKind.desc", ArgTypes.STRING);

        ListSubCommand() {
            super("list", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.list.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            String filter = assignmentArg.provided(ctx) ? assignmentArg.get(ctx) : null;
            TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
            if (filter != null && !filter.isBlank()) {
                List<String> available = TownsfolkSpawnService.availableCharacterIds(world, plugin, filter);
                ctx.sendMessage(
                    Message.translation("aetherhaven_commands_help.commands.aetherhaven.townsfolk.list.available")
                        .param("assignment", filter)
                        .param("ids", String.join(", ", available))
                );
            } else {
                List<String> available = TownsfolkSpawnService.availableCharacterIds(world, plugin, TownsfolkAssignmentKinds.IDLE);
                ctx.sendMessage(
                    Message.translation("aetherhaven_commands_help.commands.aetherhaven.townsfolk.list.availableIdle")
                        .param("ids", String.join(", ", available))
                );
            }
            StringBuilder checked = new StringBuilder();
            for (TownsfolkPoolCheckoutRecord rec : pool.getCheckouts().values()) {
                if (filter != null && !filter.isBlank() && !filter.equalsIgnoreCase(rec.getAssignmentKind())) {
                    continue;
                }
                if (!checked.isEmpty()) {
                    checked.append(", ");
                }
                checked.append(rec.getCharacterId()).append('@').append(rec.getAssignmentKind());
            }
            ctx.sendMessage(
                Message.translation("aetherhaven_commands_help.commands.aetherhaven.townsfolk.list.inUse")
                    .param("ids", checked.isEmpty() ? "(none)" : checked.toString())
            );
        }
    }

    private static final class ClearSubCommand extends AbstractPlayerCommand {
        ClearSubCommand() {
            super("clear", "aetherhaven_commands_help.commands.aetherhaven.townsfolk.clear.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                ctx.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
                return;
            }
            int despawned = TownsfolkSpawnService.clearPoolAndDespawnAll(world, plugin);
            ctx.sendMessage(
                Message.translation("aetherhaven_commands_help.commands.aetherhaven.townsfolk.clear.ok")
                    .param("count", despawned)
            );
        }
    }
}
