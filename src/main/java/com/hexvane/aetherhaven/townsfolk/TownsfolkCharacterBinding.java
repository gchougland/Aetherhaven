package com.hexvane.aetherhaven.townsfolk;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Per-entity townsfolk identity: catalog character, fixed personality traits, and assignment role. */
public final class TownsfolkCharacterBinding implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<TownsfolkCharacterBinding> CODEC =
        BuilderCodec.builder(TownsfolkCharacterBinding.class, TownsfolkCharacterBinding::new)
            .append(new KeyedCodec<>("CharacterId", Codec.STRING), (b, v) -> b.characterId = v != null ? v : "", b -> b.characterId)
            .add()
            .append(new KeyedCodec<>("ActivePersonalityId", Codec.STRING), (b, v) -> b.activePersonalityId = v != null ? v : "", b -> b.activePersonalityId)
            .add()
            .append(new KeyedCodec<>("AssignmentKind", Codec.STRING), (b, v) -> b.assignmentKind = v != null ? v : "", b -> b.assignmentKind)
            .add()
            .append(new KeyedCodec<>("ModelAssetId", Codec.STRING), (b, v) -> b.modelAssetId = v != null ? v : "", b -> b.modelAssetId)
            .add()
            .append(
                new KeyedCodec<>("PersonalityIdsCsv", Codec.STRING),
                (b, v) -> b.personalityIds = parseCsv(v),
                b -> String.join(",", b.personalityIds)
            )
            .documentation("Comma-separated personality ids for blended leisure scoring.")
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, TownsfolkCharacterBinding> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
        componentType = registry.registerComponent(TownsfolkCharacterBinding.class, "AetherhavenTownsfolkCharacterBinding", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, TownsfolkCharacterBinding> getComponentType() {
        ComponentType<EntityStore, TownsfolkCharacterBinding> t = componentType;
        if (t == null) {
            throw new IllegalStateException("TownsfolkCharacterBinding not registered");
        }
        return t;
    }

    private String characterId = "";
    private String activePersonalityId = "";
    private String assignmentKind = "";
    private String modelAssetId = "";
    private List<String> personalityIds = List.of();

    public TownsfolkCharacterBinding() {}

    public TownsfolkCharacterBinding(
        @Nonnull String characterId,
        @Nonnull String activePersonalityId,
        @Nonnull String assignmentKind,
        @Nonnull String modelAssetId,
        @Nonnull List<String> personalityIds
    ) {
        this.characterId = characterId;
        this.activePersonalityId = activePersonalityId;
        this.assignmentKind = assignmentKind;
        this.modelAssetId = modelAssetId;
        this.personalityIds = List.copyOf(personalityIds);
    }

    @Nonnull
    public String getCharacterId() {
        return characterId;
    }

    @Nonnull
    public String getActivePersonalityId() {
        return activePersonalityId;
    }

    @Nonnull
    public String getAssignmentKind() {
        return assignmentKind;
    }

    @Nonnull
    public String getModelAssetId() {
        return modelAssetId;
    }

    @Nonnull
    public List<String> getPersonalityIds() {
        return Collections.unmodifiableList(new ArrayList<>(personalityIds));
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new TownsfolkCharacterBinding(characterId, activePersonalityId, assignmentKind, modelAssetId, personalityIds);
    }

    @Nonnull
    private static List<String> parseCsv(@Nullable String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (part != null && !part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }
}
