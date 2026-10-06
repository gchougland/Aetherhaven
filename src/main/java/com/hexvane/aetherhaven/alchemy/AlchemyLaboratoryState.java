package com.hexvane.aetherhaven.alchemy;

import java.util.HashSet;
import java.util.Set;

/** Town laboratory research survives resident replacement and server restarts. */
public final class AlchemyLaboratoryState {
    private int extraIngredientSlots;
    private Set<String> discoveries = new HashSet<>();

    public int ingredientSlots() { return 1 + Math.clamp(extraIngredientSlots, 0, 2); }
    public int upgrades() { return ingredientSlots() - 1; }
    public boolean upgrade(int expected) {
        if (upgrades() != expected || expected >= 2) return false;
        extraIngredientSlots = expected + 1;
        return true;
    }
    public boolean discovered(String ingredient) { return discoveries != null && discoveries.contains(ingredient); }
    public void discover(Iterable<String> ingredients) {
        if (discoveries == null) discoveries = new HashSet<>();
        ingredients.forEach(discoveries::add);
    }
}
