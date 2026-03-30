package com.hexvane.aetherhaven;

import com.hexvane.aetherhaven.charter.CharterPlaceEventSystem;
import com.hexvane.aetherhaven.command.AetherhavenCommand;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.dialogue.AetherhavenDialogueWorldView;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueResolver;
import com.hexvane.aetherhaven.dialogue.DialogueWorldView;
import com.hexvane.aetherhaven.npc.BuilderActionOpenAetherhavenDialogue;
import com.hexvane.aetherhaven.placement.PlotConstructionBlockResolver;
import com.hexvane.aetherhaven.placement.PlotPlacementOpenHelper;
import com.hexvane.aetherhaven.plot.CharterBlock;
import com.hexvane.aetherhaven.plot.ManagementBlock;
import com.hexvane.aetherhaven.plot.PlotSignBlock;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.VillagerNeedsDecaySystem;
import com.hexvane.aetherhaven.inn.InnPoolTickSystem;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.ui.CharterTownPage;
import com.hexvane.aetherhaven.ui.PlotConstructionPage;
import com.hexvane.aetherhaven.ui.PlotPlacementPage;
import com.hexvane.aetherhaven.ui.PlotSignAdminPage;
import com.hexvane.aetherhaven.ui.QuestJournalPage;
import com.hexvane.aetherhaven.ui.VillagerNeedsOverviewPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
    private DialogueCatalog dialogueCatalog = DialogueCatalog.loadFromClasspath(AetherhavenPlugin.class.getClassLoader());
    private final DialogueResolver dialogueResolver = new DialogueResolver();
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

    @Nonnull
    public DialogueCatalog getDialogueCatalog() {
        return dialogueCatalog;
    }

    @Nonnull
    public DialogueResolver getDialogueResolver() {
        return dialogueResolver;
    }

    @Nonnull
    public DialogueWorldView createDialogueWorldView(@Nonnull World world) {
        return new AetherhavenDialogueWorldView(world, this);
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
        ManagementBlock.register(this.getChunkStoreRegistry());
        CharterBlock.register(this.getChunkStoreRegistry());

        VillagerNeeds.register(this.getEntityStoreRegistry());
        AetherhavenVillagerHandle.register(this.getEntityStoreRegistry());
        TownVillagerBinding.register(this.getEntityStoreRegistry());
        VillagerAutonomyState.register(this.getEntityStoreRegistry());
        this.getEntityStoreRegistry().registerSystem(new VillagerNeedsDecaySystem(this));
        this.getEntityStoreRegistry().registerSystem(new VillagerAutonomySystem(this));
        this.getEntityStoreRegistry().registerSystem(new CharterPlaceEventSystem(this));
        this.getEntityStoreRegistry().registerSystem(new InnPoolTickSystem(this));

        this.getEventRegistry()
            .registerGlobal(StartWorldEvent.class, e -> AetherhavenWorldRegistries.bootstrapWorld(e.getWorld(), this));
        this.getEventRegistry()
            .registerGlobal(AddWorldEvent.class, e -> AetherhavenWorldRegistries.bootstrapWorld(e.getWorld(), this));
        this.getEventRegistry().registerGlobal(RemoveWorldEvent.class, e -> AetherhavenWorldRegistries.unloadWorld(e.getWorld()));

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
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, PlotSignBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                Vector3i blockWorld = target.blockWorldPos();
                return new PlotConstructionPage(playerRef, blockRef, blockWorld, false);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            PlotConstructionPage.class,
            AetherhavenConstants.PAGE_PLOT_MANAGEMENT,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, ManagementBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                Vector3i blockWorld = target.blockWorldPos();
                return new PlotConstructionPage(playerRef, blockRef, blockWorld, true);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            VillagerNeedsOverviewPage.class,
            AetherhavenConstants.PAGE_VILLAGER_NEEDS,
            (ref, componentAccessor, playerRef, context) -> {
                BlockPosition targetBlock = context.getTargetBlock();
                if (targetBlock == null) {
                    return null;
                }
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                PlotConstructionBlockResolver.PlotConstructionTarget target =
                    PlotConstructionBlockResolver.resolveForPlotUi(world, targetBlock, ManagementBlock.getComponentType());
                if (target == null) {
                    return null;
                }
                Ref<ChunkStore> blockRef = target.blockRef();
                Store<ChunkStore> cs = blockRef.getStore();
                ManagementBlock mb = cs.getComponent(blockRef, ManagementBlock.getComponentType());
                if (mb == null || mb.getTownId().isBlank()) {
                    return null;
                }
                try {
                    UUID townUuid = UUID.fromString(mb.getTownId().trim());
                    return new VillagerNeedsOverviewPage(playerRef, townUuid);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            CharterTownPage.class,
            AetherhavenConstants.PAGE_CHARTER_TOWN,
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
                if (blockRef == null || blockRef.getStore().getComponent(blockRef, CharterBlock.getComponentType()) == null) {
                    return null;
                }
                return new CharterTownPage(playerRef, blockRef);
            }
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            this,
            PlotPlacementPage.class,
            AetherhavenConstants.PAGE_PLOT_PLACEMENT,
            PlotPlacementOpenHelper::tryOpen
        );
        OpenCustomUIInteraction.registerSimple(this, QuestJournalPage.class, AetherhavenConstants.PAGE_QUEST_JOURNAL, QuestJournalPage::new);
        OpenCustomUIInteraction.registerSimple(
            this,
            PlotSignAdminPage.class,
            AetherhavenConstants.PAGE_PLOT_SIGN_ADMIN,
            PlotSignAdminPage::new
        );
        NPCPlugin npc = NPCPlugin.get();
        if (npc != null) {
            npc.registerCoreComponentType("OpenAetherhavenDialogue", BuilderActionOpenAetherhavenDialogue::new);
            LOGGER.atInfo().log("Registered NPC action OpenAetherhavenDialogue");
        } else {
            LOGGER.atWarning().log("NPCPlugin not loaded; OpenAetherhavenDialogue action unavailable");
        }
        this.getCommandRegistry().registerCommand(new AetherhavenCommand());
        LOGGER.atInfo().log("Aetherhaven v%s loaded", this.getManifest().getVersion().toString());
    }

    @Override
    protected void start() {
        this.config.get();
        this.constructionCatalog = ConstructionCatalog.loadFromClasspath(this.getClassLoader());
        this.dialogueCatalog = DialogueCatalog.loadFromClasspath(this.getClassLoader());
        this.dialogueResolver.registerKind("merchant", "aetherhaven_merchant");
        this.dialogueResolver.registerKind("blacksmith", "aetherhaven_blacksmith");
        this.dialogueResolver.registerKind("farmer", "aetherhaven_farmer");
        LOGGER.atInfo().log("Aetherhaven constructions loaded: %s", this.constructionCatalog.ids());
        LOGGER.atInfo().log("Aetherhaven dialogues loaded: %s", this.dialogueCatalog.all().keySet());
    }

    @Override
    protected void shutdown() {
        instance = null;
        AetherhavenWorldRegistries.saveAll();
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
