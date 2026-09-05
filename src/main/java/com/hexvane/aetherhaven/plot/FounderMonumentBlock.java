package com.hexvane.aetherhaven.plot;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FounderMonumentBlock implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<FounderMonumentBlock> CODEC = BuilderCodec.builder(FounderMonumentBlock.class, FounderMonumentBlock::new)
        .append(new KeyedCodec<>("TownId", Codec.STRING), (s, v) -> s.townId = v != null ? v : "", s -> s.townId)
        .add()
        .append(new KeyedCodec<>("StatueEntityUuid", Codec.STRING), (s, v) -> s.statueEntityUuid = v, s -> s.statueEntityUuid)
        .add()
        .append(new KeyedCodec<>("SkinJson", Codec.STRING), (s, v) -> s.skinJson = v != null ? v : "", s -> s.skinJson)
        .add()
        .append(new KeyedCodec<>("Label", Codec.STRING), (s, v) -> s.label = v != null ? v : "", s -> s.label)
        .add()
        .append(new KeyedCodec<>("Pitch", Codec.FLOAT), (s, v) -> s.pitch = v != null ? v : 0f, s -> s.pitch)
        .add()
        .append(new KeyedCodec<>("Yaw", Codec.FLOAT), (s, v) -> s.yaw = v != null ? v : 0f, s -> s.yaw)
        .add()
        .append(new KeyedCodec<>("Roll", Codec.FLOAT), (s, v) -> s.roll = v != null ? v : 0f, s -> s.roll)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, FounderMonumentBlock> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType = registry.registerComponent(FounderMonumentBlock.class, "AetherhavenFounderMonumentBlock", CODEC);
    }

    @Nonnull
    public static ComponentType<ChunkStore, FounderMonumentBlock> getComponentType() {
        ComponentType<ChunkStore, FounderMonumentBlock> t = componentType;
        if (t == null) {
            throw new IllegalStateException("FounderMonumentBlock not registered");
        }
        return t;
    }

    private String townId = "";
    @Nullable
    private String statueEntityUuid;
    private String skinJson = "";
    private String label = "";
    private float pitch;
    private float yaw;
    private float roll;

    public FounderMonumentBlock() {}

    public FounderMonumentBlock(@Nonnull String townId, @Nullable String statueEntityUuid) {
        this(townId, statueEntityUuid, "", "", 0f, 0f, 0f);
    }

    public FounderMonumentBlock(
        @Nonnull String townId,
        @Nullable String statueEntityUuid,
        @Nullable String skinJson,
        @Nullable String label,
        float pitch,
        float yaw,
        float roll
    ) {
        this.townId = townId != null ? townId : "";
        this.statueEntityUuid = statueEntityUuid;
        this.skinJson = skinJson != null ? skinJson : "";
        this.label = label != null ? label : "";
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
    }

    @Nonnull
    public String getTownId() {
        return townId;
    }

    @Nullable
    public String getStatueEntityUuid() {
        return statueEntityUuid;
    }

    public void setStatueEntityUuid(@Nullable String statueEntityUuid) {
        this.statueEntityUuid = statueEntityUuid;
    }

    @Nonnull
    public String getSkinJson() {
        return skinJson != null ? skinJson : "";
    }

    @Nonnull
    public String getLabel() {
        return label != null ? label : "";
    }

    public float getPitch() {
        return pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getRoll() {
        return roll;
    }

    @Nonnull
    public FounderMonumentBlock withStatue(
        @Nullable String statueEntityUuid,
        @Nullable String skinJson,
        @Nullable String label,
        float pitch,
        float yaw,
        float roll
    ) {
        return new FounderMonumentBlock(this.townId, statueEntityUuid, skinJson, label, pitch, yaw, roll);
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new FounderMonumentBlock(townId, statueEntityUuid, skinJson, label, pitch, yaw, roll);
    }
}
