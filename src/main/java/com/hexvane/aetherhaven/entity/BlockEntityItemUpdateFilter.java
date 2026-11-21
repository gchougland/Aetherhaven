package com.hexvane.aetherhaven.entity;

import com.hypixel.hytale.protocol.BlockUpdate;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.ItemUpdate;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps decorations at their authored size. Most prefab decorations are block entities that also carry an item, so a
 * player is sent both an item update and a block update for them. The client draws the block at its entity scale, but it
 * remembers the item, and every {@code UpdateItems} packet with models or icons makes it re-apply all entities item
 * first, multiplying that scale by the item's own {@code Scale} and the block's {@code CustomModelScale}. A Village
 * Statue at 2.4 shrank to 1.44 whenever a player first saw a new plot token, prop, palette or jewelry icon, and came back
 * only when it was next sent again. Particle, trail and block type asset updates re-apply the same way. Read from the
 * 0.7.5 client: the asset update handlers and the entity set item, set block and re-apply functions.
 *
 * <p>The client is only told the block, so it has no item to go back to. Nothing changes on the entity itself, which keeps
 * saved worlds and community buildings as they are.
 */
public final class BlockEntityItemUpdateFilter {
    @Nullable
    private PacketFilter outboundFilter;

    public void register() {
        outboundFilter = PacketAdapters.registerOutbound((PlayerPacketFilter) BlockEntityItemUpdateFilter::onOutboundPacket);
    }

    public void deregister() {
        if (outboundFilter != null) {
            PacketAdapters.deregisterOutbound(outboundFilter);
            outboundFilter = null;
        }
    }

    private static boolean onOutboundPacket(@Nonnull PlayerRef player, @Nonnull Packet packet) {
        if (packet instanceof EntityUpdates updates && updates.updates != null) {
            for (EntityUpdate update : updates.updates) {
                dropItemIfAlsoBlock(update);
            }
        }
        return false;
    }

    static void dropItemIfAlsoBlock(@Nullable EntityUpdate entity) {
        if (entity == null || entity.updates == null) {
            return;
        }
        boolean block = false;
        int items = 0;
        for (ComponentUpdate update : entity.updates) {
            if (update instanceof BlockUpdate) {
                block = true;
            } else if (update instanceof ItemUpdate) {
                items++;
            }
        }
        if (block && items > 0) {
            entity.updates =
                Arrays.stream(entity.updates).filter(update -> !(update instanceof ItemUpdate)).toArray(ComponentUpdate[]::new);
        }
    }
}
