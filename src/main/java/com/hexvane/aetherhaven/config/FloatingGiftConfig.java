package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public final class FloatingGiftConfig {
    /**
     * Start looping PopHold this many seconds before {@link #popHoldLatchSeconds} so it overlaps the end of the Pop clip
     * (avoids a one-frame rest pose on the client).
     */
    public static final double POP_HOLD_EARLY_SECONDS = 0.5;

    public static final BuilderCodec<FloatingGiftConfig> CODEC =
        BuilderCodec
            .builder(FloatingGiftConfig.class, FloatingGiftConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN), (o, v) -> o.enabled = v, o -> o.enabled)
            .add()
            .append(new KeyedCodec<>("SpawnRadiusBlocks", Codec.DOUBLE), (o, v) -> o.spawnRadiusBlocks = v, o -> o.spawnRadiusBlocks)
            .add()
            .append(
                new KeyedCodec<>("SpawnHeightOffsetBlocks", Codec.DOUBLE),
                (o, v) -> o.spawnHeightOffsetBlocks = v,
                o -> o.spawnHeightOffsetBlocks
            )
            .add()
            .append(new KeyedCodec<>("SpawnIntervalDaysMin", Codec.DOUBLE), (o, v) -> o.spawnIntervalDaysMin = v, o -> o.spawnIntervalDaysMin)
            .add()
            .append(new KeyedCodec<>("SpawnIntervalDaysMax", Codec.DOUBLE), (o, v) -> o.spawnIntervalDaysMax = v, o -> o.spawnIntervalDaysMax)
            .add()
            .append(
                new KeyedCodec<>("MoveSpeedBlocksPerSec", Codec.DOUBLE),
                (o, v) -> o.moveSpeedBlocksPerSec = v,
                o -> o.moveSpeedBlocksPerSec
            )
            .add()
            .append(
                new KeyedCodec<>("FallSpeedBlocksPerSec", Codec.DOUBLE),
                (o, v) -> o.fallSpeedBlocksPerSec = v,
                o -> o.fallSpeedBlocksPerSec
            )
            .add()
            .append(new KeyedCodec<>("MaxLifeSeconds", Codec.DOUBLE), (o, v) -> o.maxLifeSeconds = v, o -> o.maxLifeSeconds)
            .add()
            .append(new KeyedCodec<>("PopDurationSeconds", Codec.DOUBLE), (o, v) -> o.popDurationSeconds = v, o -> o.popDurationSeconds)
            .add()
            .append(
                new KeyedCodec<>("PopHoldLatchSeconds", Codec.DOUBLE),
                (o, v) -> o.popHoldLatchSeconds = v,
                o -> o.popHoldLatchSeconds
            )
            .add()
            .append(
                new KeyedCodec<>("ProjectileHitRadiusBlocks", Codec.DOUBLE),
                (o, v) -> o.projectileHitRadiusBlocks = v,
                o -> o.projectileHitRadiusBlocks
            )
            .add()
            .append(new KeyedCodec<>("MaxActivePerWorld", Codec.INTEGER), (o, v) -> o.maxActivePerWorld = v, o -> o.maxActivePerWorld)
            .add()
            .build();

    private boolean enabled = true;
    private double spawnRadiusBlocks = 100.0;
    private double spawnHeightOffsetBlocks = 15.0;
    private double spawnIntervalDaysMin = 0.1;
    private double spawnIntervalDaysMax = 0.3;
    private double moveSpeedBlocksPerSec = 1.3;
    private double fallSpeedBlocksPerSec = 8.5;
    private double maxLifeSeconds = 240.0;
    private double popDurationSeconds = 2.0;
    /** After hit, elapsed seconds before swapping Action from one-shot Pop to looping PopHold (see Server/Models/Floating_Gift.json). */
    private double popHoldLatchSeconds = 1.05;
    private double projectileHitRadiusBlocks = 1.4;
    private int maxActivePerWorld = 8;

    public boolean isEnabled() {
        return enabled;
    }

    public double getSpawnRadiusBlocks() {
        return Math.max(5.0, spawnRadiusBlocks);
    }

    public double getSpawnHeightOffsetBlocks() {
        return Math.max(1.0, spawnHeightOffsetBlocks);
    }

    public double getSpawnIntervalDaysMin() {
        return Math.max(0.1, spawnIntervalDaysMin);
    }

    public double getSpawnIntervalDaysMax() {
        return Math.max(getSpawnIntervalDaysMin(), spawnIntervalDaysMax);
    }

    public double getMoveSpeedBlocksPerSec() {
        return Math.max(0.1, moveSpeedBlocksPerSec);
    }

    public double getFallSpeedBlocksPerSec() {
        return Math.max(0.5, fallSpeedBlocksPerSec);
    }

    public double getMaxLifeSeconds() {
        return Math.max(5.0, maxLifeSeconds);
    }

    public double getPopDurationSeconds() {
        return Math.max(0.0, popDurationSeconds);
    }

    /**
     * Effective time after hit when we switch Action to looping {@code PopHold}: {@link #popHoldLatchSeconds} minus
     * {@link #POP_HOLD_EARLY_SECONDS}, capped below {@link #getPopDurationSeconds()} so the latch stays within the Pop phase.
     */
    public double getPopHoldLatchSeconds() {
        double latch = Math.max(0.05, popHoldLatchSeconds);
        latch = Math.max(0.05, latch - POP_HOLD_EARLY_SECONDS);
        double popDur = getPopDurationSeconds();
        if (popDur <= 1.0e-6) {
            return latch;
        }
        double maxLatch = Math.max(0.05, popDur - 0.02);
        return Math.min(latch, maxLatch);
    }

    public double getProjectileHitRadiusBlocks() {
        return Math.max(0.25, projectileHitRadiusBlocks);
    }

    public int getMaxActivePerWorld() {
        return Math.max(1, maxActivePerWorld);
    }

    /** Town Journal: toggle balloons and the in game days between spawn rolls range. */
    public void applyJournalSpawnCadence(boolean enabled, double intervalDaysMin, double intervalDaysMax) {
        this.enabled = enabled;
        double mn = intervalDaysMin;
        if (Double.isNaN(mn) || mn < 0.1) {
            mn = 0.1;
        }
        this.spawnIntervalDaysMin = mn;
        double mx = intervalDaysMax;
        if (Double.isNaN(mx)) {
            mx = mn;
        }
        if (mx < mn) {
            mx = mn;
        }
        this.spawnIntervalDaysMax = mx;
    }
}
