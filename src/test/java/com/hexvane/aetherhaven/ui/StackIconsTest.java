package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.plot.PlotTokenMetadata;
import com.hexvane.aetherhaven.prop.PropItemMetadata;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;

/** The parts of the picture and name choice that need no running server. The pictures themselves need the game. */
@Tag("town")
class StackIconsTest {
    private static Object previousStore;

    private static class Items extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        Items(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() { super(String.class, Item.class, new DefaultAssetMap<>()); setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId); }
            @Override public Items build() { return new Items(this); }
        }
    }

    @BeforeAll static void assets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousStore = field.get(null);
        field.set(null, new Items.Config().build());
    }

    @AfterAll static void restore() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousStore);
    }

    @Test void ordinaryItemsKeepTheirOwnIconAndName() {
        var ore = new ItemStack("Ingredient_Bar_Iron", 12);
        assertNull(StackIcons.assetPath(ore));
        assertNull(StackIcons.instanceName(ore));
        assertNull(StackIcons.assetPath(null));
        assertNull(StackIcons.instanceName(null));
    }

    @Test void aPropOrTokenWithNoRecordOfWhatItIsKeepsThePlainItemIcon() {
        // The bare items exist on their own, for example when given by a command.
        assertNull(StackIcons.assetPath(new ItemStack(PropItemMetadata.PROP_ITEM_ID, 1)));
        assertNull(StackIcons.assetPath(new ItemStack(AetherhavenConstants.PLOT_TOKEN_UNIFIED, 1)));
    }

    @Test void aPictureTheClientWasNeverSentIsNotUsed() {
        var prop = PropItemMetadata.withProp(new ItemStack(PropItemMetadata.PROP_ITEM_ID, 1), "oak_bookshelf", "Oak Bookshelf");
        // No plugin is running here, so nothing can be resolved: the item icon stays.
        assertNull(StackIcons.assetPath(prop, null, path -> true));
        assertNull(StackIcons.assetPath(prop));
    }

    @Test void propsAndPlotTokensAreCalledWhatTheirInventoryTooltipCallsThem() {
        var prop = PropItemMetadata.withProp(new ItemStack(PropItemMetadata.PROP_ITEM_ID, 3), "oak_bookshelf", "Oak Bookshelf");
        String shown = com.hexvane.aetherhaven.inventory.ItemStackData.metadata(prop)
            .getDocument(PropItemMetadata.INSTANCE_TRANSLATION_PROPERTIES_KEY).getString("Name").getValue();
        assertEquals(shown, StackIcons.instanceName(prop));
        var token = PlotTokenMetadata.withConstruction(new ItemStack(AetherhavenConstants.PLOT_TOKEN_UNIFIED, 1), "plot_warehouse", "Warehouse");
        assertNotNull(StackIcons.instanceName(token));
        assertEquals("plot_warehouse", PlotTokenMetadata.readConstructionId(token));
    }
}
