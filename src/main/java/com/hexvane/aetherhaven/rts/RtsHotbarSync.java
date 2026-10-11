package com.hexvane.aetherhaven.rts;

import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.protocol.packets.inventory.UpdatePlayerInventory;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/** Pushes hotbar contents and active slot to the client after a server-side RTS hotbar rewrite. */
public final class RtsHotbarSync {
    private RtsHotbarSync() {}

    /**
     * One-shot sync after {@link RtsInventorySwap} replaces hotbar items (enter/exit RTS).
     * Matches vanilla spawn order: inventory section update, then hotbar active slot.
     * Clears the hotbar dirty flag so {@code PlayerSendInventorySystem} does not send a duplicate update next tick.
     */
    public static void syncHotbarRewriteToClient(
        @Nonnull PlayerRef playerRefComponent,
        @Nonnull InventoryComponent.Hotbar hotbar
    ) {
        RtsCommandHotbarSlotInboundAdapter.runWithOutboundHotbarSlotSync(() -> {
            playerRefComponent
                .getPacketHandler()
                .writeNoCache(new UpdatePlayerInventory(null, null, hotbar.getInventory().toPacket(), null, null, null, null, null));
            playerRefComponent.getPacketHandler().writeNoCache(new SetActiveSlot(-1, hotbar.getActiveSlot()));
            hotbar.consumeIsDirty();
            playerRefComponent.getPacketHandler().tryFlush();
            RtsDiagnostics.hotbarSync(playerRefComponent, "hotbar-rewrite", hotbar.getActiveSlot());
        });
    }
}
