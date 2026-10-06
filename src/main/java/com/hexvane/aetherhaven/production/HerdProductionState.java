package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.RanchCare;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Persistent core barn herd. Breeding animals are references only and can never be harvested here. */
public final class HerdProductionState {
    public static final String FEED="Plant_Crop_Wheat_Item";
    public static final int FEED_CAPACITY=64;
    public static final int MAX_FAMILIES=32;
    public static final long FEED_INTERVAL=6*60,NEGLECT=24*60;
    public record Pair(String family,List<UUID> breeders,WarehouseBatch.Point trough) {
        public Pair {breeders=List.copyOf(breeders);}
    }
    public static final class Herd {
        private Pair pair;private RanchCare care=new RanchCare();private long sinceFeed=FEED_INTERVAL;private int nextProduct;private String recoveringOutput;
        private Herd(Pair pair){this.pair=pair;}
        public Pair pair(){return pair;}public RanchCare care(){return care;}
        public String recoveringOutput(){return recoveringOutput;}
        public boolean recovered(UUID animal) {
            if(recoveringOutput==null||animal==null||!animal.equals(care.offspring())||!care.grown()||!care.ready())return false;
            recoveringOutput=null;return true;
        }
        public long sinceFeed(){return Math.max(0,sinceFeed);}public boolean hungry(){return sinceFeed()>=FEED_INTERVAL;}
        public int productIndex(int size){return size<=0?0:Math.floorMod(nextProduct,size);}
    }
    public record Cargo(UUID id,String output,int quantity) {}
    private boolean adopted;private long revision;private int feed;
    private int courierFeedTarget;
    private long feedOrderRevision;
    private Map<String,Herd> herds=new LinkedHashMap<>();private Cargo cargo;
    public boolean adopted(){return adopted;}public long revision(){return revision;}
    public int feed(){return Math.max(0,feed);}public Cargo cargo(){return cargo;}
    public int courierFeedTarget(){return Math.clamp(courierFeedTarget,0,FEED_CAPACITY);}
    public long feedOrderRevision(){return feedOrderRevision;}
    public boolean configureCourierFeed(long expected,int target) {
        if(expected!=feedOrderRevision||!Set.of(0,8,16,32,64).contains(target)||target==courierFeedTarget())return false;
        courierFeedTarget=target;feedOrderRevision++;return true;
    }
    public int courierFeedNeeded(){return adopted?Math.max(0,courierFeedTarget()-feed()):0;}
    /** Caller owns the real cargo and commits its removal in the same world-thread operation. */
    public boolean receiveCourierFeed(int quantity) {
        if(quantity<=0||quantity>courierFeedNeeded())return false;
        feed+=quantity;return true;
    }
    public Map<String,Herd> herds(){return Collections.unmodifiableMap(herds);}
    public boolean supply(BooleanSupplier pay){if(feed()>=FEED_CAPACITY||!pay.getAsBoolean())return false;feed++;return true;}
    public boolean takeFeed(BooleanSupplier give){if(feed()<1||!give.getAsBoolean())return false;feed--;return true;}
    public String adopt(long expected,List<Pair> pairs) {
        if(expected!=revision)return "The herd changed. Review it again.";
        if(pairs==null||pairs.isEmpty())return "Choose a trough with at least one breeding pair.";
        if(pairs.size()>MAX_FAMILIES)return "This barn has too many different herds. Keep at most "+MAX_FAMILIES+" families.";
        var seen=new HashSet<UUID>();var families=new HashSet<String>();
        for(var pair:pairs)if(pair==null||pair.family()==null||pair.family().isBlank()||pair.trough()==null||!families.add(pair.family())||pair.breeders().size()!=2
            ||pair.breeders().stream().anyMatch(id->id==null||!seen.add(id)))return "Each herd needs two different breeding animals.";
        for(var pair:pairs) {var herd=herds.computeIfAbsent(pair.family(),key->new Herd(pair));herd.pair=pair;}
        herds.entrySet().removeIf(e->!families.contains(e.getKey())&&e.getValue().care.offspring()==null);
        adopted=true;revision++;return null;
    }
    public void advance(String family,long minutes,boolean simulated) {
        var herd=herds.get(family);if(!adopted||herd==null||!simulated||minutes<=0)return;
        long step=Math.min(minutes,NEGLECT);
        if(herd.sinceFeed()<NEGLECT)herd.care.advance(Math.min(step,NEGLECT-herd.sinceFeed()));
        herd.sinceFeed=Math.min(NEGLECT,herd.sinceFeed()+step);
    }
    /** Real attended feeding must succeed before spending feed or resetting care. */
    public boolean fed(String family,BooleanSupplier action) {
        var herd=herds.get(family);if(!adopted||herd==null||!herd.hungry()||feed()<1||cargo!=null||!action.getAsBoolean())return false;
        feed--;herd.sinceFeed=0;return true;
    }
    public boolean collect(String family,UUID animal,RanchProducts.Product product,BooleanSupplier action) {
        var herd=herds.get(family);
        if(!adopted||herd==null||cargo!=null||product==null||!family.equals(product.family())||animal==null||!animal.equals(herd.care.offspring())
            ||herd.pair.breeders().contains(animal)||!herd.care.ready()||!herd.care.grown()||herd.sinceFeed()>=NEGLECT||!action.getAsBoolean())return false;
        if(!product.slaughter())herd.care.matured(animal);
        herd.recoveringOutput=!product.slaughter()&&RanchProductAppearance.get(family,product.output())!=null?product.output():null;
        cargo=new Cargo(UUID.randomUUID(),product.output(),product.amount());herd.nextProduct++;return true;
    }
    public int deliver(PlotProductionState production,UUID expected,long capacity) {
        if(cargo==null||!cargo.id().equals(expected))return 0;
        int accepted=(int)Math.min(cargo.quantity(),Math.max(0,capacity-production.getAmount(cargo.output())));if(accepted==0)return 0;
        production.addAmount(cargo.output(),accepted,capacity);
        cargo=accepted==cargo.quantity()?null:new Cargo(cargo.id(),cargo.output(),cargo.quantity()-accepted);return accepted;
    }
    public boolean lost(UUID animal){boolean changed=false;for(var herd:herds.values())if(herd.care.lost(animal)){herd.recoveringOutput=null;changed=true;}return changed;}
}
