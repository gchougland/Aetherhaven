package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.guild.GuardHireService;
import com.hexvane.aetherhaven.guild.GuildHallAdventurerPoolService;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.tourist.TouristRecord;
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

    /**
     * Live entity uuid for an active housing quest target (record uuid when reconciled, else stored quest target).
     */
    @Nullable
    public static UUID resolveLiveHousingQuestTargetUuid(@Nonnull TownRecord town, @Nonnull String questId) {
        if (!town.hasQuestActive(questId)) {
            return null;
        }
        UUID target = town.getQuestTargetEntityUuid(questId);
        if (target == null) {
            return null;
        }
        if (AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK.equals(questId)) {
            TouristRecord rec = TouristPortalTickService.findTouristRecord(town, target);
            if (rec != null && rec.getEntityUuid() != null) {
                return rec.getEntityUuid();
            }
            String characterId = characterIdForTouristQuestTarget(town, target);
            if (characterId != null) {
                for (TouristRecord tourist : town.getTouristRecords()) {
                    if (characterId.equalsIgnoreCase(tourist.getCharacterId()) && tourist.getEntityUuid() != null) {
                        return tourist.getEntityUuid();
                    }
                }
            }
            return target;
        }
        if (AetherhavenConstants.QUEST_HOUSE_GUARD.equals(questId)) {
            for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
                UUID guardUuid = rec.getEntityUuid();
                if (guardUuid != null && guardUuid.equals(target)) {
                    return guardUuid;
                }
            }
            String characterId = characterIdForGuardQuestTarget(town, target);
            if (characterId != null) {
                for (HiredGuardRecord guard : town.getHiredGuardRecords()) {
                    if (characterId.equalsIgnoreCase(guard.getCharacterId()) && guard.getEntityUuid() != null) {
                        return guard.getEntityUuid();
                    }
                }
            }
            return target;
        }
        return target;
    }

    @Nullable
    private static String characterIdForTouristQuestTarget(@Nonnull TownRecord town, @Nonnull UUID targetUuid) {
        TouristRecord rec = TouristPortalTickService.findTouristRecord(town, targetUuid);
        if (rec != null && rec.getCharacterId() != null && !rec.getCharacterId().isBlank()) {
            return rec.getCharacterId();
        }
        return null;
    }

    @Nullable
    private static String characterIdForGuardQuestTarget(@Nonnull TownRecord town, @Nonnull UUID targetUuid) {
        HiredGuardRecord rec = findHiredGuardRecord(town, targetUuid);
        if (rec != null && rec.getCharacterId() != null && !rec.getCharacterId().isBlank()) {
            return rec.getCharacterId();
        }
        return null;
    }

    private static boolean isQuestTargetForActiveQuest(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull String questId
    ) {
        if (!town.hasQuestActive(questId)) {
            return false;
        }
        UUID target = town.getQuestTargetEntityUuid(questId);
        if (target != null && entityUuid.equals(target)) {
            return true;
        }
        UUID liveTarget = resolveLiveHousingQuestTargetUuid(town, questId);
        if (liveTarget != null && entityUuid.equals(liveTarget)) {
            return true;
        }
        if (target == null) {
            return false;
        }
        if (AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK.equals(questId)) {
            TouristRecord entityRec = TouristPortalTickService.findTouristRecord(town, entityUuid);
            TouristRecord targetRec = TouristPortalTickService.findTouristRecord(town, target);
            if (entityRec != null && targetRec != null) {
                return entityRec.getCharacterId().equalsIgnoreCase(targetRec.getCharacterId());
            }
            if (entityRec != null
                && entityRec.isInvitedToStay()
                && TouristPortalTickService.findTouristRecord(town, target) == null) {
                return true;
            }
            return false;
        }
        if (AetherhavenConstants.QUEST_HOUSE_GUARD.equals(questId)) {
            HiredGuardRecord entityGuard = findHiredGuardRecord(town, entityUuid);
            HiredGuardRecord targetGuard = findHiredGuardRecord(town, target);
            if (entityGuard != null && targetGuard != null) {
                return entityGuard.getCharacterId().equalsIgnoreCase(targetGuard.getCharacterId());
            }
            if (entityGuard != null
                && !entityGuard.isCitizen()
                && findHiredGuardRecord(town, target) == null
                && countUnhousedHiredGuards(town) == 1) {
                return true;
            }
            return false;
        }
        return false;
    }

    private static int countUnhousedHiredGuards(@Nonnull TownRecord town) {
        int count = 0;
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            if (!rec.isCitizen() && rec.getEntityUuid() != null) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private static HiredGuardRecord findHiredGuardRecord(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(entityUuid)) {
                return rec;
            }
        }
        return null;
    }
}
