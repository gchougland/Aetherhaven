package com.hexvane.aetherhaven.production;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;

/** Optional native model attachment changes for living products. Does not spawn item drops. */
public final class RanchProductAppearance {
    private RanchProductAppearance() {}
    public record Appearance(String model,String slot,String ready,String collected,String particle,String sound) {
        public Appearance {
            if(model==null||model.isBlank()||slot==null||slot.isBlank()||ready==null||ready.isBlank()||collected==null)
                throw new IllegalArgumentException("A model, slot and attachment states are required");
        }
    }
    private static final Map<String,Appearance> APPEARANCES=new java.util.concurrent.ConcurrentHashMap<>();
    static {register("Sheep","Ingredient_Fabric_Scrap_Wool",new Appearance("Sheep","Fleece","Wool","","Sheep_Wool_Shear","SFX_Sheep_Sheared"));}
    public static void register(String family,String output,Appearance appearance) {
        if(family==null||family.isBlank()||output==null||output.isBlank())throw new IllegalArgumentException("Family and output are required");
        Objects.requireNonNull(appearance);var previous=APPEARANCES.putIfAbsent(family+"/"+output,appearance);
        if(previous!=null&&!previous.equals(appearance))throw new IllegalArgumentException("Ranch appearance already registered");
    }
    public static Appearance get(String family,String output){return APPEARANCES.get(family+"/"+output);}
    public static Map<String,String> attachments(Map<String,String> original,Appearance appearance,boolean ready) {
        var result=new HashMap<String,String>();if(original!=null)result.putAll(original);
        String value=ready?appearance.ready():appearance.collected();
        if(value.isEmpty())result.remove(appearance.slot());else result.put(appearance.slot(),value);
        return result;
    }
    /** Uses the same model attachment reconstruction as the native livestock action, and persists it. */
    public static boolean apply(Ref<EntityStore> ref,Store<EntityStore> store,Appearance appearance,boolean ready) {
        var component=store.getComponent(ref,ModelComponent.getComponentType());
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(component==null||npc==null)return false;var old=component.getModel();
        if(!appearance.model().equals(old.getModelAssetId()))return false;
        var asset=ModelAsset.getAssetMap().getAsset(old.getModelAssetId());if(asset==null)return false;
        var attachments=attachments(old.getRandomAttachmentIds(),appearance,ready);
        if(Objects.equals(attachments,old.getRandomAttachmentIds()))return true;
        var model=Model.createScaledModel(asset,old.getScale(),attachments);
        store.putComponent(ref,ModelComponent.getComponentType(),new ModelComponent(model));
        store.putComponent(ref,PersistentModel.getComponentType(),new PersistentModel(model.toReference()));
        if(npc.getRole()!=null)npc.getRole().updateMotionControllers(ref,model,model.getBoundingBox(),store);
        return true;
    }
    public static void effects(Ref<EntityStore> ref,Store<EntityStore> store,Appearance appearance) {
        var transform=store.getComponent(ref,TransformComponent.getComponentType());if(transform==null)return;
        var p=transform.getPosition();
        if(appearance.particle()!=null&&!appearance.particle().isBlank())ParticleUtil.spawnParticleEffect(appearance.particle(),p,store);
        if(appearance.sound()!=null) {
            int sound=SoundEvent.getAssetMap().getIndex(appearance.sound());
            if(sound>=0)SoundUtil.playSoundEvent3d(sound,SoundCategory.SFX,p.x,p.y+.5,p.z,1f,1f,store);
        }
    }
}
