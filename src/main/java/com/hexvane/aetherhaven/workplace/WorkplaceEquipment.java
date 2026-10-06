package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.*;

/** On-demand read of already simulated terrain. Never requests or promotes chunks. */
public final class WorkplaceEquipment {
    private WorkplaceEquipment() {}
    public static final String BUILDERS_BENCH="Builders";
    public static final String JEWELRY_BENCH="Aetherhaven_Jewelry_Crafting_Bench";
    public static final String APPRAISAL_BENCH="Aetherhaven_Appraisal_Bench";
    /** Decorative craft surfaces remain usable by Apprentices without exposing a player crafting window. */
    public static String benchId(com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType block) {
        if(block==null)return null;
        if(JEWELRY_BENCH.equals(block.getId())||APPRAISAL_BENCH.equals(block.getId()))return block.getId();
        return block.getBench()==null?null:block.getBench().getId();
    }
    public record Bench(String id, int tier, int x, int y, int z, Set<String> augmentTags) {
        public Bench(String id,int tier,int x,int y,int z) {this(id,tier,x,y,z,Set.of());}
        public Bench { augmentTags=Set.copyOf(augmentTags); }
    }
    public record Snapshot(boolean fullySimulated, List<Bench> benches, List<WorkplaceFurnishing> furnishings, int memoriesLevel) {
        public Snapshot(boolean fullySimulated,List<Bench> benches) {this(fullySimulated,benches,List.of(),1);}
        public Snapshot(boolean fullySimulated,List<Bench> benches,List<WorkplaceFurnishing> furnishings) {this(fullySimulated,benches,furnishings,1);}
        public Snapshot { benches = List.copyOf(benches); furnishings=List.copyOf(furnishings); }
        public double workMultiplier(com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType type) {
            // Highest applicable furnishing wins, even across different pack supplied blocks.
            return 1+(fullySimulated?furnishings.stream().filter(f->f.valid()&&f.type()==type)
                .mapToDouble(WorkplaceFurnishing::workSpeedBonus).max().orElse(0):0);
        }
        public String furnishingSummary(com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType type) {
            return furnishings.stream().filter(f->f.type()==type).map(WorkplaceFurnishing::name).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        }
        public int tier(String id) { return benches.stream().filter(b -> id.equals(b.id())).mapToInt(Bench::tier).max().orElse(0); }
        public String requirement(String id, int tier) {
            if (!fullySimulated) return "Visit the whole workplace to check its equipment.";
            return tier(id) >= tier ? null : "Needs " + label(id) + " tier " + tier + " inside the workplace.";
        }
    }
    public static Snapshot scan(World world, WorkplaceArea area) {
        if (area == null || !area.valid()) return new Snapshot(false, List.of());
        var benches = new ArrayList<Bench>();var furnishings=new ArrayList<WorkplaceFurnishing>(); boolean loaded = true;
        var plugin=com.hexvane.aetherhaven.AetherhavenPlugin.get();
        var catalog=plugin==null?null:plugin.getWorkplaceFurnishings();
        var store = world.getChunkStore().getStore();
        for (int cx = ChunkUtil.chunkCoordinate(area.minX()); cx <= ChunkUtil.chunkCoordinate(area.maxX()-1); cx++)
            for (int cz = ChunkUtil.chunkCoordinate(area.minZ()); cz <= ChunkUtil.chunkCoordinate(area.maxZ()-1); cz++) {
                if (ChunkSectionBlockUtil.worldChunkIfTicking(world, ChunkUtil.indexChunk(cx,cz)) == null) { loaded=false; continue; }
                int x0=Math.max(area.minX(),cx*32), x1=Math.min(area.maxX(),cx*32+32);
                int z0=Math.max(area.minZ(),cz*32), z1=Math.min(area.maxZ(),cz*32+32);
                for (int x=x0;x<x1;x++) for (int z=z0;z<z1;z++) for(int y=area.floorY();y<area.floorY()+WorkplaceArea.HEIGHT;y++) {
                    var section = ChunkSectionBlockUtil.blockSectionAt(world,x,y,z);
                    if (section == null || section.getFiller(x,y,z) != 0) continue;
                    var block = ChunkSectionBlockUtil.blockType(world,x,y,z); if (block == null) continue;
                    var furnishing=catalog==null?null:catalog.find(block.getId());
                    if(furnishing!=null)furnishings.add(furnishing);
                    var bench = benchId(block);
                    if (bench == null) continue;
                    var ref = ChunkSectionBlockUtil.blockEntityRefAt(world,x,y,z);
                    var state = ref != null && ref.isValid() ? store.getComponent(ref,BenchBlock.getComponentType()) : null;
                    if(state!=null)BenchBlock.refreshGrantedAugmentTags(store,ref,new org.joml.Vector3i(x,y,z));
                    benches.add(new Bench(bench,state == null ? 1 : Math.max(1,state.getTierLevel()),x,y,z,
                        state==null?Set.of():state.getGrantedAugmentTags()));
                }
            }
        return new Snapshot(loaded,benches,furnishings,memoriesLevel(world));
    }
    public static int memoriesLevel(World world) {
        var plugin=com.hypixel.hytale.builtin.adventure.memories.MemoriesPlugin.get();
        return world==null||plugin==null?1:plugin.getMemoriesLevel(world.getGameplayConfig());
    }
    public static String label(String id) {
        return switch(id) {
            case "Furnace" -> "Furnace";
            case "Weapon_Bench" -> "Blacksmith's Anvil";
            case "Armor_Bench" -> "Armorer's Workbench";
            case "Workbench" -> "Workbench";
            case BUILDERS_BENCH -> "Builder's Workbench";
            case "Tannery" -> "Tanning Rack";
            case "Cookingbench" -> "Chef's Stove";
            case "Alchemybench" -> "Alchemist's Workbench";
            case JEWELRY_BENCH -> "Jewelry Crafting Bench";
            case APPRAISAL_BENCH -> "Appraisal Bench";
            case "Farmingbench" -> "Farmer's Workbench";
            default -> id.replace('_',' ');
        };
    }
}
