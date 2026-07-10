package com.hexvane.aetherhaven.dialogue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.AetherhavenAssetPaths;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner.PackJsonFile;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueNodeDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialoguePatchDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialoguePatchDefinition.DialogueNodePatchDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Applies {@link AetherhavenAssetPaths#DIALOGUE_PATCHES} onto loaded dialogue trees. */
public final class DialoguePatchApplier {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DialoguePatchApplier() {}

    public static int applyAllPackPatches(@Nonnull Gson gson, @Nonnull Map<String, DialogueTreeDefinition> trees) {
        List<PackJsonFile> files =
            AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(AetherhavenAssetPaths.DIALOGUE_PATCHES);
        int applied = 0;
        for (PackJsonFile f : files) {
            try (InputStream in = Files.newInputStream(f.absolutePath())) {
                DialoguePatchDefinition patch =
                    gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), DialoguePatchDefinition.class);
                if (applyPatch(trees, patch, f.packName() + ":" + f.absolutePath())) {
                    applied++;
                }
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to load dialogue patch %s", f.absolutePath());
            }
        }
        if (applied > 0) {
            LOGGER.atInfo().log("Applied %s dialogue patch file(s) from asset packs", applied);
        }
        return applied;
    }

    /** Visible for tests and catalog loading. */
    public static boolean applyPatch(
        @Nonnull Map<String, DialogueTreeDefinition> trees,
        @Nonnull DialoguePatchDefinition patch,
        @Nonnull String label
    ) {
        if (patch.getTargetTreeId() == null || patch.getTargetTreeId().isBlank()) {
            LOGGER.atWarning().log("Skipping dialogue patch with missing targetTreeId: %s", label);
            return false;
        }
        String treeId = patch.getTargetTreeId().trim();
        DialogueTreeDefinition tree = trees.get(treeId);
        if (tree == null) {
            LOGGER.atWarning().log("Dialogue patch %s targets unknown tree %s", label, treeId);
            return false;
        }
        if (patch.schemaVersionOrDefault() != DialoguePatchDefinition.SUPPORTED_SCHEMA_VERSION) {
            LOGGER.atWarning().log(
                "Dialogue patch %s schemaVersion %s (expected %s)",
                label,
                patch.schemaVersionOrDefault(),
                DialoguePatchDefinition.SUPPORTED_SCHEMA_VERSION
            );
        }
        int nodesAdded = 0;
        for (var e : patch.addNodesOrEmpty().entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                continue;
            }
            tree.putNode(e.getKey().trim(), e.getValue());
            nodesAdded++;
        }
        int choicesAdded = 0;
        for (DialogueNodePatchDefinition nodePatch : patch.nodePatchesOrEmpty()) {
            if (nodePatch.getNodeId() == null || nodePatch.getNodeId().isBlank()) {
                LOGGER.atWarning().log("Dialogue patch %s has nodePatches entry without nodeId", label);
                continue;
            }
            String nodeId = nodePatch.getNodeId().trim();
            DialogueNodeDefinition node = tree.getNode(nodeId);
            if (node == null) {
                LOGGER.atWarning().log("Dialogue patch %s targets missing node %s in tree %s", label, nodeId, treeId);
                continue;
            }
            for (DialogueChoiceDefinition choice : nodePatch.addChoicesOrEmpty()) {
                if (choice == null) {
                    continue;
                }
                node.addOrReplaceChoice(choice);
                choicesAdded++;
            }
        }
        LOGGER
            .atInfo()
            .log(
                "Applied dialogue patch %s to tree %s (+%s nodes, +%s choices)",
                label,
                treeId,
                nodesAdded,
                choicesAdded
            );
        return true;
    }
}
