package com.hexvane.aetherhaven.crafter;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.production.ProductionLiveWorkDetection;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Reads what props are made of and gives the Crafter his bench time. */
public final class PatternBookService {
    private PatternBookService() {}
    public static final String KIND = "crafter";
    private static final Map<String, Map<WorkshopParts.Kind, Integer>> WEIGHTS = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, long[]> BENCH = Collections.synchronizedMap(new WeakHashMap<>());

    /** Props can be edited or replaced while the server runs. */
    public static void invalidate() { WEIGHTS.clear(); }

    /** The materials a prop shows: its blocks, plus every item or block it displays as decoration. */
    public static Map<WorkshopParts.Kind, Integer> weights(PropDefinition prop) {
        return WEIGHTS.computeIfAbsent(prop.getId() + "|" + prop.getPrefabPath(), ignored -> {
            var ids = new ArrayList<String>();
            try {
                var buffer = PrefabResolveUtil.resolvePrefabBuffer(prop.getPrefabPath());
                if (buffer != null) {
                    var sequence = ConstructionPasteOps.buildSequence(buffer, Rotation.None);
                    for (var block : sequence.pendingBlocks()) {
                        if (block.blockId() == BlockType.EMPTY_ID || block.filler() != 0) continue;
                        var type = BlockType.getAssetMap().getAsset(block.blockId());
                        if (type != null) ids.add(type.getId());
                    }
                    for (var entity : sequence.prefabEntitiesInOrder()) {
                        var item = entity.getComponent(ItemComponent.getComponentType());
                        if (item != null && item.getItemStack() != null) ids.add(item.getItemStack().getItemId());
                        var shown = entity.getComponent(BlockEntity.getComponentType());
                        if (shown != null && shown.getBlockTypeKey() != null) ids.add(shown.getBlockTypeKey());
                    }
                }
            } catch (RuntimeException unreadable) {
                // A prop whose design cannot be read is still craftable as plain woodwork.
            }
            return Map.copyOf(WorkshopParts.weights(ids));
        });
    }
    public static Map<WorkshopParts.Kind, Integer> cost(PropDefinition prop) {
        return WorkshopParts.cost(prop.getGoldPrice(), weights(prop));
    }
    public static double workSeconds(AetherhavenPlugin plugin, String propId) {
        var prop = plugin.getPropCatalog().get(propId);
        return WorkshopParts.workSeconds(prop == null ? PropDefinition.DEFAULT_GOLD_PRICE : prop.getGoldPrice());
    }

    /**
     * Called for every town villager each tick. Only the resident Crafter on his work shift builds
     * commissions, and only while his part of the world is loaded. He keeps his normal routine meanwhile.
     */
    public static void tick(float dt, Ref<EntityStore> ref, Store<EntityStore> store, NPCEntity npc,
            TownVillagerBinding binding, TownRecord town, TownManager manager, AetherhavenPlugin plugin) {
        if (town == null || !KIND.equals(binding.getKind())) return;
        var book = town.peekPatternBook();
        if (book == null || book.queue().isEmpty() || !Float.isFinite(dt) || dt <= 0
            || store.getComponent(ref, DeathComponent.getComponentType()) != null
            || !ProductionLiveWorkDetection.isOnWorkSchedule(store, ref, npc, plugin.getVillagerDefinitionCatalog(), plugin.getVillagerScheduleRegistry()))
            return;
        int finished = book.work(Math.min(dt, 1), prop -> workSeconds(plugin, prop));
        long now = System.currentTimeMillis();
        long[] saved = BENCH.computeIfAbsent(ref, ignored -> new long[1]);
        // Progress is saved now and then; a finished prop is saved at once so it is never lost.
        if (finished > 0 || now - saved[0] > 10_000) { saved[0] = now; manager.updateTown(town); }
    }
}
