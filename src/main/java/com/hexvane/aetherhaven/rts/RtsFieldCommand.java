package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * The Command Flag used in hand, away from the Guard Command Post.
 *
 * <p>At the command post the flag is one tool of a session: guards are picked on the roster, and orders end when the
 * session does. Carried around town there is no session, so the flag speaks to every guard of the holder's town that
 * is in the world, and an order stands until the holder releases the guards or leaves the world.
 *
 * <p>Every action here answers the player, whether it could act or not: a line on screen and a sound, plus the ground
 * marker and the guards themselves when it could. Until this class existed each of the flag's four inputs returned
 * early without a word whenever no command post session was open.
 */
public final class RtsFieldCommand {
    private static final String P = "aetherhaven_rts.aetherhaven.rts";

    /** Everything a flag press can come to. Each has words for the player, so no press is silent. */
    public enum Outcome {
        /** Guards were sent to the ground the holder looked at. */
        SENT(P + ".fieldSent", true),
        /** Orders now make guards fight what they meet on the way. */
        ORDER_ATTACK_MOVE(P + ".orderAttackMove", true),
        /** Orders now make guards walk straight there. */
        ORDER_MOVE(P + ".orderMove", true),
        /** Guards hold where they stand. */
        STOPPED(P + ".fieldStopped", true),
        /** Guards went back to patrols and posts. */
        RELEASED(P + ".fieldReleased", true),
        /** Tells the holder how many guards hear the flag. */
        READY(P + ".fieldReady", true),
        /** The holder belongs to no town in this world. */
        NO_TOWN(P + ".errorNoTown", false),
        /** The town has no guard in the world. */
        NO_GUARDS(P + ".errorNoGuardsToCommand", false),
        /** The holder was not looking at a block within reach. */
        NO_GROUND(P + ".errorNoGround", false),
        /** Nothing to release: no guard is following a flag order. */
        NONE_UNDER_ORDERS(P + ".errorNoneUnderOrders", false);

        private final String messageKey;
        private final boolean acted;

        Outcome(@Nonnull String messageKey, boolean acted) {
            this.messageKey = messageKey;
            this.acted = acted;
        }

        /** Translation shown to the player. It may use {@code {n}}, the number of guards. */
        @Nonnull
        public String messageKey() {
            return messageKey;
        }

        /** False when the press could not do what it was for, and the words say why. */
        public boolean acted() {
            return acted;
        }
    }

    /**
     * @param outcome what happened
     * @param guards the guards it happened to, or the guards that could have heard it
     */
    public record Result(@Nonnull Outcome outcome, int guards) {}

    /**
     * A held mouse button fires its action again and again. The same action inside this window is the same press, so
     * it is neither run nor announced a second time.
     */
    static final long REPEAT_MS = 500L;

    private record Last(@Nonnull String action, long atMs, @Nonnull Result result) {}

    private static final Map<UUID, Last> LAST = new ConcurrentHashMap<>();

    private RtsFieldCommand() {}

    /** True when {@code action} is the press already handled at {@code lastMs}, still being held. */
    static boolean repeats(@Nullable String lastAction, long lastMs, @Nonnull String action, long nowMs) {
        long since = nowMs - lastMs;
        return action.equals(lastAction) && since >= 0 && since < REPEAT_MS;
    }

    // ---- What a press comes to, from plain facts. No world needed. ----

    @Nonnull
    static Outcome decideSend(boolean hasTown, int guards, boolean hasGround) {
        if (!hasTown) {
            return Outcome.NO_TOWN;
        }
        if (guards <= 0) {
            return Outcome.NO_GUARDS;
        }
        return hasGround ? Outcome.SENT : Outcome.NO_GROUND;
    }

    @Nonnull
    static Outcome decideStop(boolean hasTown, int guards) {
        if (!hasTown) {
            return Outcome.NO_TOWN;
        }
        return guards > 0 ? Outcome.STOPPED : Outcome.NO_GUARDS;
    }

    @Nonnull
    static Outcome decideRelease(boolean hasTown, int guards, int underOrders) {
        if (!hasTown) {
            return Outcome.NO_TOWN;
        }
        if (guards <= 0) {
            return Outcome.NO_GUARDS;
        }
        return underOrders > 0 ? Outcome.RELEASED : Outcome.NONE_UNDER_ORDERS;
    }

    @Nonnull
    static Outcome decideReady(boolean hasTown, int guards) {
        if (!hasTown) {
            return Outcome.NO_TOWN;
        }
        return guards > 0 ? Outcome.READY : Outcome.NO_GUARDS;
    }

    @Nonnull
    static Outcome orderModeOutcome(@Nonnull RtsOrderMode mode) {
        return switch (mode) {
            case ATTACK_MOVE -> Outcome.ORDER_ATTACK_MOVE;
            case MOVE -> Outcome.ORDER_MOVE;
        };
    }

    // ---- The flag's actions. The held item's interactions and the server check both come through here. ----

    /**
     * Secondary: send every guard of the holder's town to the block looked at.
     *
     * @param targetBlock the block under the crosshair, or null when the holder looked at none within reach
     */
    @Nonnull
    public static Result send(
        @Nonnull Ref<EntityStore> commander,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Store<EntityStore> store,
        @Nullable Vector3i targetBlock
    ) {
        String action = targetBlock == null ? "send" : "send " + targetBlock.x + " " + targetBlock.y + " " + targetBlock.z;
        Result held = stillHeld(commander, store, action);
        if (held != null) {
            return held;
        }
        TownRecord town = townOf(commander, store);
        List<Ref<EntityStore>> guards = guardsOf(town, store);
        Outcome outcome = decideSend(town != null, guards.size(), targetBlock != null);
        int count = guards.size();
        if (outcome == Outcome.SENT && targetBlock != null) {
            RtsCommandPlayerComponent orders = ordersOf(commander, store);
            orders.setSelection(uuids(guards, store));
            // Guards stand on top of the block, in the middle of it.
            count = RtsOrderService.issueMoveOrder(
                commander,
                accessor,
                orders,
                targetBlock.x + 0.5,
                targetBlock.y + 1.0,
                targetBlock.z + 0.5
            );
            accessor.putComponent(commander, RtsCommandPlayerComponent.getComponentType(), orders);
            if (count == 0) {
                outcome = Outcome.NO_GUARDS;
            }
        }
        return remember(commander, store, action, answer(commander, accessor, new Result(outcome, count)));
    }

    /** Second tool key: every guard of the holder's town holds where it stands. */
    @Nonnull
    public static Result stop(
        @Nonnull Ref<EntityStore> commander,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Store<EntityStore> store
    ) {
        Result held = stillHeld(commander, store, "stop");
        if (held != null) {
            return held;
        }
        TownRecord town = townOf(commander, store);
        List<Ref<EntityStore>> guards = guardsOf(town, store);
        Outcome outcome = decideStop(town != null, guards.size());
        int count = guards.size();
        if (outcome == Outcome.STOPPED) {
            RtsCommandPlayerComponent orders = ordersOf(commander, store);
            orders.setSelection(uuids(guards, store));
            count = RtsOrderService.stopSelected(commander, accessor, orders);
            accessor.putComponent(commander, RtsCommandPlayerComponent.getComponentType(), orders);
            RtsMoveOrderVisuals.clearCommander(commander, store);
            if (count == 0) {
                outcome = Outcome.NO_GUARDS;
            } else {
                RtsCommandFeedback.playFreeGuards(commander, accessor);
            }
        }
        return remember(commander, store, "stop", answer(commander, accessor, new Result(outcome, count)));
    }

    /** First tool key while crouching: guards following a flag order go back to their patrols and posts. */
    @Nonnull
    public static Result release(
        @Nonnull Ref<EntityStore> commander,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Store<EntityStore> store
    ) {
        TownRecord town = townOf(commander, store);
        List<Ref<EntityStore>> guards = guardsOf(town, store);
        List<Ref<EntityStore>> ordered = new ArrayList<>();
        for (Ref<EntityStore> guard : guards) {
            GuardRtsCommandState cmd = store.getComponent(guard, GuardRtsCommandState.getComponentType());
            // An order from someone at the command post is theirs to end.
            if (cmd != null && cmd.isFieldOrder()) {
                ordered.add(guard);
            }
        }
        Outcome outcome = decideRelease(town != null, guards.size(), ordered.size());
        if (outcome == Outcome.RELEASED) {
            for (Ref<EntityStore> guard : ordered) {
                RtsCommandService.freeGuard(guard, accessor);
            }
            RtsCommandPlayerComponent orders = ordersOf(commander, store);
            orders.clearSelection();
            accessor.putComponent(commander, RtsCommandPlayerComponent.getComponentType(), orders);
            RtsMoveOrderVisuals.clearCommander(commander, store);
            RtsCommandFeedback.playFreeGuards(commander, accessor);
        }
        return answer(commander, accessor, new Result(outcome, outcome == Outcome.RELEASED ? ordered.size() : guards.size()));
    }

    /** First tool key: switch between attack move and move. The choice is the holder's and needs no guards. */
    @Nonnull
    public static Result cycleOrderMode(
        @Nonnull Ref<EntityStore> commander,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Store<EntityStore> store
    ) {
        RtsCommandPlayerComponent orders = ordersOf(commander, store);
        orders.cycleOrderMode();
        accessor.putComponent(commander, RtsCommandPlayerComponent.getComponentType(), orders);
        RtsCommandFeedback.playProfileSelect(commander, accessor);
        return answer(commander, accessor, new Result(orderModeOutcome(orders.getOrderMode()), guardsOf(townOf(commander, store), store).size()));
    }

    /** Primary: say how many guards hear the flag. Picking single guards stays a command post job. */
    @Nonnull
    public static Result ready(
        @Nonnull Ref<EntityStore> commander,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Store<EntityStore> store
    ) {
        Result held = stillHeld(commander, store, "ready");
        if (held != null) {
            return held;
        }
        TownRecord town = townOf(commander, store);
        int guards = guardsOf(town, store).size();
        Outcome outcome = decideReady(town != null, guards);
        if (outcome == Outcome.READY) {
            RtsCommandFeedback.playProfileSelect(commander, accessor);
        }
        return remember(commander, store, "ready", answer(commander, accessor, new Result(outcome, guards)));
    }

    // ---- What the HUD and the check read. ----

    /** The town whose guards the holder commands: the one they stand in if they belong to it, else their own. */
    @Nullable
    public static TownRecord townOf(@Nonnull Ref<EntityStore> commander, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || !commander.isValid()) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager towns = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        return TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, commander, towns);
    }

    /** Living guards of the town that are in the world right now. */
    @Nonnull
    public static List<Ref<EntityStore>> guardsOf(@Nullable TownRecord town, @Nonnull Store<EntityStore> store) {
        return town == null ? List.of() : RtsGuardDirectory.livingGuardRefs(town, store);
    }

    /** How many of the guards are following an order, from the flag or the command post. */
    public static int underOrders(@Nonnull List<Ref<EntityStore>> guards, @Nonnull Store<EntityStore> store) {
        int count = 0;
        for (Ref<EntityStore> guard : guards) {
            if (store.getComponent(guard, GuardRtsCommandState.getComponentType()) != null) {
                count++;
            }
        }
        return count;
    }

    /** The order mode the holder's next flag order will carry. */
    @Nonnull
    public static RtsOrderMode orderModeOf(@Nonnull Ref<EntityStore> commander, @Nonnull Store<EntityStore> store) {
        RtsCommandPlayerComponent orders = store.getComponent(commander, RtsCommandPlayerComponent.getComponentType());
        return orders != null ? orders.getOrderMode() : RtsOrderMode.ATTACK_MOVE;
    }

    /**
     * The holder's command state. A player who has never sat at the command post has none yet, so one is made; it is
     * the same state the command post uses, left inactive.
     */
    @Nonnull
    private static RtsCommandPlayerComponent ordersOf(@Nonnull Ref<EntityStore> commander, @Nonnull Store<EntityStore> store) {
        RtsCommandPlayerComponent orders = store.getComponent(commander, RtsCommandPlayerComponent.getComponentType());
        if (orders == null) {
            orders = new RtsCommandPlayerComponent();
        }
        // The stance tool belongs to the command post. A stance left over from a session must not ride along unseen.
        orders.setStanceMode(RtsCombatStance.DEFENSIVE);
        return orders;
    }

    @Nonnull
    private static List<UUID> uuids(@Nonnull List<Ref<EntityStore>> guards, @Nonnull Store<EntityStore> store) {
        List<UUID> out = new ArrayList<>(guards.size());
        for (Ref<EntityStore> guard : guards) {
            UUIDComponent id = store.getComponent(guard, UUIDComponent.getComponentType());
            if (id != null) {
                out.add(id.getUuid());
            }
        }
        return out;
    }

    @Nullable
    private static Result stillHeld(@Nonnull Ref<EntityStore> commander, @Nonnull Store<EntityStore> store, @Nonnull String action) {
        UUIDComponent id = store.getComponent(commander, UUIDComponent.getComponentType());
        Last last = id == null ? null : LAST.get(id.getUuid());
        long now = System.currentTimeMillis();
        if (last == null || !repeats(last.action(), last.atMs(), action, now)) {
            return null;
        }
        // The window slides with the hold, so a button kept down stays one press however long it is held.
        LAST.put(id.getUuid(), new Last(action, now, last.result()));
        return last.result();
    }

    @Nonnull
    private static Result remember(
        @Nonnull Ref<EntityStore> commander,
        @Nonnull Store<EntityStore> store,
        @Nonnull String action,
        @Nonnull Result result
    ) {
        UUIDComponent id = store.getComponent(commander, UUIDComponent.getComponentType());
        if (id != null) {
            LAST.put(id.getUuid(), new Last(action, System.currentTimeMillis(), result));
        }
        return result;
    }

    /** Tells the holder what happened. A commander with no client (the server check's stand-in) is told nothing. */
    @Nonnull
    private static Result answer(
        @Nonnull Ref<EntityStore> commander,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Result result
    ) {
        PlayerRef player = accessor.getComponent(commander, PlayerRef.getComponentType());
        if (player != null) {
            NotificationUtil.sendNotification(
                player.getPacketHandler(),
                Message.translation(result.outcome().messageKey()).param("n", String.valueOf(result.guards())),
                result.outcome().acted() ? NotificationStyle.Default : NotificationStyle.Warning
            );
            if (!result.outcome().acted()) {
                RtsCommandFeedback.playDenied(commander, accessor);
            }
        }
        return result;
    }
}
