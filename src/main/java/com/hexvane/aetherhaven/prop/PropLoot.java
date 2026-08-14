package com.hexvane.aetherhaven.prop;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Random eligible prop stacks for floating gifts and world loot chests. */
public final class PropLoot {
    private PropLoot() {}

    @Nonnull
    public static List<PropDefinition> listEligible(
        @Nonnull PropCatalog catalog,
        @Nonnull Set<String> excludedIds
    ) {
        List<PropDefinition> eligible = new ArrayList<>();
        for (PropDefinition def : catalog.list()) {
            if (def == null || def.getId().isEmpty()) {
                continue;
            }
            if (excludedIds.contains(def.getId())) {
                continue;
            }
            eligible.add(def);
        }
        eligible.sort(Comparator.comparing(PropDefinition::getId));
        return List.copyOf(eligible);
    }

    @Nonnull
    public static List<String> listEligibleIds(@Nonnull PropCatalog catalog, @Nonnull Set<String> excludedIds) {
        List<String> ids = new ArrayList<>();
        for (PropDefinition def : listEligible(catalog, excludedIds)) {
            ids.add(def.getId());
        }
        return List.copyOf(ids);
    }

    @Nullable
    public static ItemStack roll(
        @Nonnull PropCatalog catalog,
        @Nonnull Set<String> excludedIds,
        @Nonnull ThreadLocalRandom rnd
    ) {
        return roll(catalog, excludedIds, Set.of(), rnd);
    }

    @Nullable
    public static ItemStack roll(
        @Nonnull PropCatalog catalog,
        @Nonnull Set<String> excludedIds,
        @Nonnull Set<String> alreadyPickedIds,
        @Nonnull ThreadLocalRandom rnd
    ) {
        List<PropDefinition> pool = new ArrayList<>();
        for (PropDefinition def : listEligible(catalog, excludedIds)) {
            if (!alreadyPickedIds.contains(def.getId())) {
                pool.add(def);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        PropDefinition picked = pool.get(rnd.nextInt(pool.size()));
        return PropItemMetadata.createStack(picked);
    }

    @Nonnull
    public static List<ItemStack> rollUnique(
        @Nonnull PropCatalog catalog,
        @Nonnull Set<String> excludedIds,
        int count,
        @Nonnull ThreadLocalRandom rnd
    ) {
        if (count <= 0) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        Set<String> picked = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ItemStack stack = roll(catalog, excludedIds, picked, rnd);
            if (stack == null || ItemStack.isEmpty(stack)) {
                break;
            }
            String id = PropItemMetadata.readPropId(stack);
            if (id != null) {
                picked.add(id);
            }
            out.add(stack);
        }
        return List.copyOf(out);
    }
}
