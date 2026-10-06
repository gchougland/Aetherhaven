package com.hexvane.aetherhaven.hunter;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/** Persisted tag allows abandoned arena summons to be removed when their chunk loads again. */
public final class HuntOpponent implements Component<EntityStore> {
    public static final BuilderCodec<HuntOpponent> CODEC=BuilderCodec.builder(HuntOpponent.class,HuntOpponent::new)
        .append(new KeyedCodec<>("Town",Codec.UUID_STRING),(v,x)->v.town=x,v->v.town).add()
        .append(new KeyedCodec<>("Encounter",Codec.UUID_STRING),(v,x)->v.encounter=x,v->v.encounter).add()
        .append(new KeyedCodec<>("Hunter",Codec.UUID_STRING),(v,x)->v.hunter=x,v->v.hunter).add()
        .append(new KeyedCodec<>("Portal",Codec.BOOLEAN),(v,x)->v.portal=x,v->v.portal).add()
        .build();
    private static ComponentType<EntityStore,HuntOpponent> type;
    UUID town,encounter,hunter;
    boolean portal;
    public HuntOpponent() {}
    public HuntOpponent(UUID town,UUID encounter,UUID hunter) { this.town=town;this.encounter=encounter;this.hunter=hunter; }
    public static void register(ComponentRegistryProxy<EntityStore> registry) { type=registry.registerComponent(HuntOpponent.class,"AetherhavenHuntOpponent",CODEC); }
    public static ComponentType<EntityStore,HuntOpponent> getComponentType() { return type; }
    @Override public HuntOpponent clone() { var copy=new HuntOpponent(town,encounter,hunter);copy.portal=portal;return copy; }
}
