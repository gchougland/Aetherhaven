package com.hexvane.aetherhaven.construction.prefabmaterials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.plotcreator.CustomBuildingsPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;

public final class PrefabMaterialsWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PrefabMaterialsWriter() {}

    @Nonnull
    public static Path outputFile(@Nonnull Path dataDirectory, @Nonnull String constructionId) {
        return CustomBuildingsPaths.prefabMaterialsDirectory(dataDirectory).resolve(constructionId.trim() + ".json");
    }

    public static void write(
        @Nonnull Path outputFile,
        @Nonnull String constructionId,
        @Nonnull String prefabPath,
        @Nonnull List<MaterialRequirement> materials
    ) throws IOException {
        PrefabMaterialsFile file = new PrefabMaterialsFile();
        file.constructionId = constructionId.trim();
        file.prefabPath = prefabPath.trim();
        file.materials = materials;
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, GSON.toJson(file) + "\n", StandardCharsets.UTF_8);
    }

    private static final class PrefabMaterialsFile {
        @SerializedName("constructionId")
        String constructionId;

        @SerializedName("prefabPath")
        String prefabPath;

        @SerializedName("materials")
        List<MaterialRequirement> materials;
    }
}
