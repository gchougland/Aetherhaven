package com.hexvane.aetherhaven.dialogue;

import javax.annotation.Nullable;

/** Aggregated outcome from running a list of dialogue actions in order. */
public final class DialogueActionBatchResult {
    private boolean closeDialogue;
    @Nullable
    private String gotoNodeId;
    @Nullable
    private String openBarterShopAfterClose;

    private boolean openBlacksmithRepairAfterClose;

    private boolean openGeodePageAfterClose;

    private boolean openJewelryAppraisalAfterClose;

    private boolean jewelryAppraisalChargeGold = true;

    private boolean openTreeClimbLeaderboardAfterClose;

    private boolean openHallowsEveLeaderboardAfterClose;

    private boolean openMarketLeaderboardAfterClose;

    @Nullable
    private Runnable afterClose;

    public boolean isCloseDialogue() {
        return closeDialogue;
    }

    public void setCloseDialogue(boolean closeDialogue) {
        this.closeDialogue = closeDialogue;
    }

    @Nullable
    public String getGotoNodeId() {
        return gotoNodeId;
    }

    public void setGotoNodeId(@Nullable String gotoNodeId) {
        this.gotoNodeId = gotoNodeId;
    }

    @Nullable
    public String getOpenBarterShopAfterClose() {
        return openBarterShopAfterClose;
    }

    public void setOpenBarterShopAfterClose(@Nullable String openBarterShopAfterClose) {
        this.openBarterShopAfterClose = openBarterShopAfterClose;
    }

    public boolean isOpenBlacksmithRepairAfterClose() {
        return openBlacksmithRepairAfterClose;
    }

    public void setOpenBlacksmithRepairAfterClose(boolean openBlacksmithRepairAfterClose) {
        this.openBlacksmithRepairAfterClose = openBlacksmithRepairAfterClose;
    }

    public boolean isOpenGeodePageAfterClose() {
        return openGeodePageAfterClose;
    }

    public void setOpenGeodePageAfterClose(boolean openGeodePageAfterClose) {
        this.openGeodePageAfterClose = openGeodePageAfterClose;
    }

    public boolean isOpenJewelryAppraisalAfterClose() {
        return openJewelryAppraisalAfterClose;
    }

    public void setOpenJewelryAppraisalAfterClose(boolean openJewelryAppraisalAfterClose) {
        this.openJewelryAppraisalAfterClose = openJewelryAppraisalAfterClose;
    }

    public boolean isJewelryAppraisalChargeGold() {
        return jewelryAppraisalChargeGold;
    }

    public void setJewelryAppraisalChargeGold(boolean jewelryAppraisalChargeGold) {
        this.jewelryAppraisalChargeGold = jewelryAppraisalChargeGold;
    }

    public boolean isOpenTreeClimbLeaderboardAfterClose() {
        return openTreeClimbLeaderboardAfterClose;
    }

    public void setOpenTreeClimbLeaderboardAfterClose(boolean openTreeClimbLeaderboardAfterClose) {
        this.openTreeClimbLeaderboardAfterClose = openTreeClimbLeaderboardAfterClose;
    }

    public boolean isOpenHallowsEveLeaderboardAfterClose() {
        return openHallowsEveLeaderboardAfterClose;
    }

    public void setOpenHallowsEveLeaderboardAfterClose(boolean openHallowsEveLeaderboardAfterClose) {
        this.openHallowsEveLeaderboardAfterClose = openHallowsEveLeaderboardAfterClose;
    }

    public boolean isOpenMarketLeaderboardAfterClose() {
        return openMarketLeaderboardAfterClose;
    }

    public void setOpenMarketLeaderboardAfterClose(boolean openMarketLeaderboardAfterClose) {
        this.openMarketLeaderboardAfterClose = openMarketLeaderboardAfterClose;
    }

    /**
     * Optional work to run after dialogue closes (via {@code world.execute}), e.g. opening another mod's CustomUI.
     * Prefer this over opening pages synchronously inside a dialogue action handler.
     */
    @Nullable
    public Runnable getAfterClose() {
        return afterClose;
    }

    public void setAfterClose(@Nullable Runnable afterClose) {
        this.afterClose = afterClose;
    }

    public boolean hasAfterClose() {
        return afterClose != null;
    }
}
