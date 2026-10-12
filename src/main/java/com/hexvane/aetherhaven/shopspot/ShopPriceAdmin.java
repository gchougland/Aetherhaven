package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.shopspot.ShopPriceCatalog.Beneath;
import com.hexvane.aetherhaven.shopspot.ShopPriceCatalog.Source;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Changing item prices on a running server. Every change is written to this server's own price file first and the
 * catalog is then read again from disk exactly as a server start reads it, so what is live is always what a restart
 * would give. The table shipped in the mod is never touched.
 *
 * <p>What a changed price reaches:
 * <ul>
 *   <li>At once: the price shown on item tooltips, NPC shop stalls, every item listed from now on, and every guild
 *       delivery job posted from now on.</li>
 *   <li>Never: anything already posted. A job on the board keeps the payment it was quoted, and an item a player has
 *       already put up for sale keeps the price it was listed at until it is sold or taken back.</li>
 * </ul>
 */
public final class ShopPriceAdmin {
    /** One writer at a time: a change reads the file, rewrites it and reloads. */
    private static final Object LOCK = new Object();

    private final Path file;
    private final Supplier<ShopPriceCatalog> catalog;
    private final Runnable reload;

    /**
     * @param file this server's price file
     * @param catalog the catalog every price lookup in the mod reads
     * @param reload reads the shipped table and {@code file} again and makes the result the live catalog
     */
    public ShopPriceAdmin(@Nonnull Path file, @Nonnull Supplier<ShopPriceCatalog> catalog, @Nonnull Runnable reload) {
        this.file = file;
        this.catalog = catalog;
        this.reload = reload;
    }

    public enum Outcome {
        /** The item had no server price and has one now. */
        SET,
        /** The item's server price was replaced. */
        CHANGED,
        /** The server price was removed; the item is back on the shipped price, or has none. */
        REMOVED,
        /** There was no server price to remove. Nothing changed. */
        NOT_OVERRIDDEN,
        /** The price or lot size is out of range. Nothing changed. */
        REFUSED,
        /** The price file has a mistake in it. Nothing changed, the file was left as it is. */
        FILE_UNREADABLE,
        /** The price file could not be written. Nothing changed. */
        FILE_FAILED,
        /** The price file was read again. */
        RELOADED
    }

    /** An item's price and where it comes from. */
    public record Shown(@Nonnull String itemId, @Nonnull ShopPriceEntry price, @Nonnull Source source, @Nullable Beneath beneath) {
        public long gold() {
            return price.getGoldPerBatch();
        }

        public int lot() {
            return price.getBatchSize();
        }
    }

    /**
     * @param before the price before the command ran; null for a reload
     * @param now the price in force after it; null for a reload
     * @param detail what is wrong with the file, in plain words
     */
    public record Result(
        @Nonnull Outcome outcome,
        @Nullable Shown before,
        @Nullable Shown now,
        @Nullable ShopPriceOverrides.Refusal refusal,
        @Nullable String detail
    ) {
        public boolean changedPrices() {
            return outcome == Outcome.SET || outcome == Outcome.CHANGED || outcome == Outcome.REMOVED || outcome == Outcome.RELOADED;
        }
    }

    @Nonnull
    public Path file() {
        return file;
    }

    /** The item's current price and its source. An item with no price anywhere shows the default price. */
    @Nonnull
    public Shown show(@Nonnull String itemId) {
        ShopPriceCatalog now = catalog.get();
        return new Shown(itemId, now.getEntry(itemId), now.sourceOf(itemId), now.beneathOverride(itemId));
    }

    /** Every price set on this server, by item id. */
    @Nonnull
    public Map<String, ShopPriceEntry> list() {
        return catalog.get().overrides();
    }

    /**
     * Sets or changes an item's price.
     *
     * @param lot how many items one price buys, or null to keep the item's current lot size
     * @param lotLimit the most of this item that fits in one stack
     */
    @Nonnull
    public Result set(@Nonnull String itemId, long gold, @Nullable Integer lot, int lotLimit) {
        synchronized (LOCK) {
            Shown before = show(itemId);
            int lotSize = lot != null ? lot : before.lot();
            ShopPriceOverrides.Refusal refusal = ShopPriceOverrides.refusal(gold, lotSize, lotLimit);
            if (refusal != null) {
                return new Result(Outcome.REFUSED, before, before, refusal, null);
            }
            ShopPriceEntry wanted = new ShopPriceEntry(gold, lotSize);
            try {
                ShopPriceOverrides.set(file, itemId, wanted);
            } catch (ShopPriceOverrides.Unreadable e) {
                return new Result(Outcome.FILE_UNREADABLE, before, before, null, e.getMessage());
            } catch (IOException e) {
                return new Result(Outcome.FILE_FAILED, before, before, null, describe(e));
            }
            reload.run();
            Shown now = show(itemId);
            if (now.source() != Source.OVERRIDE || now.gold() != gold || now.lot() != lotSize) {
                // Written but not in force: say so instead of claiming a price that is not the live one.
                return new Result(Outcome.FILE_FAILED, before, now, null, "the saved price was not picked up");
            }
            return new Result(before.source() == Source.OVERRIDE ? Outcome.CHANGED : Outcome.SET, before, now, null, null);
        }
    }

    /** Removes an item's server price, so it goes back to the shipped price or to having none. */
    @Nonnull
    public Result remove(@Nonnull String itemId) {
        synchronized (LOCK) {
            Shown before = show(itemId);
            ShopPriceEntry removed;
            try {
                removed = ShopPriceOverrides.remove(file, itemId);
            } catch (ShopPriceOverrides.Unreadable e) {
                return new Result(Outcome.FILE_UNREADABLE, before, before, null, e.getMessage());
            } catch (IOException e) {
                return new Result(Outcome.FILE_FAILED, before, before, null, describe(e));
            }
            // A price deleted from the file by hand is still live until the file is read again.
            if (removed == null && before.source() != Source.OVERRIDE) {
                return new Result(Outcome.NOT_OVERRIDDEN, before, before, null, null);
            }
            reload.run();
            return new Result(Outcome.REMOVED, before, show(itemId), null, null);
        }
    }

    /**
     * Reads the price file again after a hand edit. A file with a mistake in it is refused and the prices in force
     * stay as they are.
     */
    @Nonnull
    public Result reload() {
        synchronized (LOCK) {
            String problem = ShopPriceOverrides.problem(file);
            if (problem != null) {
                return new Result(Outcome.FILE_UNREADABLE, null, null, null, problem);
            }
            reload.run();
            return new Result(Outcome.RELOADED, null, null, null, null);
        }
    }

    @Nonnull
    private static String describe(@Nonnull IOException e) {
        String message = e.getMessage();
        return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
    }
}
