package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyDebug;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyDebugTag;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.commands.NPCMultiSelectCommandBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.RoleDebugFlags;
import java.util.EnumSet;
import javax.annotation.Nonnull;

public final class AetherhavenAutonomyDebugCommand extends AbstractCommandCollection {
    public AetherhavenAutonomyDebugCommand() {
        super("debug-autonomy", "server.commands.aetherhaven.debug_autonomy.desc");
        this.addSubCommand(new ToggleCommand());
        this.addSubCommand(new ShowCommand());
        this.addSubCommand(new ClearCommand());
    }

    private static boolean requirePlayerDebug(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("Players only."));
            return false;
        }
        Ref<EntityStore> pref = context.senderAsPlayerRef();
        if (pref == null || !pref.isValid()) {
            return false;
        }
        PlayerRef pr = store.getComponent(pref, PlayerRef.getComponentType());
        if (pr == null) {
            return false;
        }
        return AetherhavenDebugUtil.requireDebug(plugin, pr);
    }

    private static boolean requireTownVillager(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        if (store.getComponent(ref, TownVillagerBinding.getComponentType()) == null) {
            context.sendMessage(Message.raw("Target is not an Aetherhaven town villager."));
            return false;
        }
        return true;
    }

    private static final class ToggleCommand extends NPCMultiSelectCommandBase {
        ToggleCommand() {
            super("toggle", "server.commands.aetherhaven.debug_autonomy.toggle.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull NPCEntity npc,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
        ) {
            if (!requirePlayerDebug(context, store)) {
                return;
            }
            if (!requireTownVillager(context, store, ref)) {
                return;
            }
            boolean next = !VillagerAutonomyDebug.isEnabled(store, ref);
            if (next) {
                store.putComponent(ref, VillagerAutonomyDebugTag.getComponentType(), new VillagerAutonomyDebugTag());
                EnumSet<RoleDebugFlags> flags = npc.getRoleDebugFlags().clone();
                flags.add(RoleDebugFlags.DisplayCustom);
                flags.add(RoleDebugFlags.VisPath);
                store.tryRemoveComponent(ref, Nameplate.getComponentType());
                npc.setRoleDebugFlags(flags);
                context.sendMessage(Message.raw("Autonomy debug ON (DisplayCustom + VisPath). AH overlay on nameplate."));
            } else {
                store.tryRemoveComponent(ref, VillagerAutonomyDebugTag.getComponentType());
                VillagerAutonomyDebug.clearAutonomyDebugForNpc(ref, store, npc);
                context.sendMessage(Message.raw("Autonomy debug OFF for this villager."));
            }
            store.putComponent(ref, NPCEntity.getComponentType(), npc);
        }
    }

    private static final class ShowCommand extends NPCMultiSelectCommandBase {
        ShowCommand() {
            super("show", "server.commands.aetherhaven.debug_autonomy.show.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull NPCEntity npc,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
        ) {
            if (!requirePlayerDebug(context, store)) {
                return;
            }
            if (!requireTownVillager(context, store, ref)) {
                return;
            }
            boolean on = VillagerAutonomyDebug.isEnabled(store, ref);
            context.sendMessage(Message.raw("Autonomy debug for this villager: " + (on ? "ON" : "OFF")));
        }
    }

    private static final class ClearCommand extends NPCMultiSelectCommandBase {
        ClearCommand() {
            super("clear", "server.commands.aetherhaven.debug_autonomy.clear.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull NPCEntity npc,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
        ) {
            if (!requirePlayerDebug(context, store)) {
                return;
            }
            if (!requireTownVillager(context, store, ref)) {
                return;
            }
            store.tryRemoveComponent(ref, VillagerAutonomyDebugTag.getComponentType());
            VillagerAutonomyDebug.clearAutonomyDebugForNpc(ref, store, npc);
            context.sendMessage(Message.raw("Cleared Aetherhaven autonomy debug for this villager."));
            store.putComponent(ref, NPCEntity.getComponentType(), npc);
        }
    }
}
