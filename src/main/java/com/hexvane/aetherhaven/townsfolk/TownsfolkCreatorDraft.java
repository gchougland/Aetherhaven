package com.hexvane.aetherhaven.townsfolk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Predicate;

/** The editable sheet, independent of the UI and transport. Nothing here changes a live resident. */
public final class TownsfolkCreatorDraft {
    public static final int ATTRIBUTE_START = 5, ATTRIBUTE_POINTS = 5, ATTRIBUTE_CAP = 8;
    public static final int SKILL_START = 1, SKILL_POINTS = 10, SKILL_CAP = 5;
    public String name = "", gender = "male", race = "human", voice = "WarmMale";
    public final List<String> traits = new ArrayList<>(List.of("adventurer", "crafty", "grumpy"));
    public final Map<String, Integer> attributes = new LinkedHashMap<>(), skills = new LinkedHashMap<>();
    public final Map<String, Integer> items = new LinkedHashMap<>();
    public static final List<String> ASSIGNMENTS = List.of("idle", "tourist", "guard", "guild_adventurer");

    public TownsfolkCreatorDraft() {
        for (var attribute : TownsfolkAttribute.values()) attributes.put(attribute.id, ATTRIBUTE_START);
        for (var skill : TownsfolkSkill.values()) skills.put(skill.id, SKILL_START);
    }

    // Derive the balance from the scores, so lowering a score refunds exactly what raising it costs.
    public int attributePointsRemaining() {
        return TownsfolkAttribute.values().length * ATTRIBUTE_START + ATTRIBUTE_POINTS
            - attributes.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int skillPointsRemaining() {
        return TownsfolkSkill.values().length * SKILL_START + SKILL_POINTS
            - skills.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void adjust(String kind, String key, int delta) {
        if (delta != -1 && delta != 1) return;
        Map<String, Integer> values = switch (kind) {
            case "attribute" -> attributes; case "skill" -> skills; case "item" -> items; default -> Map.of();
        };
        if (!values.containsKey(key)) return;
        if (delta > 0 && ((kind.equals("attribute") && attributePointsRemaining() <= 0)
            || (kind.equals("skill") && skillPointsRemaining() <= 0))) return;
        int min = kind.equals("skill") ? 0 : 1;
        int max = kind.equals("attribute") ? ATTRIBUTE_CAP : kind.equals("skill") ? SKILL_CAP : 64;
        values.put(key, Math.clamp(values.get(key) + delta, min, max));
    }

    public void addItem(String id, Predicate<String> knownItem) {
        if (!knownItem.test(id) || (!items.containsKey(id) && items.size() >= 8)) return;
        items.put(id, Math.min(64, items.getOrDefault(id, 0) + 1));
    }

    public JsonObject toJson(UUID author, Set<String> knownTraits, Set<String> knownRaces, Set<String> knownVoices,
                             Predicate<String> knownItem) {
        String displayName = name.trim();
        require(!displayName.isEmpty() && displayName.length() <= 48 && displayName.chars().noneMatch(Character::isISOControl), "Enter a name (up to 48 characters).");
        require(List.of("male", "female").contains(gender) && knownRaces.contains(race), "Choose a gender and race.");
        require(traits.size() == 3 && new HashSet<>(traits).size() == 3 && knownTraits.containsAll(traits), "Choose three different personality traits.");
        require(knownVoices.contains(voice), "Choose a voice.");
        require(!items.isEmpty() && items.size() <= 8, "Choose at least one move-in item (up to eight).");
        items.forEach((id, count) -> require(knownItem.test(id) && count >= 1 && count <= 64, "Choose available items in amounts from 1 to 64."));
        for (var attribute : TownsfolkAttribute.values()) requireScore(attributes.get(attribute.id), 1, ATTRIBUTE_CAP);
        for (var skill : TownsfolkSkill.values()) requireScore(skills.get(skill.id), 0, SKILL_CAP);
        require(attributePointsRemaining() >= 0, "You have spent too many attribute points. Lower an attribute to refund points.");
        require(skillPointsRemaining() >= 0, "You have spent too many skill points. Lower a skill to refund points.");
        String slug = Normalizer.normalize(displayName, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.length() > 24) slug = slug.substring(0, 24);
        String id = "community_" + (slug.isEmpty() ? "resident" : slug) + "_" + author.toString().replace("-", "").substring(0, 12);
        Gson gson = new Gson();
        JsonObject json = new JsonObject();
        json.addProperty("id", id); json.addProperty("displayName", displayName);
        json.addProperty("gender", gender); json.addProperty("race", race);
        json.addProperty("modelAssetId", "Townsfolk_" + id);
        json.add("personalityIds", gson.toJsonTree(traits)); json.add("allowedAssignmentKinds", gson.toJsonTree(ASSIGNMENTS));
        json.addProperty("befriendable", false); json.addProperty("speechVoiceId", voice);
        json.add("startingAttributes", gson.toJsonTree(attributes)); json.add("startingSkills", gson.toJsonTree(skills));
        json.add("moveInRequirements", gson.toJsonTree(items.entrySet().stream().map(e -> Map.of("itemId", e.getKey(), "count", e.getValue())).toList()));
        return json;
    }

    private static void requireScore(Integer value, int min, int max) { require(value != null && value >= min && value <= max, "A starting score is outside its allowed range."); }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
}
