package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Hands dialogue the festival line for a villager, but only while that villager's own town is celebrating. */
public final class FestivalDialogueGreetings {
    private FestivalDialogueGreetings() {}

    /**
     * @return the villager's line for the festival running in their town, or null when no festival is on, the villager
     *     does not belong to a town, or the festival has nothing written for them
     */
    @Nullable
    public static Message pickGreeting(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull UUID playerUuid,
        @Nonnull UUID npcEntityUuid,
        long gameEpochDay
    ) {
        FestivalDefinition festival = activeFestivalFor(store, npcRef);
        if (festival == null) {
            return null;
        }
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        String kind = binding != null ? binding.getKind() : null;
        return FestivalGreetingPicker.pickMessage(festival, kind, playerUuid, npcEntityUuid, gameEpochDay);
    }

    /** The festival running in this villager's town right now, or null. */
    @Nullable
    public static FestivalDefinition activeFestivalFor(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null || !npcRef.isValid()) {
            return null;
        }
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (binding == null || binding.getTownId() == null) {
            return null;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(binding.getTownId());
        String runningId = town != null ? town.getActiveFestivalId() : null;
        return runningId != null ? plugin.getFestivalCatalog().get(runningId) : null;
    }
}
