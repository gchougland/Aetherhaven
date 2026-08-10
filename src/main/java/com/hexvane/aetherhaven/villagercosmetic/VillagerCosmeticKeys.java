package com.hexvane.aetherhaven.villagercosmetic;

import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Stable per-town resident keys for cosmetic overrides. */
public final class VillagerCosmeticKeys {
    private VillagerCosmeticKeys() {}

    @Nonnull
    public static String roleKey(@Nonnull String npcRoleId) {
        return "role:" + npcRoleId.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    public static String characterKey(@Nonnull String characterId) {
        return "character:" + characterId.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static String resolve(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        TownsfolkCharacterBinding tb = accessor.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
        if (tb != null) {
            String characterId = tb.getCharacterId();
            if (characterId != null && !characterId.isBlank()) {
                return characterKey(characterId);
            }
        }
        NPCEntity npc = accessor.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc != null && npc.getRoleName() != null && !npc.getRoleName().isBlank()) {
            return roleKey(npc.getRoleName());
        }
        return null;
    }
}
