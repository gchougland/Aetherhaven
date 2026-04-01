package com.hexvane.aetherhaven.poi.tool;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** POI tool selection + transient debug label entity ids (labels are not persisted). */
public final class PoiToolPlayerComponent implements Component<EntityStore> {
    /** Serialized stand-in for "no selection" (codec cannot store null UUID). */
    @Nonnull
    private static final UUID NO_SELECTION = new UUID(0L, 0L);

    @Nonnull
    public static final BuilderCodec<PoiToolPlayerComponent> CODEC = BuilderCodec.builder(PoiToolPlayerComponent.class, PoiToolPlayerComponent::new)
        .append(
            new KeyedCodec<>("SelectedPoiId", Codec.UUID_BINARY),
            (c, u) -> c.selectedPoiId = u != null && !NO_SELECTION.equals(u) ? u : null,
            c -> c.selectedPoiId != null ? c.selectedPoiId : NO_SELECTION
        )
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PoiToolPlayerComponent> componentType;

    @Nullable
    private UUID selectedPoiId;
    /** Not serialized; cleared when tool is unequipped. */
    @Nonnull
    private final List<UUID> debugLabelEntityUuids = new ArrayList<>();

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(PoiToolPlayerComponent.class, "AetherhavenPoiTool", PoiToolPlayerComponent.CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, PoiToolPlayerComponent> getComponentType() {
        ComponentType<EntityStore, PoiToolPlayerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PoiToolPlayerComponent not registered");
        }
        return t;
    }

    @Nullable
    public UUID getSelectedPoiId() {
        return selectedPoiId;
    }

    public void setSelectedPoiId(@Nullable UUID selectedPoiId) {
        this.selectedPoiId = selectedPoiId;
    }

    @Nonnull
    public List<UUID> getDebugLabelEntityUuids() {
        return debugLabelEntityUuids;
    }

    public void clearDebugLabels() {
        debugLabelEntityUuids.clear();
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PoiToolPlayerComponent c = new PoiToolPlayerComponent();
        c.selectedPoiId = this.selectedPoiId;
        return c;
    }
}
