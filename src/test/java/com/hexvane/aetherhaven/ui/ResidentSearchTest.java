package com.hexvane.aetherhaven.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentSearchTest {
    @Test void nameSearchIgnoresCaseWhitespaceAndWordOrder() {
        assertTrue(ResidentSearch.matches("Briar Mosscap", "  MOSS bri  "));
        assertTrue(ResidentSearch.matches("Mertie", "mER"));
        assertFalse(ResidentSearch.matches("Mertie", "briar"));
        assertFalse(ResidentSearch.matches("Briar Mosscap", "briar mertie"));
        assertTrue(ResidentSearch.matches("Mertie", ""));
        assertTrue(ResidentSearch.matches("Mertie", null));
    }
}
