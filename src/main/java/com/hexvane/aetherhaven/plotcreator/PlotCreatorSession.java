package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotCreatorSession {
    @Nonnull
    private final UUID playerUuid;
    @Nonnull
    private final World world;
    @Nonnull
    private final PlotCreatorDraft draft = new PlotCreatorDraft();
    @Nullable
    private SimpleItemContainer materialsContainer;
    private int materialsPageIndex;
    private boolean materialsAutoFilled;
    private boolean materialsFillConfirmPending;
    private boolean materialsClearConfirmPending;
    private boolean materialsChestOpen;
    /** True while the player is depositing real items from inventory into build costs. */
    private boolean materialsManualDepositOpen;
    /** Substep index → item id → quantity granted for placement (revoked when stepping back). */
    @Nonnull
    private final Map<Integer, Map<String, Integer>> substepGrants = new HashMap<>();

    public PlotCreatorSession(@Nonnull UUID playerUuid, @Nonnull World world) {
        this.playerUuid = playerUuid;
        this.world = world;
    }

    @Nonnull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    @Nonnull
    public PlotCreatorDraft getDraft() {
        return draft;
    }

    @Nullable
    public SimpleItemContainer getMaterialsContainer() {
        return materialsContainer;
    }

    public void setMaterialsContainer(@Nullable SimpleItemContainer materialsContainer) {
        this.materialsContainer = materialsContainer;
    }

    public int getMaterialsPageIndex() {
        return materialsPageIndex;
    }

    public void setMaterialsPageIndex(int materialsPageIndex) {
        this.materialsPageIndex = Math.max(0, materialsPageIndex);
    }

    public boolean isMaterialsAutoFilled() {
        return materialsAutoFilled;
    }

    public void setMaterialsAutoFilled(boolean materialsAutoFilled) {
        this.materialsAutoFilled = materialsAutoFilled;
    }

    public boolean isMaterialsFillConfirmPending() {
        return materialsFillConfirmPending;
    }

    public void setMaterialsFillConfirmPending(boolean materialsFillConfirmPending) {
        this.materialsFillConfirmPending = materialsFillConfirmPending;
    }

    public boolean isMaterialsClearConfirmPending() {
        return materialsClearConfirmPending;
    }

    public void setMaterialsClearConfirmPending(boolean materialsClearConfirmPending) {
        this.materialsClearConfirmPending = materialsClearConfirmPending;
    }

    public boolean isMaterialsChestOpen() {
        return materialsChestOpen;
    }

    public void setMaterialsChestOpen(boolean materialsChestOpen) {
        this.materialsChestOpen = materialsChestOpen;
    }

    public boolean isMaterialsManualDepositOpen() {
        return materialsManualDepositOpen;
    }

    public void setMaterialsManualDepositOpen(boolean materialsManualDepositOpen) {
        this.materialsManualDepositOpen = materialsManualDepositOpen;
    }

    @Nonnull
    public Map<Integer, Map<String, Integer>> getSubstepGrants() {
        return substepGrants;
    }
}
