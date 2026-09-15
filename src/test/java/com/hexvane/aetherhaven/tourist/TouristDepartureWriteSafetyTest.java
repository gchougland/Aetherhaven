package com.hexvane.aetherhaven.tourist;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the compiled departure paths without booting a world or mocking the ECS. */
@org.junit.jupiter.api.Tag("town")
class TouristDepartureWriteSafetyTest {
    private static ClassModel model() throws Exception {
        try (var input = TouristAutonomySystem.class.getResourceAsStream("TouristAutonomySystem.class")) {
            assertNotNull(input);
            return ClassFile.of().parse(input.readAllBytes());
        }
    }

    @Test void tickDepartureAndPortalRetryUseTheBufferedReleasePath() throws Exception {
        var model = model();
        for (String name : List.of("tryLeaveIfDue", "beginReturnToPortal")) {
            var method = model.methods().stream().filter(m -> m.methodName().equalsString(name)).findFirst().orElseThrow();
            var calls = method.code().orElseThrow().elementStream().filter(InvokeInstruction.class::isInstance)
                .map(InvokeInstruction.class::cast).toList();
            assertFalse(calls.stream().anyMatch(i -> i.name().equalsString("beginReturnToPortalOnStore")), name);
            assertEquals(name.equals("tryLeaveIfDue") ? 2 : 1,
                calls.stream().filter(i -> i.name().equalsString("beginReturnToPortalBuffered")).count());
        }
    }

    @Test void bufferedReleaseCannotSelectTheDirectStoreOverload() throws Exception {
        var lambda = model().methods().stream()
            .filter(m -> m.methodName().stringValue().startsWith("lambda$beginReturnToPortalBuffered$")).findFirst().orElseThrow();
        var release = lambda.code().orElseThrow().elementStream().filter(InvokeInstruction.class::isInstance)
            .map(InvokeInstruction.class::cast).filter(i -> i.name().equalsString("release")).findFirst().orElseThrow();
        assertTrue(release.type().stringValue().contains("Lcom/hypixel/hytale/component/CommandBuffer;"));
        assertFalse(release.type().stringValue().contains("Lcom/hypixel/hytale/component/Store;"));
    }
}
