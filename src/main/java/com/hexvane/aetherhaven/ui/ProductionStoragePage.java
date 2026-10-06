package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
import com.hexvane.aetherhaven.production.ProductionTimeScaling;
import com.hexvane.aetherhaven.production.ProductionWithdrawal;
import com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Withdraw items from per plot workplace production storage (wardrobe block). */
public final class ProductionStoragePage extends AetherhavenInteractiveCustomUIPage<ProductionStoragePage.PageData> {
    private static final long LIVE_REFRESH_INTERVAL_MS = 1000L;
    private static final int MAX_UI_SLOTS = WorkplaceProductionUpgrades.MAX_SLOT_COUNT;
    private static final String SLOT_FRAGMENT = "[0]";
    private static final int SLOT_WIDTH_PX = 180;
    private static final int SLOT_GAP_PX = 20;
    private static final int CONTENT_PADDING_PX = 32;
    private static final int BOTTOM_BUTTON_ROW_PX = 416;
    /** DecoratedContainer title, patch border, and macro padding beyond {@code #Content}. */
    private static final int CONTAINER_CHROME_PX = 148;

    private final UUID townId;
    private final UUID plotId;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private boolean templateAppended;
    private boolean slotFragmentsAppended;
    private boolean localizationApplied;
    private boolean liveRefreshStarted;
    private volatile boolean liveRefreshActive;

    public ProductionStoragePage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nonnull UUID plotId,
        int blockX,
        int blockY,
        int blockZ
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.plotId = plotId;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/ProductionStorage.ui");
            templateAppended = true;
        }
        if (!slotFragmentsAppended) {
            for (int i = 0; i < MAX_UI_SLOTS; i++) {
                commandBuilder.append(slotHost(i), "Aetherhaven/ProductionStorageSlot.ui");
            }
            slotFragmentsAppended = true;
        }
        if (!localizationApplied) {
            AetherhavenUiLocalization.applyProductionStorage(commandBuilder);
            localizationApplied = true;
        }
        commandBuilder.set("#ErrMsg.Visible", false);
        // Clear the previous view before checking permissions or a changed plot.
        for (String section : List.of("MasteryInfo", "SlotsRow", "BottomUnlockRow", "HerdRow", "FieldRow", "GroveRow"))
            commandBuilder.set("#" + section + ".Visible", false);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || uc == null || pr == null) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.plugin"));
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.permission"));
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        String gameplayConstructionId = plugin.getConstructionCatalog().resolveGameplayConstructionId(plot != null ? plot.getConstructionId() : "");
        if (plot == null
            || plot.getState() != PlotInstanceState.COMPLETE
            || !ProductionCatalog.isProductionWorkplaceConstruction(gameplayConstructionId)
            || !plot.containsWorldBlock(blockX, blockY, blockZ)) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.plot"));
            return;
        }
        PlotProductionState state = town.getOrCreatePlotProduction(plotId);
        if (!plot.getProductionLayout().legacy() && plot.getProductionLayout().validationError()!=null) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.raw("This building needs its physical production setup. Stored resources remain available."));
        }
        state.migrateIfNeeded();
        String masteryKind=com.hexvane.aetherhaven.mastery.CoreProductionRequirements.kind(gameplayConstructionId);
        String masteryTool=masteryKind==null?null:com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.toolId(town,masteryKind);
        commandBuilder.set("#MasteryInfo.Text",masteryKind==null?"":"Mastery "+
            com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService.level(town,masteryKind)+" / 10"+
            (masteryTool==null?"":" · "+masteryTool.replace("Tool_","").replace('_',' '))+" · Train through villager dialogue");

        ProductionCatalog.Entry entry =
            ProductionEffectiveCatalog.effective(
                plugin.getProductionCatalog(),
                plugin.getWorkplaceUnlockCatalog(),
                gameplayConstructionId,
                state
            );
        if (entry == null || entry.catalogSize() <= 0) {
            commandBuilder.set("#ErrMsg.Visible", true);
            commandBuilder.set("#ErrMsg.TextSpans", Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.catalog"));
            return;
        }

        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        int slotCount = WorkplaceProductionUpgrades.slotCount(state);
        commandBuilder.set("#MasteryInfo.Visible", true);
        commandBuilder.set("#SlotsRow.Visible", true);
        commandBuilder.set("#BottomUnlockRow.Visible", true);
        commandBuilder.set("#GroveRow.Visible",com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL.equals(gameplayConstructionId));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,"#OpenGrove",EventData.of("Action","OpenGrove"),false);
        commandBuilder.set("#FieldRow.Visible",com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_FARM.equals(gameplayConstructionId)||"plot_druid".equals(gameplayConstructionId));
        commandBuilder.set("#OpenField.Text","plot_druid".equals(gameplayConstructionId)?"Review ingredient beds":"Review planted beds");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,"#OpenField",EventData.of("Action","OpenField"),false);
        commandBuilder.set("#HerdRow.Visible",com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_BARN.equals(gameplayConstructionId));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,"#OpenHerd",EventData.of("Action","OpenHerd"),false);
        double speedMul = WorkplaceProductionUpgrades.speedMultiplier(state);
        applyShellWidth(commandBuilder, MAX_UI_SLOTS);

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#OpenUnlocks",
            new EventData().append("Action", "OpenUnlocks"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CollectAll",
            new EventData().append("Action", "CollectAll"),
            false
        );

        boolean canCollectAny = false;
        for (int col = 0; col < MAX_UI_SLOTS; col++) {
            String host = slotHost(col);
            boolean active = col < slotCount;
            commandBuilder.set(host + ".Visible", true);
            resetHostAnchor(commandBuilder, host, col, MAX_UI_SLOTS);
            String base = slotBase(col);
            commandBuilder.setObject(base + ".Background", new PatchStyle().setColor(Value.of(active ? "#1a2932" : "#2b3038")));
            commandBuilder.set(base + " #Unlocked.Visible", active);
            commandBuilder.set(base + " #Locked.Visible", !active);
            commandBuilder.set(base + " #LockedTitle.Text", "Output slot " + (col + 1));
            commandBuilder.set(base + " #Pick.Disabled", !active);
            for (String control : List.of("Take1", "Take10", "Take100"))
                commandBuilder.set(base + " #" + control + ".Disabled", true);
            if (!active) continue;
            commandBuilder.set(base + " #RequirementLock.Visible", false);
            String iconPath = base + " #Icon";
            int cursor = state.getSlotCursor(col);
            String itemId = entry.itemAtCursor(cursor);
            if (itemId == null || itemId.isBlank()) {
                commandBuilder.set(iconPath + ".Visible", false);
                commandBuilder.set(base + " #Name.TextSpans", Message.raw("No output"));
                commandBuilder.set(base + " #Qty.TextSpans", Message.raw("0/0"));
                commandBuilder.set(base + " #Time.TextSpans", Message.raw(""));
                commandBuilder.set(base + " #Prog.Value", 0f);
                commandBuilder.set(base + " #Prog.Visible", false);
            } else {
                long lineCap = WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, itemId);
                Item it = Item.getAssetMap().getAsset(itemId);
                commandBuilder.set(iconPath + ".Visible", true);
                commandBuilder.set(iconPath + ".AssetPath", ItemAssetImagePath.forItem(it, itemId));
                Message nameMsg =
                    it != null && it.getTranslationKey() != null && !it.getTranslationKey().isBlank()
                        ? Message.translation(it.getTranslationKey())
                        : Message.raw(itemId);
                commandBuilder.set(base + " #Name.TextSpans", nameMsg);
                long have = state.getAmount(itemId);
                boolean canCollect = have > town.getWarehouse().reservedProduction(plotId, itemId);
                canCollectAny |= canCollect;
                for (String control : List.of("Take1", "Take10", "Take100"))
                    commandBuilder.set(base + " #" + control + ".Disabled", !canCollect);
                commandBuilder.set(base + " #Qty.TextSpans", Message.raw(have + "/" + lineCap));
                int ticks =
                    ProductionTimeScaling.effectiveTicksForItemAtPlot(
                        cfg,
                        entry.ticksAtCursor(cursor),
                        speedMul,
                        world,
                        plot.getSignX(),
                        plot.getSignZ(),
                        itemId
                    );
                boolean herd=state.usesPhysicalHerd();
                boolean field=state.usesPhysicalField();
                boolean grove=state.usesPhysicalTimber();
                boolean needsIngredientReview="plot_druid".equals(gameplayConstructionId) && !field;
                commandBuilder.set(base + " #Prog.Visible", !grove && !field && !herd && !needsIngredientReview);
                boolean physical=!grove && !field && !herd && !needsIngredientReview && !plot.getProductionLayout().legacy() && plot.getProductionLayout().validationError()==null;
                var miningCycle=physical?state.mining().cycle(col):null;
                float progress = grove || field || herd || needsIngredientReview ? 0f : physical ? (float)miningCycle.progress()
                    : ticks > 0 ? Math.min(1f, state.getSlotTickAccum(col) / (float) ticks) : 0f;
                commandBuilder.set(base + " #Prog.Value", progress);
                String requirement = com.hexvane.aetherhaven.mastery.CoreProductionRequirements.issue(town,gameplayConstructionId,itemId);
                commandBuilder.set(base + " #RequirementLock.Visible", requirement != null);
                commandBuilder.setObject(base + ".Background", new PatchStyle().setColor(Value.of(requirement == null ? "#1a2932" : "#2b3038")));
                commandBuilder.set(
                    base + " #Time.TextSpans",
                    requirement != null ? Message.raw(requirement)
                    : needsIngredientReview ? Message.raw("Review ingredient beds to begin")
                    : herd ? Message.raw(herdStatus(state,itemId,lineCap))
                    : field ? Message.raw(fieldStatus(state,itemId,lineCap))
                    : grove ? Message.raw(groveStatus(state,col,itemId,lineCap))
                    : physical ? Message.raw(miningStatus(plugin,world,town,plot,state,itemId,lineCap,miningCycle))
                    : Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.genInterval")
                        .param("time", ProductionCatalog.Entry.formatSecondsForTicks(ticks))
                );
            }
            bindColEvents(eventBuilder, col);
        }
        if (state.hasExcavationOutput() || state.usesPhysicalTimber() || state.usesPhysicalField() || state.usesPhysicalHerd())
            canCollectAny |= state.getAmounts().entrySet().stream().anyMatch(stored ->
                stored.getValue() > town.getWarehouse().reservedProduction(plotId, stored.getKey())
                    && Item.getAssetMap().getAsset(stored.getKey()) != null);
        commandBuilder.set("#CollectAll.Disabled", !canCollectAny);
        startLiveRefreshIfNeeded(store);
    }

    private static String herdStatus(PlotProductionState state,String item,long capacity) {
        if(state.getAmount(item)>=capacity)return "Storage full";
        var cargo=state.herd().cargo();
        if(cargo!=null&&cargo.output().equals(item))return cargo.quantity()+" being carried to storage";
        return state.herd().feed()>0?"Feeding and raising livestock":"Supply wheat at herd review";
    }

    private static String fieldStatus(PlotProductionState state,String item,long capacity) {
        if(state.getAmount(item)>=capacity)return "Storage full";
        var cargo=state.field().cargo();
        if(cargo!=null&&cargo.output().equals(item))return cargo.quantity()+" being carried to storage";
        long beds=state.field().beds().stream().filter(bed->bed.output().equals(item)).count();
        return beds==0?"Plant and review crop beds":beds+" beds in the field";
    }

    private static String groveStatus(PlotProductionState state,int slot,String item,long capacity) {
        var timber=state.timber();
        if(state.getAmount(item)>=capacity)return "Storage full";
        var cargo=timber.cargo();
        if(cargo!=null&&cargo.output().equals(item))return cargo.remaining()+" items awaiting delivery";
        if(com.hexvane.aetherhaven.production.NativeTimberProducts.supported(item))return "Collected with tree harvests";
        var tree=timber.trees().get(slot);
        if(tree!=null) {
            if(timber.edited(tree.tree().id()))return "Review changed tree";
            return timber.chopping(slot)>0?"Chopping in progress":"Growing or awaiting Logger";
        }
        var plan=timber.groves().get(slot);
        if(plan==null)return "Plan a tree location";
        if(!plan.output().equals(item))return "Review changed timber";
        return "Waiting for free planting";
    }

    private static String miningStatus(AetherhavenPlugin plugin, World world, TownRecord town, PlotInstance plot,
                                       PlotProductionState state, String item, long capacity,
                                       com.hexvane.aetherhaven.production.MiningCycle cycle) {
        long yield=com.hexvane.aetherhaven.town.CharterSpecializationModifiers.productionAmountPerCycle(
            town,plugin.getConstructionCatalog(),com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT);
        if (capacity-state.getAmount(item)<yield) return "Storage full";
        if (!com.hexvane.aetherhaven.production.MiningNodeBlocks.supported(item)) return "Mining appearance missing";
        boolean intact=false;
        for (int n=0;n<plot.getProductionLayout().nodes().size();n++) {
            if (com.hexvane.aetherhaven.production.MiningNodeBlocks.intact(world,plot,state.mining(),n)) { intact=true; break; }
        }
        if (!intact) return "Check mining cells";
        if (cycle.remaining()>0) return "Vein returns in "+(int)Math.ceil(cycle.remaining())+"s";
        return cycle.progress()>0 ? "Mining progress" : "Waiting for the Miner";
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
        liveRefreshActive = false;
    }

    private static String slotHost(int col) {
        return "#SlotsRow #Slot" + col + "Host";
    }

    private static String slotBase(int col) {
        return slotHost(col) + SLOT_FRAGMENT;
    }

    private static void applyShellWidth(@Nonnull UICommandBuilder commandBuilder, int slotCount) {
        int slots = Math.max(1, Math.min(MAX_UI_SLOTS, slotCount));
        int slotsWidth = slots * SLOT_WIDTH_PX + Math.max(0, slots - 1) * SLOT_GAP_PX;
        int inner = Math.max(slotsWidth, BOTTOM_BUTTON_ROW_PX);
        int shellWidth = inner + CONTENT_PADDING_PX + CONTAINER_CHROME_PX;
        Anchor shell = new Anchor();
        shell.setWidth(Value.of(shellWidth));
        commandBuilder.setObject("#ProductionShell.Anchor", shell);
        Anchor row = new Anchor();
        row.setWidth(Value.of(slotsWidth));
        commandBuilder.setObject("#SlotsRow.Anchor", row);
    }

    private static void resetHostAnchor(@Nonnull UICommandBuilder commandBuilder, @Nonnull String host, int col, int slotCount) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(SLOT_WIDTH_PX));
        if (col < slotCount - 1) {
            anchor.setRight(Value.of(SLOT_GAP_PX));
        }
        commandBuilder.setObject(host + ".Anchor", anchor);
    }

    private void startLiveRefreshIfNeeded(@Nonnull Store<EntityStore> store) {
        if (liveRefreshStarted) {
            return;
        }
        liveRefreshStarted = true;
        liveRefreshActive = true;
        scheduleLiveRefreshTick(store.getExternalData().getWorld());
    }

    private void scheduleLiveRefreshTick(@Nonnull World world) {
        if (!liveRefreshActive) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            liveRefreshActive = false;
            return;
        }
        plugin.scheduleOnWorld(
            world,
            () -> {
                if (!liveRefreshActive) {
                    return;
                }
                Ref<EntityStore> r = playerRef.getReference();
                if (r == null || !r.isValid()) {
                    liveRefreshActive = false;
                    return;
                }
                Store<EntityStore> st = r.getStore();
                Player pl = st.getComponent(r, Player.getComponentType());
                if (pl == null || pl.getPageManager().getCustomPage() != this) {
                    liveRefreshActive = false;
                    return;
                }
                refresh(r, st);
                scheduleLiveRefreshTick(world);
            },
            LIVE_REFRESH_INTERVAL_MS
        );
    }

    private static void bindColEvents(@Nonnull UIEventBuilder eventBuilder, int col) {
        String base = slotBase(col);
        String c = String.valueOf(col);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            base + " #Pick",
            new EventData().append("Action", "PickMaterial").append("Slot", c),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            base + " #Take1",
            new EventData().append("Action", "Take").append("Slot", c).append("Amount", "1"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            base + " #Take10",
            new EventData().append("Action", "Take").append("Slot", c).append("Amount", "10"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            base + " #Take100",
            new EventData().append("Action", "Take").append("Slot", c).append("Amount", "100"),
            false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        String action = data.action;
        if (action == null || action.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (plugin == null || player == null || pr == null || uc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !town.playerCanManageConstructions(uc.getUuid())) {
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        String gameplayConstructionId = plugin.getConstructionCatalog().resolveGameplayConstructionId(plot != null ? plot.getConstructionId() : "");
        if (plot == null
            || plot.getState() != PlotInstanceState.COMPLETE
            || !ProductionCatalog.isProductionWorkplaceConstruction(gameplayConstructionId)
            || !plot.containsWorldBlock(blockX, blockY, blockZ)) {
            return;
        }
        PlotProductionState state = town.getOrCreatePlotProduction(plotId);
        state.migrateIfNeeded();
        ProductionCatalog.Entry entry =
            ProductionEffectiveCatalog.effective(
                plugin.getProductionCatalog(),
                plugin.getWorkplaceUnlockCatalog(),
                gameplayConstructionId,
                state
            );
        if (entry == null || entry.catalogSize() <= 0) {
            return;
        }

        if (action.equalsIgnoreCase("OpenHerd")
                && com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_BARN.equals(gameplayConstructionId)) {
            liveRefreshActive = false;
            player.getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.RancherHerdPage(playerRef,townId,plotId));
            return;
        }
        if (action.equalsIgnoreCase("OpenField")
                && (com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_FARM.equals(gameplayConstructionId)||"plot_druid".equals(gameplayConstructionId))) {
            liveRefreshActive = false;
            player.getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.FarmerFieldPage(playerRef,townId,plotId));
            return;
        }
        if (action.equalsIgnoreCase("OpenGrove")
                && com.hexvane.aetherhaven.AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL.equals(gameplayConstructionId)) {
            liveRefreshActive = false;
            player.getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.TimberGrovePage(playerRef,townId,plotId));
            return;
        }
        if (action.equalsIgnoreCase("OpenUnlocks")) {
            liveRefreshActive = false;
            player.getPageManager().openCustomPage(ref, store, new ProductionStorageUnlocksPage(playerRef, townId, plotId, blockX, blockY, blockZ));
            return;
        }

        if (action.equalsIgnoreCase("PickMaterial")) {
            int slot = parseSlot(data.slot);
            if (slot < 0 || slot >= WorkplaceProductionUpgrades.slotCount(state)) {
                return;
            }
            liveRefreshActive = false;
            player.getPageManager().openCustomPage(
                ref,
                store,
                new ProductionMaterialPickerPage(playerRef, townId, plotId, slot, blockX, blockY, blockZ)
            );
            return;
        }

        if (action.equalsIgnoreCase("CollectAll")) {
            collectAll(ref, store, player, pr, tm, town, state, entry);
            return;
        }

        if (!action.equalsIgnoreCase("Take")) {
            return;
        }
        int slot = parseSlot(data.slot);
        if (slot < 0 || slot >= WorkplaceProductionUpgrades.slotCount(state)) {
            return;
        }
        int want;
        try {
            want = Integer.parseInt(data.amount != null ? data.amount.trim() : "0");
        } catch (NumberFormatException e) {
            return;
        }
        if (want <= 0) {
            return;
        }
        withdrawFromSlot(ref, store, player, pr, tm, town, state, entry, slot, want);
    }

    private void collectAll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef pr,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotProductionState state,
        @Nonnull ProductionCatalog.Entry entry
    ) {
        int slotCount = WorkplaceProductionUpgrades.slotCount(state);
        Map<String, Long> toTake = new LinkedHashMap<>();
        for (int slot = 0; slot < slotCount; slot++) {
            String itemId = entry.itemAtCursor(state.getSlotCursor(slot));
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            long have = state.getAmount(itemId);
            if (have <= 0L) {
                continue;
            }
            toTake.merge(itemId, have, Long::sum);
        }
        // An adopted grove cannot select legacy byproducts again. Keep those
        // saved supplies collectible locally even before a warehouse is built.
        if (state.hasExcavationOutput() || state.usesPhysicalTimber() || state.usesPhysicalField() || state.usesPhysicalHerd()) for (var stored : state.getAmounts().entrySet()) {
            if (stored.getValue() > 0 && Item.getAssetMap().getAsset(stored.getKey()) != null)
                toTake.put(stored.getKey(), stored.getValue());
        }
        if (toTake.isEmpty()) {
            refresh(ref, store);
            return;
        }
        ProductionWithdrawal.ResultSink result = new ProductionWithdrawal.ResultSink();
        for (var row : toTake.entrySet()) {
            ProductionWithdrawal.withdrawToPlayer(ref, store, player, state, entry, row.getKey(), Math.min(row.getValue(),Math.max(0,state.getAmount(row.getKey())-town.getWarehouse().reservedProduction(plotId,row.getKey()))), result);
        }
        tm.updateTown(town);
        notifyWithdrawResult(pr, result);
        refresh(ref, store);
    }

    private void withdrawFromSlot(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef pr,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotProductionState state,
        @Nonnull ProductionCatalog.Entry entry,
        int slot,
        int want
    ) {
        String itemId = entry.itemAtCursor(state.getSlotCursor(slot));
        if (itemId == null || itemId.isBlank()) {
            refresh(ref, store);
            return;
        }
        ProductionWithdrawal.ResultSink result = new ProductionWithdrawal.ResultSink();
        ProductionWithdrawal.withdrawToPlayer(ref, store, player, state, entry, itemId, Math.min(want,Math.max(0,state.getAmount(itemId)-town.getWarehouse().reservedProduction(plotId,itemId))), result);
        tm.updateTown(town);
        notifyWithdrawResult(pr, result);
        refresh(ref, store);
    }

    private static void notifyWithdrawResult(@Nonnull PlayerRef pr, @Nonnull ProductionWithdrawal.ResultSink result) {
        if (result.needEmptyBuckets) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.needEmptyBucket")
                    .param("need", String.valueOf(result.emptyBucketsRequired))
                    .param("have", String.valueOf(result.emptyBucketsHeld)),
                NotificationStyle.Warning
            );
            return;
        }
        if (result.inventoryFull) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.inventoryFull"),
                NotificationStyle.Warning
            );
            return;
        }
        if (result.inventoryPartial) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_feasts_production.aetherhaven.ui.production.err.inventoryPartial"),
                NotificationStyle.Warning
            );
        }
    }

    private static int parseSlot(@Nullable String slotStr) {
        if (slotStr == null || slotStr.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(slotStr.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("Slot", Codec.STRING), (d, v) -> d.slot = v, d -> d.slot)
                .add()
                .append(new KeyedCodec<>("Amount", Codec.STRING), (d, v) -> d.amount = v, d -> d.amount)
                .add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String slot;
        @Nullable
        private String amount;
    }
}
