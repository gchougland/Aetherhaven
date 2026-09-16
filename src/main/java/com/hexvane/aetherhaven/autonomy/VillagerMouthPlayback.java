package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;

/** One active cursor per speaker, independent of body/eye animation playback. */
public final class VillagerMouthPlayback implements Component<EntityStore> {
    static final String PREFIX = "Aetherhaven_Life_Mouth_";
    // Face is also driven by Action.ThirdPersonFace. Replacing it for each
    // syllable interrupts expressions; the dedicated overlay owns only the jaw.
    static final AnimationSlot SLOT = AnimationSlot.ServerAction;
    private static ComponentType<EntityStore, VillagerMouthPlayback> type;
    private List<VillagerLifeSpeech.MouthCue> cues = List.of();
    private long startedNanos, audioMs, nextUpdateNanos;
    private String lastShape = "A";
    private boolean smile;

    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        type = registry.registerComponent(VillagerMouthPlayback.class, VillagerMouthPlayback::new);
        registry.registerSystem(new Tick());
    }

    static void start(Ref<EntityStore> ref, VillagerLifeSpeech.Clip clip, String gesture,
                      long visualMs, boolean dialogue, Store<EntityStore> store) {
        if (clip.mouthCues().isEmpty() || !NpcFaceVisuals.supportsFaceExpressions(ref, store)) return;
        var npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || (!dialogue && NpcFaceVisuals.isInInteractionDialogue(npc))) return;
        var active = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        var model = store.getComponent(ref, ModelComponent.getComponentType());
        if (active == null || model == null || !model.getModel().getAnimationSetMap().containsKey(PREFIX+"A")) return;
        String overlay = active.getActiveAnimations()[SLOT.ordinal()];
        if (!canAcquire(overlay)) return; // Preserve an unrelated trigger-volume overlay.
        cancel(ref, store);
        NpcFaceVisuals.holdExpression(ref, visualMs/1000f, store);
        var state = new VillagerMouthPlayback();
        state.cues = clip.mouthCues();
        state.audioMs = clip.audioMs();
        state.startedNanos = System.nanoTime();
        state.smile = "Laugh".equals(gesture);
        state.lastShape = shapeAt(state.cues, 0, state.audioMs);
        npc.playAnimation(ref, SLOT, animationName(state.lastShape, state.smile), true, store);
        store.putComponent(ref, type, state);
    }

    public static void cancel(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (type == null || !ref.isValid() || store.getComponent(ref, type) == null) return;
        store.tryRemoveComponent(ref, type);
        var npc = store.getComponent(ref, NPCEntity.getComponentType());
        var active = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (npc != null && active != null && owns(active.getActiveAnimations()[SLOT.ordinal()]))
            npc.playAnimation(ref, SLOT, null, true, store);
    }

    static boolean owns(String animation) { return animation != null && animation.startsWith(PREFIX); }
    static boolean canAcquire(String animation) { return animation == null || owns(animation); }
    private static String animationName(String shape, boolean smile) {
        return PREFIX + (smile && "BCD".contains(shape) ? "Smile_" : "") + shape;
    }

    static String shapeAt(List<VillagerLifeSpeech.MouthCue> cues, long elapsedMs, long durationMs) {
        if (elapsedMs < 0 || elapsedMs >= durationMs || cues.isEmpty()) return "A";
        int low = 0, high = cues.size();
        while (low < high) {
            int middle = (low+high) >>> 1;
            if (cues.get(middle).timeMs() <= elapsedMs) low = middle+1; else high = middle;
        }
        return low == 0 ? "A" : cues.get(low-1).shape();
    }

    @Override public VillagerMouthPlayback clone() {
        // Never resume a recording after a clone/respawn/transfer.
        return new VillagerMouthPlayback();
    }

    private static final class Tick extends EntityTickingSystem<EntityStore> {
        @Override public Query<EntityStore> getQuery() { return Query.and(type, NPCEntity.getComponentType()); }
        @Override public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                                   Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            var state = chunk.getComponent(index, type);
            long now = System.nanoTime();
            if (now < state.nextUpdateNanos) return;
            // At most 12.5 updates/sec; repeated shapes produce no packets. Skip
            // expired cues after a slow tick instead of queuing catch-up traffic.
            state.nextUpdateNanos = now + 80_000_000L;
            long elapsed = (now-state.startedNanos)/1_000_000L;
            String shape = shapeAt(state.cues, elapsed, state.audioMs);
            boolean finished = elapsed >= state.audioMs;
            if (shape.equals(state.lastShape) && !finished) return;
            state.lastShape = shape;
            String name = animationName(shape, state.smile);
            var ref = chunk.getReferenceTo(index);
            buffer.run(accessor -> {
                if (!ref.isValid() || accessor.getComponent(ref, type) != state) return;
                var npc = accessor.getComponent(ref, NPCEntity.getComponentType());
                var active = accessor.getComponent(ref, ActiveAnimationComponent.getComponentType());
                if (active == null || !owns(active.getActiveAnimations()[SLOT.ordinal()])) {
                    accessor.tryRemoveComponent(ref, type);
                    return;
                }
                if (npc != null) npc.playAnimation(ref, SLOT, finished ? null : name, finished, accessor);
                if (finished) accessor.tryRemoveComponent(ref, type);
            });
        }
    }
}
