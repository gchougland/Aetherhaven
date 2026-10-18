package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import org.joml.Vector3i;

/** A corner clicked on equipment or a crop belongs to the floor beneath its base, not its visible top. */
final class WorkplaceSelectionGround {
    private WorkplaceSelectionGround() {}
    interface Blocks {
        int filler(int x,int y,int z);
        boolean equipmentOrCrop(int x,int y,int z);
    }
    static Vector3i ground(World world,Vector3i hit) {
        return ground(hit,new Blocks() {
            public int filler(int x,int y,int z) { return ChunkSectionBlockUtil.filler(world,x,y,z); }
            public boolean equipmentOrCrop(int x,int y,int z) {
                var block=ChunkSectionBlockUtil.blockType(world,x,y,z);
                return block!=null&&(WorkplaceEquipment.benchId(block)!=null || block.getFarming()!=null
                    || FoodSiteService.isFoodCrop(block.getDefaultStateKey(),block.getId())
                    || AlchemicalCrop.fromBlock(block.getId())!=null);
            }
        });
    }
    static Vector3i ground(Vector3i hit,Blocks blocks) {
        int filler=blocks.filler(hit.x,hit.y,hit.z);
        int x=hit.x-FillerBlockUtil.unpackX(filler),y=hit.y-FillerBlockUtil.unpackY(filler),z=hit.z-FillerBlockUtil.unpackZ(filler);
        // Keep the chosen horizontal corner, including when the player hits the side of a wide bench.
        return new Vector3i(hit.x,blocks.equipmentOrCrop(x,y,z)?y-1:hit.y,hit.z);
    }
}
