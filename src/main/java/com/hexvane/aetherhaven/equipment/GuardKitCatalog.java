package com.hexvane.aetherhaven.equipment;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.*;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class GuardKitCatalog {
    public static final String PATH = "Server/Aetherhaven/GuardKits";
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final Map<String, GuardKit> kits;
    public GuardKitCatalog(Collection<GuardKit> entries) {
        var map = new LinkedHashMap<String, GuardKit>();
        for (var kit : entries) if (kit != null && kit.valid()) map.put(kit.itemId(), kit);
        kits = Map.copyOf(map);
    }
    public GuardKit find(String item) { return item == null ? null : kits.get(item); }
    public GuardKit forProfile(String profile) { return kits.values().stream().filter(k -> k.equipmentProfile().equals(profile)).findFirst().orElse(null); }
    public static GuardKitCatalog load(ClassLoader loader) {
        var entries = new ArrayList<GuardKit>();
        for (String path : ClasspathResourceScanner.listJsonFiles(loader, PATH + "/")) {
            try (var in = loader.getResourceAsStream(path)) { if (in != null) read(in, entries); }
            catch (Exception ex) { LOG.atWarning().withCause(ex).log("Unable to read guard kit %s", path); }
        }
        for (var file : AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(PATH)) {
            try (var in = Files.newInputStream(file.absolutePath())) { read(in, entries); }
            catch (Exception ex) { LOG.atWarning().withCause(ex).log("Unable to read guard kit %s", file.absolutePath()); }
        }
        return new GuardKitCatalog(entries);
    }
    private static void read(InputStream in, List<GuardKit> entries) {
        var kit = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), GuardKit.class);
        if (kit != null && kit.valid()) entries.add(kit);
        else LOG.atWarning().log("Ignoring invalid guard kit definition");
    }
}
