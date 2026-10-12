package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.monument.FounderStatueLoadout;
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
        .append(
            new KeyedCodec<>("Loadout", FounderStatueLoadout.CODEC),
            (s, v) -> s.loadout = v != null ? v : new FounderStatueLoadout(),
            s -> s.loadout
        )
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, FounderMonumentBlock> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType = registry.registerComponent(FounderMonumentBlock.class, "AetherhavenFounderMonumentBlock", CODEC);
    }

    /** False when the construction part of the mod is not loaded, in which case no pedestal can exist. */
    public static boolean isRegistered() {
        return componentType != null;
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
    /** Real armor pieces on the statue and its pose. Never null; saved with the pedestal block. */
    @Nonnull
    private FounderStatueLoadout loadout = new FounderStatueLoadout();

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

    /** The statue's armor and pose. The returned object is live: changes are saved with this block. */
    @Nonnull
    public FounderStatueLoadout getLoadout() {
        return loadout;
    }

    /**
     * Hands this block's armor and pose to the block that replaces it in the component store. This block keeps an
     * empty loadout afterwards, so the pieces have one owner.
     */
    public void moveLoadoutTo(@Nonnull FounderMonumentBlock replacement) {
        if (replacement == this) {
            return;
        }
        replacement.loadout.takeOver(this.loadout);
    }

    /**
     * Carries a placed monument over to the block entity the game is about to put in its place (for example when the
     * pillar's shape changes because a neighbour was placed). The new block only takes what it does not already
     * have, and the armor moves instead of being copied.
     */
    public void moveStatueTo(@Nonnull FounderMonumentBlock replacement) {
        if (replacement == this) {
            return;
        }
        if (replacement.townId.isBlank()) {
            replacement.townId = this.townId;
        }
        if (replacement.statueEntityUuid == null || replacement.statueEntityUuid.isBlank()) {
            replacement.statueEntityUuid = this.statueEntityUuid;
        }
        if (replacement.getSkinJson().isBlank()) {
            replacement.skinJson = this.skinJson;
        }
        if (replacement.getLabel().isBlank()) {
            replacement.label = this.label;
        }
        if (replacement.pitch == 0f && replacement.yaw == 0f && replacement.roll == 0f) {
            replacement.pitch = this.pitch;
            replacement.yaw = this.yaw;
            replacement.roll = this.roll;
        }
        moveLoadoutTo(replacement);
        // The statue now belongs to the replacement; removing this block must not take the statue with it.
        this.statueEntityUuid = null;
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
        FounderMonumentBlock next = new FounderMonumentBlock(this.townId, statueEntityUuid, skinJson, label, pitch, yaw, roll);
        next.loadout = this.loadout.copy();
        return next;
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        FounderMonumentBlock copy = new FounderMonumentBlock(townId, statueEntityUuid, skinJson, label, pitch, yaw, roll);
        copy.loadout = this.loadout.copy();
        return copy;
    }
}
