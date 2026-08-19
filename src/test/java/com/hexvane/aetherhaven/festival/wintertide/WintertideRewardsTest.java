package com.hexvane.aetherhaven.festival.wintertide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class WintertideRewardsTest {
    @Test
    void ticketsMatchHowMuchTheGiftWasLiked() {
        assertEquals(1, WintertideIds.ticketCount(GiftPreference.DISLIKE));
        assertEquals(3, WintertideIds.ticketCount(GiftPreference.NEUTRAL));
        assertEquals(5, WintertideIds.ticketCount(GiftPreference.LIKE));
        assertEquals(10, WintertideIds.ticketCount(GiftPreference.LOVE));
    }

    @Test
    void villagerGiftsGiveTwiceTheUsualFriendship() {
        assertEquals(2, WintertideIds.reputationDelta(GiftPreference.NEUTRAL));
        assertEquals(6, WintertideIds.reputationDelta(GiftPreference.LIKE));
        assertEquals(10, WintertideIds.reputationDelta(GiftPreference.LOVE));
        assertEquals(-2, WintertideIds.reputationDelta(GiftPreference.DISLIKE));
    }

    @Test
    void configuredGiftsMatchTheWintertideTables() {
        Random rnd = new Random(1L);
        assertEquals(List.of(new WintertideGifts.Stack("Ore_Mithril", 10)), WintertideGifts.configuredFor("miner", rnd));
        assertEquals(
            List.of(new WintertideGifts.Stack("Ingredient_Hide_Storm", 10)),
            WintertideGifts.configuredFor("rancher", rnd)
        );
        assertEquals(
            List.of(new WintertideGifts.Stack("Plant_Seeds_Potato_Eternal", 10)),
            WintertideGifts.configuredFor("logger", rnd)
        );
        assertEquals(
            List.of(new WintertideGifts.Stack("Plant_Seeds_Wheat_Eternal", 10)),
            WintertideGifts.configuredFor("farmer", rnd)
        );
        assertEquals(
            List.of(new WintertideGifts.Stack("Food_Pie_Apple", 8), new WintertideGifts.Stack("Food_Pie_Meat", 8)),
            WintertideGifts.configuredFor("chef", rnd)
        );
        List<WintertideGifts.Stack> merchant = WintertideGifts.configuredFor("merchant", rnd);
        assertEquals(1, merchant.size());
        assertTrue(merchant.get(0).itemId().startsWith("Aetherhaven_Necklace_"));
        assertEquals(1, merchant.get(0).count());
        assertEquals(
            List.of(new WintertideGifts.Stack("Aetherhaven_Heartberry", 1)),
            WintertideGifts.configuredFor("elder", rnd)
        );
        assertEquals(
            List.of(new WintertideGifts.Stack("Food_Pie_Apple", 8), new WintertideGifts.Stack("Food_Pie_Pumpkin", 8)),
            WintertideGifts.configuredFor("innkeeper", rnd)
        );
        assertEquals(
            List.of(new WintertideGifts.Stack("Potion_Health_Greater", 5)),
            WintertideGifts.configuredFor("priestess", rnd)
        );
        assertEquals(
            List.of(new WintertideGifts.Stack("Plant_Sapling_Crystal", 3)),
            WintertideGifts.configuredFor("crystal_keeper", rnd)
        );
        assertEquals(
            List.of(
                new WintertideGifts.Stack("Metal_Iron", 16),
                new WintertideGifts.Stack("Metal_Copper", 16),
                new WintertideGifts.Stack("Metal_Bronze", 16),
                new WintertideGifts.Stack("Metal_Zinc", 16)
            ),
            WintertideGifts.configuredFor("builder", rnd)
        );
        assertEquals(
            List.of(new WintertideGifts.Stack("Aetherhaven_Mining_Bomb", 50)),
            WintertideGifts.configuredFor("pyrotechnic", rnd)
        );
        assertEquals(
            List.of(
                new WintertideGifts.Stack("Deco_Kweebec_Plush", 1),
                new WintertideGifts.Stack("Food_Candy_Cane", 10)
            ),
            WintertideGifts.configuredFor("clown", rnd)
        );

        List<WintertideGifts.Stack> smith = WintertideGifts.configuredFor("blacksmith", new Random(2L));
        assertEquals(1, smith.size());
        assertTrue(smith.get(0).itemId().contains("Mithril"));
        assertEquals(1, smith.get(0).count());

        List<WintertideGifts.Stack> guild = WintertideGifts.configuredFor("guild_master", new Random(3L));
        assertEquals(1, guild.size());
        assertTrue(guild.get(0).itemId().startsWith("Weapon_"));
        assertFalse(guild.get(0).itemId().startsWith("Tool_"));

        List<WintertideGifts.Stack> bard = WintertideGifts.configuredFor("bard", new Random(4L));
        assertEquals(20, bard.size());
        for (WintertideGifts.Stack stack : bard) {
            assertTrue(stack.itemId().startsWith("Ingredient_Crystal_"));
        }
        List<WintertideGifts.Stack> florist = WintertideGifts.configuredFor("florist", new Random(5L));
        assertEquals(16, florist.size());
        for (WintertideGifts.Stack stack : florist) {
            assertTrue(stack.itemId().startsWith("Plant_Flower_Orchid_"));
            assertFalse(stack.itemId().contains("Poisoned"));
        }
    }

    @Test
    void uniqueOutgoingTargetsNeverIncludeYourself() {
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID v1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID v2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
        List<WintertideAssignmentService.PlayerMember> players =
            List.of(
                new WintertideAssignmentService.PlayerMember(p1, "Ada"),
                new WintertideAssignmentService.PlayerMember(p2, "Ben")
            );
        List<WintertideAssignmentService.Resident> residents =
            List.of(
                new WintertideAssignmentService.Resident(v1, "miner", "Gorruk"),
                new WintertideAssignmentService.Resident(v2, "chef", "Pepper")
            );
        WintertideSession session = new WintertideSession();
        WintertideAssignmentService.assignAll(session, players, residents, 99L);

        WintertideTarget t1 = session.getOutgoing(p1);
        WintertideTarget t2 = session.getOutgoing(p2);
        assertNotNull(t1);
        assertNotNull(t2);
        assertNotEquals(p1, t1.getUuid());
        assertNotEquals(p2, t2.getUuid());
        assertNotEquals(t1.getUuid(), t2.getUuid());

        Set<UUID> outgoing = new HashSet<>();
        outgoing.add(t1.getUuid());
        outgoing.add(t2.getUuid());
        assertEquals(2, outgoing.size());
    }

    @Test
    void aPlayerAssignedToYouStillGetsAVillagerGiftBack() {
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID v1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID v2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
        List<WintertideAssignmentService.PlayerMember> players =
            List.of(
                new WintertideAssignmentService.PlayerMember(p1, "Ada"),
                new WintertideAssignmentService.PlayerMember(p2, "Ben")
            );
        List<WintertideAssignmentService.Resident> residents =
            List.of(
                new WintertideAssignmentService.Resident(v1, "miner", "Gorruk"),
                new WintertideAssignmentService.Resident(v2, "chef", "Pepper")
            );
        WintertideSession session = new WintertideSession();
        session.putOutgoing(p1, WintertideTarget.player(p2, "Ben"));
        session.putOutgoing(p2, WintertideTarget.villager(v1, "miner", "Gorruk"));
        WintertideAssignmentService.assignAll(session, players, residents, 7L);

        WintertideTarget incoming = session.getIncoming(p2);
        assertNotNull(incoming);
        assertTrue(incoming.isVillager());
        WintertideTarget otherIncoming = session.getIncoming(p1);
        assertNotNull(otherIncoming);
        assertTrue(otherIncoming.isVillager());
    }

    @Test
    void soloPlayerGivesToAVillagerAndReceivesFromAVillager() {
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID v1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID v2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
        List<WintertideAssignmentService.PlayerMember> players =
            List.of(new WintertideAssignmentService.PlayerMember(p1, "Ada"));
        List<WintertideAssignmentService.Resident> residents =
            List.of(
                new WintertideAssignmentService.Resident(v1, "miner", "Gorruk"),
                new WintertideAssignmentService.Resident(v2, "chef", "Pepper")
            );
        WintertideSession session = new WintertideSession();
        WintertideAssignmentService.assignAll(session, players, residents, 12L);

        WintertideTarget outgoing = session.getOutgoing(p1);
        WintertideTarget incoming = session.getIncoming(p1);
        assertNotNull(outgoing);
        assertNotNull(incoming);
        assertTrue(outgoing.isVillager());
        assertTrue(incoming.isVillager());
        assertNotEquals(outgoing.getUuid(), incoming.getUuid());
    }

    @Test
    void sameSeedKeepsTheSameAssignments() {
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID v1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID v2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
        List<WintertideAssignmentService.PlayerMember> players =
            List.of(new WintertideAssignmentService.PlayerMember(p1, "Ada"));
        List<WintertideAssignmentService.Resident> residents =
            List.of(
                new WintertideAssignmentService.Resident(v1, "miner", "Gorruk"),
                new WintertideAssignmentService.Resident(v2, "chef", "Pepper")
            );
        WintertideSession first = new WintertideSession();
        WintertideSession second = new WintertideSession();
        long seed = WintertideAssignmentService.seedFor(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), 3L);
        WintertideAssignmentService.assignAll(first, players, residents, seed);
        WintertideAssignmentService.assignAll(second, players, residents, seed);
        assertEquals(first.getOutgoing(p1).getUuid(), second.getOutgoing(p1).getUuid());
        assertEquals(first.getIncoming(p1).getUuid(), second.getIncoming(p1).getUuid());
    }

    @Test
    void differentSeedsCanChangeAssignments() {
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID v1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID v2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
        UUID v3 = UUID.fromString("00000000-0000-0000-0000-000000000013");
        List<WintertideAssignmentService.PlayerMember> players =
            List.of(new WintertideAssignmentService.PlayerMember(p1, "Ada"));
        List<WintertideAssignmentService.Resident> residents =
            List.of(
                new WintertideAssignmentService.Resident(v1, "miner", "Gorruk"),
                new WintertideAssignmentService.Resident(v2, "chef", "Pepper"),
                new WintertideAssignmentService.Resident(v3, "farmer", "Bram")
            );
        UUID firstOutgoing = null;
        boolean sawDifferent = false;
        for (long seed = 1L; seed <= 40L; seed++) {
            WintertideSession session = new WintertideSession();
            WintertideAssignmentService.assignAll(session, players, residents, seed);
            UUID outgoing = session.getOutgoing(p1).getUuid();
            if (firstOutgoing == null) {
                firstOutgoing = outgoing;
            } else if (!firstOutgoing.equals(outgoing)) {
                sawDifferent = true;
                break;
            }
        }
        assertTrue(sawDifferent);
    }

    @Test
    void assignedVillagersIncludeOutgoingAndIncomingGifters() {
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID v1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID v2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
        WintertideSession session = new WintertideSession();
        session.putOutgoing(p1, WintertideTarget.villager(v1, "miner", "Gorruk"));
        session.putIncoming(p1, WintertideTarget.villager(v2, "chef", "Pepper"));

        assertTrue(session.isAssignedVillager(v1));
        assertTrue(session.isAssignedVillager(v2));
        assertEquals(Set.of(v1, v2), session.assignedVillagerUuids());
        assertEquals(-1, session.overflowStandIndex(v1, Set.of("miner", "chef")));
        assertEquals(0, session.overflowStandIndex(v1, Set.of()));
    }
}
