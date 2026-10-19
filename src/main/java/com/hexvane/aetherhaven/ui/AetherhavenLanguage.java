package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** Per-player overrides of this mod's dictionary; never changes the player's game language or shared packets. */
public final class AetherhavenLanguage {
    private static final Map<UUID, Boolean> ENGLISH = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> SENDING = ThreadLocal.withInitial(() -> false);
    private static PacketFilter outbound;

    private AetherhavenLanguage() {}

    public static void register(AetherhavenPlugin plugin) {
        outbound = PacketAdapters.registerOutbound((PlayerPacketFilter) (player, packet) -> {
            if (SENDING.get() || !isEnglish(player) || !(packet instanceof UpdateTranslations update)) return false;
            I18nModule i18n = I18nModule.get();
            if (i18n == null) return false;
            // A language switch or asset reload must not undo the preference. Clone before overlaying:
            // broadcasts can reuse the original packet for players with different settings.
            send(player, rewrite(update, i18n.getMessages("en-US")));
            return true;
        });
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
            event -> ENGLISH.remove(event.getPlayerRef().getUuid()));
    }

    public static void shutdown() {
        if (outbound != null) PacketAdapters.deregisterOutbound(outbound);
        outbound = null;
        ENGLISH.clear();
    }

    /** Called from the player tick and the settings action, after the persisted component has loaded. */
    public static void sync(PlayerRef player, boolean english) {
        Boolean previous = ENGLISH.put(player.getUuid(), english);
        if (previous == null && !english || previous != null && previous == english) return;
        I18nModule i18n = I18nModule.get();
        if (i18n == null) { ENGLISH.remove(player.getUuid()); return; }
        send(player, new UpdateTranslations(UpdateType.AddOrUpdate,
            ownMessages(i18n.getMessages(english ? "en-US" : player.getLanguage()))));
        player.getPacketHandler().tryFlush();
    }

    private static void send(PlayerRef player, UpdateTranslations packet) {
        SENDING.set(true);
        try {
            player.getPacketHandler().writeNoCache(packet);
        } finally {
            SENDING.remove();
        }
    }

    public static boolean isEnglish(@Nullable PlayerRef player) {
        return player != null && Boolean.TRUE.equals(ENGLISH.get(player.getUuid()));
    }

    public static String modLanguage(@Nullable PlayerRef player) {
        return languageFor(isEnglish(player), player == null ? null : player.getLanguage(), "aetherhaven.guide");
    }

    /** Server-rendered labels must use the same per-key choice as client-rendered translations. */
    @Nullable
    public static String message(@Nullable PlayerRef player, String key) {
        I18nModule i18n = I18nModule.get();
        return i18n == null ? null : i18n.getMessage(languageFor(isEnglish(player),
            player == null ? null : player.getLanguage(), key), key);
    }

    static String languageFor(boolean english, @Nullable String gameLanguage, String key) {
        return english && owns(key) || gameLanguage == null || gameLanguage.isBlank() ? "en-US" : gameLanguage;
    }

    static UpdateTranslations rewrite(UpdateTranslations update, Map<String, String> english) {
        if (update.type != UpdateType.Remove) {
            return new UpdateTranslations(update.type, overlay(update.translations, english));
        }
        // Removing a translated line must retain its English fallback for this player.
        var removed = new HashMap<String, String>();
        if (update.translations != null) update.translations.forEach((key, value) -> {
            if (!owns(key) || !english.containsKey(key)) removed.put(key, value);
        });
        return new UpdateTranslations(UpdateType.Remove, removed);
    }

    static boolean owns(String key) {
        return key != null && (key.startsWith("aetherhaven_") || key.startsWith("aetherhaven.")
            || key.startsWith("server.memories.categories.Aetherhaven."));
    }

    static Map<String, String> ownMessages(Map<String, String> source) {
        var result = new HashMap<String, String>();
        source.forEach((key, value) -> { if (owns(key)) result.put(key, value); });
        return result;
    }

    static Map<String, String> overlay(@Nullable Map<String, String> original, Map<String, String> english) {
        var result = new HashMap<String, String>();
        if (original != null) result.putAll(original);
        result.putAll(ownMessages(english));
        return result;
    }
}
