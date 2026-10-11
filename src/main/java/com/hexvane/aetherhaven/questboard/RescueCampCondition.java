package com.hexvane.aetherhaven.questboard;

import com.hypixel.hytale.builtin.adventure.worldevents.condition.EventCondition;
import com.hypixel.hytale.builtin.adventure.worldevents.context.ContextKey;
import com.hypixel.hytale.builtin.adventure.worldevents.context.LocationContext;
import com.hypixel.hytale.builtin.adventure.worldevents.context.PrefabPasteContext;
import com.hypixel.hytale.builtin.adventure.worldevents.event.Validator;
import com.hypixel.hytale.builtin.adventure.worldevents.event.WorldEvent;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * World event condition that hands a rescue contract's chosen camp site to the event.
 *
 * <p>The base game only lets a world event pick its own place near a random player. A rescue camp must stand where
 * the contract says, so the contract registers the site under the event's id before starting the event, and this
 * condition writes it into the event's context for the ordinary {@code PrefabPasteAction} to use.
 */
public final class RescueCampCondition implements EventCondition {
    public static final String ID = "AetherhavenRescueCampSite";
    public static final Type TYPE = Type.nonTicking(ID);
    public static final BuilderCodec<RescueCampCondition> CODEC =
        BuilderCodec.builder(RescueCampCondition.class, RescueCampCondition::new).build();

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, Site> SITES = new ConcurrentHashMap<>();

    /** A prefab path inside the asset packs, the block it is pasted at, and how it is turned. */
    public record Site(@Nonnull Path prefab, @Nonnull Vector3i origin, @Nonnull PrefabRotation rotation) {}

    public static void register(@Nonnull UUID eventId, @Nonnull Site site) {
        SITES.put(eventId, site);
    }

    public static void forget(@Nonnull UUID eventId) {
        SITES.remove(eventId);
    }

    @Nonnull
    private Config config = Config.EMPTY;
    @Nullable
    private Site site;

    @Override
    public boolean test() {
        return site != null;
    }

    @Override
    public void reset() {
        site = null;
    }

    @Override
    public boolean onStart(@Nonnull WorldEvent event) {
        site = SITES.get(event.uuid);
        if (site == null) {
            LOGGER.atWarning().log("Rescue camp event %s has no registered site", event.uuid);
            return false;
        }
        return true;
    }

    @Override
    public boolean onComplete(@Nonnull WorldEvent event) {
        if (site == null || config.prefabKey == null) {
            return false;
        }
        event.context.put(config.prefabKey, new PrefabPasteContext(site.prefab(), new Vector3i(site.origin()), site.rotation()));
        event.context.put(config.prefabKey, new LocationContext(new Vector3i(site.origin())));
        SITES.remove(event.uuid);
        return true;
    }

    public static final class Config implements EventCondition.Config {
        public static final BuilderCodec<Config> CODEC = BuilderCodec.builder(Config.class, Config::new)
            .documentation("Completes once an Aetherhaven rescue contract has supplied the camp site for this event.")
            .append(new KeyedCodec<>("PrefabKey", ContextKey.CODEC), (self, value) -> self.prefabKey = value, self -> self.prefabKey)
            .documentation("Context key the camp prefab and its location are written under.")
            .addValidator(Validators.nonNull())
            .add()
            .build();

        private static final Config EMPTY = new Config();

        @Nullable
        private ContextKey prefabKey;

        @Nonnull
        @Override
        public EventCondition create() {
            RescueCampCondition condition = new RescueCampCondition();
            condition.config = this;
            return condition;
        }

        @Override
        public void validate(@Nonnull Validator validator) {
            if (prefabKey == null) {
                validator.addErrorMessage("AetherhavenRescueCampSite requires a PrefabKey");
                return;
            }
            validator.addDefinedKey(ID, "PrefabKey", prefabKey);
        }
    }
}
