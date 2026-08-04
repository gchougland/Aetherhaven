package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownVillagerDirectoryRegistryFallbackTest {

    @Test
    void storyRoleKeyMatchesCaseInsensitive() {
        UUID nil = new UUID(0L, 0L);
        ResidentNpcRecord record =
            new ResidentNpcRecord("Aetherhaven_Chef", TownVillagerBinding.KIND_CHEF, null, UUID.randomUUID());
        String roleId = record.getNpcRoleId().trim();
        Set<String> loadedStoryRoleIds = Set.of("aetherhaven_chef");

        boolean skip =
            ResidentRegistryService.isGaiaRevivalEligible(record.getKind(), roleId)
                && loadedStoryRoleIds.contains(roleId.toLowerCase(Locale.ROOT));
        boolean wouldAdd =
            !record.getLastEntityUuid().equals(nil) && !skip;

        assertEquals(false, wouldAdd);
    }
}
