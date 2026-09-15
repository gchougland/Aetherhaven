package com.hexvane.aetherhaven.speech;

/** Authorable recording categories and the expressive action paired with each one. */
public record DialogueSpeechCue(String clip, String gesture) {
    public static DialogueSpeechCue resolve(String requested) {
        String key = requested == null ? "talk" : requested.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (key) {
            case "none", "silent" -> new DialogueSpeechCue("None", "Explain");
            case "question", "quest" -> new DialogueSpeechCue("Question", "Question");
            case "thinking", "ponder" -> new DialogueSpeechCue("Thinking", "Ponder");
            case "laugh", "happy" -> new DialogueSpeechCue("Laugh", "Laugh");
            case "gasp", "surprise" -> new DialogueSpeechCue("Gasp", "Surprise");
            case "grumble", "disagree" -> new DialogueSpeechCue("Grumble", "Disagree");
            case "groan", "hungry" -> new DialogueSpeechCue("Groan", "Hungry");
            case "yawn", "sleepy" -> new DialogueSpeechCue("Yawn", "Sleepy");
            case "sigh", "bored" -> new DialogueSpeechCue("Sigh", "Bored");
            case "idle" -> new DialogueSpeechCue("Idle", "LookAround");
            case "work" -> new DialogueSpeechCue("Work", "LookAround");
            case "greet" -> new DialogueSpeechCue("Talk", "Greet");
            case "agree", "thanks" -> new DialogueSpeechCue("Talk", "Agree");
            default -> new DialogueSpeechCue("Talk", "Explain");
        };
    }

    public static String forNode(String explicit, String nodeId) {
        if (explicit != null && !explicit.isBlank()) return explicit;
        String id = nodeId == null ? "" : nodeId.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("thank") || id.contains("reward") || id.contains("complete")) return "Agree";
        if (id.contains("quest") || id.contains("offer")) return "Question";
        return "main_hub".equals(id) || "root".equals(id) ? "Greet" : "Talk";
    }
}
