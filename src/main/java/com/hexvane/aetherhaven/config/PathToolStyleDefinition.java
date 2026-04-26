package com.hexvane.aetherhaven.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One configurable path style: display {@link #name} and {@link #centerBlockIds} used for the center strip (edges use
 * grass in {@link com.hexvane.aetherhaven.pathtool.PathCementService}).
 */
public final class PathToolStyleDefinition {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type LIST_TYPE = new TypeToken<List<PathToolStyleDefinition>>() {
    }.getType();

    /**
     * Default when {@link com.hexvane.aetherhaven.config.AetherhavenPluginConfig#pathToolStyles} is missing/invalid: soil
     * mix, then vanilla cobble + mossy cobble for the path strip.
     */
    @Nonnull
    public static final String DEFAULT_JSON = "["
        + "{\"name\":\"Soil path\",\"centerBlockIds\":[\"Soil_Pathway\",\"Soil_Mud_Dry\"]},"
        + "{\"name\":\"Cobblestone\",\"centerBlockIds\":[\"Rock_Stone_Cobble\",\"Rock_Stone_Cobble_Mossy\"]}"
        + "]";

    @SerializedName("name")
    private String name = "";

    @SerializedName("centerBlockIds")
    @Nullable
    private List<String> centerBlockIds;

    @Nonnull
    public String getName() {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return "Path";
    }

    @Nonnull
    public List<String> getCenterBlockIds() {
        if (centerBlockIds == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : centerBlockIds) {
            if (id != null && !id.isBlank()) {
                out.add(id.trim());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Parses the JSON array from config. On failure or empty result, returns {@link #parseList(String) parseList} of
     * {@link #DEFAULT_JSON}.
     */
    @Nonnull
    public static List<PathToolStyleDefinition> parseList(@Nullable String json) {
        String s = json != null ? json.trim() : "";
        if (s.isEmpty()) {
            return parseList(DEFAULT_JSON);
        }
        try {
            List<PathToolStyleDefinition> list = GSON.fromJson(s, LIST_TYPE);
            if (list == null || list.isEmpty()) {
                return parseList(DEFAULT_JSON);
            }
            List<PathToolStyleDefinition> ok = new ArrayList<>();
            for (PathToolStyleDefinition d : list) {
                if (d != null && !d.getCenterBlockIds().isEmpty()) {
                    ok.add(d);
                }
            }
            return ok.isEmpty() ? parseList(DEFAULT_JSON) : Collections.unmodifiableList(ok);
        } catch (Exception e) {
            return parseList(DEFAULT_JSON);
        }
    }
}
