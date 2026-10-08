package com.hexvane.aetherhaven.workplace;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.joml.Vector3i;

/** Unsaved edit session; committed sites live in the town save. */
public final class WorkplaceToolState implements Component<EntityStore> {
    static ComponentType<EntityStore, WorkplaceToolState> TYPE;
    public enum Mode { BUILD, CREATE, SELECT, RESIZE, MINESHAFT }
    Mode mode = Mode.BUILD;
    Vector3i first;
    UUID townId, editing;
    TimberGroveSelection.Selection grove;
    long nextPreviewNanos;
    String hint = "Guide or assemble buildings. Choose a mode to manage workplaces.";
    public static boolean isWorkplaceMode(Ref<EntityStore> ref, Store<EntityStore> store) {
        var state = TYPE == null ? null : store.getComponent(ref, TYPE);
        return state != null && (state.mode != Mode.BUILD || state.grove != null);
    }
    void choose(Mode next, Ref<EntityStore> ref, Store<EntityStore> store) {
        clearSelection(); mode = next;
        hint = switch(next) {
            case BUILD -> "Guide or assemble buildings. Choose a mode to manage workplaces.";
            case CREATE -> "Choose the first ground corner.";
            case MINESHAFT -> "Select an existing Mining workplace to open its map. Create a new base around a Workbench using Create workplace mode.";
            case SELECT, RESIZE -> "Choose an existing workplace.";
        };
        var channelType = com.hexvane.aetherhaven.construction.assembly.BuildingStaffAssemblyChannelComponent.getComponentType();
        var channel = channelType == null ? null : store.getComponent(ref, channelType);
        if (channel != null) channel.resetChargeSession();
    }
    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        TYPE = registry.registerComponent(WorkplaceToolState.class, WorkplaceToolState::new);
    }
    public static WorkplaceToolState obtain(Ref<EntityStore> ref, Store<EntityStore> store) {
        var state = store.getComponent(ref, TYPE);
        if (state == null) { state = new WorkplaceToolState(); store.putComponent(ref, TYPE, state); }
        return state;
    }
    void clearSelection() { first = null; townId = null; editing = null; grove = null; nextPreviewNanos = 0; }
    @Override public WorkplaceToolState clone() {
        var copy = new WorkplaceToolState();
        copy.mode = mode; copy.first = first == null ? null : new Vector3i(first);
        copy.townId = townId; copy.editing = editing; copy.hint = hint; copy.grove = grove;
        return copy;
    }
}
