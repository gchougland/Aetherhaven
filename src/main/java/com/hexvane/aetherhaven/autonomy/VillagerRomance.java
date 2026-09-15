package com.hexvane.aetherhaven.autonomy;

import java.util.List;

/** A short shared conversation, not a persistent relationship or a gift transaction. */
final class VillagerRomance {
    static final double CONVERSATION_CHANCE = .15;
    static final double RECIPROCATE_CHANCE = .6;

    record Beat(boolean firstSpeaks, String gesture, String voice, String bubble,
                boolean speechBubble, boolean hearts, String listenerGesture,
                String listenerBubble, boolean listenerHearts) {}

    private static final Beat AFFECTION = new Beat(true, "Greet", "Talk", "Love",
        true, true, "Surprise", null, false);
    private static final List<Beat> RECIPROCATED = List.of(AFFECTION,
        new Beat(false, "Agree", "Talk", "Love", true, true, "Agree", "Happy", false),
        new Beat(true, "Laugh", "Laugh", "Love", false, true, "Agree", "Love", true));
    private static final List<Beat> REJECTED = List.of(AFFECTION,
        new Beat(false, "Disagree", "Grumble", "Disagree", true, false, "Surprise", "Surprise", false),
        new Beat(true, "Bored", "Groan", "Bored", false, false, "Question", null, false));

    static List<Beat> choose(double conversationRoll, double responseRoll) {
        if (!(conversationRoll >= 0 && conversationRoll < CONVERSATION_CHANCE)) return List.of();
        return responseRoll >= 0 && responseRoll < RECIPROCATE_CHANCE ? RECIPROCATED : REJECTED;
    }

    private VillagerRomance() {}
}
