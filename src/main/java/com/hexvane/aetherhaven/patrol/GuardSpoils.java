package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.*;

/** A temporary ground-item tag prevents other kills and player drops from merging into claimed spoils. */
public final class GuardSpoils {
    private static final String KEY="AetherhavenGuardSpoils";
    private GuardSpoils(){}
    public static String owner(ItemStack stack){return ItemStack.isEmpty(stack)?null:stack.getFromMetadataOrNull(KEY,Codec.STRING);}
    public static ItemStack mark(ItemStack stack,String owner){return stack.withMetadata(KEY,new BsonString(owner));}
    public static ItemStack clean(ItemStack stack){
        if(owner(stack)==null)return stack;var clean=stack.withMetadata(KEY,(BsonValue)null);
        var metadata=com.hexvane.aetherhaven.inventory.ItemStackData.metadata(clean);
        return metadata!=null&&metadata.isEmpty()?clean.withMetadata((BsonDocument)null):clean;
    }
    public static HiredGuardRecord guard(TownRecord town,java.util.UUID uuid){return town==null?null:town.getHiredGuardRecords().stream().filter(g->uuid.equals(g.getEntityUuid())).findFirst().orElse(null);}
    public static String owner(TownRecord town,HiredGuardRecord guard){return town.getTownId()+":"+guard.getCharacterId();}
    public static TownRecord town(Ref<EntityStore> ref,Store<EntityStore> store){
        var binding=store.getComponent(ref,TownVillagerBinding.getComponentType());
        if(binding==null||!TownVillagerBinding.KIND_GUARD.equals(binding.getKind()))return null;
        return AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),AetherhavenPlugin.get()).getTown(binding.getTownId());
    }
}
