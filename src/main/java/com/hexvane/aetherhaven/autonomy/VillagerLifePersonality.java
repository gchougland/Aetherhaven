package com.hexvane.aetherhaven.autonomy;

import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityDefinition;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Blends actual townsfolk traits and named villagers' gift preferences into acting. */
final class VillagerLifePersonality {
    static final Set<String> ITEMS = loadItems();
    private static final Set<String> SOCIAL = Set.of("Explain", "Story", "Question", "Agree", "Laugh", "Surprise", "Disagree");
    private static final Set<String> IDLES = Set.of("LookAround", "Fidget", "Stretch", "Inspect", "Greet", "Laugh", "Sleepy", "Bored");
    private static final Set<String> REACTIONS = Set.of("Agree", "Laugh", "Surprise", "Disagree", "Question");

    private VillagerLifePersonality() {}

    private record Identity(List<TownsfolkPersonalityDefinition> traits, List<String> loves,
                            List<String> dislikes, String kind, String voice) {}

    private static Identity identity(Ref<EntityStore> ref, Store<EntityStore> store, AetherhavenPlugin plugin) {
        var binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        var character = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        String voice = null;
        List<TownsfolkPersonalityDefinition> traits = new ArrayList<>();
        List<String> loves = List.of();
        List<String> dislikes = List.of();
        if (character != null) {
            var definition = plugin.getTownsfolkCharacterCatalog().byId(character.getCharacterId());
            List<String> ids = character.getPersonalityIds();
            if (definition != null) {
                if (ids.isEmpty()) ids = definition.getPersonalityIds();
                voice = definition.getSpeechVoiceId();
            }
            if (ids.isEmpty() && !character.getActivePersonalityId().isBlank()) ids = List.of(character.getActivePersonalityId());
            for (String id : ids) {
                var trait = plugin.getTownsfolkPersonalityCatalog().byId(id);
                if (trait != null) traits.add(trait);
            }
        } else {
            var npc = store.getComponent(ref, NPCEntity.getComponentType());
            var definition = npc == null ? null : plugin.getVillagerDefinitionCatalog().byNpcRoleId(npc.getRoleName());
            if (definition != null) {
                loves = definition.getGiftLoves();
                dislikes = definition.getGiftDislikes();
                voice = definition.getSpeechVoiceId();
            }
        }
        return new Identity(traits, loves, dislikes, binding == null ? "" : binding.getKind(), voice);
    }

    static String voice(Ref<EntityStore> ref, UUID id, Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        String voice = plugin == null ? null : identity(ref, store, plugin).voice;
        var character = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        var definition = plugin == null || character == null ? null : plugin.getTownsfolkCharacterCatalog().byId(character.getCharacterId());
        return voiceFor(voice, definition == null ? null : definition.getGender(), id);
    }

    static String voiceFor(String configured, UUID id) {
        return voiceFor(configured, null, id);
    }

    static String voiceFor(String configured, String gender, UUID id) {
        if (configured != null) for (String profile : VillagerLifePolicy.VOICES)
            if (profile.equalsIgnoreCase(configured.replace(" ", ""))) return profile;
        String fallback = VillagerLifePolicy.voice(id);
        String tone = configured == null ? "" : switch (configured) {
            case "low" -> "Gravely"; case "high", "sharp" -> "Bright";
            case "soft" -> "Mellow"; case "mid" -> "Warm"; default -> "";
        };
        if (tone.isEmpty()) tone = fallback.replace("Female", "").replace("Male", "");
        String suffix = "female".equalsIgnoreCase(gender) ? "Female"
            : "male".equalsIgnoreCase(gender) ? "Male" : fallback.endsWith("Female") ? "Female" : "Male";
        return tone + suffix;
    }

    static String thought(Ref<EntityStore> ref, Store<EntityStore> store, AetherhavenPlugin plugin) {
        if (VillagerLifeContext.rainy(ref, store) && ThreadLocalRandom.current().nextDouble() < .35) return "Rain";
        if (VillagerLifeContext.needsHouse(ref, store, plugin) && ThreadLocalRandom.current().nextDouble() < .3) return "Home";
        Identity who = identity(ref, store, plugin);
        Map<String, Double> weights = topicWeights(who.traits, who.loves);
        String job = switch (who.kind) {
            case "merchant" -> "Aetherhaven_Gold_Coin";
            case "miner" -> "Tool_Pickaxe_Iron";
            case "logger" -> "Tool_Hatchet_Iron";
            case "blacksmith", "builder" -> "Tool_Hammer_Iron";
            case "farmer" -> "Plant_Crop_Carrot";
            case "rancher" -> "Tool_Feedbag";
            case "chef", "innkeeper" -> "Food_Pie_Meat";
            case "florist" -> "Plant_Flower_Blood_Rose";
            case "crystal_keeper" -> "Rock_Gem_Zephyr";
            case "bard", "clown" -> "Music";
            default -> "Home";
        };
        String topic = asTopic(job);
        if (topic != null) weights.merge(topic, 3.0, Double::sum);
        return choose(weights, ThreadLocalRandom.current().nextDouble(), "Home");
    }

    static Map<String, Double> topicWeights(List<TownsfolkPersonalityDefinition> traits, List<String> loves) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("Home", .3);
        for (var trait : traits) {
            trait.getThoughtItemWeights().forEach((item, weight) -> {
                String topic = asTopic(item);
                if (topic != null && valid(weight)) weights.merge(topic, weight * 4, Double::sum);
            });
        }
        for (String item : loves) if (ITEMS.contains(item)) weights.merge("Item_" + item, 2.0, Double::sum);
        return weights;
    }

    static String hungryThought(Ref<EntityStore> ref, Store<EntityStore> store, AetherhavenPlugin plugin) {
        Identity who = identity(ref, store, plugin);
        Map<String, Double> foods = topicWeights(who.traits, who.loves);
        foods.entrySet().removeIf(e -> !e.getKey().startsWith("Item_Food_"));
        return choose(foods, ThreadLocalRandom.current().nextDouble(), "Item_Food_Bread");
    }

    static String gesture(Ref<EntityStore> ref, Store<EntityStore> store, AetherhavenPlugin plugin, boolean idle, boolean reaction) {
        Identity who = identity(ref, store, plugin);
        Map<String, Double> weights = new LinkedHashMap<>();
        Set<String> allowed = idle ? IDLES : reaction ? REACTIONS : SOCIAL;
        for (var trait : who.traits) {
            (idle ? trait.getIdleEmoteWeights() : trait.getSocialEmoteWeights()).forEach((name, weight) -> {
                if (allowed.contains(name) && valid(weight)) weights.merge(name, weight, Double::sum);
            });
        }
        if (weights.isEmpty()) {
            if (idle) weights.putAll(Map.of("LookAround", 3.0, "Fidget", 2.0, "Stretch", 1.0));
            else if (reaction) weights.putAll(Map.of("Agree", 4.0, "Laugh", 2.0, "Surprise", 1.0, "Disagree", .5));
            else weights.putAll(Map.of("Explain", 4.0, "Story", 3.0, "Question", 2.0, "Laugh", 1.0));
        }
        return choose(weights, ThreadLocalRandom.current().nextDouble(), idle ? "LookAround" : "Agree");
    }

    static String feeling(Ref<EntityStore> ref, String topic, String gesture, Store<EntityStore> store, AetherhavenPlugin plugin) {
        Identity who = identity(ref, store, plugin);
        String item = topic.startsWith("Item_") ? topic.substring(5) : "";
        if (who.loves.contains(item)) return "Love";
        if (who.dislikes.contains(item)) return "Disagree";
        for (var trait : who.traits) {
            if (trait.getThoughtItemWeights().getOrDefault(item, 0.0) > 0) return "Love";
        }
        return switch (gesture) { case "Disagree" -> "Disagree"; case "Surprise", "Question" -> "Surprise"; default -> "Happy"; };
    }

    static String choose(Map<String, Double> weights, double random, String fallback) {
        double sum = weights.values().stream().filter(VillagerLifePersonality::valid).mapToDouble(Double::doubleValue).sum();
        if (sum <= 0 || !Double.isFinite(sum)) return fallback;
        double target = Math.max(0, Math.min(Math.nextDown(1.0), random)) * sum;
        for (var entry : weights.entrySet()) {
            if (!valid(entry.getValue())) continue;
            target -= entry.getValue();
            if (target < 0) return entry.getKey();
        }
        return fallback;
    }

    private static boolean valid(Double value) { return value != null && Double.isFinite(value) && value > 0; }

    private static String asTopic(String item) {
        if (ITEMS.contains(item)) return "Item_" + item;
        return Set.of("Home", "Rain", "Music", "Flower", "Food", "Work").contains(item) ? item : null;
    }

    private static Set<String> loadItems() {
        try (var stream = VillagerLifePersonality.class.getResourceAsStream("/defaults/villager_life_items.json")) {
            if (stream == null) return Set.of();
            return Set.copyOf(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().keySet());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Cannot read villager thought item catalog", e);
        }
    }
}
