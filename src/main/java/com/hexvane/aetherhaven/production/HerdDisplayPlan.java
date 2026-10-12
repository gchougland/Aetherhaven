package com.hexvane.aetherhaven.production;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Decides which barn display entities stay in the world. Ownership lives in the town; an entity is only a picture
 * of one owned animal, so at most one may exist per animal and none may exist without naming its animal.
 */
public final class HerdDisplayPlan {
    private HerdDisplayPlan() {}

    /** One live display: the entity handle and the plot and animal its binding names. */
    public record Shown<T>(T handle, UUID plot, UUID animal) {}

    /** The single display kept for each animal of the plot, and every extra copy to remove. */
    public record Result<T>(Map<UUID, T> keep, List<T> remove) {}

    /**
     * Displays of other plots are left alone. A display is removed when its animal has no usable pen
     * ({@code penOf} below zero) or when an earlier display already shows that animal.
     */
    public static <T> Result<T> reconcile(UUID plot, Collection<Shown<T>> shown, ToIntFunction<UUID> penOf) {
        var keep = new LinkedHashMap<UUID, T>();
        var remove = new ArrayList<T>();
        for (var display : shown) {
            if (plot == null || !plot.equals(display.plot())) continue;
            if (display.animal() == null || penOf.applyAsInt(display.animal()) < 0
                    || keep.putIfAbsent(display.animal(), display.handle()) != null) remove.add(display.handle());
        }
        return new Result<>(keep, remove);
    }

    /**
     * The display roles are used for nothing else, so one found without a binding is a copy the town cannot
     * account for: it was saved or parked before it was tagged, then loaded back as an ordinary animal.
     */
    public static boolean stray(String role, boolean bound) {
        return !bound && BarnAnimalCatalog.displayRole(role);
    }
}
