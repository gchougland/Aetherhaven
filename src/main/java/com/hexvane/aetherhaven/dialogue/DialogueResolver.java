package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
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

    private final Map<String, String> kindToTree = new HashMap<>();

    public DialogueResolver() {
        kindToTree.put(KIND_TEST_VILLAGER, TREE_TEST);
        kindToTree.put(KIND_ELDER_LYREN, TREE_ELDER_WEEK2);
        kindToTree.put(KIND_INNKEEPER, TREE_INN_WELCOME);
    }

    @Nonnull
    public ResolvedDialogue resolve(
        @Nullable String explicitDialogueId,
        @Nullable String villagerKind,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (explicitDialogueId != null && !explicitDialogueId.isBlank()) {
            return new ResolvedDialogue(explicitDialogueId.trim(), "root");
        }
        String kind = villagerKind != null && !villagerKind.isBlank() ? villagerKind.trim() : KIND_TEST_VILLAGER;
        String tree = kindToTree.getOrDefault(kind, TREE_TEST);
        String entry = "root";
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null && npcRef != null && npcRef.isValid()) {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
            TownRecord town = VillagerReputationService.findTownForPlayer(playerRef, store, tm);
            UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
            UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
            if (town != null && pu != null && nu != null) {
                String pendingEntry = VillagerReputationService.peekPendingRewardEntryNode(town, pu.getUuid(), nu.getUuid());
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
