package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3i;

public final class WorkplaceToolInteraction extends SimpleInstantInteraction {
    public static final String ITEM = "Aetherhaven_Workplace_Tool";
    public static final BuilderCodec<WorkplaceToolInteraction> CODEC = BuilderCodec.builder(
        WorkplaceToolInteraction.class, WorkplaceToolInteraction::new, SimpleInstantInteraction.CODEC).build();
    @Override public WaitForDataFrom getWaitForDataFrom() { return WaitForDataFrom.Client; }
    @Override public boolean needsRemoteSync() { return true; }
    @Override protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldown) {
        handle(type, context);
    }
    public static boolean isTool(String id) {
        return ITEM.equals(id) || com.hexvane.aetherhaven.construction.assembly.BuildingStaffTiers.isBuildingStaff(id);
    }
    public static boolean routeWorkplaceAction(InteractionType type, InteractionContext context) {
        var buffer = context.getCommandBuffer();
        if (buffer == null || WorkplaceToolState.TYPE == null) return false;
        var state = buffer.getComponent(context.getEntity(), WorkplaceToolState.TYPE);
        if (state == null || state.mode == WorkplaceToolState.Mode.BUILD) return false;
        handle(type, context);
        context.getState().state = InteractionState.Finished;
        return true;
    }
    private static void handle(InteractionType type, InteractionContext context) {
        var buffer = context.getCommandBuffer();
        if (buffer == null || AetherhavenPlugin.get() == null) return;
        var ref = context.getEntity();
        var hand = InventoryComponent.getItemInHand(buffer, ref);
        if (hand == null || !isTool(hand.getItemId())) return;
        var store = buffer.getStore();
        var state = buffer.getComponent(ref, WorkplaceToolState.TYPE);
        if (state == null) { state = new WorkplaceToolState(); buffer.putComponent(ref, WorkplaceToolState.TYPE, state); }
        state.nextPreviewNanos = 0;
        if (type == InteractionType.Pick || (type == InteractionType.Use && state.mode == WorkplaceToolState.Mode.BUILD)) {
            var player = buffer.getComponent(ref, Player.getComponentType());
            var pr = buffer.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && pr != null) store.getExternalData().getWorld().execute(() -> {
                if (ref.isValid()) player.getPageManager().openCustomPage(ref, store, new WorkplaceModePage(pr));
            });
        } else if (type == InteractionType.Ability1) {
            state.choose(WorkplaceToolState.Mode.values()[(state.mode.ordinal() + 1) % WorkplaceToolState.Mode.values().length], ref, store);
        } else if (type == InteractionType.Secondary) {
            state.clearSelection(); state.hint = "Selection cleared. Your saved workplaces are unchanged.";
        } else if (type == InteractionType.Primary || type == InteractionType.Use) {
            var sync = context.getClientState();
            var block = sync != null ? sync.blockPosition : context.getTargetBlock();
            Vector3i target = block == null ? TargetUtil.getTargetBlock(ref, 6, store) : new Vector3i(block.x, block.y, block.z);
            var player = buffer.getComponent(ref, Player.getComponentType());
            var pr = buffer.getComponent(ref, PlayerRef.getComponentType());
            if (target == null || player == null || pr == null) return;
            var transform = buffer.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform == null || target.y < 0 || target.y > (state.mode == WorkplaceToolState.Mode.MINESHAFT ? 316 : 314)
                || transform.getPosition().distanceSquared(new org.joml.Vector3d(target.x + .5, target.y + .5, target.z + .5)) > 81) return;
            var world = store.getExternalData().getWorld();
            var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, AetherhavenPlugin.get());
            var town = manager.findTownContainingBlock(world.getName(), target.x, target.z);
            if (town == null || !town.hasMemberOrOwner(pr.getUuid()) || !town.playerCanManageConstructions(pr.getUuid())) { state.hint = "Choose ground inside a town you can manage."; return; }
            boolean selecting = type == InteractionType.Use || state.mode == WorkplaceToolState.Mode.SELECT
                || (state.mode == WorkplaceToolState.Mode.RESIZE && state.editing == null);
            if (state.mode == WorkplaceToolState.Mode.MINESHAFT && type == InteractionType.Primary) {
                var site=town.getApprenticeWorkplaces().atGroundOrBlock(target.x,target.y,target.z);
                if(site==null||site.type()!=com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.MINING){state.hint="Create a Mining workplace around a Workbench using Create workplace mode. Use its bench to plan tunnels.";return;}
                var page = new MineshaftPlannerPage(pr, town.getTownId(), site.id());
                world.execute(() -> { if (ref.isValid()) player.getPageManager().openCustomPage(ref, store, page); });
            } else if (selecting) {
                var site = town.getApprenticeWorkplaces().atGroundOrBlock(target.x, target.y, target.z);
                if (site == null) { state.hint = "Look at ground or equipment inside a workplace on this floor."; return; }
                if (state.mode == WorkplaceToolState.Mode.RESIZE && type != InteractionType.Use) {
                    state.editing = site.id(); state.townId = town.getTownId(); state.first = null;
                    state.hint = "Resizing " + site.name() + ". Choose the first new corner.";
                } else {
                    world.execute(() -> { if (ref.isValid()) player.getPageManager().openCustomPage(ref, store,
                        site.type()==com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.MINING?new MineshaftPlannerPage(pr,town.getTownId(),site.id()):new WorkplacePage(pr,town.getTownId(),site.id(),site.area())); });
                }
            } else if (state.first == null) {
                if (state.editing != null && !town.getTownId().equals(state.townId)) { state.hint = "Keep the workplace in its own town."; return; }
                var ground = WorkplaceSelectionGround.ground(world,target);
                if(ground.y<0||ground.y>314){state.hint="Choose ground below Y=315.";return;}
                state.first = ground; state.townId = town.getTownId();
                state.hint = "First corner set. Workplace floor Y="+(ground.y+1)+". Choose the opposite ground corner.";
            } else {
                if (!town.getTownId().equals(state.townId)) { state.hint = "Both corners must belong to the same town."; return; }
                var area = WorkplaceArea.corners(state.first.x, state.first.y, state.first.z, target.x, target.z);
                if (!area.valid()) { state.hint = "Select an area from 2 by 2 to 64 by 64 blocks."; return; }
                var page = new WorkplacePage(pr, town.getTownId(), state.editing, area);
                boolean marked = state.editing == null;
                world.execute(() -> {
                    // A new area, not a resize of an old one, is what a quest means by marking out a workplace.
                    if (marked) com.hexvane.aetherhaven.quest.WorkplaceQuestHooks.areaMarked(world, manager, town);
                    if (ref.isValid()) player.getPageManager().openCustomPage(ref, store, page);
                });
            }
        }
        context.getState().state = InteractionState.Finished;
    }
}
