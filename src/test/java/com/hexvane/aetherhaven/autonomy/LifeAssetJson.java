package com.hexvane.aetherhaven.autonomy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolve action-table inheritance before testing the effective codec payload. */
final class LifeAssetJson {
    static JsonObject read(Path path) throws Exception {
        var data = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        if (path.getParent().endsWith("Animations") && data.has("Parent")) {
            var parent = read(path.resolveSibling(data.get("Parent").getAsString()+".json"));
            data.remove("Parent");
            data.entrySet().forEach(e -> parent.add(e.getKey(), e.getValue()));
            return parent;
        }
        return data;
    }
}
