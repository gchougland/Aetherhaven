package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.ProductionInventory;
import java.util.*;

/** One physical output inventory per base. Saved tunnel ledgers remain compatible with older worlds. */
public final class MineshaftOutput implements ProductionInventory {
    private final TownWorkplaces sites;
    private final UUID base;
    private MineshaftOutput(TownWorkplaces sites,UUID base){this.sites=sites;this.base=base;}
    public static MineshaftOutput find(TownRecord town,UUID id){
        var sites=town.getApprenticeWorkplaces();var site=sites.find(id);
        return site!=null&&site.type()==TownsfolkWorkType.MINING&&site.mineBaseId()==null?new MineshaftOutput(sites,id):null;
    }
    @Override public Map<String,Long> getAmounts(){
        var result=new TreeMap<String,Long>();
        for(var site:ledgers())site.excavation().output().forEach((item,count)->result.merge(item,(long)count,Long::sum));
        return Collections.unmodifiableMap(result);
    }
    @Override public long getAmount(String item){return getAmounts().getOrDefault(item,0L);}
    @Override public long removeAmountUpTo(String item,long count){
        long left=Math.max(0,count);
        for(var site:ledgers()){left-=site.excavation().withdrawOutput(item,left);if(left==0)break;}
        return Math.max(0,count)-left;
    }
    private List<ApprenticeWorkplace> ledgers(){
        var result=new ArrayList<>(sites.tunnels(base));var home=sites.find(base);
        if(home!=null&&!result.contains(home))result.addFirst(home);
        return result;
    }
}
