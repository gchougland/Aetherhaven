package com.hexvane.aetherhaven.plot;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per player plot variant unlocks for the plot crafting bench. */
public final class PlayerPlotTokenUnlockState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<PlayerPlotTokenUnlockState> CODEC =
        BuilderCodec.builder(PlayerPlotTokenUnlockState.class, PlayerPlotTokenUnlockState::new)
            .append(
                new KeyedCodec<>("UnlockedConstructionIds", Codec.STRING_ARRAY),
                (c, v) -> c.unlockedConstructionIds = v != null ? new HashSet<>(Arrays.asList(v)) : new HashSet<>(),
                c -> c.unlockedConstructionIds.toArray(String[]::new))
            .add()
            .append(
                new KeyedCodec<>("UnlockPoints", Codec.INTEGER),
                (c, v) -> c.unlockPoints = v != null ? Math.max(0, v) : 0,
                c -> c.unlockPoints)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PlayerPlotTokenUnlockState> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(PlayerPlotTokenUnlockState.class, "AetherhavenPlayerPlotTokenUnlockState", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, PlayerPlotTokenUnlockState> getComponentType() {
        ComponentType<EntityStore, PlayerPlotTokenUnlockState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlayerPlotTokenUnlockState not registered");
        }
        return t;
    }

    @Nonnull
    private Set<String> unlockedConstructionIds = new HashSet<>();
    private int unlockPoints;

    public PlayerPlotTokenUnlockState() {}

    public boolean isUnlocked(@Nonnull String constructionId) {
        return unlockedConstructionIds.contains(normalize(constructionId));
    }

    public boolean unlock(@Nonnull String constructionId) {
        return unlockedConstructionIds.add(normalize(constructionId));
    }

    public int getUnlockPoints() {
        return unlockPoints;
    }

    public void addUnlockPoints(int amount) {
        if (amount > 0) {
            unlockPoints += amount;
        }
    }

    /** @return true when a point was spent */
    public boolean trySpendUnlockPoint() {
        if (unlockPoints <= 0) {
            return false;
        }
        unlockPoints--;
        return true;
    }

    @Nonnull
    private static String normalize(@Nonnull String constructionId) {
        return constructionId.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PlayerPlotTokenUnlockState c = new PlayerPlotTokenUnlockState();
        c.unlockedConstructionIds = new HashSet<>(unlockedConstructionIds);
        c.unlockPoints = unlockPoints;
        return c;
    }
}
