package com.hexvane.aetherhaven.prefab;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Streams prefab JSON with Gson {@link JsonReader} so large files are not loaded as a DOM tree.
 */
public final class PrefabJsonStream {
    private PrefabJsonStream() {}

    @FunctionalInterface
    public interface BlockVisitor {
        void accept(@Nullable String name, @Nullable Integer filler) throws IOException;
    }

    public record Scan(
        @Nullable String malformed,
        @Nullable Integer version,
        @Nullable Integer blockIdVersion,
        @Nonnull List<String> blockNames,
        @Nonnull List<String> fluidNames,
        @Nonnull List<String> entityAssetStrings
    ) {
        @Nonnull
        static Scan malformed(@Nonnull String detail) {
            return new Scan(detail, null, null, List.of(), List.of(), List.of());
        }
    }

    public static void forEachBlock(@Nonnull Path path, @Nonnull BlockVisitor visitor) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            forEachBlock(reader, visitor);
        }
    }

    public static void forEachBlock(@Nonnull Reader reader, @Nonnull BlockVisitor visitor) throws IOException {
        JsonReader json = new JsonReader(reader);
        if (json.peek() != JsonToken.BEGIN_OBJECT) {
            throw new IllegalArgumentException("No blocks array in prefab");
        }
        json.beginObject();
        boolean foundBlocks = false;
        while (json.hasNext()) {
            if ("blocks".equals(json.nextName())) {
                foundBlocks = true;
                visitBlocks(json, visitor);
            } else {
                json.skipValue();
            }
        }
        json.endObject();
        if (!foundBlocks) {
            throw new IllegalArgumentException("No blocks array in prefab");
        }
    }

    @Nonnull
    public static Scan scan(@Nonnull byte[] bytes) {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            return scan(reader);
        } catch (IOException e) {
            return Scan.malformed("Prefab is not valid JSON");
        }
    }

    @Nonnull
    public static Scan scan(@Nonnull Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return scan(reader);
        }
    }

    @Nonnull
    public static Scan scan(@Nonnull Reader reader) {
        try {
            return scanInternal(reader);
        } catch (MalformedJsonException | IllegalStateException | NumberFormatException e) {
            return Scan.malformed("Prefab is not valid JSON");
        } catch (IOException e) {
            return Scan.malformed("Prefab is not valid JSON");
        }
    }

    @Nonnull
    private static Scan scanInternal(@Nonnull Reader reader) throws IOException {
        JsonReader json = new JsonReader(reader);
        if (json.peek() == JsonToken.NULL) {
            json.nextNull();
            return Scan.malformed("Prefab JSON root is missing");
        }
        if (json.peek() != JsonToken.BEGIN_OBJECT) {
            return Scan.malformed("Prefab JSON root is missing");
        }
        json.beginObject();
        Integer version = null;
        Integer blockIdVersion = null;
        Set<String> blocks = new LinkedHashSet<>();
        Set<String> fluids = new LinkedHashSet<>();
        Set<String> entityAssets = new LinkedHashSet<>();
        String malformed = null;
        while (json.hasNext()) {
            String field = json.nextName();
            switch (field) {
                case "version" -> version = readOptionalInt(json);
                case "blockIdVersion" -> blockIdVersion = readOptionalInt(json);
                case "blocks" -> {
                    String error = collectUniqueNames(json, "blocks", blocks);
                    if (malformed == null) {
                        malformed = error;
                    }
                }
                case "fluids" -> {
                    String error = collectUniqueNames(json, "fluids", fluids);
                    if (malformed == null) {
                        malformed = error;
                    }
                }
                case "entities" -> collectEntityAssetStrings(json, entityAssets);
                default -> json.skipValue();
            }
        }
        json.endObject();
        return new Scan(
            malformed,
            version,
            blockIdVersion,
            List.copyOf(blocks),
            List.copyOf(fluids),
            List.copyOf(entityAssets)
        );
    }

    private static void visitBlocks(@Nonnull JsonReader json, @Nonnull BlockVisitor visitor) throws IOException {
        if (json.peek() == JsonToken.NULL) {
            json.nextNull();
            throw new IllegalArgumentException("No blocks array in prefab");
        }
        if (json.peek() != JsonToken.BEGIN_ARRAY) {
            json.skipValue();
            throw new IllegalArgumentException("No blocks array in prefab");
        }
        json.beginArray();
        while (json.hasNext()) {
            if (json.peek() != JsonToken.BEGIN_OBJECT) {
                json.skipValue();
                continue;
            }
            String name = null;
            Integer filler = null;
            json.beginObject();
            while (json.hasNext()) {
                switch (json.nextName()) {
                    case "name" -> {
                        if (json.peek() == JsonToken.STRING) {
                            name = json.nextString();
                        } else {
                            json.skipValue();
                        }
                    }
                    case "filler" -> {
                        if (json.peek() == JsonToken.NUMBER) {
                            filler = json.nextInt();
                        } else if (json.peek() == JsonToken.NULL) {
                            json.nextNull();
                        } else {
                            json.skipValue();
                        }
                    }
                    default -> json.skipValue();
                }
            }
            json.endObject();
            visitor.accept(name, filler);
        }
        json.endArray();
    }

    @Nullable
    private static String collectUniqueNames(
        @Nonnull JsonReader json,
        @Nonnull String field,
        @Nonnull Set<String> output
    ) throws IOException {
        if (json.peek() == JsonToken.NULL) {
            json.nextNull();
            return null;
        }
        if (json.peek() != JsonToken.BEGIN_ARRAY) {
            json.skipValue();
            return "Prefab " + field + " must be an array";
        }
        json.beginArray();
        String firstError = null;
        int i = 0;
        while (json.hasNext()) {
            if (json.peek() != JsonToken.BEGIN_OBJECT) {
                json.skipValue();
                if (firstError == null) {
                    firstError = "Prefab " + field + "[" + i + "] must be an object";
                }
                i++;
                continue;
            }
            String name = null;
            boolean hasStringName = false;
            json.beginObject();
            while (json.hasNext()) {
                if ("name".equals(json.nextName())) {
                    if (json.peek() == JsonToken.STRING) {
                        name = json.nextString();
                        hasStringName = true;
                    } else {
                        json.skipValue();
                    }
                } else {
                    json.skipValue();
                }
            }
            json.endObject();
            if (firstError == null) {
                if (!hasStringName) {
                    firstError = "Prefab " + field + "[" + i + "] has no string name";
                } else {
                    String key = name != null ? name.trim() : "";
                    if (key.isEmpty()) {
                        firstError = "Prefab " + field + "[" + i + "] has an empty name";
                    } else {
                        output.add(key);
                    }
                }
            } else if (hasStringName && name != null) {
                String key = name.trim();
                if (!key.isEmpty()) {
                    output.add(key);
                }
            }
            i++;
        }
        json.endArray();
        return firstError;
    }

    private static void collectEntityAssetStrings(@Nonnull JsonReader json, @Nonnull Set<String> output)
        throws IOException {
        collectStringLeaves(json, output);
    }

    private static void collectStringLeaves(@Nonnull JsonReader json, @Nonnull Set<String> output) throws IOException {
        switch (json.peek()) {
            case STRING -> {
                String s = json.nextString().trim();
                if (!s.isEmpty() && s.length() <= 128 && looksLikeAssetKey(s)) {
                    output.add(s);
                }
            }
            case BEGIN_ARRAY -> {
                json.beginArray();
                while (json.hasNext()) {
                    collectStringLeaves(json, output);
                }
                json.endArray();
            }
            case BEGIN_OBJECT -> {
                json.beginObject();
                while (json.hasNext()) {
                    json.nextName();
                    collectStringLeaves(json, output);
                }
                json.endObject();
            }
            default -> json.skipValue();
        }
    }

    @Nullable
    private static Integer readOptionalInt(@Nonnull JsonReader json) throws IOException {
        if (json.peek() == JsonToken.NUMBER) {
            return json.nextInt();
        }
        json.skipValue();
        return null;
    }

    private static boolean looksLikeAssetKey(@Nonnull String s) {
        if (s.length() < 3) {
            return false;
        }
        if (s.indexOf(' ') >= 0 || s.indexOf('\n') >= 0) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z'
                || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9'
                || c == '_'
                || c == '-'
                || c == ':'
                || c == '.'
                || c == '/')) {
                return false;
            }
        }
        return true;
    }
}
