package com.hexvane.aetherhaven.warehouse;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.town.*;
import java.util.*;

/** Discover only loaded desk block entities, never scan or load the town's terrain. */
public final class WarehouseDeskBlock implements Component<ChunkStore> {
    public static final BuilderCodec<WarehouseDeskBlock> CODEC=BuilderCodec.builder(WarehouseDeskBlock.class,WarehouseDeskBlock::new).build();
    private static ComponentType<ChunkStore,WarehouseDeskBlock> type;
    public static void register(ComponentRegistryProxy<ChunkStore> registry) {
        type=registry.registerComponent(WarehouseDeskBlock.class,"AetherhavenWarehouseDesk",CODEC);
    }
    @Override public WarehouseDeskBlock clone() { return new WarehouseDeskBlock(); }
    public static List<WarehouseBatch.Point> loaded(World world,TownManager manager,TownRecord town) {
        List<WarehouseBatch.Point> points=new ArrayList<>();
        world.getChunkStore().getStore().forEachChunk(Query.and(type,BlockModule.BlockStateInfo.getComponentType()),(chunk,buffer)->{
            for(int i=0;i<chunk.size();i++) {
                var info=chunk.getComponent(i,BlockModule.BlockStateInfo.getComponentType());
                var section=buffer.getComponent(info.getSectionRef(),ChunkSection.getComponentType());
                if(section==null) continue;
                var columnRef=section.getChunkColumnReference();
                var column=columnRef!=null&&columnRef.isValid()?buffer.getComponent(columnRef,WorldChunk.getComponentType()):null;
                if(column==null||ChunkSectionBlockUtil.worldChunkIfTicking(world,ChunkUtil.indexChunk(column.getX(),column.getZ()))==null) continue;
                int index=info.getIndex();
                int x=column.getX()*32+ChunkUtil.xFromIndex(index), y=ChunkUtil.worldCoordFromLocalCoord(section.getY(),ChunkUtil.yFromIndex(index)), z=column.getZ()*32+ChunkUtil.zFromIndex(index);
                var owner=manager.findTownContainingBlock(world.getName(),x,z);
                var block=ChunkSectionBlockUtil.blockType(world,x,y,z);
                if(owner!=null&&owner.getTownId().equals(town.getTownId())&&block!=null&&WarehouseAccess.DESK_ITEM.equals(block.getId())) points.add(new WarehouseBatch.Point(x,y,z));
            }
        });
        return List.copyOf(points);
    }
}
