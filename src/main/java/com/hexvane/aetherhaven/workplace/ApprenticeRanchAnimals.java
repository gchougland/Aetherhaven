package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import org.joml.Vector3d;

/** Disposable, animated workplace livestock. These are not capturable NPCs or sources of item drops. */
public final class ApprenticeRanchAnimals {
    private ApprenticeRanchAnimals() {}
    static final List<String> MODELS = List.of("Cow", "Chicken");
    static final int RADIUS = 3;
    private record Key(UUID site, int slot) {}
    private record Desired(WorkplaceArea area, Point trough) {}

    public static final class Marker implements Component<EntityStore> {
        private static ComponentType<EntityStore, Marker> type;
        private Key key;
        private Desired home;
        private Vector3d position;
        private long eatingUntil;
        public Marker() {}
        private Marker(Key key, Desired home, Vector3d position) {
            this.key = key; this.home = home; this.position = new Vector3d(position);
        }
        @Override public Marker clone() {
            var copy = key == null ? new Marker() : new Marker(key, home, position);
            copy.eatingUntil = eatingUntil;
            return copy;
        }
    }

    public static void register(ComponentRegistryProxy<EntityStore> registry, AetherhavenPlugin plugin) {
        Marker.type = registry.registerComponent(Marker.class, Marker::new);
        registry.registerSystem(new Tick(plugin));
    }

    private static final class Tick extends TickingSystem<EntityStore> {
        private final AetherhavenPlugin plugin;
        private final Map<World, Long> next = Collections.synchronizedMap(new WeakHashMap<>());
        Tick(AetherhavenPlugin plugin) { this.plugin = plugin; }
        @Override public void tick(float dt, int index, Store<EntityStore> store) {
            var world = store.getExternalData().getWorld();
            long now = System.currentTimeMillis();
            if (now < next.getOrDefault(world, 0L)) return;
            next.put(world, now + 2000);
            world.execute(() -> {
                if (world.isAlive()) sync(world, AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).allTowns());
            });
        }
    }

    /** Runs outside entity iteration. Deleted, moved and unloaded workplaces lose their disposable display animals. */
    public static void sync(World world, Collection<TownRecord> towns) {
        if (!world.isInThread()) throw new IllegalStateException("Ranch animal updates require the world thread");
        var store = world.getEntityStore().getStore();
        var desired = new LinkedHashMap<Key, Desired>();
        for (var town : towns) for (var site : town.getApprenticeWorkplaces().sites()) {
            if (site.type() != TownsfolkWorkType.RANCHING) continue;
            // Retire only our old tracked offspring. Player supplied or wild breeding animals remain untouched.
            var oldId = site.food().ranch().offspring();
            var old = oldId == null ? null : store.getExternalData().getRefFromUUID(oldId);
            if (old != null && old.isValid()) {
                var npc = store.getComponent(old, com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
                if (npc != null && RanchWorkService.managedRole(npc.getRoleName())) store.removeEntity(old, RemoveReason.REMOVE);
            }
            var habitat = FoodSiteService.inspect(world, site);
            if (!habitat.ready()) continue;
            var home = new Desired(site.area(), habitat.targets().getFirst());
            for (int slot = 0; slot < MODELS.size(); slot++) desired.put(new Key(site.id(), slot), home);
        }
        var retained = new HashSet<Key>();
        var remove = new ArrayList<Ref<EntityStore>>();
        var reset = new ArrayList<Ref<EntityStore>>();
        var stopEating = new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(Marker.type, (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var marker = chunk.getComponent(i, Marker.type);
                var transform = chunk.getComponent(i, TransformComponent.getComponentType());
                var bounds = chunk.getComponent(i, BoundingBox.getComponentType());
                if (marker.key == null || !marker.home.equals(desired.get(marker.key)) || transform == null
                        || bounds == null || !clear(world, marker.position, bounds.getBoundingBox())
                        || !retained.add(marker.key)) remove.add(chunk.getReferenceTo(i));
                else if (transform.getPosition().distanceSquared(marker.position) > .01) reset.add(chunk.getReferenceTo(i));
                if (marker.eatingUntil > 0 && System.currentTimeMillis() >= marker.eatingUntil) stopEating.add(chunk.getReferenceTo(i));
            }
        });
        for (var ref : remove) if (ref.isValid()) store.removeEntity(ref, RemoveReason.REMOVE);
        for (var ref : stopEating) if (ref.isValid()) {
            AnimationUtils.stopAnimation(ref, AnimationSlot.Action, true, store);
            store.getComponent(ref, Marker.type).eatingUntil = 0;
        }
        for (var ref : reset) if (ref.isValid()) {
            var transform = store.getComponent(ref, TransformComponent.getComponentType());
            transform.setPosition(new Vector3d(store.getComponent(ref, Marker.type).position));
            store.putComponent(ref, TransformComponent.getComponentType(), transform);
        }
        for (var entry : desired.entrySet()) if (!retained.contains(entry.getKey())) spawn(world, entry.getKey(), entry.getValue());
    }

    /** Full body must remain within the workplace, close to the trough and above a supported floor. */
    static boolean inside(WorkplaceArea area, Point trough, Vector3d position, Box box) {
        return Math.abs(position.x - (trough.x() + .5)) <= RADIUS && Math.abs(position.z - (trough.z() + .5)) <= RADIUS
            && position.x + box.min.x >= area.minX() && position.x + box.max.x <= area.maxX()
            && position.z + box.min.z >= area.minZ() && position.z + box.max.z <= area.maxZ()
            && position.y + box.min.y >= area.floorY() && position.y + box.max.y <= area.floorY() + area.height();
    }

    private static boolean clear(World world, Vector3d position, Box box) {
        for (int x = (int)Math.floor(position.x + box.min.x); x < Math.ceil(position.x + box.max.x); x++)
            for (int z = (int)Math.floor(position.z + box.min.z); z < Math.ceil(position.z + box.max.z); z++)
                if (!VillagerBlockUtil.isNpcStandColumn(world, x, (int)Math.floor(position.y), z)) return false;
        return true;
    }

    private static void spawn(World world, Key key, Desired home) {
        var asset = ModelAsset.getAssetMap().getAsset(MODELS.get(key.slot()));
        if (asset == null) return;
        var model = Model.createScaledModel(asset, 1);
        var box = model.getBoundingBox();
        if (box == null) return;
        var store = world.getEntityStore().getStore();
        var occupied = new ArrayList<Box>();
        store.forEachChunk(Query.and(TransformComponent.getComponentType(), BoundingBox.getComponentType()), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var position = chunk.getComponent(i, TransformComponent.getComponentType()).getPosition();
                if (position.distanceSquared(home.trough().x()+.5, home.trough().y(), home.trough().z()+.5) < 100)
                    occupied.add(new Box(chunk.getComponent(i, BoundingBox.getComponentType()).getBoundingBox()).offset(position));
            }
        });
        for (int radius = 1; radius <= RADIUS; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            int x = home.trough().x() + dx, z = home.trough().z() + dz;
            var position = new Vector3d(x + .5, home.trough().y(), z + .5);
            if (!inside(home.area(), home.trough(), position, box) || !clear(world, position, box)) continue;
            position.y = VillagerBlockUtil.resolveFeetYForStandCell(world, x, home.trough().y(), z);
            if (!inside(home.area(), home.trough(), position, box)) continue;
            var body = new Box(box).offset(position);
            if (occupied.stream().anyMatch(body::isIntersecting)) continue;
            var rotation = new Rotation3f(0, (float)Math.atan2(-dx, -dz), 0);
            var holder = store.getRegistry().newHolder();
            holder.addComponent(Marker.type, new Marker(key, home, position));
            holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.ensureComponent(UUIDComponent.getComponentType());
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
            holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(new Box(box)));
            var animation = new ActiveAnimationComponent();
            animation.setPlayingAnimation(AnimationSlot.Movement, "Idle");
            holder.addComponent(ActiveAnimationComponent.getComponentType(), animation);
            holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
            holder.addComponent(Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            var ref = store.addEntity(holder, AddReason.SPAWN);
            if (ref != null && ref.isValid()) AnimationUtils.playAnimation(ref, AnimationSlot.Movement, "Idle", true, store);
            return;
        }
    }

    public static boolean feed(World world, ApprenticeWorkplace site) {
        if (Marker.type == null) return false;
        var habitat = FoodSiteService.inspect(world, site);
        if (!habitat.ready()) return false;
        var expectedHome = new Desired(site.area(), habitat.targets().getFirst());
        var store = world.getEntityStore().getStore();
        var animals = new ArrayList<Ref<EntityStore>>();
        var slots = new HashSet<Integer>();
        store.forEachChunk(Marker.type, (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var marker = chunk.getComponent(i, Marker.type);
                var position = chunk.getComponent(i, TransformComponent.getComponentType());
                var bounds = chunk.getComponent(i, BoundingBox.getComponentType());
                if (marker.key != null && marker.key.site().equals(site.id()) && expectedHome.equals(marker.home)
                        && position != null && bounds != null && marker.key.slot() >= 0 && marker.key.slot() < MODELS.size()
                        && inside(site.area(), expectedHome.trough(), position.getPosition(), bounds.getBoundingBox())
                        && clear(world, position.getPosition(), bounds.getBoundingBox()) && slots.add(marker.key.slot()))
                    animals.add(chunk.getReferenceTo(i));
            }
        });
        // The animals are for show. A pen too cramped for them still feeds the town; the worker tends the trough alone.
        for (var animal : animals) {
            AnimationUtils.playAnimation(animal, AnimationSlot.Action, "Eat", true, store);
            store.getComponent(animal, Marker.type).eatingUntil = System.currentTimeMillis() + 2000;
        }
        return true;
    }
}
