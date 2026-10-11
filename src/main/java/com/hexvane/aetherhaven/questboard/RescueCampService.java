package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.quest.QuestAvailability;
import com.hexvane.aetherhaven.town.TownAdmission;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolPersistence;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.adventure.worldevents.WorldEventManager;
import com.hypixel.hytale.builtin.adventure.worldevents.WorldEventRecord;
import com.hypixel.hytale.builtin.adventure.worldevents.WorldEventsPlugin;
import com.hypixel.hytale.builtin.adventure.worldevents.action.EventStartAction;
import com.hypixel.hytale.builtin.adventure.worldevents.action.EventStopAction;
import com.hypixel.hytale.builtin.adventure.worldevents.util.PrefabSnapshotUtil;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Rescue contracts: a raid on a temporary camp outside town where a townsfolk is held.
 *
 * <p>The camp is a base game world event ({@value #EVENT_ID}), so the game pastes the prefab, remembers the land
 * underneath and puts it back when the event stops. This class chooses the site, starts and stops that event, and
 * places the captive. The attackers are an ordinary raid roster whose march target is the camp.
 */
public final class RescueCampService {
    public static final String EVENT_ID = "Aetherhaven_Rescue_Camp";
    /** How far the ground may rise or fall across a camp before the site is rejected. */
    static final int MAX_HEIGHT_DELTA = 2;
    static final int SITE_ATTEMPTS = 12;
    /** Captors and the captive are only placed once a town member is this close, so they appear in live terrain. */
    public static final double ARRIVAL_RADIUS = 40.0;
    static final double CAPTOR_RING_MIN = 8.0;
    static final double CAPTOR_RING_MAX = 15.0;
    /** A captor who dies this soon after appearing, and not by a player's hand, was not defeated: they are replaced. */
    static final long ARRIVAL_DEATH_MS = 6000L;
    /** Captors cannot be hurt for their first moments, so a bad landing does not kill them. */
    static final long ARRIVAL_SHIELD_MS = 3000L;
    /** Most captors of one contract that are replaced after dying on arrival. Past this their deaths count. */
    static final int MAX_REPLACEMENTS = 10;
    private static final java.util.Map<UUID, Long> CAPTOR_APPEARED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Integer> REPLACED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Integer> NO_SAFE_GROUND = new java.util.concurrent.ConcurrentHashMap<>();
    /** How long someone must be missing, with a player at the camp, before they are placed again. */
    static final long MISSING_GRACE_MS = 8000L;
    private static final java.util.Map<String, Long> MISSING_SINCE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile boolean available;
    private static final java.util.Map<UUID, Captive> CAPTIVES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> REFRESHED = new java.util.concurrent.ConcurrentHashMap<>();
    static final String UNTIE_SOUND = "SFX_Rope_Break";
    /** The rope loops a bound captive wears, attached to their pelvis so they follow the seated pose. */
    static final String ROPES_ATTACHMENT = "Items/Aetherhaven/Rescue_Ropes/Rescue_Ropes.blockymodel";
    static final String ROPES_TEXTURE = "Items/Aetherhaven/Rescue_Ropes/Rescue_Ropes_Texture.png";

    /** A held townsfolk: where they stay, and whether they are still bound. World thread only. */
    static final class Captive {
        @Nullable Vector3d anchor;
        boolean tied;
        boolean seated;
        float sincePose;

        Captive(@Nullable Vector3d anchor, boolean tied) {
            this.anchor = anchor;
            this.tied = tied;
        }
    }

    /** True while this townsfolk is bound at a rescue camp. They do nothing but sit until a player cuts the ropes. */
    public static boolean isTied(@Nullable UUID entity) {
        Captive captive = entity == null ? null : CAPTIVES.get(entity);
        return captive != null && captive.tied;
    }

    /** A captive who is still tied, with the contract and town they belong to. */
    public record Bound(@Nonnull TownRecord town, @Nonnull QuestBoardSlotRecord slot) {}

    /**
     * Looks the townsfolk up in the saved contracts, not in the remembered list of captives, so it is right even
     * if that list has fallen behind. Null when they are not a captive or their ropes are already cut.
     */
    @Nullable
    public static Bound bound(@Nonnull World world, @Nullable UUID entity) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || entity == null) {
            return null;
        }
        for (TownRecord town : com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).allTowns()) {
            for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
                if (slot.isAccepted() && slot.isRescueQuest() && !slot.isRescueUntied() && entity.equals(captiveId(slot))) {
                    return new Bound(town, slot);
                }
            }
        }
        return null;
    }

    /** What happened when a player used the interaction on a bound captive. */
    public enum CutResult { FREED, CAPTORS_REMAIN, NOT_A_MEMBER, GONE }

    /** Decides what the interaction does. Kept apart from the world so it can be tested. */
    static CutResult cutResult(boolean stillBound, boolean member, int defeated, int required) {
        if (!stillBound) {
            return CutResult.GONE;
        }
        if (!member) {
            return CutResult.NOT_A_MEMBER;
        }
        return required > 0 && defeated >= required ? CutResult.FREED : CutResult.CAPTORS_REMAIN;
    }

    /**
     * A player used the interaction on a bound captive: cut the ropes if the captors are all defeated, and tell the
     * player what happened. Never opens dialogue. Call outside entity processing.
     */
    public static void cutLoose(@Nonnull World world, @Nonnull UUID captive, @Nonnull UUID player) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        var playerRef = store.getExternalData().getRefFromUUID(player);
        var viewer = playerRef == null || !playerRef.isValid() ? null
            : store.getComponent(playerRef, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
        Bound bound = bound(world, captive);
        QuestBoardSlotRecord slot = bound == null ? null : bound.slot();
        CutResult result = cutResult(bound != null, bound != null && bound.town().playerCanCompleteQuests(player),
            slot == null ? 0 : slot.getRaidKillProgress(), slot == null ? 0 : slot.getRaidKillRequired());
        if (result == CutResult.FREED && !untie(slot, store)) {
            result = CutResult.CAPTORS_REMAIN;
        }
        if (result == CutResult.FREED) {
            com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(bound.town());
            LOGGER.atInfo().log("Rescue %s: %s was cut loose", slot.instanceIdOrEmpty(), slot.getRescueCharacterId());
        }
        if (viewer == null || result == CutResult.GONE) {
            return;
        }
        String base = "aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.";
        var message = switch (result) {
            case FREED -> com.hypixel.hytale.server.core.Message.translation(base + "rescueCutLoose")
                .param("captive", RaidQuestBoardHandler.captiveName(slot));
            case CAPTORS_REMAIN -> com.hypixel.hytale.server.core.Message.translation(base + "rescueCaptorsRemain")
                .param("current", slot.getRaidKillProgress()).param("need", slot.getRaidKillRequired())
                .param("target", RaidQuestBoardHandler.raidTargetLabel(slot));
            default -> com.hypixel.hytale.server.core.Message.translation(base + "rescueNotMember");
        };
        com.hypixel.hytale.server.core.util.NotificationUtil.sendNotification(viewer.getPacketHandler(), message,
            result == CutResult.FREED ? com.hypixel.hytale.protocol.packets.interface_.NotificationStyle.Success
                : com.hypixel.hytale.protocol.packets.interface_.NotificationStyle.Warning);
    }

    /**
     * Whether a captor's death is one the players earned. A captor who dies moments after appearing, without a player
     * striking the blow, fell foul of the ground they were put on. They are replaced, up to a limit.
     */
    static boolean diedOnArrival(long aliveMs, boolean killedByPlayer, int alreadyReplaced) {
        return !killedByPlayer && aliveMs >= 0 && aliveMs < ARRIVAL_DEATH_MS && alreadyReplaced < MAX_REPLACEMENTS;
    }

    /**
     * Called when one of a rescue's captors dies.
     *
     * @return true when the death must not count towards the contract
     */
    public static boolean captorDied(@Nonnull QuestBoardSlotRecord slot, @Nonnull UUID captor, boolean killedByPlayer, @Nonnull String cause) {
        Long appeared = CAPTOR_APPEARED.remove(captor);
        long alive = appeared == null ? -1L : System.currentTimeMillis() - appeared;
        String instance = slot.instanceIdOrEmpty();
        boolean wasted = diedOnArrival(alive, killedByPlayer, REPLACED.getOrDefault(instance, 0));
        if (!killedByPlayer) {
            LOGGER.atInfo().log("Rescue %s: a captor died %s ms after appearing, not by a player (%s)%s",
                instance, alive, cause, wasted ? "; they will be replaced" : "");
        }
        if (wasted) {
            REPLACED.merge(instance, 1, Integer::sum);
        }
        return wasted;
    }

    static boolean hasCaptives() {
        return !CAPTIVES.isEmpty();
    }

    @Nullable
    static Captive captive(@Nonnull UUID entity) {
        return CAPTIVES.get(entity);
    }

    /** Rebuilds the captive list from saved contracts every few seconds, so it survives restarts and missed cleanups. */
    static void refreshCaptives(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        long now = System.currentTimeMillis();
        Long last = REFRESHED.get(world.getName());
        if (last != null && now - last < 1000L) {
            return;
        }
        REFRESHED.put(world.getName(), now);
        Set<UUID> live = new HashSet<>();
        var towns = com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : towns.allTowns()) {
            for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
                UUID id = captiveId(slot);
                if (id == null || !slot.isAccepted() || !slot.isRescueQuest()) {
                    continue;
                }
                live.add(id);
                Captive known = CAPTIVES.computeIfAbsent(id, ignored -> new Captive(null, !slot.isRescueUntied()));
                known.tied = !slot.isRescueUntied();
            }
        }
        CAPTIVES.keySet().removeIf(id -> !live.contains(id) && world.getEntityStore().getStore().getExternalData().getRefFromUUID(id) != null);
    }

    static boolean wearsRopes(@Nonnull com.hypixel.hytale.server.core.asset.type.model.config.Model model) {
        var attachments = model.getAttachments();
        if (attachments != null) {
            for (var attachment : attachments) {
                if (attachment != null && ROPES_ATTACHMENT.equals(attachment.getModel())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The same townsfolk with the ropes put on or taken off. Everything else about their look is kept. */
    @Nonnull
    static com.hypixel.hytale.server.core.asset.type.model.config.Model withRopes(
        @Nonnull com.hypixel.hytale.server.core.asset.type.model.config.Model model, boolean tied) {
        var kept = new ArrayList<com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment>();
        if (model.getAttachments() != null) {
            for (var attachment : model.getAttachments()) {
                if (attachment != null && !ROPES_ATTACHMENT.equals(attachment.getModel())) {
                    kept.add(attachment);
                }
            }
        }
        if (tied) {
            kept.add(new com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment(ROPES_ATTACHMENT, ROPES_TEXTURE, null, null, 1.0));
        }
        return com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticAppearanceService.copyWithAttachments(model,
            kept.toArray(com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment[]::new));
    }

    @Nullable
    static UUID captiveId(@Nonnull QuestBoardSlotRecord slot) {
        try {
            String uuid = slot.getRescueCaptiveUuid();
            return uuid == null ? null : UUID.fromString(uuid);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** How many skills a rescued townsfolk is better at: two at rank E, rising to five at SS and above. */
    public static int boostCount(@Nullable String rank) {
        int index = TownQuestBoardRank.rankIndex(rank == null ? "E" : rank.trim());
        return Math.clamp(2 + Math.max(0, index) / 2, 2, 5);
    }

    /** Cuts a captive's ropes once every attacker is defeated. Returns false when it is not yet time. */
    public static boolean untie(@Nonnull QuestBoardSlotRecord slot, @Nonnull Store<EntityStore> store) {
        if (!slot.isRescueQuest() || !slot.isAccepted() || slot.isRescueUntied()
            || slot.getRaidKillRequired() <= 0 || slot.getRaidKillProgress() < slot.getRaidKillRequired()) {
            return false;
        }
        slot.setRescueUntied(true);
        UUID id = captiveId(slot);
        Captive captive = id == null ? null : CAPTIVES.get(id);
        if (captive != null) {
            captive.tied = false;
            if (captive.anchor != null) {
                int sound = com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getIndex(UNTIE_SOUND);
                if (sound != com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.EMPTY_ID && sound != Integer.MIN_VALUE && sound != 0) {
                    com.hypixel.hytale.server.core.universe.world.SoundUtil.playSoundEvent3d(sound, com.hypixel.hytale.protocol.SoundCategory.SFX,
                        captive.anchor.x, captive.anchor.y, captive.anchor.z, 1.0F, 1.0F, store);
                }
            }
        }
        return true;
    }

    private RescueCampService() {}

    /** Registers the camp site condition with the base game's world events. Call once from plugin setup. */
    public static void register(@Nonnull AetherhavenPlugin plugin) {
        try {
            if (WorldEventsPlugin.get() == null) {
                LOGGER.atInfo().log("World events are not loaded; rescue contracts are disabled.");
                return;
            }
            WorldEventsPlugin.registerCondition(plugin, RescueCampCondition.TYPE, RescueCampCondition.CODEC, RescueCampCondition.Config.CODEC);
            available = true;
        } catch (LinkageError | RuntimeException missing) {
            LOGGER.atWarning().withCause(missing).log("Could not register rescue camps with world events; rescue contracts are disabled.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /** A rescue may be posted only while the town can take another resident and no other move in is under way. */
    public static boolean canOffer(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town, @Nonnull World world) {
        if (!available || admissionIssue(plugin, town) != null) {
            return false;
        }
        for (QuestBoardSlotRecord slot : town.getQuestBoardSlots()) {
            if (slot.hasOffer() && slot.isRescueQuest()) {
                return false;
            }
        }
        return !candidates(plugin, town, world).isEmpty();
    }

    /** Why the town cannot welcome a rescued townsfolk right now, or null when it can. */
    @Nullable
    public static String admissionIssue(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        var quest = plugin.getQuestCatalog().get(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        if (quest == null || !QuestAvailability.isEnabled(quest)) {
            return "Housing quests are unavailable right now.";
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK)) {
            return "Finish the current house quest before welcoming another resident.";
        }
        return TownAdmission.blocked(plugin, town, null, null) != null ? "The town has no room for another resident yet." : null;
    }

    @Nonnull
    private static List<String> candidates(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town, @Nonnull World world) {
        var catalog = plugin.getTownsfolkCharacterCatalog();
        var pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        Set<String> idle = new HashSet<>(pool.availableCharacterIds(town.getTownId(), catalog, TownsfolkAssignmentKinds.IDLE));
        List<String> out = new ArrayList<>();
        for (String id : pool.availableCharacterIds(town.getTownId(), catalog, TownsfolkAssignmentKinds.TOURIST)) {
            if (idle.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Chooses who is held and which two skills they are better at. False when nobody is available. */
    static boolean prepareOffer(@Nonnull QuestBoardSlotRecord slot, @Nonnull TownRecord town, @Nonnull World world,
                                @Nonnull List<String> campPrefabs, @Nonnull String rank, @Nonnull Random rng) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || !available || campPrefabs.isEmpty()) {
            return false;
        }
        List<String> candidates = candidates(plugin, town, world);
        if (candidates.isEmpty()) {
            return false;
        }
        slot.setRescueCharacterId(candidates.get(rng.nextInt(candidates.size())));
        slot.setRescueSkillBoosts(pickBoosts(rng, boostCount(rank)));
        slot.setRescueCampPrefabs(campPrefabs);
        return true;
    }

    @Nonnull
    static List<String> pickBoosts(@Nonnull Random rng, int count) {
        List<TownsfolkSkill> skills = new ArrayList<>(List.of(TownsfolkSkill.values()));
        java.util.Collections.shuffle(skills, rng);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, skills.size()); i++) {
            out.add(skills.get(i).id);
        }
        return List.copyOf(out);
    }

    /**
     * Places the camp for an accepted rescue and decides where the captive will sit.
     *
     * @return the middle of the camp, or null when no camp could be placed
     */
    @Nullable
    static Vector3d start(@Nonnull TownRecord town, @Nonnull QuestBoardSlotRecord slot, @Nonnull World world,
                          @Nonnull Store<EntityStore> store, @Nonnull Random rng) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        String characterId = slot.getRescueCharacterId();
        if (plugin == null || !available || characterId == null) {
            return null;
        }
        RescueCampCondition.Site site = findSite(town, slot, world, rng);
        if (site == null) {
            LOGGER.atWarning().log("Rescue %s: no flat ground for a camp outside town", slot.instanceIdOrEmpty());
            return null;
        }
        IPrefabBuffer prefab = PrefabSnapshotUtil.getAssetPrefabOrNull(site.prefab());
        if (prefab == null) {
            return null;
        }
        UUID eventId = UUID.randomUUID();
        RescueCampCondition.register(eventId, site);
        if (EventStartAction.startEvent(store, new WorldEventRecord(eventId, EVENT_ID, true)) == null) {
            RescueCampCondition.forget(eventId);
            LOGGER.atWarning().log("Rescue %s: the camp world event did not start", slot.instanceIdOrEmpty());
            return null;
        }
        Vector3i origin = site.origin();
        slot.setRescueEventId(eventId.toString());
        slot.setRescueCamp(new int[] {origin.x, origin.y, origin.z});

        // The captive sits just outside the camp's footprint. Nobody is placed yet: the camp is far from whoever
        // accepted the contract, and the game takes back anything added to terrain that is not active. See populate.
        slot.setRescueCaptiveSpot(new int[] {origin.x + prefab.getMaxX(site.rotation()) + 2, origin.y + 1, origin.z});
        slot.setRescueCaptiveUuid(null);
        slot.setRescueUntied(false);
        return campCentre(slot);
    }

    @Nullable
    static Vector3d campCentre(@Nonnull QuestBoardSlotRecord slot) {
        int[] camp = slot.getRescueCamp();
        return camp == null ? null : new Vector3d(camp[0] + 0.5, camp[1] + 1.0, camp[2] + 0.5);
    }

    /** The nearest place to stand at about the given height in one column, or null when there is none. */
    @Nullable
    static Vector3d standNear(@Nonnull World world, int x, int feetY, int z) {
        for (int offset : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            int y = feetY + offset;
            if (VillagerBlockUtil.isNpcStandColumn(world, x, y, z)) {
                return new Vector3d(x + 0.5, VillagerBlockUtil.resolveFeetYForStandCell(world, x, y, z), z + 0.5);
            }
        }
        return null;
    }

    /**
     * Open ground: solid footing, nothing but air or plants at feet and head, and no water. A stand that merely has
     * room, such as the top of a campfire or a crate, is not good enough for someone who has to survive arriving.
     */
    static boolean openGround(@Nonnull World world, int x, int y, int z) {
        if (!VillagerBlockUtil.isNpcStandColumn(world, x, y, z)) {
            return false;
        }
        var floor = ChunkSectionBlockUtil.blockType(world, x, y - 1, z);
        return floor != null && floor.getMaterial() == com.hypixel.hytale.protocol.BlockMaterial.Solid
            && clearCell(world, x, y, z) && clearCell(world, x, y + 1, z);
    }

    private static boolean clearCell(@Nonnull World world, int x, int y, int z) {
        var block = ChunkSectionBlockUtil.blockType(world, x, y, z);
        boolean open = block != null && (block == com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY
            || block.getMaterial() == com.hypixel.hytale.protocol.BlockMaterial.Empty);
        return open && !com.hexvane.aetherhaven.workplace.NativeExcavation.observe(world,
            new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(x, y, z)).protectedCell();
    }

    /**
     * A spot for one captor on a ring around the camp, at the camp's own level. Null when this try found none.
     *
     * @param relaxed accept any place with room to stand. Only used when no open ground has been found for a while.
     */
    @Nullable
    static Vector3d captorSpawn(@Nonnull World world, @Nonnull Vector3d camp, @Nonnull Random rng, boolean relaxed) {
        int level = (int) Math.floor(camp.y);
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double distance = CAPTOR_RING_MIN + rng.nextDouble() * (CAPTOR_RING_MAX - CAPTOR_RING_MIN);
            int x = (int) Math.floor(camp.x + Math.cos(angle) * distance), z = (int) Math.floor(camp.z + Math.sin(angle) * distance);
            if (relaxed) {
                Vector3d spot = standNear(world, x, level, z);
                if (spot != null) {
                    return spot;
                }
                continue;
            }
            for (int offset : new int[] {0, 1, -1, 2, -2, 3, -3}) {
                if (openGround(world, x, level + offset, z)) {
                    return new Vector3d(x + 0.5, VillagerBlockUtil.resolveFeetYForStandCell(world, x, level + offset, z), z + 0.5);
                }
            }
        }
        return null;
    }

    /** How many captors still have to be defeated and are not standing at the camp. */
    static int captorsOwed(int required, int defeated, int present) {
        return Math.max(0, required - defeated - present);
    }

    /** True once someone has been missing long enough, with a player at the camp, to be placed again. */
    static boolean missingLongEnough(@Nonnull String key, boolean missing, long now) {
        if (!missing) {
            MISSING_SINCE.remove(key);
            return false;
        }
        Long since = MISSING_SINCE.putIfAbsent(key, now);
        return since != null && now - since >= MISSING_GRACE_MS;
    }

    /**
     * Puts the captive and any captors who are owed at the camp. Called about once a second while a player is within
     * {@link #ARRIVAL_RADIUS}, never from inside an entity system. It is safe to call again: it only adds who is
     * missing, so a captor the game removed, or one who never appeared, cannot leave the contract unfinishable.
     */
    public static void populate(@Nonnull World world, @Nonnull UUID townId, @Nonnull String instanceId) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || !available) {
            return;
        }
        var manager = com.hexvane.aetherhaven.town.AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = manager.getTown(townId);
        QuestBoardSlotRecord slot = town == null ? null : town.findBoardSlotByInstanceId(instanceId);
        Vector3d camp = slot == null ? null : campCentre(slot);
        if (camp == null || !slot.isAccepted() || !slot.isRescueQuest()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Random rng = java.util.concurrent.ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        boolean changed = placeCaptive(plugin, town, slot, world, store, camp, rng, now);

        // Captors: whoever is known to be standing, plus as many more as are still owed.
        List<String> present = new ArrayList<>();
        List<String> unseen = new ArrayList<>();
        for (String id : slot.raidSpawnedEntityUuidsOrEmpty()) {
            try {
                var ref = store.getExternalData().getRefFromUUID(UUID.fromString(id.trim()));
                if (ref == null || !ref.isValid()) {
                    unseen.add(id);
                } else if (store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType()) == null) {
                    present.add(id);
                }
            } catch (IllegalArgumentException invalid) {
                // dropped
            }
        }
        // Someone unseen may only have wandered out of loaded terrain. Give them a moment before replacing them.
        if (!missingLongEnough(instanceId + "|captors", !unseen.isEmpty(), now)) {
            present.addAll(unseen);
        }
        List<String> roster = slot.raidMobRoleIdsOrEmpty();
        int owed = slot.isRescueUntied() || roster.isEmpty() ? 0
            : captorsOwed(slot.getRaidKillRequired(), slot.getRaidKillProgress(), present.size());
        int ownerLevel = com.hexvane.aetherhaven.leveling.LevelingIntegration.ownerLevel(town, store);
        if (com.hexvane.aetherhaven.leveling.LevelingIntegration.isActive() && ownerLevel <= 0) {
            owed = 0;
        }
        // Captors who have been standing for a moment can be hurt again.
        for (String id : present) {
            UUID captor = UUID.fromString(id.trim());
            Long appeared = CAPTOR_APPEARED.get(captor);
            if (appeared != null && now - appeared >= ARRIVAL_SHIELD_MS) {
                var ref = store.getExternalData().getRefFromUUID(captor);
                if (ref != null && ref.isValid()) {
                    store.tryRemoveComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.getComponentType());
                }
            }
        }
        // Open ground first. If the camp has stood for a while with none to be found, any place to stand will do.
        boolean relaxed = NO_SAFE_GROUND.getOrDefault(instanceId, 0) >= 5;
        int placed = 0;
        for (int i = 0; i < owed; i++) {
            Vector3d spot = captorSpawn(world, camp, rng, relaxed);
            if (spot == null) {
                break;
            }
            String role = roster.get(Math.floorMod(slot.getRaidKillProgress() + present.size(), roster.size()));
            String spawned = RaidQuestSpawnService.spawnRescueCaptor(town, slot, store, role, spot, camp, ownerLevel);
            if (spawned == null) {
                break;
            }
            CAPTOR_APPEARED.put(UUID.fromString(spawned), now);
            present.add(spawned);
            placed++;
        }
        if (owed > 0) {
            if (placed == 0) {
                NO_SAFE_GROUND.merge(instanceId, 1, Integer::sum);
            } else {
                NO_SAFE_GROUND.remove(instanceId);
                LOGGER.atInfo().log("Rescue %s: placed %s of %s captors owed at the camp (%s defeated of %s)",
                    instanceId, placed, owed, slot.getRaidKillProgress(), slot.getRaidKillRequired());
            }
        }
        if (!present.equals(slot.raidSpawnedEntityUuidsOrEmpty())) {
            slot.setRaidSpawnedEntityUuids(present);
            changed = true;
        }
        if (changed) {
            manager.updateTown(town);
        }
    }

    private static boolean placeCaptive(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town, @Nonnull QuestBoardSlotRecord slot,
                                        @Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Vector3d camp,
                                        @Nonnull Random rng, long now) {
        UUID held = captiveId(slot);
        var ref = held == null ? null : store.getExternalData().getRefFromUUID(held);
        boolean missing = ref == null || !ref.isValid();
        // A captive who was placed before may only be out of loaded terrain. Wait before placing them a second time.
        if (!missing || (held != null && !missingLongEnough(slot.instanceIdOrEmpty() + "|captive", true, now))) {
            if (!missing) {
                MISSING_SINCE.remove(slot.instanceIdOrEmpty() + "|captive");
            }
            return false;
        }
        String characterId = slot.getRescueCharacterId();
        if (characterId == null) {
            return false;
        }
        var pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        if (pool.isCheckedOut(town.getTownId(), characterId)) {
            if (held != null) {
                // Our own earlier captive is gone for good. Free their place so they can be put back.
                CAPTIVES.remove(held);
                TownsfolkSpawnService.release(world, plugin, town.getTownId(), characterId);
            } else {
                // Somebody else came to town as this person since the contract was accepted. Rescue another.
                List<String> others = candidates(plugin, town, world);
                if (others.isEmpty()) {
                    return false;
                }
                characterId = others.get(rng.nextInt(others.size()));
                slot.setRescueCharacterId(characterId);
            }
        }
        int[] saved = slot.getRescueCaptiveSpot();
        Vector3d spot = saved != null ? standNear(world, saved[0], saved[1], saved[2]) : null;
        if (spot == null) {
            spot = captorSpawn(world, camp, rng, true);
        }
        if (spot == null) {
            return false;
        }
        var captive = TownsfolkSpawnService.trySpawn(world, plugin, town, store, spot, TownsfolkAssignmentKinds.IDLE, characterId, rng);
        if (captive.isEmpty()) {
            return true; // the character may have changed above; save that
        }
        slot.setRescueCaptiveUuid(captive.get().entityUuid().toString());
        CAPTIVES.put(captive.get().entityUuid(), new Captive(spot, !slot.isRescueUntied()));
        LOGGER.atInfo().log("Rescue %s: %s placed at the camp, %s", slot.instanceIdOrEmpty(), characterId,
            slot.isRescueUntied() ? "already free" : "bound");
        MISSING_SINCE.remove(slot.instanceIdOrEmpty() + "|captive");
        return true;
    }

    /**
     * Takes the camp down. The world event restores the land it covered.
     *
     * @param keepCaptive true when the contract was completed and the captive is about to move in
     */
    static void stop(@Nonnull QuestBoardSlotRecord slot, @Nonnull UUID townId, @Nonnull World world,
                     @Nonnull Store<EntityStore> store, boolean keepCaptive) {
        String event = slot.getRescueEventId();
        if (event != null && available) {
            try {
                UUID eventId = UUID.fromString(event);
                RescueCampCondition.forget(eventId);
                // Removing the event entity must not happen while the store is being processed.
                world.execute(() -> {
                    Store<EntityStore> live = world.getEntityStore().getStore();
                    if (live.getResource(WorldEventManager.getResourceType()).get(eventId) != null) {
                        EventStopAction.stopEvent(live, eventId, (running, id) -> running.uuid.equals(id));
                    }
                });
            } catch (IllegalArgumentException invalid) {
                LOGGER.atWarning().log("Rescue %s has an invalid camp event id", slot.instanceIdOrEmpty());
            }
        }
        UUID held = captiveId(slot);
        if (held != null) {
            CAPTIVES.remove(held);
            // Someone who is about to move in must not arrive still wearing the ropes.
            var ref = keepCaptive ? store.getExternalData().getRefFromUUID(held) : null;
            var worn = ref == null || !ref.isValid() ? null
                : store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType());
            if (worn != null && worn.getModel() != null && wearsRopes(worn.getModel())) {
                store.putComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.component.ModelComponent(withRopes(worn.getModel(), false)));
            }
        }
        slot.setRescueUntied(false);
        if (!keepCaptive) {
            RaidQuestSpawnService.removeEntityByUuidString(store, slot.getRescueCaptiveUuid());
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            String characterId = slot.getRescueCharacterId();
            if (plugin != null && characterId != null && slot.getRescueCaptiveUuid() != null) {
                TownsfolkSpawnService.release(world, plugin, townId, characterId);
            }
            slot.setRescueCaptiveUuid(null);
        }
        MISSING_SINCE.remove(slot.instanceIdOrEmpty() + "|captive");
        MISSING_SINCE.remove(slot.instanceIdOrEmpty() + "|captors");
        REPLACED.remove(slot.instanceIdOrEmpty());
        NO_SAFE_GROUND.remove(slot.instanceIdOrEmpty());
        slot.setRescueEventId(null);
        slot.setRescueCamp(null);
        slot.setRescueCaptiveSpot(null);
    }

    /** Resolves a camp prefab given as a path under {@code Server/Prefabs}. */
    @Nullable
    public static Path prefabPath(@Nonnull String configured) {
        String trimmed = configured.trim();
        for (String candidate : List.of("Server/Prefabs/" + trimmed, "Prefabs/" + trimmed, trimmed)) {
            Path path = Path.of(candidate);
            if (PrefabSnapshotUtil.getAssetPrefabOrNull(path) != null) {
                return path;
            }
        }
        return null;
    }

    @Nullable
    private static RescueCampCondition.Site findSite(@Nonnull TownRecord town, @Nonnull QuestBoardSlotRecord slot,
                                                     @Nonnull World world, @Nonnull Random rng) {
        List<String> prefabs = slot.rescueCampPrefabsOrEmpty();
        if (prefabs.isEmpty()) {
            return null;
        }
        RaidApproachDirection locked = null;
        for (int attempt = 0; attempt < SITE_ATTEMPTS; attempt++) {
            Path path = prefabPath(prefabs.get(rng.nextInt(prefabs.size())));
            IPrefabBuffer prefab = path == null ? null : PrefabSnapshotUtil.getAssetPrefabOrNull(path);
            if (prefab == null) {
                continue;
            }
            RaidSpawnPositionFinder.Result spot =
                RaidSpawnPositionFinder.findForMob(world, town, slot.raidApproachDirectionEnum(), locked, rng, attempt);
            if (spot == null) {
                continue;
            }
            locked = spot.direction();
            PrefabRotation rotation = PrefabRotation.VALUES[rng.nextInt(PrefabRotation.VALUES.length)];
            int x = (int) Math.floor(spot.position().x), z = (int) Math.floor(spot.position().z);
            int floor = (int) Math.floor(spot.position().y) - 1;
            if (isLevel(world, x, z, floor, prefab, rotation)) {
                slot.setRaidApproachDirection(locked.id());
                return new RescueCampCondition.Site(path, new Vector3i(x, floor, z), rotation);
            }
        }
        return null;
    }

    /** The centre, corners and edge midpoints of the footprint must all sit close to the camp's floor. */
    private static boolean isLevel(@Nonnull World world, int x, int z, int floor, @Nonnull IPrefabBuffer prefab, @Nonnull PrefabRotation rotation) {
        int[] xs = {x + prefab.getMinX(rotation), x, x + prefab.getMaxX(rotation)};
        int[] zs = {z + prefab.getMinZ(rotation), z, z + prefab.getMaxZ(rotation)};
        for (int px : xs) {
            for (int pz : zs) {
                if (!levelColumn(ChunkSectionBlockUtil.columnHeight(world, px, pz), floor)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean levelColumn(int surface, int floor) {
        return Math.abs(surface - floor) <= MAX_HEIGHT_DELTA;
    }
}
