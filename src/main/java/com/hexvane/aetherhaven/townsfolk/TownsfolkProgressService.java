package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import java.util.Locale;

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
        var progress = startingProgress(definition);
        town.getTownsfolkProgress().put(key, progress);
        manager.updateTown(town);
        return progress;
    }

    public static TownsfolkProgress startingProgress(TownsfolkCharacterDefinition definition) {
        var progress = new TownsfolkProgress();
        if (definition != null)
            progress.applyStartingValues(definition.getStartingAttributes(), definition.getStartingSkills(), true);
        return progress;
    }
}
