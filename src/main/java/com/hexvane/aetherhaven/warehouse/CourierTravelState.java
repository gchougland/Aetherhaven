package com.hexvane.aetherhaven.warehouse;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.joml.Vector3d;

/** Navigation is disposable. The actual cargo and destination remain in the town save. */
public final class CourierTravelState implements Component<EntityStore> {
    private static ComponentType<EntityStore,CourierTravelState> type;
    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        type=registry.registerComponent(CourierTravelState.class,"AetherhavenCourierTravel",
            BuilderCodec.builder(CourierTravelState.class,CourierTravelState::new).build());
    }
    public static ComponentType<EntityStore,CourierTravelState> getComponentType() { return type; }
    public UUID batchId;
    public Vector3d approach;
    public WarehouseBatch.Point endpoint;
    public String action;
    public long nextCheck, retryAt, beganAt, arrivedAt;
    public int approachAttempt;
    public boolean active;
    @Override public CourierTravelState clone() {
        var copy=new CourierTravelState();copy.batchId=batchId;copy.approach=approach==null?null:new Vector3d(approach);
        copy.endpoint=endpoint;copy.action=action;copy.nextCheck=nextCheck;copy.retryAt=retryAt;copy.beganAt=beganAt;copy.arrivedAt=arrivedAt;copy.approachAttempt=approachAttempt;copy.active=active;return copy;
    }
}
