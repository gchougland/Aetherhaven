package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.halloween.HalloweenResident;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import org.joml.Vector3i;

/** Successful native coffin opening retains its ordinary animation, contents and key prompt. */
public final class HalloweenCoffinUseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {
    private static final Set<String> COFFINS = Set.of("Furniture_Ancient_Coffin", "Furniture_Human_Ruins_Coffin",
        "Furniture_Temple_Dark_Coffin", "Furniture_Village_Coffin");
    private final AetherhavenPlugin plugin;
    private final Map<World,HalloweenCoffinOpeningQueue> pending = Collections.synchronizedMap(new WeakHashMap<>());
    public HalloweenCoffinUseSystem(AetherhavenPlugin plugin) { super(UseBlockEvent.Post.class); this.plugin = plugin; }
    public static boolean isClosedCoffin(String id) { return COFFINS.contains(id); }
    public static boolean isOpenedCoffin(BlockType type) {
        if (type == null) return false;
        for (String id : COFFINS) {
            var closed = BlockType.getAssetMap().getAsset(id);
            if (closed != null && type.getId().equals(closed.getBlockKeyForState("Open"))) return true;
        }
        return false;
    }
    @Override public Query<EntityStore> getQuery() { return Query.and(Player.getComponentType(), PlayerRef.getComponentType()); }
    @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                CommandBuffer<EntityStore> buffer, UseBlockEvent.Post event) {
        if (event.getInteractionType() != InteractionType.Use || !isClosedCoffin(event.getBlockType().getId())
            || !HalloweenResident.rescueSeason()) return;
        var world = store.getExternalData().getWorld();
        var player = chunk.getComponent(index, PlayerRef.getComponentType());
        var at = event.getTargetBlock();
        var position = new Position(at.x,at.y,at.z);
        String closed = event.getBlockType().getId();
        String opened = event.getBlockType().getBlockKeyForState("Open");
        UUID owner = player.getUuid();
        world.execute(() -> {
            if (HalloweenResident.rescueSeason()) pending.computeIfAbsent(world,w -> new HalloweenCoffinOpeningQueue())
                .offer(owner,position,closed,opened,System.nanoTime());
        });
    }

    public TickingSystem<EntityStore> openings() {
        return new TickingSystem<>() {
            @Override public void tick(float dt,int index,Store<EntityStore> store) {
                var world=store.getExternalData().getWorld();var queue=pending.get(world);
                if(queue==null||queue.isEmpty())return;
                world.execute(() -> {
                    if(!world.isAlive()||store.isShutdown())return;
                    queue.poll(System.nanoTime(),HalloweenResident.rescueSeason(),at -> {
                        if(ChunkSectionBlockUtil.worldChunkIfTicking(world,at.x(),at.z())==null)return null;
                        var type=ChunkSectionBlockUtil.blockType(world,at.x(),at.y(),at.z());return type==null?null:type.getId();
                    },opening -> {
                        var player=store.getExternalData().getRefFromUUID(opening.player());
                        if(player==null||!player.isValid()||store.getComponent(player,PlayerRef.getComponentType())==null)return;
                        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
                        var town=RescueVillagerSpawnService.resolveTownForFieldRescue(opening.player(),manager,RescueVillagerTriggers.HALLOWEEN);
                        var at=opening.position();
                        if(town!=null)RescueVillagerSpawnService.trySpawnBesideOpenedCoffin(world,store,town,new Vector3i(at.x(),at.y(),at.z()),opening.player());
                    });
                });
            }
        };
    }
}
