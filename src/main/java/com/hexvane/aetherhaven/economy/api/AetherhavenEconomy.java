package com.hexvane.aetherhaven.economy.api;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.ItemCoinEconomy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registry of the active {@link EconomyProvider}: one per server, chosen once at startup.
 *
 * <p>An economy mod registers from its plugin {@code setup()}. Its manifest depends on Aetherhaven, so Aetherhaven
 * is set up first. Without a registered provider, or with {@code EconomyProvider = COINS} in {@code config.json},
 * the gold coin item is the currency, as before.
 */
public final class AetherhavenEconomy {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicReference<EconomyProvider> REGISTERED = new AtomicReference<>();

    private AetherhavenEconomy() {}

    /**
     * Makes {@code provider} the server's economy.
     *
     * @throws IllegalStateException when another provider is already registered: two economy mods on one server is
     *     a configuration error, reported to the second one.
     */
    public static void register(@Nonnull EconomyProvider provider) {
        if (!REGISTERED.compareAndSet(null, provider)) {
            EconomyProvider current = REGISTERED.get();
            if (current == provider) {
                return;
            }
            throw new IllegalStateException(
                "Economy provider already registered: " + current.id() + " (refusing " + provider.id() + ")"
            );
        }
        if (coinsForced()) {
            LOGGER.atWarning().log("Economy provider %s registered but config says EconomyProvider = COINS; the gold coin item stays in use", provider.id());
        } else {
            LOGGER.atInfo().log("Economy provider: %s", provider.id());
        }
    }

    /** Removes {@code provider} if it is the registered one, for the mod's plugin shutdown. */
    public static void unregister(@Nonnull EconomyProvider provider) {
        REGISTERED.compareAndSet(provider, null);
    }

    /** The active provider: the registered mod under {@code AUTO}, the gold coin item otherwise. */
    @Nonnull
    public static EconomyProvider provider() {
        EconomyProvider registered = REGISTERED.get();
        if (registered == null || coinsForced()) {
            return ItemCoinEconomy.INSTANCE;
        }
        return registered;
    }

    /** Shortcut for {@code provider().account(ref, store)}. */
    @Nullable
    public static GoldAccount account(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        return provider().account(ref, store);
    }

    /** True when the gold coin item is the currency (no mod registered, or config forces it). */
    public static boolean usesCoinItem() {
        return provider() == ItemCoinEconomy.INSTANCE;
    }

    private static boolean coinsForced() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        return plugin != null && "COINS".equals(plugin.getConfig().get().getEconomyProvider());
    }
}
