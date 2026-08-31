package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.ui.PropIconPath;
import javax.annotation.Nonnull;

/** Hooks prop icon registration into {@link PropIconPacketAdapter} so inventory icons update without a world reload. */
public final class PropIconSync {
    private PropIconSync() {}

    public static void afterIconRegistered(@Nonnull AetherhavenPlugin plugin, @Nonnull String propId) {
        String id = propId.trim();
        if (id.isEmpty()) {
            return;
        }
        PropIconPath.invalidateRuntimeIconCache(id);
        PropIconPath.registerRuntimeIconIfPresent(plugin, id);
        PropIconPacketAdapter adapter = plugin.getPropIconPacketAdapter();
        if (adapter != null) {
            adapter.onPropIconRegistered(id);
        }
    }
}
