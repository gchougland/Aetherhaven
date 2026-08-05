package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("construction")
final class PlotTokenIconPngTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsEmptyBytes() {
        assertFalse(PlotTokenIconPng.isValid(new byte[0]));
    }

    @Test
    void rejectsNonPngMagic() {
        byte[] bytes = new byte[PlotTokenIconPng.MIN_PNG_BYTES];
        bytes[0] = 0x00;
        bytes[1] = 0x01;
        bytes[2] = 0x02;
        bytes[3] = 0x03;
        assertFalse(PlotTokenIconPng.isValid(bytes));
    }

    @Test
    void rejectsTinyHeaderOnlyBuffer() {
        assertFalse(PlotTokenIconPng.isValid(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}));
        byte[] almost = new byte[PlotTokenIconPng.MIN_PNG_BYTES - 1];
        almost[0] = (byte) 0x89;
        almost[1] = 0x50;
        almost[2] = 0x4E;
        almost[3] = 0x47;
        assertFalse(PlotTokenIconPng.isValid(almost));
    }

    @Test
    void acceptsMinimalValidPngSizedBuffer() {
        assertTrue(PlotTokenIconPng.isValid(validPngBytes()));
    }

    @Test
    void emptyFileIsNotValidFile() throws Exception {
        Path empty = tempDir.resolve("Aetherhaven_Token_plot_empty.png");
        Files.write(empty, new byte[0]);
        assertFalse(PlotTokenIconPng.isValidFile(empty));
        assertTrue(PlotTokenIconPng.deleteIfInvalid(empty));
        assertFalse(Files.exists(empty));
    }

    @Test
    void writeAtomicallyRejectsInvalidAndWritesValid() throws Exception {
        Path dest = tempDir.resolve("Aetherhaven_Token_plot_ok.png");
        assertThrows(Exception.class, () -> PlotTokenIconPng.writeAtomically(dest, new byte[0]));
        assertFalse(Files.exists(dest));

        PlotTokenIconPng.writeAtomically(dest, validPngBytes());
        assertTrue(PlotTokenIconPng.isValidFile(dest));
        assertFalse(Files.exists(dest.resolveSibling(dest.getFileName().toString() + ".tmp")));
    }

    private static byte[] validPngBytes() {
        byte[] png = new byte[PlotTokenIconPng.MIN_PNG_BYTES];
        png[0] = (byte) 0x89;
        png[1] = 0x50;
        png[2] = 0x4E;
        png[3] = 0x47;
        return png;
    }
}
