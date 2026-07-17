package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterCatalog;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownsfolkPoolState {
    private final Map<String, TownsfolkPoolCheckoutRecord> checkouts = new LinkedHashMap<>();

    @Nonnull
    public Map<String, TownsfolkPoolCheckoutRecord> getCheckouts() {
        return Map.copyOf(checkouts);
    }

    public boolean isCheckedOut(@Nonnull String characterId) {
        return checkouts.containsKey(characterId.trim());
    }

    @Nullable
    public TownsfolkPoolCheckoutRecord checkoutForCharacter(@Nonnull String characterId) {
        return checkouts.get(characterId.trim());
    }

    @Nullable
    public TownsfolkPoolCheckoutRecord checkoutForEntity(@Nonnull UUID entityUuid) {
        String key = entityUuid.toString();
        for (TownsfolkPoolCheckoutRecord r : checkouts.values()) {
            if (key.equals(r.getEntityUuid())) {
                return r;
            }
        }
        return null;
    }

    @Nonnull
    public List<String> availableCharacterIds(
        @Nonnull TownsfolkCharacterCatalog catalog,
        @Nonnull String assignmentKind
    ) {
        List<String> out = new ArrayList<>();
        for (TownsfolkCharacterDefinition def : catalog.allById().values()) {
            String id = def.getId();
            if (!checkouts.containsKey(id) && def.supportsAssignment(assignmentKind)) {
                out.add(id);
            }
        }
        return out;
    }

    @Nullable
    public String pickRandomAvailableCharacterId(
        @Nonnull TownsfolkCharacterCatalog catalog,
        @Nonnull String assignmentKind,
        @Nonnull Random random
    ) {
        List<String> available = availableCharacterIds(catalog, assignmentKind);
        if (available.isEmpty()) {
            return null;
        }
        return available.get(random.nextInt(available.size()));
    }

    /** Characters that allow guard assignment and are not checked out. */
    @Nonnull
    public List<String> availableGuardEligibleCharacterIds(@Nonnull TownsfolkCharacterCatalog catalog) {
        List<String> out = new ArrayList<>();
        for (TownsfolkCharacterDefinition def : catalog.allById().values()) {
            String id = def.getId();
            if (!checkouts.containsKey(id) && def.supportsAssignment(TownsfolkAssignmentKinds.GUARD)) {
                out.add(id);
            }
        }
        return out;
    }

    @Nullable
    public String pickRandomGuardEligibleCharacterId(@Nonnull TownsfolkCharacterCatalog catalog, @Nonnull Random random) {
        List<String> available = availableGuardEligibleCharacterIds(catalog);
        if (available.isEmpty()) {
            return null;
        }
        return available.get(random.nextInt(available.size()));
    }

    public void checkout(@Nonnull TownsfolkPoolCheckoutRecord record) {
        checkouts.put(record.getCharacterId(), record);
    }

    public boolean release(@Nonnull String characterId) {
        return checkouts.remove(characterId.trim()) != null;
    }

    /** Releases every character checkout owned by a town. */
    public int releaseForTown(@Nonnull UUID townId) {
        String id = townId.toString();
        int before = checkouts.size();
        checkouts.entrySet().removeIf(e -> id.equalsIgnoreCase(e.getValue().getTownId().trim()));
        return before - checkouts.size();
    }

    public int clearAllCheckouts() {
        int n = checkouts.size();
        checkouts.clear();
        return n;
    }

    public void loadFromFile(@Nonnull TownsfolkPoolFile file) {
        checkouts.clear();
        checkouts.putAll(file.checkoutsByCharacterId());
    }

    @Nonnull
    public TownsfolkPoolFile toFile() {
        TownsfolkPoolFile file = new TownsfolkPoolFile();
        file.setCheckoutsFromMap(checkouts);
        return file;
    }
}
