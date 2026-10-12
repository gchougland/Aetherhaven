package com.hexvane.aetherhaven.patrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.patrol.GuardThreatRule.Creature;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Town guards killed other mods' pets and anything a player or plugin placed near them, because they chose targets by
 * the base game's Aggressive role group alone. These tests hold the rule that replaced it.
 */
@Tag("entity")
class GuardThreatRuleTest {
    /** The rule the released mod used: a hostile kind of creature, or a raid role, whoever it belonged to. */
    private static boolean released(Creature creature) {
        return !creature.townsOwn() && (creature.raidEnemy() || creature.hostileKind());
    }

    // townsOwn, raidEnemy, arenaCreature, spared, wildSpawn, hostileKind
    private static final Creature SUMMONED_PET_BEAR = new Creature(false, false, false, false, false, true);
    private static final Creature WILD_BEAR = new Creature(false, false, false, false, true, true);
    private static final Creature WILD_SHEEP = new Creature(false, false, false, false, true, false);
    private static final Creature PLACED_SHEEP = new Creature(false, false, false, false, false, false);
    private static final Creature RAIDER = new Creature(false, true, false, false, false, true);
    private static final Creature ARENA_WOLF = new Creature(false, false, true, false, false, true);
    private static final Creature WILD_BEAR_A_PACK_SPARED = new Creature(false, false, false, true, true, true);
    private static final Creature GUARD = new Creature(true, false, false, false, false, false);

    @Test
    void aPetOrSummonOfAHostileKindIsLeftAlone() {
        // The reported fault: another mod's tamed bear keeps the role Bear_Grizzly and was attacked on sight.
        assertTrue(released(SUMMONED_PET_BEAR), "the released rule attacked a summoned bear");
        assertFalse(GuardThreatRule.unprovoked(SUMMONED_PET_BEAR), "a creature a plugin or player placed is not the town's enemy");
    }

    @Test
    void guardsStillFightHostileWildlifeTheGameSpawned() {
        assertTrue(GuardThreatRule.unprovoked(WILD_BEAR));
        assertEquals(released(WILD_BEAR), GuardThreatRule.unprovoked(WILD_BEAR), "wild monsters are treated as before");
    }

    @Test
    void peacefulAnimalsAreNeverTargets() {
        assertFalse(GuardThreatRule.unprovoked(WILD_SHEEP));
        assertFalse(GuardThreatRule.unprovoked(PLACED_SHEEP));
    }

    @Test
    void raidAttackersAreKnownByTheBindingTheModGaveThem() {
        // Raiders are placed by this mod, not by the game's spawner, so only their binding marks them.
        assertTrue(GuardThreatRule.unprovoked(RAIDER));
        assertTrue(GuardThreatRule.unprovoked(new Creature(false, true, false, false, false, false)), "even of a kind that is not hostile");
    }

    @Test
    void arenaCreaturesBelongToTheHunterAlone() {
        assertFalse(GuardThreatRule.unprovoked(ARENA_WOLF));
        assertFalse(GuardThreatRule.unprovoked(new Creature(false, false, true, false, true, true)));
    }

    @Test
    void aPackCanSpareACreatureThroughTheFriendlyGroup() {
        assertFalse(GuardThreatRule.unprovoked(WILD_BEAR_A_PACK_SPARED));
    }

    @Test
    void theTownsOwnPeopleAreNeverTargets() {
        assertFalse(GuardThreatRule.unprovoked(GUARD));
        assertFalse(GuardThreatRule.unprovoked(new Creature(true, true, false, false, true, true)));
    }

    @Test
    void everyCombinationOnlyAllowsAModEnemyOrHostileWildlife() {
        for (int bits = 0; bits < 64; bits++) {
            Creature creature = new Creature((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0);
            if (!GuardThreatRule.unprovoked(creature)) continue;
            assertFalse(creature.townsOwn() || creature.arenaCreature() || creature.spared(), creature.toString());
            assertTrue(creature.raidEnemy() || (creature.wildSpawn() && creature.hostileKind()), creature.toString());
        }
    }

    /**
     * The game gives a freed entity slot to the next entity that enters the world. Guards remembered an attacker by
     * its slot number, so the next creature to spawn after an attacker died was attacked as if it were that attacker.
     */
    @Test
    void aNewCreatureInADeadAttackersSlotIsNotAnAttacker() {
        var memory = new GuardNpcAttackerMemory();
        var trork = new Ref<EntityStore>(null, 40);
        memory.markAttacker(trork, 1_000);
        assertTrue(memory.isMarkedAttacker(trork, 1_001), "the creature that struck a guard is remembered");
        // The trork dies; a summoned pet enters the world and is given slot 40.
        var pet = new Ref<EntityStore>(null, 40);
        assertEquals(trork.getIndex(), pet.getIndex());
        assertFalse(memory.isMarkedAttacker(pet, 1_002), "a newcomer must not inherit the guilt of the slot's last holder");
        assertTrue(memory.isMarkedAttacker(trork, 1_002), "and the real attacker is still remembered");
        assertFalse(memory.isMarkedAttacker(trork, 1_000 + GuardNpcAttackerMemory.MEMORY_MS + 1), "until the memory runs out");
        var copy = (GuardNpcAttackerMemory) memory.clone();
        memory.markAttacker(pet, 2_000);
        assertFalse(copy.isMarkedAttacker(pet, 2_001), "world-state clones must own their maps");
    }

    @Test
    void aNewCreatureInASlotThePlayerJustFoughtIsNotThePlayersTarget() {
        var provoked = new GuardPlayerProvokedTargets();
        UUID player = UUID.randomUUID();
        var monster = new Ref<EntityStore>(null, 12);
        provoked.markPlayerHit(player, monster, 500);
        assertTrue(provoked.isProvokedByPlayer(player, monster, 501));
        // The player kills it and then summons a pet, which the game puts in slot 12.
        var pet = new Ref<EntityStore>(null, 12);
        assertFalse(provoked.isProvokedByPlayer(player, pet, 502), "an escort must not attack the pet as the player's target");
        assertFalse(provoked.isProvokedByPlayer(UUID.randomUUID(), monster, 502), "another player's escort is not involved");
        assertFalse(provoked.isProvokedByPlayer(player, monster, 500 + GuardPlayerProvokedTargets.PROVOKE_MEMORY_MS + 1));
    }

    @Test
    void theMemoriesForgetCreaturesThatHaveExpired() {
        var memory = new GuardNpcAttackerMemory();
        for (int i = 0; i < 500; i++) memory.markAttacker(new Ref<EntityStore>(null, i), i);
        var late = new Ref<EntityStore>(null, 7);
        long muchLater = 500 + 10 * GuardNpcAttackerMemory.MEMORY_MS;
        memory.markAttacker(late, muchLater);
        assertTrue(memory.isMarkedAttacker(late, muchLater + 1));
        assertFalse(memory.isMarkedAttacker(new Ref<EntityStore>(null, 3), muchLater + 1));
    }

    /**
     * The guard roles pick their own targets in their role files. None of them may pick by a base game role group
     * again: the group says what kind of creature it is, never whose it is.
     */
    @Test
    void guardRoleFilesChooseTargetsByTheGuardRuleNotByRoleGroup() throws Exception {
        Path root = Path.of("src/main/resources/Server/NPC/Roles/Aetherhaven");
        List<Path> files = new ArrayList<>();
        for (String kind : new String[] {"Knight", "Archer", "Mage", "Rogue"}) files.add(root.resolve("Aetherhaven_Guard_" + kind + ".json"));
        try (var steps = Files.list(root.resolve("Components/Steps"))) {
            steps.filter(file -> file.getFileName().toString().startsWith("Component_Instruction_Aetherhaven_Guard_")).forEach(files::add);
        }
        int sensors = 0;
        for (Path file : files) {
            List<JsonElement> mobSensors = new ArrayList<>();
            collectMobSensors(JsonParser.parseString(Files.readString(file)), mobSensors);
            for (JsonElement sensor : mobSensors) {
                sensors++;
                String text = sensor.toString();
                assertFalse(text.contains("\"NPCGroup\""), file.getFileName() + " picks targets by role group: " + text);
                assertTrue(text.contains("\"AetherhavenGuardThreat\""), file.getFileName() + " must use the guard rule: " + text);
            }
        }
        assertEquals(6, sensors, "four guard roles and two shared steps look for creatures to fight");
    }

    private static void collectMobSensors(JsonElement node, List<JsonElement> found) {
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) collectMobSensors(child, found);
        } else if (node.isJsonObject()) {
            var object = node.getAsJsonObject();
            if (object.has("Type") && object.get("Type").isJsonPrimitive() && "Mob".equals(object.get("Type").getAsString())) found.add(object);
            for (var entry : object.entrySet()) collectMobSensors(entry.getValue(), found);
        }
    }
}
