package com.hexvane.aetherhaven.hunter;

import java.util.*;

/** Permanent training and run results. Runtime combat never catches up from wall clock time. */
public final class TownHunts implements com.hexvane.aetherhaven.warehouse.ProductionInventory {
    public static final int ENTRY_GOLD=15;
    private Set<String> unlocked=new LinkedHashSet<>();
    private Map<String,Integer> upgrades=new LinkedHashMap<>(), stock=new LinkedHashMap<>();
    private UUID run;
    private List<String> roster=new ArrayList<>();
    private int defeated,best,lastDefeated,runs;
    private String lastResult="No runs yet.";
    private long revision;
    public UUID run() {return run;}
    public long revision() {return revision;}
    public int defeated() {return Math.max(0,defeated);}
    public int best() {return Math.max(0,best);}
    public int lastDefeated() {return Math.max(0,lastDefeated);}
    public int runs() {return Math.max(0,runs);}
    public String lastResult() {return lastResult;}
    public boolean unlocked(String family) {return unlocked.stream().anyMatch(id->HuntFamily.activeId(id).equals(HuntFamily.activeId(family)));}
    public List<String> unlocked() {return unlocked.stream().map(HuntFamily::activeId).distinct().toList();}
    public boolean unlock(String family) {family=HuntFamily.activeId(family);if(run!=null||HuntFamily.get(family)==null||unlocked(family)||!unlocked.add(family))return false;revision++;return true;}
    public int level(String upgrade) {return Math.clamp(upgrades.getOrDefault(upgrade,0),0,maxLevel(upgrade));}
    public static int maxLevel(String upgrade) {return switch(upgrade){case "vitality","power","dressing"->5;case "second_wind"->1;case "storage"->2;default->0;};}
    public int upgradeCost(String upgrade) {return "second_wind".equals(upgrade)?250:50*(level(upgrade)+1);}
    public boolean upgrade(String upgrade) {if(run!=null||level(upgrade)>=maxLevel(upgrade))return false;upgrades.put(upgrade,level(upgrade)+1);revision++;return true;}
    public int maxHealth() {return 260+60*level("vitality");}
    public double damageMultiplier() {return 1+.2*level("power");}
    public int recoveryPerWave() {return 4*level("dressing");}
    public int capacity() {return 32<<level("storage");}
    public Map<String,Integer> stock() {return Collections.unmodifiableMap(stock);}
    public int amount(String item) {return Math.max(0,stock.getOrDefault(item,0));}
    @Override public long getAmount(String item) {return amount(item);}
    @Override public Map<String,Long> getAmounts() {
        Map<String,Long> result=new LinkedHashMap<>();stock.forEach((item,count)->result.put(item,(long)Math.max(0,count)));
        return Collections.unmodifiableMap(result);
    }
    @Override public long removeAmountUpTo(String item,long count) {
        int taken=(int)Math.min(amount(item),Math.max(0,count));return withdraw(item,taken)?taken:0;
    }
    public boolean room(HuntFamily family) {return family!=null&&family.drops().entrySet().stream().allMatch(e->(long)amount(e.getKey())+e.getValue()<=capacity());}
    public UUID begin() {
        if(run!=null)return null;
        roster=new ArrayList<>(unlocked().stream().filter(id->HuntFamily.get(id)!=null).toList());
        if(roster.isEmpty())return null;
        run=UUID.randomUUID();defeated=0;runs++;revision++;return run;
    }
    public HuntFamily next() {return run==null||roster.isEmpty()?null:HuntFamily.get(roster.get(defeated()%roster.size()));}
    /** Called once for the current opponent after its verified defeat and physical collection. */
    public boolean collect(UUID expectedRun,int expectedWave,HuntFamily family) {
        return collect(expectedRun,expectedWave,family,family==null?Map.of():family.drops());
    }
    public boolean collect(UUID expectedRun,int expectedWave,HuntFamily family,Map<String,Integer> spoils) {
        if(expectedRun==null||!expectedRun.equals(run)||expectedWave!=defeated()||family==null||!family.equals(next())||spoils==null
            ||spoils.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null||e.getValue()<1||(long)amount(e.getKey())+e.getValue()>capacity()))return false;
        spoils.forEach((item,count)->stock.put(item,amount(item)+count));
        defeated++;best=Math.max(best,defeated);revision++;return true;
    }
    public void end(String reason) {
        if(run==null)return;
        lastDefeated=defeated();lastResult=reason;run=null;roster.clear();revision++;
    }
    public boolean withdraw(String item,int count) {
        if(count<=0||count>amount(item))return false;
        int remaining=amount(item)-count;if(remaining==0)stock.remove(item);else stock.put(item,remaining);
        revision++;return true;
    }
    public static double enemyHealthScale(int wave) {return Math.min(20,1+Math.max(0,wave)*.15);}
    public static double enemyDamageScale(int wave) {return Math.min(20,1+Math.max(0,wave)*.12);}
}
