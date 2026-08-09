package com.hexvane.aetherhaven.festival.lettuce;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The giant lettuce at the New Life Festival: how full it is, when it last drank, and what it throws when it pops. */
public final class FestivalLettuceComponent implements Component<EntityStore> {
    public static final String STATE_GROWING = "GROWING";
    public static final String STATE_BURSTING = "BURSTING";
    /** Already popped for this festival; the entity is removed right after, so this only covers the last frame. */
    public static final String STATE_SPENT = "SPENT";

    @Nonnull
    public static final BuilderCodec<FestivalLettuceComponent> CODEC =
        BuilderCodec
            .builder(FestivalLettuceComponent.class, FestivalLettuceComponent::new)
            .append(new KeyedCodec<>("State", Codec.STRING), (o, v) -> o.state = v, o -> o.state)
            .add()
            .append(new KeyedCodec<>("Essence", Codec.INTEGER), (o, v) -> o.essence = v, o -> o.essence)
            .add()
            .append(new KeyedCodec<>("RequiredEssence", Codec.INTEGER), (o, v) -> o.requiredEssence = v, o -> o.requiredEssence)
            .add()
            .append(new KeyedCodec<>("MinScale", Codec.FLOAT), (o, v) -> o.minScale = v, o -> o.minScale)
            .add()
            .append(new KeyedCodec<>("MaxScale", Codec.FLOAT), (o, v) -> o.maxScale = v, o -> o.maxScale)
            .add()
            .append(new KeyedCodec<>("BurstItemIds", Codec.STRING), (o, v) -> o.burstItemIds = v, o -> o.burstItemIds)
            .add()
            .append(new KeyedCodec<>("SeedsPerBurst", Codec.INTEGER), (o, v) -> o.seedsPerBurst = v, o -> o.seedsPerBurst)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, FestivalLettuceComponent> componentType;

    private String state = STATE_GROWING;
    private int essence;
    private int requiredEssence = 12;
    private float minScale = 4.0f;
    private float maxScale = 14.0f;
    /** Comma separated item ids the burst throws. */
    private String burstItemIds = "";
    private int seedsPerBurst = 24;

    /** Milliseconds of the last absorb, used to drive the squash pulse. Not persisted. */
    private long pulseStartEpochMs;
    /** Milliseconds the current burst started. Not persisted. */
    private long burstStartEpochMs;
    /** Seeds already thrown by the current burst. Not persisted. */
    private int seedsThrown;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                FestivalLettuceComponent.class,
                "AetherhavenFestivalLettuce",
                FestivalLettuceComponent.CODEC
            );
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, FestivalLettuceComponent> getComponentType() {
        ComponentType<EntityStore, FestivalLettuceComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("FestivalLettuceComponent not registered");
        }
        return t;
    }

    @Nonnull
    public String getState() {
        return state;
    }

    public void setState(@Nonnull String state) {
        this.state = state;
    }

    public boolean isGrowing() {
        return STATE_GROWING.equals(state);
    }

    public boolean isBursting() {
        return STATE_BURSTING.equals(state);
    }

    public boolean isSpent() {
        return STATE_SPENT.equals(state);
    }

    public int getEssence() {
        return essence;
    }

    public void addEssence(int amount) {
        this.essence = Math.min(requiredEssence, this.essence + Math.max(0, amount));
    }

    public void resetEssence() {
        this.essence = 0;
    }

    public int getRequiredEssence() {
        return Math.max(1, requiredEssence);
    }

    public void setRequiredEssence(int requiredEssence) {
        this.requiredEssence = Math.max(1, requiredEssence);
    }

    public boolean isFull() {
        return essence >= getRequiredEssence();
    }

    /** 0 when empty, 1 when full. */
    public float fillRatio() {
        return Math.min(1.0f, (float) essence / getRequiredEssence());
    }

    public float getMinScale() {
        return minScale;
    }

    public void setMinScale(float minScale) {
        this.minScale = minScale;
    }

    public float getMaxScale() {
        return maxScale;
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    public int getSeedsPerBurst() {
        return Math.max(1, seedsPerBurst);
    }

    public void setSeedsPerBurst(int seedsPerBurst) {
        this.seedsPerBurst = Math.max(1, seedsPerBurst);
    }

    @Nonnull
    public List<String> getBurstItemIds() {
        List<String> out = new ArrayList<>();
        for (String part : burstItemIds.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    public void setBurstItemIds(@Nonnull List<String> ids) {
        this.burstItemIds = String.join(",", ids);
    }

    public long getPulseStartEpochMs() {
        return pulseStartEpochMs;
    }

    public void setPulseStartEpochMs(long pulseStartEpochMs) {
        this.pulseStartEpochMs = pulseStartEpochMs;
    }

    public long getBurstStartEpochMs() {
        return burstStartEpochMs;
    }

    public void setBurstStartEpochMs(long burstStartEpochMs) {
        this.burstStartEpochMs = burstStartEpochMs;
    }

    public int getSeedsThrown() {
        return seedsThrown;
    }

    public void addSeedsThrown(int count) {
        this.seedsThrown += count;
    }

    public void resetSeedsThrown() {
        this.seedsThrown = 0;
    }

    /** True when {@code itemId} counts as an offering the lettuce drinks. */
    public static boolean isEssenceItem(@Nullable String itemId) {
        return itemId != null && itemId.trim().toLowerCase(Locale.ROOT).contains("life_essence");
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        FestivalLettuceComponent c = new FestivalLettuceComponent();
        c.state = this.state;
        c.essence = this.essence;
        c.requiredEssence = this.requiredEssence;
        c.minScale = this.minScale;
        c.maxScale = this.maxScale;
        c.burstItemIds = this.burstItemIds;
        c.seedsPerBurst = this.seedsPerBurst;
        return c;
    }
}
