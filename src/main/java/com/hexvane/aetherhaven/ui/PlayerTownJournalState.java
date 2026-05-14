package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Last opened tab in the Town Journal UI, persisted on the player entity. */
public final class PlayerTownJournalState implements Component<EntityStore> {
    public enum JournalTab {
        TOWN,
        GUIDE,
        QUESTS,
        SETTINGS;

        @Nonnull
        public static JournalTab fromPersisted(@Nullable String s) {
            if (s == null || s.isBlank()) {
                return QUESTS;
            }
            return switch (s.trim().toUpperCase()) {
                case "TOWN" -> TOWN;
                case "GUIDE" -> GUIDE;
                case "SETTINGS" -> SETTINGS;
                default -> QUESTS;
            };
        }

        @Nonnull
        public String persisted() {
            return name();
        }
    }

    @Nonnull
    public static final BuilderCodec<PlayerTownJournalState> CODEC =
        BuilderCodec.builder(PlayerTownJournalState.class, PlayerTownJournalState::new)
            .append(
                new KeyedCodec<>("LastTab", Codec.STRING),
                (c, v) -> c.lastTab = JournalTab.fromPersisted(v),
                c -> c.lastTab.persisted())
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PlayerTownJournalState> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(PlayerTownJournalState.class, "AetherhavenPlayerTownJournalState", PlayerTownJournalState.CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, PlayerTownJournalState> getComponentType() {
        ComponentType<EntityStore, PlayerTownJournalState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlayerTownJournalState not registered");
        }
        return t;
    }

    @Nonnull
    private JournalTab lastTab = JournalTab.QUESTS;

    public PlayerTownJournalState() {}

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PlayerTownJournalState c = new PlayerTownJournalState();
        c.lastTab = lastTab;
        return c;
    }

    @Nonnull
    public JournalTab getLastTab() {
        return lastTab;
    }

    public void setLastTab(@Nonnull JournalTab tab) {
        this.lastTab = tab;
    }
}
