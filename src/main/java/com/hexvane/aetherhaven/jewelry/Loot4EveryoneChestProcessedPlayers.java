package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Stores which player UUIDs already received Aetherhaven Loot4Everyone bonuses for this chest. */
public final class Loot4EveryoneChestProcessedPlayers implements Component<ChunkStore> {
    @Nonnull
    public static final BuilderCodec<Loot4EveryoneChestProcessedPlayers> CODEC =
        BuilderCodec.builder(Loot4EveryoneChestProcessedPlayers.class, Loot4EveryoneChestProcessedPlayers::new)
            .append(
                new KeyedCodec<>("PlayersCsv", Codec.STRING),
                (o, v) -> o.playersCsv = v != null ? v : "",
                o -> o.playersCsv
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<ChunkStore, Loot4EveryoneChestProcessedPlayers> componentType;

    @Nonnull
    private String playersCsv = "";

    public static void register(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        componentType =
            registry.registerComponent(
                Loot4EveryoneChestProcessedPlayers.class,
                "AetherhavenLoot4EveryoneChestProcessedPlayers",
                CODEC
            );
    }

    @Nonnull
    public static ComponentType<ChunkStore, Loot4EveryoneChestProcessedPlayers> getComponentType() {
        ComponentType<ChunkStore, Loot4EveryoneChestProcessedPlayers> t = componentType;
        if (t == null) {
            throw new IllegalStateException("Loot4EveryoneChestProcessedPlayers not registered");
        }
        return t;
    }

    public boolean contains(@Nonnull UUID uuid) {
        return toUuidSet().contains(uuid);
    }

    public boolean add(@Nonnull UUID uuid) {
        Set<UUID> set = toUuidSet();
        boolean changed = set.add(uuid);
        if (changed) {
            this.playersCsv = toCsv(set);
        }
        return changed;
    }

    @Nonnull
    private Set<UUID> toUuidSet() {
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        if (this.playersCsv == null || this.playersCsv.isBlank()) {
            return out;
        }
        for (String part : this.playersCsv.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(UUID.fromString(t));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed entries written by older/bad data.
            }
        }
        return out;
    }

    @Nonnull
    private static String toCsv(@Nonnull Set<UUID> uuids) {
        if (uuids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : uuids) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(uuid);
        }
        return sb.toString();
    }

    @Override
    public Component<ChunkStore> clone() {
        Loot4EveryoneChestProcessedPlayers c = new Loot4EveryoneChestProcessedPlayers();
        c.playersCsv = this.playersCsv;
        return c;
    }
}
