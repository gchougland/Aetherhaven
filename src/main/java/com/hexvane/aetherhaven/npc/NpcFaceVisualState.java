package com.hexvane.aetherhaven.npc;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Tracks ambient mood face and short dialogue talk bursts on town NPCs. */
public final class NpcFaceVisualState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<NpcFaceVisualState> CODEC =
        BuilderCodec.builder(NpcFaceVisualState.class, NpcFaceVisualState::new)
            .append(new KeyedCodec<>("LastMoodTier", Codec.INTEGER), (v, x) -> v.lastMoodTier = x != null ? x : -1, v -> v.lastMoodTier)
            .add()
            .append(new KeyedCodec<>("TalkUntilMs", Codec.LONG), (v, x) -> v.talkUntilMs = x != null ? x : 0L, v -> v.talkUntilMs)
            .add()
            .append(
                new KeyedCodec<>("LastMoodApplyEpochMs", Codec.LONG),
                (v, x) -> v.lastMoodApplyEpochMs = x != null ? x : 0L,
                v -> v.lastMoodApplyEpochMs
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, NpcFaceVisualState> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(NpcFaceVisualState.class, "AetherhavenNpcFaceVisualState", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, NpcFaceVisualState> getComponentType() {
        ComponentType<EntityStore, NpcFaceVisualState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("NpcFaceVisualState not registered");
        }
        return t;
    }

    private int lastMoodTier = -1;
    private long talkUntilMs;
    private long lastMoodApplyEpochMs;

    public NpcFaceVisualState() {}

    @Nonnull
    public static NpcFaceVisualState fresh() {
        return new NpcFaceVisualState();
    }

    public int getLastMoodTier() {
        return lastMoodTier;
    }

    public void setLastMoodTier(int lastMoodTier) {
        this.lastMoodTier = lastMoodTier;
    }

    public long getTalkUntilMs() {
        return talkUntilMs;
    }

    public void setTalkUntilMs(long talkUntilMs) {
        this.talkUntilMs = talkUntilMs;
    }

    public long getLastMoodApplyEpochMs() {
        return lastMoodApplyEpochMs;
    }

    public void setLastMoodApplyEpochMs(long lastMoodApplyEpochMs) {
        this.lastMoodApplyEpochMs = lastMoodApplyEpochMs;
    }

    public boolean isTalkBurstActive(long nowMs) {
        return talkUntilMs > nowMs;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        NpcFaceVisualState c = new NpcFaceVisualState();
        c.lastMoodTier = lastMoodTier;
        c.talkUntilMs = talkUntilMs;
        c.lastMoodApplyEpochMs = lastMoodApplyEpochMs;
        return c;
    }
}
