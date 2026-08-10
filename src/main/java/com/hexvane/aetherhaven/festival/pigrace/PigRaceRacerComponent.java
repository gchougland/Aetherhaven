package com.hexvane.aetherhaven.festival.pigrace;

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

/** Marks a temporary festival pig that is racing for a town. */
public final class PigRaceRacerComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<PigRaceRacerComponent> CODEC =
        BuilderCodec
            .builder(PigRaceRacerComponent.class, PigRaceRacerComponent::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (o, v) -> o.townId = v, o -> o.townId)
            .add()
            .append(new KeyedCodec<>("LaneIndex", Codec.INTEGER), (o, v) -> o.laneIndex = v, o -> o.laneIndex)
            .add()
            .append(new KeyedCodec<>("SpeedMultiplier", Codec.DOUBLE), (o, v) -> o.speedMultiplier = v, o -> o.speedMultiplier)
            .add()
            .append(new KeyedCodec<>("RaceProgress", Codec.DOUBLE), (o, v) -> o.raceProgress = v, o -> o.raceProgress)
            .add()
            .append(new KeyedCodec<>("StartX", Codec.DOUBLE), (o, v) -> o.startX = v, o -> o.startX)
            .add()
            .append(new KeyedCodec<>("StartY", Codec.DOUBLE), (o, v) -> o.startY = v, o -> o.startY)
            .add()
            .append(new KeyedCodec<>("StartZ", Codec.DOUBLE), (o, v) -> o.startZ = v, o -> o.startZ)
            .add()
            .append(new KeyedCodec<>("FinishX", Codec.DOUBLE), (o, v) -> o.finishX = v, o -> o.finishX)
            .add()
            .append(new KeyedCodec<>("FinishY", Codec.DOUBLE), (o, v) -> o.finishY = v, o -> o.finishY)
            .add()
            .append(new KeyedCodec<>("FinishZ", Codec.DOUBLE), (o, v) -> o.finishZ = v, o -> o.finishZ)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PigRaceRacerComponent> componentType;

    private String townId = "";
    private int laneIndex;
    private double speedMultiplier = 1.0;
    /** Distance traveled along the lane from the start; never decreases during a race. */
    private double raceProgress;
    private double startX;
    private double startY;
    private double startZ;
    private double finishX;
    private double finishY;
    private double finishZ;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                PigRaceRacerComponent.class,
                "AetherhavenPigRaceRacer",
                PigRaceRacerComponent.CODEC
            );
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, PigRaceRacerComponent> getComponentType() {
        ComponentType<EntityStore, PigRaceRacerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PigRaceRacerComponent not registered");
        }
        return t;
    }

    @Nonnull
    public static PigRaceRacerComponent create(
        @Nonnull UUID townId,
        int laneIndex,
        double speedMultiplier,
        double startX,
        double startY,
        double startZ,
        double finishX,
        double finishY,
        double finishZ
    ) {
        PigRaceRacerComponent c = new PigRaceRacerComponent();
        c.townId = townId.toString();
        c.laneIndex = laneIndex;
        c.speedMultiplier = speedMultiplier;
        c.raceProgress = 0.0;
        c.startX = startX;
        c.startY = startY;
        c.startZ = startZ;
        c.finishX = finishX;
        c.finishY = finishY;
        c.finishZ = finishZ;
        return c;
    }

    @Nonnull
    public PigRaceRacerComponent withProgress(double progress) {
        PigRaceRacerComponent c = (PigRaceRacerComponent) clone();
        c.raceProgress = Math.max(0.0, progress);
        return c;
    }

    @Nonnull
    public PigRaceRacerComponent withSpeed(double speed) {
        PigRaceRacerComponent c = (PigRaceRacerComponent) clone();
        c.speedMultiplier = speed;
        return c;
    }

    @Nullable
    public UUID getTownId() {
        if (townId == null || townId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(townId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int getLaneIndex() {
        return laneIndex;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public double getRaceProgress() {
        return raceProgress;
    }

    public double getStartX() {
        return startX;
    }

    public double getStartY() {
        return startY;
    }

    public double getStartZ() {
        return startZ;
    }

    public double getFinishX() {
        return finishX;
    }

    public double getFinishY() {
        return finishY;
    }

    public double getFinishZ() {
        return finishZ;
    }

    public double trackLength() {
        double dx = finishX - startX;
        double dy = finishY - startY;
        double dz = finishZ - startZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        PigRaceRacerComponent c = new PigRaceRacerComponent();
        c.townId = townId;
        c.laneIndex = laneIndex;
        c.speedMultiplier = speedMultiplier;
        c.raceProgress = raceProgress;
        c.startX = startX;
        c.startY = startY;
        c.startZ = startZ;
        c.finishX = finishX;
        c.finishY = finishY;
        c.finishZ = finishZ;
        return c;
    }
}
