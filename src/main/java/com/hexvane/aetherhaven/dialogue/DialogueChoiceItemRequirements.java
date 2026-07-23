package com.hexvane.aetherhaven.dialogue;

import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.tourist.TouristMoveInRequirements;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves item stacks a dialogue choice expects the player to bring (display + enablement). */
public final class DialogueChoiceItemRequirements {
    private DialogueChoiceItemRequirements() {}

    @Nonnull
    public static List<MaterialRequirement> resolve(
        @Nonnull DialogueChoiceDefinition choice,
        @Nullable AetherhavenPlugin plugin,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        List<MaterialRequirement> explicit = choice.getItemRequirements();
        if (!explicit.isEmpty()) {
            return normalizeItemOnly(explicit);
        }
        if (isTouristMoveInChoice(choice)) {
            if (plugin == null) {
                return List.of();
            }
            var fromNpc = TouristMoveInRequirements.forNpc(plugin, store, npcRef);
            if (!fromNpc.isEmpty()) {
                return fromNpc;
            }
            return List.of();
        }
        return List.of();
    }

    @Nonnull
    public static List<MaterialRequirement> resolve(
        @Nonnull List<MaterialRequirement> requirements
    ) {
        return normalizeItemOnly(requirements);
    }

    public static boolean playerHasAll(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<MaterialRequirement> requirements
    ) {
        if (requirements.isEmpty()) {
            return true;
        }
        CombinedItemContainer inv = playerInventory(playerRef, store);
        if (inv == null) {
            return false;
        }
        return InventoryMaterials.hasAllCheckedRequirements(inv, requirements);
    }

    public static boolean isTouristMoveInChoice(@Nonnull DialogueChoiceDefinition choice) {
        String icon = choice.getIcon();
        if (icon != null && "move_in_item".equalsIgnoreCase(icon.trim())) {
            return true;
        }
        if (choice.hasAction("deliver_tourist_move_in_items")) {
            return true;
        }
        for (JsonObject action : choice.getActions()) {
            if (action == null || !action.has("type")) {
                continue;
            }
            if ("deliver_tourist_move_in_items".equalsIgnoreCase(action.get("type").getAsString())) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static List<MaterialRequirement> normalizeItemOnly(@Nonnull List<MaterialRequirement> in) {
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

    @Nullable
    private static CombinedItemContainer playerInventory(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return null;
        }
        return InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
    }
}
