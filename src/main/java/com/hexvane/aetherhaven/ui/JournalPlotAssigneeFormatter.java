package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Town journal building list: construction status plus house/workplace assignee labels. */
public final class JournalPlotAssigneeFormatter {
    private static final String TAIL = "aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.";
    private static final String TOWN = "aetherhaven_ui_town.aetherhaven.ui.plotconstruction.";

    private JournalPlotAssigneeFormatter() {}

    @Nonnull
    public static Message plotStatusLine(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull PlotInstance plot
    ) {
        PlotInstanceState state = plot.getState();
        if (state != PlotInstanceState.COMPLETE) {
            return Message.translation(plotStatusLangKey(state));
        }
        if (catalog.matchesGameplayConstruction(plot.getConstructionId(), AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE)) {
            return houseAssigneeLine(plugin, store, town, plot);
        }
        List<String> workplaceRoles =
            ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog, plot.getConstructionId());
        if (workplaceRoles.isEmpty()) {
            return Message.translation(TAIL + "plotStatusComplete");
        }
        if (workplaceRoles.size() > 1
            || ProductionWorkplaceKinds.isMultiRoleWorkplacePlot(catalog, plot.getConstructionId())) {
            return multiRoleWorkplaceAssigneeLine(plugin, store, town, catalog, plot, workplaceRoles);
        }
        String residentKind = workplaceRoles.get(0);
        String gameplayId =
            ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                catalog,
                plot.getConstructionId(),
                residentKind
            );
        if (gameplayId == null || gameplayId.isBlank()) {
            return Message.translation(TAIL + "plotStatusComplete");
        }
        return singleRoleWorkplaceAssigneeLine(plugin, store, town, plot, gameplayId, residentKind);
    }

    @Nonnull
    private static Message houseAssigneeLine(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot
    ) {
        List<UUID> residents = plot.getHomeResidentEntityUuids();
        if (residents.isEmpty()) {
            return Message.translation(TAIL + "plotHouseUnassigned");
        }
        StringBuilder names = new StringBuilder();
        for (UUID resident : residents) {
            String name = resolveHouseResidentName(plugin, store, town, resident);
            if (name == null || name.isBlank()) {
                continue;
            }
            if (names.length() > 0) {
                names.append(" · ");
            }
            names.append(name.trim());
        }
        if (names.length() == 0) {
            return Message.translation(TAIL + "plotHouseUnassigned");
        }
        return Message.translation(TAIL + "plotHouseResident").param("name", Message.raw(names.toString()));
    }

    @Nonnull
    private static Message multiRoleWorkplaceAssigneeLine(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull PlotInstance plot,
        @Nonnull List<String> workplaceRoles
    ) {
        UUID plotId = plot.getPlotId();
        Message combined = null;
        for (String residentKind : workplaceRoles) {
            String gameplayId =
                ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                    catalog,
                    plot.getConstructionId(),
                    residentKind
                );
            if (gameplayId == null || gameplayId.isBlank()) {
                continue;
            }
            Message line =
                roleAssigneeLine(
                    plugin,
                    store,
                    town,
                    plotId,
                    gameplayId,
                    residentKind,
                    npcRoleFilterForResidentKind(residentKind),
                    workplaceRoleLabelKey(residentKind)
                );
            combined = combined == null ? line : Message.join(combined, Message.raw(" · "), line);
        }
        return combined != null ? combined : Message.translation(TAIL + "plotStatusComplete");
    }

    @Nonnull
    private static Message singleRoleWorkplaceAssigneeLine(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull String gameplayWorkplaceId,
        @Nonnull String residentKind
    ) {
        UUID plotId = plot.getPlotId();
        UUID worker = findEntityWithJobPlotAndKind(store, town.getTownId(), plotId, residentKind);
        if (worker == null) {
            return Message.translation(TAIL + "plotWorkplaceUnassigned");
        }
        String name =
            resolveWorkerName(
                plugin,
                store,
                town,
                gameplayWorkplaceId,
                npcRoleFilterForResidentKind(residentKind),
                worker
            );
        if (name == null || name.isBlank()) {
            return Message.translation(TAIL + "plotWorkplaceUnassigned");
        }
        return Message.translation(TAIL + "plotWorkplaceWorker").param("name", Message.raw(name.trim()));
    }

    @Nullable
    private static String npcRoleFilterForResidentKind(@Nonnull String residentKind) {
        if (TownVillagerBinding.KIND_GUILD_MASTER.equals(residentKind)) {
            return AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID;
        }
        if (TownVillagerBinding.KIND_BARD.equals(residentKind)) {
            return AetherhavenConstants.BARD_NPC_ROLE_ID;
        }
        return null;
    }

    @Nonnull
    private static String workplaceRoleLabelKey(@Nonnull String residentKind) {
        return switch (residentKind.trim()) {
            case TownVillagerBinding.KIND_GUILD_MASTER -> TOWN + "workplaceGuildMaster";
            case TownVillagerBinding.KIND_BARD -> TOWN + "workplaceBard";
            case TownVillagerBinding.KIND_INNKEEPER -> TOWN + "workplaceInnkeeper";
            case TownVillagerBinding.KIND_ELDER -> TOWN + "workplaceElder";
            case TownVillagerBinding.KIND_CHEF -> TOWN + "workplaceChef";
            default -> TOWN + "workplaceRole." + residentKind.trim();
        };
    }

    @Nonnull
    private static Message roleAssigneeLine(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull String gameplayWorkplaceId,
        @Nonnull String residentKind,
        @Nullable String filterNpcRoleId,
        @Nonnull String roleLabelLangKey
    ) {
        Message roleLabel = Message.translation(roleLabelLangKey);
        UUID worker = findEntityWithJobPlotAndKind(store, town.getTownId(), plotId, residentKind);
        if (worker == null) {
            return Message.translation(TAIL + "plotRoleUnassigned").param("role", roleLabel);
        }
        String name = resolveWorkerName(plugin, store, town, gameplayWorkplaceId, filterNpcRoleId, worker);
        if (name == null || name.isBlank()) {
            return Message.translation(TAIL + "plotRoleUnassigned").param("role", roleLabel);
        }
        return Message
            .translation(TAIL + "plotRoleAssignee")
            .param("role", roleLabel)
            .param("name", Message.raw(name.trim()));
    }

    @Nullable
    private static String resolveHouseResidentName(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid
    ) {
        HouseResidentDirectory.HouseResidentRow row = HouseResidentDirectory.resolvePreviewRow(store, town, plugin, entityUuid);
        return row != null ? row.displayName() : null;
    }

    @Nullable
    private static String resolveWorkerName(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String gameplayWorkplaceId,
        @Nullable String filterNpcRoleId,
        @Nonnull UUID entityUuid
    ) {
        WorkplaceWorkerDirectory.WorkplaceWorkerRow row =
            WorkplaceWorkerDirectory.resolvePreviewRow(store, town, plugin, gameplayWorkplaceId, filterNpcRoleId, entityUuid);
        return row != null ? row.displayName() : null;
    }

    @Nullable
    private static UUID findEntityWithJobPlotAndKind(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull UUID jobPlotId,
        @Nonnull String residentKind
    ) {
        final UUID[] holder = new UUID[1];
        Query<EntityStore> q = Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> commandBuffer) -> {
                if (holder[0] != null) {
                    return;
                }
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !townId.equals(b.getTownId()) || !residentKind.equals(b.getKind())) {
                        continue;
                    }
                    UUID jp = b.getJobPlotId();
                    if (jp == null || !jp.equals(jobPlotId)) {
                        continue;
                    }
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uc != null) {
                        holder[0] = uc.getUuid();
                        return;
                    }
                }
            }
        );
        return holder[0];
    }

    @Nonnull
    private static String plotStatusLangKey(@Nullable PlotInstanceState state) {
        if (state == null) {
            return TAIL + "plotStatusUnknown";
        }
        return switch (state) {
            case BLUEPRINTING -> TAIL + "plotStatusNotStarted";
            case ASSEMBLING -> TAIL + "plotStatusInProgress";
            case COMPLETE -> TAIL + "plotStatusComplete";
        };
    }
}
