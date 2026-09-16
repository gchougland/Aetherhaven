package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistState;
import com.hexvane.aetherhaven.builder.BuilderConstructionAssistSystem;
import com.hexvane.aetherhaven.clown.ClownCheerAssistState;
import com.hexvane.aetherhaven.clown.ClownCheerAssistSystem;
import com.hexvane.aetherhaven.festival.snowball.SnowballSessionIndex;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.npc.NpcSupportUtil;
import com.hexvane.aetherhaven.shopspot.ShopSpotOpenService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Paired conversations, needs acting and idle flavor, all on the owning world thread. */
public final class VillagerLifeSystem extends EntityTickingSystem<EntityStore> {
    private final AetherhavenPlugin plugin;

    public VillagerLifeSystem(AetherhavenPlugin plugin) { this.plugin = plugin; }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, VillagerAutonomySystem.class));
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(TownVillagerBinding.getComponentType(), VillagerNeeds.getComponentType(),
            NPCEntity.getComponentType(), UUIDComponent.getComponentType(), TransformComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        VillagerLifeState life = chunk.getComponent(index, VillagerLifeState.getComponentType());
        long now = System.currentTimeMillis();
        if (life != null && now < life.nextUpdateMs) return;
        // A single deferred transaction reserves both NPCs. A second search sees the first
        // reservation immediately, including when the NPCs belong to different chunks.
        buffer.run(s -> { if (ref.isValid()) update(ref, s, System.currentTimeMillis()); });
    }

    public static boolean ownsActivity(Ref<EntityStore> ref, Store<EntityStore> store) {
        VillagerLifeState state = store.getComponent(ref, VillagerLifeState.getComponentType());
        return state != null && state.ownsActivity(System.currentTimeMillis());
    }

    private void update(Ref<EntityStore> ref, Store<EntityStore> store, long now) {
        VillagerLifeState life = store.getComponent(ref, VillagerLifeState.getComponentType());
        if (life == null) {
            life = new VillagerLifeState();
            life.nextSearchMs = now + ThreadLocalRandom.current().nextLong(1000, 5000);
            life.nextEmoteMs = now + ThreadLocalRandom.current().nextLong(5000, 14000);
            store.putComponent(ref, VillagerLifeState.getComponentType(), life);
        }
        life.nextUpdateMs = now + 250;
        VillagerLifeVisuals.maintainReading(ref, store);
        var activity = store.getComponent(ref, VillagerAutonomyState.getComponentType());
        if (activity != null && activity.getPhase() == VillagerAutonomyState.PHASE_USE && activity.getTargetPoiUuid() != null) {
            var poi = AetherhavenWorldRegistries.getOrCreatePoiRegistry(store.getExternalData().getWorld(), plugin).get(activity.getTargetPoiUuid());
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (poi != null && PoiScoring.isEatPoi(poi) && now >= life.nextEatingSoundMs
                && npc != null && !NpcFaceVisuals.isInInteractionDialogue(npc)) {
                VillagerLifeVisuals.eatingSound(ref, store);
                life.nextEatingSoundMs = now + 3200;
            }
        }
        if (life.session != null) {
            advance(life.session, store, now);
            return;
        }
        // Departure acting owns the brief pause even though hunger is a priority need.
        if (life.mealDeparture && life.emoteUntilMs != 0) {
            if (now < life.emoteUntilMs) return;
            life.mealDeparture = false;
            releaseEmote(ref, life, store);
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null && !NpcFaceVisuals.isInInteractionDialogue(npc)) NpcStandStill.release(ref, npc, store);
            return;
        }
        if (!available(ref, store, false)) {
            if (life.emoteUntilMs != 0) releaseEmote(ref, life, store);
            return;
        }
        if (now >= life.nextContextThoughtMs) {
            life.nextContextThoughtMs = now + ThreadLocalRandom.current().nextLong(25000, 45000);
            if (VillagerLifeContext.rainy(ref, store)) VillagerLifeVisuals.bubble(ref, false, "Rain", store);
            else if (VillagerLifeContext.needsHouse(ref, store, plugin)) VillagerLifeVisuals.bubble(ref, false, "Home", store);
        }
        if (life.emoteUntilMs != 0) {
            if (now < life.emoteUntilMs) return;
            releaseEmote(ref, life, store);
        }
        VillagerNeeds needs = store.getComponent(ref, VillagerNeeds.getComponentType());
        if (needs == null) return;
        if (now < life.visualUntilMs) return;
        if (ShopSpotOpenService.isGameDay(store) && now >= life.nextSearchMs && now >= life.socialCooldownMs
            && VillagerLifePolicy.canSocialize(needs.getHunger(), needs.getEnergy())) {
            boolean recreation = needs.getFun() < 40 || isLeisure(ref, store);
            life.nextSearchMs = now + (recreation ? 45_000 : 18_000);
            // One weighted choice per break; partner cooldown is independent of search retries.
            if (VillagerLifePolicy.chooseConversation(ThreadLocalRandom.current().nextDouble(), recreation)) {
                if (tryStart(ref, life, store, now, !recreation)) return;
                life.nextSearchMs = now + 5000;
            }
        }
        if (now < life.nextEmoteMs || mounted(ref, store)) return;
        VillagerAutonomyState aut = store.getComponent(ref, VillagerAutonomyState.getComponentType());
        if (aut == null || aut.getPhase() == VillagerAutonomyState.PHASE_TRAVEL) return;
        String gesture = VillagerLifePolicy.needEmote(needs.getHunger(), needs.getEnergy(), needs.getFun());
        // Work uses its own gestures; avoid interrupting a tool swing for an idle fidget.
        if (gesture == null && aut.getPhase() != VillagerAutonomyState.PHASE_IDLE) return;
        life.nextEmoteMs = now + ThreadLocalRandom.current().nextLong(22_000, 40_000);
        String actedGesture = gesture == null ? VillagerLifePersonality.gesture(ref, store, plugin, true, false) : gesture;
        life.emoteUntilMs = now + VillagerLifeTiming.durationMs(actedGesture) + 250;
        if (aut.getPhase() == VillagerAutonomyState.PHASE_USE)
            VillagerAutonomySystem.resetAutonomyForRescue(ref, store, VillagerAutonomySystem.resolveAutonomyNowMs(store));
        hold(ref, store);
        if (gesture == null) {
            long voiceMs = VillagerLifeVisuals.ambient(ref, actedGesture, store);
            life.emoteUntilMs = Math.max(life.emoteUntilMs, now + voiceMs + 300);
            VillagerLifeVisuals.bubble(ref, false, VillagerLifePersonality.thought(ref, store, plugin), store);
        } else {
            UUID id = store.getComponent(ref, UUIDComponent.getComponentType()).getUuid();
            String icon = switch (gesture) { case "Hungry" -> VillagerLifePersonality.hungryThought(ref, store, plugin); case "Sleepy" -> "Sleep"; default -> "Bored"; };
            VillagerLifeVisuals.bubble(ref, false, icon, store);
            long voiceMs = VillagerLifeVisuals.voice(ref, id, switch (gesture) {
                case "Hungry" -> "Groan"; case "Sleepy" -> "Yawn"; default -> "Sigh";
            }, store);
            life.emoteUntilMs = Math.max(life.emoteUntilMs, now + voiceMs + 300);
            if (gesture.equals("Hungry")) VillagerLifeVisuals.voice(ref, id, "Stomach", store);
        }
    }

    private boolean tryStart(Ref<EntityStore> ref, VillagerLifeState life, Store<EntityStore> store, long now, boolean sameBuildingOnly) {
        if (!available(ref, store, true)) return false;
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        Vector3d pos = store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
        List<Ref<EntityStore>> candidates = new ArrayList<>();
        // Read-only scan, with every reservation and write after iteration is complete.
        store.forEachChunk(getQuery(), (chunk, ignored) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> other = chunk.getReferenceTo(i);
                TownVillagerBinding b = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                if (other.equals(ref) || b == null || tc == null || !binding.getTownId().equals(b.getTownId())) continue;
                if (pos.distanceSquared(tc.getPosition()) > VillagerLifePolicy.SEARCH_RADIUS * VillagerLifePolicy.SEARCH_RADIUS) continue;
                VillagerLifeState otherLife = chunk.getComponent(i, VillagerLifeState.getComponentType());
                if (otherLife != null && (otherLife.ownsActivity(now) || now < otherLife.socialCooldownMs)) continue;
                VillagerNeeds otherNeeds = chunk.getComponent(i, VillagerNeeds.getComponentType());
                if (otherNeeds == null || !VillagerLifePolicy.canSocialize(otherNeeds.getHunger(), otherNeeds.getEnergy())) continue;
                boolean nearby = VillagerLifePolicy.inTalkingRange(pos.distanceSquared(tc.getPosition()), pos.y - tc.getPosition().y);
                // A neighbor behind a wall must not repeatedly beat a visible partner.
                if (nearby && !clearSight(pos, tc.getPosition(), store)) continue;
                if (sameBuildingOnly && !sameBuilding(ref, other, store)) continue;
                if ((mounted(ref, store) || mounted(other, store)) && !nearby) continue;
                if (available(other, store, true)) candidates.add(other);
            }
        });
        candidates.sort(Comparator.comparingDouble(r -> pos.distanceSquared(store.getComponent(r, TransformComponent.getComponentType()).getPosition())));
        if (candidates.isEmpty()) return false;
        Ref<EntityStore> other = candidates.getFirst();
        VillagerLifeState otherLife = store.getComponent(other, VillagerLifeState.getComponentType());
        if (otherLife == null) {
            otherLife = new VillagerLifeState();
            store.putComponent(other, VillagerLifeState.getComponentType(), otherLife);
        }
        UUID first = store.getComponent(ref, UUIDComponent.getComponentType()).getUuid();
        UUID second = store.getComponent(other, UUIDComponent.getComponentType()).getUuid();
        VillagerLifeState.Session session = new VillagerLifeState.Session(first, second, now, VillagerLifePersonality.thought(ref, store, plugin));
        session.romance = VillagerRomance.choose(ThreadLocalRandom.current().nextDouble(), ThreadLocalRandom.current().nextDouble());
        life.session = session;
        otherLife.session = session;
        life.emoteUntilMs = otherLife.emoteUntilMs = 0;
        Vector3d otherPos = store.getComponent(other, TransformComponent.getComponentType()).getPosition();
        session.stationary = VillagerLifePolicy.inTalkingRange(pos.distanceSquared(otherPos), pos.y - otherPos.y);
        // Reserve a busy neighbor, then let their current gesture finish. Requiring
        // both workers' short animation gaps to coincide could starve chats forever.
        session.readyAfterMs = Math.max(now, otherLife.visualUntilMs);
        return true;
    }

    private void advance(VillagerLifeState.Session session, Store<EntityStore> store, long now) {
        if (now - session.lastUpdateMs < 200) return;
        double seconds = (now - session.lastUpdateMs) / 1000.0;
        session.lastUpdateMs = now;
        Ref<EntityStore> a = store.getExternalData().getRefFromUUID(session.first);
        Ref<EntityStore> b = store.getExternalData().getRefFromUUID(session.second);
        if (!paired(a, session, store) || !paired(b, session, store)
            || !available(a, store, true) || !available(b, store, true)
            || !ShopSpotOpenService.isGameDay(store)) {
            finish(session, a, b, store, now);
            return;
        }
        TownVillagerBinding ba = store.getComponent(a, TownVillagerBinding.getComponentType());
        TownVillagerBinding bb = store.getComponent(b, TownVillagerBinding.getComponentType());
        VillagerNeeds na = store.getComponent(a, VillagerNeeds.getComponentType());
        VillagerNeeds nb = store.getComponent(b, VillagerNeeds.getComponentType());
        if (!ba.getTownId().equals(bb.getTownId()) || !VillagerLifePolicy.canSocialize(na.getHunger(), na.getEnergy())
            || !VillagerLifePolicy.canSocialize(nb.getHunger(), nb.getEnergy())) {
            finish(session, a, b, store, now);
            return;
        }
        if (now < session.readyAfterMs) return;
        if (!session.prepared) {
            session.prepared = true;
            if (!session.stationary) {
                long autonomyNow = VillagerAutonomySystem.resolveAutonomyNowMs(store);
                VillagerAutonomySystem.resetAutonomyForRescue(a, store, autonomyNow);
                VillagerAutonomySystem.resetAutonomyForRescue(b, store, autonomyNow);
            }
            VillagerLifeProps.clear(a, store);
            VillagerLifeProps.clear(b, store);
            hold(b, store);
        }
        Vector3d pa = store.getComponent(a, TransformComponent.getComponentType()).getPosition();
        Vector3d pb = store.getComponent(b, TransformComponent.getComponentType()).getPosition();
        double distance = pa.distanceSquared(pb);
        if (!VillagerLifePolicy.inTalkingRange(distance, pa.y - pb.y) || !clearSight(pa, pb, store)) {
            if (session.stationary || session.talkingSinceMs != 0 || now - session.readyAfterMs > VillagerLifePolicy.APPROACH_TIMEOUT_MS) {
                finish(session, a, b, store, now);
                return;
            }
            // One approaches, one waits; fixed stand-off avoids two NPCs chasing each other.
            Vector3d direction = new Vector3d(pa).sub(pb);
            direction.y = 0;
            if (direction.lengthSquared() < .01) direction.set(1, 0, 0);
            Vector3d destination = new Vector3d(pb).add(direction.normalize().mul(2));
            NPCEntity npc = store.getComponent(a, NPCEntity.getComponentType());
            npc.setLeashPoint(destination);
            if (!NpcSupportUtil.stateName(store, a).startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI))
                NpcSupportUtil.setState(a, AetherhavenConstants.NPC_STATE_AUTONOMY_POI, null, store);
            store.putComponent(a, NPCEntity.getComponentType(), npc);
            VillagerDoorUtil.tryOpenDoorsTowardLeash(store.getExternalData().getWorld(), pa, destination, null);
            return;
        }
        if (session.talkingSinceMs == 0) {
            session.talkingSinceMs = now;
            seconds = 0; // Approaching a neighbor does not refill fun.
            hold(a, store);
            hold(b, store);
        }
        if (session.finishedTalking(now)) {
            finish(session, a, b, store, now);
            return;
        }
        face(a, pb, store);
        face(b, pa, store);
        refill(a, na, seconds, store);
        refill(b, nb, seconds, store);
        var response = session.takeResponse(now);
        if (response != null) VillagerLifeVisuals.respond(response.toFirst() ? a : b,
            response.gesture(), response.bubble(), response.hearts(), store);
        if (now < session.nextBeatMs) return;
        if (!session.romance.isEmpty()) {
            var beat = session.romance.get(session.beat);
            Ref<EntityStore> speaker = beat.firstSpeaks() ? a : b;
            Ref<EntityStore> listener = beat.firstSpeaks() ? b : a;
            long duration = VillagerLifeVisuals.romance(speaker, listener,
                beat.firstSpeaks() ? session.first : session.second, beat, store);
            long spokenAt = System.currentTimeMillis();
            session.respondLater(!beat.firstSpeaks(), beat.listenerGesture(), beat.listenerBubble(), beat.listenerHearts(), spokenAt);
            session.nextBeatMs = spokenAt + Math.max(duration,
                VillagerLifeState.Session.RESPONSE_DELAY_MS + VillagerLifeTiming.durationMs(beat.listenerGesture())) + 300;
            session.beat++;
            return;
        }
        boolean firstSpeaks = session.beat % 2 == 0;
        Ref<EntityStore> speaker = firstSpeaks ? a : b;
        Ref<EntityStore> listener = firstSpeaks ? b : a;
        if (session.beat > 0 && session.beat % 2 == 0) session.topic = VillagerLifePersonality.thought(speaker, store, plugin);
        String gesture = session.beat == 0 ? "Greet" : VillagerLifePersonality.gesture(speaker, store, plugin, false, false);
        String shownItem = session.beat == 0 ? null : VillagerConversationItems.forTopic(session.topic, ThreadLocalRandom.current().nextDouble());
        if (shownItem != null) {
            VillagerLifeVisuals.state(speaker, store).conversationItemId = shownItem;
            gesture = "ShowItem";
        }
        long speechMs = VillagerLifeVisuals.speak(speaker, firstSpeaks ? session.first : session.second, gesture, session.topic, store);
        String reaction = VillagerLifePersonality.gesture(listener, store, plugin, false, true);
        String feeling = VillagerLifePersonality.feeling(listener, session.topic, reaction, store, plugin);
        if (feeling.equals("Love")) reaction = "Agree";
        if (feeling.equals("Disagree")) reaction = "Disagree";
        long spokenAt = System.currentTimeMillis();
        session.nextBeatMs = spokenAt + Math.max(3300,
            Math.max(speechMs, Math.max(VillagerLifeTiming.durationMs(gesture),
                VillagerLifeState.Session.RESPONSE_DELAY_MS + VillagerLifeTiming.durationMs(reaction))) + 300);
        session.respondLater(!firstSpeaks, reaction, feeling, false, spokenAt);
        // Only one voiced utterance at a time. Listener reactions remain visual.
        session.beat++;
    }

    private boolean available(Ref<EntityStore> ref, Store<EntityStore> store, boolean social) {
        if (ref == null || !ref.isValid()) return false;
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (npc == null || binding == null || binding.getTownId() == null || npc.getRole() == null
            || VillagerAutonomySystem.skipsPoiAutonomy(binding, npc)
            || TownVillagerBinding.isRescueKind(binding.getKind())
            || NpcFaceVisuals.isInInteractionDialogue(npc)) return false;
        String state = NpcSupportUtil.stateName(store, ref);
        if (!(state.equals("Idle") || state.startsWith("Idle.") || state.startsWith(AetherhavenConstants.NPC_STATE_AUTONOMY_POI)
            || state.startsWith(AetherhavenConstants.NPC_STATE_STAND_STILL))) return false;
        if (store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType()) != null) return false;
        var movement = store.getComponent(ref, com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());
        if (movement != null && movement.getMovementStates() != null) {
            var flags = movement.getMovementStates();
            // Walking over a stair briefly reports falling; do not cancel a reserved approach for that.
            if (flags.jumping || flags.swimming) return false;
        }
        if (hasPriorityActivity(ref, store)) return false;
        VillagerAutonomyState aut = store.getComponent(ref, VillagerAutonomyState.getComponentType());
        VillagerLifeState life = store.getComponent(ref, VillagerLifeState.getComponentType());
        if (social && (life == null || life.session == null)) {
            if (aut.getPhase() == VillagerAutonomyState.PHASE_TRAVEL && !aut.isFillingFun() && !isLeisure(ref, store)) return false;
            VillagerNeeds needs = store.getComponent(ref, VillagerNeeds.getComponentType());
            if (needs == null) return false;

        }
        return true;
    }

    private boolean hasPriorityActivity(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType()) != null) return true;
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || NpcFaceVisuals.isInInteractionDialogue(npc)) return true;
        TownVillagerBinding binding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (binding == null || binding.getTownId() == null) return true;
        UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
        if (id == null || SnowballSessionIndex.isLivingFighter(id.getUuid())) return true;
        if (BuilderConstructionAssistSystem.shouldSkipAutonomy(store.getComponent(ref, BuilderConstructionAssistState.getComponentType()))
            || ClownCheerAssistSystem.shouldSkipAutonomy(store.getComponent(ref, ClownCheerAssistState.getComponentType()))
            || VillagerFollowPlayerSystem.shouldSkipAutonomy(store.getComponent(ref, VillagerFollowPlayerState.getComponentType()))) return true;
        if (com.hexvane.aetherhaven.festival.wintertide.WintertideGiftSeekState.isRegistered()
            && com.hexvane.aetherhaven.festival.wintertide.WintertideGiftSeekSystem.shouldSkipAutonomy(store.getComponent(ref,
                com.hexvane.aetherhaven.festival.wintertide.WintertideGiftSeekState.getComponentType()))) return true;
        if (com.hexvane.aetherhaven.calendar.PlayerBirthdayGiftSeekState.isRegistered()
            && com.hexvane.aetherhaven.calendar.PlayerBirthdayGiftSeekSystem.shouldSkipAutonomy(store.getComponent(ref,
                com.hexvane.aetherhaven.calendar.PlayerBirthdayGiftSeekState.getComponentType()))) return true;
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin).getTown(binding.getTownId());
        if (town == null || town.getActiveFestivalId() != null
            || town.getFeastGatherDeadlineEpochMs() > VillagerAutonomySystem.resolveAutonomyNowMs(store)) return true;
        VillagerAutonomyState aut = store.getComponent(ref, VillagerAutonomyState.getComponentType());
        if (aut == null || aut.isFillingHunger() || aut.isFillingEnergy()) return true;
        if (aut.getPhase() == VillagerAutonomyState.PHASE_USE && aut.getTargetPoiUuid() != null) {
            var reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(store.getExternalData().getWorld(), plugin);
            var poi = reg.get(aut.getTargetPoiUuid());
            if (poi != null && (PoiScoring.isEatPoi(poi) || PoiScoring.isRestPoi(poi))) return true;
        }
        return false;
    }

    static boolean isLeisure(Ref<EntityStore> ref, Store<EntityStore> store) {
        var schedule = store.getComponent(ref, com.hexvane.aetherhaven.schedule.VillagerScheduleTickState.getComponentType());
        return schedule != null && "park".equals(schedule.getLastAppliedScheduleSegment());
    }

    private static boolean mounted(Ref<EntityStore> ref, Store<EntityStore> store) {
        return store.getComponent(ref, com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType()) != null;
    }

    private boolean sameBuilding(Ref<EntityStore> a, Ref<EntityStore> b, Store<EntityStore> store) {
        var binding = store.getComponent(a, TownVillagerBinding.getComponentType());
        var town = AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(), plugin).getTown(binding.getTownId());
        Vector3d pa = store.getComponent(a, TransformComponent.getComponentType()).getPosition();
        Vector3d pb = store.getComponent(b, TransformComponent.getComponentType()).getPosition();
        if (town == null || Math.abs(pa.y - pb.y) > 2) return false;
        for (var plot : town.getPlotInstances()) {
            var f = plot.toFootprint();
            if (pa.x >= f.getMinX() && pa.x < f.getMaxX()+1 && pa.z >= f.getMinZ() && pa.z < f.getMaxZ()+1
                && pb.x >= f.getMinX() && pb.x < f.getMaxX()+1 && pb.z >= f.getMinZ() && pb.z < f.getMaxZ()+1) return true;
        }
        return false;
    }

    /** Reserve the pause before autonomy starts seeking a meal. */
    static boolean beforeMeal(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        var needs = store.getComponent(ref, VillagerNeeds.getComponentType());
        if (needs == null || needs.getHunger() >= 50 || mounted(ref, store)) return false;
        long now = System.currentTimeMillis();
        var life = store.getComponent(ref, VillagerLifeState.getComponentType());
        if (life == null) life = new VillagerLifeState();
        if (now < life.hungryCooldownMs) return false;
        life.hungryCooldownMs = now + 60_000;
        life.mealDeparture = true;
        life.emoteUntilMs = now + 6000;
        buffer.putComponent(ref, VillagerLifeState.getComponentType(), life);
        VillagerLifeState acting = life;
        buffer.run(s -> {
            if (!ref.isValid()) return;
            hold(ref, s);
            UUID id = s.getComponent(ref, UUIDComponent.getComponentType()).getUuid();
            VillagerLifeVisuals.bubble(ref, false, "Item_Food_Bread", s);
            long duration = VillagerLifeVisuals.voice(ref, id, "Groan", s);
            VillagerLifeVisuals.voice(ref, id, "Stomach", s);
            acting.emoteUntilMs = System.currentTimeMillis() + Math.max(duration, VillagerLifeTiming.durationMs("Hungry")) + 300;
        });
        return true;
    }

    private static boolean paired(Ref<EntityStore> ref, VillagerLifeState.Session session, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return false;
        VillagerLifeState life = store.getComponent(ref, VillagerLifeState.getComponentType());
        return life != null && life.session == session;
    }

    private static boolean clearSight(Vector3d a, Vector3d b, Store<EntityStore> store) {
        var world = store.getExternalData().getWorld();
        int steps = Math.max(1, (int)Math.ceil(a.distance(b) * 5));
        for (int i = 1; i < steps; i++) {
            double t = (double)i / steps;
            int x = (int)Math.floor(a.x + (b.x - a.x) * t);
            int y = (int)Math.floor(a.y + (b.y - a.y) * t + 1.4);
            int z = (int)Math.floor(a.z + (b.z - a.z) * t);
            if (!com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.isChunkInMemory(world, x, z)) return false;
            var block = com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world, x, y, z);
            if (block != null && block.getMaterial() == com.hypixel.hytale.protocol.BlockMaterial.Solid) return false;
        }
        return true;
    }

    private static void hold(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (mounted(ref, store)) return;
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        Vector3d pos = store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
        npc.setLeashPoint(new Vector3d(pos));
        NpcSupportUtil.setState(ref, AetherhavenConstants.NPC_STATE_STAND_STILL, null, store);
        NpcStandStill.forceIdleMovementStates(store, ref);
        var velocity = store.getComponent(ref, com.hypixel.hytale.server.core.modules.physics.component.Velocity.getComponentType());
        if (velocity != null) {
            velocity.setZero();
            store.putComponent(ref, com.hypixel.hytale.server.core.modules.physics.component.Velocity.getComponentType(), velocity);
        }
        npc.playAnimation(ref, AnimationSlot.Action, null, store);
        npc.playAnimation(ref, AnimationSlot.Movement, null, store);
        store.putComponent(ref, NPCEntity.getComponentType(), npc);
    }

    private static void face(Ref<EntityStore> ref, Vector3d target, Store<EntityStore> store) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d delta = new Vector3d(target).sub(tc.getPosition());
        delta.y = 0;
        Rotation3f rotation = Rotation3f.lookAt(delta);
        if (!mounted(ref, store)) {
            tc.setRotation(rotation);
            store.putComponent(ref, TransformComponent.getComponentType(), tc);
        }
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (head != null) {
            head.setRotation(rotation);
            store.putComponent(ref, HeadRotation.getComponentType(), head);
        }
    }

    private static void refill(Ref<EntityStore> ref, VillagerNeeds needs, double seconds, Store<EntityStore> store) {
        VillagerNeeds copy = (VillagerNeeds) needs.clone();
        copy.setFun(VillagerLifePolicy.refill(needs.getFun(), seconds));
        store.putComponent(ref, VillagerNeeds.getComponentType(), copy);
    }

    private void releaseEmote(Ref<EntityStore> ref, VillagerLifeState life, Store<EntityStore> store) {
        life.emoteUntilMs = 0;
        VillagerLifeProps.clear(ref, store);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        // Keep the quiet stance until autonomy selects travel. Going through Idle
        // starts a one-tick wander/leg animation between consecutive activities.
        if (npc != null && !hasPriorityActivity(ref, store))
            com.hypixel.hytale.server.core.entity.AnimationUtils.stopAnimation(ref, AnimationSlot.Action, store);
    }

    private void finish(VillagerLifeState.Session session, Ref<EntityStore> a, Ref<EntityStore> b,
                               Store<EntityStore> store, long now) {
        for (Ref<EntityStore> ref : java.util.Arrays.asList(a, b)) {
            if (!paired(ref, session, store)) continue;
            VillagerLifeState life = store.getComponent(ref, VillagerLifeState.getComponentType());
            life.session = null;
            VillagerLifeProps.clear(ref, store);
            life.conversationItemId = null;
            life.socialCooldownMs = life.nextSearchMs = now + (session.talkingSinceMs == 0 ? 5000 : VillagerLifePolicy.COOLDOWN_MS);
            life.nextEmoteMs = now + 10_000;
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            // A follow command, festival or player interaction may now own StandStill.
            // Do not release their hold while cleaning up our old conversation.
            if (npc != null && !hasPriorityActivity(ref, store)) {
                com.hypixel.hytale.server.core.entity.AnimationUtils.stopAnimation(ref, AnimationSlot.Action, store);
                NpcFaceVisuals.applyMoodFace(ref, store);
            }
        }
    }
}
