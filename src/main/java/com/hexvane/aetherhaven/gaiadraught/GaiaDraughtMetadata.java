package com.hexvane.aetherhaven.gaiadraught;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.jewelry.AetherhavenBsonCodecs;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;

/** Per-stack Gaia's Draught progression: charges in durability, upgrades in BSON. */
public final class GaiaDraughtMetadata {
    public static final String BSON_KEY = "AetherhavenGaiaDraught";

    private static final String FIELD_HEAL_TIER = "healTier";
    private static final String FIELD_SHARD_UPGRADES = "shardUpgradeCount";
    private static final String FIELD_CATALYST_UPGRADES = "catalystUpgradeCount";

    public enum ServiceKind {
        REFILL,
        SHARD_UPGRADE,
        CATALYST_UPGRADE
    }

    public record ServiceTarget(short slot, @Nonnull ItemStack stack) {}

    private GaiaDraughtMetadata() {}

    @Nonnull
    public static ItemStack createFreshStack(boolean fullCharges) {
        GaiaDraughtState p = GaiaDraughtState.createFresh();
        p.setUnlocked(true);
        int cap = p.getCapacity();
        p.setCharges(fullCharges ? cap : 0);
        ItemStack base = new ItemStack(AetherhavenConstants.ITEM_GAIAS_DRAUGHT, 1, p.getCharges(), cap, null);
        return writeProgress(base, p);
    }

    /** Newly crafted flasks without BSON start full at base capacity; existing flasks only normalize. */
    @Nonnull
    public static ItemStack asNewlyCraftedOrNormalized(@Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack) || !AetherhavenConstants.ITEM_GAIAS_DRAUGHT.equals(stack.getItemId())) {
            return stack;
        }
        if (readRoot(stack) == null) {
            return createFreshStack(true);
        }
        return ensureInitialized(stack);
    }

    @Nonnull
    public static ItemStack ensureInitialized(@Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack) || !AetherhavenConstants.ITEM_GAIAS_DRAUGHT.equals(stack.getItemId())) {
            return stack;
        }
        GaiaDraughtState progress = readProgress(stack);
        progress.ensureLegacyMigrated();
        return applyProgressToStack(stack, progress);
    }

    @Nonnull
    public static GaiaDraughtState readProgress(@Nonnull ItemStack stack) {
        if (readRoot(stack) == null) {
            GaiaDraughtState p = GaiaDraughtState.createFresh();
            p.setUnlocked(true);
            p.setCapacity(GaiaDraughtState.DEFAULT_CAPACITY);
            int dur = (int) Math.round(stack.getDurability());
            p.setCharges(dur > 0 ? p.getCapacity() : 0);
            p.ensureLegacyMigrated();
            return p;
        }
        GaiaDraughtState s = readBsonProgress(stack);
        int capFromDur = capacityFromStackDurability(stack);
        if (s.getCapacity() != capFromDur) {
            s.setCapacity(capFromDur);
        }
        int charges = Math.max(0, (int) Math.round(stack.getDurability()));
        s.setCharges(Math.min(charges, s.getCapacity()));
        s.setUnlocked(true);
        s.ensureLegacyMigrated();
        return s;
    }

    public static int getCharges(@Nonnull ItemStack stack) {
        return readProgress(stack).getCharges();
    }

    public static int getCapacity(@Nonnull ItemStack stack) {
        return readProgress(stack).getCapacity();
    }

    public static int getHealTier(@Nonnull ItemStack stack) {
        return readProgress(stack).getHealTier();
    }

    @Nonnull
    public static ItemStack withChargeConsumed(@Nonnull ItemStack stack) {
        GaiaDraughtState p = readProgress(stack);
        if (p.getCharges() <= 0) {
            return stack;
        }
        p.setCharges(p.getCharges() - 1);
        return applyProgressToStack(stack, p);
    }

    @Nonnull
    public static ItemStack refillToCapacity(@Nonnull ItemStack stack) {
        GaiaDraughtState p = readProgress(stack);
        p.setCharges(p.getCapacity());
        return applyProgressToStack(stack, p);
    }

    @Nonnull
    public static ItemStack tryApplyShardUpgrade(@Nonnull ItemStack stack) {
        GaiaDraughtState p = readProgress(stack);
        if (!p.tryApplyShardCapacityUpgrade()) {
            return stack;
        }
        return applyProgressToStack(stack, p);
    }

    @Nonnull
    public static ItemStack tryApplyCatalystUpgrade(@Nonnull ItemStack stack) {
        GaiaDraughtState p = readProgress(stack);
        if (!p.tryApplyCatalystHealTierUpgrade()) {
            return stack;
        }
        return applyProgressToStack(stack, p);
    }

    public static boolean hasAnyDraught(@Nullable CombinedItemContainer inv) {
        if (inv == null) {
            return false;
        }
        String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
        for (short slot = 0; slot < inv.getCapacity(); slot++) {
            ItemStack st = inv.getItemStack(slot);
            if (st != null && !st.isEmpty() && id.equals(st.getItemId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static ServiceTarget selectServiceTarget(@Nullable CombinedItemContainer inv, @Nonnull ServiceKind kind) {
        if (inv == null) {
            return null;
        }
        String id = AetherhavenConstants.ITEM_GAIAS_DRAUGHT;
        ServiceTarget best = null;
        double bestRatio = Double.POSITIVE_INFINITY;
        for (short slot = 0; slot < inv.getCapacity(); slot++) {
            ItemStack st = inv.getItemStack(slot);
            if (st == null || st.isEmpty() || !id.equals(st.getItemId())) {
                continue;
            }
            ItemStack initialized = ensureInitialized(st);
            GaiaDraughtState p = readProgress(initialized);
            if (!matchesServiceKind(p, kind)) {
                continue;
            }
            int cap = Math.max(1, p.getCapacity());
            double ratio = (double) p.getCharges() / (double) cap;
            if (best == null || ratio < bestRatio - 1e-9 || (Math.abs(ratio - bestRatio) < 1e-9 && slot < best.slot())) {
                bestRatio = ratio;
                best = new ServiceTarget(slot, initialized);
            }
        }
        return best;
    }

    private static boolean matchesServiceKind(@Nonnull GaiaDraughtState p, @Nonnull ServiceKind kind) {
        return switch (kind) {
            case REFILL -> p.getCharges() < p.getCapacity();
            case SHARD_UPGRADE -> p.canApplyShardUpgrade();
            case CATALYST_UPGRADE -> p.canApplyCatalystUpgrade();
        };
    }

    @Nonnull
    private static ItemStack applyProgressToStack(@Nonnull ItemStack stack, @Nonnull GaiaDraughtState progress) {
        progress.clampChargesToCapacity();
        int cap = progress.getCapacity();
        int charges = progress.getCharges();
        ItemStack withDur = stack.withMaxDurability(cap).withDurability(charges);
        return writeProgress(withDur, progress);
    }

    @Nonnull
    private static ItemStack writeProgress(@Nonnull ItemStack stack, @Nonnull GaiaDraughtState progress) {
        BsonDocument root = new BsonDocument();
        root.put(FIELD_HEAL_TIER, new BsonInt32(progress.getHealTier()));
        root.put(FIELD_SHARD_UPGRADES, new BsonInt32(progress.getShardUpgradeCount()));
        root.put(FIELD_CATALYST_UPGRADES, new BsonInt32(progress.getCatalystUpgradeCount()));
        return stack.withMetadata(BSON_KEY, root);
    }

    @Nonnull
    private static GaiaDraughtState readBsonProgress(@Nonnull ItemStack stack) {
        BsonDocument root = readRoot(stack);
        if (root == null) {
            GaiaDraughtState s = GaiaDraughtState.createFresh();
            s.setUnlocked(true);
            return s;
        }
        return GaiaDraughtState.fromStoredUpgrades(
            readInt(root, FIELD_HEAL_TIER, 0),
            readInt(root, FIELD_SHARD_UPGRADES, 0),
            readInt(root, FIELD_CATALYST_UPGRADES, 0)
        );
    }

    private static int capacityFromStackDurability(@Nonnull ItemStack stack) {
        int max = (int) Math.round(stack.getMaxDurability());
        if (max <= 0) {
            return GaiaDraughtState.DEFAULT_CAPACITY;
        }
        return Math.min(GaiaDraughtState.MAX_FLASK_CAPACITY, Math.max(GaiaDraughtState.DEFAULT_CAPACITY, max));
    }

    @Nullable
    private static BsonDocument readRoot(@Nonnull ItemStack stack) {
        return stack.getFromMetadataOrNull(BSON_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT);
    }

    /** True when this stack has been initialized with item-local upgrade data. */
    public static boolean hasPersistedProgress(@Nonnull ItemStack stack) {
        return readRoot(stack) != null;
    }

    private static int readInt(@Nonnull BsonDocument d, @Nonnull String k, int def) {
        BsonValue v = d.get(k);
        if (v == null || !v.isNumber()) {
            return def;
        }
        return v.asNumber().intValue();
    }
}
