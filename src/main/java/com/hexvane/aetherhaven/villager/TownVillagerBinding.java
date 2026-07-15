package com.hexvane.aetherhaven.villager;

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

/** Links an Aetherhaven NPC to a town; {@code kind} distinguishes elder, innkeeper, etc. */
public final class TownVillagerBinding implements Component<EntityStore> {
    public static final String KIND_ELDER = "elder";
    public static final String KIND_INNKEEPER = "innkeeper";
    /** Permanent merchant at the market stall (after build). */
    public static final String KIND_MERCHANT = "merchant";

    /** Permanent chef at the restaurant (after build). */
    public static final String KIND_CHEF = "chef";

    /** Resident farmer tied to a completed farm plot. */
    public static final String KIND_FARMER = "farmer";

    /** Resident blacksmith tied to a completed blacksmith shop plot (job/home plot id). */
    public static final String KIND_BLACKSMITH = "blacksmith";

    /** Resident priestess tied to a completed Gaia altar plot. */
    public static final String KIND_PRIESTESS = "priestess";

    /** Resident miner tied to a completed miners hut plot. */
    public static final String KIND_MINER = "miner";

    /** Resident logger tied to a completed lumbermill plot. */
    public static final String KIND_LOGGER = "logger";

    /** Resident rancher tied to a completed barn plot. */
    public static final String KIND_RANCHER = "rancher";

    public static final String KIND_VISITOR_MERCHANT = "visitor_merchant";
    public static final String KIND_VISITOR_CHEF = "visitor_chef";
    public static final String KIND_VISITOR_BLACKSMITH = "visitor_blacksmith";
    public static final String KIND_VISITOR_FARMER = "visitor_farmer";

    public static final String KIND_VISITOR_PRIESTESS = "visitor_priestess";

    public static final String KIND_VISITOR_MINER = "visitor_miner";

    public static final String KIND_VISITOR_LOGGER = "visitor_logger";

    public static final String KIND_VISITOR_RANCHER = "visitor_rancher";

    public static final String KIND_VISITOR_GUILD_MASTER = "visitor_guild_master";

    public static final String KIND_VISITOR_CRYSTAL_KEEPER = "visitor_crystal_keeper";

    /** Permanent crystal keeper at the crystal shop (after build). */
    public static final String KIND_CRYSTAL_KEEPER = "crystal_keeper";

    /** One-shot rescue spawn from a broken Crystallized Person block. */
    public static final String KIND_RESCUE_CRYSTAL_KEEPER = "rescue_crystal_keeper";

    public static final String KIND_VISITOR_PYROTECHNIC = "visitor_pyrotechnic";

    /** Permanent pyrotechnic at the bomb shop (after build). */
    public static final String KIND_PYROTECHNIC = "pyrotechnic";

    /** One-shot rescue spawn from a broken spider cocoon. */
    public static final String KIND_RESCUE_PYROTECHNIC = "rescue_pyrotechnic";

    public static final String KIND_VISITOR_FLORIST = "visitor_florist";

    /** Permanent florist at the flower shop (after build). */
    public static final String KIND_FLORIST = "florist";

    public static final String KIND_VISITOR_BUILDER = "visitor_builder";

    /** Permanent builder at the builder's hut (after build). */
    public static final String KIND_BUILDER = "builder";

    /** Shared pool townsfolk (tourist, guard, idle test, etc.). */
    public static final String KIND_TOWNSFOLK = "townsfolk";

    /** Permanent guild master at the guild hall (after build). */
    public static final String KIND_GUILD_MASTER = "guild_master";

    /** Permanent bard at the guild hall (after inn pool promotion). */
    public static final String KIND_BARD = "bard";

    public static final String KIND_VISITOR_BARD = "visitor_bard";

    /** Hired guard from guild hall adventurer pool. */
    public static final String KIND_GUARD = "guard";

    /** True for inn pool visitors only; permanent residents use {@link #KIND_MERCHANT}, {@link #KIND_ELDER}, etc. */
    public static boolean isVisitorKind(@Nonnull String kind) {
        return kind.startsWith("visitor_");
    }

    /** True for one-shot field rescue NPCs (before inn pool). */
    public static boolean isRescueKind(@Nonnull String kind) {
        return kind.startsWith("rescue_");
    }

    /** Visitors and one-shot rescue spawns skip weekly schedules and POI autonomy. */
    public static boolean isScheduleSuppressedKind(@Nonnull String kind) {
        return isVisitorKind(kind) || isRescueKind(kind);
    }

    @Nonnull
    public static final BuilderCodec<TownVillagerBinding> CODEC =
        BuilderCodec.builder(TownVillagerBinding.class, TownVillagerBinding::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (b, v) -> b.townId = v != null ? v : "", b -> b.townId)
            .add()
            .append(new KeyedCodec<>("Kind", Codec.STRING), (b, v) -> b.kind = v != null ? v : "", b -> b.kind)
            .add()
            .append(new KeyedCodec<>("PreferredPlotId", Codec.STRING), (b, v) -> b.preferredPlotId = v, b -> b.preferredPlotId)
            .add()
            .append(
                new KeyedCodec<>("JobPlotId", Codec.STRING),
                (b, v) -> b.jobPlotId = v,
                b -> b.jobPlotId
            )
            .documentation(
                "Workplace plot for schedule segment \"work\"; set when a villager is assigned a job plot. "
                    + "PreferredPlotId is updated by the weekly schedule for current location."
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, TownVillagerBinding> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
        componentType = registry.registerComponent(TownVillagerBinding.class, "AetherhavenTownVillagerBinding", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, TownVillagerBinding> getComponentType() {
        ComponentType<EntityStore, TownVillagerBinding> t = componentType;
        if (t == null) {
            throw new IllegalStateException("TownVillagerBinding not registered");
        }
        return t;
    }

    private String townId = "";
    private String kind = "";
    @Nullable
    private String preferredPlotId;
    /** Workplace plot UUID; stable while PreferredPlotId changes with daily schedule. */
    @Nullable
    private String jobPlotId;

    public TownVillagerBinding() {}

    public TownVillagerBinding(@Nonnull UUID townId, @Nonnull String kind, @Nullable UUID preferredPlotId) {
        this(townId, kind, preferredPlotId, null);
    }

    public TownVillagerBinding(
        @Nonnull UUID townId,
        @Nonnull String kind,
        @Nullable UUID preferredPlotId,
        @Nullable UUID jobPlotId
    ) {
        this.townId = townId.toString();
        this.kind = kind;
        this.preferredPlotId = preferredPlotId != null ? preferredPlotId.toString() : null;
        this.jobPlotId = jobPlotId != null ? jobPlotId.toString() : null;
    }

    @Nonnull
    public UUID getTownId() {
        return UUID.fromString(townId);
    }

    @Nonnull
    public String getKind() {
        return kind;
    }

    @Nullable
    public UUID getPreferredPlotId() {
        return preferredPlotId != null && !preferredPlotId.isBlank() ? UUID.fromString(preferredPlotId) : null;
    }

    public void setPreferredPlotId(@Nullable UUID plotId) {
        this.preferredPlotId = plotId != null ? plotId.toString() : null;
    }

    @Nullable
    public UUID getJobPlotId() {
        return jobPlotId != null && !jobPlotId.isBlank() ? UUID.fromString(jobPlotId) : null;
    }

    public void setJobPlotId(@Nullable UUID plotId) {
        this.jobPlotId = plotId != null ? plotId.toString() : null;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new TownVillagerBinding(getTownId(), kind, getPreferredPlotId(), getJobPlotId());
    }
}
