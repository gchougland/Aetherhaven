package com.hexvane.aetherhaven.guide;

import com.hexvane.aetherhaven.quest.QuestCatalog;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleTransition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerReputationMilestoneJson;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Builds plain language strings for villager fact panels in the town journal guide. */
public final class GuideVillagerFactsBuilder {
    private GuideVillagerFactsBuilder() {}

    @Nonnull
    public static Message questListMessage(@Nonnull QuestCatalog catalog, @Nonnull String npcRoleId) {
        List<String> ids = catalog.listQuestIdsAssignedToRole(npcRoleId);
        if (ids.isEmpty()) {
            return Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.noQuests");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String qid = ids.get(i);
            sb.append(i + 1).append(". ").append(catalog.displayName(qid));
        }
        return Message.raw(sb.toString());
    }

    @Nonnull
    public static Message reputationMessage(@Nonnull VillagerDefinition def) {
        List<VillagerReputationMilestoneJson> ms = def.getReputationMilestones();
        if (ms.isEmpty()) {
            return Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.noRepMilestones");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ms.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            VillagerReputationMilestoneJson m = ms.get(i);
            sb.append("At ").append(m.getMinReputation()).append(" friendship: ");
            List<String> bits = new ArrayList<>();
            String learn = m.getLearnRecipeItemId();
            if (learn != null && !learn.isBlank()) {
                bits.add("learn to craft " + itemDisplayName(learn));
            }
            String item = m.getItemId();
            if (item != null && !item.isBlank() && m.getItemCount() > 0) {
                bits.add(m.getItemCount() + " " + itemDisplayName(item));
            }
            if (bits.isEmpty()) {
                bits.add("talk to them for a surprise gift");
            }
            sb.append(String.join(", ", bits));
        }
        return Message.raw(sb.toString());
    }

    @Nonnull
    public static Message scheduleMessage(@Nonnull VillagerDefinition def) {
        VillagerScheduleDefinition sched = def.getWeeklySchedule();
        if (sched == null || sched.getTransitions().isEmpty()) {
            return Message.translation("aetherhaven_ui_journal_items_tail.aetherhaven.ui.townJournal.guide.noSchedule");
        }
        StringBuilder sb = new StringBuilder();
        List<VillagerScheduleTransition> tr = sched.getTransitions();
        for (int i = 0; i < tr.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            VillagerScheduleTransition t = tr.get(i);
            sb.append(formatDay(t)).append(" at ").append(formatTime(t)).append(": ").append(friendlyLocation(t.getLocation()));
        }
        return Message.raw(sb.toString());
    }

    @Nonnull
    private static String formatDay(@Nonnull VillagerScheduleTransition t) {
        Object d = t.getDayOfWeek();
        if (d == null) {
            return "Each day";
        }
        if (d instanceof Number n) {
            int v = n.intValue();
            return switch (v) {
                case 1 -> "Monday";
                case 2 -> "Tuesday";
                case 3 -> "Wednesday";
                case 4 -> "Thursday";
                case 5 -> "Friday";
                case 6 -> "Saturday";
                case 7 -> "Sunday";
                default -> "Day " + v;
            };
        }
        String s = d.toString().trim();
        if (s.isEmpty()) {
            return "Each day";
        }
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    @Nonnull
    private static String formatTime(@Nonnull VillagerScheduleTransition t) {
        int h = Math.max(0, Math.min(23, t.getHour()));
        int m = Math.max(0, Math.min(59, t.getMinute()));
        int hour12 = h % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        String mm = m < 10 ? "0" + m : Integer.toString(m);
        String dayPart = h < 12 ? "in the morning" : (h < 17 ? "in the afternoon" : "in the evening");
        return hour12 + ":" + mm + " " + dayPart;
    }

    @Nonnull
    private static String friendlyLocation(@Nullable String loc) {
        if (loc == null || loc.isBlank()) {
            return "wander nearby";
        }
        return switch (loc.trim().toLowerCase()) {
            case "home" -> "rest at home";
            case "work" -> "work at their shop";
            case "inn" -> "visit the inn";
            case "park" -> "stroll the commons";
            case "gaia_altar" -> "pray at the altar";
            default -> "go to " + loc;
        };
    }

    @Nonnull
    public static String itemDisplayName(@Nonnull String itemId) {
        Item it = Item.getAssetMap().getAsset(itemId.trim());
        if (it != null && it.getTranslationKey() != null && !it.getTranslationKey().isBlank()) {
            return Message.translation(it.getTranslationKey()).getAnsiMessage();
        }
        return itemId;
    }
}
