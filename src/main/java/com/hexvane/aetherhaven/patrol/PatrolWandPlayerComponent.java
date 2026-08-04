package com.hexvane.aetherhaven.patrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Patrol wand edit session on the player. */
public final class PatrolWandPlayerComponent implements Component<EntityStore> {
    private static final Gson GSON = new GsonBuilder().create();

    @Nonnull
    public static final BuilderCodec<PatrolWandPlayerComponent> CODEC = BuilderCodec.builder(
            PatrolWandPlayerComponent.class,
            PatrolWandPlayerComponent::new
        )
        .append(
            new KeyedCodec<>("Mode", Codec.STRING),
            (c, s) -> c.mode = parseMode(s),
            c -> c.mode.name()
        )
        .add()
        .append(
            new KeyedCodec<>("EditingRouteId", Codec.UUID_BINARY),
            (c, u) -> c.editingRouteId = u,
            c -> c.editingRouteId
        )
        .add()
        .append(
            new KeyedCodec<>("SelectedRouteId", Codec.UUID_BINARY),
            (c, u) -> c.selectedRouteId = u,
            c -> c.selectedRouteId
        )
        .add()
        .append(
            new KeyedCodec<>("NodePayload", Codec.STRING),
            (c, s) -> c.decodeNodes(s),
            c -> c.encodeNodes()
        )
        .add()
        .append(
            new KeyedCodec<>("DraftClosedLoop", Codec.BOOLEAN),
            (c, v) -> c.draftClosedLoop = v != null && v,
            c -> c.draftClosedLoop
        )
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PatrolWandPlayerComponent> componentType;

    @Nonnull
    private PatrolWandMode mode = PatrolWandMode.Build;
    @Nullable
    private UUID editingRouteId;
    @Nullable
    private UUID selectedRouteId;
    @Nonnull
    private final List<PatrolWandNode> draftNodes = new ArrayList<>();
    private boolean draftClosedLoop;

    @Nonnull
    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(
            PatrolWandPlayerComponent.class,
            "AetherhavenPatrolWand",
            PatrolWandPlayerComponent.CODEC
        );
    }

    @Nonnull
    public static ComponentType<EntityStore, PatrolWandPlayerComponent> getComponentType() {
        ComponentType<EntityStore, PatrolWandPlayerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PatrolWandPlayerComponent not registered");
        }
        return t;
    }

    @Nonnull
    public PatrolWandMode getMode() {
        return mode;
    }

    public void cycleMode() {
        mode =
            switch (mode) {
                case Build -> PatrolWandMode.Assign;
                case Assign -> PatrolWandMode.Remove;
                case Remove -> PatrolWandMode.Build;
            };
    }

    public void setMode(@Nonnull PatrolWandMode mode) {
        this.mode = mode;
    }

    /** Tab id for {@link PatrolWandStatusHud} mode tabs (visual only). */
    @Nonnull
    public String modeTabId() {
        return switch (mode) {
            case Build -> "Build";
            case Assign -> "Assign";
            case Remove -> "Remove";
        };
    }

    @Nullable
    public UUID getEditingRouteId() {
        return editingRouteId;
    }

    public void setEditingRouteId(@Nullable UUID editingRouteId) {
        this.editingRouteId = editingRouteId;
    }

    @Nullable
    public UUID getSelectedRouteId() {
        return selectedRouteId;
    }

    public void setSelectedRouteId(@Nullable UUID selectedRouteId) {
        this.selectedRouteId = selectedRouteId;
    }

    @Nonnull
    public List<PatrolWandNode> getDraftNodes() {
        return draftNodes;
    }

    public boolean isDraftClosedLoop() {
        return draftClosedLoop;
    }

    public void setDraftClosedLoop(boolean draftClosedLoop) {
        this.draftClosedLoop = draftClosedLoop;
    }

    public void toggleDraftClosedLoop() {
        draftClosedLoop = !draftClosedLoop;
    }

    public void clearDraft() {
        draftNodes.clear();
        editingRouteId = null;
    }

    /** Clears the edit session after a successful save so the next points start a fresh route. */
    public void finishSavedRoute() {
        draftNodes.clear();
        editingRouteId = null;
        selectedRouteId = null;
        draftClosedLoop = false;
    }

    /** Assign mode: pick a saved route without loading its nodes into the build draft. */
    public void selectRouteForAssign(@Nonnull UUID routeId) {
        selectedRouteId = routeId;
        editingRouteId = null;
        draftNodes.clear();
        draftClosedLoop = false;
    }

    public void startNewRoute() {
        draftNodes.clear();
        editingRouteId = null;
        selectedRouteId = null;
        draftClosedLoop = false;
    }

    public void loadFromRecord(@Nonnull PatrolRouteRecord rec) {
        draftNodes.clear();
        if (rec.nodes != null) {
            for (PatrolRouteNode n : rec.nodes) {
                if (n != null) {
                    draftNodes.add(new PatrolWandNode(UUID.randomUUID(), n.toVector()));
                }
            }
        }
        draftClosedLoop = rec.isClosedLoop();
        UUID rid = rec.getIdUuid();
        editingRouteId = rid;
        selectedRouteId = rid;
    }

    public void setDraftNodesFromList(@Nonnull List<PatrolWandNode> next) {
        draftNodes.clear();
        draftNodes.addAll(next);
    }

    @Nonnull
    private static PatrolWandMode parseMode(@Nullable String s) {
        if (s == null) {
            return PatrolWandMode.Build;
        }
        try {
            return PatrolWandMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return PatrolWandMode.Build;
        }
    }

    @Nonnull
    private String encodeNodes() {
        if (draftNodes.isEmpty()) {
            return "";
        }
        List<NodeRow> rows = new ArrayList<>();
        for (PatrolWandNode n : draftNodes) {
            NodeRow r = new NodeRow();
            r.id = n.getId().toString();
            r.x = n.getX();
            r.y = n.getY();
            r.z = n.getZ();
            rows.add(r);
        }
        return GSON.toJson(rows);
    }

    private void decodeNodes(@Nullable String payload) {
        draftNodes.clear();
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            NodeRow[] rows = GSON.fromJson(payload, NodeRow[].class);
            if (rows == null) {
                return;
            }
            for (NodeRow r : rows) {
                if (r == null || r.id == null) {
                    continue;
                }
                try {
                    UUID id = UUID.fromString(r.id);
                    draftNodes.add(new PatrolWandNode(id, new Vector3d(r.x, r.y, r.z)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static final class NodeRow {
        @SerializedName("id")
        String id;

        @SerializedName("x")
        double x;

        @SerializedName("y")
        double y;

        @SerializedName("z")
        double z;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PatrolWandPlayerComponent c = new PatrolWandPlayerComponent();
        c.mode = this.mode;
        c.editingRouteId = this.editingRouteId;
        c.selectedRouteId = this.selectedRouteId;
        c.draftNodes.addAll(this.draftNodes);
        c.draftClosedLoop = this.draftClosedLoop;
        return c;
    }
}
