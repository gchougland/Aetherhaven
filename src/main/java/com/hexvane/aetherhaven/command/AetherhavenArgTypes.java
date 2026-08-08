package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import com.hexvane.aetherhaven.pathtool.PathCommitRecord;
import com.hexvane.aetherhaven.pathtool.PathToolRegistry;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.questboard.QuestBoardCatalog;
import com.hexvane.aetherhaven.questboard.data.QuestBoardRankTierJson;
import com.hexvane.aetherhaven.reputation.ReputationRewardCatalog;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hexvane.aetherhaven.worldnpc.WorldNpcRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Custom argument types with tab completion for Aetherhaven commands. */
public final class AetherhavenArgTypes {
    private AetherhavenArgTypes() {}

    public static final SingleArgumentType<String> ONLINE_PLAYER_NAME = new SingleArgumentType<>(
        langName("onlinePlayerName"),
        langUsage("onlinePlayerName"),
        "PlayerName"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            World world = AetherhavenCommandSuggest.playerWorld(sender);
            if (world != null) {
                suggestPlayersInWorld(result, textAlreadyEntered, world);
                return;
            }
            for (World loaded : Universe.get().getWorlds().values()) {
                suggestPlayersInWorld(result, textAlreadyEntered, loaded);
            }
        }
    };

    public static final SingleArgumentType<String> CONSTRUCTION_ID = new SingleArgumentType<>(
        langName("constructionId"),
        langUsage("constructionId"),
        "plot_inn",
        "plot_barn"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (plugin == null) {
                return;
            }
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, plugin.getConstructionCatalog().ids());
        }

        @Override
        public int getSuggestionValueCount() {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            return plugin != null ? plugin.getConstructionCatalog().ids().size() : -1;
        }
    };

    public static final SingleArgumentType<String> CUSTOM_BUILDING_ID = new SingleArgumentType<>(
        langName("customBuildingId"),
        langUsage("customBuildingId"),
        "plot_my_house"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (plugin == null) {
                return;
            }
            List<String> values = new ArrayList<>(AetherhavenCommandSuggest.customBuildingIds(plugin));
            values.addAll(plugin.getConstructionCatalog().ids());
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, values);
        }
    };

    public static final SingleArgumentType<String> QUEST_ID = new SingleArgumentType<>(
        langName("questId"),
        langUsage("questId"),
        "q_build_inn"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (plugin == null) {
                return;
            }
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, plugin.getQuestCatalog().all().keySet());
        }
    };

    public static final SingleArgumentType<String> QUEST_BOARD_RANK = new SingleArgumentType<>(
        langName("questBoardRank"),
        langUsage("questBoardRank"),
        "E",
        "S"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (plugin == null) {
                return;
            }
            QuestBoardCatalog catalog = plugin.getQuestBoardCatalog();
            List<String> ranks = new ArrayList<>();
            for (QuestBoardRankTierJson tier : catalog.ranks()) {
                String id = tier.idOrEmpty();
                if (!id.isBlank()) {
                    ranks.add(id);
                }
            }
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, ranks);
        }
    };

    public static final SingleArgumentType<String> TOWN_MEMBER_ROLE = fixedStrings(
        "townMemberRole",
        new String[] {"BUILD", "QUEST", "BOTH"},
        "BUILD"
    );

    public static final SingleArgumentType<String> NEEDS_WHICH = fixedStrings(
        "needsWhich",
        new String[] {"hunger", "energy", "fun"},
        "hunger"
    );

    public static final SingleArgumentType<String> TOWNSFOLK_ASSIGNMENT_KIND = fixedStrings(
        "townsfolkAssignmentKind",
        new String[] {
            TownsfolkAssignmentKinds.IDLE,
            TownsfolkAssignmentKinds.TOURIST,
            TownsfolkAssignmentKinds.GUARD,
            TownsfolkAssignmentKinds.GUILD_ADVENTURER
        },
        TownsfolkAssignmentKinds.IDLE
    );

    public static final SingleArgumentType<String> STARTER_TOWN_PRESET = fixedStrings(
        "starterTownPreset",
        new String[] {"minimal", "full"},
        "minimal"
    );

    public static final SingleArgumentType<String> STARTER_TOWN_LAYOUT = fixedStrings(
        "starterTownLayout",
        new String[] {"line", "generated"},
        "line"
    );

    public static final SingleArgumentType<String> VILLAGER_NPC_ROLE = new SingleArgumentType<>(
        langName("villagerNpcRole"),
        langUsage("villagerNpcRole"),
        "Aetherhaven_Merchant",
        "Aetherhaven_Blacksmith"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (plugin == null) {
                return;
            }
            VillagerDefinitionCatalog catalog = plugin.getVillagerDefinitionCatalog();
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, catalog.allByNpcRoleId().keySet());
        }
    };

    public static final SingleArgumentType<String> REWARD_ID = new SingleArgumentType<>(
        langName("rewardId"),
        langUsage("rewardId"),
        "rep_merchant_50"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            List<String> ids = new ArrayList<>();
            for (ReputationRewardCatalog.ReputationRewardDefinition def : ReputationRewardCatalog.allDefinitions()) {
                if (def.rewardId() != null && !def.rewardId().isBlank()) {
                    ids.add(def.rewardId());
                }
            }
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, ids);
        }
    };

    public static final SingleArgumentType<String> DIALOGUE_TREE_ID = new SingleArgumentType<>(
        langName("dialogueTreeId"),
        langUsage("dialogueTreeId"),
        "elder_intro"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (plugin == null) {
                return;
            }
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, plugin.getDialogueCatalog().all().keySet());
        }
    };

    public static final SingleArgumentType<String> DIALOGUE_ENTRY_NODE = new SingleArgumentType<>(
        langName("dialogueEntryNode"),
        langUsage("dialogueEntryNode"),
        "start"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (plugin == null) {
                return;
            }
            TreeSet<String> nodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (DialogueTreeDefinition tree : plugin.getDialogueCatalog().all().values()) {
                nodes.addAll(tree.getNodes().keySet());
            }
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, nodes);
        }
    };

    public static final SingleArgumentType<String> TOWNSFOLK_CHARACTER_ID = new SingleArgumentType<>(
        langName("townsfolkCharacterId"),
        langUsage("townsfolkCharacterId"),
        "female_elf_01"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (plugin == null) {
                return;
            }
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, plugin.getTownsfolkCharacterCatalog().allIds());
        }
    };

    public static final SingleArgumentType<String> WORLD_NPC_PLACEMENT_ID = new SingleArgumentType<>(
        langName("worldNpcPlacementId"),
        langUsage("worldNpcPlacementId"),
        "hub_welcome"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            World world = AetherhavenCommandSuggest.playerWorld(sender);
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (world == null || plugin == null) {
                return;
            }
            WorldNpcRegistry registry = AetherhavenWorldRegistries.getOrCreateWorldNpcRegistry(world, plugin);
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, registry.listPlacementIds());
        }
    };

    public static final SingleArgumentType<String> TOWN_NAME = new SingleArgumentType<>(
        langName("townName"),
        langUsage("townName"),
        "Oak Hollow",
        "Starterville"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public boolean isGreedyString() {
            return true;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            World world = AetherhavenCommandSuggest.playerWorld(sender);
            if (world == null) {
                return;
            }
            AetherhavenCommandSuggest.suggestTownNames(result, textAlreadyEntered, world);
        }
    };

    public static final SingleArgumentType<String> PLOT_ID = new SingleArgumentType<>(
        langName("plotId"),
        langUsage("plotId"),
        "00000000-0000-0000-0000-000000000001"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            World world = AetherhavenCommandSuggest.playerWorld(sender);
            PlayerRef playerRef = AetherhavenCommandSuggest.playerRef(sender);
            if (world == null || playerRef == null) {
                return;
            }
            TownRecord town = AetherhavenCommandSuggest.primaryPlayerTown(playerRef, world);
            if (town == null) {
                return;
            }
            AetherhavenCommandSuggest.suggestPlotIds(result, textAlreadyEntered, town);
        }
    };

    public static final SingleArgumentType<String> VILLAGER_TARGET = new SingleArgumentType<>(
        langName("villagerTarget"),
        langUsage("villagerTarget"),
        "Aetherhaven_Elder",
        "Elder"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            World world = AetherhavenCommandSuggest.playerWorld(sender);
            PlayerRef playerRef = AetherhavenCommandSuggest.playerRef(sender);
            if (world == null || playerRef == null) {
                return;
            }
            TownRecord town = AetherhavenCommandSuggest.primaryPlayerTown(playerRef, world);
            if (town == null) {
                return;
            }
            AetherhavenCommandSuggest.suggestVillagerTargets(result, textAlreadyEntered, town);
        }
    };

    public static final SingleArgumentType<String> PATH_COMMIT_ID = new SingleArgumentType<>(
        langName("pathCommitId"),
        langUsage("pathCommitId"),
        "00000000-0000-0000-0000-000000000001"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            World world = AetherhavenCommandSuggest.playerWorld(sender);
            AetherhavenPlugin plugin = AetherhavenCommandSuggest.plugin();
            if (world == null || plugin == null) {
                return;
            }
            PathToolRegistry registry = AetherhavenWorldRegistries.getOrCreatePathToolRegistry(world, plugin);
            List<String> ids = new ArrayList<>();
            for (PathCommitRecord record : registry.all()) {
                if (record.id != null && !record.id.isBlank()) {
                    ids.add(record.id);
                }
            }
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, ids);
        }
    };

    public static final SingleArgumentType<String> TOURIST_PORTAL_ID = new SingleArgumentType<>(
        langName("touristPortalId"),
        langUsage("touristPortalId"),
        "00000000-0000-0000-0000-000000000001"
    ) {
        @Override
        public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            return input;
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            World world = AetherhavenCommandSuggest.playerWorld(sender);
            PlayerRef playerRef = AetherhavenCommandSuggest.playerRef(sender);
            if (world == null || playerRef == null) {
                return;
            }
            TownRecord town = AetherhavenCommandSuggest.primaryPlayerTown(playerRef, world);
            if (town == null) {
                return;
            }
            AetherhavenCommandSuggest.suggestTouristPortals(result, textAlreadyEntered, world, town);
        }
    };

    public static final SingleArgumentType<Integer> PLOT_INDEX = new SingleArgumentType<>(
        langName("plotIndex"),
        langUsage("plotIndex"),
        "1",
        "2"
    ) {
        @Nullable
        @Override
        public Integer parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            try {
                return Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                parseResult.fail(Message.raw("Plot index must be a number."));
                return null;
            }
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            for (int i = 1; i <= 10; i++) {
                String value = String.valueOf(i);
                if (textAlreadyEntered.isEmpty() || value.startsWith(textAlreadyEntered.trim())) {
                    result.suggest(value);
                }
            }
        }

        @Override
        public int getSuggestionValueCount() {
            return 10;
        }
    };

    public static final SingleArgumentType<Integer> GAME_HOUR = new SingleArgumentType<>(
        langName("gameHour"),
        langUsage("gameHour"),
        "6",
        "12"
    ) {
        @Nullable
        @Override
        public Integer parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            try {
                int hour = Integer.parseInt(input.trim());
                if (hour < 0 || hour > 23) {
                    parseResult.fail(Message.translation("aetherhaven_commands_help.commands.aetherhaven.time.hourInvalid"));
                    return null;
                }
                return hour;
            } catch (NumberFormatException e) {
                parseResult.fail(Message.raw("Hour must be a number."));
                return null;
            }
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            for (int hour = 0; hour < 24; hour++) {
                String value = String.valueOf(hour);
                if (textAlreadyEntered.isEmpty() || value.startsWith(textAlreadyEntered.trim())) {
                    result.suggest(value);
                }
            }
        }

        @Override
        public int getSuggestionValueCount() {
            return 24;
        }
    };

    public static final SingleArgumentType<String> CALENDAR_SEASON = fixedStrings(
        "calendarSeason",
        new String[] { "Spring", "Summer", "Autumn", "Winter" },
        "Spring"
    );

    public static final SingleArgumentType<Integer> CALENDAR_DAY = new SingleArgumentType<>(
        langName("calendarDay"),
        langUsage("calendarDay"),
        "1",
        "28"
    ) {
        @Nullable
        @Override
        public Integer parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            try {
                int day = Integer.parseInt(input.trim());
                if (day < 1 || day > AetherhavenCalendar.DAYS_PER_SEASON) {
                    parseResult.fail(Message.translation("aetherhaven_commands_help.commands.aetherhaven.argtype.calendarDay.invalid"));
                    return null;
                }
                return day;
            } catch (NumberFormatException e) {
                parseResult.fail(Message.translation("aetherhaven_commands_help.commands.aetherhaven.argtype.calendarDay.invalid"));
                return null;
            }
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            for (int day = 1; day <= AetherhavenCalendar.DAYS_PER_SEASON; day++) {
                String value = String.valueOf(day);
                if (textAlreadyEntered.isEmpty() || value.startsWith(textAlreadyEntered.trim())) {
                    result.suggest(value);
                }
            }
        }

        @Override
        public int getSuggestionValueCount() {
            return AetherhavenCalendar.DAYS_PER_SEASON;
        }
    };

    public static final SingleArgumentType<Long> CALENDAR_YEAR = new SingleArgumentType<>(
        langName("calendarYear"),
        langUsage("calendarYear"),
        "1"
    ) {
        @Nullable
        @Override
        public Long parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
            try {
                long year = Long.parseLong(input.trim());
                if (year < 1L) {
                    parseResult.fail(Message.translation("aetherhaven_commands_help.commands.aetherhaven.argtype.calendarYear.invalid"));
                    return null;
                }
                return year;
            } catch (NumberFormatException e) {
                parseResult.fail(Message.translation("aetherhaven_commands_help.commands.aetherhaven.argtype.calendarYear.invalid"));
                return null;
            }
        }

        @Override
        public void suggest(
            @Nonnull CommandSender sender,
            @Nonnull String textAlreadyEntered,
            int numParametersTyped,
            @Nonnull SuggestionResult result
        ) {
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, "1");
            AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, "2");
        }
    };

    @Nonnull
    private static SingleArgumentType<String> fixedStrings(
        @Nonnull String nameKey,
        @Nonnull String[] values,
        @Nonnull String example
    ) {
        return new SingleArgumentType<>(langName(nameKey), langUsage(nameKey), example) {
            @Override
            public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
                return input;
            }

            @Override
            public void suggest(
                @Nonnull CommandSender sender,
                @Nonnull String textAlreadyEntered,
                int numParametersTyped,
                @Nonnull SuggestionResult result
            ) {
                AetherhavenCommandSuggest.suggestPrefix(result, textAlreadyEntered, values);
            }

            @Override
            public int getSuggestionValueCount() {
                return values.length;
            }
        };
    }

    @Nonnull
    private static String langName(@Nonnull String key) {
        return "aetherhaven_commands_help.commands.aetherhaven.argtype." + key + ".name";
    }

    @Nonnull
    private static String langUsage(@Nonnull String key) {
        return "aetherhaven_commands_help.commands.aetherhaven.argtype." + key + ".usage";
    }

    private static void suggestPlayersInWorld(
        @Nonnull SuggestionResult result,
        @Nullable String partial,
        @Nonnull World world
    ) {
        for (PlayerRef online : world.getPlayerRefs()) {
            String username = online.getUsername();
            if (username != null && !username.isBlank()) {
                AetherhavenCommandSuggest.suggestPrefix(result, partial, username);
            }
        }
    }
}
