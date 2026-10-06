package com.hexvane.aetherhaven.patrol;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

@Tag("entity")
class GuardEscortOrdersTest {
    @Test void nativeGuardRolesCannotBypassEscortOrders() throws Exception {
        var root = java.nio.file.Path.of("src/main/resources/Server/NPC/Roles/Aetherhaven");
        for (String kind : new String[]{"Knight", "Archer", "Mage", "Rogue"}) {
            var role = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("Aetherhaven_Guard_" + kind + ".json")));
            assertTrue(role.toString().contains("AetherhavenGuardCombatAllowed"), kind + " must validate combat targets");
            assertMobGates(role, false);
        }
        for (String step : new String[]{"Idle_Aggro", "Damage_Aggro"}) {
            var data = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("Components/Steps/Component_Instruction_Aetherhaven_Guard_" + step + ".json"))).getAsJsonObject();
            var sensors = data.getAsJsonObject("Content").getAsJsonObject("Sensor").getAsJsonArray("Sensors");
            assertEquals("AetherhavenGuardAutomaticAggro", sensors.get(0).getAsJsonObject().get("Type").getAsString());
        }
    }

    private static void assertMobGates(com.google.gson.JsonElement node, boolean gated) {
        if (node.isJsonArray()) {
            for (var child : node.getAsJsonArray()) assertMobGates(child, gated);
        } else if (node.isJsonObject()) {
            var obj = node.getAsJsonObject();
            if (obj.has("Type") && "Mob".equals(obj.get("Type").getAsString())) assertTrue(gated, "Native acquisition must not override Defensive");
            boolean childGate = gated || (obj.has("Type") && "And".equals(obj.get("Type").getAsString())
                && obj.has("Sensors") && obj.getAsJsonArray("Sensors").get(0).getAsJsonObject().has("Type")
                && "AetherhavenGuardAutomaticAggro".equals(obj.getAsJsonArray("Sensors").get(0).getAsJsonObject().get("Type").getAsString()));
            for (var entry : obj.entrySet()) assertMobGates(entry.getValue(), childGate);
        }
    }

    @Test void defensiveDoesNotStartFightsOrInheritAnAlliesAggression() {
        assertFalse(GuardEscortStance.DEFENSIVE.permits(false, true, false, true));
        assertFalse(GuardEscortStance.DEFENSIVE.permits(false, true, false, false));
        assertTrue(GuardEscortStance.DEFENSIVE.permits(false, true, true, false));
        assertTrue(GuardEscortStance.OFFENSIVE.permits(false, true, false, true));
        assertFalse(GuardEscortStance.OFFENSIVE.permits(false, true, false, false));
        for (var stance : GuardEscortStance.values()) {
            assertFalse(stance.permits(false, false, true, true), "Friendly exclusions always win");
            assertFalse(stance.permits(true, true, true, true), "Wait must not acquire a chase target");
        }
    }

    @Test void waitingRetainsOwnerAndCapacityWhileResumeRetainsStance() {
        UUID owner = UUID.randomUUID();
        var state = new GuardFollowPlayerState();
        state.startFollowing(owner);
        state.setStance(GuardEscortStance.DEFENSIVE);
        state.waitHere(12, 70, -9);
        assertTrue(state.isFollowing(owner));
        assertFalse(state.isFollowing(UUID.randomUUID()));
        var clone = (GuardFollowPlayerState) state.clone();
        state.resumeFollowing();
        assertTrue(clone.isWaiting());
        assertEquals(12, clone.getWaitX());
        assertEquals(70, clone.getWaitY());
        assertEquals(-9, clone.getWaitZ());
        assertFalse(state.isWaiting());
        assertEquals(GuardEscortStance.DEFENSIVE, state.getStance());
        state.clear();
        assertFalse(state.isActive());
        assertNull(state.getPlayerUuid());
        assertFalse(state.isWaiting());
    }

    @Test void savedAndLegacyEscortStatesDecodeWithoutChangingOwner() {
        UUID owner = UUID.randomUUID();
        String oldSave = "{\"Active\":true,\"PlayerUuid\":\"" + owner + "\"}";
        var old = GuardFollowPlayerState.CODEC.decode(BsonDocument.parse(oldSave), new ExtraInfo());
        assertTrue(old.isFollowing(owner));
        assertEquals(GuardEscortStance.OFFENSIVE, old.getStance());
        assertFalse(old.isWaiting());
        String currentSave = oldSave.substring(0, oldSave.length() - 1)
            + ",\"Stance\":\"DEFENSIVE\",\"Waiting\":true,\"WaitX\":1.5,\"WaitY\":72.0,\"WaitZ\":-8.5}";
        var current = GuardFollowPlayerState.CODEC.decode(BsonDocument.parse(currentSave), new ExtraInfo());
        assertTrue(current.isFollowing(owner));
        assertTrue(current.isWaiting());
        assertEquals(GuardEscortStance.DEFENSIVE, current.getStance());
        assertEquals(-8.5, current.getWaitZ());
    }

    @Test void invalidWaitDoesNotCorruptEscort() {
        var state = new GuardFollowPlayerState();
        assertThrows(IllegalArgumentException.class, () -> state.waitHere(1, 2, 3));
        state.startFollowing(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> state.waitHere(Double.NaN, 2, 3));
        assertFalse(state.isWaiting());
    }

    @Test void retaliationMemoryIsSpecificToTheVictimAndExpires() {
        var memory = new GuardNpcAttackerMemory();
        UUID protectedPlayer = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        var attacker = new Ref<EntityStore>(null, 17);
        memory.markPlayerAttacker(protectedPlayer, attacker, 100);
        assertTrue(memory.attackedPlayer(protectedPlayer, attacker, 101));
        assertFalse(memory.attackedPlayer(bystander, attacker, 101));
        var copy = (GuardNpcAttackerMemory) memory.clone();
        assertFalse(memory.attackedPlayer(protectedPlayer, attacker, 100 + GuardNpcAttackerMemory.MEMORY_MS));
        assertTrue(copy.attackedPlayer(protectedPlayer, attacker, 101), "World-state clones must own their maps");
        assertFalse(copy.attackedPlayer(protectedPlayer, new Ref<EntityStore>(null, 18), 101));
    }
}
