package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Blocks {@link #rebuild()} and {@link #sendUpdate} after {@link #onDismiss} and when another custom page is active,
 * so scheduled world tasks cannot push UI deltas to the wrong client surface.
 */
public abstract class AetherhavenInteractiveCustomUIPage<T> extends InteractiveCustomUIPage<T> {
    private volatile boolean dismissed;
    /** Only pages that refresh by themselves keep this; see {@link #sendChanges}. */
    private PageDelta delta;

    public AetherhavenInteractiveCustomUIPage(
        @Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime, @Nonnull BuilderCodec<T> eventDataCodec
    ) {
        super(playerRef, lifetime, eventDataCodec);
    }

    protected boolean isDismissed() {
        return dismissed;
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        dismissed = true;
        super.onDismiss(ref, store);
    }

    /**
     * A page may decide to close while it is still being opened (for example when its town no longer exists).
     * {@code PageManager.openCustomPage} has already dismissed the previous page by then and has not registered this
     * one yet, so closing straight away sends the client a close it never counted and the client's acknowledgement is
     * rejected. Wait until this page is the registered one, then close it normally.
     */
    @Override
    protected void close() {
        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }
        if (playerComponent.getPageManager().getCustomPage() == this) {
            super.close();
            return;
        }
        if (dismissed) {
            return;
        }
        store.getExternalData().getWorld().execute(() -> {
            if (dismissed || !ref.isValid()) {
                return;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null && player.getPageManager().getCustomPage() == this) {
                super.close();
            }
        });
    }

    @Override
    protected void rebuild() {
        if (dismissed) {
            return;
        }
        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }
        CustomUIPage active = playerComponent.getPageManager().getCustomPage();
        if (active != this) {
            return;
        }
        super.rebuild();
    }

    /**
     * For a page that refreshes by itself. Call at the end of {@code build}: what was just built is what the client
     * receives whole, and later refreshes are compared with it.
     */
    protected final void opened(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        if (delta == null) {
            delta = new PageDelta();
        }
        delta.reset();
        delta.sent(commandBuilder.getCommands(), eventBuilder.getEvents());
    }

    /**
     * A refresh nobody asked for (a timer, a poll). Sends only the values that differ from what the client already
     * shows, and sends nothing when nothing differs. Every update sent makes the server ignore this player's presses
     * until the client answers it, so a page must never send one on a beat.
     *
     * @return true when an update was sent
     */
    protected final boolean sendChanges(@Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
        if (dismissed) {
            return false;
        }
        if (delta == null) {
            delta = new PageDelta();
        }
        PageDelta.Update update = delta.next(commandBuilder.getCommands(), eventBuilder.getEvents());
        if (update == null) {
            return false;
        }
        send(update.commands(), update.events(), false);
        return true;
    }

    @Override
    protected void sendUpdate(@Nullable UICommandBuilder commandBuilder, @Nullable UIEventBuilder eventBuilder, boolean clear) {
        if (dismissed) {
            return;
        }
        CustomUICommand[] commands = commandBuilder != null ? commandBuilder.getCommands() : UICommandBuilder.EMPTY_COMMAND_ARRAY;
        CustomUIEventBinding[] events = eventBuilder != null ? eventBuilder.getEvents() : UIEventBuilder.EMPTY_EVENT_BINDING_ARRAY;
        if (delta != null) {
            if (clear) {
                delta.reset();
            }
            delta.sent(commands, events);
        }
        send(commands, events, clear);
    }

    private void send(@Nonnull CustomUICommand[] commands, @Nonnull CustomUIEventBinding[] events, boolean clear) {
        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (dismissed || !ref.isValid()) {
                    return;
                }
                Player playerComponent = store.getComponent(ref, Player.getComponentType());
                if (playerComponent == null) {
                    return;
                }
                if (playerComponent.getPageManager().getCustomPage() != this) {
                    return;
                }
                playerComponent.getPageManager()
                    .updateLegacyCustomPage(
                        new CustomPage(
                            this.getClass().getName(),
                            false,
                            clear,
                            this.lifetime,
                            commands,
                            events
                        )
                    );
            }
        );
    }
}
