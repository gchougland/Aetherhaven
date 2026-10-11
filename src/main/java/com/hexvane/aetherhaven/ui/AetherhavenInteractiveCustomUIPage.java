package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
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

    @Override
    protected void sendUpdate(@Nullable UICommandBuilder commandBuilder, @Nullable UIEventBuilder eventBuilder, boolean clear) {
        if (dismissed) {
            return;
        }
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
                            commandBuilder != null ? commandBuilder.getCommands() : UICommandBuilder.EMPTY_COMMAND_ARRAY,
                            eventBuilder != null ? eventBuilder.getEvents() : UIEventBuilder.EMPTY_EVENT_BINDING_ARRAY
                        )
                    );
            }
        );
    }
}
