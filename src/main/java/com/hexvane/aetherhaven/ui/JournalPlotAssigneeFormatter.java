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
        String gameplayId = catalog.resolveGameplayConstructionId(plot.getConstructionId());
        if (AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(gameplayId)) {
            return houseAssigneeLine(plugin, store, town, plot);
        }
        if (ProductionWorkplaceKinds.supportsWorkerAssignment(gameplayId)) {
            return workplaceAssigneeLine(plugin, store, town, plot, gameplayId);
        }
        return Message.translation(TAIL + "plotStatusComplete");
    }

    @Nonnull
    private static Message houseAssigneeLine(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot
    ) {
        UUID resident = plot.getHomeResidentEntityUuid();
        if (resident == null) {
            return Message.translation(TAIL + "plotHouseUnassigned");
        }
        String name = resolveHouseResidentName(plugin, store, town, resident);
        if (name == null || name.isBlank()) {
            return Message.translation(TAIL + "plotHouseUnassigned");
        }
        return Message.translation(TAIL + "plotHouseResident").param("name", Message.raw(name.trim()));
    }

    @Nonnull
    private static Message workplaceAssigneeLine(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull String gameplayWorkplaceId
    ) {
        UUID plotId = plot.getPlotId();
        if (ProductionWorkplaceKinds.isMultiRoleWorkplace(gameplayWorkplaceId)) {
            Message guildMaster =
                roleAssigneeLine(
                    plugin,
                    store,
                    town,
                    plotId,
                    gameplayWorkplaceId,
                    TownVillagerBinding.KIND_GUILD_MASTER,
                    AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID,
                    TOWN + "workplaceGuildMaster"
                );
            Message bard =
                roleAssigneeLine(
                    plugin,
                    store,
                    town,
                    plotId,
                    gameplayWorkplaceId,
                    TownVillagerBinding.KIND_BARD,
                    AetherhavenConstants.BARD_NPC_ROLE_ID,
                    TOWN + "workplaceBard"
                );
            return Message.join(guildMaster, Message.raw(" · "), bard);
        }
        String residentKind = ProductionWorkplaceKinds.residentBindingKindForGameplayConstruction(gameplayWorkplaceId);
        if (residentKind == null) {
            return Message.translation(TAIL + "plotStatusComplete");
        }
        UUID worker = findEntityWithJobPlotAndKind(store, town.getTownId(), plotId, residentKind);
        if (worker == null) {
            return Message.translation(TAIL + "plotWorkplaceUnassigned");
        }
        String name = resolveWorkerName(plugin, store, town, gameplayWorkplaceId, null, worker);
        if (name == null || name.isBlank()) {
            return Message.translation(TAIL + "plotWorkplaceUnassigned");
        }
        return Message.translation(TAIL + "plotWorkplaceWorker").param("name", Message.raw(name.trim()));
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
