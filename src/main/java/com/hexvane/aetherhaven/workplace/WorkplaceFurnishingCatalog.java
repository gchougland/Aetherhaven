package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.asset.*;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Pack supplied furnishings. Later pack definitions replace a matching block ID. */
public final class WorkplaceFurnishingCatalog {
    public static final String PATH="Server/Aetherhaven/WorkplaceFurnishings";
    private static final HytaleLogger LOG=HytaleLogger.forEnclosingClass();
    private final Map<String,WorkplaceFurnishing> entries;
    public WorkplaceFurnishingCatalog(Collection<WorkplaceFurnishing> definitions) {
        var values=new LinkedHashMap<String,WorkplaceFurnishing>();
        for(var definition:definitions)if(definition!=null&&definition.valid())values.put(definition.blockId(),definition);
        entries=Map.copyOf(values);
    }
    public WorkplaceFurnishing find(String blockId) {return blockId==null?null:entries.get(blockId);}
    public static WorkplaceFurnishingCatalog load(ClassLoader loader) {
        var entries=new ArrayList<WorkplaceFurnishing>();
        for(String path:ClasspathResourceScanner.listJsonFiles(loader,PATH+"/")) {
            try(var in=loader.getResourceAsStream(path)){if(in!=null)read(in,entries);}
            catch(Exception ex){LOG.atWarning().withCause(ex).log("Unable to read workplace furnishing %s",path);}
        }
        for(var file:AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(PATH)) {
            try(var in=Files.newInputStream(file.absolutePath())){read(in,entries);}
            catch(Exception ex){LOG.atWarning().withCause(ex).log("Unable to read workplace furnishing %s",file.absolutePath());}
        }
        return new WorkplaceFurnishingCatalog(entries);
    }
    private static void read(InputStream in,List<WorkplaceFurnishing> entries) {
        var entry=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),WorkplaceFurnishing.class);
        if(entry!=null&&entry.valid())entries.add(entry);
        else LOG.atWarning().log("Ignoring invalid workplace furnishing definition");
    }
}
