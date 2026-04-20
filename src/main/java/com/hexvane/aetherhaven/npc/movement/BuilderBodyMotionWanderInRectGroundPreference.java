package com.hexvane.aetherhaven.npc.movement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.movement.builders.BuilderBodyMotionWanderInRect;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Extends rect wander: penalizes probed steps that end on bench/seat/bed surfaces; optional per-{@link
 * com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType} overrides (see asset {@code
 * WanderInRectGroundPreference}).
 */
public final class BuilderBodyMotionWanderInRectGroundPreference extends BuilderBodyMotionWanderInRect {
    /** Weight for typical walkable terrain (grass, soil, planks, etc.). */
    private double defaultGroundWeight = 1.0;
    /** Weight when the feet block is a bench, has seats, or has beds (furniture to avoid). */
    private double obstacleWeight = 0.12;
    /** Optional overrides by block type id (e.g. modded furniture without standard markers). */
    private final Map<String, Double> groundWeightsByBlockTypeId = new HashMap<>();

    @Nonnull
    @Override
    public BodyMotionWanderInRectGroundPreference build(@Nonnull BuilderSupport builderSupport) {
        super.build(builderSupport);
        return new BodyMotionWanderInRectGroundPreference(this, builderSupport);
    }

    @Nonnull
    @Override
    public BuilderBodyMotionWanderInRectGroundPreference readConfig(@Nonnull JsonElement data) {
        super.readConfig(data);
        if (!data.isJsonObject()) {
            return this;
        }
        JsonObject o = data.getAsJsonObject();
        if (o.has("DefaultGroundWeight") && o.get("DefaultGroundWeight").isJsonPrimitive()) {
            this.defaultGroundWeight = o.get("DefaultGroundWeight").getAsDouble();
        }
        if (o.has("ObstacleWeight") && o.get("ObstacleWeight").isJsonPrimitive()) {
            this.obstacleWeight = o.get("ObstacleWeight").getAsDouble();
        }
        if (o.has("GroundWeights") && o.get("GroundWeights").isJsonArray()) {
            JsonArray arr = o.get("GroundWeights").getAsJsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject row = el.getAsJsonObject();
                if (!row.has("BlockTypeId") || !row.has("Weight")) {
                    continue;
                }
                String id = row.get("BlockTypeId").getAsString().trim();
                if (id.isEmpty()) {
                    continue;
                }
                this.groundWeightsByBlockTypeId.put(id, row.get("Weight").getAsDouble());
            }
        }
        return this;
    }

    public double getDefaultGroundWeight() {
        return this.defaultGroundWeight;
    }

    public double getObstacleWeight() {
        return this.obstacleWeight;
    }

    public Map<String, Double> getGroundWeightsByBlockTypeId() {
        return Map.copyOf(this.groundWeightsByBlockTypeId);
    }
}
