package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import java.util.Map;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class AetherhavenLanguageTest {
    private static final String MOD = "aetherhaven_ui_shell.aetherhaven.ui.title";
    private static final String GAME = "server.items.Tool_Hoe.name";
    private static final String OTHER = "other_mod.title";

    @Test
    void preferenceDefaultsToGameLanguageAndSurvivesSaveLoadAndClone() {
        var state = PlayerTownJournalState.CODEC.decode(BsonDocument.parse("{}"), new ExtraInfo());
        assertFalse(state.isUseEnglishTranslations());
        state.setUseEnglishTranslations(true);
        var restored = PlayerTownJournalState.CODEC.decode(PlayerTownJournalState.CODEC.encode(state, new ExtraInfo()), new ExtraInfo());
        assertTrue(restored.isUseEnglishTranslations());
        assertTrue(((PlayerTownJournalState) restored.clone()).isUseEnglishTranslations());
        restored.setUseEnglishTranslations(false);
        assertFalse(PlayerTownJournalState.CODEC.decode(PlayerTownJournalState.CODEC.encode(restored, new ExtraInfo()), new ExtraInfo()).isUseEnglishTranslations());
    }

    @Test
    void overridingOnePlayersPacketDoesNotChangeSharedTranslationsOrOtherMods() {
        var shared = new UpdateTranslations(UpdateType.AddOrUpdate, Map.of(MOD, "Francais", GAME, "Houe", OTHER, "Autre"));
        var english = Map.of(MOD, "English", GAME, "Hoe", OTHER, "Other");
        var changed = AetherhavenLanguage.rewrite(shared, english);
        assertNotSame(shared, changed);
        assertEquals("English", changed.translations.get(MOD));
        assertEquals("Houe", changed.translations.get(GAME));
        assertEquals("Autre", changed.translations.get(OTHER));
        assertEquals("Francais", shared.translations.get(MOD));
    }

    @Test
    void laterLanguageUpdatesKeepEnglishAndFillMissingTranslations() {
        var update = new UpdateTranslations(UpdateType.AddOrUpdate, Map.of(MOD, "Deutsch"));
        String extra = "aetherhaven_backpack.aetherhaven.backpack.title";
        var changed = AetherhavenLanguage.rewrite(update, Map.of(MOD, "English", extra, "Backpack"));
        assertEquals(Map.of(MOD, "English", extra, "Backpack"), changed.translations);
    }

    @Test
    void removingATranslationRetainsEnglishFallbackButAllowsActualAssetRemoval() {
        var removed = new UpdateTranslations(UpdateType.Remove, Map.of(MOD, "Francais", GAME, "Houe", "aetherhaven_removed.title", "Old"));
        var changed = AetherhavenLanguage.rewrite(removed, Map.of(MOD, "English"));
        assertFalse(changed.translations.containsKey(MOD));
        assertTrue(changed.translations.containsKey(GAME));
        assertTrue(changed.translations.containsKey("aetherhaven_removed.title"));
        assertTrue(removed.translations.containsKey(MOD));
    }

    @Test
    void disablingRestoresOnlyTheModsLocalizedDictionary() {
        var localized = Map.of(MOD, "Francais", GAME, "Houe", OTHER, "Autre");
        assertEquals(Map.of(MOD, "Francais"), AetherhavenLanguage.ownMessages(localized));
        assertEquals("fr-FR", AetherhavenLanguage.languageFor(false, "fr-FR", MOD));
    }

    @Test
    void serverLabelsAndGuideUseEnglishWhileBaseGameAndOtherModsKeepTheirLanguage() {
        assertEquals("en-US", AetherhavenLanguage.languageFor(true, "fr-FR", MOD));
        assertEquals("en-US", AetherhavenLanguage.languageFor(true, "fr-FR", "aetherhaven.guide"));
        assertEquals("fr-FR", AetherhavenLanguage.languageFor(true, "fr-FR", GAME));
        assertEquals("fr-FR", AetherhavenLanguage.languageFor(true, "fr-FR", OTHER));
        assertEquals("en-US", AetherhavenLanguage.languageFor(false, null, MOD));
        assertTrue(AetherhavenLanguage.owns("server.memories.categories.Aetherhaven.title"));
        assertFalse(AetherhavenLanguage.owns("server.memories.categories.Other.title"));
    }
}
