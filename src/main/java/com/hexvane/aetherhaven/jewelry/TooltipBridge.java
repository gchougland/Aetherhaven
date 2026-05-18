package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.logger.HytaleLogger;
import org.herolias.tooltips.api.DynamicTooltipsApi;
import org.herolias.tooltips.api.DynamicTooltipsApiProvider;

/** Registers the jewelry tooltip provider. */
public final class TooltipBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** {@code true} once {@link #register()} successfully installed the provider into DTL's registry. */
    private static volatile boolean registered;

    private TooltipBridge() {}

    /** Re-attempt registration when {@link DynamicTooltipsApiProvider} loads after plugin {@code setup()} (classpath order). */
    public static void registerIfNeeded() {
        if (registered) {
            return;
        }
        register();
    }

    public static boolean isRegistered() {
        return registered;
    }

    /** @return {@code true} once the provider is registered successfully */
    public static boolean register() {
        if (registered) {
            return true;
        }
        DynamicTooltipsApi api = DynamicTooltipsApiProvider.get();
        if (api == null) {
            LOGGER
                .atWarning()
                .log("DynamicTooltipsLib API not available yet — deferring jewelry tooltip registration to start()");
            return false;
        }
        synchronized (TooltipBridge.class) {
            if (registered) {
                return true;
            }
            api.registerProvider(new AetherhavenJewelryDynamicTooltipProvider());
            registered = true;
        }
        LOGGER.atInfo().log("Registered AetherhavenJewelryDynamicTooltipProvider with DynamicTooltipsLib");
        return true;
    }
}
