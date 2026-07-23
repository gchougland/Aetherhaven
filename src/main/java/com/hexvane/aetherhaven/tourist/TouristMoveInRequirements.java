package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.ui.UiMaterialLabels;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-character items required before a tourist accepts the house quest. */
public final class TouristMoveInRequirements {
    public static final String MOVE_IN_OBJECTIVE_ID = "gift";

    private TouristMoveInRequirements() {}

    @Nonnull
    public static List<MaterialRequirement> forCharacterId(
        @Nonnull AetherhavenPlugin plugin,
        @Nullable String characterId
    ) {
        if (characterId == null || characterId.isBlank()) {
            return List.of();
        }
        TownsfolkCharacterDefinition def = plugin.getTownsfolkCharacterCatalog().byId(characterId.trim());
        if (def == null) {
            return List.of();
        }
        return normalizedItemOnly(def.getMoveInRequirements());
    }

    @Nonnull
    public static List<MaterialRequirement> forNpc(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return List.of();
        }
        TownsfolkCharacterBinding binding = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
        if (binding == null || binding.getCharacterId().isBlank()) {
            return List.of();
        }
        return forCharacterId(plugin, binding.getCharacterId());
    }

    @Nonnull
    public static List<MaterialRequirement> forQuestTarget(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nullable Store<EntityStore> store
    ) {
        UUID target = town.getQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        if (target == null) {
            return List.of();
        }
        if (store != null) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(target);
            if (ref != null && ref.isValid()) {
                List<MaterialRequirement> fromEntity = forNpc(plugin, store, ref);
                if (!fromEntity.isEmpty()) {
                    return fromEntity;
                }
            }
        }
        for (TouristRecord rec : town.getTouristRecords()) {
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(target)) {
                return forCharacterId(plugin, rec.getCharacterId());
            }
        }
        return List.of();
    }

    public static boolean playerHasAll(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<MaterialRequirement> requirements
    ) {
        if (requirements.isEmpty()) {
            return false;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        return InventoryMaterials.hasAllCheckedRequirements(inv, requirements);
    }

    public static boolean removeAll(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<MaterialRequirement> requirements
    ) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (!InventoryMaterials.hasAllCheckedRequirements(inv, requirements)) {
            return false;
        }
        InventoryMaterials.removeAll(inv, requirements);
        return true;
    }

    @Nullable
    public static String primaryItemId(@Nonnull List<MaterialRequirement> requirements) {
        for (MaterialRequirement m : requirements) {
            if (m.getItemId() != null && !m.getItemId().isBlank()) {
                return m.getItemId().trim();
            }
        }
        return null;
    }

    @Nonnull
    public static Message itemsLabelMessage(@Nonnull List<MaterialRequirement> requirements) {
        if (requirements.isEmpty()) {
            return Message.raw("");
        }
        Message out = Message.raw("");
        boolean first = true;
        for (MaterialRequirement m : requirements) {
            String itemId = m.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            int count = Math.max(1, m.getCount());
            Message part = UiMaterialLabels.itemNameMessage(itemId.trim());
            if (count > 1) {
                part = part.insert(Message.raw(" x" + count));
            }
            if (!first) {
                out = out.insert(Message.raw(", ")).insert(part);
            } else {
                out = part;
                first = false;
            }
        }
        return first ? Message.raw("") : out;
    }

    @Nonnull
    public static Message primaryItemLabelMessage(@Nonnull List<MaterialRequirement> requirements) {
        String id = primaryItemId(requirements);
        if (id == null) {
            return Message.raw("");
        }
        return UiMaterialLabels.itemNameMessage(id);
    }

    @Nonnull
    private static List<MaterialRequirement> normalizedItemOnly(@Nonnull List<MaterialRequirement> in) {
        List<MaterialRequirement> out = new ArrayList<>();
        for (MaterialRequirement m : in) {
            if (m.getResourceTypeId() != null && !m.getResourceTypeId().isBlank()) {
                continue;
            }
            String itemId = m.getItemId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            out.add(MaterialRequirement.ofItem(itemId, Math.max(1, m.getCount())));
        }
        return List.copyOf(out);
    }
}
