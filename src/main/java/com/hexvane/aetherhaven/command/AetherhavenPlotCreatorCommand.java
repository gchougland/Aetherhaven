package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorInteractions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorService;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSessions;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorSession;
import com.hexvane.aetherhaven.plotcreator.icon.PlotCreatorIconExporter;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.ui.LocalBuildingsPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class AetherhavenPlotCreatorCommand extends AbstractCommandCollection {
    public AetherhavenPlotCreatorCommand() {
        super("plotcreator", "aetherhaven_commands_help.commands.aetherhaven.plotcreator.desc");
        this.addSubCommand(new StartCommand());
        this.addSubCommand(new CancelCommand());
        this.addSubCommand(new EditCommand());
        this.addSubCommand(new GenerateIconCommand());
        this.addSubCommand(new BuildingsCommand());
        this.addSubCommand(new SetBoundsCommand());
    }

    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";

    private static boolean requirePlotCreatorPermission(@Nonnull PlayerRef playerRef) {
        if (playerRef.hasPermission(AetherhavenConstants.PERMISSION_PLOT_CREATOR)) {
            return true;
        }
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.noPermission"));
        return false;
    }

    private static final class StartCommand extends AbstractPlayerCommand {
        StartCommand() {
            super("start", "aetherhaven_commands_help.commands.aetherhaven.plotcreator.start.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            PlotCreatorService.startSession(playerRef, ref, store);
        }
    }

    private static final class EditCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> constructionIdArg =
            this.withRequiredArg(
                "constructionId",
                "aetherhaven_commands_help.commands.aetherhaven.plotcreator.edit.constructionId",
                AetherhavenArgTypes.CUSTOM_BUILDING_ID
            );

        EditCommand() {
            super("edit", "aetherhaven_commands_help.commands.aetherhaven.plotcreator.edit.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            PlotCreatorService.startEditSession(playerRef, ref, store, constructionIdArg.get(context));
        }
    }

    private static final class CancelCommand extends AbstractPlayerCommand {
        CancelCommand() {
            super("cancel", "aetherhaven_commands_help.commands.aetherhaven.plotcreator.cancel.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            PlotCreatorService.cancelSession(playerRef, ref, store);
        }
    }

    /**
     * Regenerates a plot token thumbnail PNG from an on-disk prefab (building id or prefab path key).
     */
    private static final class GenerateIconCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> idOrPrefabArg =
            this.withRequiredArg(
                "idOrPrefab",
                "aetherhaven_commands_help.commands.aetherhaven.plotcreator.generateicon.idOrPrefab",
                AetherhavenArgTypes.CUSTOM_BUILDING_ID
            );
        private final OptionalArg<String> outputIdArg =
            this.withOptionalArg(
                "outputId",
                "aetherhaven_commands_help.commands.aetherhaven.plotcreator.generateicon.outputId",
                AetherhavenArgTypes.CUSTOM_BUILDING_ID
            );

        GenerateIconCommand() {
            super("generateicon", "aetherhaven_commands_help.commands.aetherhaven.plotcreator.generateicon.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!requirePlotCreatorPermission(playerRef)) {
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            String input = idOrPrefabArg.get(context).trim();
            if (input.isEmpty()) {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.generateicon.error.empty"));
                return;
            }

            String prefabKey = input;
            String outputId = outputIdArg.provided(context) ? outputIdArg.get(context).trim() : null;
            String frontFacing = null;

            ConstructionDefinition def = plugin.getConstructionCatalog().get(input);
            if (def != null) {
                prefabKey = def.getPrefabPath();
                frontFacing = def.getFrontFacing();
                if (outputId == null || outputId.isEmpty()) {
                    outputId = def.getId();
                }
            } else if (outputId == null || outputId.isEmpty()) {
                outputId = findConstructionIdForPrefab(plugin, input);
                if (outputId == null) {
                    outputId = outputIdFromPrefabKey(input);
                }
            }
            if (frontFacing == null && outputId != null && !outputId.isBlank()) {
                ConstructionDefinition byOutput = plugin.getConstructionCatalog().get(outputId);
                if (byOutput != null) {
                    frontFacing = byOutput.getFrontFacing();
                }
            }

            if (prefabKey == null || prefabKey.isBlank()) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.generateicon.error.noPrefab").param("id", input)
                );
                return;
            }
            if (outputId == null || outputId.isBlank()) {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.generateicon.error.noOutputId"));
                return;
            }

            Path prefabPath = PrefabResolveUtil.resolvePrefabPath(prefabKey);
            if (prefabPath == null) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.generateicon.error.notFound")
                        .param("prefab", prefabKey)
                );
                return;
            }

            BlockSelection prefab;
            try {
                prefab = PrefabStore.get().getPrefab(prefabPath);
            } catch (Exception e) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.generateicon.error.loadFailed")
                        .param("prefab", prefabKey)
                );
                return;
            }

            boolean ok =
                PlotCreatorIconExporter.tryExportIcon(prefab, outputId, plugin.getDataDirectory(), frontFacing);
            if (!ok) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.generateicon.error.renderFailed")
                        .param("id", outputId)
                );
                return;
            }

            Path iconFile = CustomBuildingsPaths.iconFile(plugin.getDataDirectory(), outputId);
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.generateicon.ok")
                    .param("id", outputId)
                    .param("path", iconFile.toString())
                    .param("asset", CustomBuildingsPaths.iconAssetPath(outputId))
            );
        }
    }

    private static final class SetBoundsCommand extends AbstractPlayerCommand {
        SetBoundsCommand() {
            super("setbounds", "aetherhaven_commands_help.commands.aetherhaven.plotcreator.setbounds.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!requirePlotCreatorPermission(playerRef)) {
                return;
            }
            PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
            if (session == null) {
                playerRef.sendMessage(Message.translation(MSG + ".error.noSession"));
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            BlockSelection selection = BuilderToolsPlugin.getState(player, playerRef).getSelection();
            if (selection == null || !selection.hasSelectionBounds()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.noSelection"));
                return;
            }
            Vector3i min = selection.getSelectionMin();
            Vector3i max = selection.getSelectionMax();
            String err = PlotCreatorService.applyBoundsFromBuilderSelection(session, min, max);
            if (err != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + err));
                return;
            }
            int width = max.x - min.x + 1;
            int depth = max.z - min.z + 1;
            int height = max.y - min.y + 1;
            playerRef.sendMessage(
                Message.translation(MSG + ".hint.boundsSet")
                    .param("width", width)
                    .param("depth", depth)
                    .param("height", height)
            );
            PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        }
    }

    private static final class BuildingsCommand extends AbstractPlayerCommand {
        BuildingsCommand() {
            super("buildings", "aetherhaven_commands_help.commands.aetherhaven.plotcreator.buildings.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!requirePlotCreatorPermission(playerRef)) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            if (player.getPageManager().getCustomPage() != null) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store, new LocalBuildingsPage(playerRef));
        }
    }

    @Nullable
    private static String findConstructionIdForPrefab(@Nonnull AetherhavenPlugin plugin, @Nonnull String prefabPath) {
        String key = prefabPath.trim();
        for (ConstructionDefinition d : plugin.getConstructionCatalog().list()) {
            String onDef = d.getPrefabPath();
            if (onDef != null && onDef.trim().equalsIgnoreCase(key)) {
                return d.getId();
            }
        }
        return null;
    }

    @Nonnull
    private static String outputIdFromPrefabKey(@Nonnull String prefabKey) {
        String name = prefabKey.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".prefab.json")) {
            name = name.substring(0, name.length() - ".prefab.json".length());
        }
        return name;
    }
}
