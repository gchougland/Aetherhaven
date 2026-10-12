package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkRoster;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** The one screen of a grower site: what it grows, its beds and its store. Shared by the Mycologist and Berry Patch. */
public final class GrowerSitePage extends AetherhavenInteractiveCustomUIPage<GrowerSitePage.Data> {
    /** Page id the Mushroom Log's Use interaction names. */
    public static final String BED_PAGE = "AetherhavenGrowerBed";
    static final int WINDOW_WIDTH = 940, PICKER_HEIGHT = 764, PLAIN_HEIGHT = 614;

    private final UUID townId, siteId;
    private final org.joml.Vector3i station;
    private boolean appended, refreshing;
    private View rendered;
    private Note note;
    private String pendingCrop;

    /** A line of text by lang key, kept as data so two views can be compared. */
    record Note(String key, Map<String, String> params) {
        Note(String key) { this(key, Map.of()); }
        Message message() {
            var message = t(key);
            for (var param : params.entrySet()) message = message.param(param.getKey(), param.getValue());
            return message;
        }
    }
    record CropView(String id, String item, int skill, int yield, String use, boolean locked, boolean selected) {}
    record BedView(String item, int percent, String status) {}
    record StockView(String item, long count, long reserved) {}
    record View(String type, String name, String cropItem, int cropYield, String cropUse, Note status, String care, long picks,
                boolean picker, List<CropView> crops, List<BedView> beds, int found, int maxBeds, List<StockView> stock,
                int stored, int capacity, Note note, int tends, boolean readOnly) {}

    public GrowerSitePage(PlayerRef player, UUID town, UUID site, org.joml.Vector3i station) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        townId = town; siteId = site;
        this.station = station == null ? null : new org.joml.Vector3i(station);
    }

    static Message t(String key) { return Message.translation("aetherhaven_growers.aetherhaven.growers." + key); }

    private static Message itemName(String itemId) {
        var item = Item.getAssetMap().getAsset(itemId);
        return item == null ? Message.raw(itemId.replace('_', ' ')) : Message.translation(item.getTranslationKey());
    }

    /** Using a Mushroom Log opens the Mycologist it stands in. */
    public static void register(AetherhavenPlugin plugin) {
        com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction.registerCustomPageSupplier(
            plugin, GrowerSitePage.class, BED_PAGE, (ref, accessor, player, context) -> {
                var pos = context.getTargetBlock();
                if (pos == null) return null;
                var world = ref.getStore().getExternalData().getWorld();
                var town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).findTownContainingBlock(world.getName(), pos.x, pos.z);
                ApprenticeWorkplace found = null;
                if (town != null) for (var site : town.getApprenticeWorkplaces().sites()) {
                    var plan = GrowerSiteService.plan(site.type());
                    var block = com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world, pos.x, pos.y, pos.z);
                    if (plan != null && site.area().contains(pos.x, pos.y, pos.z) && plan.isBed(GrowerSiteService.baseId(block))) found = site;
                }
                if (found == null) { player.sendMessage(t("logOutside")); return null; }
                if (!town.hasMemberOrOwner(player.getUuid()) || !town.playerCanManageConstructions(player.getUuid())) {
                    player.sendMessage(t("logNoAccess")); return null;
                }
                return new GrowerSitePage(player, town.getTownId(), found.id(), null);
            });
    }

    @Override public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        // What the client receives whole. The refresh below is compared with it and sends only what moved.
        if (fill(ref, c, e, store)) opened(c, e);
    }

    /** Draws what changed since the last draw. False when the page had to close instead. */
    private boolean fill(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return false;
        var world = store.getExternalData().getWorld();
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var town = manager.getTown(townId);
        if (town == null || !town.hasMemberOrOwner(playerRef.getUuid())) { close(); return false; }
        var site = town.getApprenticeWorkplaces().find(siteId);
        var plan = site == null ? null : GrowerSiteService.plan(site.type());
        if (plan == null) { close(); return false; }
        if (!appended) {
            c.append("Aetherhaven/GrowerSitePage.ui"); appended = true;
            c.set("#Collect.TextSpans", t("collect")); c.set("#Back.TextSpans", t("settings")); c.set("#Refresh.TextSpans", t("refresh"));
            c.set("#StoreTitle.TextSpans", t("storeTitle")); c.set("#StockEmpty.TextSpans", t("storeEmpty"));
            for (String action : new String[]{"Back", "Refresh", "Collect"})
                e.addEventBinding(CustomUIEventBindingType.Activating, "#" + action, EventData.of("Action", action), false);
        }
        var view = view(world, store, town, manager, plugin, site, plan);
        if (!view.equals(rendered)) { draw(c, e, view); rendered = view; }
        if (!refreshing) { refreshing = true; scheduleRefresh(world); }
        return true;
    }

    private View view(World world, Store<EntityStore> store, TownRecord town, TownManager manager, AetherhavenPlugin plugin,
                      ApprenticeWorkplace site, GrowerPlan plan) {
        var habitat = GrowerSiteService.sync(world, site);
        var grower = site.grower();
        var crop = grower.chosen(plan);
        var roster = TownsfolkWorkRoster.list(town, manager, store, plugin);
        int bestSkill = roster.stream().filter(w -> w.alive()).mapToInt(w -> w.progress().skill(site.type().skill)).max().orElse(0);
        var able = roster.stream().filter(w -> w.progress().canPerform(site.type(), w.guard(), town.getPayroll().overdue(w.characterId()), w.alive())).toList();
        int ableSkill = able.stream().mapToInt(w -> w.progress().skill(site.type().skill)).max().orElse(0);
        boolean paused = crop == null || grower.full(plan, crop);

        Note status;
        if (habitat == null || !habitat.simulated()) status = new Note("issue." + (habitat == null ? "plan" : habitat.issue()));
        else if ("bench".equals(habitat.issue())) status = new Note("issue.bench");
        else if (habitat.beds().isEmpty()) status = new Note(site.type().id + ".noBeds");
        else if (crop == null) status = new Note("status.choose");
        else if (grower.full(plan, crop)) status = new Note("status.full");
        else if (able.isEmpty()) status = new Note("status.noWorker");
        else if (ableSkill < crop.skill()) status = new Note("status.lowSkill", Map.of("skill", Integer.toString(crop.skill())));
        else status = new Note("status.workers", Map.of("count", Integer.toString(able.size())));

        var crops = new ArrayList<CropView>();
        boolean picker = plan.onlyCrop() == null;
        if (picker) for (var option : plan.crops())
            crops.add(new CropView(option.id(), option.item(), option.skill(), option.yield(), option.use(),
                option.skill() > bestSkill, option == crop));

        var beds = new ArrayList<BedView>();
        boolean readable = habitat != null && habitat.simulated();
        for (var bed : grower.beds()) {
            boolean ripe = readable && !plan.logBeds() && GrowerSiteService.ripe(world, plan, bed.point());
            var job = crop == null ? null : grower.job(plan, bed, ripe);
            String state = paused && (job != null || bed.stage() == 0) ? "paused" : job == GrowerSite.Job.SEED ? "seed" : job == GrowerSite.Job.TEND ? "tend"
                : job == GrowerSite.Job.PICK ? "pick" : bed.stage() == 0 ? "bare" : bed.stage() > plan.tends() ? "ripening" : "growing";
            String icon = bed.stage() == 0 || crop == null ? plan.bedBlocks().getFirst()
                : plan.logBeds() || job == GrowerSite.Job.PICK ? crop.item() : plan.bedBlocks().getFirst();
            // Growth is shown in steps of five in a hundred. A bed that crept by ones would redraw the page, and
            // cost a press, every time it was looked at.
            beds.add(new BedView(icon, (int) Math.round(100 * com.hexvane.aetherhaven.ui.PageDelta.stepped(grower.growth(plan, bed, ripe), 20)), state));
        }

        var stock = new ArrayList<StockView>();
        for (var held : grower.amounts().entrySet())
            stock.add(new StockView(held.getKey(), held.getValue(), town.getWarehouse().reservedProduction(site.id(), held.getKey())));

        return new View(site.type().id, site.name(), crop == null ? plan.bedBlocks().getFirst() : crop.item(), crop == null ? 0 : crop.yield(),
            crop == null ? null : crop.use(), status, grower.lastCare(), grower.picks(), picker, crops, beds,
            habitat == null ? 0 : habitat.found(), plan.maxBeds(), stock, grower.stored(), plan.storeCapacity(),
            note, plan.tends(), !town.playerCanManageConstructions(playerRef.getUuid()));
    }

    private static void window(UICommandBuilder c, int height) {
        var anchor = new Anchor(); anchor.setWidth(Value.of(WINDOW_WIDTH)); anchor.setHeight(Value.of(height));
        c.setObject("#GrowerWindow.Anchor", anchor);
    }

    private void draw(UICommandBuilder c, UIEventBuilder e, View v) {
        c.set("#GrowerTitle.TextSpans", t(v.type() + ".title"));
        c.set("#SiteName.Text", v.name());
        c.set("#CropIcon.ItemId", v.cropItem());
        c.set("#CropName.TextSpans", v.cropUse() == null ? t("noCrop") : t(v.type() + ".growing").param("crop", itemName(v.cropItem())));
        c.set("#CropUse.TextSpans", v.cropUse() == null ? Message.raw("") : t("yield").param("count", v.cropYield()).param("use", t("use." + v.cropUse())));
        c.set("#SiteStatus.TextSpans", v.status().message());
        c.set("#Care.TextSpans", t("care." + (v.care() == null ? "none" : v.care())));
        c.set("#Picks.TextSpans", t("picks").param("count", Long.toString(v.picks())));

        window(c, v.picker() ? PICKER_HEIGHT : PLAIN_HEIGHT);
        c.set("#Picker.Visible", v.picker()); c.set("#About.Visible", !v.picker());
        if (v.picker()) {
            c.set("#ChooseTitle.TextSpans", t(v.type() + ".chooseTitle"));
            c.clear("#Crops");
            for (int i = 0; i < v.crops().size(); i++) {
                var crop = v.crops().get(i);
                c.append("#Crops", "Aetherhaven/GrowerCropCard.ui");
                String card = "#Crops[" + i + "]";
                var yield = t("yield").param("count", crop.yield()).param("use", t("use." + crop.use()));
                c.set(card + " #Icon.ItemId", crop.item());
                c.set(card + " #Name.TextSpans", itemName(crop.item()));
                c.set(card + " #Need.TextSpans", t("need").param("skill", crop.skill()));
                c.set(card + " #Need.Style.TextColor", crop.locked() ? "#c98f7c" : crop.selected() ? "#96d4b4" : "#dec68d");
                c.set(card + " #Name.Style.TextColor", crop.locked() ? "#98a3ae" : "#ffffff");
                c.set(card + " #Lock.Visible", crop.locked());
                c.set(card + " #LockedShade.Visible", crop.locked());
                c.set(card + " #Selected.Visible", crop.selected());
                c.set(card + ".Disabled", crop.locked() || v.readOnly());
                c.set(card + ".TooltipTextSpans", t(crop.locked() ? "cardLocked" : crop.selected() ? "cardGrowing" : "cardTooltip")
                    .param("name", itemName(crop.item())).param("skill", crop.skill()).param("yield", yield));
                e.addEventBinding(CustomUIEventBindingType.Activating, card, new EventData().append("Action", "Choose").append("Crop", crop.id()), false);
            }
        } else c.set("#AboutText.TextSpans", t(v.type() + ".about").param("tends", v.tends()));

        c.set("#BedsTitle.TextSpans", t(v.type() + ".bedsTitle"));
        int extra = Math.max(0, v.found() - v.beds().size());
        c.set("#BedCount.TextSpans", t(v.type() + (extra > 0 ? ".extraBeds" : ".bedCount"))
            .param("used", v.beds().size()).param("max", v.maxBeds()).param("extra", extra));
        c.clear("#Beds");
        for (int i = 0; i < v.beds().size(); i++) {
            var bed = v.beds().get(i);
            c.append("#Beds", "Aetherhaven/GrowerBedCard.ui");
            String card = "#Beds[" + i + "]";
            c.set(card + " #Icon.ItemId", bed.item());
            c.set(card + " #Growth.Value", bed.percent() / 100f);
            c.set(card + " #Status.TextSpans", t("bed." + bed.status()));
            c.set(card + " #Status.Style.TextColor", switch (bed.status()) {
                case "pick" -> "#96d4b4"; case "seed", "tend" -> "#dec68d"; case "paused" -> "#c98f7c"; default -> "#b7c9bb"; });
            c.set(card + ".TooltipTextSpans", t("bed.tooltip").param("number", i + 1).param("status", t("bed." + bed.status())).param("percent", bed.percent()));
        }
        c.set("#Beds.Visible", !v.beds().isEmpty());
        c.set("#BedsEmptyPanel.Visible", v.beds().isEmpty());
        c.set("#BedsEmpty.TextSpans", t(v.type() + ".noBeds"));

        c.set("#StoreCount.TextSpans", t("storeCount").param("stored", v.stored()).param("capacity", v.capacity()));
        c.clear("#Stock");
        boolean any = false;
        for (int i = 0; i < v.stock().size(); i++) {
            var held = v.stock().get(i);
            long free = Math.max(0, held.count() - held.reserved());
            any |= free > 0;
            c.append("#Stock", "Aetherhaven/GroveStock.ui");
            String cell = "#Stock[" + i + "] #Cell";
            c.set(cell + " #Icon.ItemId", held.item());
            c.set(cell + " #Quantity.Text", Long.toString(held.count()));
            c.set(cell + ".Disabled", free <= 0);
            c.set(cell + ".TooltipTextSpans", t(held.reserved() > 0 ? "stockReserved" : "stockTooltip").param("name", itemName(held.item()))
                .param("count", Long.toString(held.count())).param("reserved", Long.toString(held.reserved())));
            e.addEventBinding(CustomUIEventBindingType.Activating, cell, new EventData().append("Action", "Take").append("Crop", held.item()), false);
        }
        c.set("#Stock.Visible", !v.stock().isEmpty());
        c.set("#StockEmpty.Visible", v.stock().isEmpty());
        c.set("#Collect.Disabled", !any);
        c.set("#Note.TextSpans", v.note() != null ? v.note().message()
            : v.picker() ? t(v.type() + ".about").param("tends", v.tends()) : t("note.intro"));
        c.set("#Back.Disabled", v.readOnly());
    }

    private void scheduleRefresh(World world) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        plugin.scheduleOnWorld(world, () -> {
            if (isDismissed()) return;
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid() || ref.getStore().getExternalData().getWorld() != world) return;
            var store = ref.getStore(); var player = store.getComponent(ref, Player.getComponentType());
            if (player == null || player.getPageManager().getCustomPage() != this) return;
            // Looked at once a second; drawn only when the view differs, and then only the values that differ
            // are sent. The rows the player already has are kept.
            var c = new UICommandBuilder(); var e = new UIEventBuilder();
            if (fill(ref, c, e, store)) sendChanges(c, e);
            if (!isDismissed()) scheduleRefresh(world);
        }, 1000);
    }

    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var world = store.getExternalData().getWorld();
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var town = manager.getTown(townId);
        if (town == null || !town.hasMemberOrOwner(playerRef.getUuid())) { close(); return; }
        var site = town.getApprenticeWorkplaces().find(siteId);
        var plan = site == null ? null : GrowerSiteService.plan(site.type());
        if (plan == null) { close(); return; }
        boolean manage = town.playerCanManageConstructions(playerRef.getUuid());
        var action = data.action == null ? "" : data.action;
        if (!"Choose".equals(action)) pendingCrop = null;
        switch (action) {
            case "Back" -> {
                if (manage) {
                    store.getComponent(ref, Player.getComponentType()).getPageManager()
                        .openCustomPage(ref, store, new WorkplacePage(playerRef, townId, siteId, site.area()));
                    return;
                }
                note = new Note("note.noAccess");
            }
            case "Choose" -> choose(world, store, town, manager, plugin, site, plan, data.crop, manage);
            case "Collect" -> collect(ref, store, town, manager, site, null);
            case "Take" -> collect(ref, store, town, manager, site, data.crop);
            default -> note = null;
        }
        var c = new UICommandBuilder(); var e = new UIEventBuilder();
        if (fill(ref, c, e, store)) sendUpdate(c, e, false);
    }

    private void choose(World world, Store<EntityStore> store, TownRecord town, TownManager manager, AetherhavenPlugin plugin,
                        ApprenticeWorkplace site, GrowerPlan plan, String cropId, boolean manage) {
        if (!manage) { note = new Note("note.noAccess"); return; }
        var crop = plan.crop(cropId);
        var grower = site.grower();
        if (crop == null || crop == grower.chosen(plan)) { pendingCrop = null; return; }
        // Anything already growing is lost on a change, so that takes a second click on the same card.
        boolean growing = grower.chosen(plan) != null && grower.beds().stream().anyMatch(bed -> bed.stage() > (plan.logBeds() ? 0 : 1));
        if (growing && !crop.id().equals(pendingCrop)) {
            pendingCrop = crop.id();
            note = new Note("note.confirm", Map.of("crop", name(crop.item())));
            return;
        }
        pendingCrop = null;
        int bestSkill = TownsfolkWorkRoster.list(town, manager, store, plugin).stream().filter(w -> w.alive())
            .mapToInt(w -> w.progress().skill(site.type().skill)).max().orElse(0);
        String refused = grower.choose(plan, crop.id(), bestSkill, grower.revision());
        if (refused == null) {
            manager.updateTown(town);
            note = new Note("note.chose", Map.of("crop", name(crop.item())));
            // Cleared logs go back to bare bark. Blocks change outside this event.
            world.execute(() -> { if (town.getApprenticeWorkplaces().find(site.id()) == site) GrowerSiteService.refreshBeds(world, site); });
        } else note = new Note("skill".equals(refused) ? "note.skill" : "note.changed");
    }

    /** A plain name for a note. Notes are compared as text, so the item's name is resolved in the player's language. */
    private String name(String itemId) {
        var item = Item.getAssetMap().getAsset(itemId);
        var i18n = com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
        String translated = item == null || i18n == null ? null : i18n.getMessage(playerRef.getLanguage(), item.getTranslationKey());
        return translated == null || translated.isBlank() ? itemId.replace('_', ' ') : translated;
    }

    /** Move free stock to the player. Stock set aside for a workplace delivery stays. */
    private void collect(Ref<EntityStore> ref, Store<EntityStore> store, TownRecord town, TownManager manager,
                         ApprenticeWorkplace site, String only) {
        var inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        if (inventory == null) { note = new Note("note.packFull"); return; }
        var grower = site.grower();
        long moved = 0; boolean full = false;
        for (var held : new ArrayList<>(grower.amounts().entrySet())) {
            String item = held.getKey();
            if (only != null && !only.equals(item)) continue;
            var asset = Item.getAssetMap().getAsset(item);
            if (asset == null) continue;
            long free = Math.max(0, held.getValue() - town.getWarehouse().reservedProduction(site.id(), item));
            while (free > 0) {
                int count = (int) Math.min(free, Math.max(1, asset.getMaxStack()));
                if (!inventory.addItemStack(new ItemStack(item, count), true, false, true).succeeded()) { full = true; break; }
                grower.remove(item, count); moved += count; free -= count;
            }
        }
        if (moved > 0) { town.getWarehouse().markProductionWithdrawal(); manager.updateTown(town); }
        note = moved > 0 ? new Note("note.collected", Map.of("count", Long.toString(moved)))
            : new Note(full ? "note.packFull" : "note.nothing");
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
            .append(new KeyedCodec<>("Crop", Codec.STRING), (d, v) -> d.crop = v, d -> d.crop).add().build();
        String action, crop;
    }
}
