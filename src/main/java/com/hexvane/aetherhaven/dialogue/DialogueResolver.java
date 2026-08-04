package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import com.hexvane.aetherhaven.quest.QuestDialogueEntry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.rescue.RescueVillagerTriggers;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerBefriendableResolver;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hexvane.aetherhaven.worldnpc.WorldNpcBinding;
import com.hexvane.aetherhaven.worldnpc.WorldNpcPlayerProgress;
import com.hexvane.aetherhaven.worldnpc.WorldNpcReputationService;
import com.hexvane.aetherhaven.worldnpc.WorldQuestDialogueEntry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueResolver {
    private static final String DEFAULT_DIALOGUE_KIND = "merchant";
    private static final String DEFAULT_RESIDENT_DIALOGUE_TREE = "aetherhaven_merchant";
    public static final String KIND_ELDER_LYREN = "elder_lyren";
    public static final String TREE_ELDER = "aetherhaven_elder";
    public static final String KIND_INNKEEPER = "innkeeper";
    public static final String TREE_INN_WELCOME = "aetherhaven_inn_welcome";

    public static final String VISITOR_DEFAULT = "aetherhaven_visitor_generic";
    public static final String VISITOR_ELDER = "aetherhaven_visitor_elder";
    public static final String VISITOR_INN = "aetherhaven_visitor_inn";

    public static final String KIND_GUILD_MASTER = "guild_master";
    public static final String TREE_GUILD_MASTER = "aetherhaven_guild_master";
    public static final String KIND_GUILD_ADVENTURER = "guild_adventurer";
    public static final String TREE_GUILD_ADVENTURER = "aetherhaven_guild_adventurer";

    public static final String KIND_TOWNSFOLK = "townsfolk";
    public static final String TREE_TOWNSFOLK_GENERIC = "aetherhaven_townsfolk_generic";
    public static final String KIND_TOURIST = "tourist";
    public static final String TREE_TOURIST = "aetherhaven_tourist";
    public static final String KIND_GUARD = "guard";
    public static final String TREE_GUARD = "aetherhaven_guard";

    public static final String KIND_CRYSTAL_KEEPER = "crystal_keeper";
    public static final String TREE_CRYSTAL_KEEPER = "aetherhaven_crystal_keeper";

    public static final String KIND_PYROTECHNIC = "pyrotechnic";
    public static final String TREE_PYROTECHNIC = "aetherhaven_pyrotechnic";

    private final Map<String, String> kindToTree = new HashMap<>();
    private final Map<String, String> kindToVisitorTree = new HashMap<>();

    public DialogueResolver() {
        applyLegacyDefaultKindMaps();
    }

    private void applyLegacyDefaultKindMaps() {
        kindToTree.clear();
        kindToVisitorTree.clear();
        kindToTree.put(KIND_ELDER_LYREN, TREE_ELDER);
        kindToTree.put(KIND_INNKEEPER, TREE_INN_WELCOME);
        kindToTree.put(KIND_GUILD_MASTER, TREE_GUILD_MASTER);
        kindToTree.put(KIND_GUILD_ADVENTURER, TREE_GUILD_ADVENTURER);
        kindToVisitorTree.put(KIND_ELDER_LYREN, VISITOR_ELDER);
        kindToVisitorTree.put(KIND_INNKEEPER, VISITOR_INN);
        kindToVisitorTree.put("merchant", VISITOR_DEFAULT);
        kindToVisitorTree.put("blacksmith", VISITOR_DEFAULT);
        kindToVisitorTree.put("farmer", VISITOR_DEFAULT);
        kindToVisitorTree.put("priestess", VISITOR_DEFAULT);
        kindToVisitorTree.put("miner", VISITOR_DEFAULT);
        kindToVisitorTree.put("logger", VISITOR_DEFAULT);
        kindToVisitorTree.put("rancher", VISITOR_DEFAULT);
        kindToVisitorTree.put(KIND_CRYSTAL_KEEPER, VISITOR_DEFAULT);
        kindToVisitorTree.put(KIND_PYROTECHNIC, VISITOR_DEFAULT);
        kindToVisitorTree.put("florist", VISITOR_DEFAULT);
        kindToVisitorTree.put("chef", VISITOR_DEFAULT);
        kindToVisitorTree.put("builder", VISITOR_DEFAULT);
        registerNonVillagerDialogueKinds();
    }

    /** Townsfolk and other dialogue kinds not backed by {@link VillagerDefinition} assets. */
    private void registerNonVillagerDialogueKinds() {
        kindToTree.put(KIND_TOWNSFOLK, TREE_TOWNSFOLK_GENERIC);
        kindToTree.put(KIND_TOURIST, TREE_TOURIST);
        kindToTree.put(KIND_GUARD, TREE_GUARD);
        kindToTree.put(KIND_GUILD_ADVENTURER, TREE_GUILD_ADVENTURER);
        kindToVisitorTree.put(KIND_TOWNSFOLK, VISITOR_DEFAULT);
        kindToVisitorTree.put(KIND_TOURIST, VISITOR_DEFAULT);
        kindToVisitorTree.put(KIND_GUARD, VISITOR_DEFAULT);
        kindToVisitorTree.put(KIND_GUILD_MASTER, VISITOR_DEFAULT);
        kindToVisitorTree.put(KIND_GUILD_ADVENTURER, VISITOR_DEFAULT);
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
            String res =
                d.getResidentTreeId() != null && !d.getResidentTreeId().isBlank()
                    ? d.getResidentTreeId()
                    : DEFAULT_RESIDENT_DIALOGUE_TREE;
            String vis = d.getVisitorTreeId() != null && !d.getVisitorTreeId().isBlank() ? d.getVisitorTreeId() : VISITOR_DEFAULT;
            kindToTree.put(k, res);
            kindToVisitorTree.put(k, vis);
        }
        registerNonVillagerDialogueKinds();
    }

    @Nonnull
    public ResolvedDialogue resolve(
        @Nullable String explicitDialogueId,
        @Nullable String villagerKind,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        String kind = villagerKind != null && !villagerKind.isBlank() ? villagerKind.trim() : DEFAULT_DIALOGUE_KIND;
        if (plugin != null && npcRef != null && npcRef.isValid()) {
            TownVillagerBinding guardBinding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            if (guardBinding != null && TownVillagerBinding.KIND_GUARD.equals(guardBinding.getKind())) {
                kind = KIND_GUARD;
            } else {
                var townsfolkBinding =
                    store.getComponent(npcRef, com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding.getComponentType());
                if (townsfolkBinding != null) {
                    if (com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds.isGuildHallAdventurer(
                        townsfolkBinding.getAssignmentKind()
                    )) {
                        kind = KIND_GUILD_ADVENTURER;
                    } else if (com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds.isTourist(
                        townsfolkBinding.getAssignmentKind()
                    )) {
                        kind = KIND_TOURIST;
                    } else {
                        kind = KIND_TOWNSFOLK;
                    }
                }
            }
        }
        String tree;
        if (explicitDialogueId != null && !explicitDialogueId.isBlank()) {
            tree = explicitDialogueId.trim();
        } else if (KIND_GUILD_ADVENTURER.equals(kind)) {
            tree = TREE_GUILD_ADVENTURER;
        } else if (KIND_TOWNSFOLK.equals(kind)) {
            tree = TREE_TOWNSFOLK_GENERIC;
        } else if (KIND_TOURIST.equals(kind)) {
            tree = TREE_TOURIST;
        } else if (KIND_GUARD.equals(kind)) {
            tree = TREE_GUARD;
        } else {
            tree = kindToTree.getOrDefault(kind, DEFAULT_RESIDENT_DIALOGUE_TREE);
        }
        String entry = "root";
        if (plugin != null) {
            DialogueTreeDefinition treeDef = plugin.getDialogueCatalog().get(tree);
            if (treeDef != null) {
                entry = treeDef.entryOrDefault();
            }
        }
        if (plugin != null && npcRef != null && npcRef.isValid()) {
            World world = store.getExternalData().getWorld();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
            WorldNpcBinding worldBinding = store.getComponent(npcRef, WorldNpcBinding.getComponentType());
            if (worldBinding != null && pu != null) {
                // World NPCs always use the resident tree (no outsider visitor gate).
                String pendingEntry = WorldNpcReputationService.peekPendingRewardEntryNode(
                    world,
                    plugin,
                    pu.getUuid(),
                    worldBinding.getPlacementId()
                );
                if (pendingEntry != null && !pendingEntry.isBlank()) {
                    entry = pendingEntry.trim();
                }
                if ("root".equals(entry)) {
                    WorldNpcPlayerProgress progress =
                        AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin)
                            .getOrCreatePlayerProgress(pu.getUuid());
                    String role = worldBinding.getNpcRoleId();
                    if (role.isEmpty()) {
                        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                        role = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
                    }
                    String qEntry = WorldQuestDialogueEntry.resolveOfferEntryNodeId(
                        plugin.getQuestCatalog(),
                        progress,
                        role
                    );
                    if (qEntry != null && !qEntry.isBlank()) {
                        entry = qEntry.trim();
                    }
                }
                WorldNpcReputationService.applyDailyTalkBonus(
                    world,
                    plugin,
                    store,
                    pu.getUuid(),
                    worldBinding.getPlacementId()
                );
                return new ResolvedDialogue(tree, entry);
            }
            TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            if (binding != null) {
                var rescueTrigger = RescueVillagerTriggers.byBindingKind(binding.getKind());
                if (rescueTrigger != null) {
                    return new ResolvedDialogue(rescueTrigger.rescueDialogueTreeId(), "root");
                }
            }
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
                if (VillagerBefriendableResolver.isBefriendable(store, npcRef, plugin)) {
                    String pendingEntry = VillagerReputationService.peekPendingRewardEntryNode(
                        world, tm, town, pu.getUuid(), nu.getUuid()
                    );
                    if (pendingEntry != null && !pendingEntry.isBlank()) {
                        entry = pendingEntry.trim();
                    }
                }
                if ("root".equals(entry)) {
                    NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                    String npcRole = npc != null && npc.getRoleName() != null ? npc.getRoleName().trim() : "";
                    String qEntryEntity = QuestDialogueEntry.resolveOfferEntryNodeIdForEntity(
                        plugin.getQuestCatalog(),
                        town,
                        pu.getUuid(),
                        nu.getUuid()
                    );
                    if (qEntryEntity != null && !qEntryEntity.isBlank()) {
                        entry = qEntryEntity.trim();
                    }
                    if ("root".equals(entry) && !npcRole.isEmpty()) {
                        String qEntry = QuestDialogueEntry.resolveOfferEntryNodeId(
                            plugin.getQuestCatalog(),
                            town,
                            pu.getUuid(),
                            npcRole
                        );
                        if (qEntry != null && !qEntry.isBlank()) {
                            entry = qEntry.trim();
                        }
                    }
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
