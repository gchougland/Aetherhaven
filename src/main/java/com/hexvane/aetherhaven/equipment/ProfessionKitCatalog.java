package com.hexvane.aetherhaven.equipment;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.*;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class ProfessionKitCatalog {
    public static final String PATH = "Server/Aetherhaven/ProfessionKits";
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final Map<String, ProfessionKit> kits;
    public ProfessionKitCatalog(Collection<ProfessionKit> entries) {
        var map = new LinkedHashMap<String, ProfessionKit>();
        for (var kit : entries) if (kit != null && kit.valid()) map.put(kit.itemId(), kit);
        kits = Map.copyOf(map);
    }
    public ProfessionKit find(String item) { return item == null ? null : kits.get(item); }
    public static ProfessionKitCatalog load(ClassLoader loader) {
        var entries = new ArrayList<ProfessionKit>();
        for (String path : ClasspathResourceScanner.listJsonFiles(loader, PATH + "/")) {
            try (var in = loader.getResourceAsStream(path)) { if (in != null) read(in, entries); }
            catch (Exception ex) { LOG.atWarning().withCause(ex).log("Unable to read profession kit %s", path); }
        }
        for (var file : AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(PATH)) {
            try (var in = Files.newInputStream(file.absolutePath())) { read(in, entries); }
            catch (Exception ex) { LOG.atWarning().withCause(ex).log("Unable to read profession kit %s", file.absolutePath()); }
        }
        return new ProfessionKitCatalog(entries);
    }
    private static void read(InputStream in, List<ProfessionKit> entries) {
        var kit = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ProfessionKit.class);
        if (kit != null && kit.valid()) entries.add(kit);
        else LOG.atWarning().log("Ignoring invalid profession kit definition");
    }
}
