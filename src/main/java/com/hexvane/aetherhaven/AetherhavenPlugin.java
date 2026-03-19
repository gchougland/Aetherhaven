package com.hexvane.aetherhaven;

import com.hexvane.aetherhaven.command.AetherhavenCommand;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.ui.PlotConstructionPage;
import com.hexvane.aetherhaven.ui.PlotSignAdminPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AetherhavenPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static volatile AetherhavenPlugin instance;

    /** Same pattern as OrbisOrigins: {@code config.json} under the plugin data directory. */
    private final Config<AetherhavenPluginConfig> config = this.withConfig("config", AetherhavenPluginConfig.CODEC);
    private ConstructionCatalog constructionCatalog = ConstructionCatalog.loadFromClasspath(AetherhavenPlugin.class.getClassLoader());
    private ScheduledExecutorService constructionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Aetherhaven-Construction");
        t.setDaemon(true);
        return t;
    });

    public AetherhavenPlugin(JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static AetherhavenPlugin get() {
        return instance;
    }

    @Nonnull
    public Config<AetherhavenPluginConfig> getConfig() {
        return config;
    }

    @Nonnull
    public ConstructionCatalog getConstructionCatalog() {
        return constructionCatalog;
    }

    /**
     * Schedules a task on the world's thread after a delay. Used so construction batches spread across time.
     */
    public void scheduleOnWorld(@Nonnull com.hypixel.hytale.server.core.universe.world.World world, @Nonnull Runnable worldTask, long delayMs) {
        long delay = Math.max(1L, delayMs);
        constructionScheduler.schedule(() -> world.execute(worldTask), delay, TimeUnit.MILLISECONDS);
    }

    @Override
    @SuppressWarnings("removal")
    protected void setup() {
        instance = this;

        this.config.get();
        Path configPath = this.getDataDirectory().resolve("config.json");
        if (!Files.exists(configPath)) {
            this.config.save().join();
            LOGGER.atInfo().log("Created default config at %s", configPath);
        }

        PlotSignBlock.register(this.getChunkStoreRegistry());
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            PlotConstructionPage.class,
            AetherhavenConstants.PAGE_PLOT_CONSTRUCTION,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
                if (chunk == null) {
                    return null;
                }
                BlockPosition base = world.getBaseBlock(targetBlock);
                Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(base.x, base.y, base.z);
                if (blockRef == null || blockRef.getStore().getComponent(blockRef, PlotSignBlock.getComponentType()) == null) {
                    return null;
                }
                Vector3i signWorld = new Vector3i(base.x, base.y, base.z);
                return new PlotConstructionPage(playerRef, blockRef, signWorld);
            }
        );
        OpenCustomUIInteraction.registerSimple(
            this,
            PlotSignAdminPage.class,
            AetherhavenConstants.PAGE_PLOT_SIGN_ADMIN,
            PlotSignAdminPage::new
        );
        this.getCommandRegistry().registerCommand(new AetherhavenCommand());
        LOGGER.atInfo().log("Aetherhaven v%s loaded", this.getManifest().getVersion().toString());
    }

    @Override
    protected void start() {
        this.config.get();
        this.constructionCatalog = ConstructionCatalog.loadFromClasspath(this.getClassLoader());
        LOGGER.atInfo().log("Aetherhaven constructions loaded: %s", this.constructionCatalog.ids());
    }

    @Override
    protected void shutdown() {
        instance = null;
        this.constructionScheduler.shutdown();
        try {
            if (!this.constructionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                this.constructionScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.constructionScheduler.shutdownNow();
        }
    }
}
