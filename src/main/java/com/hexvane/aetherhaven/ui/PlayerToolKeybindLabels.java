package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves per-player tool key display labels from {@link PlayerTownJournalState}. */
public final class PlayerToolKeybindLabels {
    private PlayerToolKeybindLabels() {}

    @Nonnull
    public static String resolve(@Nonnull PlayerTownJournalState state, @Nonnull ToolKeybindSlot slot) {
        String stored = state.getToolKeyLabel(slot);
        if (stored == null || stored.isBlank()) {
            return slot.defaultLabel();
        }
        return stored.trim();
    }

    @Nonnull
    public static String resolveAbstract(@Nonnull PlayerTownJournalState state, @Nonnull String abstractKey) {
        ToolKeybindSlot slot = ToolKeybindSlot.fromAbstractKey(abstractKey);
        if (slot != null) {
            return resolve(state, slot);
        }
        return abstractKey;
    }

    /** Param names for lang strings: primary, secondary, use, ability1, ability2, ability3, escape, shift, ctrl, space, movement. */
    @Nonnull
    public static PlayerTownJournalState journalOrDefaults(@Nonnull PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return new PlayerTownJournalState();
        }
        Store<EntityStore> store = ref.getStore();
        PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        return journal != null ? journal : new PlayerTownJournalState();
    }

    @Nonnull
    public static PlayerTownJournalState journalOrDefaults(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
        return journal != null ? journal : new PlayerTownJournalState();
    }

    @Nonnull
    public static Message paramMessage(@Nonnull PlayerTownJournalState state, @Nonnull String langKey) {
        Message m = Message.translation(langKey);
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            m = m.param(slot.langSuffix(), resolve(state, slot));
        }
        return m;
    }

    @Nonnull
    public static String sanitizeInput(@Nullable String raw, @Nonnull ToolKeybindSlot slot) {
        if (raw == null) {
            return slot.defaultLabel();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 24) {
            return slot.defaultLabel();
        }
        return trimmed;
    }
}
