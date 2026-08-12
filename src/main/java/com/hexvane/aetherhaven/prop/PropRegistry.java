package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** In-memory registry of placed {@link PropInstance}s for one world. */
public final class PropRegistry {
    @Nonnull
    private final World world;

    @Nonnull
    private final Map<UUID, PropInstance> byId = new ConcurrentHashMap<>();

    /** Set by {@link com.hexvane.aetherhaven.prop.PropWorldRegistries} after load; called after add/remove/replaceAll. */
    @Nullable
    private volatile Runnable persistCallback;

    public PropRegistry(@Nonnull World world) {
        this.world = world;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    public void setPersistCallback(@Nullable Runnable callback) {
        this.persistCallback = callback;
    }

    private void requestPersist() {
        Runnable cb = persistCallback;
        if (cb != null) {
            cb.run();
        }
    }

    public void add(@Nonnull PropInstance instance) {
        byId.put(instance.getInstanceId(), instance);
        requestPersist();
    }

    public void remove(@Nonnull UUID instanceId) {
        if (byId.remove(instanceId) != null) {
            requestPersist();
        }
    }

    /** Replaces the entire registry contents without triggering persistence (used when loading from disk). */
    public void replaceAll(@Nonnull Collection<PropInstance> all) {
        byId.clear();
        for (PropInstance p : all) {
            byId.put(p.getInstanceId(), p);
        }
    }

    @Nonnull
    public List<PropInstance> all() {
        return new ArrayList<>(byId.values());
    }

    @Nullable
    public PropInstance findById(@Nonnull UUID instanceId) {
        return byId.get(instanceId);
    }

    /** Fast approximate check: props whose anchor point falls inside {@code fp}. For solids-aware checks use {@link PropBoundsUtil}. */
    @Nonnull
    public List<PropInstance> findIntersecting(@Nonnull PlotFootprintRecord fp) {
        List<PropInstance> out = new ArrayList<>();
        for (PropInstance p : byId.values()) {
            if (fp.containsBlock(p.getAnchorX(), p.getAnchorY(), p.getAnchorZ())) {
                out.add(p);
            }
        }
        return out;
    }

    /** Fast approximate check: the prop whose anchor exactly matches this block. For solids-aware lookup use {@link PropLookupUtil}. */
    @Nullable
    public PropInstance findAtBlock(int x, int y, int z) {
        for (PropInstance p : byId.values()) {
            if (p.getAnchorX() == x && p.getAnchorY() == y && p.getAnchorZ() == z) {
                return p;
            }
        }
        return null;
    }

    public int size() {
        return byId.size();
    }
}
