package com.hexvane.aetherhaven.restaurant;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hexvane.aetherhaven.asset.ClasspathResourceScanner;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Every dish a restaurant can serve, by the Menu level that adds it. Other packs may add their own. */
public final class RestaurantMenuCatalog {
    public static final String PATH = "Server/Aetherhaven/RestaurantDishes";
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** Dishes that read as one line on the menu: same perk, strength and length. */
    public record Course(@Nonnull MealPerk perk, int percent, int gameHours, @Nonnull List<String> itemIds) {}

    private final List<RestaurantDish> dishes;

    public RestaurantMenuCatalog(@Nonnull Collection<RestaurantDish> entries) {
        Map<String, RestaurantDish> byItem = new LinkedHashMap<>();
        for (RestaurantDish dish : entries) {
            if (dish != null && dish.valid()) {
                byItem.put(dish.itemId(), dish);
            }
        }
        List<RestaurantDish> sorted = new ArrayList<>(byItem.values());
        sorted.sort(Comparator.comparingInt(RestaurantDish::menuLevel).thenComparing(RestaurantDish::itemId));
        dishes = List.copyOf(sorted);
    }

    @Nonnull
    public static RestaurantMenuCatalog empty() {
        return new RestaurantMenuCatalog(List.of());
    }

    @Nonnull
    public List<RestaurantDish> all() {
        return dishes;
    }

    /** Everything on the menu at this Menu level, earliest dishes first. */
    @Nonnull
    public List<RestaurantDish> served(int menuLevel) {
        return dishes.stream().filter(d -> d.menuLevel() <= menuLevel).toList();
    }

    /** Only the dishes this Menu level adds. */
    @Nonnull
    public List<RestaurantDish> addedAt(int menuLevel) {
        return dishes.stream().filter(d -> d.menuLevel() == menuLevel).toList();
    }

    @Nonnull
    public static List<Course> courses(@Nonnull List<RestaurantDish> dishes) {
        Map<String, List<RestaurantDish>> grouped = new LinkedHashMap<>();
        for (RestaurantDish dish : dishes) {
            String key = dish.mealPerk() + "|" + dish.percent() + "|" + dish.gameHours();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(dish);
        }
        List<Course> out = new ArrayList<>(grouped.size());
        for (List<RestaurantDish> group : grouped.values()) {
            RestaurantDish first = group.getFirst();
            out.add(new Course(first.mealPerk(), first.percent(), first.gameHours(),
                group.stream().map(RestaurantDish::itemId).toList()));
        }
        return out;
    }

    @Nonnull
    public static RestaurantMenuCatalog load(@Nonnull ClassLoader loader) {
        List<RestaurantDish> entries = new ArrayList<>();
        for (String path : ClasspathResourceScanner.listJsonFiles(loader, PATH + "/")) {
            try (InputStream in = loader.getResourceAsStream(path)) {
                if (in != null) {
                    read(in, entries, path);
                }
            } catch (Exception ex) {
                LOG.atWarning().withCause(ex).log("Unable to read restaurant dish %s", path);
            }
        }
        for (var file : AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(PATH)) {
            try (InputStream in = Files.newInputStream(file.absolutePath())) {
                read(in, entries, String.valueOf(file.absolutePath()));
            } catch (Exception ex) {
                LOG.atWarning().withCause(ex).log("Unable to read restaurant dish %s", file.absolutePath());
            }
        }
        return new RestaurantMenuCatalog(entries);
    }

    static void read(@Nonnull InputStream in, @Nonnull List<RestaurantDish> entries, @Nonnull String source) {
        RestaurantDish dish = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), RestaurantDish.class);
        if (dish != null && dish.valid()) {
            entries.add(dish);
        } else {
            LOG.atWarning().log("Ignoring invalid restaurant dish %s", source);
        }
    }
}
