package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Persistent owned animals. Slots hold identities, never breeding pairs or harvested entities. */
public final class HerdProductionState {
    public static final String FEED="Plant_Crop_Wheat_Item";
    public static final int FEED_CAPACITY=75, MAX_OWNED=75, MAX_SLOTS=5;
    public static final int MAX_HEARTS=5, POINTS_PER_HEART=100, FEED_POINTS=20, PET_POINTS=5;
    public static final long DAY_MINUTES=1440, PRODUCT_MINUTES=360, POOP_MINUTES=1440;
    public static final class Animal {
        private UUID id; private String species,name; private int friendship;
        private long fedDay=Long.MIN_VALUE, pettedDay=Long.MIN_VALUE;
        private long productMinutes,poopMinutes;
        private Map<String,Integer> yieldRemainders=new HashMap<>();
        private Animal(String species) { id=UUID.randomUUID();this.species=species;name=species; }
        public UUID id(){return id;} public String species(){return species;}
        public String name(){return name==null||name.isBlank()?species:name;}
        public int friendship(){return Math.clamp(friendship,0,MAX_HEARTS*POINTS_PER_HEART);}
        public int hearts(){return friendship()/POINTS_PER_HEART;}
        public boolean fed(long day){return fedDay==day;} public boolean petted(long day){return pettedDay==day;}
        public double yieldMultiplier(){return 1.0+hearts()*.2;}
    }
    private long revision; private int feed,courierFeedTarget=10;
    private Map<String,Integer> foodBin=new LinkedHashMap<>(); private long feedOrderRevision;
    private List<Animal> animals=new ArrayList<>(); private UUID[] slots=new UUID[MAX_SLOTS];
    private long lastSimulationMinute=Long.MIN_VALUE;
    // Retain any already paid, carried output from the retired development system for one-time delivery.
    public record Cargo(UUID id,String output,int quantity) {}
    private Cargo cargo;
    public Cargo cargo(){return cargo;}
    public boolean adopted(){return true;} public long revision(){return revision;}
    public List<Animal> animals(){return Collections.unmodifiableList(animals);}
    public Animal animal(UUID id){return id==null?null:animals.stream().filter(a->id.equals(a.id)).findFirst().orElse(null);}
    public Animal slot(int slot){return slot>=0&&slot<MAX_SLOTS?animal(slots[slot]):null;}
    public int assignedSlot(UUID id){for(int i=0;i<MAX_SLOTS;i++)if(id!=null&&id.equals(slots[i]))return i;return -1;}
    public String buy(long expected,String species,int mastery,BooleanSupplier pay) {
        var kind=BarnAnimalCatalog.get(species);
        if(expected!=revision)return "The animal list changed. Choose again.";
        if(kind==null)return "Choose an animal from the shop.";
        if(mastery<kind.rarity().mastery)return "Requires Rancher Mastery "+kind.rarity().mastery+".";
        if(animals.size()>=MAX_OWNED)return "This barn already owns 75 animals.";
        if(!pay.getAsBoolean())return "You need "+kind.rarity().gold+" gold.";
        animals.add(new Animal(species));revision++;return null;
    }
    public boolean assign(long expected,int slot,UUID id,int unlocked) {
        if(expected!=revision||slot<0||slot>=Math.min(MAX_SLOTS,unlocked)||id!=null&&animal(id)==null)return false;
        if(Objects.equals(slots[slot],id))return false;
        // One animal can occupy only one pen. Choosing an already active animal swaps the two occupants.
        int prior=assignedSlot(id);if(prior>=0)slots[prior]=slots[slot];
        slots[slot]=id;revision++;return true;
    }
    public boolean rename(long expected,UUID id,String name) {
        var animal=animal(id);if(expected!=revision||animal==null||name==null)return false;
        String clean=name.strip().replaceAll("[\\p{Cntrl}<>]", "");
        if(clean.isEmpty()||clean.codePointCount(0,clean.length())>24)return false;
        if(clean.equals(animal.name()))return false;
        animal.name=clean;revision++;return true;
    }
    private void migrateFood() {
        if(foodBin==null)foodBin=new LinkedHashMap<>();
        if(feed>0){foodBin.merge(FEED,feed,Integer::sum);feed=0;}
    }
    public Map<String,Integer> foods(){migrateFood();return Collections.unmodifiableMap(foodBin);}
    public int feed(String item){migrateFood();return Math.max(0,foodBin.getOrDefault(item,0));}
    public boolean supply(String item,BooleanSupplier pay){return supply(item,1,amount->pay.getAsBoolean())>0;}
    /** Charge only the amount that fits, and stock nothing if inventory payment fails. */
    public int supply(String item,int available,java.util.function.IntPredicate pay){
        if(!BarnAnimalCatalog.food(item)||available<=0)return 0;
        int amount=Math.min(available,FEED_CAPACITY-feed(item));
        if(amount<=0||!pay.test(amount))return 0;
        foodBin.merge(item,amount,Integer::sum);return amount;
    }
    public boolean takeFeed(String item,BooleanSupplier give){if(feed(item)<1||!give.getAsBoolean())return false;foodBin.put(item,feed(item)-1);return true;}
    public int courierFeedTarget(){return courierFeedTarget<=0?0:courierFeedTarget<=10?10:courierFeedTarget<=25?25:courierFeedTarget<=50?50:75;}
    public long feedOrderRevision(){return feedOrderRevision;}
    public boolean configureCourierFeed(long expected,int target) {
        if(expected!=feedOrderRevision||!Set.of(0,10,25,50,75).contains(target)||target==courierFeedTarget())return false;
        courierFeedTarget=target;feedOrderRevision++;return true;
    }
    public Set<String> requestedFoods(int unlocked) {
        var result=new LinkedHashSet<String>();
        for(int i=0;i<Math.min(MAX_SLOTS,unlocked);i++){var animal=slot(i);var species=animal==null?null:BarnAnimalCatalog.get(animal.species());if(species!=null)result.add(species.food());}
        return result;
    }
    public int courierFeedNeeded(String item){return BarnAnimalCatalog.food(item)?Math.max(0,courierFeedTarget()-feed(item)):0;}
    public boolean receiveCourierFeed(String item,int quantity){if(quantity<=0||quantity>courierFeedNeeded(item))return false;foodBin.merge(item,quantity,Integer::sum);return true;}
    public boolean feedAnimal(UUID id,long day,int unlocked) {
        var animal=animal(id);int slot=assignedSlot(id);
        var species=animal==null?null:BarnAnimalCatalog.get(animal.species());
        if(species==null||slot<0||slot>=unlocked||animal.fedDay>=day||feed(species.food())<1)return false;
        foodBin.put(species.food(),feed(species.food())-1);animal.fedDay=day;animal.friendship=Math.min(MAX_HEARTS*POINTS_PER_HEART,animal.friendship()+FEED_POINTS);return true;
    }
    public boolean pet(UUID id,long day,int unlocked) {
        var animal=animal(id);int slot=assignedSlot(id);
        if(animal==null||slot<0||slot>=unlocked||animal.pettedDay>=day)return false;
        animal.pettedDay=day;animal.friendship=Math.min(MAX_HEARTS*POINTS_PER_HEART,animal.friendship()+PET_POINTS);return true;
    }
    /** Count only elapsed active, loaded, fed time. The absolute clock prevents repeated accrual and clock rewind rewards. */
    public boolean advance(long minute,int unlocked,Set<UUID> present,PlotProductionState stock,long capacity,double speed) {
        if(lastSimulationMinute==Long.MIN_VALUE){lastSimulationMinute=minute;return true;}
        if(minute<=lastSimulationMinute)return false;
        long from=lastSimulationMinute;lastSimulationMinute=minute;
        // Unloaded time is never credited by a later scan. Normal economy callbacks arrive every game minute.
        from=Math.max(from,minute-1);
        for(int i=0;i<Math.min(MAX_SLOTS,unlocked);i++) {
            var a=slot(i);if(a==null||!present.contains(a.id))continue;
            var kind=BarnAnimalCatalog.get(a.species);if(kind==null)continue;
            long day=Math.floorDiv(from,DAY_MINUTES);
            if(!a.fed(day))continue;
            long elapsed=Math.max(0,Math.min(minute,(day+1)*DAY_MINUTES)-from);
            if(elapsed==0)continue;
            a.productMinutes+=elapsed; a.poopMinutes+=elapsed;
            long interval=Math.max(1,(long)Math.ceil(PRODUCT_MINUTES/Math.max(1,speed)));
            if(a.productMinutes>=interval) {
                // All outputs share a cycle; preflight the whole bundle so full storage cannot erase a product.
                var output=new LinkedHashMap<String,Integer>();
                if(a.yieldRemainders==null)a.yieldRemainders=new HashMap<>();
                kind.products().forEach((item,qty)->output.put(item,
                    qty+(qty*a.hearts()+a.yieldRemainders.getOrDefault(item,0))/5));
                if(output.entrySet().stream().allMatch(e->stock.getAmount(e.getKey())<=capacity-e.getValue())) {
                    output.forEach((item,qty)->stock.addAmount(item,qty,capacity));
                    kind.products().forEach((item,qty)->a.yieldRemainders.put(item,
                        (qty*a.hearts()+a.yieldRemainders.getOrDefault(item,0))%5));
                    a.productMinutes-=interval;
                }else a.productMinutes=interval;
            }
            if(a.poopMinutes>=POOP_MINUTES) {
                if(stock.getAmount("Ingredient_Poop")<capacity){stock.addAmount("Ingredient_Poop",1,capacity);a.poopMinutes-=POOP_MINUTES;}
                else a.poopMinutes=POOP_MINUTES;
            }
        }
        if(cargo!=null&&cargo.quantity()>0&&BarnAnimalCatalog.output(cargo.output())) {
            int accepted=(int)Math.min(cargo.quantity(),Math.max(0,capacity-stock.getAmount(cargo.output())));
            if(accepted>0){stock.addAmount(cargo.output(),accepted,capacity);cargo=accepted==cargo.quantity()?null:new Cargo(cargo.id(),cargo.output(),cargo.quantity()-accepted);}
        }
        return true;
    }
    /** The animal remains owned if its temporary world representation disappears. */
    public boolean lost(UUID animal){return false;}
}
