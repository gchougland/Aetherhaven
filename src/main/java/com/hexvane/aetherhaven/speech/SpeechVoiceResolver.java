package com.hexvane.aetherhaven.speech;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.speech.data.SpeechVoiceCatalog;
import com.hexvane.aetherhaven.speech.data.SpeechVoiceDefinition;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves which {@link SpeechVoiceDefinition} an NPC should use while talking. */
public final class SpeechVoiceResolver {
    private static final String[] HASH_VOICES = { "high", "mid", "low", "soft", "sharp" };

    private static final Set<String> HIGH = Set.of(
        "cheerful", "silly", "mischievous", "gossipy", "curious", "sporty", "adventurer", "musical"
    );
    private static final Set<String> LOW = Set.of("stoic", "grumpy", "bitter", "cynical", "rude", "blunt", "gloomy");
    private static final Set<String> SOFT = Set.of("shy", "lazy", "homebody", "bookworm", "gardener", "foodie");
    private static final Set<String> SHARP = Set.of("snooty", "dramatic", "impatient", "stingy", "crafty");

    private SpeechVoiceResolver() {}

    @Nonnull
    public static SpeechVoiceDefinition resolve(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        SpeechVoiceCatalog catalog = plugin != null ? plugin.getSpeechVoiceCatalog() : SpeechVoiceCatalog.empty();

        String explicit = explicitVoiceId(npcRef, store, plugin);
        if (explicit != null) {
            return catalog.requireOrDefault(explicit);
        }

        String fromTownsfolk = townsfolkFallbackVoiceId(npcRef, store, plugin);
        if (fromTownsfolk != null) {
            return catalog.requireOrDefault(fromTownsfolk);
        }

        String roleId = roleId(npcRef, store);
        if (!roleId.isEmpty()) {
            return catalog.requireOrDefault(hashVoiceId(roleId));
        }
        return catalog.requireOrDefault(SpeechVoiceDefinition.DEFAULT_VOICE_ID);
    }

    @Nullable
    private static String explicitVoiceId(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable AetherhavenPlugin plugin
    ) {
        if (plugin == null) {
            return null;
        }
        TownsfolkCharacterBinding binding = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
        if (binding != null) {
            TownsfolkCharacterDefinition character = plugin.getTownsfolkCharacterCatalog().byId(binding.getCharacterId());
            if (character != null) {
                String v = character.getSpeechVoiceId();
                if (v != null) {
                    return v;
                }
            }
        }
        String roleId = roleId(npcRef, store);
        if (!roleId.isEmpty()) {
            VillagerDefinition def = plugin.getVillagerDefinitionCatalog().byNpcRoleId(roleId);
            if (def != null) {
                return def.getSpeechVoiceId();
            }
        }
        return null;
    }

    /**
     * Gender + personality blend for townsfolk without an explicit {@code speechVoiceId}.
     * Feminine baseline leans soft/high; masculine leans mid/low; personalities and race nudge further.
     */
    @Nullable
    private static String townsfolkFallbackVoiceId(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable AetherhavenPlugin plugin
    ) {
        if (plugin == null) {
            return null;
        }
        TownsfolkCharacterBinding binding = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
        if (binding == null) {
            return null;
        }
        TownsfolkCharacterDefinition character = plugin.getTownsfolkCharacterCatalog().byId(binding.getCharacterId());
        if (character == null) {
            return null;
        }
        return pickFromGenderPersonalityRace(
            character.getGender(),
            character.getRace(),
            character.getPersonalityIds(),
            character.getId()
        );
    }

    @Nonnull
    static String pickFromGenderPersonalityRace(
        @Nullable String gender,
        @Nullable String race,
        @Nonnull List<String> personalities,
        @Nonnull String characterId
    ) {
        int high = 0;
        int low = 0;
        int soft = 0;
        int sharp = 0;
        int mid = 0;
        for (String raw : personalities) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String p = raw.trim().toLowerCase(Locale.ROOT);
            if (HIGH.contains(p)) {
                high += 2;
            } else if (LOW.contains(p)) {
                low += 2;
            } else if (SOFT.contains(p)) {
                soft += 2;
            } else if (SHARP.contains(p)) {
                sharp += 2;
            } else {
                mid += 1;
            }
        }

        String r = race != null ? race.trim().toLowerCase(Locale.ROOT) : "";
        if (r.contains("goblin")) {
            sharp += 2;
            high += 1;
        } else if (r.contains("dark")) {
            soft += 1;
            sharp += 1;
        } else if (r.contains("elf") || r.contains("sylvian")) {
            soft += 1;
            high += 1;
        }

        String g = gender != null ? gender.trim().toLowerCase(Locale.ROOT) : "";
        if ("female".equals(g)) {
            soft += 2;
            high += 1;
            low = Math.max(0, low - 1);
        } else if ("male".equals(g)) {
            mid += 1;
            low += 1;
        }

        String voice = bestVoice(high, low, soft, sharp, mid);
        if ("female".equals(g) && "low".equals(voice) && low < 3) {
            voice = "soft";
        }
        if ("male".equals(g) && "soft".equals(voice) && soft < 3 && high == 0) {
            voice = "mid";
        }
        if (voice.isEmpty()) {
            return hashVoiceId(characterId.isEmpty() ? "mid" : characterId);
        }
        return voice;
    }

    @Nonnull
    private static String bestVoice(int high, int low, int soft, int sharp, int mid) {
        int bestScore = -1;
        String best = "mid";
        // Tie-break order favors variety rather than always mid.
        String[] order = { "high", "sharp", "soft", "mid", "low" };
        int[] scores = { high, sharp, soft, mid, low };
        for (int i = 0; i < order.length; i++) {
            if (scores[i] > bestScore) {
                bestScore = scores[i];
                best = order[i];
            }
        }
        return best;
    }

    @Nonnull
    private static String hashVoiceId(@Nonnull String key) {
        int h = Math.floorMod(key.hashCode(), HASH_VOICES.length);
        return HASH_VOICES[h];
    }

    @Nonnull
    private static String roleId(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null) {
            return "";
        }
        return npc.getRoleName().trim();
    }
}
