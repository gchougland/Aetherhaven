package com.hexvane.aetherhaven.command;

import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.server.core.Options;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class AetherhavenTransferAccessTest {
    private final UUID owner = UUID.randomUUID();

    @Test void dedicatedServerWithoutLocalOptionsIsRejected() {
        assertFalse(AetherhavenTransferCommand.isLocalOwner(Options.PARSER.parse(), owner));
    }

    @Test void dedicatedServerWithAnOwnerUuidStillCannotTransfer() {
        assertFalse(AetherhavenTransferCommand.isLocalOwner(
            Options.PARSER.parse("--owner-uuid", owner.toString()), owner));
    }

    @Test void localSaveHostCanTransferButLanGuestCannot() {
        var options = Options.PARSER.parse("--singleplayer", "--owner-uuid", owner.toString());
        assertTrue(AetherhavenTransferCommand.isLocalOwner(options, owner));
        assertFalse(AetherhavenTransferCommand.isLocalOwner(options, UUID.randomUUID()));
    }

    @Test void missingStartupOptionsOrIdentityFailClosed() {
        assertFalse(AetherhavenTransferCommand.isLocalOwner(null, owner));
        assertFalse(AetherhavenTransferCommand.isLocalOwner(Options.PARSER.parse("--singleplayer"), owner));
        assertFalse(AetherhavenTransferCommand.isLocalOwner(
            Options.PARSER.parse("--singleplayer", "--owner-uuid", owner.toString()), null));
    }
}
