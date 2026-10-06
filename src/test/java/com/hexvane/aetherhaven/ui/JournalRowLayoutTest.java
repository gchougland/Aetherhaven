package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("town")
class JournalRowLayoutTest {
    @Test void revisitingSamePopulationDoesNotRecreateWidgets() {
        var layout = new JournalRowLayout("#Residents", "Resident.ui");
        var first = new UICommandBuilder();
        assertEquals(0, layout.resize(first, 15));
        assertEquals(15, first.getCommands().length);
        var revisit = new UICommandBuilder();
        assertEquals(15, layout.resize(revisit, 15));
        assertEquals(0, revisit.getCommands().length);
    }

    @Test void populationChangesKeepExistingRowsAndRemoveFromEnd() {
        var layout = new JournalRowLayout("#Residents", "Resident.ui");
        layout.resize(new UICommandBuilder(), 4);
        var shrink = new UICommandBuilder();
        assertEquals(4, layout.resize(shrink, 2));
        assertEquals(2, shrink.getCommands().length);
        assertEquals(CustomUICommandType.Remove, shrink.getCommands()[0].type);
        assertEquals("#Residents[3]", shrink.getCommands()[0].selector);
        assertEquals("#Residents[2]", shrink.getCommands()[1].selector);
        var grow = new UICommandBuilder();
        assertEquals(2, layout.resize(grow, 3));
        assertEquals(1, grow.getCommands().length);
        assertEquals(CustomUICommandType.Append, grow.getCommands()[0].type);
        layout.resize(new UICommandBuilder(), 0);
        assertEquals(0, layout.resize(new UICommandBuilder(), 1));
    }
}
