package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.logger.HytaleLogger;
import org.herolias.tooltips.api.DynamicTooltipsApi;
import org.herolias.tooltips.api.DynamicTooltipsApiProvider;

/** Registers the jewelry tooltip provider. */
public final class TooltipBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TooltipBridge() {}

    /**
     * @return {@code true} if the provider was registered
     */
    public static boolean register() {
        DynamicTooltipsApi api = DynamicTooltipsApiProvider.get();
        if (api != null) {
            api.registerProvider(new AetherhavenJewelryDynamicTooltipProvider());
            LOGGER.atInfo().log("Registered AetherhavenJewelryDynamicTooltipProvider with DynamicTooltipsLib");
            return true;
        }
        LOGGER
            .atWarning()
            .log("DynamicTooltipsLib API not yet initialized — jewelry tooltips will not display");
        return false;
    }
}
