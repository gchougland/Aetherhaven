package com.hexvane.aetherhaven.monument;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What a founder statue wears and how it stands. This object is the only owner of the real armor pieces while they
 * are on the statue: the copies shown on the statue entity are display only and are never handed out.
 *
 * <p>Every piece leaves through exactly one of {@link #put} (the piece it replaces), {@link #take} or
 * {@link #takeAll}, and each of those empties the slot it reads, so a piece can be returned once and only once.
 */
public final class FounderStatueLoadout {
    /** Armor slots in the order of the game's own {@code ItemArmorSlot}; the ordinal is the armor inventory slot. */
    public enum Slot {
        HEAD("Head"),
        CHEST("Chest"),
        HANDS("Hands"),
        LEGS("Legs");

        private final String key;

        Slot(@Nonnull String key) {
            this.key = key;
        }

        /** Save key, and the name of the game's matching armor slot. */
        @Nonnull
        public String key() {
            return key;
        }

        @Nullable
        public static Slot byKey(@Nullable String key) {
            for (Slot slot : values()) {
                if (slot.key.equalsIgnoreCase(key)) {
                    return slot;
                }
            }
            return null;
        }
    }

    @Nonnull
    public static final BuilderCodec<FounderStatueLoadout> CODEC =
        BuilderCodec.builder(FounderStatueLoadout.class, FounderStatueLoadout::new)
            .append(new KeyedCodec<>("Head", ItemStack.CODEC), (o, v) -> o.armor[0] = present(v), o -> o.armor[0])
            .add()
            .append(new KeyedCodec<>("Chest", ItemStack.CODEC), (o, v) -> o.armor[1] = present(v), o -> o.armor[1])
            .add()
            .append(new KeyedCodec<>("Hands", ItemStack.CODEC), (o, v) -> o.armor[2] = present(v), o -> o.armor[2])
            .add()
            .append(new KeyedCodec<>("Legs", ItemStack.CODEC), (o, v) -> o.armor[3] = present(v), o -> o.armor[3])
            .add()
            .append(new KeyedCodec<>("Pose", Codec.STRING), (o, v) -> o.poseId = v != null ? v : "", o -> o.poseId)
            .add()
            .build();

    private final ItemStack[] armor = new ItemStack[Slot.values().length];
    private String poseId = "";

    public FounderStatueLoadout() {}

    @Nullable
    private static ItemStack present(@Nullable ItemStack stack) {
        return ItemStack.isEmpty(stack) ? null : stack;
    }

    /** The piece in this slot, or null. Looking does not move it. */
    @Nullable
    public ItemStack get(@Nonnull Slot slot) {
        return armor[slot.ordinal()];
    }

    /**
     * Puts a piece in a slot.
     *
     * @return the piece that was there before and now belongs to the caller, or null
     */
    @Nullable
    public ItemStack put(@Nonnull Slot slot, @Nonnull ItemStack piece) {
        if (ItemStack.isEmpty(piece)) {
            throw new IllegalArgumentException("A statue cannot wear an empty stack");
        }
        ItemStack before = armor[slot.ordinal()];
        armor[slot.ordinal()] = piece;
        return before;
    }

    /** Removes and returns the piece in this slot, or null when the slot is empty. */
    @Nullable
    public ItemStack take(@Nonnull Slot slot) {
        ItemStack before = armor[slot.ordinal()];
        armor[slot.ordinal()] = null;
        return before;
    }

    /** Removes and returns every piece. A second call returns nothing. */
    @Nonnull
    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            ItemStack piece = take(slot);
            if (piece != null) {
                out.add(piece);
            }
        }
        return out;
    }

    public boolean hasArmor() {
        for (ItemStack piece : armor) {
            if (piece != null) {
                return true;
            }
        }
        return false;
    }

    /** Saved pose id, or blank for the plain standing statue. */
    @Nonnull
    public String getPoseId() {
        return poseId != null ? poseId : "";
    }

    public void setPoseId(@Nullable String poseId) {
        this.poseId = poseId != null ? poseId : "";
    }

    /**
     * Moves the pieces of {@code other} into this loadout's empty slots. Used when the game replaces the pedestal's
     * block entity with a new one, so the armor follows the block instead of being copied. A piece whose slot is
     * already filled here stays in {@code other}, which still owns it, so nothing is overwritten.
     */
    public void takeOver(@Nonnull FounderStatueLoadout other) {
        if (other == this) {
            return;
        }
        for (Slot slot : Slot.values()) {
            if (armor[slot.ordinal()] != null) {
                continue;
            }
            ItemStack piece = other.take(slot);
            if (piece != null) {
                armor[slot.ordinal()] = piece;
            }
        }
        if (getPoseId().isBlank()) {
            poseId = other.getPoseId();
        }
    }

    /** A separate loadout with the same contents, for the engine's component copies. */
    @Nonnull
    public FounderStatueLoadout copy() {
        FounderStatueLoadout c = new FounderStatueLoadout();
        System.arraycopy(armor, 0, c.armor, 0, armor.length);
        c.poseId = getPoseId();
        return c;
    }
}
