package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyJob;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintChunkUtil;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.VillagerTownResetService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Server actions for the Town Journal Settings repair tools. */
public final class TownJournalAdminService {
    private TownJournalAdminService() {}

    @Nullable
    public static String resetTownVillagersNearPlayer(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d playerPosition
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        return VillagerTownResetService.resetAllTownVillagersNearPlayer(world, plugin, town, tm, store, playerPosition);
    }

    @Nonnull
    public static InnPoolService.RepairReport repairInn(
        @Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town, @Nonnull Store<EntityStore> store
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        return InnPoolService.repairInnPoolForTown(world, plugin, town, tm, store);
    }

    public enum FinishPlotResult {
        OK,
        NOT_ASSEMBLING,
        NOT_LOADED,
        NO_JOB,
        FAILED
    }

    @Nonnull
    public static FinishPlotResult tryFinishAssemblingPlot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID plotId
    ) {
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
            return FinishPlotResult.NOT_ASSEMBLING;
        }
        if (!PlotFootprintChunkUtil.isPlotFullyLoaded(world, plot)) {
            return FinishPlotResult.NOT_LOADED;
        }
        PlotAssemblyJob job = AssemblyWorldRegistry.get(world, plotId);
        if (job == null) {
            return FinishPlotResult.NO_JOB;
        }
        boolean ok = PlotAssemblyService.instantCompleteJob(world, plugin, store, town, plot, job);
        return ok ? FinishPlotResult.OK : FinishPlotResult.FAILED;
    }

    @Nonnull
    public static String buildVillagerSupportReport(
        @Nonnull Store<EntityStore> store, @Nonnull TownRecord town, @Nonnull UUID villagerEntityUuid, @Nonnull AetherhavenPlugin plugin
    ) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("Aetherhaven villager report\n");
        sb.append("Town id: ").append(town.getTownId()).append('\n');
        sb.append("Villager id: ").append(villagerEntityUuid).append('\n');
        String roleId = "";
        String display = "";
        for (TownVillagerRow row : TownVillagerDirectory.listResidents(store, town)) {
            if (villagerEntityUuid.equals(row.entityUuid())) {
                roleId = row.roleId();
                display = row.label();
                break;
            }
        }
        if (display.isEmpty()) {
            display = "(not in live town list)";
        }
        sb.append("Display name: ").append(display).append('\n');
        sb.append("Role id: ").append(roleId.isEmpty() ? "?" : roleId).append('\n');
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(villagerEntityUuid);
        boolean loaded = npcRef != null && npcRef.isValid();
        sb.append("Loaded: ").append(loaded ? "yes" : "no").append('\n');
        if (loaded) {
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc != null && npc.getRoleName() != null) {
                sb.append("Live role: ").append(npc.getRoleName()).append('\n');
            }
            TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
            if (tc != null) {
                Vector3d p = tc.getPosition();
                sb.append("Position: ")
                    .append(String.format(java.util.Locale.US, "%.2f %.2f %.2f", p.x, p.y, p.z))
                    .append('\n');
            }
            TownVillagerBinding bind = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            if (bind != null) {
                sb.append("Binding kind: ").append(bind.getKind()).append('\n');
                if (bind.getJobPlotId() != null) {
                    sb.append("Binding job plot: ").append(bind.getJobPlotId()).append('\n');
                }
            }
            VillagerNeeds needs = store.getComponent(npcRef, VillagerNeeds.getComponentType());
            if (needs != null) {
                sb.append("Needs hunger: ")
                    .append(String.format(java.util.Locale.US, "%.1f", needs.getHunger()))
                    .append(" energy: ")
                    .append(String.format(java.util.Locale.US, "%.1f", needs.getEnergy()))
                    .append(" fun: ")
                    .append(String.format(java.util.Locale.US, "%.1f", needs.getFun()))
                    .append('\n');
            }
        }
        town.getResidentNpcRecords()
            .stream()
            .filter(r -> villagerEntityUuid.equals(r.getLastEntityUuid()))
            .findFirst()
            .ifPresent(
                r -> {
                    sb.append("Town roster kind: ").append(r.getKind()).append('\n');
                    if (r.getJobPlotId() != null) {
                        sb.append("Town roster job plot: ").append(r.getJobPlotId()).append('\n');
                    }
                    if (r.hasLastKnownNeeds()) {
                        sb.append("Last saved needs hunger: ")
                            .append(String.format(java.util.Locale.US, "%.1f", r.getLastKnownHunger()))
                            .append(" energy: ")
                            .append(String.format(java.util.Locale.US, "%.1f", r.getLastKnownEnergy()))
                            .append(" fun: ")
                            .append(String.format(java.util.Locale.US, "%.1f", r.getLastKnownFun()))
                            .append('\n');
                    }
                }
            );
        if (!roleId.isEmpty()) {
            boolean hasSched =
                plugin.getVillagerDefinitionCatalog().effectiveSchedule(roleId, plugin.getVillagerScheduleRegistry()) != null;
            sb.append("Weekly route file: ").append(hasSched ? "yes" : "no").append('\n');
        }
        return sb.toString();
    }
}
