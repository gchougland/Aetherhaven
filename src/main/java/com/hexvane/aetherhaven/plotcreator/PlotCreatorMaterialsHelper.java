package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.config.PathToolStyleDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Plot creator build costs: prefab fill is draft-only (no spawned items). The deposit chest copies item counts
 * into the draft and always returns the physical items to the player.
 */
public final class PlotCreatorMaterialsHelper {
    /** Rows shown per page in the build materials menu. */
    public static final int UI_ROWS_PER_PAGE = 8;

    /** Slots in the manual deposit chest (player brings their own items). */
    public static final short DEPOSIT_CAPACITY = (short) PathToolStyleDefinition.STYLE_GRID_SLOTS;

    private PlotCreatorMaterialsHelper() {}

    public static int pageCount(@Nonnull PlotCreatorDraft draft) {
        int lines = draft.getMaterials().size();
        if (lines == 0) {
            return 1;
        }
        return (lines + UI_ROWS_PER_PAGE - 1) / UI_ROWS_PER_PAGE;
    }

    public static int clampPageIndex(@Nonnull PlotCreatorSession session) {
        int pages = pageCount(session.getDraft());
        int page = session.getMaterialsPageIndex();
        if (page < 0) {
            page = 0;
        } else if (page >= pages) {
            page = pages - 1;
        }
        session.setMaterialsPageIndex(page);
        return page;
    }

    @Nonnull
    public static List<MaterialRequirement> materialsPage(@Nonnull PlotCreatorSession session) {
        List<MaterialRequirement> all = session.getDraft().getMaterials();
        int page = clampPageIndex(session);
        int start = page * UI_ROWS_PER_PAGE;
        if (start >= all.size()) {
            return List.of();
        }
        int end = Math.min(start + UI_ROWS_PER_PAGE, all.size());
        return all.subList(start, end);
    }

    public static boolean changePage(@Nonnull PlotCreatorSession session, int delta) {
        int pages = pageCount(session.getDraft());
        int next = session.getMaterialsPageIndex() + delta;
        if (next < 0 || next >= pages) {
            return false;
        }
        session.setMaterialsPageIndex(next);
        return true;
    }

    public static void adjustMaterialCount(@Nonnull PlotCreatorSession session, int materialIndex, int delta) {
        List<MaterialRequirement> materials = session.getDraft().getMaterials();
        if (materialIndex < 0 || materialIndex >= materials.size()) {
            return;
        }
        MaterialRequirement current = materials.get(materialIndex);
        int next = current.getCount() + delta;
        if (next <= 0) {
            materials.remove(materialIndex);
            clampPageIndex(session);
            return;
        }
        materials.set(materialIndex, copyWithCount(current, next));
    }

    /**
     * Sets an absolute material count. Empty/invalid input is ignored. Count {@code <= 0} removes the row.
     *
     * @return {@code true} if the materials list changed (including removal)
     */
    public static boolean setMaterialCount(@Nonnull PlotCreatorSession session, int materialIndex, int count) {
        List<MaterialRequirement> materials = session.getDraft().getMaterials();
        if (materialIndex < 0 || materialIndex >= materials.size()) {
            return false;
        }
        if (count <= 0) {
            materials.remove(materialIndex);
            clampPageIndex(session);
            return true;
        }
        MaterialRequirement current = materials.get(materialIndex);
        if (current.getCount() == count) {
            return false;
        }
        materials.set(materialIndex, copyWithCount(current, count));
        return true;
    }

    public static void removeMaterial(@Nonnull PlotCreatorSession session, int materialIndex) {
        List<MaterialRequirement> materials = session.getDraft().getMaterials();
        if (materialIndex < 0 || materialIndex >= materials.size()) {
            return;
        }
        materials.remove(materialIndex);
        clampPageIndex(session);
    }

    /** Adds or merges a resource type line in the draft build cost list. */
    public static void addResourceType(
        @Nonnull PlotCreatorSession session,
        @Nonnull String resourceTypeId,
        int amount
    ) {
        if (resourceTypeId.isBlank() || amount <= 0) {
            return;
        }
        String id = resourceTypeId.trim();
        List<MaterialRequirement> materials = session.getDraft().getMaterials();
        mergeResourceTypeCount(materials, id, amount);
        session.setMaterialsAutoFilled(false);
        clampPageIndex(session);
    }

    public static void clearAllMaterials(@Nonnull PlotCreatorSession session) {
        session.getDraft().getMaterials().clear();
        session.setMaterialsAutoFilled(false);
        session.setMaterialsFillConfirmPending(false);
        session.setMaterialsPageIndex(0);
        SimpleItemContainer container = session.getMaterialsContainer();
        if (container != null) {
            container.clear();
        }
    }

    public static void applyGeneratedMaterials(
        @Nonnull PlotCreatorSession session,
        @Nonnull List<MaterialRequirement> materials,
        boolean autoFilled
    ) {
        session.getDraft().getMaterials().clear();
        session.getDraft().getMaterials().addAll(materials);
        session.setMaterialsAutoFilled(autoFilled);
        session.setMaterialsFillConfirmPending(false);
        session.setMaterialsPageIndex(0);
    }

    @Nonnull
    private static SimpleItemContainer ensureDepositContainer(@Nonnull PlotCreatorSession session) {
        SimpleItemContainer existing = session.getMaterialsContainer();
        if (existing != null) {
            return existing;
        }
        SimpleItemContainer created = new SimpleItemContainer(DEPOSIT_CAPACITY);
        session.setMaterialsContainer(created);
        return created;
    }

    /** Opens an empty chest so the player can deposit items from their inventory into the build cost list. */
    public static void openManualDepositChest(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        SimpleItemContainer container = ensureDepositContainer(session);
        container.clear();
        session.setMaterialsManualDepositOpen(true);
        session.setMaterialsChestOpen(true);
        ContainerWindow window = new ContainerWindow(container);
        player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
    }

    /** Records deposited item counts in the draft, returns items to the player, and closes the chest. */
    public static void closeManualDepositChest(
        @Nonnull PlotCreatorSession session,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        mergeDepositChestIntoDraft(session);
        SimpleItemContainer container = session.getMaterialsContainer();
        if (container != null) {
            returnChestContentsToPlayer(container, player, ref, store);
            container.clear();
        }
        session.setMaterialsManualDepositOpen(false);
        session.setMaterialsChestOpen(false);
        player.getPageManager().setPage(ref, store, Page.None);
    }

    /** Returns deposit chest items without updating the draft (session cancel). */
    public static void returnDepositChestToPlayer(
        @Nonnull PlotCreatorSession session,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        SimpleItemContainer container = session.getMaterialsContainer();
        if (container != null) {
            returnChestContentsToPlayer(container, player, ref, store);
            container.clear();
        }
        session.setMaterialsManualDepositOpen(false);
        session.setMaterialsChestOpen(false);
        player.getPageManager().setPage(ref, store, Page.None);
    }

    /** When leaving the materials step, commit any open deposit chest into the draft. */
    public static void snapshotAndCloseMaterials(
        @Nonnull PlotCreatorSession session,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        if (session.isMaterialsChestOpen()) {
            if (session.isMaterialsManualDepositOpen()) {
                closeManualDepositChest(session, player, ref, store);
            } else {
                session.setMaterialsChestOpen(false);
                player.getPageManager().setPage(ref, store, Page.None);
            }
        }
    }

    private static void mergeDepositChestIntoDraft(@Nonnull PlotCreatorSession session) {
        SimpleItemContainer container = session.getMaterialsContainer();
        if (container == null) {
            return;
        }
        Map<String, Integer> deposited = new HashMap<>();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            String id = stack.getItemId();
            if (id == null || id.isBlank()) {
                continue;
            }
            deposited.merge(id.trim(), stack.getQuantity(), Integer::sum);
        }
        if (deposited.isEmpty()) {
            return;
        }
        List<MaterialRequirement> materials = session.getDraft().getMaterials();
        for (Map.Entry<String, Integer> entry : deposited.entrySet()) {
            mergeItemCount(materials, entry.getKey(), entry.getValue());
        }
        session.setMaterialsAutoFilled(false);
    }

    private static void returnChestContentsToPlayer(
        @Nonnull SimpleItemContainer container,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            player.giveItem(stack, ref, store);
        }
    }

    private static void mergeItemCount(
        @Nonnull List<MaterialRequirement> materials,
        @Nonnull String itemId,
        int addCount
    ) {
        for (int i = 0; i < materials.size(); i++) {
            MaterialRequirement m = materials.get(i);
            if (m.getItemId() != null && itemId.equals(m.getItemId())) {
                materials.set(i, MaterialRequirement.ofItem(itemId, m.getCount() + addCount));
                return;
            }
        }
        materials.add(MaterialRequirement.ofItem(itemId, addCount));
    }

    private static void mergeResourceTypeCount(
        @Nonnull List<MaterialRequirement> materials,
        @Nonnull String resourceTypeId,
        int addCount
    ) {
        for (int i = 0; i < materials.size(); i++) {
            MaterialRequirement m = materials.get(i);
            String rt = m.getResourceTypeId();
            if (rt != null && resourceTypeId.equals(rt)) {
                materials.set(i, MaterialRequirement.ofResourceType(resourceTypeId, m.getCount() + addCount));
                return;
            }
        }
        materials.add(MaterialRequirement.ofResourceType(resourceTypeId, addCount));
    }

    @Nonnull
    private static MaterialRequirement copyWithCount(@Nonnull MaterialRequirement m, int count) {
        String rt = m.getResourceTypeId();
        if (rt != null && !rt.isBlank()) {
            return MaterialRequirement.ofResourceType(rt, count);
        }
        String itemId = m.getItemId();
        if (itemId != null && !itemId.isBlank()) {
            return MaterialRequirement.ofItem(itemId, count);
        }
        return m;
    }
}
