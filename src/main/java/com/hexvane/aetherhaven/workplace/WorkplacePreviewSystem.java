package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.pathtool.PathGrounding;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.*;
import org.joml.Vector3d;

/** Bounded, viewer-only borders. Reads loaded ground only and never loads a chunk. */
public final class WorkplacePreviewSystem extends EntityTickingSystem<EntityStore> {
    private static final String HUD = "AetherhavenWorkplaceTool";
    private final AetherhavenPlugin plugin;
    public WorkplacePreviewSystem(AetherhavenPlugin plugin) { this.plugin = plugin; }
    @Override public Query<EntityStore> getQuery() { return Query.and(Player.getComponentType(), PlayerRef.getComponentType()); }
    @Override public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        var ref = chunk.getReferenceTo(index); var player = chunk.getComponent(index, Player.getComponentType());
        if (player == null || player.isWaitingForClientReady()) return;
        var pr = chunk.getComponent(index, PlayerRef.getComponentType());
        var priorState = buffer.getComponent(ref, WorkplaceToolState.TYPE);
        var hand = InventoryComponent.getItemInHand(buffer, ref);
        if (hand == null || !WorkplaceToolInteraction.isTool(hand.getItemId()) || player.getPageManager().getCustomPage() != null) {
            if (player.getHudManager().getCustomHud(HUD) != null) player.getHudManager().removeCustomHud(pr, HUD);
            return;
        }
        var state = buffer.getComponent(ref, WorkplaceToolState.TYPE);
        if (state == null) { state = new WorkplaceToolState(); buffer.putComponent(ref, WorkplaceToolState.TYPE, state); }
        // Particle ages are client real time, not accelerated world time. Match
        // the two halves of the 1.2 second additive crossfade in the asset.
        long now = System.nanoTime();
        var hud = player.getHudManager().getCustomHud(HUD);
        if (state.nextPreviewNanos != 0 && now < state.nextPreviewNanos && hud != null) return;
        // Retain the cadence across normal tick jitter, but never replay missed
        // emissions after lag or switching away from the tool.
        state.nextPreviewNanos = hud == null || state.nextPreviewNanos == 0 || now - state.nextPreviewNanos >= 600_000_000L
            ? now + 600_000_000L : state.nextPreviewNanos + 600_000_000L;
        if (!(hud instanceof ToolHud)) { hud = new ToolHud(pr); player.getHudManager().addCustomHud(pr, hud); }
        ((ToolHud) hud).refresh(state);
        if (state.mode == WorkplaceToolState.Mode.BUILD) return;
        var transform = buffer.getComponent(ref, TransformComponent.getComponentType()); if (transform == null) return;
        var world = store.getExternalData().getWorld(); var pos = transform.getPosition();
        var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var town = manager.findTownContainingBlock(world.getName(), (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (town == null || !town.hasMemberOrOwner(pr.getUuid())) return;
        Set<WorkplaceArea> areas = new LinkedHashSet<>();
        if (state.first != null) {
            var target = TargetUtil.getTargetBlock(ref, 6, store);
            if (target != null) {
                var preview = WorkplaceArea.corners(state.first.x, state.first.y, state.first.z, target.x, target.z);
                if (preview.valid()) areas.add(preview);
            }
        }
        town.getApprenticeWorkplaces().sites().stream()
            .filter(s -> s.mineBaseId() == null)
            .filter(s -> Math.abs(s.area().floorY() - pos.y) <= 8 && distanceSquared(s.area(), pos) < 48 * 48)
            .sorted(Comparator.comparingDouble(s -> distanceSquared(s.area(), pos)))
            .limit(3).forEach(s -> areas.add(s.area()));
        int budget = 1600;
        for (var area : areas) {
            budget = draw(area, world, ref, store, budget, pos);
            if (budget <= 0) break;
        }
    }
    private static double distanceSquared(WorkplaceArea a, Vector3d p) {
        double x = Math.clamp(p.x, a.minX(), a.maxX()), z = Math.clamp(p.z, a.minZ(), a.maxZ());
        return (x-p.x)*(x-p.x)+(z-p.z)*(z-p.z);
    }
    private static int draw(WorkplaceArea a, World world, Ref<EntityStore> ref, Store<EntityStore> store, int budget, Vector3d viewer) {
        List<Vector3d> corners = List.of(new Vector3d(a.minX(), 0, a.minZ()), new Vector3d(a.maxX(), 0, a.minZ()),
            new Vector3d(a.maxX(), 0, a.maxZ()), new Vector3d(a.minX(), 0, a.maxZ()));
        var audience = Collections.singletonList(ref);
        for (int edge = 0; edge < 4; edge++) {
            var start = corners.get(edge); var end = corners.get((edge + 1) % 4);
            int length = (int) Math.round(start.distance(end)); Double previousY = null;
            boolean horizontal = start.z == end.z;
            float yaw = horizontal ? 0 : (float)(Math.PI/2);
            for (int step = 0; step < length; step++) {
                double f = (step + .5) / length, x = start.x + (end.x-start.x)*f, z = start.z + (end.z-start.z)*f;
                int bx = Math.clamp((int) Math.floor(x), a.minX(), a.maxX()-1), bz = Math.clamp((int) Math.floor(z), a.minZ(), a.maxZ()-1);
                var y = PathGrounding.findSupportY(world, bx, bz, a.floorY()+2, 8, Math.max(1, a.floorY()-5));
                if (y == null) { previousY = null; continue; }
                double surface = y + 1.035;
                var current = new Vector3d(x,surface,z);
                if (current.distanceSquared(viewer) > 48*48) { previousY=null; continue; }
                if (budget-- <= 0) return 0;
                // Each fixed ground strip spans one block and keeps its world orientation.
                ParticleUtil.spawnParticleEffect("Aetherhaven_Workplace_Border",x,surface,z,yaw,0,0,1,null,null,audience,store);
                if (previousY != null && previousY != surface) {
                    double joinX=x-(end.x-start.x)/length*.5, joinZ=z-(end.z-start.z)/length*.5;
                    for(double vertical=Math.min(previousY,surface);vertical<Math.max(previousY,surface);vertical++) {
                        if (budget-- <= 0) return 0;
                        ParticleUtil.spawnParticleEffect("Aetherhaven_Workplace_Border",joinX,vertical+.5,joinZ,
                            yaw,0,(float)(Math.PI/2),1,null,null,audience,store);
                    }
                }
                previousY = surface;
            }
        }
        return budget;
    }
    private static final class ToolHud extends CustomUIHud {
        private final PlayerRef viewer;
        ToolHud(PlayerRef player) { super(player, HUD, 0); viewer = player; }
        @Override protected void build(UICommandBuilder b) { b.append("Aetherhaven/WorkplaceToolHud.ui"); }
        void refresh(WorkplaceToolState state) {
            var b = new UICommandBuilder();
            b.set("#BuildingStaffModeTabs.SelectedTab",state.mode.name());
            b.set("#Mode.Text",switch(state.mode) { case BUILD -> "Build structures"; case CREATE -> "Create workplace"; case SELECT -> "Manage workplace"; case RESIZE -> "Resize workplace"; case MINESHAFT -> "Manage mineshafts"; });
            b.set("#Hint.Text", state.hint);
            b.clear("#Controls");
            var slots = new com.hexvane.aetherhaven.ui.ToolKeybindSlot[]{
                com.hexvane.aetherhaven.ui.ToolKeybindSlot.PRIMARY, com.hexvane.aetherhaven.ui.ToolKeybindSlot.SECONDARY,
                com.hexvane.aetherhaven.ui.ToolKeybindSlot.USE, com.hexvane.aetherhaven.ui.ToolKeybindSlot.ABILITY1,
                com.hexvane.aetherhaven.ui.ToolKeybindSlot.PICK};
            String[] labels = {"Choose corner or workplace", "Cancel selection", "Open workplace", "Change mode", "Choose mode"};
            if (state.mode == WorkplaceToolState.Mode.BUILD) labels = new String[]{"Find building work", "Hold to build", "Choose mode", "Change mode", "Choose mode"};
            if (state.mode == WorkplaceToolState.Mode.MINESHAFT) labels = new String[]{"Open mine planner", "Cancel selection", "Open workplace", "Change mode", "Choose mode"};
            for (int i = 0; i < slots.length; i++) com.hexvane.aetherhaven.ui.ToolHudHotkeyRows.appendRow(b,
                "#Controls", i, slots[i], com.hypixel.hytale.server.core.Message.raw(labels[i]), viewer);
            update(false, b);
        }
    }
}
