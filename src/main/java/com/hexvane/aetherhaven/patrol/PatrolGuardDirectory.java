package com.hexvane.aetherhaven.patrol;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.ui.GuardRoleLabels;
import com.hexvane.aetherhaven.ui.NpcPortraitProvider;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Live hired guards in a town for patrol route assignment UI. */
public final class PatrolGuardDirectory {
    private PatrolGuardDirectory() {}

    public record PatrolGuardRow(
        @Nonnull String displayName,
        @Nonnull UUID entityUuid,
        @Nonnull String guardRoleId,
        @Nonnull String guardTypeLangKey,
        @Nonnull String portraitPath
    ) {}

    @Nonnull
    public static List<PatrolGuardRow> listGuards(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin
    ) {
        UUID tid = town.getTownId();
        List<PatrolGuardRow> out = new ArrayList<>();
        Query<EntityStore> q =
            Query.and(
                TownVillagerBinding.getComponentType(),
                UUIDComponent.getComponentType(),
                NPCEntity.getComponentType()
            );
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding binding = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (binding == null || !tid.equals(binding.getTownId()) || !TownVillagerBinding.KIND_GUARD.equals(binding.getKind())) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    if (uc == null || npc == null || npc.getRoleName() == null) {
                        continue;
                    }
                    String role = npc.getRoleName();
                    if (!isPatrolGuardRole(role)) {
                        continue;
                    }
                    out.add(
                        new PatrolGuardRow(
                            displayName(store, archetypeChunk, i, plugin),
                            uc.getUuid(),
                            role,
                            GuardRoleLabels.guardTypeLangKey(role),
                            portraitPath(store, archetypeChunk, i, role, plugin)
                        )
                    );
                }
            }
        );
        out.sort(Comparator.comparing(PatrolGuardRow::displayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static boolean isPatrolGuardRole(@Nonnull String role) {
        return AetherhavenConstants.NPC_GUARD_KNIGHT.equals(role)
            || AetherhavenConstants.NPC_GUARD_ARCHER.equals(role)
            || AetherhavenConstants.NPC_GUARD_MAGE.equals(role)
            || AetherhavenConstants.NPC_GUARD_ROGUE.equals(role);
    }

    @Nonnull
    private static String displayName(
        @Nonnull Store<EntityStore> store,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull AetherhavenPlugin plugin
    ) {
        PersistentDisplayName dn = chunk.getComponent(index, PersistentDisplayName.getComponentType());
        if (dn != null && dn.getDisplayName() != null) {
            String raw = dn.getDisplayName().getRawText();
            if (raw != null && !raw.isBlank()) {
                return raw.trim();
            }
        }
        TownsfolkCharacterBinding tb = chunk.getComponent(index, TownsfolkCharacterBinding.getComponentType());
        if (tb != null) {
            TownsfolkCharacterDefinition def = plugin.getTownsfolkCharacterCatalog().byId(tb.getCharacterId());
            if (def != null && def.getDisplayName() != null && !def.getDisplayName().isBlank()) {
                return def.getDisplayName().trim();
            }
        }
        return "Guard";
    }

    @Nonnull
    private static String portraitPath(
        @Nonnull Store<EntityStore> store,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull String role,
        @Nonnull AetherhavenPlugin plugin
    ) {
        TownsfolkCharacterBinding tb = chunk.getComponent(index, TownsfolkCharacterBinding.getComponentType());
        if (tb != null) {
            if (!tb.getCharacterId().isBlank()) {
                TownsfolkCharacterDefinition def = plugin.getTownsfolkCharacterCatalog().byId(tb.getCharacterId());
                if (def != null) {
                    return NpcPortraitProvider.portraitPathForTownsfolk(def);
                }
            }
            String modelId = tb.getModelAssetId();
            if (modelId != null && !modelId.isBlank()) {
                return NpcPortraitProvider.portraitPathForModelAssetId(modelId);
            }
        }
        return NpcPortraitProvider.portraitPathForRoleId(role);
    }
}
