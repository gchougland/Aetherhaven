package com.hexvane.aetherhaven.wall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Loads the shipped wall buildings so tests run against the real authored connection points. */
public final class WallStyleFixtures {
    public static final List<String> CORE_BUILDING_IDS =
        List.of(
            "plot_wall_segment",
            "plot_wall_gate",
            "plot_wall_tower_endcap_s",
            "plot_wall_tower_eastdoor_ns",
            "plot_wall_tower_outercorner_se",
            "plot_wall_tower_eastdoor_sw"
        );

    private WallStyleFixtures() {}

    @Nonnull
    public static ConstructionCatalog coreConstructionCatalog() {
        Gson gson = new GsonBuilder().create();
        Map<String, ConstructionDefinition> byId = new LinkedHashMap<>();
        for (String id : CORE_BUILDING_IDS) {
            byId.put(id, read(gson, id));
        }
        return ConstructionCatalog.forTests(byId);
    }

    @Nonnull
    public static WallStyleCatalog coreCatalog() {
        return WallStyleCatalog.from(coreConstructionCatalog());
    }

    @Nonnull
    private static ConstructionDefinition read(@Nonnull Gson gson, @Nonnull String id) {
        String resource = "Server/Aetherhaven/Buildings/" + id + ".json";
        try (InputStream in = WallStyleFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: " + resource);
            }
            return gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ConstructionDefinition.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read " + resource, e);
        }
    }

    @Nonnull
    public static WallStyle coreStyle() {
        WallStyle style = coreCatalog().style(WallStyleCatalog.DEFAULT_STYLE_ID);
        if (style == null) {
            throw new IllegalStateException("core wall style missing");
        }
        return style;
    }
}
