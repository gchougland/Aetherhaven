package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

/**
 * Saved Rootstone bindings, planted trees and original terrain. No entity, chunk or world references.
 * Integer keys identify one Rootstone each. Saves from the earlier grove planner used the same keys for material slots.
 */
public final class TimberProductionState {
    /** Upper bound for stones at one lumbermill, whatever its upgrades. */
    public static final int MAX_STONES = 6;
    public static final int BLESSING_COST = 5;
    public static final String BLESSING_ITEM = "Ingredient_Life_Essence";
    public record Ground(boolean loaded, String block, int rotation, int filler,
            boolean empty, boolean soil, boolean naturalTerrain, boolean protectedCell) {}
    public record Original(Position position, String block, int rotation) {}
    public record Planting(TimberPlantingReceipt tree, List<Original> originals) {
        public Planting { originals = List.copyOf(originals); }
    }
    public record Prepared(Planting planting, String issue) {
        public boolean ready() { return planting != null && issue == null; }
    }
    private Map<Integer, Planting> trees = new LinkedHashMap<>();
    private Map<Integer, TimberPlantingReceipt> groves = new LinkedHashMap<>();
    private long layoutRevision;
    /** At most one tree's yield can wait for delivery. It is not warehouse stock. */
    public record Cargo(UUID tree, String output, Position source, int remaining, int carried) {}
    private Cargo cargo;
    private List<Cargo> remainingCargo=new ArrayList<>();
    private int nextSlot;
    private Map<Integer, Double> chopping = new LinkedHashMap<>();
    /** Stones whose next harvest is doubled by a Life Essence blessing. */
    private Set<Integer> elder = new HashSet<>();
    private boolean fibre;
    /** The one wood the Logger may char, chosen by the player. Null leaves every log as it is. */
    private String charWood;
    // Once a lumbermill tends Rootstones, harvesting the last tree must not restart the old resource timer.
    private boolean adopted;

    public boolean adopted() { return adopted || trees != null && !trees.isEmpty(); }
    public Map<Integer, Planting> trees() { return trees == null ? Map.of() : Collections.unmodifiableMap(trees); }
    public Map<Integer, TimberPlantingReceipt> groves() { return groves == null ? Map.of() : Collections.unmodifiableMap(groves); }
    public long layoutRevision() { return layoutRevision; }
    public Cargo cargo() { return cargo; }
    public boolean gathersFibre() { return fibre; }
    public String charWood() { return charWood == null || charWood.isBlank() ? null : charWood; }
    public void setGathersFibre(boolean value) { fibre = value; }
    public void setCharWood(String wood) { charWood = wood == null || wood.isBlank() ? null : wood; }
    public boolean blessed(int stone) { return elder != null && elder.contains(stone); }
    public Map<String,Integer> waitingProducts() {
        var result=new LinkedHashMap<String,Integer>();
        if(cargo!=null)result.put(cargo.output(),cargo.remaining());
        if(remainingCargo!=null)for(var held:remainingCargo)result.merge(held.output(),held.remaining(),Math::addExact);
        return Collections.unmodifiableMap(result);
    }
    public int firstSlot(int count) { return count<=0?0:Math.floorMod(nextSlot,count); }
    public void rotateAfter(int slot) { nextSlot=slot+1; }

    /** The stone whose tree grows directly above this block, or -1. */
    public int stoneAt(Position block) {
        if (block == null) return -1;
        for (var bound : groves().entrySet()) {
            var root = bound.getValue() == null ? null : bound.getValue().root();
            if (root != null && root.x() == block.x() && root.z() == block.z() && root.y() - 1 == block.y()) return bound.getKey();
        }
        for (var planted : trees().entrySet()) {
            var root = planted.getValue() == null || planted.getValue().tree() == null ? null : planted.getValue().tree().root();
            if (root != null && root.x() == block.x() && root.z() == block.z() && root.y() - 1 == block.y()) return planted.getKey();
        }
        return -1;
    }
    /** Lowest unused stone number below the allowance, or -1 when every stone is in use. */
    public int freeStone(int allowance) {
        for (int stone = 0; stone < Math.min(allowance, MAX_STONES); stone++)
            if (!groves().containsKey(stone) && !trees().containsKey(stone)) return stone;
        return -1;
    }
    /** One blessing at a time. The caller takes payment only when this returns null. */
    public String blessingIssue(int stone) {
        if (!groves().containsKey(stone)) return "Choose a tree for this Rootstone first.";
        if (blessed(stone)) return "This Rootstone is already blessed.";
        return null;
    }
    public boolean bless(int stone) {
        if (blessingIssue(stone) != null) return false;
        if (elder == null) elder = new HashSet<>();
        elder.add(stone); layoutRevision++;
        return true;
    }
    /** Forgets a stone and any tree it owns. Blocks already grown stay in the world for the player. */
    public boolean uproot(int stone) {
        boolean changed = groves != null && groves.remove(stone) != null;
        changed |= trees != null && trees.remove(stone) != null;
        if (chopping != null) chopping.remove(stone);
        if (elder != null) elder.remove(stone);
        if (changed) layoutRevision++;
        return changed;
    }
    /** A tree that vanished or was cleared away keeps its stone, which simply grows again. */
    public boolean release(int stone, UUID treeId) {
        var planted = trees().get(stone);
        if (planted == null || !planted.tree().id().equals(treeId)) return false;
        trees.remove(stone); if (chopping != null) chopping.remove(stone);
        return true;
    }
    public double chopping(int slot) { return chopping == null ? 0 : chopping.getOrDefault(slot, 0.0); }
    public void work(int slot, double seconds) {
        if (!trees().containsKey(slot) || cargo != null || !Double.isFinite(seconds) || seconds <= 0) return;
        if (chopping == null) chopping = new LinkedHashMap<>();
        chopping.put(slot, Math.min(86_400, chopping(slot) + Math.min(seconds, 1)));
    }
    /** Only a successful physical removal creates a pending delivery. No stock is credited here. */
    public boolean fell(int slot, UUID treeId, int yield, BooleanSupplier remove) {
        return fell(slot,treeId,yield,Map.of(),remove);
    }
    public boolean fell(int slot,UUID treeId,int yield,Map<String,Integer> extras,BooleanSupplier remove) {
        if(extras==null||extras.size()>4||extras.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey().isBlank()||e.getValue()==null||e.getValue()<1||e.getValue()>TimberHarvestPlan.MAX_TREE_CELLS*16))return false;
        var planted = trees().get(slot);
        if(planted!=null&&extras.containsKey(planted.tree().output()))return false;
        if (cargo != null || planted == null || !planted.tree().id().equals(treeId)
                || yield < 1 || yield > TimberHarvestPlan.MAX_TREE_CELLS || !remove.getAsBoolean()) return false;
        // A blessed stone doubles the wood from this one tree only.
        int wood = blessed(slot) ? Math.min(TimberHarvestPlan.MAX_TREE_CELLS, yield * 2) : yield;
        cargo = new Cargo(treeId, planted.tree().output(), planted.tree().root(), wood, 0);
        remainingCargo=new ArrayList<>();
        for(var entry:extras.entrySet())remainingCargo.add(new Cargo(treeId,entry.getKey(),planted.tree().root(),entry.getValue(),0));
        trees.remove(slot);
        if (chopping != null) chopping.remove(slot);
        if (elder != null) elder.remove(slot);
        nextSlot=slot+1;
        adopted = true;
        return true;
    }
    /** A paid charring batch joins the same saved delivery path, already held by the attending Logger. */
    public boolean charLogs(Position source, BooleanSupplier pay) {
        if (!adopted() || source == null || cargo != null
            || remainingCargo != null && !remainingCargo.isEmpty() || !pay.getAsBoolean()) return false;
        cargo = new Cargo(UUID.randomUUID(), TimberCharring.OUTPUT, source, 1, 1);
        return true;
    }
    /** Caller has verified attendance at the felled tree. One bounded armful per trip. */
    public int pickUp(int carryLimit) {
        if(cargo==null || carryLimit<=0 || cargo.carried()>0)return 0;
        int count=Math.min(cargo.remaining(),carryLimit);
        cargo=new Cargo(cargo.tree(),cargo.output(),cargo.source(),cargo.remaining(),count);
        return count;
    }
    /** Caller has verified physical attendance at this plot's storage on the world thread. */
    public int deliver(PlotProductionState production, long capacity, int carryLimit) {
        if (cargo == null || carryLimit <= 0 || capacity <= 0) return 0;
        long cap = Math.min(capacity, ProductionCatalog.MAX_STORAGE_PER_OUTPUT);
        int count = (int)Math.min(Math.min(cargo.carried(), carryLimit), Math.max(0, cap - production.getAmount(cargo.output())));
        if (count <= 0) return 0;
        production.addAmount(cargo.output(), count, cap);
        if(count==cargo.remaining()) {
            if(remainingCargo==null||remainingCargo.isEmpty()){cargo=null;}
            else {var next=remainingCargo.removeFirst();cargo=new Cargo(next.tree(),next.output(),next.source(),next.remaining(),0);}
        }
        else cargo=new Cargo(cargo.tree(),cargo.output(),cargo.source(),cargo.remaining()-count,cargo.carried()-count);
        return count;
    }
    /** The Logger shoulders the whole harvest of one tree at once, so nothing is left lying at the stump. */
    public boolean carryAll() {
        if (cargo == null) return false;
        boolean changed = cargo.carried() < cargo.remaining();
        cargo = new Cargo(cargo.tree(), cargo.output(), cargo.source(), cargo.remaining(), cargo.remaining());
        if (remainingCargo != null) for (int i = 0; i < remainingCargo.size(); i++) {
            var held = remainingCargo.get(i);
            changed |= held.carried() < held.remaining();
            remainingCargo.set(i, new Cargo(held.tree(), held.output(), held.source(), held.remaining(), held.remaining()));
        }
        return changed;
    }
    /**
     * One visit to the stores puts away every product that fits. Anything that does not fit is discarded,
     * so one full store never holds up the rest. Caller has verified attendance at this plot's storage.
     */
    public int deliverAll(PlotProductionState production, java.util.function.ToLongFunction<String> capacity) {
        if (cargo == null) return 0;
        var all = new ArrayList<Cargo>(); all.add(cargo); if (remainingCargo != null) all.addAll(remainingCargo);
        int total = 0;
        for (var held : all) {
            long cap = Math.min(capacity.applyAsLong(held.output()), ProductionCatalog.MAX_STORAGE_PER_OUTPUT);
            int count = (int) Math.max(0, Math.min(held.remaining(), cap - production.getAmount(held.output())));
            if (count > 0) { production.addAmount(held.output(), count, cap); total += count; }
        }
        cargo = null; remainingCargo = new ArrayList<>();
        return total;
    }
    public boolean conflictsWith(TimberPlantingReceipt tree) {
        Set<Position> footprint = new HashSet<>(tree.footprint()); footprint.add(below(tree.root()));
        for (var other : groves().values()) {
            if (other == null || other.root() == null || footprint.contains(below(other.root()))
                || other.footprint().stream().anyMatch(footprint::contains)) return true;
        }
        for (var other : trees().values()) {
            if (other == null || other.originals().stream().anyMatch(c -> footprint.contains(c.position()))) return true;
        }
        return false;
    }

    /** Binding a stone never plants a tree by itself. The Logger plants it on her next visit. */
    public String plan(int slot, int allowance, long expectedRevision, TimberPlantingReceipt tree,
            Predicate<Position> withinGrove, Function<Position, Ground> read) {
        if (expectedRevision != layoutRevision) return "The grove changed. Open the Rootstone again.";
        Prepared prepared = prepare(slot, allowance, tree, withinGrove, read);
        if (!prepared.ready()) return prepared.issue();
        if (groves == null) groves = new LinkedHashMap<>();
        groves.put(slot, tree); layoutRevision++; adopted = true;
        return null;
    }

    public Prepared prepare(int slot, int allowance, TimberPlantingReceipt tree, Predicate<Position> withinGrove,
            Function<Position, Ground> read) {
        if (slot < 0 || slot >= allowance || allowance > MAX_STONES || tree == null)
            return rejected("This lumbermill cannot tend another Rootstone yet.");
        if (trees().containsKey(slot)) return rejected("This Rootstone already has a growing tree.");
        String issue = tree.issue(withinGrove);
        if (issue != null) return rejected(issue);
        if (tree.initialGeneration() != 0) return rejected("Plant a fresh sapling to start this tree.");
        Set<Position> footprint = new LinkedHashSet<>(tree.footprint());
        Position below = below(tree.root());
        if (below == null || !withinGrove.test(below)) return rejected("The Rootstone must be inside your town.");
        footprint.add(below);
        for (var planned : groves().entrySet()) {
            if (planned.getKey() == slot) continue;
            var other = planned.getValue();
            if (other == null || other.root() == null) return rejected("Another Rootstone needs review before planting.");
            if (other.footprint().stream().anyMatch(footprint::contains) || footprint.contains(below(other.root())))
                return rejected("This tree would grow into another Rootstone's tree. Move the stones further apart.");
        }
        for (Planting existing : trees().values()) {
            if (existing == null || existing.tree() == null) return rejected("A saved tree needs review before planting.");
            if (existing.tree().id().equals(tree.id())) return rejected("This planting is already recorded.");
            if (existing.originals().stream().anyMatch(c -> footprint.contains(c.position())))
                return rejected("This tree would grow into another Rootstone's tree. Move the stones further apart.");
        }
        List<Original> originals = new ArrayList<>();
        for (Position point : footprint) {
            Ground ground = read.apply(point);
            String blocked = groundIssue(point, tree.root(), below, ground);
            if (blocked != null) return rejected(blocked);
            originals.add(new Original(point, ground.block(), ground.rotation()));
        }
        return new Prepared(new Planting(tree, originals), null);
    }

    /**
     * Rechecks the complete preflight on the world thread. No ownership is saved
     * unless placement returns success. The caller saves the town immediately
     * after success and owns worker-attendance prerequisites. Planting consumes no items.
     */
    public String plant(int slot, int allowance, Prepared prepared, Predicate<Position> withinGrove,
            Function<Position, Ground> read, BooleanSupplier place) {
        if (prepared == null || !prepared.ready()) return "Prepare this tree before planting it.";
        Prepared current = prepare(slot, allowance, prepared.planting().tree(), withinGrove, read);
        if (!current.ready()) return current.issue();
        if (!current.planting().originals().equals(prepared.planting().originals()))
            return "The growing space changed. Check it before planting.";
        if (!place.getAsBoolean()) return "The sapling could not be planted.";
        if (trees == null) trees = new LinkedHashMap<>();
        trees.put(slot, prepared.planting()); adopted = true;
        return null;
    }

    private static Position below(Position root) {
        return root.y() == Integer.MIN_VALUE ? null : new Position(root.x(), root.y()-1, root.z());
    }
    private static String groundIssue(Position point, Position root, Position below, Ground ground) {
        if (ground == null || !ground.loaded()) return "The tree needs its whole growing space nearby and loaded.";
        if (ground.block() == null || ground.block().isBlank() || ground.rotation() < 0
                || ground.filler() != 0 || ground.protectedCell()) return "Something important is in the way of this tree. Try another spot.";
        if (point.equals(below) && !ground.soil()) return "The tree needs its Rootstone beneath it.";
        if (point.y() >= root.y() && !ground.empty()) return "This tree needs more room. Clear the space above and around the Rootstone.";
        if (point.y() < root.y() && !ground.empty() && !ground.naturalTerrain())
            return "The tree's roots would reach something you built. Try another spot.";
        return null;
    }
    private static Prepared rejected(String issue) { return new Prepared(null, issue); }
}
