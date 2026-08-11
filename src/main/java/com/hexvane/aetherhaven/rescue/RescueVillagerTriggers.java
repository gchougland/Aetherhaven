package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Static registry of rescue-villager triggers keyed by block type and binding kind. */
public final class RescueVillagerTriggers {
    private static final List<RescueVillagerTrigger> ALL = List.of(
        new RescueVillagerTrigger(
            AetherhavenConstants.CRYSTALLIZED_PERSON_BLOCK_TYPE_ID,
            AetherhavenConstants.QUEST_CRYSTAL_KEEPER_RESCUE,
            AetherhavenConstants.NPC_CRYSTAL_KEEPER_RESCUE,
            TownVillagerBinding.KIND_RESCUE_CRYSTAL_KEEPER,
            "aetherhaven_crystal_keeper_rescue",
            -1,
            2,
            AetherhavenConstants.CRYSTAL_KEEPER_RESCUE_VANISH_PARTICLE_SYSTEM_ID,
            AetherhavenConstants.CRYSTAL_KEEPER_RESCUE_VANISH_SOUND_EVENT_ID,
            "Crystal Keeper"
        ),
        new RescueVillagerTrigger(
            AetherhavenConstants.DECO_SPIDER_COCOON_BLOCK_TYPE_ID,
            AetherhavenConstants.QUEST_PYROTECHNIC_RESCUE,
            AetherhavenConstants.NPC_PYROTECHNIC_RESCUE,
            TownVillagerBinding.KIND_RESCUE_PYROTECHNIC,
            "aetherhaven_pyrotechnic_rescue",
            0,
            0,
            AetherhavenConstants.PYROTECHNIC_RESCUE_VANISH_PARTICLE_SYSTEM_ID,
            AetherhavenConstants.PYROTECHNIC_RESCUE_VANISH_SOUND_EVENT_ID,
            "Pyrotechnic"
        ),
        new RescueVillagerTrigger(
            AetherhavenConstants.CLOWN_WHEEL_RESCUE_BLOCK_TYPE_ID,
            AetherhavenConstants.QUEST_CLOWN_RESCUE,
            AetherhavenConstants.NPC_CLOWN_RESCUE,
            TownVillagerBinding.KIND_RESCUE_CLOWN,
            "aetherhaven_clown_rescue",
            0,
            0,
            AetherhavenConstants.CLOWN_RESCUE_VANISH_PARTICLE_SYSTEM_ID,
            AetherhavenConstants.CLOWN_RESCUE_VANISH_SOUND_EVENT_ID,
            "Clown"
        )
    );

    private static final Map<String, RescueVillagerTrigger> BY_BLOCK = buildBlockMap();
    private static final Map<String, RescueVillagerTrigger> BY_BINDING_KIND = buildBindingMap();

    private RescueVillagerTriggers() {}

    @Nonnull
    public static List<RescueVillagerTrigger> all() {
        return ALL;
    }

    @Nullable
    public static RescueVillagerTrigger byBlockTypeId(@Nonnull String blockTypeId) {
        return BY_BLOCK.get(blockTypeId.trim());
    }

    @Nullable
    public static RescueVillagerTrigger byBindingKind(@Nonnull String bindingKind) {
        return BY_BINDING_KIND.get(bindingKind.trim());
    }

    @Nonnull
    private static Map<String, RescueVillagerTrigger> buildBlockMap() {
        Map<String, RescueVillagerTrigger> out = new HashMap<>();
        for (RescueVillagerTrigger t : ALL) {
            out.put(t.triggerBlockTypeId(), t);
        }
        return Map.copyOf(out);
    }

    @Nonnull
    private static Map<String, RescueVillagerTrigger> buildBindingMap() {
        Map<String, RescueVillagerTrigger> out = new HashMap<>();
        for (RescueVillagerTrigger t : ALL) {
            out.put(t.rescueBindingKind(), t);
        }
        return Map.copyOf(out);
    }
}
