package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Core Farmer or Druid beds and carried produce. Apprentice food support never uses this inventory. */
public final class FieldProductionState {
    public static final int MAX_BEDS=75;
    public record Bed(WarehouseBatch.Point point,String crop,String output) {}
    public record Cargo(UUID id,String output,int quantity) {}
    public record Pending(UUID id,Bed bed,int quantity,String regrownBlock,Map<String,Integer> yields) {
        public Pending(UUID id,Bed bed,int quantity,String regrownBlock){this(id,bed,quantity,regrownBlock,null);}
    }
    private boolean adopted;
    private long revision;
    private List<Bed> beds=new ArrayList<>();
    private Cargo cargo;
    private List<Cargo> remainingCargo=new ArrayList<>();
    private Pending pending;
    private int nextBed;
    public boolean adopted(){return adopted;}
    public long revision(){return revision;}
    public List<Bed> beds(){return beds==null?List.of():Collections.unmodifiableList(beds);}
    public Cargo cargo(){return cargo;}
    public Pending pending(){return pending;}
    public int heldQuantity(){return (cargo==null?0:cargo.quantity())+(remainingCargo==null?0:remainingCargo.stream().mapToInt(Cargo::quantity).sum());}
    /**
     * Hands over everything the Farmer was carrying or had just cut, and forgets it here, so the Scarecrow Fields
     * can put it in her basket exactly once. The beds stay recorded as they were.
     */
    public Map<String,Integer> retire() {
        var held=new LinkedHashMap<String,Integer>();
        if(cargo!=null)held.merge(cargo.output(),cargo.quantity(),Integer::sum);
        if(remainingCargo!=null)for(var part:remainingCargo)held.merge(part.output(),part.quantity(),Integer::sum);
        if(pending!=null) {
            var yields=pending.yields()==null?Map.of(pending.bed().output(),pending.quantity()):pending.yields();
            for(var part:yields.entrySet())held.merge(part.getKey(),part.getValue(),Integer::sum);
        }
        cargo=null;remainingCargo=new ArrayList<>();pending=null;
        return held;
    }
    public int firstBed(){return beds().isEmpty()?0:Math.floorMod(nextBed,beds().size());}
    public void rotateAfter(int index){nextBed=index+1;}

    /** Caller freshly checks the plot, native crop shapes and the player's authority. */
    public String adopt(long expected,List<Bed> reviewed) {
        if(expected!=revision)return "The field changed. Review it again.";
        if(reviewed==null||reviewed.isEmpty()||reviewed.size()>MAX_BEDS)return "Plant between 1 and 75 supported crops inside this building.";
        var points=new HashSet<WarehouseBatch.Point>();
        for(var bed:reviewed)if(bed==null||bed.point()==null||bed.crop()==null||bed.crop().isBlank()
                ||bed.output()==null||bed.output().isBlank()||!points.add(bed.point()))return "These beds need another review.";
        beds=new ArrayList<>(reviewed);adopted=true;revision++;nextBed=0;return null;
    }
    /** Reset a real ripe plant before granting one held harvest. Never credit production stock here. */
    public boolean harvest(Bed bed,int yield,BooleanSupplier regrow) {
        if(!adopted||cargo!=null||pending!=null||yield<1||yield>16||!beds().contains(bed))return false;
        if(!regrow.getAsBoolean())return false;
        cargo=new Cargo(UUID.randomUUID(),bed.output(),yield);return true;
    }
    /** Native crop transitions are deferred. Save a claim and only settle it after observing the resulting block. */
    public boolean beginHarvest(Bed bed,String regrownBlock,BooleanSupplier start) {
        return beginHarvest(bed,regrownBlock,Map.of(bed.output(),1),start);
    }
    public boolean beginHarvest(Bed bed,String regrownBlock,Map<String,Integer> yields,BooleanSupplier start) {
        if(!adopted||cargo!=null||pending!=null||!beds().contains(bed)||regrownBlock==null||regrownBlock.isBlank())return false;
        if(yields==null||yields.isEmpty()||yields.size()>16||yields.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey().isBlank()||e.getValue()==null||e.getValue()<1||e.getValue()>4096))return false;
        var claim=new Pending(UUID.randomUUID(),bed,1,regrownBlock,new LinkedHashMap<>(yields));pending=claim;
        try {if(start.getAsBoolean())return true;}
        catch(RuntimeException failure){pending=null;throw failure;}
        pending=null;return false;
    }
    /** A changed or unchanged ripe crop cancels the claim without payment. Unavailable cells wait for simulation. */
    public boolean settleHarvest(UUID expected,String observedBlock) {
        if(pending==null||!pending.id().equals(expected)||observedBlock==null||cargo!=null)return false;
        if(pending.regrownBlock().equals(observedBlock)) {
            remainingCargo=new ArrayList<>();
            var yields=pending.yields()==null?Map.of(pending.bed().output(),pending.quantity()):pending.yields();
            for(var entry:yields.entrySet())remainingCargo.add(new Cargo(UUID.randomUUID(),entry.getKey(),entry.getValue()));
            cargo=remainingCargo.isEmpty()?null:remainingCargo.removeFirst();
        }
        pending=null;return true;
    }
    /**
     * One trip empties the worker's hands, as at the lumbermill: every carried product is stored up to its own
     * room and whatever does not fit is left behind. A full store can therefore never hold up the field.
     *
     * @return how many items were stored, or -1 when {@code expected} is not the harvest being carried
     */
    public int deliverAll(PlotProductionState production,UUID expected,java.util.function.ToLongFunction<String> capacity) {
        if(cargo==null||!cargo.id().equals(expected))return -1;
        var all=new ArrayList<Cargo>();all.add(cargo);if(remainingCargo!=null)all.addAll(remainingCargo);
        int total=0;
        for(var held:all) {
            long cap=Math.min(capacity.applyAsLong(held.output()),ProductionCatalog.MAX_STORAGE_PER_OUTPUT);
            int count=(int)Math.max(0,Math.min(held.quantity(),cap-production.getAmount(held.output())));
            if(count>0){production.addAmount(held.output(),count,cap);total+=count;}
        }
        cargo=null;remainingCargo=new ArrayList<>();
        return total;
    }
    public int deliver(PlotProductionState production,UUID expected,long capacity) {
        if(cargo==null||!cargo.id().equals(expected))return 0;
        long room=Math.max(0,capacity-production.getAmount(cargo.output()));
        int accepted=(int)Math.min(room,cargo.quantity());if(accepted==0)return 0;
        production.addAmount(cargo.output(),accepted,capacity);
        cargo=accepted==cargo.quantity()?(remainingCargo==null||remainingCargo.isEmpty()?null:remainingCargo.removeFirst())
                :new Cargo(cargo.id(),cargo.output(),cargo.quantity()-accepted);
        return accepted;
    }
}
