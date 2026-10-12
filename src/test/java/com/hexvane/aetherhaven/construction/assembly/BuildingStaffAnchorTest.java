package com.hexvane.aetherhaven.construction.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Everything that is meant to come out of the Building Staff leaves it from one point, the tip of its model.
 *
 * <p>The mint stars once came from a place the server worked out near the player's chest while the cone hung on the
 * staff, so the two flowed from different points in both views. What is held now hangs on the staff by one
 * attachment, and what the server must still place in the world (the guide bolt, the bead line) starts from one
 * estimate of that same tip.
 */
@Tag("construction")
class BuildingStaffAnchorTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path ITEMS = RESOURCES.resolve("Server/Item/Items/Aetherhaven");
    private static final Path PARTICLES = RESOURCES.resolve("Server/Particles/Aetherhaven");
    private static final Path SECONDARY = RESOURCES.resolve(
        "Server/Item/Interactions/Aetherhaven/" + BuildingStaffEffects.HELD_INTERACTION_ID + ".json");
    private static final Path SECONDARY_ROOT =
        RESOURCES.resolve("Server/Item/RootInteractions/Aetherhaven/Aetherhaven_BuildingStaff_Secondary_Root.json");
    private static final Path ASSEMBLY_SOURCES = Path.of("src/main/java/com/hexvane/aetherhaven/construction/assembly");
    private static final String TIP_NODE = "Origin_Projectile";
    /** A held item's model has 64 units to the block, like the character that holds it. */
    private static final double UNITS_PER_BLOCK = 64.0;

    @Test
    void theHeldEffectsHangOnTheTipOfTheStaffModel() throws IOException {
        JsonObject effects = read(SECONDARY).getAsJsonObject("Effects");
        JsonArray attached = effects.getAsJsonArray("Particles");
        assertEquals(1, attached.size(), "one attachment, so cone and stars cannot drift apart");
        assertFalse(effects.has("FirstPersonParticles"), "first person must use the same attachment as third person");
        JsonObject anchor = attached.get(0).getAsJsonObject();
        assertEquals(BuildingStaffEffects.HELD_SYSTEM_ID, anchor.get("SystemId").getAsString());
        assertEquals(BuildingStaffEffects.HELD_ANCHOR_NODE, anchor.get("TargetNodeName").getAsString());
        assertFalse(anchor.has("TargetEntityPart"), "as the base game's ice staff does it: the node is looked up on the held item");
        assertFalse(anchor.has("DetachedFromModel") && anchor.get("DetachedFromModel").getAsBoolean(), "it must follow the staff");

        // Along the handle, as far as the model's own tip node: the base game's ice staff is anchored the same way
        // (Handle, X 1.5, Yaw -90 on a model whose top is 1.51 blocks along its handle).
        StaffModel model = StaffModel.load(staffModelPath());
        double tipAlongHandle = model.alongHandle(model.pivot(TIP_NODE)) / UNITS_PER_BLOCK;
        JsonObject offset = anchor.getAsJsonObject("PositionOffset");
        assertEquals(tipAlongHandle, offset.get("X").getAsDouble(), 0.01, "PositionOffset.X is blocks along the handle to " + TIP_NODE);
        assertEquals(0.0, number(offset, "Y"), 1.0e-6);
        assertEquals(0.0, number(offset, "Z"), 1.0e-6);
        assertEquals(tipAlongHandle, BuildingStaffTip.TIP_REACH_BLOCKS, 0.01, "the server's estimate reaches the same node");

        // The handle's +X runs up the staff, and the system's forward (-Z) is turned onto it by Yaw -90.
        JsonObject rotation = anchor.getAsJsonObject("RotationOffset");
        assertEquals(-90.0, number(rotation, "Yaw"), 1.0e-6);
        assertEquals(0.0, number(rotation, "Pitch"), 1.0e-6);
        assertEquals(0.0, number(rotation, "Roll"), 1.0e-6);

        // That node is the visible tip: on the staff's axis, inside the topmost piece of the model.
        Vector3d tip = model.pivot(TIP_NODE);
        assertEquals(0.0, model.offAxis(tip), 0.5, TIP_NODE + " sits on the handle's axis");
        double top = model.topAlongHandle() / UNITS_PER_BLOCK;
        assertTrue(tipAlongHandle <= top && top - tipAlongHandle < 0.125,
            TIP_NODE + " is " + tipAlongHandle + " blocks up the handle; the model ends at " + top);
    }

    @Test
    void coneAndStarsStartAtTheAnchorItself() throws IOException {
        JsonObject system = read(PARTICLES.resolve(BuildingStaffEffects.HELD_SYSTEM_ID + ".particlesystem"));
        List<String> spawnerIds = new ArrayList<>();
        int stars = 0;
        int others = 0;
        for (JsonElement element : system.getAsJsonArray("Spawners")) {
            JsonObject entry = element.getAsJsonObject();
            String id = entry.get("SpawnerId").getAsString();
            spawnerIds.add(id);
            if (entry.has("PositionOffset")) {
                JsonObject moved = entry.getAsJsonObject("PositionOffset");
                assertEquals(0.0, Math.abs(number(moved, "X")) + Math.abs(number(moved, "Y")) + Math.abs(number(moved, "Z")), 1.0e-6,
                    id + " must start at the anchor, not beside it");
            }
            assertFalse(entry.has("StartDelay"), id + " must start with the others");
            JsonObject spawner = read(PARTICLES.resolve(id + ".particlespawner"));
            String texture = spawner.getAsJsonObject("Particle").get("Texture").getAsString();
            if (texture.contains("Star")) {
                stars++;
            } else {
                others++;
            }
            // Particles leave the staff and stay where they were shed, the same for all three.
            assertEquals(1.0, number(spawner, "TrailSpawnerPositionMultiplier"), 1.0e-6, id);
            assertTrue(spawner.get("ParticleRotateWithSpawner").getAsBoolean(), id);
            // All fly forward along the system's -Z: yaw and pitch ranges centred on zero.
            JsonObject velocity = spawner.getAsJsonObject("InitialVelocity");
            assertEquals(0.0, number(velocity.getAsJsonObject("Yaw"), "Min") + number(velocity.getAsJsonObject("Yaw"), "Max"), 1.0e-6, id);
            assertEquals(0.0, number(velocity.getAsJsonObject("Pitch"), "Min") + number(velocity.getAsJsonObject("Pitch"), "Max"), 1.0e-6, id);
        }
        assertEquals(List.of(
            "Aetherhaven_Building_Staff_ConduitStream",
            "Aetherhaven_Building_Staff_ConduitAccent",
            "Aetherhaven_Building_Staff_ConduitMist"), spawnerIds);
        assertEquals(2, stars, "violet accent stars and mint tip stars");
        assertEquals(1, others, "the orb cone");
    }

    @Test
    void everyStaffTierHoldsTheSameModelAndTheSameSecondaryAction() throws IOException {
        Set<String> models = new HashSet<>();
        int staffs = 0;
        try (Stream<Path> files = Files.list(ITEMS)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("Aetherhaven_Building_Staff")).toList()) {
                JsonObject item = read(file);
                staffs++;
                models.add(item.get("Model").getAsString());
                assertEquals("Aetherhaven_BuildingStaff_Secondary_Root",
                    item.getAsJsonObject("Interactions").get("Secondary").getAsString(), file.toString());
                assertFalse(item.has("Particles") || item.has("FirstPersonParticles"), file + " adds an effect with an anchor of its own");
            }
        }
        assertEquals(5, staffs, "copper, iron, thorium, cobalt and adamantite staffs");
        assertEquals(1, models.size(), "one model, so one tip: " + models);
        JsonArray steps = read(SECONDARY_ROOT).getAsJsonArray("Interactions");
        assertEquals(1, steps.size());
        assertEquals(BuildingStaffEffects.HELD_INTERACTION_ID, steps.get(0).getAsString());
    }

    @Test
    void whatTheServerPlacesInTheWorldStartsFromTheOneTipEstimate() throws IOException {
        // The guide bolt and the bead line need a world position; both must take it from BuildingStaffTip.
        Map<String, String> sources = new HashMap<>();
        try (Stream<Path> files = Files.list(ASSEMBLY_SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                sources.put(file.getFileName().toString(), Files.readString(file));
            }
        }
        assertTrue(sources.get("BuildingStaffFrontierTracerInteraction.java").contains("BuildingStaffTip.estimate("));
        assertTrue(sources.get("BuildingStaffAssemblyChannelExecutor.java").contains("BuildingStaffTip.estimate("));
        assertFalse(sources.get("BuildingStaffSecondaryInteraction.java").contains("spawnParticleEffect"));
        // No second idea of where the tip is: the old chest-height point survives only as the centre of the work search.
        int chestPoints = 0;
        for (Map.Entry<String, String> source : sources.entrySet()) {
            int at = source.getValue().indexOf("1.32");
            while (at >= 0) {
                chestPoints++;
                assertEquals("BuildingStaffFrontierTracerInteraction.java", source.getKey());
                at = source.getValue().indexOf("1.32", at + 1);
            }
        }
        assertEquals(1, chestPoints);
        assertTrue(sources.get("BuildingStaffFrontierTracerInteraction.java").contains("Vector3d searchOrigin(")) ;
    }

    private static Path staffModelPath() throws IOException {
        String model = read(ITEMS.resolve("Aetherhaven_Building_Staff.json")).get("Model").getAsString();
        return RESOURCES.resolve("Common").resolve(model);
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static double number(JsonObject object, String key) {
        return object != null && object.has(key) ? object.get(key).getAsDouble() : 0.0;
    }

    /**
     * The staff model, read the way the game lays a model out: a node's position is measured from the centre of its
     * parent's shape (the parent's pivot plus its shape offset), in the parent's frame.
     */
    private static final class StaffModel {
        private final Map<String, Vector3d> pivots = new HashMap<>();
        private final Map<String, Quaterniond> rotations = new HashMap<>();
        private final List<Vector3d> corners = new ArrayList<>();

        static StaffModel load(Path file) throws IOException {
            StaffModel model = new StaffModel();
            for (JsonElement root : read(file).getAsJsonArray("nodes")) {
                model.walk(root.getAsJsonObject(), new Vector3d(), new Quaterniond());
            }
            assertNotNull(model.pivots.get(BuildingStaffEffects.HELD_ANCHOR_NODE), "the staff model has no Handle node");
            assertNotNull(model.pivots.get(TIP_NODE), "the staff model has no " + TIP_NODE + " node");
            return model;
        }

        private void walk(JsonObject node, Vector3d parentCentre, Quaterniond parentRotation) {
            Vector3d pivot = parentRotation.transform(vector(node.getAsJsonObject("position"), 0.0)).add(parentCentre);
            JsonObject o = node.getAsJsonObject("orientation");
            Quaterniond rotation = new Quaterniond(parentRotation);
            if (o != null) {
                rotation.mul(new Quaterniond(number(o, "x"), number(o, "y"), number(o, "z"), o.has("w") ? o.get("w").getAsDouble() : 1.0));
            }
            JsonObject shape = node.getAsJsonObject("shape");
            Vector3d centre = new Vector3d(pivot);
            if (shape != null) {
                centre.add(rotation.transform(vector(shape.getAsJsonObject("offset"), 0.0)));
                JsonObject settings = shape.getAsJsonObject("settings");
                JsonObject size = settings == null ? null : settings.getAsJsonObject("size");
                if (size != null && "box".equals(shape.get("type").getAsString())) {
                    Vector3d stretch = vector(shape.getAsJsonObject("stretch"), 1.0);
                    for (int i = 0; i < 8; i++) {
                        Vector3d corner = new Vector3d(
                            ((i & 1) == 0 ? -0.5 : 0.5) * number(size, "x") * stretch.x,
                            ((i & 2) == 0 ? -0.5 : 0.5) * number(size, "y") * stretch.y,
                            ((i & 4) == 0 ? -0.5 : 0.5) * number(size, "z") * stretch.z);
                        corners.add(rotation.transform(corner).add(centre));
                    }
                }
            }
            String name = node.get("name").getAsString();
            pivots.putIfAbsent(name, pivot);
            rotations.putIfAbsent(name, rotation);
            JsonArray children = node.getAsJsonArray("children");
            if (children != null) {
                for (JsonElement child : children) {
                    walk(child.getAsJsonObject(), centre, rotation);
                }
            }
        }

        Vector3d pivot(String node) {
            return new Vector3d(pivots.get(node));
        }

        private Vector3d handleAxis() {
            return rotations.get(BuildingStaffEffects.HELD_ANCHOR_NODE).transform(new Vector3d(1.0, 0.0, 0.0));
        }

        /** Model units from the handle's origin along its +X axis. */
        double alongHandle(Vector3d point) {
            return new Vector3d(point).sub(pivots.get(BuildingStaffEffects.HELD_ANCHOR_NODE)).dot(handleAxis());
        }

        /** Model units from the handle's axis. */
        double offAxis(Vector3d point) {
            Vector3d fromHandle = new Vector3d(point).sub(pivots.get(BuildingStaffEffects.HELD_ANCHOR_NODE));
            return fromHandle.sub(handleAxis().mul(fromHandle.dot(handleAxis()))).length();
        }

        double topAlongHandle() {
            double top = Double.NEGATIVE_INFINITY;
            for (Vector3d corner : corners) {
                top = Math.max(top, alongHandle(corner));
            }
            return top;
        }

        private static Vector3d vector(JsonObject value, double fallback) {
            if (value == null) {
                return new Vector3d(fallback);
            }
            return new Vector3d(
                value.has("x") ? value.get("x").getAsDouble() : fallback,
                value.has("y") ? value.get("y").getAsDouble() : fallback,
                value.has("z") ? value.get("z").getAsDouble() : fallback);
        }
    }
}
