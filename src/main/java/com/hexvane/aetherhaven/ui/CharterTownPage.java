package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class CharterTownPage extends CustomUIPage {
    private final Ref<ChunkStore> charterBlockRef;

    public CharterTownPage(@Nonnull PlayerRef playerRef, @Nonnull Ref<ChunkStore> charterBlockRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction);
        this.charterBlockRef = charterBlockRef;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/CharterTownPage.ui");
        Store<ChunkStore> cs = charterBlockRef.getStore();
        CharterBlock ch = cs.getComponent(charterBlockRef, CharterBlock.getComponentType());
        String townIdStr = ch != null ? ch.getTownId() : "";
        if (townIdStr.isEmpty()) {
            commandBuilder.set("#TownInfo.TextSpans", Message.raw("Charter is not linked to a town yet."));
            return;
        }
        UUID townId;
        try {
            townId = UUID.fromString(townIdStr);
        } catch (IllegalArgumentException e) {
            commandBuilder.set("#TownInfo.TextSpans", Message.raw("Invalid town id on charter."));
            return;
        }
        World world = store.getExternalData().getWorld();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            commandBuilder.set("#TownInfo.TextSpans", Message.raw("Aetherhaven not loaded."));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord tr = tm.getTown(townId);
        if (tr == null) {
            commandBuilder.set("#TownInfo.TextSpans", Message.raw("Town data not found (see server log)."));
            return;
        }
        commandBuilder.set(
            "#TownInfo.TextSpans",
            Message.raw(
                "Town ID: "
                    + townId
                    + "\nTier: "
                    + tr.getTier()
                    + "\nTerritory (chunk radius): "
                    + tr.getTerritoryChunkRadius()
                    + "\nCharter at "
                    + tr.getCharterX()
                    + ", "
                    + tr.getCharterY()
                    + ", "
                    + tr.getCharterZ()
            )
        );
    }
}
