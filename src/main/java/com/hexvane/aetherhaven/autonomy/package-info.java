/**
 * Town villager POI autonomy: minimal Java bridge between mod data ({@link com.hexvane.aetherhaven.poi.PoiRegistry},
 * {@link com.hexvane.aetherhaven.villager.VillagerNeeds}) and the engine. Prefer expressing behavior in NPC role JSON
 * ({@code Instructions}, {@code StateTransitions}); keep this package as small orchestration helpers.
 */
package com.hexvane.aetherhaven.autonomy;
