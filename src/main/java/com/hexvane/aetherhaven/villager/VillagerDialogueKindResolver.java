package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.worldnpc.WorldNpcBinding;
import com.hexvane.aetherhaven.worldnpc.WorldNpcSpawnRoles;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves the villager kind used for festival greetings and Wintertide gift lines. */
public final class VillagerDialogueKindResolver {
    private static final String VISITOR_PREFIX = "visitor_";
    private static final String RESCUE_PREFIX = "rescue_";

    private VillagerDialogueKindResolver() {}

  /**
   * Kind for festival dialogue lookup: {@link VillagerDefinition#getDialogueVillagerKind()} when known, else a
   * normalized {@link TownVillagerBinding} kind ({@code visitor_*} and {@code rescue_*} stripped).
   */
    @Nonnull
    public static String resolve(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable AetherhavenPlugin plugin
    ) {
        if (!npcRef.isValid()) {
            return FestivalKind.DEFAULT;
        }
        if (plugin != null) {
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc != null && npc.getRoleName() != null && !npc.getRoleName().isBlank()) {
                String role = resolveLogicalRoleId(store, npcRef, npc.getRoleName().trim());
                VillagerDefinition vdef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(role);
                if (vdef != null && !vdef.getDialogueVillagerKind().isEmpty()) {
                    return normalizeKind(vdef.getDialogueVillagerKind());
                }
            }
        }
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (binding != null && binding.getKind() != null && !binding.getKind().isBlank()) {
            return normalizeBindingKind(binding.getKind());
        }
        return FestivalKind.DEFAULT;
    }

    @Nonnull
    private static String resolveLogicalRoleId(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull String roleName
    ) {
        WorldNpcBinding worldBinding = store.getComponent(npcRef, WorldNpcBinding.getComponentType());
        if (worldBinding != null) {
            String logical = WorldNpcSpawnRoles.toLogicalRoleId(worldBinding.getNpcRoleId());
            if (!logical.isEmpty()) {
                return logical;
            }
            return WorldNpcSpawnRoles.toLogicalRoleId(roleName);
        }
        return WorldNpcSpawnRoles.toLogicalRoleId(roleName);
    }

    @Nonnull
    static String normalizeBindingKind(@Nonnull String kind) {
        String normalized = normalizeKind(kind);
        if (normalized.startsWith(VISITOR_PREFIX) && normalized.length() > VISITOR_PREFIX.length()) {
            return normalized.substring(VISITOR_PREFIX.length());
        }
        if (normalized.startsWith(RESCUE_PREFIX) && normalized.length() > RESCUE_PREFIX.length()) {
            return normalized.substring(RESCUE_PREFIX.length());
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeKind(@Nonnull String kind) {
        String normalized = kind.trim().toLowerCase(Locale.ROOT);
        // Elder Lyren's dialogueVillagerKind is elder_lyren; lang lines use elder.
        if ("elder_lyren".equals(normalized)) {
            return "elder";
        }
        return normalized;
    }

    /** Shared default bucket name for festival greeting lookup. */
    public static final class FestivalKind {
        public static final String DEFAULT = "default";

        private FestivalKind() {}
    }
}
