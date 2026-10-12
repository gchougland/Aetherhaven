package com.hexvane.aetherhaven.alchemy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.asset.AetherhavenPackAssetScanner;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Predicate;

/** Reads one alchemy data file: the bundled copy, replaced by a later asset pack's file at the same path. */
final class AlchemyData {
    private AlchemyData() {}
    static final String FOLDER="Server/Aetherhaven/Alchemy";
    private static final HytaleLogger LOG=HytaleLogger.forEnclosingClass();

    /**
     * @param valid a pack's file replaces the bundled one only when this accepts it, so a typo cannot break alchemy
     * @return the data, or null when no valid file exists
     */
    static JsonObject read(ClassLoader loader,String file,Predicate<JsonObject> valid) {
        JsonObject result=null;
        try(var in=loader.getResourceAsStream(FOLDER+"/"+file)) {
            if(in!=null) {
                var bundled=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                if(valid.test(bundled))result=bundled;
            }
        } catch(Exception ex){LOG.atWarning().withCause(ex).log("Unable to read bundled alchemy data %s",file);}
        for(var pack:AetherhavenPackAssetScanner.listJsonFilesUnderAllPacks(FOLDER)) {
            if(!file.equals(pack.absolutePath().getFileName().toString()))continue;
            try(var in=Files.newBufferedReader(pack.absolutePath(),StandardCharsets.UTF_8)) {
                var parsed=JsonParser.parseReader(in).getAsJsonObject();
                if(valid.test(parsed))result=parsed;
                else LOG.atWarning().log("Ignoring invalid alchemy data %s",pack.absolutePath());
            } catch(Exception ex){LOG.atWarning().withCause(ex).log("Unable to read %s",pack.absolutePath());}
        }
        if(result==null)LOG.atWarning().log("No valid alchemy data file %s was found",file);
        return result;
    }
}
