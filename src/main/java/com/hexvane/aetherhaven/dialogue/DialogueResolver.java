package com.hexvane.aetherhaven.dialogue;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueResolver {
    public static final String KIND_TEST_VILLAGER = "test_villager";
    public static final String KIND_ELDER_LYREN = "elder_lyren";
    public static final String TREE_TEST = "aetherhaven_dialogue_test";
    public static final String TREE_ELDER_WEEK1 = "aetherhaven_elder_week1";

    private final Map<String, String> kindToTree = new HashMap<>();

    public DialogueResolver() {
        kindToTree.put(KIND_TEST_VILLAGER, TREE_TEST);
        kindToTree.put(KIND_ELDER_LYREN, TREE_ELDER_WEEK1);
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
        return new ResolvedDialogue(tree, "root");
    }

    public void registerKind(@Nonnull String kind, @Nonnull String treeId) {
        kindToTree.put(kind, treeId);
    }

    public record ResolvedDialogue(@Nonnull String treeId, @Nonnull String entryNodeId) {}
}
