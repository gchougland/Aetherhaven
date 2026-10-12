package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.rts.GuardRtsCommandState;
import com.hexvane.aetherhaven.rts.RtsCommandPhase;
import com.hexvane.aetherhaven.rts.RtsFieldCommand;
import com.hexvane.aetherhaven.rts.RtsFieldCommand.Outcome;
import com.hexvane.aetherhaven.rts.RtsFlagOrderCycleInteraction;
import com.hexvane.aetherhaven.rts.RtsFlagStopInteraction;
import com.hexvane.aetherhaven.rts.RtsOrderMode;
import com.hexvane.aetherhaven.rts.RtsToolPrimaryInteraction;
import com.hexvane.aetherhaven.rts.RtsToolSecondaryInteraction;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.ToolAbilityKeys;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.world.PersistentWorldSupport;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * The Command Flag carried in hand, checked in a disposable world with a fixture town and real guard NPCs.
 *
 * <p>Each order goes through {@link RtsFieldCommand}, the code the held item's interactions call, and the guards'
 * orders are read back from the world after it has ticked. The check also asks the running game whether the flag item
 * binds each input to the interaction class registered for it.
 *
 * <p>What it does not show: a key press. No player can join the check, so the commander is a stand-in entity with the
 * town owner's id, and nothing is seen or heard. That the client sends each input, and what the panel, markers and
 * sounds look like, still needs a person holding the flag.
 */
final class AetherhavenFlagTestCommand extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    AetherhavenFlagTestCommand() {
        super("flag", "Check Command Flag orders on real guards in an isolated world");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        Consumer<String> report = text -> {
            ctx.sendMessage(Message.raw(text));
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);
        };
        if (!RUNNING.compareAndSet(false, true)) {
            report.accept("A Command Flag check is already running.");
            return CompletableFuture.completedFuture(null);
        }
        String name = "aetherhaven_flag_check_" + UUID.randomUUID().toString().replace("-", "");
        var universe = Universe.get();
        var path = universe.validateWorldPath(name);
        if (Files.exists(path)) {
            RUNNING.set(false);
            return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));
        }
        var config = new WorldConfig();
        com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new VoidWorldGenProvider());
        config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false);
        config.setCanSaveChunks(false);
        config.setSaveNewChunks(false);
        config.setSpawningNPC(false);
        config.setGameTimePaused(true);
        config.setBlockTicking(false);
        config.setDeleteOnRemove(true);
        config.setDeleteOnUniverseStart(true);
        report.accept("Starting isolated Command Flag check. No town of yours is used.");
        return universe.makeWorld(name, path, config).thenCompose(world -> {
            Fixture fixture = new Fixture(world);
            return fixture.run().orTimeout(120, TimeUnit.SECONDS).whenCompleteAsync((done, error) -> {
                if (error == null) {
                    report.accept("Command Flag isolated world check: PASS. " + fixture.summary());
                } else {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    report.accept("Command Flag isolated world check: FAIL at " + fixture.stage + ": " + cause);
                }
                universe.removeWorld(name);
            });
        }).whenComplete((done, error) -> RUNNING.set(false));
    }

    private static final class Fixture {
        private static final List<String> CLASSES = List.of("knight", "archer", "mage");
        /** Where the guards start, the first place they are sent, and the second. All on the fixture floor. */
        private static final Vector3d MUSTER = new Vector3d(8.5, 128, 8.5);
        private static final Vector3i FIRST = new Vector3i(24, 127, 24);
        private static final Vector3i SECOND = new Vector3i(8, 127, 22);

        private final World world;
        private final AetherhavenPlugin plugin = AetherhavenPlugin.get();
        private final Store<EntityStore> store;
        private final List<String> lines = new ArrayList<>();
        private final Map<String, Ref<EntityStore>> guards = new LinkedHashMap<>();
        private final Map<String, Vector3d> startedAt = new LinkedHashMap<>();
        private final UUID owner = UUID.randomUUID();
        private TownRecord town;
        private TownManager manager;
        private Ref<EntityStore> commander, stranger;
        volatile String stage = "setup";

        Fixture(World world) {
            this.world = world;
            this.store = world.getEntityStore().getStore();
        }

        String summary() {
            return String.join(" ", lines);
        }

        CompletableFuture<Void> run() {
            var sections = new ArrayList<CompletableFuture<?>>();
            for (int y = 3; y <= 4; y++) sections.add(world.getChunkStore().getChunkSectionReferenceAsync(0, y, 0, GetChunkFlags.SET_TICKING));
            return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> onWorld(this::item))
                .thenCompose(ignored -> onWorld(this::build))
                .thenCompose(ignored -> later(300))
                .thenCompose(ignored -> onWorld(this::withoutGuards))
                .thenCompose(ignored -> onWorld(this::hire))
                .thenCompose(ignored -> later(500))
                .thenCompose(ignored -> onWorld(this::send))
                // The orders have to outlive many ticks of the system that frees guards nobody commands.
                .thenCompose(ignored -> later(2500))
                .thenCompose(ignored -> onWorld(this::ordersStand))
                .thenCompose(ignored -> onWorld(this::sendAgainInMoveMode))
                .thenCompose(ignored -> later(400))
                .thenCompose(ignored -> onWorld(this::stop))
                .thenCompose(ignored -> later(400))
                .thenCompose(ignored -> onWorld(this::release))
                .thenCompose(ignored -> await("released guards kept their orders", () -> underOrders() == 0))
                .thenCompose(ignored -> onWorld(this::afterRelease))
                .thenCompose(ignored -> await("an order with no commander at the post and no flag mark was kept", () -> underOrders() == 0))
                .thenCompose(ignored -> onWorld(() -> lines.add("An order with no command post session and no flag mark was dropped, as before.")));
        }

        // ---- The item, as the running game loaded it ----

        private void item() {
            stage = "item";
            require(plugin != null, "Plugin missing");
            Item flag = Item.getAssetMap().getAsset(AetherhavenConstants.RTS_FLAG_ITEM_ID);
            require(flag != null, "The Command Flag item is not loaded");
            Map<InteractionType, Class<? extends Interaction>> expected = new LinkedHashMap<>();
            expected.put(InteractionType.Primary, RtsToolPrimaryInteraction.class);
            expected.put(InteractionType.Secondary, RtsToolSecondaryInteraction.class);
            expected.put(ToolAbilityKeys.FIRST, RtsFlagOrderCycleInteraction.class);
            expected.put(ToolAbilityKeys.SECOND, RtsFlagStopInteraction.class);
            List<String> bound = new ArrayList<>();
            for (var entry : expected.entrySet()) {
                String rootId = flag.getInteractions().get(entry.getKey());
                require(rootId != null, "The flag binds nothing to " + entry.getKey());
                RootInteraction root = RootInteraction.getAssetMap().getAsset(rootId);
                require(root != null, "Missing root interaction " + rootId + " for " + entry.getKey());
                String[] ids = root.getInteractionIds();
                require(ids != null && ids.length == 1, entry.getKey() + " should run one interaction, runs " + (ids == null ? 0 : ids.length));
                Interaction first = Interaction.getAssetMap().getAsset(ids[0]);
                require(entry.getValue().isInstance(first), entry.getKey() + " runs " + (first == null ? "nothing" : first.getClass().getSimpleName())
                    + ", expected " + entry.getValue().getSimpleName());
                bound.add(entry.getKey().name());
            }
            float reach = flag.getInteractionConfig().getUseDistance(GameMode.Adventure);
            require(reach >= 16, "The flag reaches only " + reach + " blocks");
            lines.add("The item binds " + String.join(", ", bound) + " to their handlers and reaches " + (int) reach + " blocks.");
        }

        // ---- Setting the scene ----

        private void build() {
            stage = "setup";
            require(!PersistentWorldSupport.shouldPersistWorldData(world), "The fixture world must never be saved");
            for (int x = 2; x <= 29; x++) for (int z = 2; z <= 29; z++)
                require(ChunkSectionBlockUtil.setBlockByKey(world, x, 127, z, "Rock_Stone", 0), "Floor placement failed");
            town = new TownRecord(UUID.randomUUID(), owner, world.getName(), 16, 128, 16, 1, 1, 0);
            town.setDisplayName("Flag Fixture");
            manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            manager.putTown(town);
            commander = standIn(owner, new Vector3d(16.5, 128, 16.5));
            stranger = standIn(UUID.randomUUID(), new Vector3d(14.5, 128, 16.5));
            require(commander.equals(store.getExternalData().getRefFromUUID(owner)), "The stand-in commander cannot be found by id");
            require(town.equals(RtsFieldCommand.townOf(commander, store)), "The stand-in commander's town was not found");
            require(RtsFieldCommand.townOf(stranger, store) == null, "A stranger was given a town");
        }

        /** A commander with no client: the town owner's id and a place to stand, nothing else. */
        private Ref<EntityStore> standIn(UUID id, Vector3d at) {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(id));
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(at, new Rotation3f(0f, 0f, 0f)));
            Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
            require(ref != null && ref.isValid(), "Could not add the stand-in commander");
            return ref;
        }

        private void hire() {
            stage = "hire";
            int i = 0;
            for (String cls : CLASSES) {
                var profile = plugin.getEquipmentProfileCatalog().byId("guard_" + cls);
                require(profile != null, "Missing guard class " + cls);
                Vector3d at = new Vector3d(MUSTER).add(i++ * 1.5, 0, 0);
                var spawned = NPCPlugin.get().spawnNPC(store, profile.getGuardNpcRole(), null, at, Rotation3f.ZERO);
                require(spawned != null, "Could not spawn a " + cls);
                Ref<EntityStore> ref = spawned.first();
                UUID id = store.getComponent(ref, UUIDComponent.getComponentType()).getUuid();
                town.getHiredGuardRecords().add(new HiredGuardRecord("flag_check_" + cls, id, "guard_" + cls, true));
                store.putComponent(ref, TownVillagerBinding.getComponentType(), new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_GUARD, null, null));
                guards.put(cls, ref);
                startedAt.put(cls, new Vector3d(at));
            }
            require(RtsFieldCommand.guardsOf(town, store).size() == CLASSES.size(), "The town does not see its guards");
        }

        // ---- Every way a press can come to nothing ----

        private void withoutGuards() {
            stage = "no guards";
            expect(RtsFieldCommand.send(stranger, store, store, FIRST), Outcome.NO_TOWN, 0, "send with no town");
            expect(RtsFieldCommand.stop(stranger, store, store), Outcome.NO_TOWN, 0, "stop with no town");
            expect(RtsFieldCommand.ready(stranger, store, store), Outcome.NO_TOWN, 0, "count with no town");
            expect(RtsFieldCommand.release(stranger, store, store), Outcome.NO_TOWN, 0, "release with no town");
            expect(RtsFieldCommand.send(commander, store, store, FIRST), Outcome.NO_GUARDS, 0, "send with no guards");
            expect(RtsFieldCommand.stop(commander, store, store), Outcome.NO_GUARDS, 0, "stop with no guards");
            expect(RtsFieldCommand.ready(commander, store, store), Outcome.NO_GUARDS, 0, "count with no guards");
            expect(RtsFieldCommand.release(commander, store, store), Outcome.NO_GUARDS, 0, "release with no guards");
            lines.add("With no town and with no guards every press answered why (8 of 8).");
        }

        // ---- The orders ----

        private void send() {
            stage = "send";
            int n = CLASSES.size();
            expect(RtsFieldCommand.send(commander, store, store, null), Outcome.NO_GROUND, n, "send at no block");
            require(underOrders() == 0, "A send at no block gave an order");
            expect(RtsFieldCommand.release(commander, store, store), Outcome.NONE_UNDER_ORDERS, n, "release with nobody under orders");
            expect(RtsFieldCommand.ready(commander, store, store), Outcome.READY, n, "count");
            expect(RtsFieldCommand.send(commander, store, store, FIRST), Outcome.SENT, n, "send");
            for (var guard : guards.entrySet()) {
                GuardRtsCommandState order = order(guard.getKey());
                require(order.getPhase() == RtsCommandPhase.TRAVELING, guard.getKey() + " is not on the way");
                require(order.getOrderMode() == RtsOrderMode.ATTACK_MOVE, guard.getKey() + " was not sent in attack move");
                require(order.isFieldOrder(), guard.getKey() + " has no flag mark on its order");
                require(owner.equals(order.getCommanderPlayerUuid()), guard.getKey() + " has the wrong commander");
                double off = Math.hypot(order.getHoldX() - (FIRST.x + .5), order.getHoldZ() - (FIRST.z + .5));
                require(off <= 6 && Math.abs(order.getHoldY() - (FIRST.y + 1)) < .01, guard.getKey() + " was sent " + off + " blocks from the block looked at");
            }
        }

        private void ordersStand() {
            stage = "orders stand";
            require(underOrders() == CLASSES.size(), "After 2.5 seconds only " + underOrders() + " of " + CLASSES.size() + " guards still had their order");
            List<String> walked = new ArrayList<>();
            double least = Double.MAX_VALUE;
            for (var guard : guards.entrySet()) {
                require(order(guard.getKey()).isFieldOrder(), guard.getKey() + " lost its flag mark");
                String state = NpcSupportUtil.stateName(store, guard.getValue());
                require(state.contains(AetherhavenConstants.NPC_STATE_GUARD_RTS_COMMAND) || state.contains("Combat"),
                    guard.getKey() + " is in state " + state + ", not under command");
                Vector3d now = position(guard.getValue());
                Vector3d start = startedAt.get(guard.getKey());
                double before = Math.hypot(start.x - (FIRST.x + .5), start.z - (FIRST.z + .5));
                double after = Math.hypot(now.x - (FIRST.x + .5), now.z - (FIRST.z + .5));
                least = Math.min(least, before - after);
                walked.add(String.format(Locale.ROOT, "%s %.1f", guard.getKey(), before - after));
            }
            lines.add("Sent " + CLASSES.size() + " guards to " + FIRST.x + "," + FIRST.z + ": all " + CLASSES.size()
                + " held a TRAVELING ATTACK_MOVE flag order 2.5 seconds later, in the RtsCommand state, and had closed on it by "
                + String.join(", ", walked) + " blocks.");
            require(least > 1.0, "A guard did not walk toward the order: " + walked);
        }

        private void sendAgainInMoveMode() {
            stage = "order mode";
            expect(RtsFieldCommand.cycleOrderMode(commander, store, store), Outcome.ORDER_MOVE, CLASSES.size(), "order mode");
            require(RtsFieldCommand.orderModeOf(commander, store) == RtsOrderMode.MOVE, "The order mode did not change");
            expect(RtsFieldCommand.send(commander, store, store, SECOND), Outcome.SENT, CLASSES.size(), "send in move mode");
            for (String cls : CLASSES) {
                GuardRtsCommandState order = order(cls);
                require(order.getOrderMode() == RtsOrderMode.MOVE && order.getPhase() == RtsCommandPhase.TRAVELING, cls + " did not take the move order");
                require(Math.hypot(order.getHoldX() - (SECOND.x + .5), order.getHoldZ() - (SECOND.z + .5)) <= 6, cls + " kept the old destination");
            }
            lines.add("Order mode switched to MOVE and a second send retargeted all " + CLASSES.size() + ".");
        }

        private void stop() {
            stage = "stop";
            expect(RtsFieldCommand.stop(commander, store, store), Outcome.STOPPED, CLASSES.size(), "stop");
            for (var guard : guards.entrySet()) {
                GuardRtsCommandState order = order(guard.getKey());
                Vector3d at = position(guard.getValue());
                require(order.getPhase() == RtsCommandPhase.HOLDING, guard.getKey() + " is not holding");
                require(Math.hypot(order.getHoldX() - at.x, order.getHoldZ() - at.z) < .01, guard.getKey() + " was not told to hold where it stands");
                require(order.isFieldOrder(), guard.getKey() + " lost its flag mark on stop");
            }
            lines.add("Stop: all " + CLASSES.size() + " HOLDING where they stood.");
        }

        private void release() {
            stage = "release";
            require(underOrders() == CLASSES.size(), "A stopped guard lost its order before release");
            expect(RtsFieldCommand.release(commander, store, store), Outcome.RELEASED, CLASSES.size(), "release");
        }

        private void afterRelease() {
            stage = "after release";
            for (var guard : guards.entrySet()) {
                String state = NpcSupportUtil.stateName(store, guard.getValue());
                require(!state.contains(AetherhavenConstants.NPC_STATE_GUARD_RTS_COMMAND), guard.getKey() + " is still in state " + state);
            }
            expect(RtsFieldCommand.release(commander, store, store), Outcome.NONE_UNDER_ORDERS, CLASSES.size(), "second release");
            lines.add("Release: all " + CLASSES.size() + " back to their own duties with no order.");
            // What every flag order was before the fix: an order whose commander has no command post session open.
            stage = "unmarked order";
            GuardRtsCommandState unmarked = new GuardRtsCommandState();
            unmarked.setCommanderPlayerUuid(owner);
            unmarked.setHold(FIRST.x + .5, FIRST.y + 1, FIRST.z + .5);
            store.putComponent(guards.get(CLASSES.getFirst()), GuardRtsCommandState.getComponentType(), unmarked);
            require(underOrders() == 1, "The unmarked order was not placed");
        }

        // ---- Reading the world ----

        private GuardRtsCommandState order(String cls) {
            GuardRtsCommandState order = store.getComponent(live(cls), GuardRtsCommandState.getComponentType());
            require(order != null, cls + " has no order");
            return order;
        }

        private Ref<EntityStore> live(String cls) {
            Ref<EntityStore> ref = guards.get(cls);
            require(ref != null && ref.isValid(), "The " + cls + " left the world");
            return ref;
        }

        private int underOrders() {
            int count = 0;
            for (String cls : guards.keySet()) if (store.getComponent(live(cls), GuardRtsCommandState.getComponentType()) != null) count++;
            return count;
        }

        private Vector3d position(Ref<EntityStore> ref) {
            return new Vector3d(store.getComponent(ref, TransformComponent.getComponentType()).getPosition());
        }

        private static void expect(RtsFieldCommand.Result result, Outcome outcome, int guards, String what) {
            require(result.outcome() == outcome, what + ": expected " + outcome + ", got " + result.outcome());
            require(result.guards() == guards, what + ": expected " + guards + " guards, got " + result.guards());
            require(!result.outcome().messageKey().isBlank(), what + " has no words for the player");
        }

        // ---- Plumbing ----

        private CompletableFuture<Void> onWorld(Runnable action) {
            return CompletableFuture.runAsync(action, world);
        }

        private static CompletableFuture<Void> later(long millis) {
            return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS));
        }

        private CompletableFuture<Void> await(String failure, BooleanSupplier ready) {
            return await(failure, ready, 0);
        }

        private CompletableFuture<Void> await(String failure, BooleanSupplier ready, int attempt) {
            return CompletableFuture.supplyAsync(ready::getAsBoolean, world).thenCompose(done -> {
                if (done) return CompletableFuture.completedFuture(null);
                if (attempt >= 50) return CompletableFuture.failedFuture(new IllegalStateException(failure));
                return later(100).thenCompose(ignored -> await(failure, ready, attempt + 1));
            });
        }

        private static void require(boolean okay, String reason) {
            if (!okay) throw new IllegalStateException(reason);
        }
    }
}
