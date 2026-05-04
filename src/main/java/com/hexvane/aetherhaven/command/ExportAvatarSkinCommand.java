package com.hexvane.aetherhaven.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.cosmetics.PlayerSkinModelExporter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ExportAvatarSkinCommand extends AbstractPlayerCommand {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Nonnull
    private final OptionalArg<String> pathArg =
        this.withOptionalArg("path", "aetherhaven_commands_root.commands.aetherhaven.exportskin.arg.path", ArgTypes.GREEDY_STRING);

    public ExportAvatarSkinCommand() {
        super("exportskin", "aetherhaven_commands_root.commands.aetherhaven.exportskin.desc");
        this.setPermissionGroup(GameMode.Creative);
        this.addUsageVariant(new ExportAvatarSkinOtherCommand());
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        exportForRef(context, store, ref, playerRef, this.pathArg.provided(context) ? this.pathArg.get(context) : null);
    }

    private static void exportForRef(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nullable String pathString
    ) {
        PlayerSkinComponent skinComponent = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (skinComponent == null) {
            context.sendMessage(Message.translation("aetherhaven_commands_root.commands.aetherhaven.exportskin.noSkin"));
            return;
        }
        PlayerSkin skin = skinComponent.getPlayerSkin();
        CosmeticsModule cosmetics = CosmeticsModule.get();
        try {
            cosmetics.validateSkin(skin);
        } catch (CosmeticsModule.InvalidSkinException e) {
            context.sendMessage(
                Message.translation("aetherhaven_commands_root.commands.aetherhaven.exportskin.invalidSkin").param("detail", e.getMessage())
            );
            return;
        }
        JsonObject json;
        try {
            json = PlayerSkinModelExporter.toModelJson(skin, cosmetics.getRegistry());
        } catch (IllegalArgumentException ex) {
            context.sendMessage(
                Message.translation("aetherhaven_commands_root.commands.aetherhaven.exportskin.resolveError").param("detail", ex.getMessage())
            );
            return;
        }
        Path out = resolveOutputPath(playerRef.getUsername(), pathString);
        try {
            Files.createDirectories(out.getParent());
            String text = new GsonBuilder().setPrettyPrinting().create().toJson(json);
            Files.writeString(out, text, StandardCharsets.UTF_8);
        } catch (Exception io) {
            context.sendMessage(
                Message.translation("aetherhaven_commands_root.commands.aetherhaven.exportskin.ioError")
                    .param("path", out.toString())
                    .param("detail", io.getMessage())
            );
            return;
        }
        context.sendMessage(Message.translation("aetherhaven_commands_root.commands.aetherhaven.exportskin.success").param("path", out.toString()));
    }

    @Nonnull
    private static Path resolveOutputPath(@Nonnull String username, @Nullable String pathString) {
        if (pathString != null && !pathString.isBlank()) {
            Path p = Path.of(pathString.trim());
            if (p.isAbsolute()) {
                return p.normalize();
            }
            return Path.of(System.getProperty("user.dir", ".")).resolve(p).normalize();
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Path base =
            plugin != null
                ? plugin.getDataDirectory().resolve("avatar_exports")
                : Path.of(System.getProperty("user.dir", ".")).resolve("avatar_exports");
        String safeUser = username.replaceAll("[^a-zA-Z0-9._-]", "_");
        return base.resolve(safeUser + "_" + LocalDateTime.now().format(STAMP) + ".json").normalize();
    }

    private static final class ExportAvatarSkinOtherCommand extends CommandBase {
        @Nonnull
        private final RequiredArg<PlayerRef> playerArg =
            this.withRequiredArg("player", "server.commands.argtype.player.desc", ArgTypes.PLAYER_REF);

        @Nonnull
        private final OptionalArg<String> pathArg =
            this.withOptionalArg("path", "aetherhaven_commands_root.commands.aetherhaven.exportskin.arg.path", ArgTypes.GREEDY_STRING);

        ExportAvatarSkinOtherCommand() {
            super("aetherhaven_commands_root.commands.aetherhaven.exportskin.other.desc");
            this.setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            CommandUtil.requirePermission(
                context.sender(),
                Objects.requireNonNull(this.getPermission(), "exportskin permission not set") + ".other"
            );
            PlayerRef target = this.playerArg.get(context);
            Ref<EntityStore> targetRef = target.getReference();
            if (targetRef == null || !targetRef.isValid()) {
                context.sendMessage(Message.translation("server.commands.errors.playerNotInWorld"));
                return;
            }
            Store<EntityStore> store = targetRef.getStore();
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                PlayerRef pr = store.getComponent(targetRef, PlayerRef.getComponentType());
                if (pr == null) {
                    context.sendMessage(Message.translation("server.commands.errors.playerNotInWorld"));
                } else {
                    exportForRef(
                        context,
                        store,
                        targetRef,
                        pr,
                        this.pathArg.provided(context) ? this.pathArg.get(context) : null
                    );
                }
            });
        }
    }
}
