package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.*;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Audited core-building equipment, shared by creation, editing and both save barriers. */
public final class CoreBuildingSpecialties {
    public static final String TAG="BUILDING_SPECIALTY";
    private static final Map<String,List<String>> BUILDINGS=load();
    private CoreBuildingSpecialties() {}
    private static Map<String,List<String>> load() {
        try(var in=CoreBuildingSpecialties.class.getClassLoader().getResourceAsStream("Server/Aetherhaven/core_building_specialties.json")) {
            if(in==null)throw new IllegalStateException("Missing core building specialty audit");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            var out=new LinkedHashMap<String,List<String>>();
            for(var entry:root.entrySet())out.put(entry.getKey(),entry.getValue().getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList());
            return Collections.unmodifiableMap(out);
        } catch(java.io.IOException e){throw new IllegalStateException("Cannot load core building specialties",e);}
    }
    public static Map<String,List<String>> auditedBuildings(){return BUILDINGS;}
    public static boolean isSpecialty(String block){return block!=null&&BUILDINGS.values().stream().anyMatch(b->b.contains(block));}
    public static String label(String block) {
        if("Aetherhaven_Hunt_Beacon".equals(block))return "Hunt Beacon and arena preview";
        if("Aetherhaven_Warehouse_Ledger_Desk".equals(block))return "Warehouse Ledger Desk";
        if("Bench_Alchemy".equals(block))return "Alchemist's Workbench";
        if("Bench_Farming".equals(block))return "Farmer's Workbench";
        if("Bench_WorkBench".equals(block))return "Workbench";
        if("Bench_Tannery".equals(block))return "Tanning Rack";
        return block==null?"Specialty block":block.replace("Aetherhaven_","").replace('_',' ');
    }
    public static List<String> required(PlotCreatorDraft draft,AetherhavenPlugin plugin) {
        if(draft.isDecorationOnly()||draft.isFestivalMode()||draft.isPropMode()||draft.isWallMode())return List.of();
        var ids=new HashSet<>(draft.getCountsAsConstructionIds());ids.add(draft.getConstructionId());ids.add(draft.getEditingConstructionId());
        var result=new LinkedHashSet<String>();
        for(var entry:BUILDINGS.entrySet())for(String id:ids)
            if(entry.getKey().equals(id)||id!=null&&plugin!=null&&plugin.getConstructionCatalog().matchesGameplayConstruction(id,entry.getKey()))result.addAll(entry.getValue());
        return List.copyOf(result);
    }
    static void enforce(PlotCreatorDraft draft,AetherhavenPlugin plugin,List<PlotBuildingKindRequirements.SubstepRequirement> steps) {
        for(String block:required(draft,plugin)) {
            steps.removeIf(r->r.type()==PlotCreatorSubstepType.SPECIALTY_BLOCK&&block.equals(r.workResidentKind()));
            steps.add(new PlotBuildingKindRequirements.SubstepRequirement(PlotCreatorSubstepType.SPECIALTY_BLOCK,1,block));
        }
    }
    public static boolean matches(PlotCreatorPoiDraft poi,String block) {
        return poi.getTags().contains(TAG)&&Objects.equals(block,poi.getBlockTypeId());
    }
    /** Re-read actual blocks on editor load and before export: deleted markers cannot satisfy the save barrier. */
    static void refresh(com.hypixel.hytale.server.core.universe.world.World world,PlotCreatorDraft draft,AetherhavenPlugin plugin) {
        var required=required(draft,plugin);if(required.isEmpty())return;
        var min=draft.boundsMin();var max=draft.boundsMax();
        long volume=((long)max.x-min.x+1)*((long)max.y-min.y+1)*((long)max.z-min.z+1);
        var previous = List.copyOf(draft.getPois());
        draft.getPois().removeIf(p->p.getTags().contains(TAG)&&required.contains(p.getBlockTypeId()));
        if(volume<=0||volume>2_000_000)return;
        var found=new HashSet<String>();
        for(int x=min.x;x<=max.x;x++)for(int z=min.z;z<=max.z;z++)for(int y=min.y;y<=max.y;y++) {
            var section=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockSectionAt(world,x,y,z);
            if(section==null||section.getFiller(x,y,z)!=0)continue;
            var block=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world,x,y,z);if(block==null)continue;
            String id=block.getDefaultStateKey()==null?block.getId():block.getDefaultStateKey();
            if(!required.contains(id)||!found.add(id))continue;
            int[] local=PlotCreatorLocalCoords.toLocal(draft,new org.joml.Vector3i(x,y,z));
            var poi=new PlotCreatorPoiDraft();poi.setLocal(local[0],local[1],local[2]);poi.setTags(List.of(TAG));poi.setBlockTypeId(id);poi.setInteractionKind("NONE");preserveFacing(previous,poi);draft.getPois().add(poi);
            if(found.containsAll(required))return;
        }
    }
    /** Revalidation must retain authored facing on the same specialty block. */
    static void preserveFacing(List<PlotCreatorPoiDraft> previous, PlotCreatorPoiDraft found) {
        for (var old : previous) if (matches(old, found.getBlockTypeId())
            && old.getLocalX() == found.getLocalX() && old.getLocalY() == found.getLocalY() && old.getLocalZ() == found.getLocalZ()) {
            found.setInteractionTargetYawDegrees(old.getInteractionTargetYawDegrees());
            return;
        }
    }
    static String validateArena(com.hypixel.hytale.server.core.universe.world.World world,PlotCreatorDraft draft) {
        for(var poi:draft.getPois())if(matches(poi,com.hexvane.aetherhaven.hunter.HuntService.BEACON)) {
            var center=PlotCreatorLocalCoords.toWorldBlock(draft,new int[]{poi.getLocalX(),poi.getLocalY()+1,poi.getLocalZ()});
            for(int x=center.x-4;x<=center.x+4;x++)for(int z=center.z-4;z<=center.z+4;z++)
                if(!draft.isInsideBounds(new org.joml.Vector3i(x,center.y,z))||!com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,x,center.y,z))return "huntArenaRequired";
        }
        return null;
    }
    static String validationError(PlotCreatorDraft draft,AetherhavenPlugin plugin) {
        for(String block:required(draft,plugin))
            if(draft.getPois().stream().noneMatch(p->matches(p,block)&&draft.isInsideBounds(PlotCreatorLocalCoords.toWorldBlock(draft,new int[]{p.getLocalX(),p.getLocalY(),p.getLocalZ()}))))return "specialtyRequired";
        return null;
    }
}
