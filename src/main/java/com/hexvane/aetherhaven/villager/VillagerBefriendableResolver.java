package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Whether an NPC participates in reputation, gifts, and friendship UI. */
public final class VillagerBefriendableResolver {
    private VillagerBefriendableResolver() {}

    public static boolean isBefriendable(
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        TownsfolkCharacterBinding tf = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
        if (tf != null) {
            // Townsfolk progress through work, skills and attributes, not friendship.
            return false;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null) {
            return false;
        }
        return isBefriendableByRoleId(npc.getRoleName().trim(), plugin);
    }

    public static boolean isBefriendableByEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref != null && ref.isValid()) {
            return isBefriendable(store, ref, plugin);
        }
        return isBefriendableByRoleId(roleId, plugin);
    }

    public static boolean isBefriendableForJournal(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid,
        @Nonnull String roleId,
        @Nonnull AetherhavenPlugin plugin
    ) {
        return isBefriendableByEntity(store, entityUuid, roleId, plugin);
    }

    public static boolean isBefriendableByRoleId(@Nonnull String npcRoleId, @Nonnull AetherhavenPlugin plugin) {
        if (plugin.getTownsfolkCharacterCatalog().isTownsfolkRole(npcRoleId)) {
            return false;
        }
        VillagerDefinitionCatalog catalog = plugin.getVillagerDefinitionCatalog();
        VillagerDefinition def = catalog.byNpcRoleId(npcRoleId);
        if (def == null) {
            return false;
        }
        return def.isBefriendable();
    }
}
