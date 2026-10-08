package com.hexvane.aetherhaven.production;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Runtime display only. Ownership and care are saved in the town, so chunk reload cannot duplicate stock. */
public final class BarnAnimalBinding implements Component<EntityStore> {
    private static ComponentType<EntityStore,BarnAnimalBinding> TYPE;
    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        if(TYPE==null)TYPE=registry.registerComponent(BarnAnimalBinding.class,()->new BarnAnimalBinding(null,null,null));
    }
    public static ComponentType<EntityStore,BarnAnimalBinding> getComponentType(){return TYPE;}
    public final UUID town,plot,animal;
    public BarnAnimalBinding(UUID town,UUID plot,UUID animal){this.town=town;this.plot=plot;this.animal=animal;}
    @Override public Component<EntityStore> clone(){return new BarnAnimalBinding(town,plot,animal);}
}
