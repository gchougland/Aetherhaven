package com.hexvane.aetherhaven.festival.lettuce;

import com.hexvane.aetherhaven.AetherhavenConstants;
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
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The giant lettuce at the New Life Festival: how full it is, when it last drank, and what it throws when it pops. */
public final class FestivalLettuceComponent implements Component<EntityStore> {
    public static final String STATE_GROWING = "GROWING";
    public static final String STATE_BURSTING = "BURSTING";
    /** Already popped for this festival; the entity is removed right after, so this only covers the last frame. */
    public static final String STATE_SPENT = "SPENT";

    /** Essence needed for max size and for the player to pop it with F. */
    public static final int DEFAULT_REQUIRED_ESSENCE = 20;
    /** Extra essence past full that raises the drop multiplier by 0.5x. */
    public static final int OVERCHARGE_STEP_ESSENCE = 100;
    public static final double MULTIPLIER_STEP = 0.5;
    public static final double MAX_MULTIPLIER = 5.0;
    /** Spring festival tickets thrown at 1x. */
    public static final int BASE_TICKETS = 4;
    public static final String SPRING_TICKET_ITEM_ID = "Aetherhaven_Festival_Ticket_Spring";
    public static final String ROOT_INTERACTION_BURST = "Aetherhaven_Festival_Lettuce_Burst_Root";
    public static final String INTERACTION_HINT =
        "aetherhaven_festivals.aetherhaven.festival.interactionHints.burstLettuce";

    /**
     * Essence at which the multiplier hits {@link #MAX_MULTIPLIER}:
     * {@code required + OVERCHARGE_STEP_ESSENCE * ((MAX_MULTIPLIER - 1) / MULTIPLIER_STEP)}.
     */
    public static final int MAX_ESSENCE_AT_DEFAULT =
        DEFAULT_REQUIRED_ESSENCE
            + OVERCHARGE_STEP_ESSENCE * (int) Math.round((MAX_MULTIPLIER - 1.0) / MULTIPLIER_STEP);

    @Nonnull
    public static final BuilderCodec<FestivalLettuceComponent> CODEC =
        BuilderCodec
            .builder(FestivalLettuceComponent.class, FestivalLettuceComponent::new)
            .append(new KeyedCodec<>("State", Codec.STRING), (o, v) -> o.state = v, o -> o.state)
            .add()
            .append(new KeyedCodec<>("TownId", Codec.STRING), (o, v) -> o.townId = v, o -> o.townId)
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
    @Nullable
    private String townId;
    private int essence;
    private int requiredEssence = DEFAULT_REQUIRED_ESSENCE;
    private float minScale = 4.0f;
    private float maxScale = 11.2f;
    /** Comma separated item ids the burst throws. */
    private String burstItemIds = "";
    private int seedsPerBurst = 24;

    /** Milliseconds of the last absorb, used to drive the squash pulse. Not persisted. */
    private long pulseStartEpochMs;
    /** Milliseconds the current burst started. Not persisted. */
    private long burstStartEpochMs;
    /** Seeds already thrown by the current burst. Not persisted. */
    private int seedsThrown;
    /** Tickets already thrown by the current burst. Not persisted. */
    private int ticketsThrown;
    /** Seed count locked in when the burst starts. Not persisted. */
    private int burstSeedTarget;
    /** Ticket count locked in when the burst starts. Not persisted. */
    private int burstTicketTarget;
    /** Last Model scale applied for mesh + Use hitbox. Not persisted. */
    private float appliedModelScale;

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

    /** Which town's festival square this lettuce belongs to, so one town's festival never clears another's. */
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

    public int getEssence() {
        return essence;
    }

    public void addEssence(int amount) {
        if (amount <= 0) {
            return;
        }
        this.essence = Math.min(maxEssenceCapacity(), this.essence + amount);
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

    /** Hard cap so the multiplier cannot climb past {@link #MAX_MULTIPLIER}. */
    public int maxEssenceCapacity() {
        int steps = (int) Math.round((MAX_MULTIPLIER - 1.0) / MULTIPLIER_STEP);
        return getRequiredEssence() + OVERCHARGE_STEP_ESSENCE * steps;
    }

    public boolean isFull() {
        return essence >= getRequiredEssence();
    }

    /** Ready for the player to pop with F. */
    public boolean isReadyToBurst() {
        return isGrowing() && isFull();
    }

    /** Overcharged to the multiplier cap; the lettuce pops on its own. */
    public boolean isMaxOvercharge() {
        return isGrowing() && seedMultiplier() >= MAX_MULTIPLIER - 1.0e-9;
    }

    /**
     * Drop multiplier from overcharge: {@code 1 + 0.5 * floor((essence - required) / 100)}, capped at 5.
     * At exactly {@link #getRequiredEssence()} this is 1x.
     */
    public double seedMultiplier() {
        int extra = Math.max(0, essence - getRequiredEssence());
        int steps = extra / OVERCHARGE_STEP_ESSENCE;
        return Math.min(MAX_MULTIPLIER, 1.0 + MULTIPLIER_STEP * steps);
    }

    public int scaledSeedCount() {
        return Math.max(1, (int) Math.round(getSeedsPerBurst() * seedMultiplier()));
    }

    public int scaledTicketCount() {
        return Math.max(0, (int) Math.round(BASE_TICKETS * seedMultiplier()));
    }

    /** 0 when empty, 1 when full or overcharged (size stops growing past full). */
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

    public int getTicketsThrown() {
        return ticketsThrown;
    }

    public void addTicketsThrown(int count) {
        this.ticketsThrown += count;
    }

    public void resetTicketsThrown() {
        this.ticketsThrown = 0;
    }

    public int getBurstSeedTarget() {
        return burstSeedTarget;
    }

    public void setBurstSeedTarget(int burstSeedTarget) {
        this.burstSeedTarget = Math.max(0, burstSeedTarget);
    }

    public int getBurstTicketTarget() {
        return burstTicketTarget;
    }

    public void setBurstTicketTarget(int burstTicketTarget) {
        this.burstTicketTarget = Math.max(0, burstTicketTarget);
    }

    public float getAppliedModelScale() {
        return appliedModelScale > 0.01f ? appliedModelScale : getMinScale();
    }

    public void setAppliedModelScale(float appliedModelScale) {
        this.appliedModelScale = Math.max(0.01f, appliedModelScale);
    }

    /** True when {@code itemId} counts as an offering the lettuce drinks. */
    public static boolean isEssenceItem(@Nullable String itemId) {
        return itemId != null && itemId.trim().toLowerCase(Locale.ROOT).contains("life_essence");
    }

    /** Fill credited per item in a stack: concentrated life essence is worth 100 regular essence. */
    public static int essenceValue(@Nullable String itemId) {
        if (!isEssenceItem(itemId)) {
            return 0;
        }
        if (AetherhavenConstants.ITEM_LIFE_ESSENCE_CONCENTRATED.equalsIgnoreCase(itemId.trim())) {
            return 100;
        }
        return 1;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        FestivalLettuceComponent c = new FestivalLettuceComponent();
        c.state = this.state;
        c.townId = this.townId;
        c.essence = this.essence;
        c.requiredEssence = this.requiredEssence;
        c.minScale = this.minScale;
        c.maxScale = this.maxScale;
        c.burstItemIds = this.burstItemIds;
        c.seedsPerBurst = this.seedsPerBurst;
        return c;
    }
}
