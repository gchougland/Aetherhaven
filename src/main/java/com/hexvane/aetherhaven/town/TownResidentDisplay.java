package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.ui.NpcPortraitProvider;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolved display name and portrait for town resident UI. */
public final class TownResidentDisplay {
    public record Resolved(@Nonnull String displayName, @Nonnull String portraitPath) {}

    private TownResidentDisplay() {}

    @Nonnull
    public static Resolved resolveFromChunk(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin
    ) {
        String name = displayNameFromChunk(chunk, index, roleId, plugin);
        String portrait = portraitFromChunk(chunk, index, roleId, plugin);
        return new Resolved(name, portrait);
    }

    @Nonnull
    public static Resolved resolveFromEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin
    ) {
        PersistentDisplayName dn = store.getComponent(ref, PersistentDisplayName.getComponentType());
        if (dn != null && dn.getDisplayName() != null) {
            String raw = dn.getDisplayName().getRawText();
            if (raw != null && !raw.isBlank()) {
                String portrait = portraitFromEntity(store, ref, roleId, plugin);
                return new Resolved(raw.trim(), portrait);
            }
        }
        TownsfolkCharacterBinding tb = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        if (tb != null) {
            return resolveOffline(plugin, roleId, tb.getCharacterId(), tb.getModelAssetId());
        }
        return new Resolved(NpcPortraitProvider.displayLabelForRoleId(roleId), NpcPortraitProvider.portraitPathForRoleId(roleId));
    }

    @Nonnull
    public static Resolved resolveOffline(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String roleId,
        @Nullable String characterId,
        @Nullable String modelAssetId
    ) {
        String name = null;
        String model = modelAssetId != null && !modelAssetId.isBlank() ? modelAssetId.trim() : null;
        if (characterId != null && !characterId.isBlank()) {
            TownsfolkCharacterDefinition def = plugin.getTownsfolkCharacterCatalog().byId(characterId);
            if (def != null) {
                if (def.getDisplayName() != null && !def.getDisplayName().isBlank()) {
                    name = def.getDisplayName().trim();
                }
                if (model == null && !def.getModelAssetId().isBlank()) {
                    model = def.getModelAssetId();
                }
            }
        }
        if (name == null) {
            name = NpcPortraitProvider.displayLabelForRoleId(roleId);
        }
        String portrait = portraitForOffline(plugin, roleId, characterId, model);
        return new Resolved(name, portrait);
    }

    @Nonnull
    private static String displayNameFromChunk(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull String roleId,
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
        return NpcPortraitProvider.displayLabelForRoleId(roleId);
    }

    @Nonnull
    private static String portraitFromChunk(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull String roleId,
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
        return NpcPortraitProvider.portraitPathForRoleId(roleId);
    }

    @Nonnull
    private static String portraitFromEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin
    ) {
        TownsfolkCharacterBinding tb = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
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
        return NpcPortraitProvider.portraitPathForRoleId(roleId);
    }

    @Nonnull
    private static String portraitForOffline(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String roleId,
        @Nullable String characterId,
        @Nullable String modelAssetId
    ) {
        if (characterId != null && !characterId.isBlank()) {
            TownsfolkCharacterDefinition def = plugin.getTownsfolkCharacterCatalog().byId(characterId);
            if (def != null) {
                return NpcPortraitProvider.portraitPathForTownsfolk(def);
            }
        }
        if (modelAssetId != null && !modelAssetId.isBlank()) {
            return NpcPortraitProvider.portraitPathForModelAssetId(modelAssetId);
        }
        return NpcPortraitProvider.portraitPathForRoleId(roleId);
    }
}
