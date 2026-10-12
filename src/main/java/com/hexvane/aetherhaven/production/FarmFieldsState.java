package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Saved Scarecrow Fields for one farm: each field's crop plan, the richness of every cell, the Farmer's basket,
 * her seed store, her compost and the farm's upgrades. No entity, chunk or world references.
 *
 * <p>Field 0 is the farm building's own field and has no Scarecrow block. The other fields each belong to one
 * Scarecrow placed somewhere in town. A cell is named by the crop's own position, directly above its soil.
 */
public final class FarmFieldsState {
    /** The farm building's own field. */
    public static final int HOME = 0;
    /** The farm's own field and at most three Scarecrow fields, whatever the upgrades. */
    public static final int MAX_FIELDS = 4;
    /** Most cells one field may plan. */
    public static final int MAX_CELLS = 256;
    public static final int WORN = 0, GOOD = 1, RICH = 2;

    /**
     * @param crop the planned crop's item, or null when the cell is only remembered for its soil
     * @param soil {@link #WORN}, {@link #GOOD} or {@link #RICH}
     * @param last the crop last harvested here
     * @param repeats harvests of {@code last} in a row since the soil last wore
     * @param fresh true from a harvest until the next planting, so one harvest can restore the soil only once
     */
    public record Cell(int x, int y, int z, String crop, int soil, String last, int repeats, boolean fresh) {
        public Point point() { return new Point(x, y, z); }
        public boolean planned() { return crop != null; }
        private Cell withCrop(String next) { return new Cell(x, y, z, next, soil, last, repeats, fresh); }
        private boolean forgettable() { return crop == null && soil == GOOD && last == null; }
    }
    /** One field: the Scarecrow that claims it, or no block for the farm's own field. */
    public static final class Field {
        private UUID id = UUID.randomUUID();
        private Point anchor;
        private List<Cell> cells = new ArrayList<>();
        public UUID id() { return id; }
        public Point anchor() { return anchor; }
        public List<Cell> cells() { return cells == null ? List.of() : Collections.unmodifiableList(cells); }
        private int index(int x, int z) {
            if (cells != null) for (int i = 0; i < cells.size(); i++) if (cells.get(i).x() == x && cells.get(i).z() == z) return i;
            return -1;
        }
    }
    /** What one harvest put in the basket, and what it did to the soil. */
    public record Harvest(int crops, int essence, boolean golden, boolean seedReturned, int soilBefore, int soilAfter) {}
    /** What one trip to the stores did. */
    public record Delivery(Map<String, Integer> stored, int composted, int compostMade) {}
    public record Target(int slot, Cell cell) {}

    private boolean adopted;
    private long revision;
    private Map<Integer, Field> fields = new LinkedHashMap<>();
    private Map<String, Integer> basket = new LinkedHashMap<>();
    private Map<String, Integer> seeds = new LinkedHashMap<>();
    private int compost;
    private int compostProgress;
    private Map<String, Integer> upgrades = new LinkedHashMap<>();
    private boolean legacyMapped;
    private boolean starterGiven;
    /** The revision of the farm's old bed list that was last taken into the plan. Zero means none was. */
    private long legacyBeds;

    /** True once a crop plan was ever painted. From then on the farm lives from its fields, not from automatic production. */
    public boolean adopted() { return adopted || carrying() || plannedCount() > 0; }
    public void adopt() { adopted = true; }
    public long revision() { return revision; }
    public Map<Integer, Field> fields() { return fields == null ? Map.of() : Collections.unmodifiableMap(fields); }
    public Field field(int slot) { return fields().get(slot); }
    boolean legacyMapped() { return legacyMapped; }
    void markLegacyMapped() { legacyMapped = true; }
    boolean starterGiven() { return starterGiven; }
    void markStarterGiven() { starterGiven = true; }

    public int level(String track) { return upgrades == null ? 0 : Math.max(0, upgrades.getOrDefault(track, 0)); }
    public void setLevel(String track, int level) {
        if (upgrades == null) upgrades = new LinkedHashMap<>();
        upgrades.put(track, Math.max(0, level));
    }

    // Fields ---------------------------------------------------------------------------------------------------

    /** The field this Scarecrow claims, or -1. */
    public int fieldAt(Point anchor) {
        if (anchor != null) for (var claimed : fields().entrySet())
            if (anchor.equals(claimed.getValue().anchor())) return claimed.getKey();
        return -1;
    }
    /** Lowest unused Scarecrow field below the allowance, or -1 when every Scarecrow is in use. */
    public int freeSlot(int scarecrows) {
        for (int slot = 1; slot <= Math.min(scarecrows, MAX_FIELDS - 1); slot++) if (!fields().containsKey(slot)) return slot;
        return -1;
    }
    /** The farm's own field exists from the start and is never removed. */
    public Field home() {
        if (fields == null) fields = new LinkedHashMap<>();
        return fields.computeIfAbsent(HOME, slot -> new Field());
    }
    /**
     * A Scarecrow claims its field.
     *
     * @return null on success, otherwise the short name of what stopped it
     */
    public String claim(int slot, int scarecrows, Point anchor) {
        if (anchor == null) return "anchor";
        if (slot < 1 || slot > scarecrows || slot >= MAX_FIELDS) return "full";
        if (fields().containsKey(slot) || fieldAt(anchor) >= 0) return "taken";
        if (fields == null) fields = new LinkedHashMap<>();
        var field = new Field(); field.anchor = anchor;
        fields.put(slot, field); revision++;
        return null;
    }
    /** A broken Scarecrow gives up its field. Crops already growing stay where they are. */
    public boolean release(int slot) {
        if (slot == HOME || fields == null || fields.remove(slot) == null) return false;
        revision++;
        return true;
    }
    public boolean releaseAt(Point anchor) {
        int slot = fieldAt(anchor);
        return slot > HOME && release(slot);
    }

    // The crop plan --------------------------------------------------------------------------------------------

    public Cell cell(int slot, int x, int z) {
        var field = field(slot);
        int index = field == null ? -1 : field.index(x, z);
        return index < 0 ? null : field.cells.get(index);
    }
    /** The field that plans this column, or -1. */
    public int owner(int x, int z) {
        for (var claimed : fields().entrySet()) {
            int index = claimed.getValue().index(x, z);
            if (index >= 0 && claimed.getValue().cells.get(index).planned()) return claimed.getKey();
        }
        return -1;
    }
    public int soil(int slot, int x, int z) {
        var cell = cell(slot, x, z);
        return cell == null ? GOOD : cell.soil();
    }
    public List<Cell> planned(int slot) {
        var field = field(slot);
        return field == null ? List.of() : field.cells().stream().filter(Cell::planned).toList();
    }
    public int plannedCount() {
        int total = 0;
        for (var field : fields().values()) for (var cell : field.cells()) if (cell.planned()) total++;
        return total;
    }

    /**
     * Paints one cell with a crop, or clears it with {@code null}. The caller has checked the ground. Soil is
     * remembered when a cell is cleared, so clearing and painting again never mends worn soil.
     */
    public boolean paint(int slot, int x, int y, int z, String crop) {
        var field = field(slot);
        if (field == null || crop != null && crop.isBlank()) return false;
        int index = field.index(x, z);
        if (index < 0) {
            if (crop == null || planned(slot).size() >= MAX_CELLS) return false;
            if (field.cells == null) field.cells = new ArrayList<>();
            field.cells.add(new Cell(x, y, z, crop, GOOD, null, 0, false));
        } else {
            var cell = field.cells.get(index);
            if (Objects.equals(cell.crop(), crop) && cell.y() == y) return false;
            if (crop != null && !cell.planned() && planned(slot).size() >= MAX_CELLS) return false;
            var next = new Cell(x, crop == null ? cell.y() : y, z, crop, cell.soil(), cell.last(), cell.repeats(), cell.fresh());
            if (next.forgettable()) field.cells.remove(index); else field.cells.set(index, next);
        }
        if (crop != null) adopted = true;
        revision++;
        return true;
    }
    /** Clears a whole field's plan. Its soil is remembered. */
    public boolean clear(int slot) {
        var field = field(slot);
        if (field == null || field.cells == null || field.cells.stream().noneMatch(Cell::planned)) return false;
        field.cells.replaceAll(cell -> cell.withCrop(null));
        field.cells.removeIf(Cell::forgettable);
        revision++;
        return true;
    }
    /**
     * Moves every crop in a field's plan on to the next crop that field grows, so each cell gets a different crop
     * from last time. A field with one crop cannot rotate.
     */
    public boolean rotate(int slot) {
        var field = field(slot);
        if (field == null) return false;
        var crops = new ArrayList<String>();
        for (var cell : field.cells()) if (cell.planned() && !crops.contains(cell.crop())) crops.add(cell.crop());
        if (crops.size() < 2) return false;
        field.cells.replaceAll(cell -> cell.planned() ? cell.withCrop(crops.get((crops.indexOf(cell.crop()) + 1) % crops.size())) : cell);
        revision++;
        return true;
    }

    // Soil -----------------------------------------------------------------------------------------------------

    private void put(int slot, Cell cell) {
        var field = field(slot);
        field.cells.set(field.index(cell.x(), cell.z()), cell);
    }
    /**
     * The Farmer set a seed here. A different crop from the one last harvested restores the soil one step, once
     * for each harvest.
     *
     * @return true when the soil was restored
     */
    public boolean planted(int slot, int x, int z, String crop) {
        var cell = cell(slot, x, z);
        if (cell == null || crop == null) return false;
        boolean restores = cell.fresh() && cell.last() != null && !cell.last().equals(crop) && cell.soil() < RICH;
        put(slot, new Cell(cell.x(), cell.y(), cell.z(), cell.crop(), restores ? cell.soil() + 1 : cell.soil(), cell.last(), cell.repeats(), false));
        return restores;
    }

    /**
     * One ripe crop goes into the basket. Rich soil multiplies the crop, a favoured crop adds to it, and only
     * Rich soil can give a Golden Harvest. Harvesting the same crop again and again wears the soil.
     *
     * @param rolledCrops what the game's own harvest list gave for this plant
     * @param rolledEssence the Life Essence that list gave
     * @param seedChance chance that the harvest gives its seed back
     * @param goldenRoll a number from 0 to 1, compared with the golden chance
     * @param seedRoll a number from 0 to 1, compared with the seed chance
     * @param crop the crop that stood there. It is usually the planned crop, and after the plan changed it is the
     *             crop from before, gathered once more before the new one goes in
     * @return what was gathered, or null when this cell is not part of the plan
     */
    public Harvest harvest(int slot, int x, int z, String crop, String seed, int rolledCrops, int rolledEssence, boolean favoured,
            double seedChance, double goldenRoll, double seedRoll, FarmFieldRules rules) {
        var cell = cell(slot, x, z);
        if (cell == null || !cell.planned() || crop == null || rolledCrops < 0 || rolledEssence < 0) return null;
        boolean rich = cell.soil() >= RICH;
        int crops = Math.clamp((long) rolledCrops * (rich ? rules.soil().richYield() : 1) + (favoured ? rules.almanac().extraYield() : 0), 0, 4096);
        boolean golden = rich && goldenRoll < rules.soil().goldenChance();
        boolean seedBack = seed != null && seedRoll < seedChance;
        if (crops > 0) hold(crop, crops);
        if (rolledEssence > 0) hold(rules.seeds().essence(), Math.min(rolledEssence, 4096));
        if (golden) hold(rules.golden().item(), 1);
        if (seedBack) addSeeds(seed, 1, rules.seeds().keep());
        int repeats = crop.equals(cell.last()) ? cell.repeats() + 1 : 1, soil = cell.soil();
        if (repeats >= rules.soil().wearEvery()) { soil = Math.max(WORN, soil - 1); repeats = 0; }
        put(slot, new Cell(cell.x(), cell.y(), cell.z(), cell.crop(), soil, crop, repeats, true));
        adopted = true;
        return new Harvest(crops, rolledEssence, golden, seedBack, cell.soil(), soil);
    }

    // Basket and stores ----------------------------------------------------------------------------------------

    private void hold(String item, int count) {
        if (basket == null) basket = new LinkedHashMap<>();
        basket.merge(item, count, (a, b) -> Math.min(1_000_000, a + b));
    }
    public Map<String, Integer> basket() { return basket == null ? Map.of() : Collections.unmodifiableMap(basket); }
    public boolean carrying() { return basket != null && !basket.isEmpty(); }
    public int carried() { return basket().values().stream().mapToInt(Integer::intValue).sum(); }

    /**
     * One visit to the stores empties the basket. Each item is stored up to the amount the farm keeps of it. Crops
     * beyond that are not wasted: they are composted, a fixed number of crops for each compost.
     *
     * @param capacity how much of an item the farm keeps
     * @param crop true for an item that can be composted
     */
    public Delivery deliverAll(PlotProductionState production, ToLongFunction<String> capacity, Predicate<String> crop,
            int cropsPerCompost, int compostKeep) {
        var stored = new LinkedHashMap<String, Integer>(); int composted = 0;
        for (var held : basket().entrySet()) {
            long cap = Math.min(capacity.applyAsLong(held.getKey()), ProductionCatalog.MAX_STORAGE_PER_OUTPUT);
            int count = (int) Math.max(0, Math.min(held.getValue(), cap - production.getAmount(held.getKey())));
            if (count > 0) { production.addAmount(held.getKey(), count, cap); stored.put(held.getKey(), count); }
            if (crop.test(held.getKey())) composted += held.getValue() - count;
        }
        basket = new LinkedHashMap<>();
        int made = 0, each = Math.max(1, cropsPerCompost);
        compostProgress += composted;
        while (compostProgress >= each && compost < compostKeep) { compost++; made++; compostProgress -= each; }
        // A full compost heap takes no more. What does not fit is gone, as overflow always was.
        if (compost >= compostKeep) compostProgress = Math.min(compostProgress, each - 1);
        return new Delivery(stored, composted, made);
    }

    public int compost() { return Math.max(0, compost); }
    /** Spare crops already set aside toward the next compost. */
    public int compostProgress() { return Math.max(0, compostProgress); }
    /** True when compost may go on soil of this richness: always on Worn soil, and on Good soil once compost enriches. */
    public static boolean takesCompost(int soil, boolean enriches) { return soil == WORN || enriches && soil < RICH; }
    /**
     * The planned cell that most needs compost: the most worn first.
     *
     * @param enriches true when compost may also lift Good soil to Rich
     */
    public Target compostTarget(boolean enriches) {
        Target best = null;
        for (var claimed : fields().entrySet()) for (var cell : claimed.getValue().cells())
            if (cell.planned() && takesCompost(cell.soil(), enriches) && (best == null || cell.soil() < best.cell().soil())) best = new Target(claimed.getKey(), cell);
        return best;
    }
    /** One compost lifts one cell one step. */
    public boolean spread(int slot, int x, int z, boolean enriches) {
        var cell = cell(slot, x, z);
        if (cell == null || !cell.planned() || !takesCompost(cell.soil(), enriches) || compost < 1) return false;
        compost--;
        put(slot, new Cell(cell.x(), cell.y(), cell.z(), cell.crop(), cell.soil() + 1, cell.last(), cell.repeats(), cell.fresh()));
        return true;
    }

    public Map<String, Integer> seeds() { return seeds == null ? Map.of() : Collections.unmodifiableMap(seeds); }
    public int seeds(String item) { return item == null ? 0 : Math.max(0, seeds().getOrDefault(item, 0)); }
    /** @return how many were accepted */
    public int addSeeds(String item, int count, int keep) {
        if (item == null || item.isBlank() || count < 1) return 0;
        if (seeds == null) seeds = new LinkedHashMap<>();
        int accepted = Math.max(0, Math.min(count, keep - seeds(item)));
        if (accepted > 0) seeds.merge(item, accepted, Integer::sum);
        return accepted;
    }
    public boolean takeSeed(String item) {
        if (seeds(item) < 1) return false;
        if (seeds.merge(item, -1, Integer::sum) <= 0) seeds.remove(item);
        return true;
    }

    // Old saves ------------------------------------------------------------------------------------------------

    /**
     * A farm that already did field work keeps working: its beds become the plan of the farm's own field with the
     * crops that are in them, and whatever the Farmer was carrying moves into her basket.
     *
     * @return how many beds were taken over
     */
    public int adoptLegacy(FieldProductionState old) {
        if (old == null || !old.adopted() || old.revision() == legacyBeds) return 0;
        legacyBeds = old.revision();
        home(); int taken = 0;
        for (var bed : old.beds()) {
            if (bed == null || bed.point() == null || bed.output() == null || bed.output().isBlank()) continue;
            var p = bed.point();
            if (owner(p.x(), p.z()) < 0 && paint(HOME, p.x(), p.y(), p.z(), bed.output())) taken++;
        }
        for (var held : old.retire().entrySet()) hold(held.getKey(), held.getValue());
        adopted = true;
        return taken;
    }
}
