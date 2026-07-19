package com.hexvane.aetherhaven.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerSystem;
import com.hexvane.aetherhaven.bard.BardPerformanceService;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleResolver;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTickState;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.time.LocalDateTime;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DialogueConditionEvaluator {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final DialogueWorldView worldView;

    public DialogueConditionEvaluator(@Nonnull DialogueWorldView worldView) {
        this.worldView = worldView;
    }

    public boolean evaluate(
        @Nullable JsonObject condition,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (condition == null) {
            return true;
        }
        return evalObject(condition, playerRef, store, npcRef);
    }

    private boolean evalObject(
        @Nonnull JsonObject o,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        String type = getString(o, "type");
        if (type == null) {
            LOGGER.atWarning().log("Dialogue condition missing type: %s", o);
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            Boolean custom = plugin.getDialogueConditionRegistry().tryEvaluate(type, o, playerRef, store, npcRef);
            if (custom != null) {
                return custom;
            }
        }
        return switch (type) {
            case "literal" -> o.get("value") != null && o.get("value").isJsonPrimitive() && o.get("value").getAsBoolean();
            case "true" -> true;
            case "false" -> false;
            case "not" -> {
                JsonObject inner = o.getAsJsonObject("condition");
                yield inner == null || !evalObject(inner, playerRef, store, npcRef);
            }
            case "and" -> {
                JsonArray arr = o.getAsJsonArray("conditions");
                if (arr == null) {
                    yield false;
                }
                boolean ok = true;
                for (JsonElement el : arr) {
                    if (!el.isJsonObject() || !evalObject(el.getAsJsonObject(), playerRef, store, npcRef)) {
                        ok = false;
                        break;
                    }
                }
                yield ok;
            }
            case "or" -> {
                JsonArray arr = o.getAsJsonArray("conditions");
                if (arr == null) {
                    yield false;
                }
                boolean any = false;
                for (JsonElement el : arr) {
                    if (el.isJsonObject() && evalObject(el.getAsJsonObject(), playerRef, store, npcRef)) {
                        any = true;
                        break;
                    }
                }
                yield any;
            }
            case "achievement" -> worldView.hasAchievement(stringOrEmpty(o, "id"));
            case "flag" -> worldView.getFlag(stringOrEmpty(o, "id"));
            case "item" -> {
                String itemId = stringOrEmpty(o, "itemId");
                if (itemId.isEmpty()) {
                    itemId = stringOrEmpty(o, "item");
                }
                int count = o.has("count") && o.get("count").isJsonPrimitive() ? o.get("count").getAsInt() : 1;
                yield worldView.hasItem(playerRef, store, itemId, Math.max(1, count));
            }
            case "villagerInTown" -> worldView.isVillagerInTown(stringOrEmpty(o, "id"));
            case "town_quest_active" -> worldView.townQuestActive(playerRef, store, stringOrEmpty(o, "questId"));
            case "town_quest_completed" -> worldView.townQuestCompleted(playerRef, store, stringOrEmpty(o, "questId"));
            case "world_quest_active" -> worldView.worldQuestActive(playerRef, store, stringOrEmpty(o, "questId"));
            case "world_quest_completed" -> worldView.worldQuestCompleted(playerRef, store, stringOrEmpty(o, "questId"));
            case "town_has_complete_plot" -> worldView.townHasCompletePlot(playerRef, store, stringOrEmpty(o, "constructionId"));
            case "aetherhaven_has_town" -> worldView.aetherhavenHasTown(playerRef, store);
            case "aetherhaven_player_can_accept_quests" -> worldView.aetherhavenPlayerCanAcceptQuests(playerRef, store);
            case "npc_binding_is_visitor" -> npcBindingIsVisitor(store, npcRef);
            case "npc_inn_pool_role" -> worldView.innPoolHasNpcRole(playerRef, store, stringOrEmpty(o, "roleId"));
            case "town_inn_visitor_pool_empty" -> worldView.townInnVisitorPoolEmpty(playerRef, store);
            case "town_npc_home_resident_house" -> worldView.townNpcHomeResidentOnHousePlot(playerRef, store, npcRef);
            case "player_holds_item" -> {
                String itemId = stringOrEmpty(o, "itemId");
                if (itemId.isEmpty()) {
                    itemId = stringOrEmpty(o, "item");
                }
                int minCount = o.has("minCount") && o.get("minCount").isJsonPrimitive() ? o.get("minCount").getAsInt() : 1;
                yield worldView.playerHoldsItemInActiveHotbar(playerRef, store, itemId, Math.max(1, minCount));
            }
            case "player_holds_any_item" -> worldView.playerHoldsAnyItemInActiveHotbar(playerRef, store);
            case "villager_gift_allowed" -> worldView.villagerGiftAllowed(playerRef, store, npcRef);
            case "gold_coin_payment_can_afford" -> {
                long cost = o.has("cost") && o.get("cost").isJsonPrimitive() ? o.get("cost").getAsLong() : 0L;
                yield worldView.goldCoinPaymentCanAfford(playerRef, store, npcRef, cost);
            }
            case "player_health_below_max" -> worldView.playerHealthBelowMax(playerRef, store);
            case "gaia_draught_unlocked" -> worldView.gaiaDraughtUnlocked(playerRef, store, npcRef);
            case "gaia_draught_charges_below_max" -> worldView.gaiaDraughtChargesBelowCapacity(playerRef, store, npcRef);
            case "gaia_draught_capacity_below_max" -> worldView.gaiaDraughtCapacityBelowMax(playerRef, store, npcRef);
            case "gaia_draught_heal_tier_below_max" -> worldView.gaiaDraughtHealTierBelowMax(playerRef, store, npcRef);
            case "gaia_draught_shard_upgrade_gold_affordable" -> worldView.gaiaDraughtShardUpgradeGoldAffordable(
                playerRef,
                store,
                npcRef
            );
            case "gaia_draught_catalyst_upgrade_gold_affordable" -> worldView.gaiaDraughtCatalystUpgradeGoldAffordable(
                playerRef,
                store,
                npcRef
            );
            case "town_quest_entity_kills_met" -> worldView.townQuestEntityKillsMet(
                playerRef,
                store,
                stringOrEmpty(o, "questId"),
                getString(o, "objectiveId")
            );
            case "priestess_heal_affordable" -> worldView.priestessHealGoldAffordable(playerRef, store, npcRef);
            case "npc_is_guild_hall_adventurer" -> worldView.npcIsGuildHallAdventurer(playerRef, store, npcRef);
            case "guard_hire_affordable" -> worldView.guardHireAffordable(playerRef, store, npcRef);
            case "player_has_unhoused_hired_guard" -> worldView.playerHasUnhousedHiredGuard(playerRef, store);
            case "guard_house_quest_target_housed" -> worldView.guardHouseQuestTargetHoused(playerRef, store);
            case "npc_is_unhoused_hired_guard" -> worldView.npcIsUnhousedHiredGuard(playerRef, store, npcRef);
            case "npc_is_quest_target" -> worldView.npcIsQuestTarget(
                playerRef,
                store,
                npcRef,
                stringOrEmpty(o, "questId")
            );
            case "npc_is_active_tourist" -> worldView.npcIsActiveTourist(playerRef, store, npcRef);
            case "npc_is_invited_unhoused_townsfolk" -> worldView.npcIsInvitedUnhousedTownsfolk(playerRef, store, npcRef);
            case "quest_target_entity_housed" -> worldView.questTargetEntityHoused(
                playerRef,
                store,
                stringOrEmpty(o, "questId")
            );
            case "quest_completed_for_npc" -> worldView.questCompletedForNpc(
                playerRef,
                store,
                npcRef,
                stringOrEmpty(o, "questId")
            );
            case "npc_schedule_at_work" -> npcScheduleAtWork(store, npcRef);
            case "bard_can_offer_music" -> bardCanOfferMusic(store, npcRef);
            case "bard_is_performing" -> BardPerformanceService.isPerforming(store, npcRef);
            case "npc_following_this_player" -> npcFollowingThisPlayer(playerRef, store, npcRef);
            case "subplugin_enabled" -> subpluginEnabled(o);
            default -> {
                LOGGER.atWarning().log("Unknown dialogue condition type: %s", type);
                yield false;
            }
        };
    }

    @Nullable
    private static String getString(@Nonnull JsonObject o, @Nonnull String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            return null;
        }
        return o.get(key).getAsString();
    }

    @Nonnull
    private static String stringOrEmpty(@Nonnull JsonObject o, @Nonnull String key) {
        String s = getString(o, key);
        return s != null ? s : "";
    }

    private static boolean subpluginEnabled(@Nonnull JsonObject o) {
        String name = stringOrEmpty(o, "subplugin");
        if (name.isEmpty()) {
            name = stringOrEmpty(o, "name");
        }
        if (name.isEmpty()) {
            return false;
        }
        return AetherhavenFeatures.isLoaded(AetherhavenPluginIds.id(name));
    }

    private static boolean npcBindingIsVisitor(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        TownVillagerBinding b = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        return b != null && TownVillagerBinding.isVisitorKind(b.getKind());
    }

    private static boolean npcFollowingThisPlayer(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        UUIDComponent playerUuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (playerUuid == null) {
            return false;
        }
        return VillagerFollowPlayerSystem.isFollowingPlayer(store, npcRef, playerUuid.getUuid());
    }

    private static boolean npcScheduleAtWork(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        VillagerScheduleTickState tick = store.getComponent(npcRef, VillagerScheduleTickState.getComponentType());
        if (tick != null
            && VillagerScheduleResolver.LOC_WORK.equalsIgnoreCase(tick.getLastAppliedScheduleSegment().trim())) {
            return true;
        }
        return npcScheduleLocationIsWorkNow(store, npcRef);
    }

    /**
     * Guild-hall bard assigned as {@link TownVillagerBinding#KIND_BARD}, or any resident on a work schedule segment.
     */
    private static boolean bardCanOfferMusic(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return false;
        }
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (binding != null && TownVillagerBinding.KIND_BARD.equals(binding.getKind())) {
            return true;
        }
        return npcScheduleAtWork(store, npcRef);
    }

    @Nullable
    private static String npcRoleId(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
            return null;
        }
        return npc.getRoleName().trim();
    }

    private static boolean npcScheduleLocationIsWorkNow(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
        TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (binding == null || TownVillagerBinding.isScheduleSuppressedKind(binding.getKind())) {
            return false;
        }
        String roleId = npcRoleId(store, npcRef);
        if (roleId == null) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        VillagerScheduleDefinition def =
            plugin.getVillagerDefinitionCatalog().effectiveSchedule(roleId, plugin.getVillagerScheduleRegistry());
        if (def == null) {
            return false;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return false;
        }
        LocalDateTime gameTime = wtr.getGameDateTime();
        String loc = VillagerScheduleResolver.activeLocationSymbol(def, gameTime);
        return loc != null && VillagerScheduleResolver.LOC_WORK.equalsIgnoreCase(loc.trim());
    }
}
