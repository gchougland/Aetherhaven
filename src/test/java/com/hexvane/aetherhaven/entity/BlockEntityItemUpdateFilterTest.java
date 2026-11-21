package com.hexvane.aetherhaven.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.protocol.BlockUpdate;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.ItemUpdate;
import com.hypixel.hytale.protocol.ModelUpdate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
final class BlockEntityItemUpdateFilterTest {
    @Test
    void aBlockEntityIsOnlyToldItsBlockWhicheverCameFirst() {
        ItemUpdate item = new ItemUpdate(null, 2.4f);
        BlockUpdate block = new BlockUpdate(7, 2.4f);
        for (ComponentUpdate[] sent : new ComponentUpdate[][] {{item, block}, {block, item}}) {
            EntityUpdate entity = new EntityUpdate(1, null, sent);
            BlockEntityItemUpdateFilter.dropItemIfAlsoBlock(entity);
            assertArrayEquals(new ComponentUpdate[] {block}, entity.updates);
        }
    }

    @Test
    void anItemOnItsOwnStaysAnItem() {
        ComponentUpdate[] sent = {new ItemUpdate(null, 1f)};
        EntityUpdate entity = new EntityUpdate(1, null, sent);
        BlockEntityItemUpdateFilter.dropItemIfAlsoBlock(entity);
        assertSame(sent, entity.updates);
    }

    @Test
    void otherUpdatesAroundThemAreKeptInOrder() {
        ModelUpdate model = new ModelUpdate(null, 0f);
        BlockUpdate block = new BlockUpdate(7, 1f);
        EntityUpdate entity = new EntityUpdate(1, null, new ComponentUpdate[] {model, new ItemUpdate(null, 1f), block});
        BlockEntityItemUpdateFilter.dropItemIfAlsoBlock(entity);
        assertArrayEquals(new ComponentUpdate[] {model, block}, entity.updates);
    }

    @Test
    void entitiesWithNoUpdatesAreLeftAlone() {
        assertDoesNotThrow(() -> BlockEntityItemUpdateFilter.dropItemIfAlsoBlock(null));
        assertDoesNotThrow(() -> BlockEntityItemUpdateFilter.dropItemIfAlsoBlock(new EntityUpdate(1, null, null)));
    }
}
