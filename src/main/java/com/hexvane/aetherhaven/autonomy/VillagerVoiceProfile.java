package com.hexvane.aetherhaven.autonomy;

/** Fixed recording identity and optional two-semitone pitch variation; never seeded by an entity UUID. */
public record VillagerVoiceProfile(String id, String recording, float pitch, String variant) {
    public static VillagerVoiceProfile resolve(String requested, String gender, String race) {
        String key = requested == null ? "" : requested.replace(" ", "").trim();
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        String variant = lower.endsWith("higher") ? "Higher" : lower.endsWith("lower") ? "Lower" : "";
        String base = variant.isEmpty() ? key : key.substring(0, key.length() - variant.length());
        String canonical = null;
        for (String voice : VillagerLifePolicy.VOICES) if (voice.equalsIgnoreCase(base)) canonical = voice;
        if (canonical == null) {
            String tone = switch (base.toLowerCase(java.util.Locale.ROOT)) {
                case "low" -> "Gravely";
                case "high", "sharp" -> "Bright";
                case "soft" -> "Mellow";
                default -> "Warm";
            };
            canonical = tone + ("female".equalsIgnoreCase(gender) ? "Female" : "Male");
            variant = "";
        }
        if ("goblin".equalsIgnoreCase(race)) {
            String suffix = "female".equalsIgnoreCase(gender) ? "Female"
                : "male".equalsIgnoreCase(gender) ? "Male" : canonical.endsWith("Female") ? "Female" : "Male";
            canonical = "Gravely" + suffix;
        }
        float pitch = variant.equals("Higher") ? (float)Math.pow(2, 2.0/12)
            : variant.equals("Lower") ? (float)Math.pow(2, -2.0/12) : 1f;
        return new VillagerVoiceProfile(canonical + variant, canonical, pitch, variant);
    }

    public String actionsId() {
        return VillagerLifeVisuals.BODY_ANIMATIONS + (variant.isEmpty() ? "" : "_" + variant);
    }
}
