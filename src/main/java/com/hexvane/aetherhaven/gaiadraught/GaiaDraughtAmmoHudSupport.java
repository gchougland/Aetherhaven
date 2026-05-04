package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * While Gaia's Draught is active in the main hand, mirrors town charges into the engine {@code Ammo} entity stat so the
 * vanilla ammo HUD can show remaining uses (same pattern as weapon magazine + {@code DisplayEntityStatsHUD}).
 */
public final class GaiaDraughtAmmoHudSupport {
    private static final String MOD_KEY = "aetherhaven:gaiadraught:capacity_max";

    private GaiaDraughtAmmoHudSupport() {}

    public static void clearAmmoHudModifier(@Nonnull EntityStatMap map) {
        int ai = DefaultEntityStatTypes.getAmmo();
        if (ai == Integer.MIN_VALUE) {
            return;
        }
        map.removeModifier(ai, MOD_KEY);
        map.getStatModifiersManager().scheduleRecalculate();
    }

    public static void applyAmmoHudFromTown(
        @Nonnull EntityStatMap map,
        int capacity,
        int charges
    ) {
        int ai = DefaultEntityStatTypes.getAmmo();
        if (ai == Integer.MIN_VALUE) {
            return;
        }
        int cap = Math.max(1, capacity);
        int ch = Math.max(0, Math.min(cap, charges));
        map.putModifier(
            ai,
            MOD_KEY,
            new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, cap)
        );
        map.setStatValue(ai, (float) ch);
        map.getStatModifiersManager().scheduleRecalculate();
    }

    /**
     * When the player is holding the draught and has unlocked town state, drive the ammo HUD; otherwise clear our
     * modifier so other items (bows) regain normal ammo display.
     */
    public static void syncHeldDraughtAmmoHud(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid
    ) {
        EntityStatMap map = accessor.getComponent(playerRef, EntityStatMap.getComponentType());
        if (map == null) {
            return;
        }
        ItemStack hand = InventoryComponent.getItemInHand(accessor, playerRef);
        if (hand == null
            || hand.isEmpty()
            || !AetherhavenConstants.ITEM_GAIAS_DRAUGHT.equals(hand.getItemId())) {
            clearAmmoHudModifier(map);
            return;
        }
        GaiaDraughtState s = town.findGaiaDraughtState(playerUuid);
        if (s == null || !s.isUnlocked()) {
            clearAmmoHudModifier(map);
            return;
        }
        applyAmmoHudFromTown(map, s.getCapacity(), s.getCharges());
    }
}
