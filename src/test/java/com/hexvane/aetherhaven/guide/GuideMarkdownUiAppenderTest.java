package com.hexvane.aetherhaven.guide;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class GuideMarkdownUiAppenderTest {
    @Test void villagerPortraitIsAlwaysFirstAndLegacyHeroDoesNotDuplicateIt() {
        for(String article : new String[] {"Hello", "![Farmer](wiki/villager_farmer.png)\n\nHello", ""}) {
            var commands=new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder();
            int count=GuideMarkdownUiAppender.appendMarkdown(commands,"#Article",article,getClass().getClassLoader(),"Aetherhaven_Farmer",32);
            var all=commands.getCommands();
            assertEquals(com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType.Append,all[1].type);
            assertEquals("#Article",all[1].selector);
            assertEquals("#Article[0] #MdImage.AssetPath",all[2].selector);
            assertTrue(all[2].data.contains("Aetherhaven_Farmer.png"));
            assertEquals(article.isEmpty()?1:2,count);
        }
    }

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
