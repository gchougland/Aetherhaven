package com.hexvane.aetherhaven.festival.lettuce;

import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Turns the lettuce that ships inside the New Life prefab into the festival centerpiece. The prefab already places the
 * oversized lettuce prop at the right spot, so this only finds it and gives it the festival behaviour.
 */
public final class FestivalLettuceSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Item id of the lettuce prop the New Life prefab places in the middle of the square. */
    public static final String CENTERPIECE_ITEM_ID = "Plant_Crop_Lettuce_Item";

    /** Life essence the lettuce needs before it pops. */
    private static final int REQUIRED_ESSENCE = 12;
    private static final float MIN_SCALE = 4.0f;
    private static final float MAX_SCALE = 14.0f;
    private static final int SEEDS_PER_BURST = 28;

    private FestivalLettuceSpawnService() {}

    /** Marks the prefab's lettuce prop as the festival centerpiece. Runs on the world thread after the prefab paste. */
    public static void spawnCenterpiece(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance festivalPlot,
        @Nonnull FestivalDefinition festival
    ) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !FestivalLettuceComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        Ref<EntityStore> centerpiece = findCenterpiece(store, festivalPlot);
        if (centerpiece == null) {
            LOGGER.atWarning().log(
                "New Life festival: no %s prop found inside the festival square, the lettuce will not grow",
                CENTERPIECE_ITEM_ID
            );
            return;
        }
        FestivalLettuceComponent lettuce = new FestivalLettuceComponent();
        lettuce.setRequiredEssence(REQUIRED_ESSENCE);
        lettuce.setMinScale(MIN_SCALE);
        lettuce.setMaxScale(MAX_SCALE);
        lettuce.setSeedsPerBurst(SEEDS_PER_BURST);
        lettuce.setBurstItemIds(festival.getBurstItemIds());
        store.putComponent(centerpiece, FestivalLettuceComponent.getComponentType(), lettuce);

        EntityScaleComponent scale = store.getComponent(centerpiece, EntityScaleComponent.getComponentType());
        if (scale != null) {
            scale.setScale(MIN_SCALE);
        } else {
            store.putComponent(centerpiece, EntityScaleComponent.getComponentType(), new EntityScaleComponent(MIN_SCALE));
        }
        TransformComponent tc = store.getComponent(centerpiece, TransformComponent.getComponentType());
        if (tc != null) {
            FestivalLettuceEffects.playFestivalStart(store, new Vector3d(tc.getPosition()));
        }
    }

    /** Removes the centerpiece before the square swaps back, so no giant lettuce is left standing. */
    public static void despawnCenterpiece(@Nonnull World world, @Nonnull PlotInstance festivalPlot) {
        var entityStore = world.getEntityStore();
        if (entityStore == null || !FestivalLettuceComponent.isRegistered()) {
            return;
        }
        Store<EntityStore> store = entityStore.getStore();
        List<Ref<EntityStore>> refs = new ArrayList<>();
        store.forEachChunk(
            Query.and(FestivalLettuceComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> r = chunk.getReferenceTo(i);
                    if (r != null && r.isValid()) {
                        refs.add(r);
                    }
                }
            }
        );
        for (Ref<EntityStore> r : refs) {
            if (r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
            }
        }
    }

    /** The biggest lettuce prop standing inside the plot footprint. */
    @Nullable
    private static Ref<EntityStore> findCenterpiece(@Nonnull Store<EntityStore> store, @Nonnull PlotInstance plot) {
        PlotFootprintRecord fp = plot.toFootprint();
        List<Ref<EntityStore>> found = new ArrayList<>();
        List<Float> scales = new ArrayList<>();
        store.forEachChunk(
            Query.and(
                ItemComponent.getComponentType(),
                TransformComponent.getComponentType(),
                PropComponent.getComponentType()
            ),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    ItemComponent item = chunk.getComponent(i, ItemComponent.getComponentType());
                    TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (item == null || tc == null) {
                        continue;
                    }
                    ItemStack stack = item.getItemStack();
                    if (stack == null || !CENTERPIECE_ITEM_ID.equalsIgnoreCase(stack.getItemId())) {
                        continue;
                    }
                    Vector3d p = tc.getPosition();
                    if (!containsBlock(fp, p)) {
                        continue;
                    }
                    Ref<EntityStore> r = chunk.getReferenceTo(i);
                    if (r == null || !r.isValid()) {
                        continue;
                    }
                    EntityScaleComponent sc = chunk.getComponent(i, EntityScaleComponent.getComponentType());
                    found.add(r);
                    scales.add(sc != null ? sc.getScale() : 1.0f);
                }
            }
        );
        Ref<EntityStore> best = null;
        float bestScale = -1.0f;
        for (int i = 0; i < found.size(); i++) {
            if (scales.get(i) > bestScale) {
                bestScale = scales.get(i);
                best = found.get(i);
            }
        }
        return best;
    }

    private static boolean containsBlock(@Nonnull PlotFootprintRecord fp, @Nonnull Vector3d p) {
        int bx = (int) Math.floor(p.x);
        int by = (int) Math.floor(p.y);
        int bz = (int) Math.floor(p.z);
        return bx >= fp.getMinX()
            && bx <= fp.getMaxX()
            && by >= fp.getMinY() - 1
            && by <= fp.getMaxY() + 2
            && bz >= fp.getMinZ()
            && bz <= fp.getMaxZ();
    }
}
