package com.hexvane.aetherhaven.workplace;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.joml.Vector3d;

/** Disposable movement and animation state. Recipe progress is held in the saved batch. */
public final class ApprenticeWorkState implements Component<EntityStore> {
    private static ComponentType<EntityStore,ApprenticeWorkState> type;
    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        type=registry.registerComponent(ApprenticeWorkState.class,"AetherhavenApprenticeWork",
            BuilderCodec.builder(ApprenticeWorkState.class,ApprenticeWorkState::new).build());
    }
    public static ComponentType<EntityStore,ApprenticeWorkState> getComponentType(){return type;}
    public UUID batch;
    public UUID foodSite;
    public UUID growerSite;
    public com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point growerBed;
    public GrowerSite.Job growerJob;
    public UUID buildingSite;
    public transient NativeBuildingWork.Target buildingTarget;
    public UUID excavationSite,excavationPlan;
    public com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point excavationTarget;
    public boolean excavationDelivery,excavationCarrying,excavationPickup;
    public long excavationGeneration;
    public Vector3d approach;
    public boolean active,working,foodHarvest,foodPlanting;
    public long nextDecision,beganAt,retryAt,nextVisual,nextSave;
    public WorkplaceRouteRetries routeRetries=new WorkplaceRouteRetries();
    public UUID furnishingSite;
    public WorkplaceArea furnishingArea;
    public long nextFurnishingCheck;
    public WorkplaceEquipment.Snapshot furnishingEquipment;
    /** A builder fetching a loose item off a site (P47). Runtime only, like the rest of this state. */
    public transient Ref<EntityStore> salvageTarget;
    public long nextSalvage,salvageHold;
    @Override public ApprenticeWorkState clone() {
        var copy=new ApprenticeWorkState();copy.batch=batch;copy.approach=approach==null?null:new Vector3d(approach);
        copy.excavationSite=excavationSite;copy.excavationPlan=excavationPlan;copy.excavationTarget=excavationTarget;copy.excavationPickup=excavationPickup;copy.excavationDelivery=excavationDelivery;copy.excavationCarrying=excavationCarrying;copy.buildingSite=buildingSite;copy.buildingTarget=buildingTarget;copy.foodSite=foodSite;copy.foodHarvest=foodHarvest;
        copy.foodPlanting=foodPlanting;
        copy.growerSite=growerSite;copy.growerBed=growerBed;copy.growerJob=growerJob;
        copy.active=active;copy.working=working;copy.nextDecision=nextDecision;copy.beganAt=beganAt;
        copy.retryAt=retryAt;copy.nextVisual=nextVisual;copy.nextSave=nextSave;
        copy.excavationGeneration=excavationGeneration;
        copy.routeRetries=routeRetries.copy();
        copy.furnishingSite=furnishingSite;copy.furnishingArea=furnishingArea;
        copy.nextFurnishingCheck=nextFurnishingCheck;copy.furnishingEquipment=furnishingEquipment;
        copy.salvageTarget=salvageTarget;copy.nextSalvage=nextSalvage;copy.salvageHold=salvageHold;return copy;
    }
}
