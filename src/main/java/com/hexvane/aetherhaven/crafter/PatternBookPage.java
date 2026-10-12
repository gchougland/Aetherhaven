package com.hexvane.aetherhaven.crafter;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.prop.PropItemMetadata;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.ui.PropIconPath;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** The Crafter's own screen: study props, commission copies from simple materials, and salvage spares. */
public final class PatternBookPage extends AetherhavenInteractiveCustomUIPage<PatternBookPage.Data> {
    private record Context(AetherhavenPlugin plugin, TownManager manager, TownRecord town, PatternBook book, ItemContainer inventory,
                           com.hexvane.aetherhaven.warehouse.TownWarehouse warehouse) {}
    private static final int COLUMNS = 9, MAX_SHELF = 8;
    private static final String CELL = "Aetherhaven/PatternBookCell.ui";
    private final Ref<EntityStore> crafter;
    private String selected;
    private String note;
    private boolean appended, polling;
    private volatile boolean open = true;

    public PatternBookPage(PlayerRef player, Ref<EntityStore> crafter) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.crafter = crafter;
    }

    private Context current(Ref<EntityStore> ref, Store<EntityStore> store) {
        var plugin = AetherhavenPlugin.get();
        if (plugin == null || crafter == null || !crafter.isValid()) return null;
        var binding = store.getComponent(crafter, TownVillagerBinding.getComponentType());
        var here = store.getComponent(ref, TransformComponent.getComponentType());
        var there = store.getComponent(crafter, TransformComponent.getComponentType());
        if (binding == null || !PatternBookService.KIND.equals(binding.getKind()) || here == null || there == null
            || here.getPosition().distanceSquared(there.getPosition()) > 100) return null;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(binding.getTownId());
        var inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        if (town == null || inventory == null || !town.hasMemberOrOwner(playerRef.getUuid())) return null;
        // Anyone allowed to open the town's stores may also pay Tobin from them.
        var warehouse = com.hexvane.aetherhaven.warehouse.WarehouseAccess.canBrowse(town, playerRef.getUuid()) ? town.getWarehouse() : null;
        return new Context(plugin, manager, town, town.getPatternBook(), inventory, warehouse);
    }

    /** Props in the player's pack, by prop id. */
    private static Map<String, Integer> carried(ItemContainer inventory) {
        var result = new LinkedHashMap<String, Integer>();
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            var stack = inventory.getItemStack(slot);
            String prop = ItemStack.isEmpty(stack) ? null : PropItemMetadata.readPropId(stack);
            if (prop != null && !prop.isBlank()) result.merge(prop, stack.getQuantity(), Integer::sum);
        }
        return result;
    }
    private static String name(PropDefinition prop) {
        return prop.getDisplayName() == null || prop.getDisplayName().isBlank() ? prop.getId() : prop.getDisplayName();
    }
    private static String minutes(double seconds) {
        long whole = Math.round(seconds);
        return whole < 60 ? whole + " seconds" : whole % 60 == 0 ? whole / 60 + " minutes" : whole / 60 + " min " + whole % 60 + " s";
    }
    /** The bench bar moves in twentieths: every step shown is an update sent, and an update can cost a press. */
    static float shownProgress(double progress) {
        return (float) (Math.floor(20 * Math.clamp(progress, 0, 1) + 1e-9) / 20.0);
    }

    @Override public void build(Ref<EntityStore> ref, UICommandBuilder b, UIEventBuilder e, Store<EntityStore> store) {
        if (!fill(ref, b, e, store)) return;
        // What the client receives whole. The poll below is compared with it and sends only what moved.
        opened(b, e);
        if (!polling) { polling = true; poll(store.getExternalData().getWorld()); }
    }

    /** Draws the whole page. False when the page had to close instead. */
    private boolean fill(Ref<EntityStore> ref, UICommandBuilder b, UIEventBuilder e, Store<EntityStore> store) {
        var c = current(ref, store);
        if (c == null) { open = false; close(); return false; }
        var book = c.book();
        if (!appended) {
            b.append("Aetherhaven/PatternBookPage.ui"); appended = true;
            for (String action : List.of("Commission", "Salvage", "Learn", "Collect", "Refresh"))
                e.addEventBinding(CustomUIEventBindingType.Activating, "#" + action, EventData.of("Action", action), false);
        }
        var props = new ArrayList<>(c.plugin().getPropCatalog().list());
        props.sort(Comparator.comparing((PropDefinition p) -> !book.knows(p.getId())).thenComparing(PatternBookPage::name, String.CASE_INSENSITIVE_ORDER));
        var carried = carried(c.inventory());
        if (selected == null || c.plugin().getPropCatalog().get(selected) == null) selected = props.isEmpty() ? null : props.getFirst().getId();

        b.set("#Summary.Text", "PATTERNS " + book.known().size() + " / " + props.size());
        var next = book.next();
        b.set("#Goal.Text", next == null ? "Every collection goal reached" : "At " + next.patterns() + " patterns: " + next.perk());

        b.clear("#Grid");
        for (int index = 0; index < props.size(); index++) {
            var prop = props.get(index);
            if (index % COLUMNS == 0) b.append("#Grid", "Aetherhaven/PatternBookRow.ui");
            String row = "#Grid[" + index / COLUMNS + "]";
            b.append(row, CELL);
            String cell = row + "[" + index % COLUMNS + "] #Cell";
            boolean known = book.knows(prop.getId());
            int have = carried.getOrDefault(prop.getId(), 0);
            b.set(cell + " #Art.AssetPath", PropIconPath.forPropId(prop.getId(), c.plugin().getDataDirectory()));
            b.set(cell + " #Unknown.Visible", !known);
            b.set(cell + " #Chosen.Visible", prop.getId().equals(selected));
            b.set(cell + " #Amount.Text", have > 0 ? Integer.toString(have) : "");
            b.set(cell + ".TooltipText", known ? name(prop) + (have > 0 ? "\nYou carry " + have : "")
                : have > 0 ? name(prop) + "\nYou carry one. Show it to Tobin to add its pattern." : "A prop Tobin has not seen yet");
            e.addEventBinding(CustomUIEventBindingType.Activating, cell, EventData.of("Action", "Select").append("Key", prop.getId()), false);
        }
        drawDetail(b, c, carried);
        drawBench(b, e, c);
        boolean fresh = carried.keySet().stream().anyMatch(prop -> !book.knows(prop) && c.plugin().getPropCatalog().get(prop) != null);
        b.set("#Learn.Disabled", !fresh);
        b.set("#BookNote.Text", note != null ? note : book.queue().isEmpty()
            ? "Props are built from sticks, stone rubble, scraps and linen scraps. Apprentices at a Crafting workplace can make all four."
            : "Tobin builds the first prop on his bench during his work hours.");
        return true;
    }

    private void drawDetail(UICommandBuilder b, Context c, Map<String, Integer> carried) {
        var prop = selected == null ? null : c.plugin().getPropCatalog().get(selected);
        b.clear("#Costs");
        if (prop == null) {
            b.set("#PropState.Text", ""); b.set("#PropName.Text", "No props yet"); b.set("#PropTime.Text", "");
            b.set("#Commission.Disabled", true); b.set("#Salvage.Disabled", true);
            return;
        }
        boolean known = c.book().knows(prop.getId());
        int have = carried.getOrDefault(prop.getId(), 0);
        b.set("#PropIcon.AssetPath", PropIconPath.forPropId(prop.getId(), c.plugin().getDataDirectory()));
        b.set("#PropState.Text", known ? "PATTERN KNOWN" : "NOT STUDIED YET");
        b.set("#PropName.Text", known || have > 0 ? name(prop) : "Unknown prop");
        var cost = PatternBookService.cost(prop);
        boolean afford = true; int line = 0;
        for (var part : cost.entrySet()) {
            long held = PatternBookPayment.available(c.inventory(), c.warehouse(), part.getKey().item());
            afford &= held >= part.getValue();
            b.append("#Costs", "Aetherhaven/PatternBookCost.ui");
            String path = "#Costs[" + line++ + "]";
            b.set(path + " #PartIcon.ItemId", part.getKey().item());
            var asset = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(part.getKey().item());
            b.set(path + " #PartName.TextSpans", asset != null && asset.getTranslationKey() != null && !asset.getTranslationKey().isBlank()
                ? com.hypixel.hytale.server.core.Message.translation(asset.getTranslationKey())
                : com.hypixel.hytale.server.core.Message.raw(part.getKey().label()));
            b.set(path + " #PartCount.Text", held + " / " + part.getValue());
        }
        b.set("#CostTitle.Text", known ? "MATERIALS NEEDED" : "MATERIALS IT WOULD NEED");
        b.set("#PropTime.Text", known
            ? "Takes Tobin about " + minutes(WorkshopParts.workSeconds(prop.getGoldPrice()) / c.book().speed()) + " at his bench."
            : "Find one and show it to Tobin. Cap'n Clive, treasure and festivals are good places to look.");
        b.set("#Commission.Disabled", !known || !afford || c.book().commissionIssue(prop.getId()) != null);
        b.set("#Salvage.Disabled", have < 1);
        b.set("#Salvage.Text", "Salvage one for " + c.book().salvagePercent() + "% of its materials");
    }

    private void drawBench(UICommandBuilder b, UIEventBuilder e, Context c) {
        var book = c.book();
        b.set("#BenchTitle.Text", "ON THE BENCH " + book.queue().size() + " / " + book.queueLimit());
        b.clear("#Bench");
        for (int index = 0; index < book.queue().size(); index++) {
            var prop = c.plugin().getPropCatalog().get(book.queue().get(index).prop());
            b.append("#Bench", CELL);
            String cell = "#Bench[" + index + "] #Cell";
            b.set(cell + " #Art.AssetPath", PropIconPath.forPropId(book.queue().get(index).prop(), c.plugin().getDataDirectory()));
            b.set(cell + " #Amount.Text", index == 0 ? "now" : "");
            b.set(cell + ".TooltipText", (prop == null ? "A prop" : name(prop))
                + (index == 0 ? "\nTobin is building this one." : "\nWaiting its turn.") + "\nSelect to cancel and get everything back.");
            // The press names the prop as well as the place. Tobin finishing the first one moves every other
            // commission along, and a press that only knew its place would then cancel the wrong one.
            e.addEventBinding(CustomUIEventBindingType.Activating, cell,
                EventData.of("Action", "Cancel").append("Key", index + "|" + book.queue().get(index).prop()), false);
        }
        b.set("#BenchProgress.Value", shownProgress(book.progress(prop -> PatternBookService.workSeconds(c.plugin(), prop))));
        b.clear("#Shelf");
        int shown = 0;
        for (var ready : book.shelf().entrySet()) {
            if (shown >= MAX_SHELF) break;
            var prop = c.plugin().getPropCatalog().get(ready.getKey());
            b.append("#Shelf", CELL);
            String cell = "#Shelf[" + shown++ + "] #Cell";
            b.set(cell + " #Art.AssetPath", PropIconPath.forPropId(ready.getKey(), c.plugin().getDataDirectory()));
            b.set(cell + " #Amount.Text", Integer.toString(ready.getValue()));
            b.set(cell + ".TooltipText", (prop == null ? "A finished prop" : name(prop)) + "\nSelect to take it.");
            e.addEventBinding(CustomUIEventBindingType.Activating, cell, EventData.of("Action", "Take").append("Key", ready.getKey()), false);
        }
        b.set("#Collect.Disabled", book.shelf().isEmpty());
    }

    /**
     * Looks once a second and sends only what differs from what the player already sees: a step of the bench bar,
     * or the bench and shelf when a prop is finished. While nothing on the page moves, nothing is sent.
     */
    private void poll(World world) {
        var plugin = AetherhavenPlugin.get();
        if (plugin == null || !open) return;
        plugin.scheduleOnWorld(world, () -> {
            var ref = playerRef.getReference();
            if (!open || ref == null || !ref.isValid()) { open = false; return; }
            var store = ref.getStore();
            var player = store.getComponent(ref, Player.getComponentType());
            if (player == null || player.getPageManager().getCustomPage() != this) { open = false; return; }
            var b = new UICommandBuilder(); var e = new UIEventBuilder();
            if (!fill(ref, b, e, store)) return;
            sendChanges(b, e);
            poll(world);
        }, 1000L);
    }

    @Override public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        super.onDismiss(ref, store);
        open = false;
    }

    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        var c = current(ref, store);
        if (c == null || data.action == null) { open = false; close(); return; }
        var book = c.book(); note = null;
        switch (data.action) {
            case "Select" -> { if (c.plugin().getPropCatalog().get(data.key) != null) selected = data.key; }
            case "Learn" -> {
                var learned = new ArrayList<String>();
                for (String prop : carried(c.inventory()).keySet()) {
                    var known = c.plugin().getPropCatalog().get(prop);
                    if (known != null && book.learn(prop)) learned.add(name(known));
                }
                if (learned.isEmpty()) note = "Tobin already knows every prop you carry.";
                else {
                    c.manager().updateTown(c.town());
                    note = "Tobin sketches " + (learned.size() == 1 ? learned.getFirst() : learned.size() + " new patterns") + " into his book and hands everything back.";
                }
            }
            case "Commission" -> commission(c);
            case "Cancel" -> cancel(c, data.key);
            case "Salvage" -> salvage(c);
            case "Take" -> take(c, data.key, false);
            case "Collect" -> { for (String prop : new ArrayList<>(book.shelf().keySet())) if (!take(c, prop, true)) break; }
            default -> {}
        }
        var b = new UICommandBuilder(); var e = new UIEventBuilder();
        if (fill(ref, b, e, store)) sendUpdate(b, e, false);
    }

    private void commission(Context c) {
        var prop = selected == null ? null : c.plugin().getPropCatalog().get(selected);
        if (prop == null) return;
        String issue = c.book().commissionIssue(prop.getId());
        var cost = PatternBookService.cost(prop);
        if (issue == null) for (var part : cost.entrySet())
            if (PatternBookPayment.available(c.inventory(), c.warehouse(), part.getKey().item()) < part.getValue()) {
                issue = "You need " + part.getValue() + " " + part.getKey().label() + " in your pack or the warehouse for this prop."; break;
            }
        if (issue != null) { note = issue; return; }
        var paid = new LinkedHashMap<String, Integer>();
        for (var part : cost.entrySet()) paid.put(part.getKey().item(), part.getValue());
        if (!PatternBookPayment.pay(c.inventory(), c.warehouse(), paid)) {
            note = "The materials could not be taken from your pack or the warehouse."; return;
        }
        c.book().commission(prop.getId(), paid);
        c.manager().updateTown(c.town());
        note = "Tobin takes the materials and adds " + name(prop) + " to his bench.";
    }

    private void cancel(Context c, String key) {
        int bar = key == null ? -1 : key.indexOf('|');
        if (bar < 0) return;
        int drawnAt;
        try { drawnAt = Integer.parseInt(key.substring(0, bar)); } catch (NumberFormatException invalid) { return; }
        String named = key.substring(bar + 1);
        int index = c.book().locate(drawnAt, named);
        if (index < 0) {
            var finished = c.plugin().getPropCatalog().get(named);
            note = "Tobin has already finished " + (finished == null ? "that prop" : name(finished)) + ". Look on his shelf.";
            return;
        }
        for (var part : c.book().queue().get(index).paid().entrySet())
            if (!c.inventory().canAddItemStack(new ItemStack(part.getKey(), part.getValue()))) {
                note = "Make room in your pack for the returned materials first."; return;
            }
        var paid = c.book().cancel(index);
        if (paid == null) return;
        for (var part : paid.entrySet()) c.inventory().addItemStack(new ItemStack(part.getKey(), part.getValue()));
        c.manager().updateTown(c.town());
        note = "Commission cancelled. Tobin hands everything back.";
    }

    private void salvage(Context c) {
        var prop = selected == null ? null : c.plugin().getPropCatalog().get(selected);
        if (prop == null) return;
        var parts = WorkshopParts.salvage(PatternBookService.cost(prop), c.book().salvagePercent());
        for (var part : parts.entrySet())
            if (!c.inventory().canAddItemStack(new ItemStack(part.getKey().item(), part.getValue()))) {
                note = "Make room in your pack for the salvaged materials first."; return;
            }
        for (short slot = 0; slot < c.inventory().getCapacity(); slot++) {
            var stack = c.inventory().getItemStack(slot);
            if (ItemStack.isEmpty(stack) || !prop.getId().equals(PropItemMetadata.readPropId(stack))) continue;
            if (!c.inventory().removeItemStackFromSlot(slot, 1).succeeded()) continue;
            for (var part : parts.entrySet()) c.inventory().addItemStack(new ItemStack(part.getKey().item(), part.getValue()));
            // Taking a prop apart teaches Tobin how it goes together.
            boolean learned = c.book().learn(prop.getId());
            c.manager().updateTown(c.town());
            note = "Tobin takes " + name(prop) + " apart for materials." + (learned ? " He notes down its pattern as he goes." : "");
            return;
        }
        note = "You are not carrying that prop.";
    }

    private boolean take(Context c, String propId, boolean all) {
        var prop = c.plugin().getPropCatalog().get(propId);
        int count = c.book().shelf().getOrDefault(propId, 0);
        if (count < 1) return true;
        if (prop == null) { note = "That prop is no longer available in this world."; return true; }
        int given = 0;
        for (int i = 0; i < (all ? count : 1); i++) {
            var stack = PropItemMetadata.createStack(prop);
            if (!c.inventory().canAddItemStack(stack) || !c.inventory().addItemStack(stack).succeeded()) break;
            c.book().take(propId); given++;
        }
        if (given > 0) c.manager().updateTown(c.town());
        if (given < (all ? count : 1)) { note = "Your pack is full. The rest stays on Tobin's shelf."; return false; }
        return true;
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
            .append(new KeyedCodec<>("Key", Codec.STRING), (d, v) -> d.key = v, d -> d.key).add().build();
        String action, key;
    }
}
