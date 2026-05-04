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
}
