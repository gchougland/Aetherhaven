package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.clinic.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;

/** Explicit fixtures and normal screen entry. Never heals anyone or advances a course. */
final class AetherhavenClinicTestCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> action = withRequiredArg("action", "status, open, supplies, doctor or wound", ArgTypes.STRING);
    private final DebugTownTargetArgs target;
    AetherhavenClinicTestCommand() {
        super("clinic", "Inspect clinic care or prepare labeled test fixtures"); setPermissionGroups("hytale:WorldEditor");
        target = DebugTownTargetArgs.registerOn(this);
    }
    @Override protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> player, PlayerRef pr, World world) {
        var plugin = AetherhavenPlugin.get(); if (plugin == null) return;
        var resolved = target.resolve(ctx, world, store, player, pr, true);
        if (!resolved.isOk()) { ctx.sendMessage(resolved.error()); return; }
        var town = resolved.townOrThrow(); var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var plot = town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), ClinicCareSystem.CONSTRUCTION);
        var doctors = new ArrayList<Ref<EntityStore>>();
        store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(), NPCEntity.getComponentType()), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                var b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                if (town.getTownId().equals(b.getTownId()) && "Aetherhaven_Doctor".equals(chunk.getComponent(i, NPCEntity.getComponentType()).getRoleName()))
                    doctors.add(chunk.getReferenceTo(i));
            }
        });
        switch (ctx.get(action)) {
            case "wound" -> {
                var origin=store.getComponent(player,TransformComponent.getComponentType()).getPosition();
                var candidates=new ArrayList<Ref<EntityStore>>();
                store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(),NPCEntity.getComponentType(),
                    UUIDComponent.getComponentType(),TransformComponent.getComponentType(),
                    com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType()),(chunk,buffer)->{
                    for(int i=0;i<chunk.size();i++) {
                        var ref=chunk.getReferenceTo(i);
                        var binding=chunk.getComponent(i,TownVillagerBinding.getComponentType());
                        var npc=chunk.getComponent(i,NPCEntity.getComponentType());
                        var hp=chunk.getComponent(i,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType())
                            .get(com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());
                        if(town.getTownId().equals(binding.getTownId())&&!ClinicCareSystem.KIND.equals(binding.getKind())
                            &&TownAdmission.isMember(town,chunk.getComponent(i,UUIDComponent.getComponentType()).getUuid())
                            &&!com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem.skipsPoiAutonomy(binding,npc)
                            &&store.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType())==null
                            &&hp!=null&&hp.get()>1&&hp.getMax()>=20
                            &&origin.distanceSquared(chunk.getComponent(i,TransformComponent.getComponentType()).getPosition())<=144)
                            candidates.add(ref);
                    }
                });
                candidates.sort(Comparator.comparingDouble(ref->origin.distanceSquared(store.getComponent(ref,TransformComponent.getComponentType()).getPosition())));
                if(candidates.isEmpty()) { report(ctx,"No living civilian resident is within twelve blocks."); return; }
                var patient=candidates.getFirst();
                var stats=store.getComponent(patient,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
                int key=com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth();
                var hp=stats.get(key);
                stats.setStatValue(key,Math.max(1,Math.min(hp.get(),hp.getMax()*.6f)));
                store.putComponent(patient,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType(),stats);
                report(ctx,"Test injury applied to "+store.getComponent(patient,NPCEntity.getComponentType()).getRoleName()
                    +". Health="+stats.get(key).get()+"/"+hp.getMax()+". No movement or treatment was forced.");
            }
            case "supplies" -> {
                var inventory = InventoryComponent.getCombined(store, player, InventoryComponent.EVERYTHING);
                boolean granted = inventory != null && inventory.addItemStack(new ItemStack(TownClinic.SUPPLY_ITEM, 5), true, false, true).succeeded();
                report(ctx, granted ? "Granted five test Lesser Health Potions. Donate them through the normal clinic screen." : "Make room for five potions first.");
            }
            case "doctor" -> {
                if (!doctors.isEmpty() || town.getResidentNpcRecords().stream().anyMatch(r -> "Aetherhaven_Doctor".equals(r.getNpcRoleId()))) {
                    report(ctx, "The Doctor already exists or has a saved record. Use ordinary recruitment or Gaia recovery."); return;
                }
                if (plot == null) { report(ctx, "Complete the Clinic first."); return; }
                var admission = TownAdmission.blocked(plugin, town, null, "Aetherhaven_Doctor");
                if (admission != null) { ctx.sendMessage(admission); return; }
                var position = store.getComponent(player, TransformComponent.getComponentType()).getPosition();
                int x = (int)Math.floor(position.x), y = (int)Math.floor(position.y), z = (int)Math.floor(position.z);
                if (!plot.containsWorldBlock(x,y,z) || !com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.isNpcStandColumn(world,x,y,z)) {
                    report(ctx, "Stand in a clear, loaded cell inside the completed Clinic."); return;
                }
                var npc = TownVillagerSpawner.spawn(store, "Aetherhaven_Doctor", new org.joml.Vector3d(position),
                    new TownVillagerBinding(town.getTownId(), ClinicCareSystem.KIND, plot.getPlotId(), plot.getPlotId()), "clinic_test", "Explicit Doctor test fixture");
                if (npc == null) { report(ctx, "The Doctor fixture could not be created."); return; }
                var uuid = store.getComponent(npc, UUIDComponent.getComponentType()).getUuid();
                ResidentRegistryService.upsert(town, manager, "Aetherhaven_Doctor", ClinicCareSystem.KIND, plot.getPlotId(), uuid);
                manager.updateTown(town); report(ctx, "Created one Doctor test resident. No medicine or healing was granted. This does not verify recruitment.");
            }
            case "open" -> {
                if (doctors.isEmpty()) { report(ctx, "No Doctor is loaded in this town."); return; }
                store.getComponent(player, Player.getComponentType()).getPageManager().openCustomPage(player, store, new ClinicPage(pr, town.getTownId(), doctors.getFirst()));
            }
            case "status" -> {
                report(ctx, "Clinic test: complete=" + (plot != null) + " medicine=" + town.getClinic().medicine() + " paused=" + town.getClinic().paused() + " doctors=" + doctors.size());
                for (var doctor : doctors) {
                    var p = store.getComponent(doctor, TransformComponent.getComponentType());
                    report(ctx, "Doctor position=" + (p == null ? null : p.getPosition()) + " patients=" + (plot == null ? 0 : ClinicCareSystem.patients(town,plot,doctor,store).size()));
                    var needs = store.getComponent(doctor, VillagerNeeds.getComponentType());
                    var autonomy = store.getComponent(doctor, com.hexvane.aetherhaven.autonomy.VillagerAutonomyState.getComponentType());
                    report(ctx, "Doctor state=" + com.hexvane.aetherhaven.npc.NpcSupportUtil.stateName(store,doctor)
                        + " needs=" + (needs == null ? null : needs.getHunger()+"/"+needs.getEnergy())
                        + " target=" + (autonomy == null ? null : autonomy.getTargetX()+","+autonomy.getTargetY()+","+autonomy.getTargetZ()));
                    report(ctx,"Doctor care="+com.hexvane.aetherhaven.autonomy.ClinicVisitService.diagnostic(doctor));
                }
                store.forEachChunk(Query.and(TownVillagerBinding.getComponentType(),NPCEntity.getComponentType(),
                        com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType()),(chunk,buffer)->{
                    for(int i=0;i<chunk.size();i++) {
                        var binding=chunk.getComponent(i,TownVillagerBinding.getComponentType());
                        if(!town.getTownId().equals(binding.getTownId()))continue;
                        var hp=chunk.getComponent(i,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType())
                            .get(com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());
                        var ref=chunk.getReferenceTo(i);
                        if(hp!=null&&(hp.get()<hp.getMax()||com.hexvane.aetherhaven.autonomy.ClinicVisitService.active(ref)))
                            report(ctx,"Resident care role="+chunk.getComponent(i,NPCEntity.getComponentType()).getRoleName()
                                +" health="+hp.get()+"/"+hp.getMax()+" "+com.hexvane.aetherhaven.autonomy.ClinicVisitService.diagnostic(ref));
                    }
                });
            }
            default -> report(ctx, "Use status, open, supplies, doctor or wound.");
        }
    }
    private static void report(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text)); com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);
    }
}
