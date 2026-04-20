package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.BsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * Merges missing keys from codec-encoded defaults into an existing JSON config file so upgrades add new
 * options without overwriting user-set values.
 */
public final class PluginConfigMerge {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PluginConfigMerge() {}

    /**
     * For each key present in {@code defaults} but missing in {@code user}, inserts the default value.
     * For nested documents, recurses so missing sub-keys are filled; existing keys keep the user's value.
     *
     * @return number of leaf values inserted (each missing key at any depth counts as one).
     */
    public static int mergeMissingKeys(BsonDocument user, BsonDocument defaults) {
        int added = 0;
        for (String key : defaults.keySet()) {
            BsonValue dv = defaults.get(key);
            if (!user.containsKey(key)) {
                user.put(key, dv);
                added++;
            } else {
                BsonValue uv = user.get(key);
                if (dv.isDocument() && uv.isDocument()) {
                    added += mergeMissingKeys(uv.asDocument(), dv.asDocument());
                }
            }
        }
        return added;
    }

    /**
     * Reads {@code configJsonPath}, merges in any keys from {@code codec}'s default value, and writes back
     * if anything was added. Does nothing if the file is absent, empty, or not valid JSON.
     *
     * @return number of leaf values inserted, or 0 if the file was unchanged or merge was skipped
     */
    public static <T> int appendMissingKeys(Path configJsonPath, BuilderCodec<T> codec) {
        if (!Files.isRegularFile(configJsonPath)) {
            return 0;
        }
        String content;
        try {
            content = Files.readString(configJsonPath);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Could not read %s; skipping config merge", configJsonPath);
            return 0;
        }
        if (content.isBlank()) {
            return 0;
        }
        BsonDocument user;
        try {
            user = BsonDocument.parse(content);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Could not parse %s as JSON; skipping config merge", configJsonPath);
            return 0;
        }

        BsonValue encoded = codec.encode(codec.getDefaultValue(), new ExtraInfo());
        if (!encoded.isDocument()) {
            LOGGER.atWarning().log("Codec default is not a BSON document; skipping config merge for %s", configJsonPath);
            return 0;
        }
        BsonDocument defaults = encoded.asDocument();
        int added = mergeMissingKeys(user, defaults);
        if (added > 0) {
            BsonUtil.writeDocument(configJsonPath, user, true).join();
        }
        return added;
    }
}
