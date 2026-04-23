package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.ObjectSchema;
import com.hypixel.hytale.codec.schema.config.Schema;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * Avoids {@link com.hypixel.hytale.codec.Codec#BSON_DOCUMENT} / {@code BsonDocumentCodec} (deprecated in
 * the current Hytale API).
 */
public final class AetherhavenBsonCodecs {

    public static final Codec<BsonDocument> BSON_DOCUMENT = new Codec<>() {
        @Nullable
        @Override
        public BsonDocument decode(@Nullable BsonValue bsonValue, @Nonnull ExtraInfo extraInfo) {
            if (bsonValue == null || !bsonValue.isDocument()) {
                return null;
            }
            return bsonValue.asDocument();
        }

        @Nonnull
        @Override
        public BsonValue encode(BsonDocument document, @Nonnull ExtraInfo extraInfo) {
            return document;
        }

        @Nonnull
        @Override
        public Schema toSchema(@Nonnull SchemaContext context) {
            return new ObjectSchema();
        }
    };

    private AetherhavenBsonCodecs() {}
}
