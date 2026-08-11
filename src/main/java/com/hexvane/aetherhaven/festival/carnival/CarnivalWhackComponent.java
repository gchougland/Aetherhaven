package com.hexvane.aetherhaven.festival.carnival;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Marker + pop motion state for a carnival whack goblin. */
public final class CarnivalWhackComponent implements Component<EntityStore> {
    public enum State {
        RISING,
        UP,
        HIT,
        RETRACTING
    }

    @Nonnull
    public static final BuilderCodec<CarnivalWhackComponent> CODEC =
        BuilderCodec
            .builder(CarnivalWhackComponent.class, CarnivalWhackComponent::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (o, v) -> o.townId = v, o -> o.townId)
            .add()
            .append(new KeyedCodec<>("HoleIndex", Codec.INTEGER), (o, v) -> o.holeIndex = v, o -> o.holeIndex)
            .add()
            .append(new KeyedCodec<>("State", Codec.STRING), (o, v) -> o.stateName = v, o -> o.stateName)
            .add()
            .append(new KeyedCodec<>("StateSeconds", Codec.FLOAT), (o, v) -> o.stateSeconds = v, o -> o.stateSeconds)
            .add()
            .append(new KeyedCodec<>("StandY", Codec.DOUBLE), (o, v) -> o.standY = v, o -> o.standY)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, CarnivalWhackComponent> componentType;

    @Nullable
    private String townId;
    private int holeIndex;
    @Nullable
    private String stateName = State.RISING.name();
    private float stateSeconds;
    private double standY;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(CarnivalWhackComponent.class, "AetherhavenCarnivalWhack", CODEC);
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, CarnivalWhackComponent> getComponentType() {
        ComponentType<EntityStore, CarnivalWhackComponent> type = componentType;
        if (type == null) {
            throw new IllegalStateException("CarnivalWhackComponent not registered");
        }
        return type;
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

    public void setTownId(@Nullable UUID townId) {
        this.townId = townId != null ? townId.toString() : null;
    }

    public int getHoleIndex() {
        return holeIndex;
    }

    public void setHoleIndex(int holeIndex) {
        this.holeIndex = holeIndex;
    }

    @Nonnull
    public State getState() {
        if (stateName == null || stateName.isBlank()) {
            return State.RISING;
        }
        try {
            return State.valueOf(stateName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return State.RISING;
        }
    }

    public void setState(@Nonnull State state) {
        this.stateName = state.name();
        this.stateSeconds = 0f;
    }

    public float getStateSeconds() {
        return stateSeconds;
    }

    public void addStateSeconds(float dt) {
        stateSeconds += dt;
    }

    public double getStandY() {
        return standY;
    }

    public void setStandY(double standY) {
        this.standY = standY;
    }

    public boolean canAcceptHit() {
        State state = getState();
        return state == State.RISING || state == State.UP;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        CarnivalWhackComponent copy = new CarnivalWhackComponent();
        copy.townId = townId;
        copy.holeIndex = holeIndex;
        copy.stateName = stateName;
        copy.stateSeconds = stateSeconds;
        copy.standY = standY;
        return copy;
    }
}
