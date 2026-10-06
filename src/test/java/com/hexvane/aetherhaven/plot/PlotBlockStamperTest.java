package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class PlotBlockStamperTest {
    private static final int INDEX = ChunkUtil.indexBlock(-3, 120, -17);

    private static final class RetainedData implements Component<ChunkStore> {
        final String value;
        RetainedData(String value) { this.value = value; }
        @Override public RetainedData clone() { return new RetainedData(value); }
    }

    private static final class Fixture implements AutoCloseable {
        final ComponentRegistry<ChunkStore> registry = new ComponentRegistry<>();
        final ComponentType<ChunkStore, ManagementBlock> type =
            registry.registerComponent(ManagementBlock.class, "Management", ManagementBlock.CODEC);
        final ComponentType<ChunkStore, RetainedData> extraType =
            registry.registerComponent(RetainedData.class, () -> new RetainedData("default"));
        final Store<ChunkStore> store = registry.addStore(null, EmptyResourceStorage.get());
        final BlockComponentSection section = new BlockComponentSection();
        final Holder<ChunkStore> template = holder("", "");
        boolean parkCreated;
        int attachments;

        Holder<ChunkStore> holder(String plot, String town) {
            var holder = registry.newHolder();
            holder.putComponent(type, new ManagementBlock(plot, town));
            holder.putComponent(extraType, new RetainedData("keep inventory and other saved state"));
            return holder;
        }

        Ref<ChunkStore> live(int index, Holder<ChunkStore> holder) {
            var ref = store.addEntity(holder, AddReason.LOAD);
            section.loadBlockReference(index, ref);
            return ref;
        }

        void attach(Holder<ChunkStore> holder) {
            attachments++;
            assertCorrect(holder.getComponent(type));
            if (parkCreated) section.addBlockHolder(INDEX, holder);
            else section.addBlockReference(INDEX, store.addEntity(holder, AddReason.SPAWN));
        }

        PlotBlockStamper.StampOutcome stamp(boolean force, boolean dryRun) {
            return PlotBlockStamper.stampLink(store, section, INDEX, type, new ManagementBlock("plot", "town"),
                value -> "plot".equals(value.getPlotId()) && "town".equals(value.getTownId()),
                template, this::attach, force, dryRun);
        }

        @Override public void close() { store.shutdown(); }
    }

    private static void assertCorrect(ManagementBlock link) {
        assertNotNull(link);
        assertEquals("plot", link.getPlotId());
        assertEquals("town", link.getTownId());
    }

    @Test void missingEntityIsAttachedOnceWithLinksAlreadySetAndTemplatePreserved() {
        for (boolean parked : new boolean[]{false, true}) {
            try (var f = new Fixture()) {
                f.parkCreated = parked;
                assertEquals(PlotBlockStamper.StampOutcome.STAMPED, f.stamp(false, false));
                assertEquals(1, f.attachments);
                assertEquals("", f.template.getComponent(f.type).getPlotId());
                assertEquals("", f.template.getComponent(f.type).getTownId());
                if (parked) {
                    assertNull(f.section.getBlockReference(INDEX));
                    assertNotSame(f.template, f.section.getBlockHolder(INDEX));
                    assertCorrect(f.section.getBlockHolder(INDEX).getComponent(f.type));
                } else {
                    assertCorrect(f.store.getComponent(f.section.getBlockReference(INDEX), f.type));
                }
                assertTrue(f.section.takeDirtyBlocks().contains((short) INDEX));
                for (int i = 0; i < 100; i++) {
                    assertEquals(PlotBlockStamper.StampOutcome.ALREADY_OK, f.stamp(false, false));
                }
                assertEquals(1, f.attachments, "Periodic repair must not repeatedly attach an already linked block");
                assertNull(f.section.takeDirtyBlocks());
                assertNull(f.section.takeRemovedBlocks());
            }
        }
    }

    @Test void correctParkedHolderIsRecognizedWithoutReplacementOrDirtying() {
        try (var f = new Fixture()) {
            var parked = f.holder("plot", "town");
            f.section.storeBlockHolder(INDEX, parked);
            assertEquals(PlotBlockStamper.StampOutcome.ALREADY_OK, f.stamp(false, false));
            assertSame(parked, f.section.getBlockHolder(INDEX));
            assertNull(f.section.getBlockReference(INDEX));
            assertEquals(0, f.attachments);
            assertNull(f.section.takeDirtyBlocks());
            assertFalse(f.section.needsSaving());
        }
    }

    @Test void staleLiveAndParkedLinksAreUpdatedInPlaceWithoutLosingOtherComponents() {
        for (boolean parked : new boolean[]{false, true}) {
            try (var f = new Fixture()) {
                var holder = f.holder("old plot", "old town");
                var data = holder.getComponent(f.extraType);
                Ref<ChunkStore> ref = null;
                if (parked) f.section.storeBlockHolder(INDEX, holder);
                else ref = f.live(INDEX, holder);
                assertEquals(PlotBlockStamper.StampOutcome.STAMPED, f.stamp(false, false));
                assertEquals(0, f.attachments);
                if (parked) {
                    assertSame(holder, f.section.getBlockHolder(INDEX));
                    assertSame(data, holder.getComponent(f.extraType));
                    assertCorrect(holder.getComponent(f.type));
                } else {
                    assertSame(ref, f.section.getBlockReference(INDEX));
                    assertTrue(ref.isValid());
                    assertSame(data, f.store.getComponent(ref, f.extraType));
                    assertCorrect(f.store.getComponent(ref, f.type));
                }
                assertTrue(f.section.takeDirtyBlocks().contains((short) INDEX));
                assertNull(f.section.takeRemovedBlocks());
            }
        }
    }

    @Test void unrelatedVerticalNeighborNeverReceivesTheMissingShelfsLink() {
        try (var f = new Fixture()) {
            int above = ChunkUtil.indexBlock(-3, 121, -17);
            var neighbor = f.live(above, f.holder("other plot", "other town"));
            var link = f.store.getComponent(neighbor, f.type);
            assertEquals(PlotBlockStamper.StampOutcome.STAMPED, f.stamp(false, false));
            assertSame(link, f.store.getComponent(neighbor, f.type));
            assertEquals("other plot", link.getPlotId());
            assertSame(neighbor, f.section.getBlockReference(above));
            assertNotSame(neighbor, f.section.getBlockReference(INDEX));
            var dirty = f.section.takeDirtyBlocks();
            assertTrue(dirty.contains((short) INDEX));
            assertFalse(dirty.contains((short) above));
        }
    }

    @Test void inspectionDoesNotCreateOrModifyLiveOrParkedEntities() {
        for (int state = 0; state < 3; state++) {
            try (var f = new Fixture()) {
                var holder = f.holder("old plot", "old town");
                Ref<ChunkStore> ref = null;
                if (state == 1) f.section.storeBlockHolder(INDEX, holder);
                if (state == 2) ref = f.live(INDEX, holder);
                assertEquals(PlotBlockStamper.StampOutcome.STAMPED, f.stamp(false, true));
                assertEquals(0, f.attachments);
                assertNull(f.section.takeDirtyBlocks());
                if (state == 0) assertNull(f.section.getBlockHolder(INDEX));
                if (state == 1) assertEquals("old plot", holder.getComponent(f.type).getPlotId());
                if (state == 2) assertEquals("old plot", f.store.getComponent(ref, f.type).getPlotId());
            }
        }
    }

    @Test void forceStampKeepsExistingHolderAndOtherState() {
        try (var f = new Fixture()) {
            var holder = f.holder("plot", "town");
            var data = holder.getComponent(f.extraType);
            f.section.storeBlockHolder(INDEX, holder);
            assertEquals(PlotBlockStamper.StampOutcome.STAMPED, f.stamp(true, false));
            assertSame(holder, f.section.getBlockHolder(INDEX));
            assertSame(data, holder.getComponent(f.extraType));
            assertEquals(0, f.attachments);
        }
    }

    @Test void invalidExistingReferenceIsNotOverwrittenOrAttachedTwice() {
        try (var f = new Fixture()) {
            var ref = f.live(INDEX, f.holder("old", "old"));
            f.store.removeEntity(ref, RemoveReason.UNLOAD);
            assertFalse(ref.isValid());
            assertEquals(PlotBlockStamper.StampOutcome.FAILED, f.stamp(false, false));
            assertEquals(0, f.attachments);
            assertSame(ref, f.section.getBlockReference(INDEX));
        }
    }
}
