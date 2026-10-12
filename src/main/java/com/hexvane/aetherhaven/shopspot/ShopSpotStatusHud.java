package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class ShopSpotStatusHud extends CustomUIHud {
    private static final String MSG = "aetherhaven_shop.aetherhaven.shop.hud";

    public ShopSpotStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.SHOP_SPOT_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/ShopSpotStatusHud.ui");
    }

    public void refresh(
        @Nonnull World world,
        @Nonnull ShopSpotRecord record,
        @Nonnull TownRecord town,
        boolean gameDay,
        @Nonnull UUID viewerUuid,
        @Nonnull AetherhavenPlugin plugin
    ) {
        UICommandBuilder b = new UICommandBuilder();
        b.set("#ShopSpotHudTitle.TextSpans", Message.translation(MSG + ".title"));
        applyHint(b, record, town, viewerUuid, gameDay, getPlayerRef());
        if (!gameDay) {
            showClosed(b, Message.translation(MSG + ".closedNight"));
            this.update(false, b);
            return;
        }
        if (!record.hasStock()) {
            String emptyKey = record.isPlayerControlled() ? MSG + ".emptyListing" : MSG + ".soldOut";
            showClosed(b, Message.translation(emptyKey));
            this.update(false, b);
            return;
        }
        Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (store != null && !ShopSpotOpenService.hasStaffedWorkplace(record, town, store)) {
            var plot = town.findPlotById(record.getPlotId());
            showClosed(b, record.isPlayerControlled() ? Message.raw(plot != null && plot.getShopClerkId() != null
                ? "Closed. Your clerk is away from the counter."
                : "Closed. Assign a clerk at the Town Records Shelf.") : Message.translation(MSG + ".closed"));
            this.update(false, b);
            return;
        }
        b.set("#OpenPanel.Visible", true);
        b.set("#ClosedPanel.Visible", false);
        UUID sellerUuid = record.getSellerUuid();
        if (record.isPlayerControlled() && sellerUuid != null) {
            String sellerName = record.sellerDisplayName(world);
            if (!sellerName.isBlank()) {
                b.set("#SellerLine.Visible", true);
                b.set(
                    "#SellerLine.TextSpans",
                    Message.translation(MSG + ".seller").param("seller", sellerName)
                );
            } else {
                b.set("#SellerLine.Visible", false);
            }
        } else {
            b.set("#SellerLine.Visible", false);
        }
        String itemId = record.getItemId();
        if (itemId != null) {
            // The stack's own name and details, as its inventory tooltip gives them: what is in a flask, a ring's
            // stats, how worn a tool is.
            Message itemName = ShopListingText.name(record);
            b.set("#ItemLine.TextSpans", Message.translation(MSG + ".item").param("item", itemName));
            Message details = ShopListingText.details(record);
            b.set("#DetailRow.Visible", details != null);
            if (details != null) {
                b.set("#DetailLine.TextSpans", details);
            }
            ShopPriceEntry entry = ShopSpotPricing.entry(plugin, record);
            long gold = ShopSpotPricing.goldPerBatch(plugin, record, itemId);
            if (entry.isBatched()) {
                b.set(
                    "#PriceLine.TextSpans",
                    Message.translation(MSG + ".priceBatch")
                        .param("gold", String.valueOf(gold))
                        .param("count", String.valueOf(entry.getBatchSize()))
                        .param("item", itemName)
                );
                int batches = entry.batchCountFromItemStock(record.getStock());
                b.set(
                    "#StockLine.TextSpans",
                    Message.translation(MSG + ".stockBatches")
                        .param("batches", String.valueOf(batches))
                        .param("items", String.valueOf(record.getStock()))
                );
            } else {
                b.set(
                    "#PriceLine.TextSpans",
                    Message.translation(MSG + ".price").param("gold", String.valueOf(gold))
                );
                b.set(
                    "#StockLine.TextSpans",
                    Message.translation(MSG + ".stock").param("n", String.valueOf(record.getStock()))
                );
            }
        }
        this.update(false, b);
    }

    private static void applyHint(
        @Nonnull UICommandBuilder b,
        @Nonnull ShopSpotRecord record,
        @Nonnull TownRecord town,
        @Nonnull UUID viewerUuid,
        boolean gameDay,
        @Nonnull PlayerRef playerRef
    ) {
        String hintKey = ShopSpotHudHints.hintTranslationKey(record, town, viewerUuid, gameDay);
        ShopSpotHudHotkeyHints.appendHintRows(b, "#HintRows", hintKey, playerRef);
    }

    private static void showClosed(@Nonnull UICommandBuilder b, @Nonnull Message line) {
        b.set("#OpenPanel.Visible", false);
        b.set("#ClosedPanel.Visible", true);
        b.set("#ClosedLine.TextSpans", line);
    }
}
