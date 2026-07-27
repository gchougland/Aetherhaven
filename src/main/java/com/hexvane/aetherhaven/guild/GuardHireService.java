package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.equipment.VillagerEquipmentService;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hexvane.aetherhaven.questboard.TownRankCapacity;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hexvane.aetherhaven.villager.audit.VillagerAuditContext;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolCheckoutRecord;
import com.hexvane.aetherhaven.ui.GuardRoleLabels;
import java.util.List;
import com.hexvane.aetherhaven.villager.NpcModelSpawnUtil;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class GuardHireService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private GuardHireService() {}

    public static boolean canAfford(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull CombinedItemContainer inventory,
        @Nonnull UUID playerUuid,
        @Nonnull String profileId
    ) {
        EquipmentProfileDefinition profile = plugin.getEquipmentProfileCatalog().byId(profileId);
        if (profile == null) {
            return false;
        }
        long cost = profile.getHireGoldCost();
        return GoldCoinPayment.canAfford(town, inventory, cost, town.playerCanSpendTreasuryGold(playerUuid));
    }

    public static long hireCost(@Nonnull AetherhavenPlugin plugin, @Nonnull String profileId) {
        EquipmentProfileDefinition profile = plugin.getEquipmentProfileCatalog().byId(profileId);
        return profile != null ? profile.getHireGoldCost() : 0L;
    }

    @Nullable
    public static String equipmentProfileForNpc(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        TownsfolkCharacterBinding tb = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
        if (tb == null) {
            return null;
        }
        TownsfolkCharacterDefinition def = plugin.getTownsfolkCharacterCatalog().byId(tb.getCharacterId());
        if (def == null) {
            return null;
        }
        String profileId = def.getEquipmentProfileId();
        return profileId != null ? profileId : "guard_knight";
    }

    @Nonnull
    public static String guardNpcRoleForNpc(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        String profileId = equipmentProfileForNpc(plugin, npcRef, store);
        if (profileId == null) {
            return AetherhavenConstants.NPC_GUARD_KNIGHT;
        }
        EquipmentProfileDefinition profile = plugin.getEquipmentProfileCatalog().byId(profileId);
        return profile != null ? profile.getGuardNpcRole() : AetherhavenConstants.NPC_GUARD_KNIGHT;
    }

    @Nonnull
    public static String guardTypeLangKeyForNpc(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        return GuardRoleLabels.guardTypeLangKey(guardNpcRoleForNpc(plugin, npcRef, store));
    }

    public static boolean tryHire(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null || !town.hasMemberOrOwner(pu.getUuid())) {
            return false;
        }
        UUID adventurerUuid = npcUuid(store, npcRef);
        if (adventurerUuid == null || !GuildHallAdventurerPoolService.isGuildHallAdventurer(town, adventurerUuid)) {
            return false;
        }
        TownsfolkCharacterBinding tb = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (tb == null || binding == null || !town.getTownId().equals(binding.getTownId())) {
            return false;
        }
        if (!TownsfolkAssignmentKinds.isGuildHallAdventurer(tb.getAssignmentKind())) {
            return false;
        }

        TownsfolkPoolCheckoutRecord checkout =
            TownsfolkExistenceService.checkoutForCharacter(world, plugin, town.getTownId(), tb.getCharacterId());
        if (checkout == null) {
            LOGGER.atWarning().log("Cannot hire %s: missing townsfolk ledger entry", tb.getCharacterId());
            return false;
        }

        if (!TownRankCapacity.canHireGuard(town, plugin.getQuestBoardCatalog())) {
            return false;
        }

        String profileId = equipmentProfileForNpc(plugin, npcRef, store);
        if (profileId == null) {
            return false;
        }
        EquipmentProfileDefinition profile = plugin.getEquipmentProfileCatalog().byId(profileId);
        if (profile == null) {
            return false;
        }
        long cost = profile.getHireGoldCost();
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inv == null) {
            return false;
        }
        if (cost > 0 && !GoldCoinPayment.trySpend(town, inv, cost, town.playerCanSpendTreasuryGold(pu.getUuid()))) {
            return false;
        }

        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return false;
        }
        Vector3d spawnPos = new Vector3d(tc.getPosition());
        Rotation3f spawnRot = new Rotation3f(tc.getRotation());

        var guildPlot = town.findCompletePlotWithConstruction(
            plugin.getConstructionCatalog(),
            AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL
        );
        UUID jobPlot = guildPlot != null ? guildPlot.getPlotId() : null;

        GuardHireCleanup.prepareForGuardDuty(npcRef, store);
        Ref<EntityStore> guardRef = spawnHiredGuard(
            world,
            plugin,
            store,
            profile,
            townsfolkBindingForGuard(tb),
            town,
            spawnPos,
            spawnRot,
            jobPlot,
            guildPlot
        );
        if (guardRef == null) {
            LOGGER.atWarning().log("Failed to spawn hired guard for townsfolk %s in town %s", tb.getCharacterId(), town.getTownId());
            return false;
        }

        UUIDComponent guardUuidComp = store.getComponent(guardRef, UUIDComponent.getComponentType());
        if (guardUuidComp == null) {
            VillagerAuditContext.removeEntity(store, guardRef, "guard_hire_replace");
            return false;
        }
        UUID newEntityUuid = guardUuidComp.getUuid();

        if (!TownsfolkExistenceService.transferInstanceOnHire(world, plugin, tb.getCharacterId(), newEntityUuid, town.getTownId())) {
            VillagerAuditContext.removeEntity(store, guardRef, "guard_hire_replace");
            return false;
        }

        VillagerAuditContext.removeEntity(store, npcRef, "guard_hire_replace");
        TownsfolkExistenceService.purgeDuplicateEntities(world, store, town.getTownId(), tb.getCharacterId(), newEntityUuid);

        Integer hiredSlot = town.getGuildHallAdventurerSlotByNpcId().get(adventurerUuid.toString());
        town.getGuildHallAdventurerNpcIds().removeIf(s -> adventurerUuid.toString().equalsIgnoreCase(s != null ? s.trim() : ""));
        town.getGuildHallAdventurerSlotByNpcId().remove(adventurerUuid.toString());
        if (hiredSlot != null) {
            town.getGuildHallAdventurerFilledSlots().remove(hiredSlot);
        }

        town.getHiredGuardRecords().removeIf(r -> tb.getCharacterId().equalsIgnoreCase(r.getCharacterId()));
        town.getHiredGuardRecords().add(new HiredGuardRecord(tb.getCharacterId(), newEntityUuid, profileId, false));
        tm.updateTown(town);

        LOGGER.atInfo().log("Hired guard %s for town %s", tb.getCharacterId(), town.getTownId());
        return true;
    }

    /**
     * Respawns a hired guard near {@code spawnPos} (e.g. after {@code /ah villager reset}). Keeps character identity,
     * equipment, and display name. Updates the townsfolk ledger when a checkout row exists.
     *
     * @return new entity uuid, or null on failure
     */
    @Nullable
    public static UUID respawnHiredGuardAtPosition(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull String characterId,
        @Nonnull String equipmentProfileId,
        @Nonnull TownsfolkCharacterBinding characterBinding,
        @Nonnull Vector3d spawnPos,
        @Nullable UUID jobPlotId
    ) {
        String profileId = equipmentProfileId.trim();
        if (profileId.isEmpty()) {
            profileId = "guard_knight";
        }
        EquipmentProfileDefinition profile = plugin.getEquipmentProfileCatalog().byId(profileId);
        if (profile == null) {
            LOGGER.atWarning().log("Cannot respawn guard %s: unknown equipment profile %s", characterId, profileId);
            return null;
        }
        PlotInstance guildPlot = town.findCompletePlotWithConstruction(
            plugin.getConstructionCatalog(),
            AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL
        );
        if (jobPlotId == null && guildPlot != null) {
            jobPlotId = guildPlot.getPlotId();
        }
        Ref<EntityStore> guardRef = spawnHiredGuard(
            world,
            plugin,
            store,
            profile,
            characterBinding,
            town,
            spawnPos,
            new Rotation3f(),
            jobPlotId,
            guildPlot
        );
        if (guardRef == null) {
            return null;
        }
        UUIDComponent uc = store.getComponent(guardRef, UUIDComponent.getComponentType());
        if (uc == null) {
            return null;
        }
        UUID newUuid = uc.getUuid();
        if (!TownsfolkExistenceService.transferInstanceOnHire(world, plugin, characterId, newUuid, town.getTownId())) {
            LOGGER.atFine().log("Respawned guard %s without townsfolk ledger checkout update", characterId);
        }
        return newUuid;
    }

    @Nonnull
    private static TownsfolkCharacterBinding townsfolkBindingForGuard(@Nonnull TownsfolkCharacterBinding tb) {
        return new TownsfolkCharacterBinding(
            tb.getCharacterId(),
            tb.getActivePersonalityId(),
            TownsfolkAssignmentKinds.GUARD,
            tb.getModelAssetId(),
            tb.getPersonalityIds()
        );
    }

    @Nonnull
    public static TownsfolkCharacterBinding characterBindingFromCatalog(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String characterId
    ) {
        String cid = characterId.trim();
        TownsfolkCharacterDefinition character = plugin.getTownsfolkCharacterCatalog().byId(cid);
        if (character == null) {
            return new TownsfolkCharacterBinding(cid, "", TownsfolkAssignmentKinds.GUARD, "", List.of());
        }
        return new TownsfolkCharacterBinding(
            cid,
            "",
            TownsfolkAssignmentKinds.GUARD,
            character.getModelAssetId(),
            character.getPersonalityIds()
        );
    }

    @Nullable
    private static Ref<EntityStore> spawnHiredGuard(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull EquipmentProfileDefinition profile,
        @Nonnull TownsfolkCharacterBinding tb,
        @Nonnull TownRecord town,
        @Nonnull Vector3d spawnPos,
        @Nonnull Rotation3f spawnRot,
        @Nullable UUID jobPlot,
        @Nullable PlotInstance guildPlot
    ) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            return null;
        }
        String guardRole = profile.getGuardNpcRole();
        int roleIndex = npcPlugin.getIndex(guardRole);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Cannot spawn hired guard: unknown NPC role %s (profile %s)", guardRole, profile.getId());
            return null;
        }

        TownsfolkCharacterDefinition character = plugin.getTownsfolkCharacterCatalog().byId(tb.getCharacterId());
        Model spawnModel = null;
        final float spawnScale;
        if (character != null) {
            spawnModel = NpcModelSpawnUtil.buildScaledModel(character.getModelAssetId(), character.getModelScale());
            spawnScale = spawnModel != null ? spawnModel.getScale() : 1.0f;
        } else {
            spawnScale = 1.0f;
        }

        var pair = npcPlugin.spawnEntity(
            store,
            roleIndex,
            spawnPos,
            spawnRot,
            spawnModel,
            (npcEntity, holder, st) -> npcEntity.setInitialModelScale(spawnScale),
            null
        );
        if (pair == null) {
            return null;
        }
        Ref<EntityStore> guardRef = pair.first();

        if (character != null) {
            String displayName = character.getDisplayName();
            if (displayName != null) {
                store.putComponent(guardRef, PersistentDisplayName.getComponentType(), new PersistentDisplayName(Message.raw(displayName)));
            }
        }

        String handle = "Guard_" + tb.getCharacterId() + "_" + shortHex(town.getTownId());
        store.putComponent(guardRef, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(handle));
        store.putComponent(
            guardRef,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_GUARD, jobPlot, jobPlot)
        );
        store.putComponent(guardRef, TownsfolkCharacterBinding.getComponentType(), tb);
        NpcSpawnOriginUtil.attach(
            store,
            guardRef,
            "GUARD_HIRE",
            "characterId=" + tb.getCharacterId() + ",guardRole=" + guardRole,
            world,
            spawnPos
        );

        NPCEntity npc = store.getComponent(guardRef, NPCEntity.getComponentType());
        if (npc != null) {
            if (guildPlot != null) {
                npc.setLeashPoint(GuardHireCleanup.patrolLeashPoint(store, guildPlot));
            }
            if (npc.getRole() != null) {
                npc.getRole().getStateSupport().setState(guardRef, "Idle", null, store);
            }
        }

        VillagerEquipmentService.applyProfile(guardRef, store, null, plugin.getEquipmentProfileCatalog(), profile.getId());
        return guardRef;
    }

    @Nonnull
    private static String shortHex(@Nonnull UUID townId) {
        String hex = townId.toString().replace("-", "");
        return hex.length() >= 8 ? hex.substring(0, 8) : hex;
    }

    @Nullable
    private static UUID npcUuid(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
        UUIDComponent uc = store.getComponent(npcRef, UUIDComponent.getComponentType());
        return uc != null ? uc.getUuid() : null;
    }

    public static boolean isUnhousedHiredGuard(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            if (rec.isCitizen()) {
                continue;
            }
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(entityUuid)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasUnhousedHiredGuard(@Nonnull TownRecord town, @Nonnull AetherhavenPlugin plugin) {
        return firstUnhousedHiredGuardUuid(town, plugin) != null;
    }

    @Nullable
    public static UUID firstUnhousedHiredGuardUuid(@Nonnull TownRecord town, @Nonnull AetherhavenPlugin plugin) {
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            if (rec.isCitizen()) {
                continue;
            }
            UUID u = rec.getEntityUuid();
            if (u != null && !town.isNpcHomeResidentOnHousePlot(u, plugin.getConstructionCatalog())) {
                return u;
            }
        }
        return null;
    }

    public static boolean isGuardHouseQuestTargetHoused(@Nonnull TownRecord town, @Nonnull AetherhavenPlugin plugin) {
        UUID target = town.getQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_GUARD);
        return target != null && town.isNpcHomeResidentOnHousePlot(target, plugin.getConstructionCatalog());
    }
}
