package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.production.*;
import com.hexvane.aetherhaven.town.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Native asset diagnostics and a separately created temporary world for real growth checks. */
final class AetherhavenTimberTestCommand extends AbstractCommandCollection {
    AetherhavenTimberTestCommand() {
        super("timber", "Inspect native timber growth and test isolated groves");
        setPermissionGroups("hytale:WorldEditor");
        addSubCommand(new Plan());
        addSubCommand(new Assets());
        addSubCommand(new WorldCheck());
        addSubCommand(new CharringCheck());
    }
    private static final class Assets extends AbstractAsyncCommand {
        Assets() { super("assets", "Validate native growth plans at representative coordinates"); setPermissionGroups("hytale:WorldEditor"); }
        @Override protected java.util.concurrent.CompletableFuture<Void> executeAsync(CommandContext ctx) {
            int passed = 0, failed = 0;
            for (var species : NativeTimberGrowth.species()) {
                String wood = species.output();
                for (int offset = 0; offset < 4; offset++) {
                    var root = new TimberHarvestPlan.Position(-33 + offset * 17, 128 + offset, 31 - offset * 17);
                    var result = NativeTimberGrowth.capture(species.output(), root, 0, p ->
                            p.y() >= ChunkUtil.MIN_Y && p.y() <= ChunkUtil.HEIGHT_MINUS_1);
                    if (!result.ready()) { failed++; report(ctx, wood + " at " + root + ": " + result.issue()); }
                    else {
                        String harvestIssue = harvestFixture(result.receipt());
                        if (harvestIssue != null) { failed++; report(ctx, wood + " at " + root + ": " + harvestIssue); }
                        else {
                            passed++;
                            report(ctx, wood + " at " + root + ": " + result.receipt().stages().size()
                                    + " native stages, " + result.receipt().footprint().size() + " exact growing cells. In memory harvest restored its footprint.");
                        }
                    }
                }
            }
            report(ctx, "Timber native asset check: " + passed + " passed, " + failed + " failed. No world or inventory changed.");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
    private static final class WorldCheck extends AbstractAsyncCommand {
        WorldCheck(){super("worldcheck","Test native growth in a separate temporary world");setPermissionGroups("hytale:WorldEditor");}
        @Override protected java.util.concurrent.CompletableFuture<Void> executeAsync(CommandContext ctx) {
            return TimberWorldFixture.run(text->report(ctx,text));
        }
    }
    private static final class CharringCheck extends AbstractAsyncCommand {
        CharringCheck(){super("charringcheck","Test native campfire heat and saved Burnt wood delivery in an isolated world");setPermissionGroups("hytale:WorldEditor");}
        @Override protected java.util.concurrent.CompletableFuture<Void> executeAsync(CommandContext ctx) {
            return TimberWorldFixture.runCharring(text->report(ctx,text));
        }
    }
    /** Uses real prefab geometry and native hitboxes, but a map instead of changing the user's world. */
    private static String harvestFixture(TimberPlantingReceipt receipt) {
        try {
            var mature=NativeTimberHarvest.matureCells(receipt);
            var points=new java.util.LinkedHashSet<>(receipt.footprint());
            points.add(new TimberHarvestPlan.Position(receipt.root().x(),receipt.root().y()-1,receipt.root().z()));
            var original=new java.util.LinkedHashMap<TimberHarvestPlan.Position,TimberHarvestPlan.Observed>();
            var saved=new java.util.ArrayList<TimberProductionState.Original>();
            for(var p:points) {
                String id=p.y()<receipt.root().y()?"Soil_Dirt":"Empty";
                original.put(p,new TimberHarvestPlan.Observed(true,id,0,false));
                saved.add(new TimberProductionState.Original(p,id,0));
            }
            var simulated=new java.util.LinkedHashMap<>(original);
            mature.forEach((p,c)->simulated.put(p,new TimberHarvestPlan.Observed(true,c.blockId(),c.rotation(),c.filler(),false)));
            var inspected=TimberFellingPlan.inspect(new TimberProductionState.Planting(receipt,saved),mature,p->true,simulated::get,
                (c,o)->c.blockId().equals(o.blockId())&&c.rotation()==o.rotation()&&c.filler()==o.filler());
            if(!inspected.ready())return "Harvest fixture failed: "+inspected.issue();
            if(!inspected.plan().restore(simulated::get,(p,b)->{simulated.put(p,b);return true;})||!simulated.equals(original))
                return "Harvest fixture did not restore its original cells.";
            return null;
        } catch(RuntimeException failure) { return "Harvest fixture could not read native geometry: "+failure.getClass().getSimpleName(); }
    }
    private static final class Plan extends AbstractPlayerCommand {
        private final RequiredArg<String> output = withRequiredArg("output", "Timber item ID", ArgTypes.STRING);
        private final RequiredArg<Integer> x = withRequiredArg("x", "Planting X", ArgTypes.INTEGER);
        private final RequiredArg<Integer> y = withRequiredArg("y", "Planting Y", ArgTypes.INTEGER);
        private final RequiredArg<Integer> z = withRequiredArg("z", "Planting Z", ArgTypes.INTEGER);
        Plan() {
            super("plan", "Inspect a native tree's planned growth without planting");
            setPermissionGroups("hytale:WorldEditor");
        }
        @Override protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
            var root = new TimberHarvestPlan.Position(ctx.get(x), ctx.get(y), ctx.get(z));
            var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            var town = manager.findTownContainingBlock(world.getName(), root.x(), root.z());
            if (town == null || !town.hasMemberOrOwner(player.getUuid()) || !town.playerCanManageConstructions(player.getUuid())) {
                report(ctx, "Choose a root inside a town you can manage."); return;
            }
            var result = NativeTimberGrowth.capture(ctx.get(output), root, 0, p -> p.y() >= ChunkUtil.MIN_Y
                    && p.y() <= ChunkUtil.HEIGHT_MINUS_1 && TownTerritoryClaims.contains(town,
                        ChunkUtil.chunkCoordinate(p.x()), ChunkUtil.chunkCoordinate(p.z())));
            if (!result.ready()) { report(ctx, result.issue()); return; }
            var receipt = result.receipt();
            report(ctx, "Tree plan only: " + receipt.sapling() + ", " + receipt.stages().size()
                    + " stages, " + receipt.footprint().size() + " growing cells. No terrain or stock changed.");
            for (var stage : receipt.stages()) {
                long trunks = stage.cells().stream().filter(c -> c.kind() == TimberHarvestPlan.Kind.TIMBER && c.filler() == 0).count();
                long roots = stage.cells().stream().filter(c -> c.position().y() < root.y()).count();
                report(ctx, "Stage " + stage.index() + ": " + stage.source() + ", " + stage.cells().size()
                        + " cells, " + trunks + " timber cells, " + roots + " below the root.");
            }
        }
    }
    private static void report(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text));
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);
    }
}
