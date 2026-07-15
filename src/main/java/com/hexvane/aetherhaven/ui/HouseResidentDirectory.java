package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.guild.GuildHallAdventurerPoolService;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Town NPCs eligible for house resident assignment on a residential plot. */
public final class HouseResidentDirectory {
    private HouseResidentDirectory() {}

    public record HouseResidentRow(
        @Nonnull String displayName,
        @Nonnull UUID entityUuid,
        @Nonnull String portraitPath,
        @Nonnull Message roleLine
    ) {}

    @Nonnull
    public static List<HouseResidentRow> listAssignable(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID currentHousePlotId,
        boolean hideElsewhereHoused
    ) {
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        UUID tid = town.getTownId();
        Map<UUID, HouseResidentRow> byUuid = new LinkedHashMap<>();
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    if (uc == null || npc == null || npc.getRoleName() == null) {
                        continue;
                    }
                    UUID u = uc.getUuid();
                    if (excludeFromHouseAssignment(store, town, archetypeChunk, i, u)) {
                        continue;
                    }
                    if (hideElsewhereHoused && isHomeResidentElsewhereOnHouse(town, catalog, currentHousePlotId, u)) {
                        continue;
                    }
                    String roleId = npc.getRoleName().trim();
                    TownResidentDisplay.Resolved display = TownResidentDisplay.resolveFromChunk(archetypeChunk, i, roleId, plugin);
                    byUuid.put(
                        u,
                        new HouseResidentRow(
                            display.displayName(),
                            u,
                            display.portraitPath(),
                            roleLineMessage(b.getKind(), roleId)
                        )
                    );
                }
            }
        );
        addStoryFallbackIfMissing(
            byUuid,
            town,
            plugin,
            catalog,
            currentHousePlotId,
            hideElsewhereHoused,
            town.getElderEntityUuid(),
            AetherhavenConstants.ELDER_NPC_ROLE_ID,
            TownVillagerBinding.KIND_ELDER
        );
        addStoryFallbackIfMissing(
            byUuid,
            town,
            plugin,
            catalog,
            currentHousePlotId,
            hideElsewhereHoused,
            town.getInnkeeperEntityUuid(),
            AetherhavenConstants.INNKEEPER_NPC_ROLE_ID,
            TownVillagerBinding.KIND_INNKEEPER
        );
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            if (rec.isCitizen()) {
                continue;
            }
            UUID guardUuid = rec.getEntityUuid();
            if (guardUuid == null || byUuid.containsKey(guardUuid)) {
                continue;
            }
            if (GuildHallAdventurerPoolService.isGuildHallAdventurer(town, guardUuid)) {
                continue;
            }
            if (hideElsewhereHoused
                && isHomeResidentElsewhereOnHouse(town, catalog, currentHousePlotId, guardUuid)) {
                continue;
            }
            String roleId = guardRoleIdForRecord(rec, plugin);
            TownResidentDisplay.Resolved display =
                TownResidentDisplay.resolveOffline(plugin, roleId, rec.getCharacterId(), null);
            byUuid.put(
                guardUuid,
                new HouseResidentRow(
                    display.displayName(),
                    guardUuid,
                    display.portraitPath(),
                    roleLineMessage(TownVillagerBinding.KIND_GUARD, roleId)
                )
            );
        }
        List<HouseResidentRow> out = new ArrayList<>(byUuid.values());
        out.sort(Comparator.comparing(HouseResidentRow::displayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Nullable
    public static HouseResidentRow findRow(@Nonnull List<HouseResidentRow> rows, @Nonnull UUID entityUuid) {
        for (HouseResidentRow row : rows) {
            if (entityUuid.equals(row.entityUuid())) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    public static HouseResidentRow resolvePreviewRow(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID entityUuid
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref != null && ref.isValid()) {
            TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (b != null && npc != null && npc.getRoleName() != null) {
                String roleId = npc.getRoleName().trim();
                TownResidentDisplay.Resolved display = TownResidentDisplay.resolveFromEntity(store, ref, roleId, plugin);
                return new HouseResidentRow(
                    display.displayName(),
                    entityUuid,
                    display.portraitPath(),
                    roleLineMessage(b.getKind(), roleId)
                );
            }
        }
        if (entityUuid.equals(town.getElderEntityUuid())) {
            TownResidentDisplay.Resolved display =
                TownResidentDisplay.resolveOffline(plugin, AetherhavenConstants.ELDER_NPC_ROLE_ID, null, null);
            return new HouseResidentRow(
                display.displayName(),
                entityUuid,
                display.portraitPath(),
                roleLineMessage(TownVillagerBinding.KIND_ELDER, AetherhavenConstants.ELDER_NPC_ROLE_ID)
            );
        }
        if (entityUuid.equals(town.getInnkeeperEntityUuid())) {
            TownResidentDisplay.Resolved display =
                TownResidentDisplay.resolveOffline(plugin, AetherhavenConstants.INNKEEPER_NPC_ROLE_ID, null, null);
            return new HouseResidentRow(
                display.displayName(),
                entityUuid,
                display.portraitPath(),
                roleLineMessage(TownVillagerBinding.KIND_INNKEEPER, AetherhavenConstants.INNKEEPER_NPC_ROLE_ID)
            );
        }
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID guardUuid = rec.getEntityUuid();
            if (guardUuid != null && guardUuid.equals(entityUuid)) {
                String roleId = guardRoleIdForRecord(rec, plugin);
                TownResidentDisplay.Resolved display =
                    TownResidentDisplay.resolveOffline(plugin, roleId, rec.getCharacterId(), null);
                return new HouseResidentRow(
                    display.displayName(),
                    entityUuid,
                    display.portraitPath(),
                    roleLineMessage(TownVillagerBinding.KIND_GUARD, roleId)
                );
            }
        }
        return null;
    }

    @Nonnull
    public static Message roleLineMessage(@Nonnull String bindingKind, @Nonnull String roleId) {
        if (TownVillagerBinding.KIND_GUARD.equals(bindingKind)) {
            return Message
                .translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.houseResidentGuardLine")
                .param("type", Message.translation(GuardRoleLabels.guardTypeLangKey(roleId)));
        }
        if (TownVillagerBinding.KIND_TOWNSFOLK.equals(bindingKind)) {
            return Message.translation("aetherhaven_ui_town.aetherhaven.ui.plotconstruction.houseResidentTownsfolk");
        }
        return Message.translation(NpcPortraitProvider.professionTranslationKey(roleId, bindingKind));
    }

    private static boolean excludeFromHouseAssignment(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull UUID entityUuid
    ) {
        if (GuildHallAdventurerPoolService.isGuildHallAdventurer(town, entityUuid)) {
            return true;
        }
        TownsfolkCharacterBinding tb = chunk.getComponent(index, TownsfolkCharacterBinding.getComponentType());
        return tb != null && TownsfolkAssignmentKinds.isGuildHallAdventurer(tb.getAssignmentKind());
    }

    private static void addStoryFallbackIfMissing(
        @Nonnull Map<UUID, HouseResidentRow> byUuid,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID currentHousePlotId,
        boolean hideElsewhereHoused,
        @Nullable UUID entityUuid,
        @Nonnull String roleId,
        @Nonnull String kind
    ) {
        if (entityUuid == null || byUuid.containsKey(entityUuid)) {
            return;
        }
        if (GuildHallAdventurerPoolService.isGuildHallAdventurer(town, entityUuid)) {
            return;
        }
        if (hideElsewhereHoused && isHomeResidentElsewhereOnHouse(town, catalog, currentHousePlotId, entityUuid)) {
            return;
        }
        TownResidentDisplay.Resolved display = TownResidentDisplay.resolveOffline(plugin, roleId, null, null);
        byUuid.put(
            entityUuid,
            new HouseResidentRow(
                display.displayName(),
                entityUuid,
                display.portraitPath(),
                roleLineMessage(kind, roleId)
            )
        );
    }

    @Nonnull
    private static String guardRoleIdForRecord(@Nonnull HiredGuardRecord rec, @Nonnull AetherhavenPlugin plugin) {
        String characterId = rec.getCharacterId();
        if (characterId != null && !characterId.isBlank()) {
            var def = plugin.getTownsfolkCharacterCatalog().byId(characterId);
            if (def != null && def.getEquipmentProfileId() != null && !def.getEquipmentProfileId().isBlank()) {
                var profile = plugin.getEquipmentProfileCatalog().byId(def.getEquipmentProfileId());
                if (profile != null && profile.getGuardNpcRole() != null && !profile.getGuardNpcRole().isBlank()) {
                    return profile.getGuardNpcRole().trim();
                }
            }
        }
        return AetherhavenConstants.NPC_GUARD_KNIGHT;
    }

    static boolean isHomeResidentElsewhereOnHouse(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID exceptPlotId,
        @Nonnull UUID villagerUuid
    ) {
        for (PlotInstance p : town.getPlotInstances()) {
            if (p.getPlotId().equals(exceptPlotId)) {
                continue;
            }
            if (!catalog.matchesGameplayConstruction(p.getConstructionId(), AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE)) {
                continue;
            }
            if (p.hasHomeResident(villagerUuid)) {
                return true;
            }
        }
        return false;
    }
}
