package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Runtime plot-creator important-spot visualization; never bake into prefabs or paste from them. */
public final class PlotCreatorSpotViz {
    private PlotCreatorSpotViz() {}

    public static boolean isSpotViz(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (!ref.isValid()) {
            return false;
        }
        if (store.getComponent(ref, PlotCreatorSpotPreview.getComponentType()) != null) {
            return true;
        }
        ComponentType<EntityStore, PlotCreatorSpotMarkerEntity> markerType =
            PlotCreatorSpotMarkerEntity.getComponentType();
        return markerType != null && store.getComponent(ref, markerType) != null;
    }

    public static boolean isSpotViz(@Nonnull Holder<EntityStore> holder) {
        if (holder.getComponent(PlotCreatorSpotPreview.getComponentType()) != null) {
            return true;
        }
        ComponentType<EntityStore, PlotCreatorSpotMarkerEntity> markerType =
            PlotCreatorSpotMarkerEntity.getComponentType();
        return markerType != null && holder.getComponent(markerType) != null;
    }
}
