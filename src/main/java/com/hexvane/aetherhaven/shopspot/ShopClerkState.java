package com.hexvane.aetherhaven.shopspot;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.joml.Vector3d;

/** Disposable navigation only. The plot owns the saved assignment. */
public final class ShopClerkState implements Component<EntityStore> {
    private static ComponentType<EntityStore,ShopClerkState> type;
    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        type=registry.registerComponent(ShopClerkState.class,"AetherhavenShopClerk",
            BuilderCodec.builder(ShopClerkState.class,ShopClerkState::new).build());
    }
    public static ComponentType<EntityStore,ShopClerkState> getComponentType(){return type;}
    public UUID plot;
    public Vector3d approach, entrance;
    public boolean active, attending;
    public long nextCheck, beganAt, retryAt, lastAttendance;
    public int attempt;
    @Override public ShopClerkState clone() {
        var c=new ShopClerkState();c.plot=plot;c.approach=approach==null?null:new Vector3d(approach);
        c.entrance=entrance==null?null:new Vector3d(entrance);
        c.active=active;c.attending=attending;c.nextCheck=nextCheck;c.beganAt=beganAt;c.retryAt=retryAt;
        c.lastAttendance=lastAttendance;c.attempt=attempt;return c;
    }
}
