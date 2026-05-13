package com.hexvane.aetherhaven.floatinggift;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FloatingGiftComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<FloatingGiftComponent> CODEC =
        BuilderCodec
            .builder(FloatingGiftComponent.class, FloatingGiftComponent::new)
            .append(new KeyedCodec<>("State", Codec.STRING), (o, v) -> o.state = v, o -> o.state)
            .add()
            .append(new KeyedCodec<>("DirX", Codec.DOUBLE), (o, v) -> o.dirX = v, o -> o.dirX)
            .add()
            .append(new KeyedCodec<>("DirY", Codec.DOUBLE), (o, v) -> o.dirY = v, o -> o.dirY)
            .add()
            .append(new KeyedCodec<>("DirZ", Codec.DOUBLE), (o, v) -> o.dirZ = v, o -> o.dirZ)
            .add()
            .append(new KeyedCodec<>("AnchorY", Codec.DOUBLE), (o, v) -> o.anchorY = v, o -> o.anchorY)
            .add()
            .append(new KeyedCodec<>("SpeedBlocksPerSec", Codec.DOUBLE), (o, v) -> o.speedBlocksPerSec = v, o -> o.speedBlocksPerSec)
            .add()
            .append(new KeyedCodec<>("FallBlocksPerSec", Codec.DOUBLE), (o, v) -> o.fallBlocksPerSec = v, o -> o.fallBlocksPerSec)
            .add()
            .append(new KeyedCodec<>("LifeSeconds", Codec.DOUBLE), (o, v) -> o.lifeSeconds = v, o -> o.lifeSeconds)
            .add()
            .append(new KeyedCodec<>("PopSeconds", Codec.DOUBLE), (o, v) -> o.popSeconds = v, o -> o.popSeconds)
            .add()
            .append(new KeyedCodec<>("ProjectileHitRadius", Codec.DOUBLE), (o, v) -> o.projectileHitRadius = v, o -> o.projectileHitRadius)
            .add()
            .append(
                new KeyedCodec<>("FloatClipRetriggerAccum", Codec.DOUBLE),
                (o, v) -> o.floatClipRetriggerAccum = v,
                o -> o.floatClipRetriggerAccum
            )
            .add()
            .append(
                new KeyedCodec<>("AmbientCueAccum", Codec.DOUBLE),
                (o, v) -> o.ambientCueAccum = v,
                o -> o.ambientCueAccum
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, FloatingGiftComponent> componentType;

    private String state = FloatingGiftState.FLOATING.name();
    private double dirX;
    private double dirY;
    private double dirZ;
    /** World Y the balloon hovers around while floating (sinusoidal bob adds on top). */
    private double anchorY;
    private double speedBlocksPerSec = 1.0;
    private double fallBlocksPerSec = 8.0;
    private double lifeSeconds = 0.0;
    private double popSeconds = 0.0;
    private double projectileHitRadius = 1.25;
    /** Transient gameplay: seconds toward re-sending float PlayAnimation (not meaningful across save). */
    private double floatClipRetriggerAccum = 0.0;
    private double ambientCueAccum = 0.0;
    /**
     * When true, first {@link FloatingGiftSystem} tick should send the initial Action-slot float (buffered entity adds
     * are rare; natural spawns use {@link World#execute} and play float immediately). Omitted from CODEC;
     * default false so decoded entities do not replay the spawn clip.
     */
    private boolean needsDeferredSpawnFloatAnimation = false;
    /** Tracks whether looping {@code PopHold} was swapped in for Action (not persisted). */
    private boolean popHoldClipApplied = false;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                FloatingGiftComponent.class,
                "AetherhavenFloatingGift",
                FloatingGiftComponent.CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, FloatingGiftComponent> getComponentType() {
        ComponentType<EntityStore, FloatingGiftComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("FloatingGiftComponent not registered");
        }
        return t;
    }

    @Nonnull
    public FloatingGiftState getState() {
        try {
            return FloatingGiftState.valueOf(state);
        } catch (IllegalArgumentException ex) {
            return FloatingGiftState.FLOATING;
        }
    }

    public void setState(@Nonnull FloatingGiftState state) {
        this.state = state.name();
    }

    public double getDirX() {
        return dirX;
    }

    public void setDirX(double dirX) {
        this.dirX = dirX;
    }

    public double getDirY() {
        return dirY;
    }

    public void setDirY(double dirY) {
        this.dirY = dirY;
    }

    public double getDirZ() {
        return dirZ;
    }

    public void setDirZ(double dirZ) {
        this.dirZ = dirZ;
    }

    public double getAnchorY() {
        return anchorY;
    }

    public void setAnchorY(double anchorY) {
        this.anchorY = anchorY;
    }

    public double getSpeedBlocksPerSec() {
        return speedBlocksPerSec;
    }

    public void setSpeedBlocksPerSec(double speedBlocksPerSec) {
        this.speedBlocksPerSec = speedBlocksPerSec;
    }

    public double getFallBlocksPerSec() {
        return fallBlocksPerSec;
    }

    public void setFallBlocksPerSec(double fallBlocksPerSec) {
        this.fallBlocksPerSec = fallBlocksPerSec;
    }

    public double getLifeSeconds() {
        return lifeSeconds;
    }

    public void addLifeSeconds(double dt) {
        this.lifeSeconds += dt;
    }

    public double getPopSeconds() {
        return popSeconds;
    }

    public void addPopSeconds(double dt) {
        this.popSeconds += dt;
    }

    public void resetPopSeconds() {
        this.popSeconds = 0.0;
    }

    public double getProjectileHitRadius() {
        return projectileHitRadius;
    }

    public void setProjectileHitRadius(double projectileHitRadius) {
        this.projectileHitRadius = projectileHitRadius;
    }

    public void addFloatClipRetriggerAccum(double dt) {
        this.floatClipRetriggerAccum += dt;
    }

    public void resetFloatClipRetriggerAccum() {
        this.floatClipRetriggerAccum = 0.0;
    }

    /**
     * When accumulated time reaches {@code threshold}, subtract one threshold and return true (carries remainder).
     */
    public boolean consumeFloatClipRetriggerAccum(double threshold) {
        if (this.floatClipRetriggerAccum < threshold) {
            return false;
        }
        this.floatClipRetriggerAccum -= threshold;
        return true;
    }

    public void addAmbientCueAccum(double dt) {
        this.ambientCueAccum += dt;
    }

    public void resetAmbientCueAccum() {
        this.ambientCueAccum = 0.0;
    }

    public boolean consumeAmbientCueAccum(double threshold) {
        if (this.ambientCueAccum < threshold) {
            return false;
        }
        this.ambientCueAccum -= threshold;
        return true;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        FloatingGiftComponent c = new FloatingGiftComponent();
        c.state = this.state;
        c.dirX = this.dirX;
        c.dirY = this.dirY;
        c.dirZ = this.dirZ;
        c.anchorY = this.anchorY;
        c.speedBlocksPerSec = this.speedBlocksPerSec;
        c.fallBlocksPerSec = this.fallBlocksPerSec;
        c.lifeSeconds = this.lifeSeconds;
        c.popSeconds = this.popSeconds;
        c.projectileHitRadius = this.projectileHitRadius;
        c.floatClipRetriggerAccum = this.floatClipRetriggerAccum;
        c.ambientCueAccum = this.ambientCueAccum;
        c.needsDeferredSpawnFloatAnimation = false;
        c.popHoldClipApplied = false;
        return c;
    }

    public boolean isPopHoldClipApplied() {
        return popHoldClipApplied;
    }

    public void markPopHoldClipApplied() {
        this.popHoldClipApplied = true;
    }

    public void resetPopHoldClipApplied() {
        this.popHoldClipApplied = false;
    }

    /** Buffered spawns: float packet must wait until the entity exists. */
    public void requestDeferredSpawnFloatAnimation() {
        this.needsDeferredSpawnFloatAnimation = true;
    }

    /** After synchronous {@code Store.addEntity} + immediate float play. */
    public void markSpawnFloatPlayedImmediately() {
        this.needsDeferredSpawnFloatAnimation = false;
    }

    /** First tick after buffered spawn: consume and send float clip once entity exists. */
    public boolean consumeDeferredSpawnFloatAnimation() {
        if (!this.needsDeferredSpawnFloatAnimation) {
            return false;
        }
        this.needsDeferredSpawnFloatAnimation = false;
        return true;
    }
}
