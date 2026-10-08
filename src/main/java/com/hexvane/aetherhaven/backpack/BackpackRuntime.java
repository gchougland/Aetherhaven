package com.hexvane.aetherhaven.backpack;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Small, disposable player cache. Contents remain solely in the item metadata. */
public final class BackpackRuntime implements Component<EntityStore> {
    private static ComponentType<EntityStore,BackpackRuntime> type;
    long nextScan;
    public int music;
    public static void register(ComponentRegistryProxy<EntityStore> registry){type=registry.registerComponent(BackpackRuntime.class,BackpackRuntime::new);}
    public static ComponentType<EntityStore,BackpackRuntime> getComponentType(){return type;}
    @Override public BackpackRuntime clone(){var c=new BackpackRuntime();c.nextScan=nextScan;c.music=music;return c;}
}
