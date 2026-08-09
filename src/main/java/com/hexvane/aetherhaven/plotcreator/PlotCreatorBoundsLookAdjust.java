package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Expand or shrink committed bounds by one block from look direction while standing inside the box. */
final class PlotCreatorBoundsLookAdjust {
    private PlotCreatorBoundsLookAdjust() {}

    /**
     * @return validation error key, or {@code null} on success
     */
    @Nullable
    static String tryNudgeFromLook(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorDraft draft,
        boolean expand
    ) {
        if (draft.isFestivalSizeLocked()) {
            return "boundsLockedFestival";
        }
        if (draft.getCornerFirst() == null || draft.getCornerSecond() == null) {
            return "boundsTooSmall";
        }
        if (!isPlayerInsideBounds(ref, store, draft)) {
            return "boundsNudgeOutside";
        }
        Transform look = TargetUtil.getLook(ref, store);
        Vector3d dir = look.getDirection();
        PlotCreatorBoundsFace face = faceFromLook(dir);
        return nudge(draft, face, expand);
    }

    static boolean isPlayerInsideBounds(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorDraft draft
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null || tc.getPosition() == null) {
            return false;
        }
        Vector3d pos = tc.getPosition();
        Vector3i block =
            new Vector3i(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.y),
                (int) Math.floor(pos.z)
            );
        return draft.isInsideBounds(block);
    }

    @Nonnull
    static PlotCreatorBoundsFace faceFromLook(@Nonnull Vector3d dir) {
        double ax = Math.abs(dir.x());
        double ay = Math.abs(dir.y());
        double az = Math.abs(dir.z());
        if (ax >= ay && ax >= az) {
            return dir.x() >= 0.0 ? PlotCreatorBoundsFace.MAX_X : PlotCreatorBoundsFace.MIN_X;
        }
        if (ay >= ax && ay >= az) {
            return dir.y() >= 0.0 ? PlotCreatorBoundsFace.MAX_Y : PlotCreatorBoundsFace.MIN_Y;
        }
        return dir.z() >= 0.0 ? PlotCreatorBoundsFace.MAX_Z : PlotCreatorBoundsFace.MIN_Z;
    }

    /**
     * @return validation error key, or {@code null} on success
     */
    @Nullable
    static String nudge(@Nonnull PlotCreatorDraft draft, @Nonnull PlotCreatorBoundsFace face, boolean expand) {
        if (draft.isFestivalSizeLocked()) {
            return "boundsLockedFestival";
        }
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        Vector3i newMin = new Vector3i(min);
        Vector3i newMax = new Vector3i(max);
        if (expand) {
            switch (face) {
                case MIN_X -> newMin.x--;
                case MAX_X -> newMax.x++;
                case MIN_Y -> newMin.y--;
                case MAX_Y -> newMax.y++;
                case MIN_Z -> newMin.z--;
                case MAX_Z -> newMax.z++;
            }
        } else {
            switch (face) {
                case MIN_X -> newMin.x++;
                case MAX_X -> newMax.x--;
                case MIN_Y -> newMin.y++;
                case MAX_Y -> newMax.y--;
                case MIN_Z -> newMin.z++;
                case MAX_Z -> newMax.z--;
            }
        }
        String err = PlotCreatorBoundsValidation.validateMinMax(newMin, newMax);
        if (err != null) {
            return err;
        }
        PlotCreatorBoundsValidation.commitCorners(draft, newMin, newMax);
        return null;
    }
}
