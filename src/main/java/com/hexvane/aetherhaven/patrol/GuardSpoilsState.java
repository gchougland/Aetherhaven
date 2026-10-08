package com.hexvane.aetherhaven.patrol;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
public final class GuardSpoilsState implements Component<EntityStore> {
    private static ComponentType<EntityStore,GuardSpoilsState> type;
    long nextScan,safeAfter,started,retryAfter;
    Ref<EntityStore> target;
    Vector3d destination;
    boolean active;
    public static void register(ComponentRegistryProxy<EntityStore> registry){type=registry.registerComponent(GuardSpoilsState.class,GuardSpoilsState::new);}
    public static ComponentType<EntityStore,GuardSpoilsState> getComponentType(){return type;}
    @Override public GuardSpoilsState clone(){var c=new GuardSpoilsState();c.nextScan=nextScan;c.safeAfter=safeAfter;c.started=started;c.retryAfter=retryAfter;c.target=target;c.destination=destination==null?null:new Vector3d(destination);c.active=active;return c;}
}
