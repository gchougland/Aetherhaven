package com.hexvane.aetherhaven.speech;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerLifeVisuals;
import com.hexvane.aetherhaven.ui.DialoguePage;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Rapid choices replace queued responses instead of overlapping voiced clips. */
public final class NpcDialogueSpeech {
    private static final ConcurrentHashMap<UUID, Playback> PLAYBACK = new ConcurrentHashMap<>();
    private static final class Playback { volatile long generation; long untilMs; boolean closing; }
    private NpcDialogueSpeech() {}

    public static void startTalkSpeech(Ref<EntityStore> player, Ref<EntityStore> npc, Store<EntityStore> store) {
        startClip(player, npc, store, "Talk");
    }

    /** Compatibility for callers that previously supplied body text; no text-to-blip conversion remains. */
    public static void startTalkSpeech(Ref<EntityStore> player, Ref<EntityStore> npc, Store<EntityStore> store, String body) {
        startClip(player, npc, store, "Talk");
    }

    public static void startClip(Ref<EntityStore> player, Ref<EntityStore> npc, Store<EntityStore> store, String type) {
        startClip(player, npc, store, type, false);
    }

    public static void startClosingClip(Ref<EntityStore> player, Ref<EntityStore> npc, Store<EntityStore> store, String type) {
        startClip(player, npc, store, type, true);
    }

    private static void startClip(Ref<EntityStore> player, Ref<EntityStore> npc, Store<EntityStore> store, String type, boolean closing) {
        if (!player.isValid() || !npc.isValid()) return;
        var id = store.getComponent(player, UUIDComponent.getComponentType());
        if (id == null) return;
        var playback = PLAYBACK.computeIfAbsent(id.getUuid(), ignored -> new Playback());
        long generation = ++playback.generation;
        playback.closing = closing;
        if (DialogueSpeechCue.resolve(type).clip().equals("None")) {
            playback.closing = false;
            expire(id.getUuid(), playback);
            return;
        }
        long delay = Math.max(0, playback.untilMs - System.currentTimeMillis());
        if (delay == 0) { play(player, npc, store, type, playback); return; }
        var world = store.getExternalData().getWorld();
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> world.execute(() -> {
            if (PLAYBACK.get(id.getUuid()) != playback || playback.generation != generation || !player.isValid() || !npc.isValid()) return;
            var entity = store.getComponent(player, Player.getComponentType());
            if (entity == null || (!closing && !(entity.getPageManager().getCustomPage() instanceof DialoguePage))) return;
            play(player, npc, store, type, playback);
        }), delay, TimeUnit.MILLISECONDS);
    }

    private static void play(Ref<EntityStore> player, Ref<EntityStore> npc, Store<EntityStore> store, String type, Playback playback) {
        var plugin = AetherhavenPlugin.get();
        var preferences = store.getComponent(player, PlayerTownJournalState.getComponentType());
        boolean enabled = plugin != null && plugin.getConfig().get().isDialogueSpeechEnabled()
            && (preferences == null || preferences.isDialogueSpeechEnabled());
        float volume = enabled ? (preferences == null ? .7f : preferences.getDialogueSpeechVolumeLinear()) : 0;
        playback.untilMs = System.currentTimeMillis() + VillagerLifeVisuals.dialogue(player, npc, type, volume, store) + 120;
        playback.closing = false;
        var id = store.getComponent(player, UUIDComponent.getComponentType());
        if (id != null) expire(id.getUuid(), playback);
    }

    private static void expire(UUID player, Playback playback) {
        long generation = playback.generation;
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (playback.generation == generation) PLAYBACK.remove(player, playback);
        }, Math.max(1, playback.untilMs - System.currentTimeMillis()) + 1000, TimeUnit.MILLISECONDS);
    }

    public static void cancelForPlayer(UUID player) {
        var playback = PLAYBACK.get(player);
        if (playback == null || playback.closing) return;
        playback.generation++;
        // Preserve the current one-shot's end time through close/reopen.
        expire(player, playback);
    }
}
