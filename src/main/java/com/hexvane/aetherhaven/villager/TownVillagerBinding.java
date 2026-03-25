package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Links an Aetherhaven NPC to a town; {@code kind} distinguishes elder, innkeeper, etc. */
public final class TownVillagerBinding implements Component<EntityStore> {
    public static final String KIND_ELDER = "elder";
    public static final String KIND_INNKEEPER = "innkeeper";

    @Nonnull
    public static final BuilderCodec<TownVillagerBinding> CODEC =
        BuilderCodec.builder(TownVillagerBinding.class, TownVillagerBinding::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (b, v) -> b.townId = v != null ? v : "", b -> b.townId)
            .add()
            .append(new KeyedCodec<>("Kind", Codec.STRING), (b, v) -> b.kind = v != null ? v : "", b -> b.kind)
            .add()
            .append(new KeyedCodec<>("PreferredPlotId", Codec.STRING), (b, v) -> b.preferredPlotId = v, b -> b.preferredPlotId)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, TownVillagerBinding> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(TownVillagerBinding.class, "AetherhavenTownVillagerBinding", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, TownVillagerBinding> getComponentType() {
        ComponentType<EntityStore, TownVillagerBinding> t = componentType;
        if (t == null) {
            throw new IllegalStateException("TownVillagerBinding not registered");
        }
        return t;
    }

    private String townId = "";
    private String kind = "";
    @Nullable
    private String preferredPlotId;

    public TownVillagerBinding() {}

    public TownVillagerBinding(@Nonnull UUID townId, @Nonnull String kind, @Nullable UUID preferredPlotId) {
        this.townId = townId.toString();
        this.kind = kind;
        this.preferredPlotId = preferredPlotId != null ? preferredPlotId.toString() : null;
    }

    @Nonnull
    public UUID getTownId() {
        return UUID.fromString(townId);
    }

    @Nonnull
    public String getKind() {
        return kind;
    }

    @Nullable
    public UUID getPreferredPlotId() {
        return preferredPlotId != null && !preferredPlotId.isBlank() ? UUID.fromString(preferredPlotId) : null;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new TownVillagerBinding(getTownId(), kind, getPreferredPlotId());
    }
}
