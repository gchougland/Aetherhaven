package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.restaurant.PlotRestaurantState;
import com.hexvane.aetherhaven.restaurant.RestaurantDish;
import com.hexvane.aetherhaven.restaurant.RestaurantMenuCatalog;
import com.hexvane.aetherhaven.restaurant.RestaurantMenuCatalog.Course;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Kind;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Node;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The restaurant's menu in the Building Upgrades details panel: what is served now (on the Restaurant node) or what a
 * Chef's menu level adds (on that level's node), each line with its perk in one sentence.
 */
public final class RestaurantMenuBoardUi {
    /**
     * Lines that fit the details panel. A line is 76 high and the heading 18. On the Restaurant node the board has the
     * 294 left by the hidden description and purchase rows: three lines (246) and the provisions text (36). On a
     * menu node it has the description's 108: one line (94) and the one line "more" note (14).
     */
    static final int MENU_ROWS = 3;
    static final int LEVEL_ROWS = 1;
    /** Item pictures in one line; further dishes are still named in the text. */
    static final int ICONS = 3;

    public enum Mode {
        /** Any other tab or node: the board is hidden. */
        NONE,
        /** The Restaurant node: everything served now, plus what the Provisions add. */
        MENU,
        /** A Chef's menu node: only the dishes that level adds. */
        LEVEL
    }

    private RestaurantMenuBoardUi() {}

    @Nonnull
    public static Mode mode(@Nonnull String tab, @Nullable Node node) {
        if (!"Restaurant".equals(tab) || node == null) {
            return Mode.NONE;
        }
        if (node.kind() == Kind.FOUNDATION) {
            return Mode.MENU;
        }
        if (node.kind() == Kind.RESTAURANT && RestaurantUpgrades.Branch.MENU.name().equals(node.branch())) {
            return Mode.LEVEL;
        }
        return Mode.NONE;
    }

    /**
     * @param state this restaurant's saved levels, or null when no restaurant is selected
     * @param farmAndRanchFood food points from the town's farms and ranches; Provisions never add more than this
     */
    public static void apply(
        @Nonnull UICommandBuilder c,
        @Nonnull Mode mode,
        @Nullable Node node,
        @Nullable PlotRestaurantState state,
        @Nonnull RestaurantMenuCatalog menu,
        int farmAndRanchFood
    ) {
        c.set("#MenuBoard.Visible", mode != Mode.NONE);
        c.clear("#MenuBoardRows");
        if (mode == Mode.NONE) {
            return;
        }
        boolean current = mode == Mode.MENU;
        int menuLevel = state != null ? state.getMenuLevel() : 0;
        List<RestaurantDish> dishes = current ? menu.served(menuLevel) : menu.addedAt(node != null ? node.tier() : 0);
        List<Course> courses = RestaurantMenuCatalog.courses(dishes);
        int full = current && state != null ? RestaurantUpgrades.provisionsFoodPoints(state.getProvisionsLevel()) : 0;
        int rows = Math.min(courses.size(), current ? MENU_ROWS : LEVEL_ROWS);
        if (current && courses.size() > MENU_ROWS && full > 0) {
            // Three lines, the "more" note and the provisions line do not all fit: give up one line.
            rows = MENU_ROWS - 1;
        }
        c.set("#MenuBoardHeading.TextSpans", t(current ? "board.menuHeading" : "board.levelHeading"));
        for (int i = 0; i < rows; i++) {
            Course course = courses.get(i);
            c.append("#MenuBoardRows", "Aetherhaven/RestaurantMenuRow.ui");
            String row = "#MenuBoardRows[" + i + "]";
            List<String> items = course.itemIds();
            for (int k = 0; k < ICONS; k++) {
                boolean shown = k < items.size();
                c.set(row + " #Dish" + k + ".Visible", shown);
                if (shown) {
                    c.set(row + " #Dish" + k + ".ItemId", items.get(k));
                }
            }
            c.set(row + " #Dishes.TextSpans", dishNames(items));
            c.set(row + " #Perk.TextSpans", perkLine(course));
        }
        boolean plain = courses.isEmpty();
        boolean more = courses.size() > rows;
        c.set("#MenuBoardNote.Visible", plain);
        if (plain) {
            c.set("#MenuBoardNote.TextSpans", t(current ? "board.plain" : "board.empty"));
        }
        c.set("#MenuBoardMore.Visible", more);
        if (more) {
            c.set("#MenuBoardMore.TextSpans", t("board.more").param("count", String.valueOf(courses.size() - rows)));
        }
        c.set("#MenuBoardProvisions.Visible", full > 0);
        if (full > 0) {
            int food = RestaurantUpgrades.provisionsFoodPoints(state.getProvisionsLevel(), farmAndRanchFood);
            c.set("#MenuBoardProvisions.TextSpans", food >= full
                ? t("board.provisions").param("food", String.valueOf(food))
                : t("board.provisionsShort").param("food", String.valueOf(food)).param("full", String.valueOf(full)));
        }
    }

    /** One plain sentence for a perk, with the dish's own numbers. */
    @Nonnull
    public static Message perkLine(@Nonnull Course course) {
        return t("perk." + course.perk().id)
            .param("percent", String.valueOf(course.percent()))
            .param("hours", String.valueOf(course.gameHours()));
    }

    @Nonnull
    private static Message dishNames(@Nonnull List<String> itemIds) {
        Message names = UiMaterialLabels.itemNameMessage(itemIds.getFirst());
        for (int i = 1; i < itemIds.size(); i++) {
            names = Message.join(names, Message.raw(", "), UiMaterialLabels.itemNameMessage(itemIds.get(i)));
        }
        return names;
    }

    @Nonnull
    private static Message t(@Nonnull String key) {
        return Message.translation("aetherhaven_ui_town.aetherhaven.ui.restaurantUpgrades." + key);
    }
}
