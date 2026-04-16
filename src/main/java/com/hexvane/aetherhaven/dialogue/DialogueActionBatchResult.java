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
}
