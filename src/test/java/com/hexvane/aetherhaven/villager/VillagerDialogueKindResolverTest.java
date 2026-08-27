package com.hexvane.aetherhaven.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
final class VillagerDialogueKindResolverTest {
    @Test
    void normalizeBindingKind_stripsVisitorPrefix() {
        assertEquals("mechanic", VillagerDialogueKindResolver.normalizeBindingKind("visitor_mechanic"));
    }

    @Test
    void normalizeBindingKind_stripsRescuePrefix() {
        assertEquals("clown", VillagerDialogueKindResolver.normalizeBindingKind("rescue_clown"));
    }

    @Test
    void normalizeBindingKind_lowercases() {
        assertEquals("merchant", VillagerDialogueKindResolver.normalizeBindingKind("MERCHANT"));
    }
}
