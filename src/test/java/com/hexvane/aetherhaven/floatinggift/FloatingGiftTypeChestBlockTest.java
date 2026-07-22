package com.hexvane.aetherhaven.floatinggift;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("floating-gift")
class FloatingGiftTypeChestBlockTest {
    @Test
    void chestBlockIdsMatchGiftChestAssets() {
        assertEquals(FloatingGiftChestUtil.CHEST_WHITE, FloatingGiftType.REGULAR.chestBlockId());
        assertEquals(FloatingGiftChestUtil.CHEST_GREEN, FloatingGiftType.GREEN.chestBlockId());
        assertEquals(FloatingGiftChestUtil.CHEST_RED, FloatingGiftType.RED.chestBlockId());
    }
}
