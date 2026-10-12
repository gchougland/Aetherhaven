package com.hexvane.aetherhaven.rts;

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

/** Overrides guard patrol while the player is commanding this unit. */
public final class GuardRtsCommandState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<GuardRtsCommandState> CODEC = BuilderCodec.builder(
            GuardRtsCommandState.class,
            GuardRtsCommandState::new
        )
        .append(
            new KeyedCodec<>("OrderMode", Codec.STRING),
            (c, s) -> c.orderMode = parseOrder(s),
            c -> c.orderMode.name()
        )
        .add()
        .append(
            new KeyedCodec<>("CombatStance", Codec.STRING),
            (c, s) -> c.combatStance = parseStance(s),
            c -> c.combatStance.name()
        )
        .add()
        .append(
            new KeyedCodec<>("Phase", Codec.STRING),
            (c, s) -> c.phase = parsePhase(s),
            c -> c.phase.name()
        )
        .add()
        .append(new KeyedCodec<>("HoldX", Codec.DOUBLE), (c, v) -> c.holdX = v != null ? v : 0, c -> c.holdX)
        .add()
        .append(new KeyedCodec<>("HoldY", Codec.DOUBLE), (c, v) -> c.holdY = v != null ? v : 0, c -> c.holdY)
        .add()
        .append(new KeyedCodec<>("HoldZ", Codec.DOUBLE), (c, v) -> c.holdZ = v != null ? v : 0, c -> c.holdZ)
        .add()
        .append(
            new KeyedCodec<>("TargetUuid", Codec.UUID_BINARY),
            (c, u) -> c.targetEntityUuid = u,
            c -> c.targetEntityUuid
        )
        .add()
        .append(new KeyedCodec<>("FocusFire", Codec.BOOLEAN), (c, v) -> c.focusFire = Boolean.TRUE.equals(v), c -> c.focusFire)
        .add()
        .append(
            new KeyedCodec<>("CommanderUuid", Codec.UUID_BINARY),
            (c, u) -> c.commanderPlayerUuid = u,
            c -> c.commanderPlayerUuid
        )
        .add()
        .append(new KeyedCodec<>("FieldOrder", Codec.BOOLEAN), (c, v) -> c.fieldOrder = Boolean.TRUE.equals(v), c -> c.fieldOrder)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, GuardRtsCommandState> componentType;

    @Nonnull
    private RtsOrderMode orderMode = RtsOrderMode.ATTACK_MOVE;
    @Nonnull
    private RtsCombatStance combatStance = RtsCombatStance.DEFENSIVE;
    @Nonnull
    private RtsCommandPhase phase = RtsCommandPhase.TRAVELING;
    private double holdX;
    private double holdY;
    private double holdZ;
    @Nullable
    private UUID targetEntityUuid;
    private boolean focusFire;
    @Nullable
    private UUID commanderPlayerUuid;
    /**
     * True for an order given with the Command Flag in hand, outside the command post. Such an order lasts while its
     * commander is in the world; an order from the command post lasts while that session is open.
     */
    private boolean fieldOrder;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(GuardRtsCommandState.class, "AetherhavenGuardRtsCommand", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, GuardRtsCommandState> getComponentType() {
        ComponentType<EntityStore, GuardRtsCommandState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("GuardRtsCommandState not registered");
        }
        return t;
    }

    @Nonnull
    public RtsOrderMode getOrderMode() {
        return orderMode;
    }

    public void setOrderMode(@Nonnull RtsOrderMode orderMode) {
        this.orderMode = orderMode;
    }

    @Nonnull
    public RtsCombatStance getCombatStance() {
        return combatStance;
    }

    public void setCombatStance(@Nonnull RtsCombatStance combatStance) {
        this.combatStance = combatStance;
    }

    @Nonnull
    public RtsCommandPhase getPhase() {
        return phase;
    }

    public void setPhase(@Nonnull RtsCommandPhase phase) {
        this.phase = phase;
    }

    public double getHoldX() {
        return holdX;
    }

    public double getHoldY() {
        return holdY;
    }

    public double getHoldZ() {
        return holdZ;
    }

    public void setHold(double x, double y, double z) {
        this.holdX = x;
        this.holdY = y;
        this.holdZ = z;
    }

    @Nullable
    public UUID getTargetEntityUuid() {
        return targetEntityUuid;
    }

    public void setTargetEntityUuid(@Nullable UUID targetEntityUuid) {
        this.targetEntityUuid = targetEntityUuid;
    }

    public boolean isFocusFire() {
        return focusFire;
    }

    public void setFocusFire(boolean focusFire) {
        this.focusFire = focusFire;
    }

    @Nullable
    public UUID getCommanderPlayerUuid() {
        return commanderPlayerUuid;
    }

    public void setCommanderPlayerUuid(@Nullable UUID commanderPlayerUuid) {
        this.commanderPlayerUuid = commanderPlayerUuid;
    }

    public boolean isFieldOrder() {
        return fieldOrder;
    }

    public void setFieldOrder(boolean fieldOrder) {
        this.fieldOrder = fieldOrder;
    }

    @Nonnull
    private static RtsOrderMode parseOrder(@Nullable String s) {
        if (s == null) {
            return RtsOrderMode.ATTACK_MOVE;
        }
        try {
            return RtsOrderMode.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return RtsOrderMode.ATTACK_MOVE;
        }
    }

    @Nonnull
    private static RtsCombatStance parseStance(@Nullable String s) {
        if (s == null) {
            return RtsCombatStance.DEFENSIVE;
        }
        try {
            return RtsCombatStance.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return RtsCombatStance.DEFENSIVE;
        }
    }

    @Nonnull
    private static RtsCommandPhase parsePhase(@Nullable String s) {
        if (s == null) {
            return RtsCommandPhase.TRAVELING;
        }
        try {
            return RtsCommandPhase.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return RtsCommandPhase.TRAVELING;
        }
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        GuardRtsCommandState c = new GuardRtsCommandState();
        c.orderMode = orderMode;
        c.combatStance = combatStance;
        c.phase = phase;
        c.holdX = holdX;
        c.holdY = holdY;
        c.holdZ = holdZ;
        c.targetEntityUuid = targetEntityUuid;
        c.focusFire = focusFire;
        c.commanderPlayerUuid = commanderPlayerUuid;
        c.fieldOrder = fieldOrder;
        return c;
    }
}
