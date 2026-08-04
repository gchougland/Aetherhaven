package com.hexvane.aetherhaven.guide;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class GuideMarkdownUiAppenderTest {

    @Test
    void defaultFallbackUsesSquareSizeForPortraitIcons() {
        assertArrayEquals(
            new int[] { 128, 128 },
            GuideMarkdownUiAppender.defaultFallbackPixelSize("Icons/ModelsGenerated/Portrait_Hobbit_Bilbo.png")
        );
    }

    @Test
    void defaultFallbackUsesWideHeroSizeForWikiScreenshots() {
        assertArrayEquals(
            new int[] { 560, 280 },
            GuideMarkdownUiAppender.defaultFallbackPixelSize("UI/Custom/Aetherhaven/wiki/villager_farmer.png")
        );
    }
}
