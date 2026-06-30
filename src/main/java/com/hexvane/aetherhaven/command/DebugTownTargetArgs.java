package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared {@code --town} / {@code --player} optional args for debug commands. */
public final class DebugTownTargetArgs {
    @Nonnull
    private final OptionalArg<String> townArg;
    @Nonnull
    private final OptionalArg<String> playerArg;

    private DebugTownTargetArgs(
        @Nonnull OptionalArg<String> townArg, @Nonnull OptionalArg<String> playerArg
    ) {
        this.townArg = townArg;
        this.playerArg = playerArg;
    }

    /** Registers {@code --town} and {@code --player} on {@code command}. */
    @Nonnull
    public static DebugTownTargetArgs registerOn(@Nonnull AbstractPlayerCommand command) {
        OptionalArg<String> town =
            command.withOptionalArg(
                "town",
                "aetherhaven_commands_help.commands.aetherhaven.debug.townFlag.desc",
                ArgTypes.GREEDY_STRING
            );
        OptionalArg<String> player =
            command.withOptionalArg(
                "player",
                "aetherhaven_commands_help.commands.aetherhaven.debug.playerFlag.desc",
                ArgTypes.STRING
            );
        return new DebugTownTargetArgs(town, player);
    }

    /** Same as {@link #registerOn} but adds {@code townName} as an alias for {@code --town}. */
    @Nonnull
    public static DebugTownTargetArgs registerOnWithTownNameAlias(@Nonnull AbstractPlayerCommand command) {
        DebugTownTargetArgs args = registerOn(command);
        args.townArg.addAliases("townName");
        return args;
    }

    @Nonnull
    public TownCommandResolution resolve(
        @Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        boolean requireQuestPermissionWhenImplicit
    ) {
        return resolve(context, world, store, ref, playerRef, requireQuestPermissionWhenImplicit, null);
    }

    /**
     * When no {@code --town} / {@code --player} flags are set, {@code legacyTownDisplayName} is resolved with
     * {@link TownCommandResolution#resolveForOwnerAction} (owner or town admin by display name).
     */
    @Nonnull
    public TownCommandResolution resolve(
        @Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        boolean requireQuestPermissionWhenImplicit,
        @Nullable String legacyTownDisplayName
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return TownCommandResolution.error(
                com.hypixel.hytale.server.core.Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded")
            );
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (player == null || uc == null) {
            return TownCommandResolution.error(
                com.hypixel.hytale.server.core.Message.translation("aetherhaven_common.aetherhaven.common.noTownInWorld")
            );
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        boolean admin = TownPermissionUtil.canAdministerForeignTowns(player, playerRef);
        String townFlag = context.provided(townArg) ? context.get(townArg) : null;
        String playerFlag = context.provided(playerArg) ? context.get(playerArg) : null;
        boolean hasExplicitTarget =
            (townFlag != null && !townFlag.isBlank()) || (playerFlag != null && !playerFlag.isBlank());
        if (!hasExplicitTarget && legacyTownDisplayName != null && !legacyTownDisplayName.isBlank()) {
            return TownCommandResolution.resolveForOwnerAction(tm, uc.getUuid(), legacyTownDisplayName, admin);
        }
        return TownCommandResolution.resolveDebugTarget(
            tm,
            world,
            uc.getUuid(),
            admin,
            requireQuestPermissionWhenImplicit,
            townFlag,
            playerFlag
        );
    }
}
