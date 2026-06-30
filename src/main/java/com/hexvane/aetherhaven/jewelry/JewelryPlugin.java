package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.jewelry.JewelryGemTraits;
import com.hexvane.aetherhaven.jewelry.JewelryNativeTooltipManager;
import com.hexvane.aetherhaven.jewelry.LootrIntegration;
import com.hexvane.aetherhaven.plugin.AetherhavenSubplugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class JewelryPlugin extends AetherhavenSubplugin {
    @Nullable
    private static JewelryPlugin instance;

    public JewelryPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static JewelryPlugin get() {
        return instance;
    }

    @Override
    protected void registerFeature() {
        instance = this;
        JewelryBootstrap.register(core(), this);
    }

    @Override
    protected void startFeature() {
        JewelryNativeTooltipManager.refreshAllPlayers();
        JewelryGemTraits.validateStatIdsAtStartup();
        LootrIntegration.registerIfAvailable(core());
    }
}
