package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Grower plans by work type. A later pack's plan for the same work type replaces the bundled one. */
public final class GrowerCatalog {
    public static final String PATH = "Server/Aetherhaven/GrowerPlans";
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final Map<TownsfolkWorkType, GrowerPlan> plans;

    public GrowerCatalog(Collection<GrowerPlan> definitions) {
        var values = new EnumMap<TownsfolkWorkType, GrowerPlan>(TownsfolkWorkType.class);
        for (var plan : definitions) if (plan != null && plan.valid()) values.put(plan.type(), plan);
        plans = Collections.unmodifiableMap(values);
    }

    public static GrowerCatalog empty() { return new GrowerCatalog(List.of()); }

    public GrowerPlan find(TownsfolkWorkType type) { return type == null ? null : plans.get(type); }

    public Collection<GrowerPlan> all() { return plans.values(); }

    /** True when the block, given by its base id, is a bed of any grower site. */
    public boolean isBed(String baseBlock) { return plans.values().stream().anyMatch(plan -> plan.isBed(baseBlock)); }

    /** Bundled plans only, for tests and tools that run without the asset packs. */
    public static GrowerCatalog loadFromClasspath(ClassLoader loader) {
        var entries = new ArrayList<GrowerPlan>();
        readClasspath(loader, entries);
        return new GrowerCatalog(entries);
    }

    public static GrowerCatalog load(ClassLoader loader) {
        var entries = new ArrayList<GrowerPlan>();
        readClasspath(loader, entries);
        for (var file : AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(PATH)) {
            try (var in = Files.newInputStream(file.absolutePath())) { read(in, file.absolutePath().toString(), entries); }
            catch (Exception ex) { LOG.atWarning().withCause(ex).log("Unable to read grower plan %s", file.absolutePath()); }
        }
        return new GrowerCatalog(entries);
    }

    private static void readClasspath(ClassLoader loader, List<GrowerPlan> entries) {
        for (String path : ClasspathResourceScanner.listJsonFiles(loader, PATH + "/")) {
            try (var in = loader.getResourceAsStream(path)) { if (in != null) read(in, path, entries); }
            catch (Exception ex) { LOG.atWarning().withCause(ex).log("Unable to read grower plan %s", path); }
        }
    }

    private static void read(InputStream in, String label, List<GrowerPlan> entries) {
        var plan = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), GrowerPlan.class);
        if (plan != null && plan.valid()) entries.add(plan);
        else LOG.atWarning().log("Ignoring invalid grower plan %s", label);
    }
}
