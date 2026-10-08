package com.hexvane.aetherhaven.warehouse;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.window.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.player.windows.MaterialExtraResourcesSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public interface WarehouseLinkedWindow {
    final class Simple extends SimpleCraftingWindow implements WarehouseLinkedWindow {
        private final WarehouseSupplyLink link;
        public Simple(int x,int y,int z,int rotation,BlockType block,BenchBlock bench,WarehouseSupplyLink link) {
            super(x,y,z,rotation,block,bench);this.link=link;
        }
        @Override public boolean onOpen0(Ref<EntityStore> ref,Store<EntityStore> store) {
            if(!super.onOpen0(ref,store))return false;
            getExtraResourcesSection();return true;
        }
        @Override public MaterialExtraResourcesSection getExtraResourcesSection() {
            return link.append(super.getExtraResourcesSection(),blockType,getBenchTierLevel());
        }
    }
    final class Diagram extends DiagramCraftingWindow implements WarehouseLinkedWindow {
        private final WarehouseSupplyLink link;
        public Diagram(Ref<EntityStore> ref,ComponentAccessor<EntityStore> accessor,int x,int y,int z,int rotation,BlockType block,BenchBlock bench,WarehouseSupplyLink link) {
            super(ref,accessor,x,y,z,rotation,block,bench);this.link=link;
        }
        @Override public boolean onOpen0(Ref<EntityStore> ref,Store<EntityStore> store) {
            if(!super.onOpen0(ref,store))return false;
            getExtraResourcesSection();return true;
        }
        @Override public MaterialExtraResourcesSection getExtraResourcesSection() {
            return link.append(super.getExtraResourcesSection(),blockType,getBenchTierLevel());
        }
    }
    final class Structural extends StructuralCraftingWindow implements WarehouseLinkedWindow {
        private final WarehouseSupplyLink link;
        public Structural(int x,int y,int z,int rotation,BlockType block,BenchBlock bench,WarehouseSupplyLink link) {
            super(x,y,z,rotation,block,bench);this.link=link;
        }
        @Override public boolean onOpen0(Ref<EntityStore> ref,Store<EntityStore> store) {
            if(!super.onOpen0(ref,store))return false;
            getExtraResourcesSection();return true;
        }
        @Override public MaterialExtraResourcesSection getExtraResourcesSection() {
            return link.append(super.getExtraResourcesSection(),blockType,getBenchTierLevel());
        }
    }
}
