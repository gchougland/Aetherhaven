package com.hexvane.aetherhaven.gaiadraught;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class PlayerHealUtil {
    private PlayerHealUtil() {}

    /** @return missing health (max minus current), or 0 if unmapped */
    public static float missingHealth(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        EntityStatMap map = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (map == null) {
            return 0f;
        }
        int hi = DefaultEntityStatTypes.getHealth();
        EntityStatValue hv = map.get(hi);
        if (hv == null) {
            return 0f;
        }
        float max = hv.getMax();
        float cur = hv.get();
        return Math.max(0f, max - cur);
    }

    public static void healToFull(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        EntityStatMap map = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (map == null) {
            return;
        }
        int hi = DefaultEntityStatTypes.getHealth();
        EntityStatValue hv = map.get(hi);
        if (hv == null) {
            return;
        }
        map.setStatValue(hi, hv.getMax());
        store.putComponent(playerRef, EntityStatMap.getComponentType(), map);
    }

    /** Heals a fraction of max health (matches vanilla instant health potion tiers). */
    public static void healPercentOfMax(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        float percent
    ) {
        if (percent <= 0f) {
            return;
        }
        EntityStatMap map = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (map == null) {
            return;
        }
        int hi = DefaultEntityStatTypes.getHealth();
        EntityStatValue hv = map.get(hi);
        if (hv == null) {
            return;
        }
        float max = hv.getMax();
        float cur = hv.get();
        float add = max * (percent / 100f);
        map.setStatValue(hi, Math.min(max, cur + add));
        store.putComponent(playerRef, EntityStatMap.getComponentType(), map);
    }

    /** @see GaiaDraughtState#instantHealEffectId(int) */
    public static float healPercentForDraughtTier(int tier) {
        return switch (Math.min(GaiaDraughtState.MAX_HEAL_TIER, Math.max(0, tier))) {
            case 0 -> 15f;
            case 1 -> 25f;
            case 2 -> 35f;
            default -> 50f;
        };
    }
}
