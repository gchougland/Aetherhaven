package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.guild.GuardHireService;
import com.hexvane.aetherhaven.guild.GuildHallAdventurerPoolService;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Who appears in town resident lists, uses needs, and pays flat townsfolk tax. */
public final class TownResidentEligibility {
    private TownResidentEligibility() {}

    /**
     * House assignment picker: portal tourists and unhoused guards only appear after their housing quest is
     * accepted and this NPC is the bound quest target.
     */
    public static boolean excludeFromHouseAssignmentPicker(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        if (TouristPortalTickService.isActivePortalTourist(town, entityUuid)
            && !isQuestTargetForActiveQuest(town, entityUuid, AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK)) {
            return true;
        }
        if (GuardHireService.isUnhousedHiredGuard(town, entityUuid)
            && !isQuestTargetForActiveQuest(town, entityUuid, AetherhavenConstants.QUEST_HOUSE_GUARD)) {
            return true;
        }
        return false;
    }

    public static boolean excludeFromResidentLists(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull TownVillagerBinding binding
    ) {
        if (TownVillagerBinding.isVisitorKind(binding.getKind())) {
            return true;
        }
        return GuildHallAdventurerPoolService.isGuildHallAdventurer(town, entityUuid)
            || TouristPortalTickService.isActivePortalTourist(town, entityUuid);
    }

    public static boolean requiresHouseToAppear(
        @Nonnull String bindingKind,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin
    ) {
        return isTownsfolkPoolKind(bindingKind, roleId, plugin);
    }

    public static boolean includeInResidentList(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull TownVillagerBinding binding,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (excludeFromResidentLists(town, entityUuid, binding)) {
            return false;
        }
        if (requiresHouseToAppear(binding.getKind(), roleId, plugin)) {
            return town.isNpcHomeResidentOnHousePlot(entityUuid, constructionCatalog);
        }
        return true;
    }

    public static boolean includeInResidentList(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull String bindingKind,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (TownVillagerBinding.isVisitorKind(bindingKind)) {
            return false;
        }
        if (GuildHallAdventurerPoolService.isGuildHallAdventurer(town, entityUuid)) {
            return false;
        }
        if (TouristPortalTickService.isActivePortalTourist(town, entityUuid)) {
            return false;
        }
        if (requiresHouseToAppear(bindingKind, roleId, plugin)) {
            return town.isNpcHomeResidentOnHousePlot(entityUuid, constructionCatalog);
        }
        return true;
    }

    public static boolean usesVillagerNeeds(
        @Nonnull String bindingKind,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin
    ) {
        return !isTownsfolkPoolKind(bindingKind, roleId, plugin);
    }

    public static boolean countsAsTownsfolkTax(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull TownVillagerBinding binding,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (excludeFromResidentLists(town, entityUuid, binding)) {
            return false;
        }
        if (!isTownsfolkPoolKind(binding.getKind(), roleId, plugin)) {
            return false;
        }
        return town.isNpcHomeResidentOnHousePlot(entityUuid, constructionCatalog);
    }

    public static boolean countsAsTownsfolkTax(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull String bindingKind,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        if (TownVillagerBinding.isVisitorKind(bindingKind)) {
            return false;
        }
        if (GuildHallAdventurerPoolService.isGuildHallAdventurer(town, entityUuid)) {
            return false;
        }
        if (TouristPortalTickService.isActivePortalTourist(town, entityUuid)) {
            return false;
        }
        if (!isTownsfolkPoolKind(bindingKind, roleId, plugin)) {
            return false;
        }
        return town.isNpcHomeResidentOnHousePlot(entityUuid, constructionCatalog);
    }

    public static boolean isTownsfolkPoolKind(
        @Nonnull String bindingKind,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (TownVillagerBinding.KIND_GUARD.equals(bindingKind) || TownVillagerBinding.KIND_TOWNSFOLK.equals(bindingKind)) {
            return true;
        }
        return plugin.getTownsfolkCharacterCatalog().isTownsfolkRole(roleId);
    }

    private static boolean isQuestTargetForActiveQuest(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull String questId
    ) {
        return town.hasQuestActive(questId) && entityUuid.equals(town.getQuestTargetEntityUuid(questId));
    }
}
