package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunitySubmissionService;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/** Community marketplace commands (submit locally saved custom buildings). */
public final class AetherhavenCommunityCommand extends AbstractCommandCollection {
    public AetherhavenCommunityCommand() {
        super("community", "aetherhaven_commands_help.commands.aetherhaven.community.desc");
        this.addSubCommand(new SubmitCommand());
    }

    private static final class SubmitCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> constructionIdArg =
            this.withRequiredArg(
                "constructionId",
                "aetherhaven_commands_help.commands.aetherhaven.community.submit.constructionId",
                ArgTypes.STRING
            );

        SubmitCommand() {
            super("submit", "aetherhaven_commands_help.commands.aetherhaven.community.submit.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            if (!playerRef.hasPermission(AetherhavenConstants.PERMISSION_PLOT_CREATOR)) {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.noPermission"));
                return;
            }
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (plugin == null) {
                return;
            }
            String constructionId = constructionIdArg.get(context).trim();
            if (constructionId.isEmpty()) {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.needConstructionId"));
                return;
            }
            Path buildingFile = CustomBuildingsPaths.buildingFile(plugin.getDataDirectory(), constructionId);
            if (!Files.isRegularFile(buildingFile)) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.communitySubmitNotFound")
                        .param("id", constructionId)
                );
                return;
            }
            String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : "Unknown";
            String submitErr = CommunitySubmissionService.submitSavedBuilding(
                plugin,
                playerRef.getUuid(),
                playerName,
                constructionId
            );
            CommunitySubmissionService.notifyPlayer(playerRef, submitErr);
        }
    }
}
