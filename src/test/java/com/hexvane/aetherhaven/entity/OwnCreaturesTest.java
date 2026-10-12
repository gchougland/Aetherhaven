package com.hexvane.aetherhaven.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.festival.snowball.SnowballIds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** What counts as this mod's own, for the code that changes or removes things across the whole world. */
@Tag("entity")
class OwnCreaturesTest {
    @Test
    void baseGameAndOtherModsCreaturesAreNotOurs() {
        for (String role : List.of("Bear_Grizzly", "Wolf_Black", "Sheep", "Tamed_Sheep", "Trork_Warrior", "Skeleton_Fighter",
            "Tamework_Pet_Bear", "aetherhaven_lowercase", "My_Aetherhaven_Fan_Npc")) {
            assertFalse(OwnCreatures.isOwn(role, false), role);
        }
        assertFalse(OwnCreatures.isOwn(null, false));
        assertFalse(OwnCreatures.isOwn("", false));
    }

    @Test
    void theModsOwnRolesAndBoundCharactersAreOurs() {
        assertTrue(OwnCreatures.isOwn("Aetherhaven_Guard_Knight", false));
        assertTrue(OwnCreatures.isOwn("Aetherhaven_Barn_Sheep", false));
        assertTrue(OwnCreatures.isOwn("Aetherhaven_Raid_Trork_Warrior", false));
        // A custom character from an add-on pack has its own role name and carries the town's binding.
        assertTrue(OwnCreatures.isOwn("Some_Pack_Villager", true));
        assertTrue(OwnCreatures.isOwn(null, true));
    }

    @Test
    void onlyComponentsThisModRegisteredMarkAnEntityAsOurs() {
        assertTrue(OwnCreatures.isModComponent(com.hexvane.aetherhaven.villager.TownVillagerBinding.class));
        assertTrue(OwnCreatures.isModComponent(com.hexvane.aetherhaven.questboard.RaidQuestMobBinding.class));
        assertFalse(OwnCreatures.isModComponent(com.hypixel.hytale.server.npc.entities.NPCEntity.class));
        assertFalse(OwnCreatures.isModComponent(String.class));
        assertFalse(OwnCreatures.isModComponent(null));
        assertFalse(OwnCreatures.carriesModComponent(null));
    }

    /** The prefix test is only sound while every creature role this mod ships carries it. */
    @Test
    void everyRoleFileThisModShipsCarriesThePrefix() throws IOException {
        for (String folder : List.of("src/main/resources/Server/NPC/Roles/Aetherhaven", "subplugin-assets")) {
            Path root = Path.of(folder);
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String path = file.toString().replace('\\', '/');
                    String name = file.getFileName().toString();
                    if (!path.contains("/NPC/Roles/") || !name.endsWith(".json")) continue;
                    // Shared steps and templates are parts of roles, not creatures that can be placed.
                    if (path.contains("/Components/") || path.contains("/Templates/")) continue;
                    assertTrue(name.startsWith(OwnCreatures.ROLE_PREFIX), path + " would not be recognised as this mod's creature");
                }
            }
        }
    }

    @Test
    void onlyTheModsOwnSnowballIsASnowballFightProjectile() {
        assertTrue(SnowballIds.isOwnProjectileModel("Aetherhaven_Snowball_Projectile"));
        // The released test was "contains snowball", which matched all of these.
        for (String other : List.of("Snowball", "Yeti_Snowball_Projectile", "OtherMod_Snowball", "Projectile_Snowball_Large")) {
            assertTrue(other.toLowerCase(java.util.Locale.ROOT).contains("snowball"));
            assertFalse(SnowballIds.isOwnProjectileModel(other), other);
        }
        assertFalse(SnowballIds.isOwnProjectileModel(null));
    }
}
