package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;

/** Base class for Aetherhaven feature subplugins loaded from {@code manifest.json} SubPlugins. */
public abstract class AetherhavenSubplugin extends JavaPlugin {
    private static AetherhavenSubplugin latest;

    protected AetherhavenSubplugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nonnull
    protected final AetherhavenPlugin core() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            throw new IllegalStateException("Aetherhaven core plugin is not loaded");
        }
        return plugin;
    }

    @Override
    protected void setup() {
        if (!AetherhavenFeatures.shouldSetup(this)) {
            getLogger().atInfo().log("Skipping setup for disabled subplugin %s", getIdentifier());
            return;
        }
        latest = this;
        registerFeature();
    }

  @Override
  protected void start() {
    if (!AetherhavenFeatures.shouldSetup(this)) {
      return;
    }
    startFeature();
  }

  @Override
  protected void shutdown() {
    shutdownFeature();
    if (latest == this) {
      latest = null;
    }
  }

    protected abstract void registerFeature();

  protected void startFeature() {}

  protected void shutdownFeature() {}
}
