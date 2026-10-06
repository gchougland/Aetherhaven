package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalService;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in verification using normal world ticks and serialization in an unsaved disposable world. */
final class AetherhavenEntityCleanupTestCommand extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    AetherhavenEntityCleanupTestCommand() {
        super("entitycleanup", "Check stale entity cleanup before native section serialization");
        setPermissionGroups("hytale:WorldEditor");
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if (!RUNNING.compareAndSet(false, true)) return CompletableFuture.failedFuture(new IllegalStateException("Cleanup check already running"));
        String name = "aetherhaven_entity_cleanup_check_" + UUID.randomUUID().toString().replace("-", "");
        var universe = Universe.get(); var path = universe.validateWorldPath(name);
        var config = new WorldConfig();
        com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider());
        config.setChunkStorageProvider(com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false); config.setCanSaveChunks(false); config.setSaveNewChunks(false); config.setSpawningNPC(false);
        config.setGameTimePaused(true); config.setBlockTicking(false); config.setDeleteOnRemove(true); config.setDeleteOnUniverseStart(true);
        return universe.makeWorld(name, path, config).thenCompose(world -> check(world)
            .orTimeout(30, TimeUnit.SECONDS).whenCompleteAsync((ignored, error) -> {
                universe.removeWorld(name);
                String result = error == null ? "Entity cleanup: PASSED. Native single, bulk and queued removal, unload, survivor preservation and section serialization."
                    : "Entity cleanup: FAILED: " + error;
                ctx.sendMessage(Message.raw(result)); com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
            })).whenComplete((ignored, error) -> RUNNING.set(false));
    }
    @SuppressWarnings("unchecked")
    private static CompletableFuture<Void> check(World world) {
        return world.getChunkStore().getChunkSectionReferenceAsync(0, 4, 0, GetChunkFlags.SET_TICKING).thenCompose(sectionRef -> {
            var section = new EntitySection[1];
            var survivor = new java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>>();
            return CompletableFuture.runAsync(() -> {
                var chunks = world.getChunkStore().getStore(); var entities = world.getEntityStore().getStore();
                section[0] = chunks.getComponent(sectionRef, EntitySection.getComponentType());
                if (section[0] == null) {
                    section[0] = new EntitySection(); chunks.putComponent(sectionRef, EntitySection.getComponentType(), section[0]);
                }
                var alive = entity(entities); survivor.set(alive);
                var dead = entity(entities);
                section[0].addEntityReference(alive); section[0].addEntityReference(dead);
                entities.removeEntity(dead, RemoveReason.REMOVE);
                require(section[0].getEntityReferences().contains(dead), "Fixture did not create a stale ref");
            }, world).thenCompose(ignored -> afterTick(world, () -> {
                require(section[0].getEntityReferences().size() == 1 && section[0].getEntityReferences().contains(survivor.get()), "Single removal lost a survivor or retained an invalid ref");
                section[0].cloneSerializable();
                var entities = world.getEntityStore().getStore();
                var a = entity(entities); var b = entity(entities);
                section[0].addEntityReference(a); section[0].addEntityReference(b);
                entities.removeEntities(new Ref[]{a, b}, RemoveReason.UNLOAD);
            })).thenCompose(ignored -> afterTick(world, () -> {
                require(section[0].getEntityReferences().size() == 1 && section[0].getEntityReferences().contains(survivor.get()), "Bulk unload cleanup failed");
                section[0].cloneSerializable();
                var entities = world.getEntityStore().getStore();
                var a = entity(entities); var b = entity(entities);
                section[0].addEntityReference(a); section[0].addEntityReference(b);
                UUID aId = entities.getComponent(a, UUIDComponent.getComponentType()).getUuid();
                UUID bId = entities.getComponent(b, UUIDComponent.getComponentType()).getUuid();
                PendingEntityRemovalService.schedule(world, aId, "cleanup_fixture_single");
                PendingEntityRemovalService.scheduleAll(world, java.util.Arrays.asList(aId, null, bId), "cleanup_fixture_batch");
                require(a.isValid() && b.isValid(), "Queued removal ran before the next tick");
            })).thenCompose(ignored -> afterTick(world, () -> {
                require(section[0].getEntityReferences().size() == 1 && section[0].getEntityReferences().contains(survivor.get()), "Queued removal lost a survivor or retained a target");
                section[0].cloneSerializable();
                world.getEntityStore().getStore().removeEntity(survivor.get(), RemoveReason.REMOVE);
            })).thenCompose(ignored -> afterTick(world, () -> {
                require(section[0].getEntityReferences().isEmpty(), "Final removal cleanup failed");
                section[0].cloneSerializable();
            }));
        });
    }
    private static Ref<EntityStore> entity(Store<EntityStore> store) {
        var holder = EntityStore.REGISTRY.newHolder(); holder.putComponent(UUIDComponent.getComponentType(), new UUIDComponent(UUID.randomUUID()));
        return store.addEntity(holder, AddReason.SPAWN);
    }
    private static CompletableFuture<Void> afterTick(World world, Runnable action) {
        return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS))
            .thenRunAsync(action, world);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
