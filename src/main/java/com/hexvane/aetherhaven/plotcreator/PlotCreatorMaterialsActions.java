package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.construction.prefabmaterials.PrefabMaterialsService;
import com.hexvane.aetherhaven.ui.PlotCreatorMaterialsPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Auto fill and editing for the plot creator materials step. */
public final class PlotCreatorMaterialsActions {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";

    private PlotCreatorMaterialsActions() {}

    public static void onEnterMaterialsStep(@Nonnull PlotCreatorSession session) {
        PlotCreatorDraft draft = session.getDraft();
        if (!draft.getMaterials().isEmpty()) {
            return;
        }
        tryAutoFillFromBuildShape(session, false);
    }

    public static void requestFillFromBuildShape(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef
    ) {
        if (!session.getDraft().getMaterials().isEmpty() && !session.isMaterialsFillConfirmPending()) {
            session.setMaterialsFillConfirmPending(true);
            playerRef.sendMessage(Message.translation(MSG + ".materials.confirmReplace"));
            return;
        }
        session.setMaterialsFillConfirmPending(false);
        tryAutoFillFromBuildShape(session, true);
        if (session.getDraft().getMaterials().isEmpty()) {
            playerRef.sendMessage(Message.translation(MSG + ".materials.fillFailed"));
        }
    }

    public static void requestClearPrefabMaterials(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef
    ) {
        if (session.getDraft().getMaterials().isEmpty()) {
            return;
        }
        if (!session.isMaterialsClearConfirmPending()) {
            session.setMaterialsClearConfirmPending(true);
            playerRef.sendMessage(Message.translation(MSG + ".materials.confirmClear"));
            return;
        }
        session.setMaterialsClearConfirmPending(false);
        PlotCreatorMaterialsHelper.clearAllMaterials(session);
    }

    private static void tryAutoFillFromBuildShape(@Nonnull PlotCreatorSession session, boolean overwrite) {
        PlotCreatorDraft draft = session.getDraft();
        if (!overwrite && !draft.getMaterials().isEmpty()) {
            return;
        }
        String prefabPath = draft.getPrefabPath();
        if (prefabPath == null || prefabPath.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PrefabMaterialsService service = plugin.getPrefabMaterialsService();
        List<MaterialRequirement> materials = service.generateFromSessionPrefab(prefabPath, plugin.getDataDirectory());
        if (materials.isEmpty()) {
            return;
        }
        PlotCreatorMaterialsHelper.applyGeneratedMaterials(session, materials, true);
    }

    public static void openMaterialsPanel(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        session.getWorld().execute(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            closeOpenChestIfNeeded(session, player, ref, store);
            player.getPageManager().openCustomPage(ref, store, new PlotCreatorMaterialsPage(playerRef, session));
        });
    }

    public static void changeMaterialsPage(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        int delta,
        @Nullable Runnable afterChange
    ) {
        session.getWorld().execute(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            closeOpenChestIfNeeded(session, player, ref, store);
            if (!PlotCreatorMaterialsHelper.changePage(session, delta)) {
                return;
            }
            PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
            if (afterChange != null) {
                afterChange.run();
            }
        });
    }

    public static void adjustMaterialCount(
        @Nonnull PlotCreatorSession session,
        int materialIndex,
        int delta,
        @Nullable Runnable afterChange
    ) {
        session.getWorld().execute(() -> {
            PlotCreatorMaterialsHelper.adjustMaterialCount(session, materialIndex, delta);
            if (afterChange != null) {
                afterChange.run();
            }
        });
    }

    public static void setMaterialCount(
        @Nonnull PlotCreatorSession session,
        int materialIndex,
        int count,
        @Nullable Runnable afterChange
    ) {
        session.getWorld().execute(() -> {
            boolean changed = PlotCreatorMaterialsHelper.setMaterialCount(session, materialIndex, count);
            if (changed && afterChange != null) {
                afterChange.run();
            }
        });
    }

    public static void removeMaterial(
        @Nonnull PlotCreatorSession session,
        int materialIndex,
        @Nullable Runnable afterChange
    ) {
        session.getWorld().execute(() -> {
            PlotCreatorMaterialsHelper.removeMaterial(session, materialIndex);
            if (afterChange != null) {
                afterChange.run();
            }
        });
    }

    public static void openManualDepositChest(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        session.getWorld().execute(() -> {
            PlotCreatorMaterialsHelper.openManualDepositChest(playerRef, ref, store, session);
            PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        });
    }

    public static void clearFillConfirm(@Nonnull PlotCreatorSession session) {
        session.setMaterialsFillConfirmPending(false);
        session.setMaterialsClearConfirmPending(false);
    }

    private static void closeOpenChestIfNeeded(
        @Nonnull PlotCreatorSession session,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        if (!session.isMaterialsChestOpen()) {
            return;
        }
        if (session.isMaterialsManualDepositOpen()) {
            PlotCreatorMaterialsHelper.closeManualDepositChest(session, player, ref, store);
        } else {
            session.setMaterialsChestOpen(false);
            player.getPageManager().setPage(ref, store, com.hypixel.hytale.protocol.packets.interface_.Page.None);
        }
    }
}
