package com.hexvane.aetherhaven.command;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.economy.TownEconomyPresence;
import com.hexvane.aetherhaven.economy.TownPayroll;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.*;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Synthetic player references in two disposable native stores. No real player, town or treasury edits. */
final class AetherhavenPayrollTestCommand extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    AetherhavenPayrollTestCommand() {
        super("payroll", "Check wage and worker presence gates in isolated worlds");
        setPermissionGroups("hytale:WorldEditor");
    }
    private static CompletableFuture<World> makeWorld(String name) {
        var universe = Universe.get();
        var path = universe.validateWorldPath(name);
        if (Files.exists(path)) return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));
        var config = new WorldConfig();
        com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new VoidWorldGenProvider());
        config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false); config.setCanSaveChunks(false); config.setSaveNewChunks(false);
        config.setSpawningNPC(false); config.setGameTimePaused(true); config.setBlockTicking(false);
        config.setDeleteOnRemove(true); config.setDeleteOnUniverseStart(true);
        return universe.makeWorld(name, path, config);
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if (!RUNNING.compareAndSet(false,true)) return CompletableFuture.failedFuture(new IllegalStateException("A payroll check is already running"));
        String base = "aetherhaven_payroll_check_" + UUID.randomUUID().toString().replace("-", "");
        var created = new ArrayList<World>();
        return makeWorld(base + "_home").thenCompose(home -> {
            created.add(home);
            return makeWorld(base + "_away").thenCompose(away -> {
                created.add(away);
                return CompletableFuture.supplyAsync(() -> {
                    var store = away.getEntityStore().getStore();
                    var holder = EntityStore.REGISTRY.newHolder();
                    holder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(UUID.randomUUID()));
                    return store.addEntity(holder,AddReason.SPAWN);
                },away).thenCompose(otherRef -> CompletableFuture.runAsync(() -> exercise(home,otherRef),home)
                    .whenCompleteAsync((unused,error) -> {
                        if (otherRef.isValid()) away.getEntityStore().getStore().removeEntity(otherRef,RemoveReason.REMOVE);
                    },away));
            });
        }).whenCompleteAsync((unused,error) -> {
            // Synthetic references are never registered with a world or the live player roster.
            // Remove off both world threads so native shutdown finishes before its save directory is deleted.
            for (var world : created) Universe.get().removeWorld(world.getName());
            RUNNING.set(false);
            String result = error == null
                ? "Payroll isolated check: PASSED. World attachment, departure, other-world references, membership, one clock per town, offline pause, saved overdue debt, payment and retained priorities."
                : "Payroll isolated check: FAILED: " + error;
            ctx.sendMessage(Message.raw(result));
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
        });
    }
    private static PlayerRef player(UUID id,Ref<EntityStore> ref) {
        // No network session is created. This reference exists only inside one world-thread action.
        var player = new PlayerRef(null,id,"Payroll fixture","en-US",null,null);
        if (ref != null) player.addedToStore(ref);
        return player;
    }
    private static void exercise(World home, Ref<EntityStore> awayRef) {
        var store = home.getEntityStore().getStore();
        var holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(UUIDComponent.getComponentType(),new UUIDComponent(UUID.randomUUID()));
        var localRef = store.addEntity(holder,AddReason.SPAWN);
        var tracked = new ArrayList<PlayerRef>();
        try {
            var owner = UUID.randomUUID(); var member = UUID.randomUUID();
            var town = new TownRecord(); town.setOwner(owner,"Owner");
            town.getMemberRolesRaw().put(member.toString(),"BOTH");
            var progress = new TownsfolkProgress(); progress.setEmployed(true);
            progress.setPriority(TownsfolkWorkType.SMELTING,1);
            var payroll = town.getPayroll(); payroll.ensureCurrentBill("worker");
            require(!TownEconomyPresence.hasMemberInStore(store,town,tracked),"Empty world is active");
            for (var candidate : List.of(player(UUID.randomUUID(),localRef),player(owner,null),player(member,awayRef))) {
                tracked.add(candidate);
            }
            require(!TownEconomyPresence.hasMemberInStore(store,town,tracked),"Outsider, detached or other-world reference counts as a member here");
            payroll.advance(TownPayroll.WEEK_MINUTES,TownEconomyPresence.hasMemberInStore(store,town,tracked),Set.of("worker"));
            require(payroll.onlineMinutes()==0,"Absent member advanced wages");
            for (var candidate : List.of(player(owner,localRef),player(member,localRef))) {
                tracked.add(candidate);
            }
            require(TownEconomyPresence.hasMemberInStore(store,town,tracked),"Attached members are absent");
            payroll.advance(1440,TownEconomyPresence.hasMemberInStore(store,town,tracked),Set.of("worker"));
            require(payroll.onlineMinutes()==1440,"Multiple members multiplied wages");
            store.removeEntity(localRef,RemoveReason.REMOVE);
            require(!TownEconomyPresence.hasMemberInStore(store,town,tracked),"Stale tracked reference counts after departure");
            payroll.advance(1440,TownEconomyPresence.hasMemberInStore(store,town,tracked),Set.of("worker"));
            require(payroll.onlineMinutes()==1440,"Departed members advanced wages");
            payroll.advance(TownPayroll.WEEK_MINUTES-1440,true,Set.of("worker"));
            town = new Gson().fromJson(new Gson().toJson(town),TownRecord.class); payroll=town.getPayroll();
            require(payroll.overdue("worker"),"Overdue debt did not survive saving");
            require(!progress.canPerform(TownsfolkWorkType.SMELTING,false,payroll.overdue("worker"),true),"Overdue worker is eligible");
            require(payroll.settle(payroll.bills().getFirst().id(),TownPayroll.WEEKLY_WAGE)==TownPayroll.WEEKLY_WAGE,"Payment failed");
            require(progress.canPerform(TownsfolkWorkType.SMELTING,false,payroll.overdue("worker"),true),"Payment did not restore eligibility");
            require(progress.priority(TownsfolkWorkType.SMELTING)==1,"Saved priority was erased");
        } finally {
            tracked.clear();
            if (localRef.isValid()) store.removeEntity(localRef,RemoveReason.REMOVE);
        }
    }
    private static void require(boolean value,String issue) { if (!value) throw new IllegalStateException(issue); }
}
