package com.hexvane.aetherhaven.hunter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The hunt portal, the Hunter's mark and the creature's first step turn with the Hunter's Lodge.
 *
 * <p>Each test places the shipped lodge at an anchor in all four rotations the way a plot does (anchor plus the turned
 * prefab offset), takes the layout a run would use, and turns the answers back into the lodge's own cells. The same
 * cells must come back whatever the rotation.
 */
@Tag("town")
class HuntArenaLayoutTest {
    private static final Path PREFAB = Path.of("src/main/resources/Server/Prefabs/Hunter_Lodge.prefab.json");
    private static final List<Rotation> TURNS = List.of(Rotation.None, Rotation.Ninety, Rotation.OneEighty, Rotation.TwoSeventy);
    private static final Vector3i ANCHOR = new Vector3i(1200, 64, -340);

    private record Lodge(Map<Vector3i, String> cells, Vector3i beacon) {
        String at(int x, int y, int z) {
            return cells.getOrDefault(new Vector3i(x, y, z), "Outside");
        }
    }

    private static Lodge lodge() throws Exception {
        JsonObject prefab = JsonParser.parseString(Files.readString(PREFAB)).getAsJsonObject();
        Map<Vector3i, String> cells = new HashMap<>();
        Vector3i beacon = null;
        for (var element : prefab.getAsJsonArray("blocks")) {
            JsonObject block = element.getAsJsonObject();
            Vector3i at = new Vector3i(block.get("x").getAsInt(), block.get("y").getAsInt(), block.get("z").getAsInt());
            String name = block.get("name").getAsString();
            cells.put(at, name);
            if (HuntService.BEACON.equals(name)) {
                assertNull(beacon, "the lodge has one Hunt Beacon");
                beacon = at;
            }
        }
        assertNotNull(beacon, "the lodge has a Hunt Beacon");
        return new Lodge(cells, beacon);
    }

    /** The standing point HuntService.findBeacon gives for a beacon block: its centre, one block up. */
    private static Vector3d beaconPoint(Lodge lodge, Rotation turn) {
        Vector3i block = new Vector3i(ANCHOR).add(PrefabLocalOffset.rotate(turn, lodge.beacon().x, lodge.beacon().y, lodge.beacon().z));
        return new Vector3d(block.x + .5, block.y + 1, block.z + .5);
    }

    /** The lodge's own cell under a world point. */
    private static Vector3i lodgeCell(Vector3d world, Rotation turn) {
        int dx = (int) Math.floor(world.x) - ANCHOR.x, dy = (int) Math.floor(world.y) - ANCHOR.y, dz = (int) Math.floor(world.z) - ANCHOR.z;
        return PrefabLocalOffset.inverseRotateWorldDelta(turn, dx, dy, dz);
    }

    @Test
    void anUnturnedLodgeKeepsTheLayoutItHasAlwaysHad() {
        Vector3d beacon = new Vector3d(10.5, 65, 20.5);
        HuntArenaLayout layout = HuntArenaLayout.of(beacon, Rotation.None);
        assertEquals(new Vector3d(10.5, 65, 17.5), layout.hunterStand());
        assertEquals(new Vector3d(10.5, 65, 24.5), layout.portal());
        assertEquals(new Vector3d(10.5, 65, 23.5), layout.creatureSpawn());
        assertEquals(0f, layout.yawTowardBeacon(), 0f);
        assertEquals(0f, layout.facing().yaw(), 0f);
    }

    @Test
    void everyRotationPutsThePiecesOnTheSameCellsOfTheLodge() throws Exception {
        Lodge lodge = lodge();
        Vector3i b = lodge.beacon();
        Set<Vector3d> worldPortals = new HashSet<>();
        for (Rotation turn : TURNS) {
            Vector3d beacon = beaconPoint(lodge, turn);
            HuntArenaLayout layout = HuntArenaLayout.of(beacon, turn);
            // Feet are one block above the beacon block, so the lodge cell is the beacon's with y + 1.
            assertEquals(new Vector3i(b.x, b.y + 1, b.z + HuntArenaLayout.PORTAL_LOCAL_Z), lodgeCell(layout.portal(), turn), turn + " portal");
            assertEquals(new Vector3i(b.x, b.y + 1, b.z + HuntArenaLayout.HUNTER_LOCAL_Z), lodgeCell(layout.hunterStand(), turn), turn + " Hunter");
            assertEquals(new Vector3i(b.x, b.y + 1, b.z + HuntArenaLayout.CREATURE_LOCAL_Z), lodgeCell(layout.creatureSpawn(), turn), turn + " creature");
            worldPortals.add(new Vector3d(layout.portal()).sub(beacon));
        }
        assertEquals(4, worldPortals.size(), "four rotations give four different places in the world");
    }

    @Test
    void thePortalAndTheCreatureLookAtTheBeaconInEveryRotation() throws Exception {
        Lodge lodge = lodge();
        for (Rotation turn : TURNS) {
            Vector3d beacon = beaconPoint(lodge, turn);
            HuntArenaLayout layout = HuntArenaLayout.of(beacon, turn);
            double yaw = layout.facing().yaw();
            // The game's forward for a body yaw (TriggerVolumeTestCommand, FrontFacing.lookCardinalFromYaw).
            double forwardX = -Math.sin(yaw), forwardZ = -Math.cos(yaw);
            Vector3d toBeacon = new Vector3d(beacon).sub(layout.portal());
            assertEquals(HuntArenaLayout.PORTAL_LOCAL_Z, toBeacon.length(), 1e-9, turn + " distance");
            assertEquals(1.0, (forwardX * toBeacon.x + forwardZ * toBeacon.z) / toBeacon.length(), 1e-6, turn + " portal looks at the beacon");
            // The Hunter waits on the far side of the beacon, the creature steps out between portal and beacon.
            Vector3d toHunter = new Vector3d(layout.hunterStand()).sub(beacon);
            assertEquals(Math.abs(HuntArenaLayout.HUNTER_LOCAL_Z), forwardX * toHunter.x + forwardZ * toHunter.z, 1e-6, turn + " Hunter is beyond the beacon");
            assertEquals(1.0, layout.creatureSpawn().distance(layout.portal()), 1e-9, turn + " creature is one block in front");
            assertEquals(HuntArenaLayout.CREATURE_LOCAL_Z, layout.creatureSpawn().distance(beacon), 1e-9, turn + " creature is on the beacon side");
            assertEquals(0f, layout.facing().pitch(), 0f);
            assertEquals(0f, layout.facing().roll(), 0f);
        }
    }

    @Test
    void theShippedLodgeHasOpenFloorUnderEveryPieceInEveryRotation() throws Exception {
        Lodge lodge = lodge();
        for (Rotation turn : TURNS) {
            HuntArenaLayout layout = HuntArenaLayout.of(beaconPoint(lodge, turn), turn);
            for (var piece : Map.of("portal", layout.portal(), "Hunter", layout.hunterStand(), "creature", layout.creatureSpawn()).entrySet()) {
                Vector3i cell = lodgeCell(piece.getValue(), turn);
                String where = turn + " " + piece.getKey() + " at lodge cell " + cell;
                assertNotEquals("Empty", lodge.at(cell.x, cell.y - 1, cell.z), where + " floor");
                assertNotEquals("Outside", lodge.at(cell.x, cell.y - 1, cell.z), where + " floor");
                for (int up = 0; up < 3; up++) {
                    assertEquals("Empty", lodge.at(cell.x, cell.y + up, cell.z), where + " room at +" + up);
                }
            }
            // The portal is three blocks wide across the lodge's X axis and three and a half tall.
            Vector3i portal = lodgeCell(layout.portal(), turn);
            for (int side = -1; side <= 1; side++) {
                for (int up = 0; up < 4; up++) {
                    assertEquals("Empty", lodge.at(portal.x + side, portal.y + up, portal.z), turn + " portal frame " + side + "," + up);
                }
            }
        }
    }

    @Test
    void fixedWorldOffsetsWouldMoveThePortalAcrossAQuarterTurnedLodge() throws Exception {
        // What the run did before: always four blocks to world +Z of the beacon. On the lodge's own grid that is a
        // different cell for every rotation, which is why the portal did not follow the building.
        Lodge lodge = lodge();
        Vector3i b = lodge.beacon();
        Map<Rotation, Vector3i> old = new HashMap<>();
        for (Rotation turn : TURNS) {
            old.put(turn, lodgeCell(new Vector3d(beaconPoint(lodge, turn)).add(0, 0, 4), turn));
        }
        assertEquals(new Vector3i(b.x, b.y + 1, b.z + 4), old.get(Rotation.None));
        assertEquals(new Vector3i(b.x, b.y + 1, b.z - 4), old.get(Rotation.OneEighty));
        assertEquals(4, new HashSet<>(old.values()).size());
        for (Rotation quarter : List.of(Rotation.Ninety, Rotation.TwoSeventy)) {
            assertEquals(b.z, old.get(quarter).z, quarter + " put the portal on the lodge's long axis");
            assertEquals(4, Math.abs(old.get(quarter).x - b.x), quarter.name());
        }
    }
}
