package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.charter.TownFoundingService;
import com.hexvane.aetherhaven.startertown.StarterTownBuildService;
import com.hexvane.aetherhaven.startertown.StarterTownLayoutPlan;
import com.hexvane.aetherhaven.startertown.StarterTownLayoutPlanner;
import com.hexvane.aetherhaven.startertown.StarterTownPreset;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Builds a complete test town without advancing story quests. */
public final class AetherhavenStarterTownCommand extends AbstractPlayerCommand {
    public AetherhavenStarterTownCommand() {
        super("startertown", "aetherhaven_commands_help.commands.aetherhaven.startertown.desc");
        this.setPermissionGroups("hytale:WorldEditor");
        this.addUsageVariant(new WithPreset());
        this.addUsageVariant(new WithPresetAndLayout());
        this.addUsageVariant(new WithPresetLayoutAndSeed());
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        executeWithOptions(context, store, ref, playerRef, world, "minimal", "generated", null);
    }

    private static void executeWithOptions(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world,
        @Nonnull String presetText,
        @Nonnull String layoutText,
        String seedText
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            context.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        if (PersistentWorldSupport.isTemporaryInstance(world)) {
            context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.temporary"));
            return;
        }
        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (uuidComponent == null || transform == null) {
            return;
        }
        presetText = presetText.trim();
        layoutText = layoutText.trim();
        if (!presetText.equalsIgnoreCase("minimal") && !presetText.equalsIgnoreCase("full")) {
            context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.badPreset"));
            return;
        }
        if (!layoutText.equalsIgnoreCase("line") && !layoutText.equalsIgnoreCase("generated")) {
            context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.badLayout"));
            return;
        }
        long seed = ThreadLocalRandom.current().nextLong();
        if (seedText != null) {
            try {
                seed = Long.parseLong(seedText.trim());
            } catch (NumberFormatException e) {
                context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.badSeed"));
                return;
            }
        }

        Vector3d playerPosition = transform.getPosition();
        int x = (int) Math.floor(playerPosition.x);
        int z = (int) Math.floor(playerPosition.z);
        WorldChunk surfaceChunk = world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (surfaceChunk == null) {
            context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.chunkMissing"));
            return;
        }
        int y = surfaceChunk.getHeight(x, z) + 1;
        Vector3i origin = new Vector3i(x, y, z);
        Rotation facing = cardinalRotation(transform.getRotation().yaw());
        UUID actorUuid = uuidComponent.getUuid();
        TownManager townManager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = townManager.findTownForPlayerInWorld(actorUuid);
        boolean createTown = town == null;
        TownRecord planningTown = town;
        if (planningTown == null) {
            planningTown = new TownRecord(
                UUID.randomUUID(),
                actorUuid,
                world.getName(),
                x,
                y,
                z,
                0,
                TownManager.defaultTerritoryRadiusChunks(plugin.getConfig().get()),
                System.currentTimeMillis()
            );
        }
        StarterTownPreset preset = StarterTownPreset.parse(presetText);
        List<String> ids = preset.resolve(plugin.getConstructionCatalog());
        StarterTownLayoutPlan plan;
        try {
            plan = StarterTownLayoutPlanner.plan(
                world,
                townManager,
                planningTown,
                plugin.getConstructionCatalog(),
                ids,
                origin,
                facing,
                layoutText,
                seed
            );
        } catch (StarterTownLayoutPlanner.PlanException e) {
            context.sendMessage(
                Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.preflightFailed")
                    .param("reason", e.getMessage())
            );
            return;
        }
        if (createTown) {
            town = TownFoundingService.foundWithNewCharter(
                world,
                plugin,
                actorUuid,
                context.sender() != null ? context.sender().getUsername() : null,
                origin,
                facing,
                new Random(seed)
            );
            if (town == null) {
                context.sendMessage(Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.foundFailed"));
                return;
            }
        }
        UUID townId = town.getTownId();
        long resolvedSeed = seed;
        context.sendMessage(
            Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.started")
                .param("layout", plan.layout())
                .param("seed", String.valueOf(resolvedSeed))
                .param("count", String.valueOf(plan.buildings().size()))
        );
        StarterTownBuildService.build(
            world,
            plugin,
            actorUuid,
            townId,
            plan,
            result ->
                playerRef.sendMessage(
                    Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.done")
                        .param("buildings", String.valueOf(result.buildings()))
                        .param("paths", String.valueOf(result.paths()))
                        .param("quests", String.valueOf(result.completedQuests()))
                        .param("villagers", String.valueOf(result.villagers()))
                        .param("skipped", String.valueOf(result.skippedVillagers()))
                        .param("seed", String.valueOf(resolvedSeed))
                ),
            reason ->
                playerRef.sendMessage(
                    Message.translation("aetherhaven_commands_help.commands.aetherhaven.startertown.failed")
                        .param("reason", reason)
                )
        );
    }

    private static final class WithPreset extends AbstractPlayerCommand {
        private final RequiredArg<String> preset =
            this.withRequiredArg("preset", "aetherhaven_commands_help.commands.aetherhaven.startertown.preset", ArgTypes.STRING);

        WithPreset() {
            super("aetherhaven_commands_help.commands.aetherhaven.startertown.desc");
            this.setPermissionGroups("hytale:WorldEditor");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            executeWithOptions(context, store, ref, playerRef, world, preset.get(context), "generated", null);
        }
    }

    private static final class WithPresetAndLayout extends AbstractPlayerCommand {
        private final RequiredArg<String> preset =
            this.withRequiredArg("preset", "aetherhaven_commands_help.commands.aetherhaven.startertown.preset", ArgTypes.STRING);
        private final RequiredArg<String> layout =
            this.withRequiredArg("layout", "aetherhaven_commands_help.commands.aetherhaven.startertown.layout", ArgTypes.STRING);

        WithPresetAndLayout() {
            super("aetherhaven_commands_help.commands.aetherhaven.startertown.desc");
            this.setPermissionGroups("hytale:WorldEditor");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            executeWithOptions(context, store, ref, playerRef, world, preset.get(context), layout.get(context), null);
        }
    }

    private static final class WithPresetLayoutAndSeed extends AbstractPlayerCommand {
        private final RequiredArg<String> preset =
            this.withRequiredArg("preset", "aetherhaven_commands_help.commands.aetherhaven.startertown.preset", ArgTypes.STRING);
        private final RequiredArg<String> layout =
            this.withRequiredArg("layout", "aetherhaven_commands_help.commands.aetherhaven.startertown.layout", ArgTypes.STRING);
        private final RequiredArg<String> seed =
            this.withRequiredArg("seed", "aetherhaven_commands_help.commands.aetherhaven.startertown.seed", ArgTypes.STRING);

        WithPresetLayoutAndSeed() {
            super("aetherhaven_commands_help.commands.aetherhaven.startertown.desc");
            this.setPermissionGroups("hytale:WorldEditor");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            executeWithOptions(
                context,
                store,
                ref,
                playerRef,
                world,
                preset.get(context),
                layout.get(context),
                seed.get(context)
            );
        }
    }

    @Nonnull
    private static Rotation cardinalRotation(float yawRadians) {
        int quarter = Math.floorMod((int) Math.round(yawRadians / (Math.PI * 0.5)), 4);
        return switch (quarter) {
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> Rotation.None;
        };
    }
}
