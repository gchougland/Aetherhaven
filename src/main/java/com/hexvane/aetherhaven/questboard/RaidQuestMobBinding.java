package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.autonomy.AutonomyStallTrackable;
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
import org.joml.Vector3d;

/** Tags quest-board raid mobs so only their deaths count toward raid progress. */
public final class RaidQuestMobBinding implements Component<EntityStore>, AutonomyStallTrackable {
    @Nonnull
    public static final BuilderCodec<RaidQuestMobBinding> CODEC =
        BuilderCodec.builder(RaidQuestMobBinding.class, RaidQuestMobBinding::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (b, v) -> b.townId = v != null ? v : "", b -> b.townId)
            .add()
            .append(
                new KeyedCodec<>("BoardInstanceId", Codec.STRING),
                (b, v) -> b.boardInstanceId = v != null ? v : "",
                b -> b.boardInstanceId
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, RaidQuestMobBinding> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(RaidQuestMobBinding.class, "AetherhavenRaidQuestMobBinding", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, RaidQuestMobBinding> getComponentType() {
        ComponentType<EntityStore, RaidQuestMobBinding> t = componentType;
        if (t == null) {
            throw new IllegalStateException("RaidQuestMobBinding not registered");
        }
        return t;
    }

    private String townId = "";
    private String boardInstanceId = "";
    private double marchLeashX;
    private double marchLeashY;
    private double marchLeashZ;
    private double marchTargetX;
    private double marchTargetY;
    private double marchTargetZ;
    private boolean marchTargetSet;
    private long nextMarchAdvanceEpochMs;
    private boolean marchInitialized;
    private transient double autonomySampleX = Double.NaN;
    private transient double autonomySampleZ = Double.NaN;
    private transient double autonomyAnchorX = Double.NaN;
    private transient double autonomyAnchorZ = Double.NaN;
    private transient double autonomyGoalDistSq = Double.NaN;
    private transient int autonomyStallTicks;
    private transient double marchFlyCruiseY = Double.NaN;

    public RaidQuestMobBinding() {}

    public RaidQuestMobBinding(@Nonnull UUID townId, @Nonnull String boardInstanceId) {
        this.townId = townId.toString();
        this.boardInstanceId = boardInstanceId.trim();
    }

    @Nonnull
    public UUID getTownId() {
        return UUID.fromString(townId);
    }

    @Nonnull
    public String getBoardInstanceId() {
        return boardInstanceId;
    }

    public boolean isMarchInitialized() {
        return marchInitialized;
    }

    public void setMarchInitialized(boolean marchInitialized) {
        this.marchInitialized = marchInitialized;
    }

    public long getNextMarchAdvanceEpochMs() {
        return nextMarchAdvanceEpochMs;
    }

    public void setNextMarchAdvanceEpochMs(long nextMarchAdvanceEpochMs) {
        this.nextMarchAdvanceEpochMs = nextMarchAdvanceEpochMs;
    }

    @Nonnull
    public Vector3d getMarchLeash() {
        return new Vector3d(marchLeashX, marchLeashY, marchLeashZ);
    }

    public void setMarchLeash(@Nonnull Vector3d leash) {
        this.marchLeashX = leash.x;
        this.marchLeashY = leash.y;
        this.marchLeashZ = leash.z;
    }

    public void setMarchTarget(@Nonnull Vector3d target) {
        this.marchTargetX = target.x;
        this.marchTargetY = target.y;
        this.marchTargetZ = target.z;
        this.marchTargetSet = true;
    }

    @Nonnull
    public Vector3d getMarchTarget() {
        return new Vector3d(marchTargetX, marchTargetY, marchTargetZ);
    }

    public boolean hasMarchTarget() {
        return marchTargetSet;
    }

    public boolean hasMarchFlyCruiseY() {
        return Double.isFinite(marchFlyCruiseY);
    }

    public double getMarchFlyCruiseY() {
        return marchFlyCruiseY;
    }

    public void setMarchFlyCruiseY(double y) {
        this.marchFlyCruiseY = y;
    }

    @Override
    public double getAutonomySampleX() {
        return autonomySampleX;
    }

    @Override
    public double getAutonomySampleZ() {
        return autonomySampleZ;
    }

    @Override
    public double getAutonomyAnchorX() {
        return autonomyAnchorX;
    }

    @Override
    public double getAutonomyAnchorZ() {
        return autonomyAnchorZ;
    }

    @Override
    public double getAutonomyGoalDistSq() {
        return autonomyGoalDistSq;
    }

    @Override
    public int getAutonomyStallTicks() {
        return autonomyStallTicks;
    }

    @Override
    public void setAutonomySamplePosition(double x, double z) {
        autonomySampleX = x;
        autonomySampleZ = z;
    }

    @Override
    public void setAutonomyAnchorPosition(double x, double z) {
        autonomyAnchorX = x;
        autonomyAnchorZ = z;
    }

    @Override
    public void setAutonomyGoalDistSq(double distSq) {
        autonomyGoalDistSq = distSq;
    }

    @Override
    public void setAutonomyStallTicks(int ticks) {
        autonomyStallTicks = Math.max(0, ticks);
    }

    @Override
    public void resetAutonomyStallTracking() {
        autonomySampleX = Double.NaN;
        autonomySampleZ = Double.NaN;
        autonomyAnchorX = Double.NaN;
        autonomyAnchorZ = Double.NaN;
        autonomyGoalDistSq = Double.NaN;
        autonomyStallTicks = 0;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        RaidQuestMobBinding copy = new RaidQuestMobBinding(getTownId(), boardInstanceId);
        copy.marchLeashX = marchLeashX;
        copy.marchLeashY = marchLeashY;
        copy.marchLeashZ = marchLeashZ;
        copy.marchTargetX = marchTargetX;
        copy.marchTargetY = marchTargetY;
        copy.marchTargetZ = marchTargetZ;
        copy.marchTargetSet = marchTargetSet;
        copy.nextMarchAdvanceEpochMs = nextMarchAdvanceEpochMs;
        copy.marchInitialized = marchInitialized;
        copy.autonomySampleX = autonomySampleX;
        copy.autonomySampleZ = autonomySampleZ;
        copy.autonomyAnchorX = autonomyAnchorX;
        copy.autonomyAnchorZ = autonomyAnchorZ;
        copy.autonomyGoalDistSq = autonomyGoalDistSq;
        copy.autonomyStallTicks = autonomyStallTicks;
        copy.marchFlyCruiseY = marchFlyCruiseY;
        return copy;
    }
}
