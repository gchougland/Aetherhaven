package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.item.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import org.joml.Vector3d;

/** Noncollectible native item display in the disposable preview, with no inventory changes. */
final class SpecialistArtPreview {
    private static final List<String> ITEMS=List.of("Alchemist_Kit","Botanist_Kit","Cook_Kit","Courier_Kit",
        "CourierPack_Kit","Crafter_Kit","Field_Ledger","Jeweler_Kit","Leatherworker_Kit","MasterSmith_Kit","Rancher_Kit","Smith_Kit");
    private Ref<EntityStore> display;
    private int next;

    String showNext(World world,Vector3d viewpoint) {
        if(!world.getName().startsWith("aetherhaven_specialist_preview_")||PersistentWorldSupport.shouldPersistWorldData(world))
            throw new IllegalStateException("Use the unsaved specialist preview.");
        var position=new Vector3d(viewpoint).add(0,0,-3);
        if(!VillagerBlockUtil.isNpcStandColumn(world,(int)Math.floor(position.x),(int)Math.floor(position.y),(int)Math.floor(position.z)))
            throw new IllegalStateException("The display position is not clear and supported.");
        String item="Aetherhaven_"+ITEMS.get(next);
        if(Item.getAssetMap().getAsset(item)==null)throw new IllegalStateException("Missing preview item "+item);
        var store=world.getEntityStore().getStore();var holder=store.getRegistry().newHolder();
        var rotation=new Rotation3f(0,(float)(Math.PI/4),0);
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(),NonSerialized.get());
        holder.addComponent(NetworkId.getComponentType(),new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.addComponent(TransformComponent.getComponentType(),new TransformComponent(position.add(0,1,0),rotation));
        holder.addComponent(HeadRotation.getComponentType(),new HeadRotation(rotation));
        holder.addComponent(EntityScaleComponent.getComponentType(),new EntityScaleComponent(1));
        holder.addComponent(Intangible.getComponentType(),Intangible.INSTANCE);
        holder.addComponent(PropComponent.getComponentType(),PropComponent.get());
        holder.addComponent(ItemComponent.getComponentType(),new ItemComponent(new ItemStack(item,1)));
        holder.addComponent(PreventPickup.getComponentType(),PreventPickup.INSTANCE);
        holder.addComponent(PreventItemMerging.getComponentType(),PreventItemMerging.INSTANCE);
        if(display!=null&&display.isValid())store.removeEntity(display,RemoveReason.REMOVE);
        display=store.addEntity(holder,AddReason.SPAWN);
        next=(next+1)%ITEMS.size();
        return "Preview item "+item+" at "+position+". Use kitart for the next model. No items were granted.";
    }
}
