package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

/**
 * Per-player town gameplay permissions (owner and members). Serialized under {@link TownRecord}.
 * Editing who may open the management permissions UI is always owner-only in code, not a stored flag.
 */
public final class TownMemberPermissions {
    @SerializedName("placePlots")
    private boolean placePlots;

    @SerializedName("manageConstructions")
    private boolean manageConstructions;

    @SerializedName("spendTreasuryGold")
    private boolean spendTreasuryGold;

    @SerializedName("openTreasuryPanel")
    private boolean openTreasuryPanel;

    @SerializedName("acceptQuests")
    private boolean acceptQuests;

    @SerializedName("completeQuests")
    private boolean completeQuests;

    @SerializedName("abandonQuests")
    private boolean abandonQuests;

    @SerializedName("reviveVillagers")
    private boolean reviveVillagers;

    public TownMemberPermissions() {}

    public TownMemberPermissions(
        boolean placePlots,
        boolean manageConstructions,
        boolean spendTreasuryGold,
        boolean openTreasuryPanel,
        boolean acceptQuests,
        boolean completeQuests,
        boolean abandonQuests,
        boolean reviveVillagers
    ) {
        this.placePlots = placePlots;
        this.manageConstructions = manageConstructions;
        this.spendTreasuryGold = spendTreasuryGold;
        this.openTreasuryPanel = openTreasuryPanel;
        this.acceptQuests = acceptQuests;
        this.completeQuests = completeQuests;
        this.abandonQuests = abandonQuests;
        this.reviveVillagers = reviveVillagers;
    }

    @Nonnull
    public static TownMemberPermissions fullMember() {
        return new TownMemberPermissions(true, true, true, true, true, true, true, true);
    }

    @Nonnull
    public static TownMemberPermissions fromRole(@Nonnull TownMemberRole role) {
        return switch (role) {
            case BUILD -> new TownMemberPermissions(true, true, true, false, false, false, false, false);
            case QUEST -> new TownMemberPermissions(false, false, false, false, true, true, true, false);
            case BOTH -> fullMember();
        };
    }

    /** Best-effort legacy enum for saves that still carry {@link TownRecord#getMemberRolesRaw()}. */
    @Nonnull
    public TownMemberRole toCoarseRole() {
        boolean b = placePlots || manageConstructions || spendTreasuryGold;
        boolean q = acceptQuests || completeQuests || abandonQuests;
        if (b && q) {
            return TownMemberRole.BOTH;
        }
        if (b) {
            return TownMemberRole.BUILD;
        }
        if (q) {
            return TownMemberRole.QUEST;
        }
        return TownMemberRole.QUEST;
    }

    public boolean placePlots() {
        return placePlots;
    }

    public void setPlacePlots(boolean placePlots) {
        this.placePlots = placePlots;
    }

    public boolean manageConstructions() {
        return manageConstructions;
    }

    public void setManageConstructions(boolean manageConstructions) {
        this.manageConstructions = manageConstructions;
    }

    public boolean spendTreasuryGold() {
        return spendTreasuryGold;
    }

    public void setSpendTreasuryGold(boolean spendTreasuryGold) {
        this.spendTreasuryGold = spendTreasuryGold;
    }

    public boolean openTreasuryPanel() {
        return openTreasuryPanel;
    }

    public void setOpenTreasuryPanel(boolean openTreasuryPanel) {
        this.openTreasuryPanel = openTreasuryPanel;
    }

    public boolean acceptQuests() {
        return acceptQuests;
    }

    public void setAcceptQuests(boolean acceptQuests) {
        this.acceptQuests = acceptQuests;
    }

    public boolean completeQuests() {
        return completeQuests;
    }

    public void setCompleteQuests(boolean completeQuests) {
        this.completeQuests = completeQuests;
    }

    public boolean abandonQuests() {
        return abandonQuests;
    }

    public void setAbandonQuests(boolean abandonQuests) {
        this.abandonQuests = abandonQuests;
    }

    public boolean reviveVillagers() {
        return reviveVillagers;
    }

    public void setReviveVillagers(boolean reviveVillagers) {
        this.reviveVillagers = reviveVillagers;
    }

    @Nonnull
    public TownMemberPermissions copy() {
        return new TownMemberPermissions(
            placePlots,
            manageConstructions,
            spendTreasuryGold,
            openTreasuryPanel,
            acceptQuests,
            completeQuests,
            abandonQuests,
            reviveVillagers
        );
    }
}
