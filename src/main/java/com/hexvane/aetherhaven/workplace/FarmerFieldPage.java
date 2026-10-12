package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.*;
import com.hexvane.aetherhaven.autonomy.FieldWorkService;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.production.FarmFieldWorld.Ground;
import com.hexvane.aetherhaven.production.FarmFieldWorld.Spot;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.ui.ProductionStoragePage;
import com.hexvane.aetherhaven.warehouse.ProductionStorageLocator;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
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

/**
 * One screen for a farm's Scarecrow Fields: a map of the field the player is looking at, the palette that paints its
 * crop plan, the Almanac, and the farm's stores. Opened from a Scarecrow or from the farm's own storage. Opening it
 * never changes a field; the first painted cell does.
 */
public final class FarmerFieldPage extends AetherhavenInteractiveCustomUIPage<FarmerFieldPage.Data> {
    record Context(TownManager manager, TownRecord town, PlotInstance plot, PlotProductionState production,
                   ProductionCatalog.Entry entry, World world, Sight sight, boolean manage) {}
    /**
     * Everything the screen reads from the world and from the game's items. In the game the answers come from the
     * blocks ({@link WorldSight}); the picture tests give a field written out by hand and run the same screen code.
     */
    interface Sight {
        java.time.LocalDateTime now();
        Spot inspect(TownRecord town, PlotInstance farm, boolean home, Point anchor, FarmFields.Bounds bounds, int x, int z);
        boolean ripe(Point cell, NativeFarmCrops.Crop crop);
        double growth(Point cell, NativeFarmCrops.Crop crop);
        FarmFieldWork.View view(TownRecord town, FarmFieldsState.Cell cell);
        boolean scarecrowAt(Point block);
        boolean itemExists(String item);
        /** The item's own name where the game has one, otherwise its id. */
        Message itemName(String item);
    }
    record WorldSight(World world) implements Sight {
        @Override public java.time.LocalDateTime now() { return FarmFieldWorld.dateTime(world); }
        @Override public Spot inspect(TownRecord town, PlotInstance farm, boolean home, Point anchor, FarmFields.Bounds bounds, int x, int z) {
            return FarmFieldWorld.inspect(world, town, farm, home, anchor, bounds, x, z);
        }
        @Override public boolean ripe(Point cell, NativeFarmCrops.Crop crop) { return FarmFieldWorld.ripe(world, cell, crop); }
        @Override public double growth(Point cell, NativeFarmCrops.Crop crop) { return FarmFieldWorld.growth(world, cell, crop); }
        @Override public FarmFieldWork.View view(TownRecord town, FarmFieldsState.Cell cell) { return FarmFieldWorld.view(world, town, cell); }
        @Override public boolean scarecrowAt(Point block) {
            var type = ChunkSectionBlockUtil.blockType(world, block.x(), block.y(), block.z());
            return type != null && FarmFields.is(type.getDefaultStateKey() != null ? type.getDefaultStateKey() : type.getId());
        }
        @Override public boolean itemExists(String item) { return Item.getAssetMap().getAsset(item) != null; }
        @Override public Message itemName(String item) {
            var asset = Item.getAssetMap().getAsset(item);
            return asset != null && asset.getTranslationKey() != null && !asset.getTranslationKey().isBlank()
                ? Message.translation(asset.getTranslationKey()) : Message.raw(item);
        }
    }
    /** The Scarecrow the player used, while it has not claimed a field yet. */
    private static final int PENDING = -2;
    /** The brush that clears a cell's plan. */
    private static final String BARE = "";
    private static final String BARE_ICON = "Soil_Dirt_Tilled";
    /** Room for the map inside its frame, and the largest a cell is drawn. */
    private static final int MAP_WIDTH = 440, MAP_HEIGHT = 428, MAP_LEFT = 8, MAP_TOP = 8, MAX_CELL = 48;
    private static final int MAX_STOCK = 5;
    private final UUID townId, plotId;
    /** The Scarecrow the player used, when the page was opened from one. */
    private final Point scarecrow;
    private int slot = Integer.MIN_VALUE;
    private String brush;
    private boolean appended, almanac;
    private Message hint;
    /** What is laid out on the map right now, so a click only resends the cells that changed. */
    private String layout;
    private FarmFields.Bounds bounds;
    private Point anchor;
    private List<Spot> spots = List.of();
    private String[] drawn = new String[0];
    private int paletteDrawn = -1;

    public FarmerFieldPage(PlayerRef player, UUID town, UUID plot, Point scarecrow) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        townId = town; plotId = plot; this.scarecrow = scarecrow;
    }

    private static Message t(String key) { return Message.translation(FarmFields.LANG + key); }
    private static Message name(Context c, String item) { return c.sight().itemName(item); }
    private static Message lines(Message... parts) {
        var all = new ArrayList<Message>();
        for (var part : parts) { if (part == null) continue; if (!all.isEmpty()) all.add(Message.raw("\n")); all.add(part); }
        return Message.join(all.toArray(Message[]::new));
    }

    /**
     * The farm a Scarecrow belongs to: the one whose field it already is, else the farm whose yard it stands in,
     * else the first farm with a Scarecrow to spare.
     */
    public static PlotInstance farmFor(AetherhavenPlugin plugin, TownRecord town, Point block) {
        PlotInstance yard = null, spare = null, any = null;
        for (var plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.COMPLETE || !plugin.getConstructionCatalog().matchesGameplayConstruction(
                plot.getConstructionId(), FarmFields.CONSTRUCTION)) continue;
            var production = FarmFields.production(town, plot);
            if (production.farm().fieldAt(block) >= 0) return plot;
            if (yard == null && inYard(plot, block)) yard = plot;
            if (any == null) any = plot;
            if (spare == null && production.farm().freeSlot(FarmUpgrades.scarecrows(production)) >= 0) spare = plot;
        }
        return yard != null ? yard : spare != null ? spare : any;
    }
    private static boolean inYard(PlotInstance plot, Point block) {
        var f = plot.toFootprint();
        return block.x() >= f.getMinX() && block.x() <= f.getMaxX() && block.z() >= f.getMinZ() && block.z() <= f.getMaxZ();
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
            || !plugin.getConstructionCatalog().matchesGameplayConstruction(plot.getConstructionId(), FarmFields.CONSTRUCTION)) return null;
        var production = FarmFields.production(town, plot);
        var entry = ProductionEffectiveCatalog.effective(plugin.getProductionCatalog(), plugin.getWorkplaceUnlockCatalog(), FarmFields.CONSTRUCTION, production);
        return entry == null || entry.catalogSize() <= 0 ? null
            : new Context(manager, town, plot, production, entry, world, new WorldSight(world), town.playerCanManageConstructions(pr.getUuid()));
    }

    private static int mastery(Context c) { return CoreVillagerMasteryService.level(c.town(), FarmFields.KIND); }
    private boolean scarecrowStands(Context c) { return scarecrow != null && c.sight().scarecrowAt(scarecrow); }
    /** Which field the screen shows: the Scarecrow the player used when it has or may have a field, else the farm's own. */
    private void resolveSlot(Context c) {
        var farm = c.production().farm();
        if (slot == Integer.MIN_VALUE) {
            int claimed = scarecrow == null ? -1 : farm.fieldAt(scarecrow);
            slot = claimed >= 0 ? claimed : scarecrow != null && !inYard(c.plot(), scarecrow) && scarecrowStands(c) ? PENDING : FarmFieldsState.HOME;
        }
        if (slot == PENDING && farm.fieldAt(scarecrow) >= 0) slot = farm.fieldAt(scarecrow);
        if (slot == PENDING && !scarecrowStands(c) || slot > FarmFieldsState.HOME && farm.field(slot) == null) slot = FarmFieldsState.HOME;
        if (slot == FarmFieldsState.HOME) { bounds = FarmFields.homeBounds(c.plot()); anchor = null; }
        else {
            anchor = slot == PENDING ? scarecrow : farm.field(slot).anchor();
            bounds = FarmFields.scarecrowBounds(anchor, FarmUpgrades.reach(c.production()));
        }
    }
    /** True when another field, of this farm or of another, already plans this column. */
    private boolean elsewhere(Context c, int x, int z) {
        for (var plot : c.town().getPlotInstances()) {
            var other = plot == c.plot() ? c.production() : c.town().peekPlotProduction(plot.getPlotId());
            if (other == null) continue;
            int owner = other.farm().owner(x, z);
            if (owner >= 0 && (plot != c.plot() || owner != slot)) return true;
        }
        return false;
    }
    private void scan(Context c) {
        var found = new ArrayList<Spot>(bounds.width() * bounds.depth());
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) for (int x = bounds.minX(); x <= bounds.maxX(); x++)
            found.add(c.sight().inspect(c.town(), c.plot(), slot == FarmFieldsState.HOME, anchor, bounds, x, z));
        spots = found;
    }
    private int index(int x, int z) { return (z - bounds.minZ()) * bounds.width() + (x - bounds.minX()); }
    private int xOf(int index) { return bounds.minX() + index % bounds.width(); }
    private int zOf(int index) { return bounds.minZ() + index / bounds.width(); }
    private boolean seedReady(Context c, String crop) { return FarmFieldJobs.seedReady(c.town(), c.plot(), c.production(), crop); }

    @Override public void build(Ref<EntityStore> ref, UICommandBuilder b, UIEventBuilder e, Store<EntityStore> store) {
        var c = current(ref, store); if (c == null) { close(); return; }
        draw(b, e, c);
    }

    /** The whole screen for one farm as it stands. Everything it needs to know about the world it asks {@link Sight}. */
    void draw(UICommandBuilder b, UIEventBuilder e, Context c) {
        var farm = c.production().farm();
        resolveSlot(c);
        if (!appended) {
            b.append("Aetherhaven/FarmerFieldPage.ui"); appended = true;
            for (String action : List.of("Almanac", "Fill", "Clear", "Rotate", "Adopt", "Copy", "Collect", "Refresh", "Seeds", "Storage", "Upgrades"))
                e.addEventBinding(CustomUIEventBindingType.Activating, "#" + action, EventData.of("Action", action), false);
            for (int i = 0; i < FarmFieldsState.MAX_FIELDS; i++)
                e.addEventBinding(CustomUIEventBindingType.Activating, "#Tab" + i, EventData.of("Action", "Field").append("Key", Integer.toString(i)), false);
            e.addEventBinding(CustomUIEventBindingType.Activating, "#TabNew", EventData.of("Action", "Field").append("Key", Integer.toString(PENDING)), false);
            b.set("#TitleText.TextSpans", t("ui.title")); b.set("#PaletteTitle.TextSpans", t("ui.palette"));
            b.set("#Legend.TextSpans", t("ui.legend")); b.set("#StoresTitle.TextSpans", t("ui.stores"));
            b.set("#StockEmpty.TextSpans", t("ui.stores.empty")); b.set("#Collect.TextSpans", t("ui.collect"));
            b.set("#Fill.TextSpans", t("ui.fill")); b.set("#Clear.TextSpans", t("ui.clear")); b.set("#Rotate.TextSpans", t("ui.rotate"));
            b.set("#Adopt.TextSpans", t("ui.adopt")); b.set("#Copy.TextSpans", t("ui.copy"));
            b.set("#Refresh.TextSpans", t("ui.refresh")); b.set("#Seeds.TextSpans", t("ui.seeds")); b.set("#Storage.TextSpans", t("ui.storage"));
            b.set("#Upgrades.TextSpans", t("ui.upgrades")); b.set("#FavouredTitle.TextSpans", t("ui.favoured"));
            b.set("#AlmanacTitle.TextSpans", t("ui.almanac.title")); b.set("#AlmanacIntro.TextSpans", t("ui.almanac.intro"));
            b.set("#Tab0.TextSpans", t("ui.tab.home")); b.set("#TabNew.TextSpans", t("ui.tab.new"));
            for (int i = 1; i < FarmFieldsState.MAX_FIELDS; i++) b.set("#Tab" + i + ".TextSpans", t("ui.tab.scarecrow").param("number", i));
            b.set("#Fill.TooltipTextSpans", t("ui.tip.fill")); b.set("#Clear.TooltipTextSpans", t("ui.tip.clear"));
            b.set("#Rotate.TooltipTextSpans", t("ui.tip.rotate")); b.set("#Adopt.TooltipTextSpans", t("ui.tip.adopt"));
            b.set("#Copy.TooltipTextSpans", t("ui.tip.copy")); b.set("#Seeds.TooltipTextSpans", t("ui.tip.seeds"));
        }
        var when = c.sight().now(); long week = FarmAlmanac.week(when);
        var crops = FarmFields.cropOrder(); var growable = FarmFields.growable(c.town(), c.entry());
        var favoured = FarmAlmanac.favoured(week, growable, FarmUpgrades.favouredCrops(c.production()));
        if (brush == null || !BARE.equals(brush) && !crops.contains(brush)) brush = growable.isEmpty() ? BARE : growable.getFirst();
        int allowed = FarmUpgrades.scarecrows(c.production());
        b.set("#Summary.TextSpans", t("ui.summary").param("used", farm.fields().size() + (farm.fields().containsKey(FarmFieldsState.HOME) ? 0 : 1))
            .param("allowed", allowed + 1).param("cells", farm.plannedCount()));
        b.set("#Mastery.TextSpans", t("ui.mastery").param("level", mastery(c)));
        for (int i = 0; i < FarmFieldsState.MAX_FIELDS; i++) {
            b.set("#Tab" + i + ".Visible", i == FarmFieldsState.HOME || farm.field(i) != null);
            b.set("#Tab" + i + ".Disabled", slot == i);
        }
        boolean pending = scarecrow != null && farm.fieldAt(scarecrow) < 0 && !inYard(c.plot(), scarecrow) && scarecrowStands(c);
        b.set("#TabNew.Visible", pending); b.set("#TabNew.Disabled", slot == PENDING);
        b.set("#Almanac.TextSpans", t(almanac ? "ui.almanac.close" : "ui.almanac.open"));
        b.set("#PlanPanel.Visible", !almanac); b.set("#AlmanacPanel.Visible", almanac);
        scan(c);
        boolean blocked = slot == PENDING && farm.freeSlot(allowed) < 0;
        drawMap(b, e, c, favoured, blocked);
        drawPalette(b, e, c, crops, favoured);
        drawFavoured(b, c, when, week, favoured);
        drawAlmanac(b, c, when, week, growable);
        drawStock(b, e, c);
        boolean found = false, others = false;
        for (int i = 0; i < spots.size(); i++) {
            var spot = spots.get(i);
            if (spot.ok() && spot.crop() != null && growable.contains(spot.crop().output()) && slot >= 0
                && (farm.cell(slot, xOf(i), zOf(i)) == null || !farm.cell(slot, xOf(i), zOf(i)).planned()) && !elsewhere(c, xOf(i), zOf(i))) found = true;
        }
        for (var claimed : farm.fields().keySet()) if (claimed != slot && claimed > FarmFieldsState.HOME) others = true;
        boolean planned = slot >= 0 && !farm.planned(slot).isEmpty();
        b.set("#Adopt.Visible", found); b.set("#Copy.Visible", others && planned && slot > FarmFieldsState.HOME);
        b.set("#MoreTools.Visible", found || others && planned && slot > FarmFieldsState.HOME);
        for (String tool : List.of("Fill", "Clear", "Rotate", "Adopt", "Copy", "Seeds")) b.set("#" + tool + ".Disabled", !c.manage() || blocked);
        b.set("#SoilNote.TextSpans", soilNote(c));
        b.set("#SeedsNote.TextSpans", farm.carrying() ? t("ui.basket").param("count", farm.carried()) : Message.raw(""));
        b.set("#Status.TextSpans", status(c, growable, blocked));
    }

    // The map ---------------------------------------------------------------------------------------------------

    private void drawMap(UICommandBuilder b, UIEventBuilder e, Context c, List<String> favoured, boolean blocked) {
        var farm = c.production().farm();
        int size = Math.clamp(Math.min(MAP_WIDTH / bounds.width(), MAP_HEIGHT / bounds.depth()), 14, MAX_CELL);
        String key = slot + ":" + bounds + ":" + size;
        if (!key.equals(layout)) {
            b.clear("#Map"); layout = key; drawn = new String[spots.size()];
            int left = MAP_LEFT + (MAP_WIDTH - size * bounds.width()) / 2, top = MAP_TOP + (MAP_HEIGHT - size * bounds.depth()) / 2;
            for (int i = 0; i < spots.size(); i++) {
                b.append("#Map", "Aetherhaven/FarmFieldCell.ui"); String path = "#Map[" + i + "]";
                var at = new Anchor(); at.setLeft(Value.of(left + (i % bounds.width()) * size)); at.setTop(Value.of(top + (i / bounds.width()) * size));
                at.setWidth(Value.of(size)); at.setHeight(Value.of(size));
                b.setObject(path + ".Anchor", at);
                var key3 = EventData.of("Action", "Cell").append("Key", Integer.toString(i));
                e.addEventBinding(CustomUIEventBindingType.Activating, path, key3, false);
                e.addEventBinding(CustomUIEventBindingType.RightClicking, path, EventData.of("Action", "Erase").append("Key", Integer.toString(i)), false);
                e.addEventBinding(CustomUIEventBindingType.DoubleClicking, path, EventData.of("Action", "Bed").append("Key", Integer.toString(i)), false);
            }
        }
        boolean none = true;
        for (int i = 0; i < spots.size(); i++) {
            var spot = spots.get(i); int x = xOf(i), z = zOf(i); String path = "#Map[" + i + "]";
            none &= !spot.ok();
            var cell = slot >= 0 ? farm.cell(slot, x, z) : null;
            boolean scare = spot.ground() == Ground.SCARECROW, taken = spot.ok() && elsewhere(c, x, z), usable = spot.ok() && !taken;
            int soil = cell == null ? FarmFieldsState.GOOD : cell.soil();
            String plan = usable && cell != null && cell.planned() ? cell.crop() : null;
            String standing = usable && spot.crop() != null ? spot.crop().output() : null;
            boolean ripe = plan != null && standing != null && c.sight().ripe(new Point(x, spot.y(), z), spot.crop());
            boolean need = plan != null && standing == null && FarmFields.cropIssue(c.town(), c.entry(), plan) == null && !seedReady(c, plan);
            int grown = standing == null ? 0 : (int) Math.floor(c.sight().growth(new Point(x, spot.y(), z), spot.crop()) * 3);
            String state = spot.ground() + "|" + taken + "|" + soil + "|" + plan + "|" + standing + "|" + ripe + "|" + need + "|" + grown
                + "|" + (plan != null && favoured.contains(plan)) + "|" + (blocked || !c.manage());
            if (state.equals(drawn[i])) continue;
            // A cell that was just laid out is as its document draws it, so only what differs from that is sent.
            boolean fresh = drawn[i] == null;
            drawn[i] = state;
            String icon = scare ? FarmFields.BLOCK : plan != null ? plan : standing;
            show(b, path + " #Off", !usable && !scare, true, fresh);
            show(b, path + " #Worn", usable && soil == FarmFieldsState.WORN, false, fresh);
            show(b, path + " #Good", usable && soil == FarmFieldsState.GOOD || scare, false, fresh);
            show(b, path + " #Rich", usable && soil == FarmFieldsState.RICH, false, fresh);
            show(b, path + " #PipWorn", usable && soil == FarmFieldsState.WORN, false, fresh);
            show(b, path + " #PipRich", usable && soil == FarmFieldsState.RICH, false, fresh);
            show(b, path + " #Icon", icon != null, false, fresh); if (icon != null) b.set(path + " #Icon.ItemId", icon);
            // Dimmed while the crop is not standing as planned yet, and for a crop that grows outside the plan.
            show(b, path + " #Waiting", plan != null ? !plan.equals(standing) : standing != null, false, fresh);
            show(b, path + " #Ripe", ripe, false, fresh); show(b, path + " #Need", need && !ripe, false, fresh);
            b.set(path + ".TooltipTextSpans", tip(c, spot, taken, soil, plan, standing, ripe, need, grown, favoured));
        }
        b.set("#MapNote.Visible", none || blocked);
        if (none || blocked) b.set("#MapNote.TextSpans", blocked ? t("ui.map.full").param("allowed", FarmUpgrades.scarecrows(c.production())) : t("ui.map.none"));
    }

    private static void show(UICommandBuilder b, String element, boolean visible, boolean drawnVisible, boolean fresh) {
        if (!fresh || visible != drawnVisible) b.set(element + ".Visible", visible);
    }

    private Message tip(Context c, Spot spot, boolean taken, int soil, String plan, String standing, boolean ripe, boolean need, int grown, List<String> favoured) {
        if (spot.ground() == Ground.SCARECROW) return t("cell.scarecrow");
        if (taken) return t("cell.off.elsewhere");
        if (!spot.ok()) return t("cell.off." + switch (spot.ground()) {
            case FAR -> "far"; case OUTSIDE_TOWN -> "outside"; case BUILDING -> "building"; case FARMYARD -> "farmyard";
            case WORKPLACE -> "workplace"; case BLOCKED -> "blocked"; default -> "notSoil";
        });
        var soilLine = t("soil." + (soil == FarmFieldsState.RICH ? "rich" : soil == FarmFieldsState.WORN ? "worn" : "good"))
            .param("times", FarmFieldRules.get().soil().wearEvery());
        if (plan == null) return standing == null ? lines(t("cell.free"), soilLine) : lines(name(c, standing), t("cell.found"), soilLine);
        Message state;
        if (standing != null && !standing.equals(plan)) state = t("cell.state.changing").param("old", name(c, standing));
        else if (ripe) state = t("cell.state.ripe");
        else if (standing != null) state = t("cell.state.growing" + Math.clamp(grown, 0, 2));
        else if (FarmFields.cropIssue(c.town(), c.entry(), plan) != null) state = t("cell.state.locked");
        else if (need) state = t("cell.state.needSeed").param("seed", name(c, NativeFarmCrops.seedFor(plan)));
        else state = t(spot.tilled() ? "cell.state.waiting" : "cell.state.waitingTill");
        return lines(name(c, plan), state, favoured.contains(plan) ? t("cell.favoured") : null, soilLine);
    }

    // The palette, the Almanac and the stores --------------------------------------------------------------------

    private void drawPalette(UICommandBuilder b, UIEventBuilder e, Context c, List<String> crops, List<String> favoured) {
        var farm = c.production().farm();
        if (paletteDrawn != crops.size()) {
            b.clear("#Palette"); paletteDrawn = crops.size();
            for (int i = 0; i <= crops.size(); i++) {
                b.append("#Palette", "Aetherhaven/FarmFieldCrop.ui");
                e.addEventBinding(CustomUIEventBindingType.Activating, "#Palette[" + i + "]",
                    EventData.of("Action", "Brush").append("Key", i < crops.size() ? crops.get(i) : BARE), false);
            }
        }
        for (int i = 0; i < crops.size(); i++) {
            String crop = crops.get(i), seed = NativeFarmCrops.seedFor(crop), path = "#Palette[" + i + "]";
            String issue = FarmFields.cropIssue(c.town(), c.entry(), crop);
            b.set(path + " #Icon.ItemId", crop); b.set(path + " #Selected.Visible", crop.equals(brush));
            b.set(path + " #Lock.Visible", issue != null); b.set(path + " #Dim.Visible", issue != null);
            b.set(path + " #Star.Visible", issue == null && favoured.contains(crop));
            b.set(path + " #SeedCount.Text", issue != null ? "" : Integer.toString(farm.seeds(seed)));
            b.set(path + ".TooltipTextSpans", issue == null
                ? lines(name(c, crop), t("palette.seeds").param("count", farm.seeds(seed)).param("cost", FarmFields.seedCost(seed)),
                    favoured.contains(crop) ? t("cell.favoured") : null)
                : lines(name(c, crop), "mastery".equals(issue) ? t("palette.mastery").param("level", FarmFields.mastery(crop)) : t("palette.locked")));
        }
        String bare = "#Palette[" + crops.size() + "]";
        b.set(bare + " #Icon.ItemId", BARE_ICON); b.set(bare + " #Selected.Visible", BARE.equals(brush));
        b.set(bare + " #SeedCount.Text", ""); b.set(bare + ".TooltipTextSpans", t("palette.bare"));
        b.set("#BrushName.TextSpans", BARE.equals(brush) ? t("ui.brush.bare") : t("ui.brush").param("crop", name(c, brush)));
    }

    private static Message daysLeft(java.time.LocalDateTime when) {
        int days = FarmAlmanac.daysLeft(when);
        return days <= 1 ? t("ui.lastDay") : t("ui.daysLeft").param("days", days);
    }
    private static Message weekName(FarmAlmanac.Week week) {
        return t("ui.week").param("season", week.season().displayName()).param("week", week.weekOfSeason());
    }
    private void drawFavoured(UICommandBuilder b, Context c, java.time.LocalDateTime when, long week, List<String> favoured) {
        b.set("#FavouredWhen.TextSpans", lines(weekName(FarmAlmanac.describe(week)), daysLeft(when)));
        b.clear("#Favoured");
        for (int i = 0; i < favoured.size(); i++) {
            b.append("#Favoured", "Aetherhaven/FarmAlmanacCrop.ui"); String path = "#Favoured[" + i + "]";
            b.set(path + " #Icon.ItemId", favoured.get(i)); b.set(path + " #Name.TextSpans", name(c, favoured.get(i)));
        }
    }
    private void drawAlmanac(UICommandBuilder b, Context c, java.time.LocalDateTime when, long week, List<String> growable) {
        b.clear("#Weeks");
        if (!almanac) return;
        int shown = FarmUpgrades.weeksShown(c.production()), count = FarmUpgrades.favouredCrops(c.production());
        for (int i = 0; i < shown; i++) {
            b.append("#Weeks", "Aetherhaven/FarmAlmanacWeek.ui"); String path = "#Weeks[" + i + "]";
            b.set(path + " #Now.Visible", i == 0);
            b.set(path + " #When.TextSpans", i == 0 ? t("ui.almanac.now") : i == 1 ? t("ui.almanac.next") : t("ui.almanac.later").param("weeks", i));
            b.set(path + " #Season.TextSpans", weekName(FarmAlmanac.describe(week + i)));
            b.set(path + " #Left.TextSpans", i == 0 ? daysLeft(when) : Message.raw(""));
            var crops = FarmAlmanac.favoured(week + i, growable, count);
            for (int k = 0; k < crops.size(); k++) {
                b.append(path + " #Crops", "Aetherhaven/FarmAlmanacCrop.ui"); String crop = path + " #Crops[" + k + "]";
                b.set(crop + " #Icon.ItemId", crops.get(k)); b.set(crop + " #Name.TextSpans", name(c, crops.get(k)));
            }
        }
    }

    private void drawStock(UICommandBuilder b, UIEventBuilder e, Context c) {
        b.clear("#Stock"); int shown = 0; boolean any = false;
        var stored = new ArrayList<>(c.production().getAmounts().entrySet());
        stored.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        for (var row : stored) {
            if (row.getValue() <= 0 || !c.sight().itemExists(row.getKey())) continue;
            any |= row.getValue() > c.town().getWarehouse().reservedProduction(plotId, row.getKey());
            if (shown >= MAX_STOCK) continue;
            b.append("#Stock", "Aetherhaven/GroveStock.ui"); String path = "#Stock[" + (shown++) + "] #Cell";
            b.set(path + " #Icon.ItemId", row.getKey()); b.set(path + " #Quantity.Text", Long.toString(row.getValue()));
            b.set(path + ".TooltipTextSpans", t("ui.stock.tip").param("name", name(c, row.getKey())).param("count", row.getValue())
                .param("max", FarmUpgrades.capacity(c.production(), c.entry(), row.getKey())));
            e.addEventBinding(CustomUIEventBindingType.Activating, path, EventData.of("Action", "Take").append("Key", row.getKey()), false);
        }
        b.set("#Stock.Visible", shown > 0); b.set("#StockEmpty.Visible", shown == 0);
        b.set("#Collect.Disabled", !any);
    }

    private static Message soilNote(Context c) {
        var farm = c.production().farm(); int each = FarmUpgrades.cropsPerCompost(c.production());
        boolean enriches = FarmUpgrades.compostEnriches(c.production());
        var target = farm.compostTarget(enriches);
        if (farm.compost() < 1) return t("ui.compost.none").param("each", each).param("have", farm.compostProgress());
        return t(target == null ? enriches ? "ui.compost.idle" : "ui.compost.noWorn" : target.cell().soil() == FarmFieldsState.WORN ? "ui.compost.worn" : "ui.compost.good")
            .param("count", farm.compost());
    }

    private Message status(Context c, List<String> growable, boolean blocked) {
        if (hint != null) return hint;
        var farm = c.production().farm();
        if (blocked) return t("status.full").param("allowed", FarmUpgrades.scarecrows(c.production()));
        if (!c.production().usesFarmFields()) return t(slot == PENDING ? "status.newField" : "status.automatic");
        var missing = FarmFieldWork.missingSeeds(farm, (field, cell) -> c.sight().view(c.town(), cell), growable::contains,
            NativeFarmCrops::seedFor, crop -> FarmFields.seedCost(NativeFarmCrops.seedFor(crop)), FarmFieldJobs.spareEssence(c.town(), c.plot(), c.production()));
        if (!missing.isEmpty()) {
            var first = missing.entrySet().iterator().next(); String seed = NativeFarmCrops.seedFor(first.getKey());
            return t("status.needSeeds").param("count", first.getValue()).param("seed", name(c, seed)).param("cost", FarmFields.seedCost(seed));
        }
        var doing = FieldWorkService.doing(plotId);
        if (doing != null) return t("status.doing." + doing.name().toLowerCase(Locale.ROOT));
        if (farm.carrying()) return t("status.carrying").param("count", farm.carried());
        return t(farm.plannedCount() == 0 ? "status.empty" : "status.idle");
    }

    // Events ----------------------------------------------------------------------------------------------------

    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        var c = current(ref, store); if (c == null || data.action == null) { close(); return; }
        hint = null;
        resolveSlot(c); scan(c);
        boolean change = List.of("Cell", "Erase", "Bed", "Fill", "Clear", "Rotate", "Adopt", "Copy", "Seeds").contains(data.action);
        if (change && !c.manage()) hint = t("hint.permission");
        else switch (data.action) {
            case "Field" -> { try { int chosen = Integer.parseInt(data.key); if (chosen == PENDING || chosen >= 0 && chosen < FarmFieldsState.MAX_FIELDS) slot = chosen; } catch (NumberFormatException ignored) {} }
            case "Brush" -> { if (data.key != null) brush = data.key; }
            case "Almanac" -> almanac = !almanac;
            case "Cell" -> paint(c, parse(data.key), brush, false);
            case "Erase" -> paint(c, parse(data.key), BARE, false);
            case "Bed" -> paint(c, parse(data.key), brush, true);
            case "Fill" -> fill(c);
            case "Adopt" -> adopt(c);
            case "Clear" -> { if (slot >= 0 && c.production().farm().clear(slot)) { c.manager().updateTown(c.town()); hint = t("hint.cleared"); } }
            case "Rotate" -> {
                if (slot >= 0 && c.production().farm().rotate(slot)) { c.manager().updateTown(c.town()); hint = t("hint.rotated"); }
                else hint = t("hint.rotateNeedsTwo");
            }
            case "Copy" -> copy(c);
            case "Seeds" -> addSeeds(ref, store, c);
            case "Collect" -> collect(ref, store, c, null);
            case "Take" -> collect(ref, store, c, data.key);
            case "Storage" -> {
                var player = store.getComponent(ref, Player.getComponentType());
                var block = ProductionStorageLocator.find(c.world(), c.plot(), System.currentTimeMillis());
                if (player != null && block != null) {
                    player.getPageManager().openCustomPage(ref, store, new ProductionStoragePage(playerRef, townId, plotId, block.x(), block.y(), block.z()));
                    return;
                }
                hint = t("hint.noStorage");
            }
            case "Upgrades" -> {
                var player = store.getComponent(ref, Player.getComponentType());
                if (player != null) player.getPageManager().openCustomPage(ref, store, WorkplaceUpgradesPage.farm(playerRef, townId));
                return;
            }
            default -> {}
        }
        var b = new UICommandBuilder(); var e = new UIEventBuilder(); build(ref, b, e, store); sendUpdate(b, e, false);
    }
    private static int parse(String key) { try { return Integer.parseInt(key); } catch (NumberFormatException ignored) { return -1; } }

    /** A Scarecrow claims its field with the first cell painted there. */
    private boolean ensureField(Context c) {
        if (slot >= 0) return true;
        var farm = c.production().farm(); int allowed = FarmUpgrades.scarecrows(c.production()), free = farm.freeSlot(allowed);
        if (slot != PENDING || !scarecrowStands(c)) return false;
        if (free < 0) { hint = t("status.full").param("allowed", allowed); return false; }
        String issue = farm.claim(free, allowed, scarecrow);
        if (issue != null) { hint = t("status.full").param("allowed", allowed); return false; }
        slot = free; layout = null;
        return true;
    }
    private Message refuse(Spot spot, boolean taken) {
        if (taken) return t("cell.off.elsewhere");
        return t("cell.off." + switch (spot.ground()) {
            case FAR -> "far"; case OUTSIDE_TOWN -> "outside"; case BUILDING -> "building"; case FARMYARD -> "farmyard";
            case WORKPLACE -> "workplace"; case BLOCKED -> "blocked"; case SCARECROW -> "scarecrow"; default -> "notSoil";
        });
    }
    private Message cropRefusal(Context c, String crop) {
        String issue = FarmFields.cropIssue(c.town(), c.entry(), crop);
        return issue == null ? null : "mastery".equals(issue) ? t("palette.mastery").param("level", FarmFields.mastery(crop)) : t("palette.locked");
    }

    /** Paints one cell, or with {@code bed} every farmable cell joined to it. */
    private void paint(Context c, int index, String with, boolean bed) {
        if (index < 0 || index >= spots.size() || with == null) return;
        String crop = BARE.equals(with) ? null : with;
        if (crop != null && (hint = cropRefusal(c, crop)) != null) return;
        var spot = spots.get(index); boolean taken = spot.ok() && elsewhere(c, xOf(index), zOf(index));
        if (!spot.ok() || taken) { hint = refuse(spot, taken); return; }
        if (crop == null && slot < 0 || !ensureField(c)) return;
        var farm = c.production().farm(); int painted = 0;
        var queue = new ArrayDeque<Integer>(); var seen = new HashSet<Integer>(); queue.add(index); seen.add(index);
        while (!queue.isEmpty()) {
            int at = queue.poll(); var here = spots.get(at); int x = xOf(at), z = zOf(at);
            if (farm.paint(slot, x, here.y(), z, crop)) painted++;
            if (!bed) break;
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + step[0], nz = z + step[1];
                if (!bounds.contains(nx, nz)) continue;
                int next = index(nx, nz);
                if (seen.add(next) && spots.get(next).ok() && !elsewhere(c, nx, nz)) queue.add(next);
            }
        }
        if (painted > 0) c.manager().updateTown(c.town());
        if (bed) hint = t(crop == null ? "hint.bedCleared" : "hint.bedPainted").param("count", painted);
    }
    private void fill(Context c) {
        String crop = BARE.equals(brush) ? null : brush;
        if (crop == null) { hint = t("hint.pickCrop"); return; }
        if ((hint = cropRefusal(c, crop)) != null || !ensureField(c)) return;
        int painted = 0;
        for (int i = 0; i < spots.size(); i++)
            if (spots.get(i).ok() && !elsewhere(c, xOf(i), zOf(i)) && c.production().farm().paint(slot, xOf(i), spots.get(i).y(), zOf(i), crop)) painted++;
        if (painted > 0) c.manager().updateTown(c.town());
        hint = t("hint.filled").param("count", painted).param("crop", name(c, crop));
    }
    /** Takes the crops already growing in the field into the plan, each cell with the crop that stands in it. */
    private void adopt(Context c) {
        if (!ensureField(c)) return;
        var farm = c.production().farm(); int painted = 0;
        for (int i = 0; i < spots.size(); i++) {
            var spot = spots.get(i); int x = xOf(i), z = zOf(i);
            if (!spot.ok() || spot.crop() == null || elsewhere(c, x, z) || FarmFields.cropIssue(c.town(), c.entry(), spot.crop().output()) != null) continue;
            var cell = farm.cell(slot, x, z);
            if ((cell == null || !cell.planned()) && farm.paint(slot, x, spot.y(), z, spot.crop().output())) painted++;
        }
        if (painted > 0) c.manager().updateTown(c.town());
        hint = t("hint.adopted").param("count", painted);
    }
    /** Lays this field's plan over every other Scarecrow field, cell for cell from each Scarecrow outward. */
    private void copy(Context c) {
        var farm = c.production().farm();
        if (slot <= FarmFieldsState.HOME || anchor == null) return;
        int fields = 0, painted = 0;
        for (var claimed : new ArrayList<>(farm.fields().entrySet())) {
            int target = claimed.getKey(); var to = claimed.getValue().anchor();
            if (target == slot || target == FarmFieldsState.HOME || to == null) continue;
            var area = FarmFields.scarecrowBounds(to, FarmUpgrades.reach(c.production())); fields++;
            for (var cell : farm.planned(slot)) {
                int x = to.x() + cell.x() - anchor.x(), z = to.z() + cell.z() - anchor.z();
                var spot = c.sight().inspect(c.town(), c.plot(), false, to, area, x, z);
                int before = slot; slot = target; boolean taken = elsewhere(c, x, z); slot = before;
                if (spot.ok() && !taken && farm.paint(target, x, spot.y(), z, cell.crop())) painted++;
            }
        }
        if (painted > 0) c.manager().updateTown(c.town());
        hint = t("hint.copied").param("count", painted).param("fields", fields);
    }

    /** Moves every crop seed the player carries into the farm's seed store, up to what the store keeps of each. */
    private void addSeeds(Ref<EntityStore> ref, Store<EntityStore> store, Context c) {
        var inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (inv == null) return;
        var farm = c.production().farm(); int keep = FarmFieldRules.get().seeds().keep(), moved = 0;
        for (String crop : FarmFields.cropOrder()) {
            String seed = NativeFarmCrops.seedFor(crop);
            int room = keep - farm.seeds(seed), carried = InventoryMaterials.count(inv, seed), take = Math.min(room, carried);
            if (take < 1 || !inv.removeItemStack(new ItemStack(seed, take)).succeeded()) continue;
            moved += farm.addSeeds(seed, take, keep);
        }
        if (moved > 0) c.manager().updateTown(c.town());
        hint = moved > 0 ? t("hint.seedsAdded").param("count", moved) : t("hint.noSeeds");
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
