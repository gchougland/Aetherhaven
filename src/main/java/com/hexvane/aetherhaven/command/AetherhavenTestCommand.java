package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.patrol.GuardFollowPlayerState;
import com.hexvane.aetherhaven.patrol.GuardFollowPlayerSystem;
import com.hexvane.aetherhaven.rts.RtsGuardDirectory;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Operator tools for repeatable local gameplay checks. Never advances simulation off screen. */
public final class AetherhavenTestCommand extends AbstractCommandCollection {
    public AetherhavenTestCommand() {
        super("test", "aetherhaven_test.commands.root");
        setPermissionGroups("hytale:WorldEditor");
        addSubCommand(new Escort());
        addSubCommand(new Residents());
        addSubCommand(new Workplaces());
        addSubCommand(new Warehouse());
        addSubCommand(new Production());
        addSubCommand(new AetherhavenProductionStorageTestCommand());
        addSubCommand(new Couriers());
        addSubCommand(new AetherhavenLogisticsTestCommand());
        addSubCommand(new Bench());
        addSubCommand(new Inn(false));
        addSubCommand(new Inn(true));
        addSubCommand(new Placement());
        addSubCommand(new Construction());
        addSubCommand(new Shop());
        addSubCommand(new AetherhavenClinicTestCommand());
        addSubCommand(new AetherhavenHuntTestCommand());
        addSubCommand(new AetherhavenMiningTestCommand());
        addSubCommand(new AetherhavenTimberTestCommand());
        addSubCommand(new AetherhavenFieldTestCommand());
        addSubCommand(new AetherhavenHerdTestCommand());
        addSubCommand(new AetherhavenExcavationTestCommand());
        addSubCommand(new AetherhavenBuildingTestCommand());
        addSubCommand(new AetherhavenAttachmentTestCommand());
        addSubCommand(new AetherhavenFounderAssetsTestCommand());
        addSubCommand(new AetherhavenAssetTestCommand());
        addSubCommand(new AetherhavenQuestEconomyTestCommand());
        addSubCommand(new AetherhavenPrefabTestCommand());
        addSubCommand(new AetherhavenPayrollTestCommand());
        addSubCommand(new AetherhavenGuardStatsTestCommand());
        addSubCommand(new AetherhavenBuildingIconsTestCommand());
        addSubCommand(new AetherhavenAlchemyAssetsTestCommand());
        addSubCommand(new AetherhavenHalloweenTestCommand());
        addSubCommand(new AetherhavenEntityCleanupTestCommand());
        addSubCommand(new AetherhavenMasteryAssetsTestCommand());
        addSubCommand(new AetherhavenTownsfolkMasteryTestCommand());
        addSubCommand(new AetherhavenAlchemyTestCommand());
        addSubCommand(new AetherhavenAlchemyUiTestCommand());
        addSubCommand(new AetherhavenSpecialistPreviewCommand());
    }

    private static final class Shop extends AbstractPlayerCommand {
        private final RequiredArg<String> action=withRequiredArg("action","staff, records, stock, status or visit",ArgTypes.STRING);
        Shop(){super("shop","Inspect nearby Commerce staffing and exercise one test visitor purchase");setPermissionGroups("hytale:WorldEditor");}
        @Override protected void execute(CommandContext ctx,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef pr,World world) {
            var plugin=AetherhavenPlugin.get();if(plugin==null)return;
            var position=store.getComponent(ref,TransformComponent.getComponentType());if(position==null)return;
            var manager=com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
            var town=manager.findTownContainingBlock(world.getName(),(int)position.getPosition().x,(int)position.getPosition().z);
            if(town==null||!town.hasMemberOrOwner(pr.getUuid()))return;
            var plot=town.getPlotInstances().stream().filter(p->com.hexvane.aetherhaven.shopspot.ShopStaffing.isShop(p,plugin.getConstructionCatalog()))
                .min(Comparator.comparingDouble(p->position.getPosition().distanceSquared(p.getSignX(),p.getSignY(),p.getSignZ()))).orElse(null);
            if(plot==null){ctx.sendMessage(Message.raw("Build a player shop first."));return;}
            if(position.getPosition().distanceSquared(plot.getSignX(),plot.getSignY(),plot.getSignZ())>64*64){ctx.sendMessage(Message.raw("Move closer to the shop first."));return;}
            if("records".equals(ctx.get(action))) {
                var def=plugin.getConstructionCatalog().get(plot.getConstructionId());
                var anchor=plot.getStoredPrefabWorldAnchor();var local=def==null?null:def.getManagementBlockLocalPos();
                if(anchor==null||local==null)return;
                var offset=com.hexvane.aetherhaven.construction.PrefabLocalOffset.rotate(plot.resolvePrefabYaw(),local[0],local[1],local[2]);
                var point=new org.joml.Vector3i(anchor).add(offset);
                var block=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockEntityRefAt(world,point.x,point.y,point.z);
                if(block==null||!block.isValid()){ctx.sendMessage(Message.raw("The records shelf is not loaded."));return;}
                store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                    .openCustomPage(ref,store,new com.hexvane.aetherhaven.ui.PlotConstructionPage(pr,block,point,true));return;
            }
            if("stock".equals(ctx.get(action))) {
                var registry=com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world,plugin);
                var counter=registry.listForPlot(plot.getPlotId()).stream().filter(com.hexvane.aetherhaven.shopspot.ShopSpotRecord::isPlayerControlled)
                    .filter(r->!r.hasStock()).findFirst().orElse(null);
                String item="Ingredient_Bar_Copper";
                if(counter==null||!plugin.getShopPriceCatalog().hasExplicitPrice(item)){ctx.sendMessage(Message.raw("An empty counter and copper price are required."));return;}
                counter.setItemId(item);counter.setStock(20);counter.setSellerUuid(pr.getUuid());counter.setSellerName(pr.getUsername());
                com.hexvane.aetherhaven.shopspot.ShopSpotJewelrySupport.clearJewelryListing(counter);
                registry.put(counter);com.hexvane.aetherhaven.shopspot.ShopSpotPersistence.save(world,plugin,registry);
                com.hexvane.aetherhaven.shopspot.ShopSpotDisplayService.syncDisplay(world,store,null,plugin,registry,counter,town);
                ctx.sendMessage(Message.raw("Test fixture added 20 copper ingots to one empty counter. Sales still require an attending clerk."));return;
            }
            if("staff".equals(ctx.get(action))) {
                store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager()
                    .openCustomPage(ref,store,new com.hexvane.aetherhaven.ui.ShopStaffingPage(pr,town.getTownId(),plot.getPlotId()));return;
            }
            if("visit".equals(ctx.get(action))) {
                var result=com.hexvane.aetherhaven.shopspot.NpcShopSpotBuyerService.attempt(world,town.getTownId(),plot.getPlotId(),"Test shopper",UUID.randomUUID());
                String text="Shop test visitor: "+result+". A purchase uses real stock and credits the seller's safe.";
                ctx.sendMessage(Message.raw(text));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);return;
            }
            if(!"status".equals(ctx.get(action)))return;
            var registry=com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateShopSpotRegistry(world,plugin);
            String text="Shop test: "+plot.getPlotId()+" clerk="+plot.getShopClerkId()+" attendingSkill="
                +com.hexvane.aetherhaven.autonomy.ShopClerkWorkService.attendingSkill(store,town,plot.getPlotId())
                +" daylight="+com.hexvane.aetherhaven.shopspot.ShopSpotOpenService.isGameDay(store)+" stockedListings="+registry.listPlayerListingsOnPlot(plot.getPlotId()).size()
                +" stock="+registry.listPlayerListingsOnPlot(plot.getPlotId()).stream().mapToInt(com.hexvane.aetherhaven.shopspot.ShopSpotRecord::getStock).sum();
            for(var worker:com.hexvane.aetherhaven.townsfolk.TownsfolkWorkRoster.list(town,manager,store,plugin)) {
                if(!worker.characterId().equals(plot.getShopClerkId()))continue;
                var workerRef=store.getExternalData().getRefFromUUID(worker.entityId());if(workerRef==null||!workerRef.isValid())continue;
                var transform=store.getComponent(workerRef,TransformComponent.getComponentType());
                var state=store.getComponent(workerRef,com.hexvane.aetherhaven.shopspot.ShopClerkState.getComponentType());
                text+=" | "+worker.name()+" pos="+(transform==null?null:transform.getPosition())+" active="+(state!=null&&state.active)+" approach="+(state==null?null:state.approach)
                    +" attempt="+(state==null?0:state.attempt)+" travellingMs="+(state==null?0:System.currentTimeMillis()-state.beganAt)
                    +" role="+NpcSupportUtil.stateName(store,workerRef);
            }
            ctx.sendMessage(Message.raw(text));com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);
        }
    }

    private static final class Construction extends AbstractPlayerCommand {
        private final RequiredArg<String> construction=withRequiredArg("building","Construction ID of a nearby plot",ArgTypes.STRING);
        Construction(){super("construction","Open a nearby plot's normal construction screen");setPermissionGroups("hytale:WorldEditor");}
        @Override protected void execute(CommandContext ctx,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef pr,World world){
            var plugin=AetherhavenPlugin.get();if(plugin==null)return;
            var manager=com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(world,plugin);
            var position=store.getComponent(ref,TransformComponent.getComponentType());
            var player=store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if(position==null||player==null)return;
            var plot=manager.allTowns().stream().filter(t->t.hasMemberOrOwner(pr.getUuid())).flatMap(t->t.getPlotInstances().stream())
                .filter(p->ctx.get(construction).equals(p.getConstructionId())&&position.getPosition().distanceSquared(p.getSignX(),p.getSignY(),p.getSignZ())<64*64)
                .min(Comparator.comparingDouble(p->position.getPosition().distanceSquared(p.getSignX(),p.getSignY(),p.getSignZ()))).orElse(null);
            if(plot==null){ctx.sendMessage(Message.raw("That building is not nearby."));return;}
            var block=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockEntityRefAt(world,plot.getSignX(),plot.getSignY(),plot.getSignZ());
            if(block==null||!block.isValid()){ctx.sendMessage(Message.raw("The construction sign is not loaded."));return;}
            player.getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.ui.PlotConstructionPage(pr,block,
                new org.joml.Vector3i(plot.getSignX(),plot.getSignY(),plot.getSignZ()),false));
        }
    }

    private static final class Placement extends AbstractPlayerCommand {
        private final RequiredArg<String> construction=withRequiredArg("building","Construction ID for a carried plot plan",ArgTypes.STRING);
        Placement(){super("placement","Open normal building placement at your feet");setPermissionGroups("hytale:WorldEditor");}
        @Override protected void execute(CommandContext ctx,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef pr,World world){
            var plugin=AetherhavenPlugin.get();if(plugin==null)return;
            var inventory=com.hypixel.hytale.server.core.inventory.InventoryComponent.getCombined(store,ref,com.hypixel.hytale.server.core.inventory.InventoryComponent.EVERYTHING);
            var definition=plugin.getConstructionCatalog().get(ctx.get(construction));
            if(inventory==null||definition==null||!com.hexvane.aetherhaven.plot.PlotTokenInventory.hasPlotToken(inventory,definition)){
                ctx.sendMessage(Message.raw("Carry that building's plot plan first."));return;
            }
            var position=store.getComponent(ref,TransformComponent.getComponentType());
            var player=store.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if(position==null||player==null)return;
            var p=position.getPosition();
            var anchor=new org.joml.Vector3i((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z));
            var option=com.hexvane.aetherhaven.plot.PlotTokenPlacementOption.forNewPlot(ctx.get(construction));
            var session=com.hexvane.aetherhaven.placement.PlotPlacementSessionFactory.createFromOption(world,anchor,option,plugin,
                com.hexvane.aetherhaven.placement.PlotPlacementNudgeUtil.getPlayerYawRadians(ref,store));
            if(session==null)return;
            com.hexvane.aetherhaven.placement.PlotPlacementOpenHelper.cancelActivePlotPlacement(ref,store,pr);
            com.hexvane.aetherhaven.placement.PlotPlacementSessions.replaceClearingWorldPreview(pr.getUuid(),session,store);
            player.getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.ui.PlotPlacementPage(pr,session));
        }
    }

    private static final class Inn extends AbstractPlayerCommand {
        private final RequiredArg<String> role;
        Inn(boolean named) {
            super(named?"talk":"inn", "Open a nearby loaded villager dialogue for a UI check"); setPermissionGroups("hytale:WorldEditor");
            role=named?withRequiredArg("role","Villager role ID",ArgTypes.STRING):null;
        }
        @Override protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef pr, World world) {
            var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
            var position = store.getComponent(ref, TransformComponent.getComponentType()); if (position == null) return;
            String requested=role==null?com.hexvane.aetherhaven.AetherhavenConstants.INNKEEPER_NPC_ROLE_ID:ctx.get(role);
            java.util.List<Ref<EntityStore>> candidates = new java.util.ArrayList<>();
            store.forEachChunk(com.hypixel.hytale.component.query.Query.and(
                com.hexvane.aetherhaven.villager.TownVillagerBinding.getComponentType(), NPCEntity.getComponentType(), TransformComponent.getComponentType()), (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    var binding = chunk.getComponent(i, com.hexvane.aetherhaven.villager.TownVillagerBinding.getComponentType());
                    var npc=chunk.getComponent(i,NPCEntity.getComponentType());
                    if (requested.equals(npc.getRoleName()) && chunk.getComponent(i,TransformComponent.getComponentType()).getPosition().distanceSquared(position.getPosition())<=64*64) candidates.add(chunk.getReferenceTo(i));
                }
            });
            var target = candidates.stream().min(Comparator.comparingDouble(r -> store.getComponent(r, TransformComponent.getComponentType()).getPosition().distanceSquared(position.getPosition()))).orElse(null);
            if (target == null) { ctx.sendMessage(Message.raw("That villager is not loaded nearby.")); return; }
            var entity = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (entity == null) return;
            var binding=store.getComponent(target,com.hexvane.aetherhaven.villager.TownVillagerBinding.getComponentType());
            var definition = plugin.getVillagerDefinitionCatalog().byNpcRoleId(requested);
            String kind = definition != null ? definition.getDialogueVillagerKind() : binding.getKind();
            var dialogue = plugin.getDialogueResolver().resolve(null, kind, target, ref, store);
            entity.getPageManager().openCustomPage(ref, store, new com.hexvane.aetherhaven.ui.DialoguePage(pr,
                plugin.getDialogueCatalog(), plugin.createDialogueWorldView(world, target), dialogue.treeId(), dialogue.entryNodeId(), target));
            com.hexvane.aetherhaven.npc.NpcFaceVisuals.onDialogueOpened(target, ref, store);
            com.hexvane.aetherhaven.npc.NpcDialogueCleanup.scheduleEnterInteraction(target, ref, store);
        }
    }

    /** Loaded, empty spaces only. Keeps asset review fixtures out of production commands. */
    private static final class Bench extends AbstractPlayerCommand {
        private final RequiredArg<String> item = withRequiredArg("item", "ledger, storage, workbench, furnace, anvil, furniture, cauldron, view, inspect or tasks", ArgTypes.STRING);
        private final RequiredArg<Integer> x = withRequiredArg("x", "Block X", ArgTypes.INTEGER);
        private final RequiredArg<Integer> y = withRequiredArg("y", "Block Y", ArgTypes.INTEGER);
        private final RequiredArg<Integer> z = withRequiredArg("z", "Block Z", ArgTypes.INTEGER);
        private final RequiredArg<Integer> turn = withRequiredArg("turn", "Quarter turns from 0 to 3", ArgTypes.INTEGER);
        Bench() { super("bench", "Place a bench in an empty space for asset review"); setPermissionGroups("hytale:WorldEditor"); }
        @Override protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef pr, World world) {
            int px=ctx.get(x), py=ctx.get(y), pz=ctx.get(z), rotation=ctx.get(turn);
            var position=store.getComponent(ref,TransformComponent.getComponentType());
            if (rotation<0 || rotation>3 || position==null || position.getPosition().distanceSquared(px,py,pz)>32*32) {
                ctx.sendMessage(Message.raw("Choose a nearby space and a turn from 0 to 3.")); return;
            }
            if ("inspect".equals(ctx.get(item)) || "tasks".equals(ctx.get(item))) {
                var clicked=new org.joml.Vector3i(px,py,pz);
                var target=com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess.find(world,clicked);
                var block=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world,px,py,pz);
                ctx.sendMessage(Message.raw("Bench inspection: block="+(block==null?"unloaded":block.getId())
                    +" interactions="+(block==null?"none":block.getInteractions())
                    +" workplace="+(target==null?"none":target.site().name()+" / "+target.site().type())
                    +" allowed="+com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess.allowed(target,pr.getUuid())
                    +" nearby="+com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess.inReach(ref,store,clicked)));
                if (target!=null && "tasks".equals(ctx.get(item)))
                    com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess.open(ref,store,pr,target.pos());
                return;
            }
            if ("view".equals(ctx.get(item))) {
                for (int h=0;h<3;h++) {
                    if (com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockSectionAt(world,px,py+h,pz)==null
                        || com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockId(world,px,py+h,pz)!=0) {
                        ctx.sendMessage(Message.raw("The viewing position must be empty and loaded.")); return;
                    }
                }
                store.putComponent(ref,Teleport.getComponentType(),Teleport.createForPlayer(world,
                    new Vector3d(px+.5,py,pz+.5),new Rotation3f(-.3f,(float)(rotation*Math.PI/2),0)));
                return;
            }
            String id=switch(ctx.get(item)) {
                case "ledger" -> com.hexvane.aetherhaven.warehouse.WarehouseAccess.DESK_ITEM;
                case "storage" -> com.hexvane.aetherhaven.AetherhavenConstants.BLOCK_PRODUCTION_STORAGE;
                case "workbench" -> "Bench_WorkBench";
                case "furniture" -> "Bench_Furniture";
                case "furnace" -> "Bench_Furnace";
                case "anvil" -> "Bench_Weapon";
                case "cauldron" -> "Alchemy_Cauldron_Big";
                default -> null;
            };
            if (id==null) { ctx.sendMessage(Message.raw("Use ledger, storage, workbench, furnace, anvil, furniture, cauldron, view, inspect or tasks.")); return; }
            // Conservative clearance includes all rotated filler cells and upper decorations.
            for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) for (int dy=0;dy<3;dy++) {
                if (com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockSectionAt(world,px+dx,py+dy,pz+dz)==null
                    || com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockId(world,px+dx,py+dy,pz+dz)!=0) {
                    ctx.sendMessage(Message.raw("The bench needs a clear, loaded space. No blocks were changed.")); return;
                }
            }
            boolean placed=com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.setBlockByKey(world,px,py,pz,id,
                com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple.of(
                    com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.VALUES[rotation],
                    com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.None).index(),0);
            ctx.sendMessage(Message.raw(placed ? "Review bench placed at "+px+", "+py+", "+pz+"." : "The bench could not be placed."));
        }
    }

    /** Opens the normal local screen without changing stock or adopting a production layout. */
    private static final class Production extends AbstractPlayerCommand {
        private final RequiredArg<String> building = withRequiredArg("building", "Production building ID", ArgTypes.STRING);
        Production() {
            super("production", "Inspect a nearby production storage screen");
            setPermissionGroups("hytale:WorldEditor");
        }
        @Override protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef pr, World world) {
            var plugin = AetherhavenPlugin.get();
            var transform = store.getComponent(ref, TransformComponent.getComponentType());
            var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (plugin == null || transform == null || player == null) return;
            var manager = com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            var pos = transform.getPosition();
            var town = manager.findTownContainingBlock(world.getName(), (int)Math.floor(pos.x), (int)Math.floor(pos.z));
            if (town == null || !town.hasMemberOrOwner(pr.getUuid()) || !town.playerCanManageConstructions(pr.getUuid())) return;
            String id = ctx.get(building);
            String gameplay = plugin.getConstructionCatalog().resolveGameplayConstructionId(id);
            if (!com.hexvane.aetherhaven.production.ProductionCatalog.isProductionWorkplaceConstruction(gameplay)) {
                ctx.sendMessage(com.hypixel.hytale.server.core.Message.raw("Choose a production building.")); return;
            }
            var plot = town.getPlotInstances().stream()
                .filter(p -> p.getState() == com.hexvane.aetherhaven.town.PlotInstanceState.COMPLETE && id.equals(p.getConstructionId()))
                .min(Comparator.comparingDouble(p -> pos.distanceSquared(p.getSignX(), p.getSignY(), p.getSignZ()))).orElse(null);
            if (plot == null || pos.distanceSquared(plot.getSignX(), plot.getSignY(), plot.getSignZ()) > 32 * 32) {
                ctx.sendMessage(com.hypixel.hytale.server.core.Message.raw("Move closer to that building.")); return;
            }
            var at = com.hexvane.aetherhaven.warehouse.ProductionStorageLocator.find(world, plot, System.currentTimeMillis());
            if (at == null || pos.distanceSquared(at.x(), at.y(), at.z()) > 16 * 16) {
                ctx.sendMessage(com.hypixel.hytale.server.core.Message.raw("Move closer to its loaded storage.")); return;
            }
            player.getPageManager().openCustomPage(ref, store, new com.hexvane.aetherhaven.ui.ProductionStoragePage(
                pr, town.getTownId(), plot.getPlotId(), at.x(), at.y(), at.z()));
        }
    }

    private static final class Warehouse extends AbstractPlayerCommand {
        private final DebugTownTargetArgs townTarget;
        Warehouse() {
            super("warehouse", "Open the town storage test screen");
            setPermissionGroups("hytale:WorldEditor");
            townTarget = DebugTownTargetArgs.registerOn(this);
        }
        @Override protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef pr, World world) {
            var plugin = AetherhavenPlugin.get();
            if (plugin == null) return;
            var resolved = townTarget.resolve(ctx, world, store, ref, pr, true);
            if (!resolved.isOk()) { ctx.sendMessage(resolved.error()); return; }
            var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player != null) player.getPageManager().openCustomPage(ref, store,
                new com.hexvane.aetherhaven.warehouse.WarehousePage(pr, resolved.townOrThrow().getTownId()));
        }
    }

    private static final class Couriers extends AbstractPlayerCommand {
        private final DebugTownTargetArgs townTarget;
        Couriers() {
            super("couriers", "Open the journal work priorities for Logistics testing");
            setPermissionGroups("hytale:WorldEditor");
            townTarget = DebugTownTargetArgs.registerOn(this);
        }
        @Override protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef pr, World world) {
            if (AetherhavenPlugin.get() == null) return;
            var resolved = townTarget.resolve(ctx, world, store, ref, pr, true);
            if (!resolved.isOk()) { ctx.sendMessage(resolved.error()); return; }
            var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player != null) com.hexvane.aetherhaven.ui.QuestJournalPage.openWork(ref,store,pr,resolved.townOrThrow().getTownId());
        }
    }

    private static final class Workplaces extends AbstractPlayerCommand {
        private final RequiredArg<String> action = withRequiredArg("action", "aetherhaven_workplace.test.action", ArgTypes.STRING);
        private final DebugTownTargetArgs townTarget;
        Workplaces() { super("workplaces", "aetherhaven_workplace.test"); setPermissionGroups("hytale:WorldEditor"); townTarget = DebugTownTargetArgs.registerOn(this); }
        @Override protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef pr, World world) {
            var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
            var resolved = townTarget.resolve(ctx,world,store,ref,pr,true);
            if (!resolved.isOk()) { ctx.sendMessage(resolved.error()); return; }
            var town = resolved.townOrThrow();
            var player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            String mode = ctx.get(action);
            if ("upgrades".equals(mode)||mode.startsWith("upgrades:")) {
                String kind=mode.contains(":")?mode.substring(mode.indexOf(':')+1):"blacksmith";
                if(!com.hexvane.aetherhaven.workplace.WorkplaceUpgradesPage.supports(kind)) {ctx.sendMessage(Message.raw("No upgrade tree for that villager."));return;}
                player.getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.WorkplaceUpgradesPage(pr,town.getTownId(),kind));
            }
            else if ("charter".equals(mode)) {
                player.getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.ui.CharterAmendmentsPage(pr));
            } else if ("create".equals(mode)) {
                var pos = store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
                var area = com.hexvane.aetherhaven.workplace.WorkplaceArea.corners((int)Math.floor(pos.x), (int)Math.floor(pos.y)-1, (int)Math.floor(pos.z), (int)Math.floor(pos.x)+4, (int)Math.floor(pos.z)+4);
                player.getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.WorkplacePage(pr,town.getTownId(),null,area));
            } else if ("orders".equals(mode)) {
                var position = store.getComponent(ref,TransformComponent.getComponentType()).getPosition();
                var site = town.getApprenticeWorkplaces().sites().stream().min(Comparator.comparingDouble(s ->
                    position.distanceSquared((s.area().minX()+s.area().maxX())*.5,s.area().floorY(),(s.area().minZ()+s.area().maxZ())*.5))).orElse(null);
                if (site == null) { ctx.sendMessage(Message.raw("Create a workplace first.")); return; }
                player.getPageManager().openCustomPage(ref,store,new com.hexvane.aetherhaven.workplace.WorkplaceOrdersPage(pr,town.getTownId(),site.id()));
            } else if ("recipes".equals(mode)) {
                for (var recipe : plugin.getApprenticeRecipes().all()) com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo()
                    .log("Workplace recipe: %s source=%s available=%s",recipe.id(),recipe.sourceRecipe(),recipe.source()!=null);
            } else if ("status".equals(mode)) {
                String details = town.getApprenticeWorkplaces().sites().stream().map(s -> s.name()+" "+s.type()+" "+s.area()).collect(java.util.stream.Collectors.joining(" | "));
                ctx.sendMessage(Message.raw(details.isEmpty() ? "No Apprentice workplaces." : details));
                com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Workplace test: %s",details);
            } else ctx.sendMessage(Message.raw("Use upgrades, create, orders, recipes or status."));
        }
    }

    private static final class Residents extends AbstractPlayerCommand {
        private final RequiredArg<String> action = withRequiredArg("action", "aetherhaven_test.commands.residents.action", ArgTypes.STRING);
        private final DebugTownTargetArgs townTarget;

        Residents() {
            super("residents", "aetherhaven_test.commands.residents");
            setPermissionGroups("hytale:WorldEditor");
            townTarget = DebugTownTargetArgs.registerOn(this);
        }

        @Override protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> player, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            var plugin = AetherhavenPlugin.get();
            if (plugin == null) return;
            var resolved = townTarget.resolve(ctx, world, store, player, playerRef, true);
            if (!resolved.isOk()) { ctx.sendMessage(resolved.error()); return; }
            var town = resolved.townOrThrow();
            String order = ctx.get(action).toLowerCase(Locale.ROOT);
            if ("status".equals(order)) {
                String details = com.hexvane.aetherhaven.town.TownPopulation.snapshot(town, plugin.getConstructionCatalog())
                    + " | confirmed deaths=" + com.hexvane.aetherhaven.town.ResidentRegistryService.deadResidents(town).size();
                ctx.sendMessage(Message.raw(details));
                com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Resident test: %s", details);
                for(var guard:town.getHiredGuardRecords()) {
                    var live=guard.getEntityUuid()==null?null:store.getExternalData().getRefFromUUID(guard.getEntityUuid());
                    var npc=live==null||!live.isValid()?null:store.getComponent(live,NPCEntity.getComponentType());
                    var pos=live==null||!live.isValid()?null:store.getComponent(live,TransformComponent.getComponentType());
                    com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Guard test: %s id=%s profile=%s role=%s position=%s",guard.getCharacterId(),guard.getEntityUuid(),guard.getEquipmentProfileId(),npc==null?"unloaded":npc.getRoleName(),pos==null?null:pos.getPosition());
                    if(live!=null&&live.isValid()) {
                        var stats=store.getComponent(live,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
                        var health=stats==null?null:stats.get(com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());
                        var armor=store.getComponent(live,com.hypixel.hytale.server.core.inventory.InventoryComponent.Armor.getComponentType());
                        var pieces=new java.util.ArrayList<String>();
                        if(armor!=null)for(short s=0;s<armor.getInventory().getCapacity();s++){var item=armor.getInventory().getItemStack(s);if(!com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(item))pieces.add(item.getItemId());}
                        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Guard combat: health=%s/%s derived=%s armor=%s",health==null?null:health.get(),health==null?null:health.getMax(),com.hexvane.aetherhaven.guild.GuardAttributeCombat.resolve(live,store),pieces);
                        var hotbar=store.getComponent(live,com.hypixel.hytale.server.core.inventory.InventoryComponent.Hotbar.getComponentType());
                        var utility=store.getComponent(live,com.hypixel.hytale.server.core.inventory.InventoryComponent.Utility.getComponentType());
                        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Guard held equipment: main=%s offhand=%s movement=%s",hotbar==null?null:hotbar.getActiveItem(),utility==null?null:utility.getActiveItem(),npc==null?null:npc.getCurrentHorizontalSpeedMultiplier(live,store));
                        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Guard utility: capacity=%s active=%s first=%s",utility==null?null:utility.getInventory().getCapacity(),utility==null?null:utility.getActiveSlot(),utility==null||utility.getInventory().getCapacity()==0?null:utility.getInventory().getItemStack((short)0));
                    }
                }
                return;
            }
            if ("altar".equals(order)) {
                var position = store.getComponent(player, TransformComponent.getComponentType());
                var entity = store.getComponent(player, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                if (position == null || entity == null) return;
                int px = (int) Math.floor(position.getPosition().x), py = (int) Math.floor(position.getPosition().y), pz = (int) Math.floor(position.getPosition().z);
                for (int x = px - 12; x <= px + 12; x++) for (int z = pz - 12; z <= pz + 12; z++) for (int y = py - 6; y <= py + 6; y++) {
                    var pos = new org.joml.Vector3i(x, y, z);
                    var statue = com.hexvane.aetherhaven.plot.GaiaStatueAppearanceService.componentAt(world, pos);
                    if (statue == null || !town.getTownId().toString().equals(statue.getTownId())) continue;
                    var block = com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
                    entity.getPageManager().openCustomPage(player, store, new com.hexvane.aetherhaven.ui.GaiaStatueRevivePage(playerRef, block, pos));
                    return;
                }
                ctx.sendMessage(Message.raw("Stand near this town's Gaia statue first."));
                return;
            }
            if (!"profile".equals(order) && !"guardprofile".equals(order) && !"dialogue".equals(order)) { ctx.sendMessage(Message.raw("Use status, profile, guardprofile, dialogue, or altar.")); return; }
            var transform = store.getComponent(player, TransformComponent.getComponentType());
            if (transform == null) return;
            java.util.List<Ref<EntityStore>> candidates = new java.util.ArrayList<>();
            store.forEachChunk(com.hypixel.hytale.component.query.Query.and(
                com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding.getComponentType(),
                com.hexvane.aetherhaven.villager.TownVillagerBinding.getComponentType(), TransformComponent.getComponentType()), (chunk, buffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        var binding = chunk.getComponent(i, com.hexvane.aetherhaven.villager.TownVillagerBinding.getComponentType());
                        if (town.getTownId().equals(binding.getTownId())
                            && (!"guardprofile".equals(order) || com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_GUARD.equals(binding.getKind())))
                            candidates.add(chunk.getReferenceTo(i));
                    }
                });
            var target = candidates.stream().min(Comparator.comparingDouble(ref -> store.getComponent(ref,
                TransformComponent.getComponentType()).getPosition().distanceSquared(transform.getPosition()))).orElse(null);
            if (target == null) { ctx.sendMessage(Message.raw("No Townsfolk are loaded in this town.")); return; }
            var character = store.getComponent(target, com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding.getComponentType());
            var uuid = store.getComponent(target, UUIDComponent.getComponentType());
            var entity = store.getComponent(player, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (entity != null && "dialogue".equals(order)) {
                var binding = store.getComponent(target, com.hexvane.aetherhaven.villager.TownVillagerBinding.getComponentType());
                var dialogue = plugin.getDialogueResolver().resolve(null, binding.getKind(), target, player, store);
                entity.getPageManager().openCustomPage(player, store, new com.hexvane.aetherhaven.ui.DialoguePage(playerRef,
                    plugin.getDialogueCatalog(), plugin.createDialogueWorldView(world, target), dialogue.treeId(), dialogue.entryNodeId(), target));
                com.hexvane.aetherhaven.npc.NpcFaceVisuals.onDialogueOpened(target, player, store);
                com.hexvane.aetherhaven.npc.NpcDialogueCleanup.scheduleEnterInteraction(target, player, store);
                return;
            }
            if (entity != null && uuid != null) entity.getPageManager().openCustomPage(player, store,
                new com.hexvane.aetherhaven.ui.TownsfolkProfilePage(playerRef, town.getTownId(), character.getCharacterId(), uuid.getUuid()));
        }
    }

    private static final class Escort extends AbstractPlayerCommand {
        private final RequiredArg<String> action = withRequiredArg("action", "aetherhaven_test.commands.escort.action", ArgTypes.STRING);
        private final DebugTownTargetArgs townTarget;
        private static final Set<String> ACTIONS = Set.of("status", "visit", "follow", "stop", "wait", "resume", "defensive", "offensive", "strike", "recover");

        Escort() {
            super("escort", "aetherhaven_test.commands.escort");
            setPermissionGroups("hytale:WorldEditor");
            townTarget = DebugTownTargetArgs.registerOn(this);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> player, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (AetherhavenPlugin.get() == null) return;
            String order = ctx.get(action).toLowerCase(Locale.ROOT);
            if (!ACTIONS.contains(order)) {
                ctx.sendMessage(Message.raw("Use status, visit, follow, stop, wait, resume, defensive, offensive, strike, or recover."));
                return;
            }
            var resolved = townTarget.resolve(ctx, world, store, player, playerRef, true);
            if (!resolved.isOk()) { ctx.sendMessage(resolved.error()); return; }
            var town = resolved.townOrThrow();
            var playerTransform = store.getComponent(player, TransformComponent.getComponentType());
            var playerId = store.getComponent(player, UUIDComponent.getComponentType());
            if (playerTransform == null || playerId == null) return;
            var guards = RtsGuardDirectory.livingGuardRefs(town, store).stream()
                .filter(ref -> store.getComponent(ref, TransformComponent.getComponentType()) != null)
                .sorted(Comparator.comparingDouble(ref -> store.getComponent(ref, TransformComponent.getComponentType())
                    .getPosition().distanceSquared(playerTransform.getPosition())))
                .toList();
            if (guards.isEmpty()) {
                ctx.sendMessage(Message.raw("No town guards are loaded nearby. Move closer to the guild hall or a patrol."));
                return;
            }
            var guard = guards.getFirst();
            var transform = store.getComponent(guard, TransformComponent.getComponentType());
            var position = transform.getPosition();
            switch (order) {
                case "strike" -> {
                    var map=store.getComponent(guard,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
                    int h=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth();
                    var health=map==null?null:map.get(h);
                    if(position.distanceSquared(playerTransform.getPosition())>144||health==null||health.get()<50) {
                        ctx.sendMessage(Message.raw("Stand within twelve blocks of a guard with at least 50 health."));return;
                    }
                    float before=health.get();
                    var damage=new com.hypixel.hytale.server.core.modules.entity.damage.Damage(
                        new com.hypixel.hytale.server.core.modules.entity.damage.Damage.EnvironmentSource("Aetherhaven guard test"),
                        com.hypixel.hytale.server.core.modules.entity.damage.DamageCause.getAssetMap().getIndex("Physical"),20);
                    var knockback=new com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent();
                    knockback.setVelocity(new Vector3d(0,.1,.1));
                    damage.putMetaObject(com.hypixel.hytale.server.core.modules.entity.damage.Damage.KNOCKBACK_COMPONENT,knockback);
                    com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.executeDamage(guard,store,damage);
                    com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Guard strike test: before=%s after=%s input=20 filtered=%s cancelled=%s",before,health.get(),damage.getAmount(),damage.isCancelled());
                }
                case "recover" -> {
                    if(position.distanceSquared(playerTransform.getPosition())>144){ctx.sendMessage(Message.raw("Stand within twelve blocks of the test guard."));return;}
                    com.hexvane.aetherhaven.villager.TownVillagerHealthTopUp.maximizeHealth(guard,store,null);
                }
                case "visit" -> store.putComponent(player, Teleport.getComponentType(),
                    Teleport.createForPlayer(world, new Vector3d(position).add(0, 0, 2), new Rotation3f(0, 0, 0)));
                case "follow" -> GuardFollowPlayerSystem.startFollow(guard, store, playerId.getUuid());
                case "stop" -> GuardFollowPlayerSystem.stopFollow(guard, store, true);
                case "wait", "resume", "defensive", "offensive" ->
                    GuardFollowPlayerSystem.applyEscortOrder(guard, store, playerId.getUuid(), order);
                default -> { }
            }
            // Read the actual stored state after the action; a rejected order must not report success.
            var follow = store.getComponent(guard, GuardFollowPlayerState.getComponentType());
            var npc = store.getComponent(guard, NPCEntity.getComponentType());
            String details = String.format(Locale.ROOT, "%s at %.2f, %.2f, %.2f | %s | follow=%s | wait=%s | stance=%s",
                npc != null ? npc.getRoleName() : "Guard", position.x, position.y, position.z,
                NpcSupportUtil.stateName(store, guard), follow != null && follow.isActive(),
                follow != null && follow.isWaiting(), follow != null ? follow.getStance() : "OFFENSIVE");
            ctx.sendMessage(Message.raw(details));
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Escort test: %s", details);
        }
    }
}
