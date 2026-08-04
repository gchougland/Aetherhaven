package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonString;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PlotTokenSalvageBenchSystemTest {
    @Test
    void unifiedTokenWithMetadataShouldBeStripped() {
        ItemStack withMeta = testStack(
            AetherhavenConstants.PLOT_TOKEN_UNIFIED,
            2,
            unifiedTokenMetadata("plot_community_test")
        );
        assertTrue(PlotBlueprintSalvageBenchSystem.shouldStripSalvageMetadata(withMeta));
    }

    @Test
    void bareUnifiedTokenShouldBeLeftUnchanged() {
        ItemStack bare = testStack(AetherhavenConstants.PLOT_TOKEN_UNIFIED, 1, null);
        assertFalse(PlotBlueprintSalvageBenchSystem.shouldStripSalvageMetadata(bare));
    }

    @Test
    void blueprintWithMetadataShouldBeStripped() {
        ItemStack withMeta = testStack(
            AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE,
            1,
            blueprintMetadata("plot_farm")
        );
        assertTrue(PlotBlueprintSalvageBenchSystem.shouldStripSalvageMetadata(withMeta));
    }

    @Test
    void bareBlueprintShouldBeLeftUnchanged() {
        ItemStack bare = testStack(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, 1, null);
        assertFalse(PlotBlueprintSalvageBenchSystem.shouldStripSalvageMetadata(bare));
    }

    @Test
    void legacyTokenWithoutMetadataShouldBeUntouched() {
        ItemStack legacy = testStack(AetherhavenConstants.PLOT_TOKEN_GUILD_HALL, 1, null);
        assertFalse(PlotBlueprintSalvageBenchSystem.shouldStripSalvageMetadata(legacy));
    }

    @Test
    void moveTokenShouldNotBeStrippedForSalvage() {
        UUID plotId = UUID.randomUUID();
        ItemStack withMeta = testStack(
            AetherhavenConstants.PLOT_TOKEN_UNIFIED,
            1,
            moveTokenMetadata("plot_inn", plotId.toString())
        );
        assertFalse(PlotBlueprintSalvageBenchSystem.shouldStripSalvageMetadata(withMeta));
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
    private static BsonDocument unifiedTokenMetadata(@Nonnull String constructionId) {
        BsonDocument root = new BsonDocument();
        root.put(PlotTokenMetadata.FIELD_CONSTRUCTION_ID, new BsonString(constructionId));
        BsonDocument meta = new BsonDocument();
        meta.put(PlotTokenMetadata.BSON_KEY, root);
        return meta;
    }

    @Nonnull
    private static BsonDocument blueprintMetadata(@Nonnull String constructionId) {
        BsonDocument root = new BsonDocument();
        root.put(PlotTokenUnlockPageMetadata.FIELD_CONSTRUCTION_ID, new BsonString(constructionId));
        BsonDocument meta = new BsonDocument();
        meta.put(PlotTokenUnlockPageMetadata.BSON_KEY, root);
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
