package com.hexvane.aetherhaven.net;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ToServerPacket;
import com.hypixel.hytale.server.core.io.handlers.GenericPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.IPacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wraps the handler a connection already has for a packet id, so a mod can inspect or rewrite an inbound packet before
 * the server handles it, and optionally consume it.
 *
 * <p>{@code PacketAdapters.registerInbound} used to do this, but it only runs on the Netty pipeline. The Quiche
 * transport that singleplayer uses hands packets straight to {@code PacketHandler.handle}, so inbound adapters never
 * fire there. Replacing the handler on the connection itself works on every transport because it sits below all of
 * them.
 *
 * <p>The existing handler is read reflectively since {@code GenericPacketHandler} keeps its handler table private.
 * When it cannot be read the packet id is left completely alone, so a failure here costs a mod feature rather than
 * breaking vanilla handling of that packet.
 */
public final class InboundPacketInterceptor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static final Field HANDLERS_FIELD = resolveHandlersField();

    /** Inspects an inbound packet. Returns {@code true} to consume it and skip the server's handler. */
    public interface InboundFilter {
        boolean test(@Nonnull PlayerRef playerRef, @Nonnull ToServerPacket packet);
    }

    private InboundPacketInterceptor() {}

    /**
     * Installs {@code filter} in front of the handler currently registered for {@code packetId}.
     *
     * @return whether the filter was installed.
     */
    public static boolean intercept(@Nonnull IPacketHandler handler, int packetId, @Nonnull InboundFilter filter) {
        Consumer<ToServerPacket> delegate = findRegisteredHandler(handler, packetId);
        if (delegate == null) {
            LOGGER.atWarning().log("No existing handler for packet %d, skipping Aetherhaven interception", packetId);
            return false;
        }
        handler.registerHandler(packetId, packet -> {
            PlayerRef playerRef = handler.getPlayerRef();
            if (playerRef != null) {
                try {
                    if (filter.test(playerRef, packet)) {
                        return;
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().log("Aetherhaven filter for packet %d failed: %s", packetId, e.getMessage());
                }
            }
            delegate.accept(packet);
        });
        return true;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Consumer<ToServerPacket> findRegisteredHandler(@Nonnull IPacketHandler handler, int packetId) {
        Field field = HANDLERS_FIELD;
        if (field == null || !(handler instanceof GenericPacketHandler)) {
            return null;
        }
        try {
            Consumer<ToServerPacket>[] handlers = (Consumer<ToServerPacket>[]) field.get(handler);
            if (handlers == null || packetId < 0 || packetId >= handlers.length) {
                return null;
            }
            return handlers[packetId];
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Field resolveHandlersField() {
        try {
            Field field = GenericPacketHandler.class.getDeclaredField("handlers");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.atSevere().log("Cannot read the packet handler table, inbound packet features are disabled: %s", e.getMessage());
            return null;
        }
    }
}
