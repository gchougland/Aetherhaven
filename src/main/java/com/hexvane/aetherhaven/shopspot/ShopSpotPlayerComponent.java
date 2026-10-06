package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.town.TownRecord;
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
import org.joml.Vector3i;

public final class ShopSpotPlayerComponent implements Component<EntityStore> {
    @Nonnull
    private static final UUID NO_SPOT = new UUID(0L, 0L);

    @Nonnull
    public static final BuilderCodec<ShopSpotPlayerComponent> CODEC = BuilderCodec.builder(
            ShopSpotPlayerComponent.class,
            ShopSpotPlayerComponent::new
        )
        .append(
            new KeyedCodec<>("PendingSpotId", Codec.UUID_BINARY),
            (c, u) -> c.pendingSpotId = u != null && !NO_SPOT.equals(u) ? u : null,
            c -> c.pendingSpotId != null ? c.pendingSpotId : NO_SPOT
        )
        .add()
        .append(
            new KeyedCodec<>("PendingTownId", Codec.UUID_BINARY),
            (c, u) -> c.pendingTownId = u != null && !NO_SPOT.equals(u) ? u : null,
            c -> c.pendingTownId != null ? c.pendingTownId : NO_SPOT
        )
        .add()
        .append(
            new KeyedCodec<>("PendingPlotId", Codec.UUID_BINARY),
            (c, u) -> c.pendingPlotId = u != null && !NO_SPOT.equals(u) ? u : null,
            c -> c.pendingPlotId != null ? c.pendingPlotId : NO_SPOT
        )
        .add()
        .append(new KeyedCodec<>("PendingX", Codec.INTEGER), (c, v) -> c.pendingX = v != null ? v : 0, c -> c.pendingX)
        .add()
        .append(new KeyedCodec<>("PendingY", Codec.INTEGER), (c, v) -> c.pendingY = v != null ? v : 0, c -> c.pendingY)
        .add()
        .append(new KeyedCodec<>("PendingZ", Codec.INTEGER), (c, v) -> c.pendingZ = v != null ? v : 0, c -> c.pendingZ)
        .add()
        .append(
            new KeyedCodec<>("FocusedSpotId", Codec.UUID_BINARY),
            (c, u) -> c.focusedSpotId = u != null && !NO_SPOT.equals(u) ? u : null,
            c -> c.focusedSpotId != null ? c.focusedSpotId : NO_SPOT
        )
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, ShopSpotPlayerComponent> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(ShopSpotPlayerComponent.class, "AetherhavenShopSpotPlayer", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, ShopSpotPlayerComponent> getComponentType() {
        ComponentType<EntityStore, ShopSpotPlayerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("ShopSpotPlayerComponent not registered");
        }
        return t;
    }

    @Nullable
    private UUID pendingSpotId;
    @Nullable
    private UUID pendingTownId;
    @Nullable
    private UUID pendingPlotId;
    private int pendingX;
    private int pendingY;
    private int pendingZ;
    @Nullable
    private UUID focusedSpotId;

    public ShopSpotPlayerComponent() {}

    public void setPendingPlacement(
        @Nonnull UUID spotId,
        @Nonnull UUID townId,
        @Nonnull UUID plotId,
        @Nonnull Vector3i block
    ) {
        this.pendingSpotId = spotId;
        this.pendingTownId = townId;
        this.pendingPlotId = plotId;
        this.pendingX = block.x;
        this.pendingY = block.y;
        this.pendingZ = block.z;
    }

    public void clearPendingPlacement() {
        pendingSpotId = null;
        pendingTownId = null;
        pendingPlotId = null;
    }

    @Nullable
    public UUID getPendingSpotId() {
        return pendingSpotId;
    }

    @Nullable
    public UUID getPendingTownId() {
        return pendingTownId;
    }

    @Nullable
    public UUID getPendingPlotId() {
        return pendingPlotId;
    }

    @Nullable
    public Vector3i getPendingBlock() {
        if (pendingSpotId == null) {
            return null;
        }
        return new Vector3i(pendingX, pendingY, pendingZ);
    }

    @Nullable
    public UUID getFocusedSpotId() {
        return focusedSpotId;
    }

    public void setFocusedSpotId(@Nullable UUID focusedSpotId) {
        this.focusedSpotId = focusedSpotId;
    }

    public int hudSignature(
        @Nullable ShopSpotRecord record,
        boolean gameDay,
        @Nonnull UUID viewerUuid,
        @Nonnull TownRecord town,
        boolean staffedWorkplace
    ) {
        if (record == null) {
            return 0;
        }
        int h = record.getSpotId().hashCode();
        h = 31 * h + (record.getItemId() != null ? record.getItemId().hashCode() : 0);
        h = 31 * h + record.getStock();
        h = 31 * h + (gameDay ? 1 : 0);
        h = 31 * h + (record.isPlayerControlled() ? 1 : 0);
        h = 31 * h + (staffedWorkplace ? 1 : 0);
        var plot = town.findPlotById(record.getPlotId());
        h = 31 * h + (plot != null && plot.getShopClerkId() != null ? plot.getShopClerkId().hashCode() : 0);
        h = 31 * h + viewerUuid.hashCode();
        h = 31 * h + (town.playerCanUseShopSpots(viewerUuid) ? 1 : 0);
        UUID seller = record.getSellerUuid();
        h = 31 * h + (seller != null ? seller.hashCode() : 0);
        String hint = ShopSpotHudHints.hintTranslationKey(record, town, viewerUuid, gameDay);
        h = 31 * h + (hint != null ? hint.hashCode() : 0);
        return h;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        ShopSpotPlayerComponent c = new ShopSpotPlayerComponent();
        c.pendingSpotId = pendingSpotId;
        c.pendingTownId = pendingTownId;
        c.pendingPlotId = pendingPlotId;
        c.pendingX = pendingX;
        c.pendingY = pendingY;
        c.pendingZ = pendingZ;
        c.focusedSpotId = focusedSpotId;
        return c;
    }
}
