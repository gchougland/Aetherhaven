package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

public final class JewelryStatSync {
    private static final String MOD_PREFIX = "aetherhaven:jewelry:";

    private JewelryStatSync() {}

    public static void apply(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, @Nonnull PlayerJewelryLoadout loadout) {
        EntityStatMap map = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (map == null) {
            return;
        }
        clearAll(map);
        for (int slot = 0; slot < 3; slot++) {
            ItemStack equipped = loadout.getSlot(slot);
            if (ItemStack.isEmpty(equipped)) {
                continue;
            }
            List<JewelryMetadata.RolledTrait> traits = JewelryMetadata.readTraits(equipped);
            int ti = 0;
            for (JewelryMetadata.RolledTrait rt : traits) {
                int statIndex = EntityStatType.getAssetMap().getIndex(rt.statId());
                if (statIndex == Integer.MIN_VALUE) {
                    ti++;
                    continue;
                }
                StaticModifier.CalculationType ct = parseCalc(rt.calculationType());
                String key = MOD_PREFIX + slot + ":" + rt.gemTraitIndex() + ":" + ti;
                Modifier mod = new StaticModifier(Modifier.ModifierTarget.MAX, ct, rt.amount());
                map.putModifier(statIndex, key, mod);
                ti++;
            }
        }
        map.getStatModifiersManager().scheduleRecalculate();
    }

    public static void clearAll(@Nonnull EntityStatMap map) {
        int n = map.size();
        for (int i = 0; i < n; i++) {
            EntityStatValue value = map.get(i);
            if (value == null) {
                continue;
            }
            Map<String, Modifier> mods = value.getModifiers();
            if (mods == null || mods.isEmpty()) {
                continue;
            }
            List<String> keys = new ArrayList<>();
            for (String k : mods.keySet()) {
                if (k != null && k.startsWith(MOD_PREFIX)) {
                    keys.add(k);
                }
            }
            for (String k : keys) {
                map.removeModifier(i, k);
            }
        }
        map.getStatModifiersManager().scheduleRecalculate();
    }

    @Nonnull
    private static StaticModifier.CalculationType parseCalc(@Nonnull String s) {
        if ("MULTIPLICATIVE".equalsIgnoreCase(s.trim())) {
            return StaticModifier.CalculationType.MULTIPLICATIVE;
        }
        return StaticModifier.CalculationType.ADDITIVE;
    }
}
