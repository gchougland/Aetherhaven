package com.hexvane.aetherhaven.leveling;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Raid snapshots survive unloading/restarts. Applied state must be rebuilt for each loaded entity. */
public final class NpcLevelState implements Component<EntityStore> {
    public static final BuilderCodec<NpcLevelState> CODEC = BuilderCodec.builder(NpcLevelState.class, NpcLevelState::new)
        .append(new KeyedCodec<>("Provider", Codec.STRING), (s, v) -> s.provider = v, s -> s.provider).add()
        .append(new KeyedCodec<>("RaidLevel", Codec.INTEGER), (s, v) -> s.raidLevel = v, s -> s.raidLevel).add()
        .build();
    private static ComponentType<EntityStore, NpcLevelState> type;
    String provider = "";
    int raidLevel;
    transient int appliedLevel;

    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        type = registry.registerComponent(NpcLevelState.class, "AetherhavenNpcLevel", CODEC);
    }
    public static ComponentType<EntityStore, NpcLevelState> getComponentType() { return type; }

    int target(String activeProvider, int ownerLevel, boolean raid) {
        if (raid && raidLevel > 0) return provider.equals(activeProvider) ? raidLevel : 0;
        if (ownerLevel <= 0) return 0;
        provider = activeProvider;
        if (raid) raidLevel = ownerLevel;
        return ownerLevel;
    }

    @Override public NpcLevelState clone() {
        var copy = new NpcLevelState();
        copy.provider = provider;
        copy.raidLevel = raidLevel;
        return copy;
    }
}
