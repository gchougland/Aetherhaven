package com.hexvane.aetherhaven.townsfolk;

import java.util.LinkedHashMap;
import java.util.Map;

/** Town-owned progression keyed by catalog identity, independent of entity unloads and resurrection. */
public final class TownsfolkProgress {
    public static final int MAX_SKILL = 10;
    public static final int MAX_ATTRIBUTE = 20;
    public static final int MAX_MASTERY_LEVEL = 20;
    /** Total earned XP is authoritative; point entitlements are derived, never reissued on load. */
    private long masteryXp;
    private int spentMasterySkillPoints;
    private int spentMasteryAttributePoints;
    private Map<String, Integer> attributes = new LinkedHashMap<>();
    private Map<String, Integer> skills = new LinkedHashMap<>();
    private Map<String, Integer> workPriorities = new LinkedHashMap<>();
    private boolean employed;
    private boolean courier;
    private String professionKitData;
    private transient com.hypixel.hytale.server.core.inventory.ItemStack professionKit;

    public com.hypixel.hytale.server.core.inventory.ItemStack professionKit() {
        if (professionKit == null && professionKitData != null) professionKit =
            com.hypixel.hytale.server.core.inventory.ItemStack.CODEC.decode(org.bson.BsonDocument.parse(professionKitData), new com.hypixel.hytale.codec.ExtraInfo());
        return professionKit;
    }
    public static String encodeProfessionKit(com.hypixel.hytale.server.core.inventory.ItemStack stack) {
        return com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack) ? null
            : com.hypixel.hytale.server.core.inventory.ItemStack.CODEC.encode(stack, new com.hypixel.hytale.codec.ExtraInfo()).asDocument().toJson();
    }
    public void setProfessionKitData(String data) { professionKitData = data; professionKit = null; }

    private String guardKitData;
    private transient com.hypixel.hytale.server.core.inventory.ItemStack guardKit;
    public com.hypixel.hytale.server.core.inventory.ItemStack guardKit() {
        if(guardKit==null&&guardKitData!=null)guardKit=com.hypixel.hytale.server.core.inventory.ItemStack.CODEC.decode(
            org.bson.BsonDocument.parse(guardKitData),new com.hypixel.hytale.codec.ExtraInfo());
        return guardKit;
    }
    public void setGuardKitData(String data) { guardKitData=data;guardKit=null; }

    public boolean isEmployed() { return employed; }
    public void setEmployed(boolean value) { employed = value; }
    /** Compatibility for old integrations. Logistics is a priority, never an exclusive role. */
    @Deprecated public boolean isCourier() { return priority(TownsfolkWorkType.LOGISTICS)>0; }
    @Deprecated public void setCourier(boolean value) { setPriority(TownsfolkWorkType.LOGISTICS,value?1:0); }
    private void migrateCourierPriority() {
        if(!courier)return;
        if(workPriorities==null)workPriorities=new LinkedHashMap<>();
        workPriorities.putIfAbsent(TownsfolkWorkType.LOGISTICS.id,1);
        courier=false;
    }

    /** Zero disables work. One is highest priority, four is lowest. */
    public int priority(TownsfolkWorkType type) {
        migrateCourierPriority();
        if (skill(type.skill) == 0) return 0;
        Integer value = workPriorities == null ? null : workPriorities.get(type.id);
        // Clerks served their shop before Commerce had a priority. Until one is chosen it stays first, as it was.
        if (value == null && type == TownsfolkWorkType.COMMERCE) return 1;
        return Math.clamp(value == null ? 0 : value, 0, 4);
    }

    public void setPriority(TownsfolkWorkType type, int priority) {
        migrateCourierPriority();
        if (workPriorities == null) workPriorities = new LinkedHashMap<>();
        workPriorities.put(type.id, skill(type.skill) == 0 ? 0 : Math.clamp(priority, 0, 4));
    }

    /** A skill above this untrained level is one the townsfolk is good at. */
    public static final int UNTRAINED_SKILL = 1;
    /** Saved once starting priorities have been considered for this resident, so it is never done a second time. */
    private boolean startingPrioritiesSet;
    public boolean startingPrioritiesSet() { return startingPrioritiesSet; }

    /** Whether a new resident starts with this kind of work switched on. */
    private boolean startsWith(TownsfolkWorkType type) {
        // Commerce is left alone: a clerk is chosen at a shop, and until a priority is chosen the shop comes first.
        return type.availableForNewWork() && type != TownsfolkWorkType.COMMERCE && skill(type.skill) > UNTRAINED_SKILL;
    }

    /**
     * The priorities a new resident starts with, by how good they are at each kind of work. Their best skill comes
     * first (1), their next best skill second (2), the one after that third (3), and every other skill they are trained
     * in last (4). Kinds of work that share a skill share its priority, and equal skills share one too. Skills at the
     * untrained level of 1, and work they cannot do at all, are left off.
     */
    public Map<TownsfolkWorkType, Integer> startingPriorities() {
        var levels = new java.util.TreeSet<Integer>(java.util.Comparator.reverseOrder());
        for (var type : TownsfolkWorkType.values()) if (startsWith(type)) levels.add(skill(type.skill));
        var ranked = new java.util.ArrayList<>(levels);
        var priorities = new LinkedHashMap<TownsfolkWorkType, Integer>();
        for (var type : TownsfolkWorkType.values())
            if (startsWith(type)) priorities.put(type, Math.min(4, ranked.indexOf(skill(type.skill)) + 1));
        return priorities;
    }

    /**
     * Gives a resident their starting priorities. It acts once in a resident's life, and it leaves the priorities alone
     * when any has been set already, whether by a player, by a courier assignment or by an older save.
     *
     * @return whether this was the first call, so the caller knows the town needs saving
     */
    public boolean applyStartingPriorities() {
        if (startingPrioritiesSet) return false;
        startingPrioritiesSet = true;
        migrateCourierPriority();
        if (workPriorities == null) workPriorities = new LinkedHashMap<>();
        if (workPriorities.isEmpty())
            for (var start : startingPriorities().entrySet()) workPriorities.put(start.getKey().id, start.getValue());
        return true;
    }

    public boolean canPerform(TownsfolkWorkType type, boolean onGuardDuty, boolean overdue, boolean alive) {
        return employed && alive && !onGuardDuty && !overdue && priority(type) > 0;
    }

    public int attribute(TownsfolkAttribute attribute) {
        Integer value = attributes == null ? null : attributes.get(attribute.id);
        return Math.clamp(value == null ? 5 : value, 1, MAX_ATTRIBUTE);
    }

    public int skill(TownsfolkSkill skill) {
        Integer value = skills == null ? null : skills.get(skill.id);
        return Math.clamp(value == null ? 1 : value, 0, MAX_SKILL);
    }

    public void setAttribute(TownsfolkAttribute attribute, int value) {
        if (attributes == null) attributes = new LinkedHashMap<>();
        attributes.put(attribute.id, Math.clamp(value, 1, MAX_ATTRIBUTE));
    }

    public void setSkill(TownsfolkSkill skill, int value) {
        if (skills == null) skills = new LinkedHashMap<>();
        skills.put(skill.id, Math.clamp(value, 0, MAX_SKILL));
    }

    public int attributeCost(TownsfolkAttribute attribute) {
        int level = attribute(attribute);
        return level >= MAX_ATTRIBUTE ? 0 : 1;
    }

    public int skillCost(TownsfolkSkill skill) {
        int level = skill(skill);
        return level == 0 || level >= MAX_SKILL ? 0 : 1;
    }

    /** Provisional curve: 100 XP for level 2, then 50 more for each subsequent level. */
    public static long masteryXpForLevel(int level) {
        int steps = Math.clamp(level, 1, MAX_MASTERY_LEVEL) - 1;
        return 100L * steps + 25L * steps * (steps - 1);
    }

    public long masteryXp() { return Math.clamp(masteryXp, 0L, masteryXpForLevel(MAX_MASTERY_LEVEL)); }
    public int masteryLevel() {
        int level = 1;
        while (level < MAX_MASTERY_LEVEL && masteryXp() >= masteryXpForLevel(level + 1)) level++;
        return level;
    }
    public long masteryXpInLevel() { return masteryXp() - masteryXpForLevel(masteryLevel()); }
    public long masteryXpToNextLevel() {
        int level = masteryLevel();
        return level == MAX_MASTERY_LEVEL ? 0 : masteryXpForLevel(level + 1) - masteryXpForLevel(level);
    }
    public int masterySkillPoints() {
        int earned = masteryLevel() - 1;
        return earned - Math.clamp(spentMasterySkillPoints, 0, earned);
    }
    public int masteryAttributePoints() {
        int earned = masteryLevel() / 5 * 2;
        return earned - Math.clamp(spentMasteryAttributePoints, 0, earned);
    }
    /** Called only alongside a successful physical work transaction, before that town is saved. */
    public long earnMasteryXp(long amount) {
        if (amount <= 0) return 0;
        long before = masteryXp();
        masteryXp = before + Math.min(amount, masteryXpForLevel(MAX_MASTERY_LEVEL) - before);
        return masteryXp - before;
    }
    public boolean spendMasteryPoint(TownsfolkSkill skill) {
        if (skill == null || masterySkillPoints() == 0 || !train(skill)) return false;
        spentMasterySkillPoints = Math.clamp(spentMasterySkillPoints, 0, masteryLevel() - 1) + 1;
        return true;
    }
    public boolean spendMasteryPoint(TownsfolkAttribute attribute) {
        if (attribute == null || masteryAttributePoints() == 0 || !train(attribute)) return false;
        spentMasteryAttributePoints = Math.clamp(spentMasteryAttributePoints, 0, masteryLevel() / 5 * 2) + 1;
        return true;
    }

    public boolean train(TownsfolkSkill skill) {
        if (skillCost(skill) == 0) return false;
        setSkill(skill, skill(skill) + 1);
        return true;
    }

    public boolean train(TownsfolkAttribute attribute) {
        if (attributeCost(attribute) == 0) return false;
        setAttribute(attribute, attribute(attribute) + 1);
        return true;
    }

    /** Floors from multiple personalities combine by maximum; explicit character values override afterward. */
    public void applyStartingValues(Map<String, Integer> attributeValues, Map<String, Integer> skillValues, boolean override) {
        for (var attribute : TownsfolkAttribute.values()) {
            Integer value = attributeValues.get(attribute.id);
            if (value != null) setAttribute(attribute, override ? value : Math.max(attribute(attribute), value));
        }
        for (var skill : TownsfolkSkill.values()) {
            Integer value = skillValues.get(skill.id);
            if (value != null) setSkill(skill, override ? value : Math.max(skill(skill), value));
        }
    }
}
