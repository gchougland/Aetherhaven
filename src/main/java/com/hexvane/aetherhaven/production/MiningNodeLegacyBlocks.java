package com.hexvane.aetherhaven.production;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.migrations.ChunkSectionMigrationSystem;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.function.IntUnaryOperator;

/** Convert removed preview block IDs before a loaded section is sent to players. */
public final class MiningNodeLegacyBlocks extends ChunkSectionMigrationSystem {
    @Override public Query<ChunkStore> getQuery(){return BlockSection.getComponentType();}
    @Override public void onEntityAdd(Holder<ChunkStore> holder,AddReason reason,Store<ChunkStore> store){
        var section=holder.getComponent(BlockSection.getComponentType());
        int changed=convert(section,old->{
            var type=BlockType.getAssetMap().getAsset(old);
            if(type==null||!type.getId().startsWith(MiningNodeBlocks.PREFIX))return old;
            String nativeId=MiningNodeBlocks.nativeOwned(type.getId());
            return MiningNodeBlocks.safeOriginal(BlockType.getAssetMap().getAsset(nativeId))?BlockType.getAssetMap().getIndex(nativeId):old;
        });
        if(changed>0){
            var chunk=holder.getComponent(ChunkSection.getComponentType());if(chunk!=null)chunk.markNeedsSaving();
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Converted %s retired mining blocks to native blocks in a loaded section",changed);
        }
    }
    @Override public void onEntityRemoved(Holder<ChunkStore> holder,RemoveReason reason,Store<ChunkStore> store){}
    static int convert(BlockSection section,IntUnaryOperator replacement){
        int changed=0;
        // Inspect palette entries once, not every block in every ordinary loaded section.
        var palette=new IntArrayList(section.values());
        for(int old:palette){
            int next=replacement.applyAsInt(old);if(next==old||next<0)continue;
            var cells=new IntArrayList();section.find(IntArrayList.of(old),(java.util.function.IntConsumer)cells::add);
            for(int cell:cells)if(section.get(cell)==old&&section.getFiller(cell)==0){
                if(section.set(cell,next,section.getRotationIndex(cell),0))changed++;
            }
        }
        return changed;
    }
}
