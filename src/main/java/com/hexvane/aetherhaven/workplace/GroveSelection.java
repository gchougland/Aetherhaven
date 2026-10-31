package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import java.util.*;

/** Window-local navigation follows physical stones, even after uprooting frees their slot. */
final class GroveSelection {
    private final Set<Position> remembered = new LinkedHashSet<>();
    private Map<Integer, Position> bound = Map.of();
    private List<Position> stones = List.of();
    private Position selected;
    private UUID confirmedTree;

    GroveSelection(Position openedAt) {
        selected = openedAt;
        if (openedAt != null) remembered.add(openedAt);
    }

    void refresh(Map<Integer, Position> current) {
        rememberSelected();
        bound = new TreeMap<>(current);
        var positions = new LinkedHashSet<>(bound.values());
        positions.addAll(remembered);
        stones = List.copyOf(positions);
        if (selected == null || !stones.contains(selected)) selected = stones.isEmpty() ? null : stones.getFirst();
    }

    void selectSlot(int slot) {
        var position = bound.get(slot);
        if (position != null) { cancelConfirmation(); selected = position; }
    }

    void move(int direction) {
        cancelConfirmation();
        if (!stones.isEmpty()) selected = stones.get(Math.floorMod(stones.indexOf(selected) + direction, stones.size()));
    }

    void confirmUproot(UUID tree) { confirmedTree = tree; }
    void cancelConfirmation() { confirmedTree = null; }
    boolean takeUprootConfirmation(UUID tree) {
        boolean matches = tree != null && tree.equals(confirmedTree);
        cancelConfirmation();
        return matches;
    }

    void rememberSelected() { if (selected != null) remembered.add(selected); }
    Position block() { return selected; }
    int slot() { return bound.entrySet().stream().filter(e -> e.getValue().equals(selected)).mapToInt(Map.Entry::getKey).findFirst().orElse(-1); }
    int count() { return stones.size(); }
    int number() { return selected == null ? 0 : stones.indexOf(selected) + 1; }
}
