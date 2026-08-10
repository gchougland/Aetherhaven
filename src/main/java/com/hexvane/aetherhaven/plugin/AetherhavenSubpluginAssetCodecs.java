package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagersBootstrap;
import com.hexvane.aetherhaven.bard.BardBootstrap;
import com.hexvane.aetherhaven.construction.ConstructionBootstrap;
import com.hexvane.aetherhaven.dialogue.DialogueBootstrap;
import com.hexvane.aetherhaven.economy.EconomyBootstrap;
import com.hexvane.aetherhaven.festival.FestivalsBootstrap;
import com.hexvane.aetherhaven.floatinggift.FloatingGiftsBootstrap;
import com.hexvane.aetherhaven.guild.GuildBootstrap;
import com.hexvane.aetherhaven.jewelry.JewelryBootstrap;
import com.hexvane.aetherhaven.pathtool.PathDesignerBootstrap;
import com.hexvane.aetherhaven.patrol.PatrolRoutesBootstrap;
import com.hexvane.aetherhaven.plotcreator.PlotCreatorBootstrap;
import com.hexvane.aetherhaven.production.ProductionBootstrap;
import com.hexvane.aetherhaven.quest.QuestsBootstrap;
import com.hexvane.aetherhaven.reputation.ReputationBootstrap;
import com.hexvane.aetherhaven.rts.RtsBootstrap;
import com.hexvane.aetherhaven.shopspot.CommerceBootstrap;
import com.hexvane.aetherhaven.worldnpc.WorldNpcsBootstrap;
import javax.annotation.Nonnull;

/**
 * Registers interaction codecs, custom UI pages, and other asset-referenced types on the parent plugin so the shared
 * asset pack decodes when subplugins are config-disabled. Runtime systems stay in each subplugin's {@code setup()}.
 */
public final class AetherhavenSubpluginAssetCodecs {
    private AetherhavenSubpluginAssetCodecs() {}

    public static void registerAll(@Nonnull AetherhavenPlugin core) {
        AdminToolsBootstrap.registerAssetCodecs(core);
        PathDesignerBootstrap.registerAssetCodecs(core);
        PatrolRoutesBootstrap.registerAssetCodecs(core);
        RtsBootstrap.registerAssetCodecs(core);
        PlotCreatorBootstrap.registerAssetCodecs(core);
        ReputationUnlocksBootstrap.registerAssetCodecs(core);
        JewelryBootstrap.registerAssetCodecs(core);
        EconomyBootstrap.registerAssetCodecs(core);
        CommerceBootstrap.registerAssetCodecs(core);
        QuestsBootstrap.registerAssetCodecs(core);
        ConstructionBootstrap.registerAssetCodecs(core);
        ProductionBootstrap.registerAssetCodecs(core);
        VillagersBootstrap.registerAssetCodecs(core);
        DialogueBootstrap.registerAssetCodecs(core);
        WorldNpcsBootstrap.registerAssetCodecs(core);

        // No asset-pack interaction/UI registrations; kept for symmetry if added later.
        FloatingGiftsBootstrap.registerAssetCodecs(core);
        BardBootstrap.registerAssetCodecs(core);
        ReputationBootstrap.registerAssetCodecs(core);
        GuildBootstrap.registerAssetCodecs(core);
        FestivalsBootstrap.registerAssetCodecs(core);
    }
}
