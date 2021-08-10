package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.io.gson.metadata.MetadataAdapterFactory;
import org.parchmentmc.feather.metadata.SourceMetadata;
import org.parchmentmc.feather.util.SimpleVersion;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public abstract class ExtractMetadataTask extends MinecraftVersionTask {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    private void execute() throws IOException {
        final Gson gson = new GsonBuilder()
                .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                .registerTypeAdapterFactory(new MetadataAdapterFactory())
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        final File output = this.getOutput().getAsFile().get();
        final File outputDir = output.getParentFile();
        outputDir.mkdirs();

        final File input = this.getInput().getAsFile().get();

        SourceMetadata sourceMetadata = extractMetadata(input);

        final FileWriter fileWriter = new FileWriter(output);
        gson.toJson(sourceMetadata, fileWriter);
        fileWriter.flush();
        fileWriter.close();
    }

    protected abstract SourceMetadata extractMetadata(File inputFile) throws IOException;

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}
