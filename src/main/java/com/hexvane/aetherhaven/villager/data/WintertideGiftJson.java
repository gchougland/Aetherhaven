package com.hexvane.aetherhaven.villager.data;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One row of {@code wintertideGifts} on a villager definition: a fixed item, or a random pick from a list. */
public final class WintertideGiftJson {
    @SerializedName("itemId")
    @Nullable
    private String itemId;

    @SerializedName("pickOne")
    @Nullable
    private List<String> pickOne;

    @SerializedName("count")
    @Nullable
    private Integer count;

    /** How many times to roll this row. Defaults to 1. */
    @SerializedName("repeats")
    @Nullable
    private Integer repeats;

    @Nullable
    public String getItemId() {
        return itemId != null && !itemId.isBlank() ? itemId.trim() : null;
    }

    @Nonnull
    public List<String> getPickOne() {
        if (pickOne == null || pickOne.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String raw : pickOne) {
            if (raw != null && !raw.isBlank()) {
                out.add(raw.trim());
            }
        }
        return Collections.unmodifiableList(out);
    }

    public int getCount() {
        return count != null && count > 0 ? count : 1;
    }

    public int getRepeats() {
        return repeats != null && repeats > 0 ? repeats : 1;
    }
}
