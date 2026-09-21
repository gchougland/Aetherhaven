package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenSupportCommand;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.town.PlotLinkReconcileService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.VillagerTownResetService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import org.joml.Vector3d;

/** Confirmed repairs always use this elder's town, never the player's nearest town. */
public final class ElderRepairActions {
    private static final Set<String> ACTIONS = Set.of("villagers", "plots", "inn", "support");
    private static final String MSG = "aetherhaven_repairs.aetherhaven.repairs.";
    private ElderRepairActions() {}

    public static TownRecord manageableTown(Ref<EntityStore> player, Store<EntityStore> store, Ref<EntityStore> elder) {
        var plugin = AetherhavenPlugin.get();
        if (plugin == null || elder == null || !elder.isValid()) return null;
        var npc = store.getComponent(elder, NPCEntity.getComponentType());
        var binding = store.getComponent(elder, TownVillagerBinding.getComponentType());
        var pr = store.getComponent(player, PlayerRef.getComponentType());
        if (npc == null || !"Aetherhaven_Elder_Lyren".equals(npc.getRoleName()) || binding == null || pr == null) return null;
        var world = store.getExternalData().getWorld();
        var town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(binding.getTownId());
        return town != null && town.playerCanManageConstructions(pr.getUuid()) ? town : null;
    }

    public static void schedule(String action, Ref<EntityStore> player, Store<EntityStore> store, Ref<EntityStore> elder) {
        TownRecord town = manageableTown(player, store, elder);
        if (town == null || (action == null || !ACTIONS.contains(action))) return;
        var world = store.getExternalData().getWorld();
        var townId = town.getTownId();
        // The reset replaces Lyren too. Let the current dialogue close before changing entities.
        world.execute(() -> {
            if (!player.isValid()) return;
            var plugin = AetherhavenPlugin.get();
            if (plugin == null) return;
            var pr = store.getComponent(player, PlayerRef.getComponentType());
            var tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            var current = tm.getTown(townId);
            if (pr == null || current == null || !current.playerCanManageConstructions(pr.getUuid())) return;
            switch (action) {
                case "villagers" -> {
                    var position = store.getComponent(player, TransformComponent.getComponentType());
                    if (position == null) return;
                    String error = VillagerTownResetService.resetAllTownVillagersNearPlayer(world, plugin, current, tm, store,
                        new Vector3d(position.getPosition()));
                    pr.sendMessage(error == null
                        ? Message.translation("aetherhaven_commands_help.aetherhaven.villager.resetDone")
                        : Message.translation("aetherhaven_commands_help.aetherhaven.villager.resetFailed").param("reason", error));
                }
                case "plots" -> {
                    var report = PlotLinkReconcileService.repairTown(world, plugin, current, true);
                    pr.sendMessage(Message.translation(MSG + "plots.result")
                        .param("repaired", report.getRelinked()).param("skipped", report.getSkippedChunkUnloaded())
                        .param("failed", report.getFailed()));
                }
                case "inn" -> {
                    var report = InnPoolService.repairInnPoolForTown(world, plugin, current, tm, store, true);
                    pr.sendMessage(Message.translation(MSG + "inn.result")
                        .param("promoted", report.getPromotedResidents()).param("fixed", report.getPoolEntriesFixed()));
                }
                case "support" -> AetherhavenSupportCommand.beginUpload(pr, world, "Town: " + townId);
                default -> { }
            }
        });
    }
}
