package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Rows for the guild hall town records guard roster. */
public final class GuardHireManifest {
    public record Row(@Nonnull String label, @Nonnull String portraitPath, @Nonnull String guardRoleId, boolean housed) {}

    private GuardHireManifest() {}

    @Nonnull
    public static List<Row> listRows(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        List<Row> out = new ArrayList<>();
        Set<String> seenCharacterIds = new HashSet<>();
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            String characterId = rec.getCharacterId();
            if (!characterId.isBlank()) {
                String key = characterId.toLowerCase();
                if (!seenCharacterIds.add(key)) {
                    continue;
                }
            }
            String guardRoleId = guardRoleIdForRecord(rec, plugin);
            UUID entityUuid = rec.getEntityUuid();
            TownResidentDisplay.Resolved resolved = resolveDisplay(store, plugin, characterId, entityUuid, guardRoleId);
            out.add(new Row(resolved.displayName(), resolved.portraitPath(), guardRoleId, rec.isCitizen()));
        }
        out.sort(Comparator.comparing(Row::label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Nonnull
    private static String guardRoleIdForRecord(@Nonnull HiredGuardRecord rec, @Nonnull AetherhavenPlugin plugin) {
        String profileId = rec.getEquipmentProfileId();
        if (!profileId.isBlank()) {
            EquipmentProfileDefinition profile = plugin.getEquipmentProfileCatalog().byId(profileId);
            if (profile != null && profile.getGuardNpcRole() != null && !profile.getGuardNpcRole().isBlank()) {
                return profile.getGuardNpcRole().trim();
            }
        }
        String characterId = rec.getCharacterId();
        if (!characterId.isBlank()) {
            var def = plugin.getTownsfolkCharacterCatalog().byId(characterId);
            if (def != null && def.getEquipmentProfileId() != null && !def.getEquipmentProfileId().isBlank()) {
                EquipmentProfileDefinition profile = plugin.getEquipmentProfileCatalog().byId(def.getEquipmentProfileId());
                if (profile != null && profile.getGuardNpcRole() != null && !profile.getGuardNpcRole().isBlank()) {
                    return profile.getGuardNpcRole().trim();
                }
            }
        }
        return AetherhavenConstants.NPC_GUARD_KNIGHT;
    }

    @Nonnull
    private static TownResidentDisplay.Resolved resolveDisplay(
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String characterId,
        @Nullable UUID entityUuid,
        @Nonnull String guardRoleId
    ) {
        if (entityUuid != null) {
            Ref<EntityStore> entityRef = store.getExternalData().getRefFromUUID(entityUuid);
            if (entityRef != null && entityRef.isValid()) {
                NPCEntity npc = store.getComponent(entityRef, NPCEntity.getComponentType());
                String roleId =
                    npc != null && npc.getRoleName() != null && !npc.getRoleName().isBlank()
                        ? npc.getRoleName()
                        : guardRoleId;
                return TownResidentDisplay.resolveFromEntity(store, entityRef, roleId, plugin);
            }
        }
        return TownResidentDisplay.resolveOffline(plugin, guardRoleId, characterId, null);
    }
}
