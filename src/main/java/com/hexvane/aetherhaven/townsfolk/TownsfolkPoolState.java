package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterCatalog;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownsfolkPoolState {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, TownsfolkPoolCheckoutRecord> checkouts = new LinkedHashMap<>();

    @Nonnull
    public Map<String, TownsfolkPoolCheckoutRecord> getCheckouts() {
        return Map.copyOf(checkouts);
    }

    public boolean isCheckedOut(@Nonnull UUID townId, @Nonnull String characterId) {
        return checkouts.containsKey(TownsfolkPoolKeys.checkoutKey(townId, characterId));
    }

    @Nullable
    public TownsfolkPoolCheckoutRecord checkoutForCharacter(@Nonnull UUID townId, @Nonnull String characterId) {
        return checkouts.get(TownsfolkPoolKeys.checkoutKey(townId, characterId));
    }

    @Nullable
    public TownsfolkPoolCheckoutRecord checkoutForEntity(@Nonnull UUID entityUuid) {
        String key = entityUuid.toString();
        for (TownsfolkPoolCheckoutRecord r : checkouts.values()) {
            if (key.equalsIgnoreCase(r.getEntityUuid().trim())) {
                return r;
            }
        }
        return null;
    }

    @Nonnull
    public List<String> availableCharacterIds(
        @Nonnull UUID townId,
        @Nonnull TownsfolkCharacterCatalog catalog,
        @Nonnull String assignmentKind
    ) {
        List<String> out = new ArrayList<>();
        for (TownsfolkCharacterDefinition def : catalog.allById().values()) {
            String id = def.getId();
            if (!isCheckedOut(townId, id)
                && def.supportsAssignment(assignmentKind)
                && TownsfolkCharacterAvailability.isEligibleForPoolDraw(def)) {
                out.add(id);
            }
        }
        return out;
    }

    @Nullable
    public String pickRandomAvailableCharacterId(
        @Nonnull UUID townId,
        @Nonnull TownsfolkCharacterCatalog catalog,
        @Nonnull String assignmentKind,
        @Nonnull Random random
    ) {
        List<String> available = availableCharacterIds(townId, catalog, assignmentKind);
        if (available.isEmpty()) {
            return null;
        }
        return available.get(random.nextInt(available.size()));
    }

    /** Characters that allow guard assignment and are not checked out in this town. */
    @Nonnull
    public List<String> availableGuardEligibleCharacterIds(
        @Nonnull UUID townId,
        @Nonnull TownsfolkCharacterCatalog catalog
    ) {
        List<String> out = new ArrayList<>();
        for (TownsfolkCharacterDefinition def : catalog.allById().values()) {
            String id = def.getId();
            if (!isCheckedOut(townId, id)
                && def.supportsAssignment(TownsfolkAssignmentKinds.GUARD)
                && TownsfolkCharacterAvailability.isEligibleForPoolDraw(def)) {
                out.add(id);
            }
        }
        return out;
    }

    @Nullable
    public String pickRandomGuardEligibleCharacterId(
        @Nonnull UUID townId,
        @Nonnull TownsfolkCharacterCatalog catalog,
        @Nonnull Random random
    ) {
        List<String> available = availableGuardEligibleCharacterIds(townId, catalog);
        if (available.isEmpty()) {
            return null;
        }
        return available.get(random.nextInt(available.size()));
    }

    public void checkout(@Nonnull TownsfolkPoolCheckoutRecord record) {
        if (TownsfolkPoolKeys.isBlankTownId(record.getTownId())) {
            LOGGER.atWarning().log("Skipping townsfolk checkout with blank townId for character %s", record.getCharacterId());
            return;
        }
        checkouts.put(TownsfolkPoolKeys.checkoutKey(record.getTownId(), record.getCharacterId()), record);
    }

    public boolean release(@Nonnull UUID townId, @Nonnull String characterId) {
        return checkouts.remove(TownsfolkPoolKeys.checkoutKey(townId, characterId)) != null;
    }

    /** Releases every character checkout owned by a town. */
    public int releaseForTown(@Nonnull UUID townId) {
        String id = townId.toString().trim().toLowerCase();
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
        checkouts.putAll(file.checkoutsByLedgerKey());
    }

    @Nonnull
    public TownsfolkPoolFile toFile() {
        TownsfolkPoolFile file = new TownsfolkPoolFile();
        file.setCheckoutsFromMap(checkouts);
        return file;
    }
}
