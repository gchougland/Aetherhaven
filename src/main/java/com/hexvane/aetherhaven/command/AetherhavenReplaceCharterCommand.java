package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.placement.CharterRelocationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownCommandResolution;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import javax.annotation.Nonnull;

/**
 * Re-links or re-places the charter at the {@link com.hexvane.aetherhaven.town.TownRecord}'s saved charter coordinates
 * (destroyed block, missing {@link com.hexvane.aetherhaven.plot.CharterBlock} component, etc.). Does not move the anchor.
 */
public final class AetherhavenReplaceCharterCommand extends AbstractPlayerCommand {
    @Nonnull
    private final OptionalArg<String> townArg =
        this.withOptionalArg(
            "townName",
            "aetherhaven_commands_help.commands.aetherhaven.replace_charter.townName.desc",
            ArgTypes.GREEDY_STRING
        );

    public AetherhavenReplaceCharterCommand() {
        super("replace-charter", "aetherhaven_commands_help.commands.aetherhaven.replace_charter.desc");
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
        Player player = store.getComponent(ref, Player.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (player == null || uc == null) {
            return;
        }
        boolean admin = TownPermissionUtil.canAdministerForeignTowns(player);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        String townOpt = context.provided(townArg) ? context.get(townArg) : null;
        TownCommandResolution res = TownCommandResolution.resolveForOwnerAction(tm, uc.getUuid(), townOpt, admin);
        if (!res.isOk()) {
            playerRef.sendMessage(res.error());
            return;
        }
        TownRecord town = res.townOrThrow();

        Rotation yaw = horizontalRotationFromPlayer(store, ref);
        CharterRelocationService.tryReplaceCharter(world, tm, town, yaw, uc.getUuid(), admin, playerRef);
    }

    @Nonnull
    private static Rotation horizontalRotationFromPlayer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return Rotation.None;
        }
        float yaw = tc.getRotation().getYaw();
        double twoPi = Math.PI * 2.0;
        double n = (yaw + Math.PI / 4.0 + twoPi) % twoPi;
        int step = (int) (n / (Math.PI / 2.0)) % 4;
        return switch (step) {
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> Rotation.None;
        };
    }
}
