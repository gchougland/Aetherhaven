package com.hexvane.aetherhaven.bard;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Active bard song performance on an NPC entity. */
public final class BardPerformanceComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<BardPerformanceComponent> CODEC =
        BuilderCodec.builder(BardPerformanceComponent.class, BardPerformanceComponent::new)
            .append(new KeyedCodec<>("SongId", Codec.STRING), (c, v) -> c.songId = v != null ? v : "", c -> c.songId)
            .add()
            .append(
                new KeyedCodec<>("EndAtEpochMs", Codec.LONG),
                (c, v) -> c.endAtEpochMs = v,
                c -> c.endAtEpochMs
            )
            .add()
            .append(
                new KeyedCodec<>("LastParticleSpawnMs", Codec.LONG),
                (c, v) -> c.lastParticleSpawnMs = v,
                c -> c.lastParticleSpawnMs
            )
            .add()
            .append(
                new KeyedCodec<>("AmbienceFxIndex", Codec.INTEGER),
                (c, v) -> c.ambienceFxIndex = v,
                c -> c.ambienceFxIndex
            )
            .add()
            .append(
                new KeyedCodec<>("PlaybackMode", Codec.STRING),
                (c, v) -> c.playbackMode = BardPlaybackMode.fromString(v),
                c -> c.playbackMode.wireName()
            )
            .add()
            .append(
                new KeyedCodec<>("ShuffleRemaining", Codec.STRING_ARRAY),
                (c, v) -> c.shuffleRemaining = v != null ? v : new String[0],
                c -> c.shuffleRemaining
            )
            .add()
            .append(
                new KeyedCodec<>("HasMusicOrigin", Codec.BOOLEAN),
                (c, v) -> c.hasMusicOrigin = v != null && v,
                c -> c.hasMusicOrigin
            )
            .add()
            .append(new KeyedCodec<>("OriginX", Codec.DOUBLE), (c, v) -> c.originX = v, c -> c.originX)
            .add()
            .append(new KeyedCodec<>("OriginY", Codec.DOUBLE), (c, v) -> c.originY = v, c -> c.originY)
            .add()
            .append(new KeyedCodec<>("OriginZ", Codec.DOUBLE), (c, v) -> c.originZ = v, c -> c.originZ)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, BardPerformanceComponent> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(BardPerformanceComponent.class, "AetherhavenBardPerformance", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, BardPerformanceComponent> getComponentType() {
        ComponentType<EntityStore, BardPerformanceComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("BardPerformanceComponent not registered");
        }
        return t;
    }

    @Nonnull
    private String songId = "";
    private long endAtEpochMs = 0L;
    private long lastParticleSpawnMs = 0L;
    private int ambienceFxIndex = 0;
    @Nonnull
    private BardPlaybackMode playbackMode = BardPlaybackMode.ONCE;
    @Nonnull
    private String[] shuffleRemaining = new String[0];
    private boolean hasMusicOrigin;
    private double originX;
    private double originY;
    private double originZ;

    public BardPerformanceComponent() {}

    public BardPerformanceComponent(@Nonnull String songId, long endAtEpochMs, int ambienceFxIndex) {
        this(songId, endAtEpochMs, ambienceFxIndex, BardPlaybackMode.ONCE, new String[0]);
    }

    public BardPerformanceComponent(
        @Nonnull String songId,
        long endAtEpochMs,
        int ambienceFxIndex,
        @Nonnull BardPlaybackMode playbackMode,
        @Nonnull String[] shuffleRemaining
    ) {
        this.songId = songId;
        this.endAtEpochMs = endAtEpochMs;
        this.ambienceFxIndex = ambienceFxIndex;
        this.playbackMode = playbackMode != null ? playbackMode : BardPlaybackMode.ONCE;
        this.shuffleRemaining = shuffleRemaining != null ? shuffleRemaining.clone() : new String[0];
    }

    @Nonnull
    public String getSongId() {
        return songId;
    }

    public long getEndAtEpochMs() {
        return endAtEpochMs;
    }

    public long getLastParticleSpawnMs() {
        return lastParticleSpawnMs;
    }

    public void setLastParticleSpawnMs(long lastParticleSpawnMs) {
        this.lastParticleSpawnMs = lastParticleSpawnMs;
    }

    public int getMusicContainerIndex() {
        return ambienceFxIndex;
    }

    @Nonnull
    public BardPlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public boolean isLooping() {
        return playbackMode == BardPlaybackMode.LOOP;
    }

    public boolean isShuffling() {
        return playbackMode == BardPlaybackMode.SHUFFLE;
    }

    @Nonnull
    public String[] getShuffleRemaining() {
        return shuffleRemaining.clone();
    }

    public boolean hasMusicOrigin() {
        return hasMusicOrigin;
    }

    public double getOriginX() {
        return originX;
    }

    public double getOriginY() {
        return originY;
    }

    public double getOriginZ() {
        return originZ;
    }

    public void setMusicOrigin(double x, double y, double z) {
        hasMusicOrigin = true;
        originX = x;
        originY = y;
        originZ = z;
    }

    public void copyMusicOriginFrom(@Nonnull BardPerformanceComponent other) {
        if (!other.hasMusicOrigin) {
            return;
        }
        setMusicOrigin(other.originX, other.originY, other.originZ);
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        BardPerformanceComponent c =
            new BardPerformanceComponent(songId, endAtEpochMs, ambienceFxIndex, playbackMode, shuffleRemaining);
        c.lastParticleSpawnMs = lastParticleSpawnMs;
        c.copyMusicOriginFrom(this);
        return c;
    }
}
