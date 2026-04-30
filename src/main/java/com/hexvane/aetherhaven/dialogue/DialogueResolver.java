package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueResolver {
    public static final String KIND_TEST_VILLAGER = "test_villager";
    public static final String KIND_ELDER_LYREN = "elder_lyren";
    public static final String TREE_TEST = "aetherhaven_dialogue_test";
    public static final String TREE_ELDER_WEEK1 = "aetherhaven_elder_week1";
    public static final String TREE_ELDER_WEEK2 = "aetherhaven_elder_week2";
    public static final String KIND_INNKEEPER = "innkeeper";
    public static final String TREE_INN_WELCOME = "aetherhaven_inn_welcome";

    public static final String VISITOR_DEFAULT = "aetherhaven_visitor_generic";
    public static final String VISITOR_ELDER = "aetherhaven_visitor_elder";
    public static final String VISITOR_INN = "aetherhaven_visitor_inn";

    private final Map<String, String> kindToTree = new HashMap<>();
    private final Map<String, String> kindToVisitorTree = new HashMap<>();

    public DialogueResolver() {
        applyLegacyDefaultKindMaps();
    }

    private void applyLegacyDefaultKindMaps() {
        kindToTree.clear();
        kindToVisitorTree.clear();
        kindToTree.put(KIND_TEST_VILLAGER, TREE_TEST);
        kindToTree.put(KIND_ELDER_LYREN, TREE_ELDER_WEEK2);
        kindToTree.put(KIND_INNKEEPER, TREE_INN_WELCOME);
        kindToVisitorTree.put(KIND_TEST_VILLAGER, VISITOR_DEFAULT);
        kindToVisitorTree.put(KIND_ELDER_LYREN, VISITOR_ELDER);
        kindToVisitorTree.put(KIND_INNKEEPER, VISITOR_INN);
        kindToVisitorTree.put("merchant", VISITOR_DEFAULT);
        kindToVisitorTree.put("blacksmith", VISITOR_DEFAULT);
        kindToVisitorTree.put("farmer", VISITOR_DEFAULT);
        kindToVisitorTree.put("priestess", VISITOR_DEFAULT);
        kindToVisitorTree.put("miner", VISITOR_DEFAULT);
        kindToVisitorTree.put("logger", VISITOR_DEFAULT);
        kindToVisitorTree.put("rancher", VISITOR_DEFAULT);
    }

    /** Called on asset catalog reload. Falls back to {@link #applyLegacyDefaultKindMaps} when the catalog is empty. */
    public void reloadFromVillagerCatalog(@Nullable VillagerDefinitionCatalog catalog) {
        if (catalog == null || catalog.allByNpcRoleId().isEmpty()) {
            applyLegacyDefaultKindMaps();
            return;
        }
        kindToTree.clear();
        kindToVisitorTree.clear();
        for (VillagerDefinition d : catalog.allByNpcRoleId().values()) {
            String k = d.getDialogueVillagerKind();
            if (k.isEmpty()) {
                continue;
            }
            String res = d.getResidentTreeId() != null && !d.getResidentTreeId().isBlank() ? d.getResidentTreeId() : TREE_TEST;
            String vis = d.getVisitorTreeId() != null && !d.getVisitorTreeId().isBlank() ? d.getVisitorTreeId() : VISITOR_DEFAULT;
            kindToTree.put(k, res);
            kindToVisitorTree.put(k, vis);
        }
    }

    @Nonnull
    public ResolvedDialogue resolve(
        @Nullable String explicitDialogueId,
        @Nullable String villagerKind,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        String kind = villagerKind != null && !villagerKind.isBlank() ? villagerKind.trim() : KIND_TEST_VILLAGER;
        String tree =
            explicitDialogueId != null && !explicitDialogueId.isBlank()
                ? explicitDialogueId.trim()
                : kindToTree.getOrDefault(kind, TREE_TEST);
        String entry = "root";
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null && npcRef != null && npcRef.isValid()) {
            World world = store.getExternalData().getWorld();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
            TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            if (pu != null && binding != null) {
                TownRecord npcTown = tm.getTown(binding.getTownId());
                boolean outsider = npcTown == null || !npcTown.hasMemberOrOwner(pu.getUuid());
                if (outsider) {
                    String vTree = kindToVisitorTree.getOrDefault(kind, VISITOR_DEFAULT);
                    return new ResolvedDialogue(vTree, "root");
                }
            }
            TownRecord town = VillagerReputationService.findTownForPlayer(playerRef, store, tm);
            UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
            if (town != null && pu != null && nu != null) {
                String pendingEntry = VillagerReputationService.peekPendingRewardEntryNode(
                    world, tm, town, pu.getUuid(), nu.getUuid()
                );
                if (pendingEntry != null && !pendingEntry.isBlank()) {
                    entry = pendingEntry.trim();
                }
            }
        }
        return new ResolvedDialogue(tree, entry);
    }

    public void registerKind(@Nonnull String kind, @Nonnull String treeId) {
        kindToTree.put(kind, treeId);
    }

    public record ResolvedDialogue(@Nonnull String treeId, @Nonnull String entryNodeId) {}
}
