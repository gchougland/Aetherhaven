package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;

/** Read the actual local weather and resident housing assignment, not random weather guesses. */
final class VillagerLifeContext {
    private VillagerLifeContext() {}

    static boolean rainy(Ref<EntityStore> ref, Store<EntityStore> store) {
        var tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null || com.hypixel.hytale.builtin.weather.WeatherPlugin.get() == null) return false;
        var weather = store.getResource(WeatherResource.getResourceType());
        int index = weather.getForcedWeatherIndex();
        if (index == Weather.UNKNOWN_ID) {
            var tracker = new WeatherTracker();
            tracker.updateEnvironment(tc, store);
            index = weather.getWeatherIndexForEnvironment(tracker.getEnvironmentId());
        }
        var asset = Weather.getAssetMap().getAsset(index);
        return asset != null && rainWeatherId(asset.getId());
    }

    static boolean rainWeatherId(String id) {
        if (id == null) return false;
        String name = id.toLowerCase(java.util.Locale.ROOT);
        return name.contains("rain") || name.contains("thunder") || (name.contains("storm") && !name.contains("snow") && !name.contains("sand"));
    }

    static boolean needsHouse(Ref<EntityStore> ref, Store<EntityStore> store, AetherhavenPlugin plugin) {
        var binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        var id = store.getComponent(ref, UUIDComponent.getComponentType());
        if (binding == null || id == null || binding.getTownId() == null
            || TownVillagerBinding.isVisitorKind(binding.getKind()) || TownVillagerBinding.isRescueKind(binding.getKind())) return false;
        var town = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin).getTown(binding.getTownId());
        return town != null && PoiScoring.resolveHomePlotId(town, id.getUuid(), plugin.getConstructionCatalog()) == null;
    }
}
