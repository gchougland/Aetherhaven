package com.hexvane.aetherhaven;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class AetherhavenPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public AetherhavenPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Aetherhaven v%s loaded", this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        // Systems (dimension, island grid, dialogue, construction, achievements, economy, raids)
        // will be registered here as development progresses.
    }
}
