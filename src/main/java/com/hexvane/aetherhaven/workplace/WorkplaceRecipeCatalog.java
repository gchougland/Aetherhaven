package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.*;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class WorkplaceRecipeCatalog {
    public static final String PATH="Server/Aetherhaven/WorkplaceRecipes";
    private static final HytaleLogger LOG=HytaleLogger.forEnclosingClass();
    private final Map<String,WorkplaceRecipe> configured;
    private record Snapshot(Map<String,WorkplaceRecipe> recipes,List<WorkplaceRecipe> sorted) {}
    private volatile Snapshot snapshot;
    private WorkplaceRecipeCatalog(Map<String,WorkplaceRecipe> recipes) {
        this.configured=Map.copyOf(recipes);
        replace(recipes);
    }
    private void replace(Map<String,WorkplaceRecipe> recipes) {
        snapshot=new Snapshot(Map.copyOf(recipes),recipes.values().stream()
            .sorted(Comparator.comparing(WorkplaceRecipe::name).thenComparing(WorkplaceRecipe::id)).toList());
    }
    public static WorkplaceRecipeCatalog empty() { return new WorkplaceRecipeCatalog(Map.of()); }
    public WorkplaceRecipe find(String id) { return snapshot.recipes().get(id); }
    public List<WorkplaceRecipe> all() { return snapshot.sorted(); }
    /** Rebuild only on asset changes, never during work ticks or a page redraw. */
    public void refreshNativeRecipes() {
        var store=com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe.getAssetStore();
        if(store==null)return;
        replace(NativeWorkplaceRecipes.merge(configured,store.getAssetMap().getAssetMap()));
    }
    public static WorkplaceRecipeCatalog load(ClassLoader loader) {
        var map=new LinkedHashMap<String,WorkplaceRecipe>();
        // Always include bundled rules, even if another mod is the only pack contributing this directory.
        for(String path:ClasspathResourceScanner.listJsonFiles(loader,PATH+"/")) {
            try(var stream=loader.getResourceAsStream(path)) { if(stream!=null) read(stream,path,map); }
            catch(Exception e) { LOG.atWarning().withCause(e).log("Unable to read workplace recipe %s",path); }
        }
        for(var file:AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(PATH)) {
            try(var stream=Files.newInputStream(file.absolutePath())) { read(stream,file.absolutePath().toString(),map); }
            catch(Exception e) { LOG.atWarning().withCause(e).log("Unable to read workplace recipe %s",file.absolutePath()); }
        }
        var catalog=new WorkplaceRecipeCatalog(map);
        catalog.refreshNativeRecipes();
        return catalog;
    }
    private static void read(InputStream stream,String label,Map<String,WorkplaceRecipe> map) throws IOException {
        var rule=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),WorkplaceRecipe.class);
        if(rule==null || !rule.valid()) { LOG.atWarning().log("Invalid workplace recipe %s",label); return; }
        if (!rule.type().availableForNewWork()) return;
        map.put(rule.id(),rule);
    }
}
