package com.hexvane.aetherhaven.festival.wintertide;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Someone a town member gives a Wintertide gift to, or who gives a gift back. */
public final class WintertideTarget {
    public enum Kind {
        PLAYER,
        VILLAGER
    }

    @Nonnull
    private final Kind kind;
    @Nonnull
    private final UUID uuid;
    @Nullable
    private final String villagerKind;
    @Nonnull
    private final String displayName;

    public WintertideTarget(
        @Nonnull Kind kind,
        @Nonnull UUID uuid,
        @Nullable String villagerKind,
        @Nonnull String displayName
    ) {
        this.kind = kind;
        this.uuid = uuid;
        this.villagerKind = villagerKind;
        this.displayName = displayName;
    }

    @Nonnull
    public static WintertideTarget player(@Nonnull UUID uuid, @Nonnull String displayName) {
        return new WintertideTarget(Kind.PLAYER, uuid, null, displayName);
    }

    @Nonnull
    public static WintertideTarget villager(
        @Nonnull UUID uuid, @Nonnull String villagerKind, @Nonnull String displayName
    ) {
        return new WintertideTarget(Kind.VILLAGER, uuid, villagerKind, displayName);
    }

    public boolean isPlayer() {
        return kind == Kind.PLAYER;
    }

    public boolean isVillager() {
        return kind == Kind.VILLAGER;
    }

    @Nonnull
    public Kind getKind() {
        return kind;
    }

    @Nonnull
    public UUID getUuid() {
        return uuid;
    }

    @Nullable
    public String getVillagerKind() {
        return villagerKind;
    }

    @Nonnull
    public String getDisplayName() {
        return displayName;
    }
}
