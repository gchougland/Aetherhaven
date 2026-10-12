package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.autonomy.GardenWorkService;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.ui.ProductionStoragePage;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** One screen for a Conservatory's Spirit Planters, opened from a planter or from the Conservatory's stores. */
public final class SpiritGardenPage extends AetherhavenInteractiveCustomUIPage<SpiritGardenPage.Data> {
    record Context(TownManager manager, TownRecord town, PlotInstance plot, PlotProductionState production,
                   ProductionCatalog.Entry entry, World world, boolean manage) {}
    private static final int MAX_STOCK = 10;
    /** Window heights without and with the seed picker, so the screen never shows an empty band. */
    private static final int HEIGHT = 624, HEIGHT_CHOOSING = 720;
    private final UUID townId, plotId;
    /** The Spirit Planter the player used, when the page was opened from one. */
    private final Point block;
    private int slot = Integer.MIN_VALUE;
    private boolean appended, confirmUproot;
    private Message hint;

    public SpiritGardenPage(PlayerRef player, UUID town, UUID plot, Point block) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        townId = town; plotId = plot; this.block = block;
    }

    private static Message t(String key) { return Message.translation(SpiritGarden.LANG + key); }
    private static Message name(String item) {
        var asset = Item.getAssetMap().getAsset(item);
        return asset != null && asset.getTranslationKey() != null && !asset.getTranslationKey().isBlank()
            ? Message.translation(asset.getTranslationKey()) : Message.raw(item);
    }

    /** The Conservatory that owns this planter, or else the first one with a planter to spare. */
    public static PlotInstance gardenFor(AetherhavenPlugin plugin, TownRecord town, Point block) {
        PlotInstance spare = null, any = null;
        for (var plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.COMPLETE || !plugin.getConstructionCatalog().matchesGameplayConstruction(
                plot.getConstructionId(), SpiritGarden.CONSTRUCTION)) continue;
            var production = SpiritGarden.production(town, plot);
            if (production.garden().planterAt(block) >= 0) return plot;
            if (any == null) any = plot;
            if (spare == null && production.garden().freeSlot(GardenUpgrades.planters(production)) >= 0) spare = plot;
        }
        return spare != null ? spare : any;
    }

    private Context current(Ref<EntityStore> ref, Store<EntityStore> store) {
        var plugin = AetherhavenPlugin.get(); var pr = store.getComponent(ref, PlayerRef.getComponentType());
        var pos = store.getComponent(ref, TransformComponent.getComponentType());
        if (plugin == null || pr == null || pos == null) return null;
        var world = store.getExternalData().getWorld();
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var town = manager.getTown(townId); var p = pos.getPosition();
        if (town == null || !town.hasMemberOrOwner(pr.getUuid())
            || !TownTerritoryClaims.contains(town, ChunkUtil.chunkCoordinate((int) Math.floor(p.x)), ChunkUtil.chunkCoordinate((int) Math.floor(p.z)))) return null;
        var plot = town.findPlotById(plotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE
            || !plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(), SpiritGarden.CONSTRUCTION)) return null;
        var production = SpiritGarden.production(town, plot);
        var entry = ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(), plugin.getWorkplaceUnlockCatalog(), SpiritGarden.CONSTRUCTION, production);
        return entry == null || entry.catalogSize() <= 0 ? null
            : new Context(manager, town, plot, production, entry, world, town.playerCanManageConstructions(pr.getUuid()));
    }

    /** True while the planter the player used still stands and has no seed yet. */
    private boolean pending(Context c) {
        return block != null && c.production().garden().planterAt(block) < 0 && SpiritGardenWorld.planterStands(c.world(), block);
    }
    private static int mastery(Context c) { return CoreVillagerMasteryService.level(c.town(), SpiritGarden.KIND); }

    /** What a planter is doing, as the short name its card and status text are filed under. */
    private static String state(Context c, int slot, long clock, long rest) {
        var garden = c.production().garden(); var planter = garden.planters().get(slot);
        var plant = SpiritGardenRules.get().plant(planter.plant());
        if (plant == null || SpiritGardenWorld.planterMissing(c.world(), planter.block())) return "missing";
        if (mastery(c) < plant.mastery()) return "training";
        if (!SpiritGardenWorld.planterStands(c.world(), planter.block())) return "far";
        if (planter.stage() == SpiritGardenState.WAITING)
            return slot >= GardenUpgrades.planters(c.production()) ? "beyond" : SpiritGardenWorld.cellFree(c.world(), planter.cell()) ? "waiting" : "blocked";
        if (planter.ripe()) return GardenWorkService.hasRoom(c.production(), c.entry(), plant) ? "ripe" : "full";
        return garden.rested(slot, clock, rest) ? "rite" : "resting";
    }

    @Override public void build(Ref<EntityStore> ref, UICommandBuilder b, UIEventBuilder e, Store<EntityStore> store) {
        var c = current(ref, store); if (c == null) { close(); return; }
        var garden = c.production().garden(); var rules = SpiritGardenRules.get();
        int allowed = GardenUpgrades.planters(c.production());
        boolean pending = pending(c); int free = garden.freeSlot(allowed);
        if (slot == Integer.MIN_VALUE) slot = block != null ? garden.planterAt(block) : garden.planters().keySet().stream().findFirst().orElse(-1);
        if (slot >= 0 && !garden.planters().containsKey(slot)) slot = -1;
        if (!appended) {
            b.append("Aetherhaven/SpiritGardenPage.ui"); appended = true;
            for (int i = 0; i < SpiritGardenState.MAX_PLANTERS; i++) {
                b.append("#Planters", "Aetherhaven/SpiritGardenPlanter.ui");
                e.addEventBinding(CustomUIEventBindingType.Activating, "#Planters[" + i + "]", EventData.of("Action", "Planter").append("Key", Integer.toString(i)), false);
            }
            for (String action : List.of("Bless", "Uproot", "Refresh", "Collect", "Upgrades"))
                e.addEventBinding(CustomUIEventBindingType.Activating, "#" + action, EventData.of("Action", action), false);
            b.set("#TitleText.TextSpans", t("ui.title")); b.set("#Intro.TextSpans", t("ui.intro"));
            b.set("#StoresTitle.TextSpans", t("ui.stores")); b.set("#StockEmpty.TextSpans", t("ui.stores.empty"));
            b.set("#Collect.TextSpans", t("ui.collect")); b.set("#Refresh.TextSpans", t("ui.refresh")); b.set("#Upgrades.TextSpans", t("ui.upgrades"));
        }
        long clock = SpiritGardenWorld.now(c.world()), rest = GardenUpgrades.restSeconds(c.production());
        b.set("#Summary.TextSpans", t("ui.summary").param("used", garden.planters().size()).param("allowed", allowed));
        b.set("#Mastery.TextSpans", t("ui.mastery").param("level", mastery(c)));
        for (int i = 0; i < SpiritGardenState.MAX_PLANTERS; i++) {
            String path = "#Planters[" + i + "]"; var bound = garden.planters().get(i);
            boolean locked = i >= allowed && bound == null, fresh = pending && i == free;
            String card = locked ? "locked" : bound != null ? state(c, i, clock, rest) : fresh ? "new" : "empty";
            b.set(path + ".Disabled", locked || bound == null && !fresh);
            b.set(path + " #Selected.Visible", bound != null ? i == slot : fresh && slot < 0);
            b.set(path + " #LockedShade.Visible", locked); b.set(path + " #Lock.Visible", locked);
            b.set(path + " #Icon.Visible", !locked && (bound != null || fresh));
            b.set(path + " #Icon.ItemId", bound != null ? bound.plant() : SpiritGarden.BLOCK);
            b.set(path + " #Number.TextSpans", t("ui.planter").param("number", i + 1));
            b.set(path + " #Name.TextSpans", bound != null ? name(bound.plant()) : Message.raw(""));
            b.set(path + " #Bar.Visible", bound != null); b.set(path + " #Bar.Value", bound == null ? 0f : (float) garden.growth(i, clock, rest));
            b.set(path + " #Status.TextSpans", t("ui.card." + card));
            b.set(path + ".TooltipTextSpans", t("ui.tip." + (locked ? "locked" : bound != null ? "bound" : fresh ? "new" : "empty")));
        }
        var planter = slot >= 0 ? garden.planters().get(slot) : null;
        boolean choosing = pending && planter == null && free >= 0;
        b.set("#Growth.Visible", planter != null); b.set("#Growth.Value", planter == null ? 0f : (float) garden.growth(slot, clock, rest));
        if (planter != null) {
            b.set("#PlantIcon.ItemId", planter.plant());
            b.set("#PlanterTitle.TextSpans", t(garden.blessed(slot) ? "ui.planterBlessed" : "ui.planter").param("number", slot + 1));
            b.set("#PlantName.TextSpans", name(planter.plant()));
            b.set("#PlanterStatus.TextSpans", status(c, slot, clock, rest));
        } else {
            b.set("#PlantIcon.ItemId", SpiritGarden.BLOCK);
            b.set("#PlanterTitle.TextSpans", t(pending ? "ui.detail.new.title" : "ui.detail.none.title"));
            b.set("#PlantName.TextSpans", t(pending ? "ui.detail.new.name" : "ui.detail.none.name"));
            b.set("#PlanterStatus.TextSpans", !pending ? t("ui.detail.none.status")
                : free < 0 ? t("ui.detail.new.full").param("allowed", allowed) : t("ui.detail.new.status"));
        }
        drawSeeds(ref, store, b, e, c, choosing);
        var inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        var blessing = rules.blessing();
        boolean canBless = planter != null && c.manage() && !garden.blessed(slot) && inv != null && InventoryMaterials.count(inv, blessing.item()) >= blessing.cost();
        b.set("#DetailActions.Visible", planter != null);
        b.set("#Bless.Disabled", !canBless);
        b.set("#Bless.TextSpans", planter != null && garden.blessed(slot) ? t("ui.blessed") : t("ui.bless").param("cost", blessing.cost()));
        b.set("#Uproot.Disabled", !c.manage());
        b.set("#Uproot.TextSpans", t(confirmUproot ? "ui.uprootConfirm" : "ui.uproot"));
        drawStock(b, e, c);
        b.set("#GardenNote.TextSpans", hint != null ? hint : garden.carrying() ? t("ui.note.carrying").param("count", garden.carried())
            : garden.planters().isEmpty() ? t("ui.note.start") : t("ui.note.idle"));
        var window = new Anchor();
        window.setWidth(Value.of(920)); window.setHeight(Value.of(choosing ? HEIGHT_CHOOSING : HEIGHT));
        b.setObject("#GardenWindow.Anchor", window);
    }

    private static Message status(Context c, int slot, long clock, long rest) {
        var planter = c.production().garden().planters().get(slot);
        String state = state(c, slot, clock, rest);
        var plant = SpiritGardenRules.get().plant(planter.plant());
        if ("training".equals(state)) return t("ui.status.training").param("level", plant.mastery());
        if ("resting".equals(state)) {
            long left = Math.max(0, Math.min(rest, planter.restUntil() - clock));
            return left < 3600 ? t("ui.status.restingSoon") : t("ui.status.resting").param("hours", (int) Math.ceil(left / 3600.0));
        }
        return t("ui.status." + state);
    }

    /** All nine seeds, so a player sees what Rowan can grow and what still needs training. */
    private void drawSeeds(Ref<EntityStore> ref, Store<EntityStore> store, UICommandBuilder b, UIEventBuilder e, Context c, boolean choosing) {
        b.set("#SeedHint.Visible", choosing); b.set("#Seeds.Visible", choosing); b.clear("#Seeds");
        if (!choosing) return;
        var inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        var plants = SpiritGardenRules.get().plants(); boolean any = false;
        for (int i = 0; i < plants.size(); i++) {
            var plant = plants.get(i); String path = "#Seeds[" + i + "]"; b.append("#Seeds", "Aetherhaven/SpiritGardenSeed.ui");
            boolean locked = mastery(c) < plant.mastery(), carried = inv != null && InventoryMaterials.count(inv, plant.seed()) > 0;
            any |= carried && !locked;
            b.set(path + " #Icon.ItemId", plant.seed());
            b.set(path + ".Disabled", locked || !carried || !c.manage());
            b.set(path + " #Lock.Visible", locked); b.set(path + " #Dim.Visible", locked || !carried);
            b.set(path + ".TooltipTextSpans", locked ? t("ui.seed.tip.locked").param("name", name(plant.plant())).param("level", plant.mastery())
                : t(carried ? "ui.seed.tip.ready" : "ui.seed.tip.missing").param("name", name(plant.plant())));
            e.addEventBinding(CustomUIEventBindingType.Activating, path, EventData.of("Action", "Seed").append("Key", plant.seed()), false);
        }
        b.set("#SeedHint.TextSpans", t(any ? "ui.seed.hint" : "ui.seed.none"));
    }

    private void drawStock(UICommandBuilder b, UIEventBuilder e, Context c) {
        b.clear("#Stock"); int shown = 0; boolean any = false;
        var stored = new ArrayList<>(c.production().getAmounts().entrySet());
        stored.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        for (var row : stored) {
            if (row.getValue() <= 0 || Item.getAssetMap().getAsset(row.getKey()) == null) continue;
            any |= row.getValue() > c.town().getWarehouse().reservedProduction(plotId, row.getKey());
            if (shown >= MAX_STOCK) continue;
            b.append("#Stock", "Aetherhaven/GroveStock.ui"); String path = "#Stock[" + (shown++) + "] #Cell";
            b.set(path + " #Icon.ItemId", row.getKey()); b.set(path + " #Quantity.Text", Long.toString(row.getValue()));
            b.set(path + ".TooltipTextSpans", t("ui.stock.tip").param("name", name(row.getKey())).param("count", row.getValue())
                .param("max", GardenUpgrades.capacity(c.production(), c.entry(), row.getKey())));
            e.addEventBinding(CustomUIEventBindingType.Activating, path, EventData.of("Action", "Take").append("Key", row.getKey()), false);
        }
        b.set("#Stock.Visible", shown > 0); b.set("#StockEmpty.Visible", shown == 0);
        b.set("#Collect.Disabled", !any);
    }

    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        var c = current(ref, store); if (c == null || data.action == null) { close(); return; }
        var garden = c.production().garden();
        boolean confirming = confirmUproot; confirmUproot = false; hint = null;
        boolean change = List.of("Seed", "Bless", "Uproot").contains(data.action);
        if (change && !c.manage()) hint = t("hint.permission");
        else switch (data.action) {
            case "Planter" -> {
                try {
                    int chosen = Integer.parseInt(data.key);
                    if (garden.planters().containsKey(chosen)) slot = chosen;
                    else if (pending(c) && chosen == garden.freeSlot(GardenUpgrades.planters(c.production()))) slot = -1;
                } catch (NumberFormatException ignored) {}
            }
            case "Seed" -> bind(ref, store, c, data.key);
            case "Bless" -> bless(ref, store, c);
            case "Uproot" -> uproot(ref, store, c, confirming);
            case "Collect" -> collect(ref, store, c, null);
            case "Take" -> collect(ref, store, c, data.key);
            case "Upgrades" -> {
                var player = store.getComponent(ref, Player.getComponentType());
                if (player != null) player.getPageManager().openCustomPage(ref, store, WorkplaceUpgradesPage.garden(playerRef, townId));
                return;
            }
            default -> {}
        }
        var b = new UICommandBuilder(); var e = new UIEventBuilder(); build(ref, b, e, store); sendUpdate(b, e, false);
    }

    private void bind(Ref<EntityStore> ref, Store<EntityStore> store, Context c, String seed) {
        var garden = c.production().garden(); var plant = SpiritGardenRules.get().bySeed(seed);
        var inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (plant == null || inv == null || !pending(c)) { hint = t("issue.seed"); return; }
        int allowed = GardenUpgrades.planters(c.production()), free = garden.freeSlot(allowed);
        if (free < 0) { hint = t("issue.full"); return; }
        if (mastery(c) < plant.mastery()) { hint = t("issue.locked").param("level", plant.mastery()); return; }
        if (InventoryMaterials.count(inv, plant.seed()) < 1) { hint = t("issue.noSeed"); return; }
        if (!SpiritGardenWorld.cellFree(c.world(), new Point(block.x(), block.y() + 1, block.z()))
            || !SpiritGardenWorld.cellFree(c.world(), new Point(block.x(), block.y() + 2, block.z()))) { hint = t("issue.space"); return; }
        String issue = garden.bind(free, allowed, garden.revision(), block, plant.plant());
        if (issue != null) { hint = t("issue." + issue); return; }
        if (!inv.removeItemStack(new ItemStack(plant.seed(), 1)).succeeded()) { garden.uproot(free); hint = t("issue.payment"); return; }
        c.manager().updateTown(c.town()); slot = free;
        hint = t("hint.bound");
    }

    private void bless(Ref<EntityStore> ref, Store<EntityStore> store, Context c) {
        var garden = c.production().garden(); var blessing = SpiritGardenRules.get().blessing();
        var inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        String issue = garden.blessingIssue(slot);
        if (issue != null) { hint = t("issue." + issue); return; }
        if (inv == null || InventoryMaterials.count(inv, blessing.item()) < blessing.cost()) { hint = t("hint.needEssence").param("cost", blessing.cost()); return; }
        if (!inv.removeItemStack(new ItemStack(blessing.item(), blessing.cost())).succeeded()) { hint = t("issue.payment"); return; }
        garden.bless(slot); c.manager().updateTown(c.town());
        hint = t("hint.blessed");
    }

    private void uproot(Ref<EntityStore> ref, Store<EntityStore> store, Context c, boolean confirming) {
        var garden = c.production().garden(); var planter = garden.planters().get(slot);
        if (planter == null) return;
        boolean waiting = planter.stage() == SpiritGardenState.WAITING;
        if (!confirming) { confirmUproot = true; hint = t(waiting ? "hint.uprootAskWaiting" : "hint.uprootAsk"); return; }
        var plant = SpiritGardenRules.get().plant(planter.plant());
        if (!garden.uproot(slot)) return;
        if (plant != null) {
            SpiritGardenWorld.clear(c.world(), planter, plant);
            // A seed Rowan never planted goes back to the player.
            var inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
            if (waiting && inv != null) inv.addItemStack(new ItemStack(plant.seed(), 1));
        }
        c.manager().updateTown(c.town()); slot = Integer.MIN_VALUE;
        hint = t("hint.uprooted");
    }

    private void collect(Ref<EntityStore> ref, Store<EntityStore> store, Context c, String only) {
        var player = store.getComponent(ref, Player.getComponentType()); var pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) return;
        var result = new ProductionWithdrawal.ResultSink();
        for (var row : new LinkedHashMap<>(c.production().getAmounts()).entrySet()) {
            if (row.getValue() <= 0 || Item.getAssetMap().getAsset(row.getKey()) == null || only != null && !only.equals(row.getKey())) continue;
            long spare = Math.max(0, row.getValue() - c.town().getWarehouse().reservedProduction(plotId, row.getKey()));
            ProductionWithdrawal.withdrawToPlayer(ref, store, player, c.production(), c.entry(), row.getKey(), spare, result);
        }
        c.manager().updateTown(c.town());
        ProductionStoragePage.notifyWithdrawResult(pr, result);
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
            .append(new KeyedCodec<>("Key", Codec.STRING), (d, v) -> d.key = v, d -> d.key).add().build();
        String action, key;
    }
}
