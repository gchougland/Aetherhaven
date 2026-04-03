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
    /** Permanent stall / resident merchant (not an inn visitor). */
    public static final String KIND_MERCHANT = "merchant";

    /** Resident farmer tied to a completed farm plot. */
    public static final String KIND_FARMER = "farmer";

    /** Resident blacksmith (promoted from inn visitor; not a forge plot yet). */
    public static final String KIND_BLACKSMITH = "blacksmith";

    public static final String KIND_VISITOR_MERCHANT = "visitor_merchant";
    public static final String KIND_VISITOR_BLACKSMITH = "visitor_blacksmith";
    public static final String KIND_VISITOR_FARMER = "visitor_farmer";

    /** True for inn pool visitors only; permanent residents use {@link #KIND_MERCHANT}, {@link #KIND_ELDER}, etc. */
    public static boolean isVisitorKind(@Nonnull String kind) {
        return kind.startsWith("visitor_");
    }

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
