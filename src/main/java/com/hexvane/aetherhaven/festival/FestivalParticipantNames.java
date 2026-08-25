package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Resolves the name a festival roster shows for a villager taking part in an activity. Villagers spawned by other
 * mods have no town binding, so the role id is prettified as a last resort rather than falling back to a generic
 * "Villager" label.
 */
public final class FestivalParticipantNames {
    private FestivalParticipantNames() {}

    @Nonnull
    public static Message villagerName(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid,
        @Nonnull Message fallback
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref == null || !ref.isValid()) {
            return fallback;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        String roleId = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return fallback;
        }
        String resolved = TownResidentDisplay.resolveFromEntity(store, ref, roleId, plugin).displayName();
        if (resolved.isBlank()) {
            return fallback;
        }
        // resolveFromEntity hands back the raw role id when nothing knows the villager, which reads badly in a HUD.
        return Message.raw(resolved.equals(roleId) ? prettifyRoleId(roleId) : resolved);
    }

    @Nonnull
    private static String prettifyRoleId(@Nonnull String roleId) {
        String tail = roleId;
        int colon = tail.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < tail.length()) {
            tail = tail.substring(colon + 1);
        }
        StringBuilder out = new StringBuilder(tail.length());
        boolean startOfWord = true;
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (c == '_' || c == '-' || c == '.') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                startOfWord = true;
                continue;
            }
            out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
            startOfWord = false;
        }
        String text = out.toString().trim();
        return text.isEmpty() ? roleId : text;
    }
}
