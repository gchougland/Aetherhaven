package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import java.util.Locale;
import java.util.Map;

public final class TownsfolkProgressService {
    private TownsfolkProgressService() {}

    public static boolean canTrain(TownRecord town, java.util.UUID player, java.util.UUID resident) {
        return town != null && player != null && town.hasMemberOrOwner(player)
            && town.playerCanManageConstructions(player)
            && com.hexvane.aetherhaven.town.TownAdmission.isMember(town, resident);
    }

    public static TownsfolkProgress getOrCreate(TownRecord town, TownManager manager,
                                               AetherhavenPlugin plugin, String characterId) {
        String key = characterId.trim().toLowerCase(Locale.ROOT);
        var existing = town.getTownsfolkProgress().get(key);
        if (existing != null) return existing;
        var definition = plugin.getTownsfolkCharacterCatalog().byId(characterId);
        var equipment = definition == null || definition.getEquipmentProfileId() == null ? null
            : plugin.getEquipmentProfileCatalog().byId(definition.getEquipmentProfileId());
        var progress = startingProgress(definition, equipment == null ? null : equipment.getGuardNpcRole());
        if (definition != null) {
            for (String personalityId : definition.getPersonalityIds()) {
                var personality = plugin.getTownsfolkPersonalityCatalog().byId(personalityId);
                if (personality != null) progress.applyStartingValues(
                    personality.getStartingAttributes(), personality.getStartingSkills(), false);
            }
            progress.applyStartingValues(definition.getStartingAttributes(), definition.getStartingSkills(), true);
        }
        town.getTownsfolkProgress().put(key, progress);
        manager.updateTown(town);
        return progress;
    }

    public static TownsfolkProgress startingProgress(TownsfolkCharacterDefinition definition) {
        return startingProgress(definition, null);
    }

    public static TownsfolkProgress startingProgress(TownsfolkCharacterDefinition definition, String guardRole) {
        var progress = new TownsfolkProgress();
        if (definition == null) return progress;
        String profile = guardRole != null ? guardRole : definition.getEquipmentProfileId();
        if (profile != null) {
            profile = profile.toLowerCase(Locale.ROOT);
            Map<String, Integer> attributes = profile.contains("mage") ? Map.of("intelligence", 8)
                : profile.contains("archer") ? Map.of("dexterity", 8, "speed", 6)
                : profile.contains("rogue") ? Map.of("dexterity", 7, "speed", 8)
                : profile.contains("knight") ? Map.of("strength", 8, "vitality", 7) : Map.of();
            progress.applyStartingValues(attributes, Map.of(), false);
        }
        return progress;
    }
}
