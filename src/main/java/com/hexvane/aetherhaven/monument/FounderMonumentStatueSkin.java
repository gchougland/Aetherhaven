package com.hexvane.aetherhaven.monument;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Persisted copy of the placer's {@link PlayerSkin} so founder statue entities can rebuild the full cosmetics model
 * after chunk load ({@link com.hypixel.hytale.server.core.modules.entity.component.ModelComponent} is not saved).
 */
public final class FounderMonumentStatueSkin implements Component<EntityStore> {
    private static final Gson GSON = new GsonBuilder().create();

    @Nonnull
    public static final BuilderCodec<FounderMonumentStatueSkin> CODEC = BuilderCodec.builder(FounderMonumentStatueSkin.class, FounderMonumentStatueSkin::new)
        .append(new KeyedCodec<>("SkinJson", Codec.STRING), (s, v) -> s.skinJson = v != null ? v : "", x -> x.skinJson)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, FounderMonumentStatueSkin> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(
            FounderMonumentStatueSkin.class,
            "AetherhavenFounderMonumentStatueSkin",
            CODEC
        );
    }

    @Nonnull
    public static ComponentType<EntityStore, FounderMonumentStatueSkin> getComponentType() {
        ComponentType<EntityStore, FounderMonumentStatueSkin> t = componentType;
        if (t == null) {
            throw new IllegalStateException("FounderMonumentStatueSkin not registered");
        }
        return t;
    }

    private String skinJson = "";

    public FounderMonumentStatueSkin() {}

    @Nonnull
    public static FounderMonumentStatueSkin fromProtocol(@Nonnull PlayerSkin skin) {
        FounderMonumentStatueSkin c = new FounderMonumentStatueSkin();
        c.skinJson = GSON.toJson(skin);
        return c;
    }

    @Nonnull
    public PlayerSkin toProtocol() {
        if (skinJson == null || skinJson.isEmpty()) {
            return new PlayerSkin();
        }
        PlayerSkin s = GSON.fromJson(skinJson, PlayerSkin.class);
        return s != null ? s : new PlayerSkin();
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        FounderMonumentStatueSkin c = new FounderMonumentStatueSkin();
        c.skinJson = this.skinJson;
        return c;
    }
}
