package com.hexvane.aetherhaven.bard;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.bard.data.BardSongDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public final class BardDialogueSongs {
    private BardDialogueSongs() {}

    @Nonnull
    public static List<DialogueChoiceDefinition> buildSongChoices(@Nonnull AetherhavenPlugin plugin) {
        List<DialogueChoiceDefinition> out = new ArrayList<>();
        for (BardSongDefinition song : plugin.getBardSongCatalog().songsOrdered()) {
            DialogueChoiceDefinition ch = new DialogueChoiceDefinition();
            ch.setText(song.getDisplayLangKey());
            JsonObject play = new JsonObject();
            play.addProperty("type", "play_bard_song");
            play.addProperty("songId", song.getId());
            ch.setActions(List.of(play));
            ch.setNext(null);
            out.add(ch);
        }
        return out;
    }

    @Nonnull
    public static List<DialogueChoiceDefinition> buildLoopSongChoices(@Nonnull AetherhavenPlugin plugin) {
        List<DialogueChoiceDefinition> out = new ArrayList<>();
        for (BardSongDefinition song : plugin.getBardSongCatalog().songsOrdered()) {
            DialogueChoiceDefinition ch = new DialogueChoiceDefinition();
            ch.setText(song.getDisplayLangKey());
            JsonObject play = new JsonObject();
            play.addProperty("type", "play_bard_song");
            play.addProperty("songId", song.getId());
            play.addProperty("loop", true);
            ch.setActions(List.of(play));
            ch.setNext("loop_started");
            out.add(ch);
        }
        return out;
    }
}
