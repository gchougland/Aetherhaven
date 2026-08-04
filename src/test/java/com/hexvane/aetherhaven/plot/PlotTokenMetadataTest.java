package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PlotTokenMetadataTest {
    @Test
    void moveTokenRoundTrip() {
        UUID plotId = UUID.randomUUID();
        UUID townId = UUID.randomUUID();
        ItemStack stack = testStack(
            AetherhavenConstants.PLOT_TOKEN_UNIFIED,
            1,
            moveTokenMetadata("plot_inn", plotId.toString(), townId.toString())
        );
        assertTrue(PlotTokenMetadata.isMoveToken(stack));
        assertEquals("plot_inn", PlotTokenMetadata.readConstructionId(stack));
        assertEquals(plotId.toString(), PlotTokenMetadata.readPlotId(stack));
        assertEquals(townId.toString(), PlotTokenMetadata.readTownId(stack));
        assertFalse(PlotTokenMetadata.matchesConstruction(stack, "plot_inn"));
    }

    @Test
    void regularTokenIsNotMoveToken() {
        ItemStack stack =
            testStack(AetherhavenConstants.PLOT_TOKEN_UNIFIED, 1, regularTokenMetadata("plot_inn"));
        assertFalse(PlotTokenMetadata.isMoveToken(stack));
        assertTrue(PlotTokenMetadata.matchesConstruction(stack, "plot_inn"));
    }

    @Nonnull
    private static BsonDocument regularTokenMetadata(@Nonnull String constructionId) {
        BsonDocument root = new BsonDocument();
        root.put(PlotTokenMetadata.FIELD_CONSTRUCTION_ID, new BsonString(constructionId));
        BsonDocument meta = new BsonDocument();
        meta.put(PlotTokenMetadata.BSON_KEY, root);
        return meta;
    }

    @Nonnull
    private static BsonDocument moveTokenMetadata(
        @Nonnull String constructionId,
        @Nonnull String plotId,
        @Nonnull String townId
    ) {
        BsonDocument root = new BsonDocument();
        root.put(PlotTokenMetadata.FIELD_CONSTRUCTION_ID, new BsonString(constructionId));
        root.put(PlotTokenMetadata.FIELD_PLOT_ID, new BsonString(plotId));
        root.put(PlotTokenMetadata.FIELD_TOWN_ID, new BsonString(townId));
        BsonDocument meta = new BsonDocument();
        meta.put(PlotTokenMetadata.BSON_KEY, root);
        return meta;
    }

    @Nonnull
    private static ItemStack testStack(@Nonnull String itemId, int quantity, @Nullable BsonDocument metadata) {
        try {
            Constructor<ItemStack> ctor = ItemStack.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ItemStack stack = ctor.newInstance();
            setField(stack, "itemId", itemId);
            setField(stack, "quantity", quantity);
            if (metadata != null) {
                setField(stack, "metadata", metadata);
            }
            return stack;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(@Nonnull ItemStack stack, @Nonnull String name, @Nullable Object value)
        throws ReflectiveOperationException {
        Field field = ItemStack.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(stack, value);
    }
}
