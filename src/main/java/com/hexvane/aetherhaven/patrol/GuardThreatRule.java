package com.hexvane.aetherhaven.patrol;

/**
 * Which creatures a town guard may start a fight with when nobody has been attacked.
 *
 * <p>Guards used to attack every creature whose role name put it in the base game's Aggressive group, so they killed
 * other mods' pets and summons (a tamed bear keeps the role {@code Bear_Grizzly}) and anything a player or plugin
 * placed near them. A role name says what kind of creature it is, never whose it is. Guards now open a fight only
 * with an enemy this mod spawned itself, or with a hostile kind of creature the game's own spawner put in the world.
 * A creature that attacks a guard, a resident or a protected player is still fought back, whatever it is: that is a
 * separate, reactive rule.
 */
public final class GuardThreatRule {
    private GuardThreatRule() {}

    /**
     * What is known about a creature, as plain values so the rule can be checked without a server.
     *
     * @param townsOwn      it carries this mod's villager binding or has one of this mod's townsfolk roles
     * @param raidEnemy     it carries the binding this mod gives every raid and rescue attacker it spawns
     * @param arenaCreature it carries the mark this mod gives the Hunter's arena creatures; only the Hunter fights those
     * @param spared        a pack listed its role in the {@code Aetherhaven_Guard_Spared} or {@code Friendly} group
     * @param wildSpawn     the game's own spawner placed it: it has a world spawn rule, a spawn beacon, a spawn marker or a
     *                      world generation id. A creature placed by a command or another plugin has none of these
     * @param hostileKind   its kind attacks people (the Aggressive, Outlander and Scarak groups and a few named roles)
     */
    public record Creature(boolean townsOwn, boolean raidEnemy, boolean arenaCreature, boolean spared, boolean wildSpawn,
                           boolean hostileKind) {}

    /** True when a guard may attack this creature although it has attacked nobody. */
    public static boolean unprovoked(Creature creature) {
        if (creature.townsOwn() || creature.arenaCreature() || creature.spared()) return false;
        if (creature.raidEnemy()) return true;
        return creature.wildSpawn() && creature.hostileKind();
    }
}
