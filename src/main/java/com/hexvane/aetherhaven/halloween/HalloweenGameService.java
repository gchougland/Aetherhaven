package com.hexvane.aetherhaven.halloween;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.production.NativeTimberPlanting;
import com.hexvane.aetherhaven.production.TimberHarvestPlan.Position;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3i;

/** World-thread adapter. Only empty loaded cells are used, and only our own head block is removed. */
public final class HalloweenGameService {
    public static final String BLOCK = "Aetherhaven_Halloween_Hidden_Head";
    private HalloweenGameService() {}
    public static void register(AetherhavenPlugin plugin) {
        var conditions = plugin.getDialogueConditionRegistry();
        conditions.register("halloween_game_resident", (c,p,s,n) -> resolve(p,s,n) != null);
        conditions.register("halloween_game_can_start", (c,p,s,n) -> {
            var access = resolve(p,s,n);
            return access != null && access.town().halloweenGame().active() == null;
        });
        conditions.register("halloween_game_can_return", (c,p,s,n) -> {
            var access = resolve(p,s,n); if (access == null) return false;
            var game = access.town().halloweenGame().active();
            return game != null && game.found() && game.player().equals(access.player().getUuid());
        });
        conditions.register("halloween_game_can_cancel", (c,p,s,n) -> {
            var access = resolve(p,s,n); if (access == null) return false;
            var game = access.town().halloweenGame().active();
            return game != null && (game.player().equals(access.player().getUuid())
                || access.town().playerCanManageConstructions(access.player().getUuid()));
        });
        var actions = plugin.getDialogueActionRegistry();
        actions.register("halloween_game_start", (a,p,s,o,n) -> { say(s,p,start(p,s,n)); o.setGotoNodeId("main_hub"); });
        actions.register("halloween_game_return", (a,p,s,o,n) -> { say(s,p,finish(p,s,n)); o.setGotoNodeId("main_hub"); });
        actions.register("halloween_game_cancel", (a,p,s,o,n) -> { say(s,p,cancel(p,s,n)); o.setGotoNodeId("main_hub"); });
    }
    private record Access(TownManager manager, TownRecord town, PlayerRef player) {}
    private static Access resolve(Ref<EntityStore> player, Store<EntityStore> store, Ref<EntityStore> npc) {
        var plugin = AetherhavenPlugin.get();
        if (plugin == null || player == null || !player.isValid() || npc == null || !npc.isValid()) return null;
        var binding = store.getComponent(npc, TownVillagerBinding.getComponentType());
        var identity = store.getComponent(player, PlayerRef.getComponentType());
        var p = store.getComponent(player, TransformComponent.getComponentType());
        var n = store.getComponent(npc, TransformComponent.getComponentType());
        if (binding == null || !HalloweenResident.KIND.equals(binding.getKind()) || identity == null || p == null || n == null
            || p.getPosition().distanceSquared(n.getPosition()) > 36) return null;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin);
        var town = manager.getTown(binding.getTownId());
        return town != null && town.hasMemberOrOwner(identity.getUuid()) ? new Access(manager, town, identity) : null;
    }
    private static boolean rewarded(TownManager manager, UUID player, long day) {
        return manager.retiredHalloweenGames().rewarded(player, day)
            || manager.allTowns().stream().anyMatch(t -> t.halloweenGame().rewarded(player, day));
    }
    private static String start(Ref<EntityStore> player, Store<EntityStore> store, Ref<EntityStore> npc) {
        var access = resolve(player, store, npc); if (access == null) return "unavailable";
        var world = store.getExternalData().getWorld(); var town = access.town(); var state = town.halloweenGame();
        reconcileRetired(world, access.manager());
        reconcile(world, store, access.manager(), town);
        if (state.active() != null) return state.active().player().equals(access.player().getUuid()) ? "already_playing" : "busy";
        long day = VillagerReputationService.currentGameEpochDay(store);
        if (rewarded(access.manager(), access.player().getUuid(), day)) return "tomorrow";
        if (!state.canStart(access.player().getUuid(), day)) return "cleanup_pending";
        var spot = chooseSpot(world, town, access.manager()); if (spot == null) return "no_spot";
        // Reserve ownership before placement; persist the resulting state after placement succeeds or fails.
        var game = state.start(access.player().getUuid(), day, spot);
        if (game == null) return "busy";
        if (!ChunkSectionBlockUtil.setBlockByKey(world, spot.x(), spot.y(), spot.z(), BLOCK, 0)) {
            state.cancel(); clean(world, state); access.manager().updateTown(town); return "no_spot";
        }
        HalloweenResident.setHeadWorn(npc, store, false);
        access.manager().updateTown(town); return "started";
    }
    private static String finish(Ref<EntityStore> player, Store<EntityStore> store, Ref<EntityStore> npc) {
        var access = resolve(player, store, npc); if (access == null) return "unavailable";
        reconcile(store.getExternalData().getWorld(), store, access.manager(), access.town());
        var state = access.town().halloweenGame(); var game = state.active();
        if (game == null || !game.player().equals(access.player().getUuid()) || !game.found()) return "not_found";
        long day = VillagerReputationService.currentGameEpochDay(store);
        if (rewarded(access.manager(), game.player(), day)) return "tomorrow";
        var inventory = InventoryComponent.getCombined(store, player, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        if (!HalloweenGameInventory.reward(inventory, access.town().getTownId(), game,
            () -> state.reward(game.player(), game.id(), day))) return "return_inventory";
        HalloweenResident.setHeadWorn(npc, store, true);
        access.manager().updateTown(access.town()); return "rewarded";
    }
    private static String cancel(Ref<EntityStore> player, Store<EntityStore> store, Ref<EntityStore> npc) {
        var access = resolve(player, store, npc); if (access == null) return "unavailable";
        var state = access.town().halloweenGame(); var game = state.active();
        if (game == null) return "not_playing";
        if (!game.player().equals(access.player().getUuid()) && !access.town().playerCanManageConstructions(access.player().getUuid())) return "busy";
        state.cancel(); clean(store.getExternalData().getWorld(), state);
        HalloweenResident.setHeadWorn(npc, store, true); access.manager().updateTown(access.town()); return "cancelled";
    }
    public static void pickup(Ref<EntityStore> player, Store<EntityStore> store, Vector3i position) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null || !player.isValid()) return;
        var world = store.getExternalData().getWorld();
        if (!BLOCK.equals(block(world, position.x, position.y, position.z))) return;
        var identity = store.getComponent(player, PlayerRef.getComponentType());
        var transform = store.getComponent(player, TransformComponent.getComponentType());
        if (identity == null || transform == null || transform.getPosition().distanceSquared(position.x + .5, position.y + .5, position.z + .5) > 36) return;
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (var town : manager.allTowns()) {
            var state = town.halloweenGame(); var game = state.active();
            if (game == null || game.found() || !at(game.spot(), position)) continue;
            if (!town.hasMemberOrOwner(identity.getUuid()) || !game.player().equals(identity.getUuid())) { say(store, player, "someone_else"); return; }
            if (!validBuilding(town, game.spot())) { state.cancel(); clean(world, state); manager.updateTown(town); say(store, player, "cancelled"); return; }
            var inventory = InventoryComponent.getCombined(store, player, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            boolean picked = HalloweenGameInventory.pickup(inventory, HalloweenGameInventory.head(town.getTownId(), game), () -> {
                if (!state.matches(identity.getUuid(), game.id()) || state.active().found()
                    || !BLOCK.equals(block(world, position.x, position.y, position.z))) return false;
                if (!ChunkSectionBlockUtil.setBlockEmpty(world, position.x, position.y, position.z, 0)) return false;
                return state.pickup(identity.getUuid(), game.id());
            });
            if (picked) manager.updateTown(town);
            say(store, player, picked ? "found" : "full"); return;
        }
        // A removed town or a cancelled game cannot leave a collectible reward behind.
        ChunkSectionBlockUtil.setBlockEmpty(world, position.x, position.y, position.z, 0);
        say(store, player, "expired");
    }
    private static boolean at(HalloweenGameState.Spot spot, Vector3i p) { return spot.x() == p.x && spot.y() == p.y && spot.z() == p.z; }
    static String buildingPosition(PlotInstance plot) {
        var f = plot.toFootprint();
        return plot.getConstructionId() + ":" + f.getMinX() + ":" + f.getMinY() + ":" + f.getMinZ()
            + ":" + f.getMaxX() + ":" + f.getMaxY() + ":" + f.getMaxZ() + ":" + plot.resolvePrefabYaw();
    }
    static boolean validBuilding(TownRecord town, HalloweenGameState.Spot spot) {
        var plot = town.findPlotById(spot.plot());
        return plot != null && plot.containsWorldBlock(spot.x(), spot.y(), spot.z()) && buildingPosition(plot).equals(spot.buildingPosition());
    }
    static HalloweenGameState.Spot chooseSpot(World world, TownRecord town, TownManager manager) {
        var plots = new ArrayList<>(town.getPlotInstances()); Collections.shuffle(plots);
        var search = new HalloweenHidingSearch(at -> hidingCell(world, at), 8192);
        var reserved = new HashSet<Position>();
        for (var owner : manager.allTowns()) {
            var state = owner.halloweenGame();
            if (state.active() != null) reserve(reserved, state.active().spot());
            state.cleanup().forEach(spot -> reserve(reserved, spot));
        }
        manager.retiredHalloweenGames().cleanup().forEach(spot -> reserve(reserved, spot));
        var random = ThreadLocalRandom.current(); int budget = 4096;
        for (var plot : plots) {
            if (plot.getState() != PlotInstanceState.COMPLETE) continue;
            var f = plot.toFootprint();
            if ((long)f.getMaxX()-f.getMinX() < 2 || (long)f.getMaxZ()-f.getMinZ() < 2 || (long)f.getMaxY()-f.getMinY() < 2) continue;
            int minY = Math.max(1, f.getMinY()+1);
            int maxY = Math.min(com.hypixel.hytale.math.util.ChunkUtil.HEIGHT_MINUS_1, f.getMaxY());
            if (minY >= maxY) continue;
            for (int attempt = 0; attempt < 256 && budget-- > 0 && !search.exhausted(); attempt++) {
                int x = random.nextInt(f.getMinX()+1, f.getMaxX());
                int z = random.nextInt(f.getMinZ()+1, f.getMaxZ());
                int y = random.nextInt(minY, maxY);
                var at = new Position(x,y,z);
                if (reserved.contains(at) || !search.canHide(x,y,z) || !search.hasRoof(x,y,z,f.getMaxY())) continue;
                if (search.reachesOutside(f, at)) return new HalloweenGameState.Spot(plot.getPlotId(),x,y,z,buildingPosition(plot));
            }
            if (budget <= 0 || search.exhausted()) break;
        }
        return null;
    }
    private static void reserve(Set<Position> positions, HalloweenGameState.Spot spot) {
        positions.add(new Position(spot.x(),spot.y(),spot.z()));
    }
    private static HalloweenHidingSearch.Cell hidingCell(World world, Position at) {
        var ground = NativeTimberPlanting.observe(world,at);
        if (!ground.loaded()) return HalloweenHidingSearch.Cell.UNKNOWN;
        boolean empty = ground.empty() && ground.filler() == 0 && !ground.protectedCell();
        var type = ChunkSectionBlockUtil.blockType(world,at.x(),at.y(),at.z());
        boolean floor = type != null && "Cube".equals(String.valueOf(type.getDrawType())) && !ground.empty() && !ground.protectedCell();
        boolean door = false;
        if (!empty && !floor && !ground.protectedCell()) {
            int filler = ground.filler();
            var primary = new Vector3i(at.x()-com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackX(filler),
                at.y()-com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackY(filler),
                at.z()-com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackZ(filler));
            door = com.hexvane.aetherhaven.autonomy.VillagerDoorUtil.isDoorPrimaryBlock(world,primary);
        }
        return new HalloweenHidingSearch.Cell(true,empty,empty || door,floor);
    }
    private static String block(World world,int x,int y,int z) {
        if (ChunkSectionBlockUtil.worldChunkIfTicking(world,x,z) == null) return null;
        var type = ChunkSectionBlockUtil.blockType(world,x,y,z); return type == null ? null : type.getId();
    }
    private static boolean clean(World world, HalloweenGameState state) {
        boolean changed = false;
        for (var spot : state.cleanup()) {
            String id = block(world,spot.x(),spot.y(),spot.z());
            if (id == null) continue;
            if (!BLOCK.equals(id) || ChunkSectionBlockUtil.setBlockEmpty(world,spot.x(),spot.y(),spot.z(),0)) {
                state.cleaned(spot); changed = true;
            }
        }
        return changed;
    }
    public static void reconcileRetired(World world, TownManager manager) {
        if (clean(world, manager.retiredHalloweenGames())) TownSaveCoordinator.requestSave(manager);
    }

    /** Call only after move validation succeeds, before clearing the old building. */
    public static void beforeBuildingMove(World world, Store<EntityStore> store, TownManager manager, TownRecord town, UUID plot) {
        var game = town.halloweenGame().active();
        if (game == null || !game.spot().plot().equals(plot)) return;
        town.halloweenGame().cancel();
        reconcile(world, store, manager, town);
        manager.updateTown(town);
    }
    public static void reconcile(World world, Store<EntityStore> store, TownManager manager, TownRecord town) {
        var state = town.halloweenGame(); boolean changed = false;
        var game = state.active();
        boolean resident = town.getResidentNpcRecords().stream().anyMatch(r -> HalloweenResident.KIND.equals(r.getKind()));
        if (game != null) {
            String actual = block(world,game.spot().x(),game.spot().y(),game.spot().z());
            if (!resident || !town.hasMemberOrOwner(game.player()) || !validBuilding(town,game.spot())
                || !game.found() && actual != null && !BLOCK.equals(actual)) { state.cancel(); changed = true; }
        }
        changed |= clean(world,state);
        for (var saved : town.getResidentNpcRecords()) if (HalloweenResident.KIND.equals(saved.getKind()) && saved.getLastEntityUuid() != null) {
            var npc = store.getExternalData().getRefFromUUID(saved.getLastEntityUuid());
            if (npc != null && npc.isValid()) HalloweenResident.setHeadWorn(npc,store,state.active() == null);
        }
        if (changed) manager.updateTown(town);
    }
    private static void say(Store<EntityStore> store, Ref<EntityStore> player, String key) {
        var identity = store.getComponent(player,PlayerRef.getComponentType());
        if (identity != null) identity.sendMessage(Message.translation("aetherhaven_halloween.game." + key));
    }
}
