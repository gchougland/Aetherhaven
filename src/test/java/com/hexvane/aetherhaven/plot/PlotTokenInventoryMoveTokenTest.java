package com.hexvane.aetherhaven.plot;

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
class PlotTokenInventoryMoveTokenTest {
    @Test
    void moveTokenExcludedFromRegularPlotTokenCheck() throws ReflectiveOperationException {
        SimpleItemContainer inv = new SimpleItemContainer((short) 9);
        UUID plotId = UUID.randomUUID();
        inv.setItemStackForSlot(
            (short) 0,
            testStack(
                AetherhavenConstants.PLOT_TOKEN_UNIFIED,
                1,
                moveTokenMetadata("plot_inn", plotId.toString())
            )
        );
        ConstructionDefinition def = testDefinition("plot_inn", AetherhavenConstants.PLOT_TOKEN_UNIFIED);
        assertFalse(PlotTokenInventory.hasPlotToken(inv, def));
        assertTrue(PlotTokenInventory.hasMoveTokenForPlot(inv, plotId));
    }

    @Nonnull
    private static BsonDocument moveTokenMetadata(@Nonnull String constructionId, @Nonnull String plotId) {
        BsonDocument root = new BsonDocument();
        root.put(PlotTokenMetadata.FIELD_CONSTRUCTION_ID, new BsonString(constructionId));
        root.put(PlotTokenMetadata.FIELD_PLOT_ID, new BsonString(plotId));
        BsonDocument meta = new BsonDocument();
        meta.put(PlotTokenMetadata.BSON_KEY, root);
        return meta;
    }

    @Nonnull
    private static ConstructionDefinition testDefinition(@Nonnull String id, @Nonnull String tokenItemId)
        throws ReflectiveOperationException {
        ConstructionDefinition def = new ConstructionDefinition();
        setField(def, "id", id);
        setField(def, "plotTokenItemId", tokenItemId);
        return def;
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

    private static void setField(@Nonnull Object target, @Nonnull String name, @Nullable Object value)
        throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
