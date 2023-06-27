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

/**
 * The ExtractMetadataTask is an abstract base class for tasks that extract metadata from Minecraft code in various formats
 * and output the metadata as a JSON file.
 */
public abstract class ExtractMetadataTask extends MinecraftVersionTask {

    /**
     * Executes the task by extracting metadata from the input file and writing it to the output file.
     *
     * @throws IOException if an error occurs while reading or writing the files
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    private void execute() throws IOException {
        final Gson gson = createMetadataGson();

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

    /**
     * Creates a Gson instance with the necessary type adapters for serializing metadata to JSON.
     *
     * @return a Gson instance with the necessary type adapters for serializing metadata to JSON
     */
    protected static Gson createMetadataGson() {
        return new GsonBuilder()
                .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                .registerTypeAdapterFactory(new MetadataAdapterFactory())
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    /**
     * Extracts metadata from the given input file and returns a SourceMetadata object that represents the metadata.
     *
     * @param inputFile the input file to extract metadata from
     * @return a SourceMetadata object that represents the metadata from the input file
     * @throws IOException if an error occurs while reading or parsing the input file
     */
    protected abstract SourceMetadata extractMetadata(File inputFile) throws IOException;

    /**
     * Returns the input file for the task.
     *
     * @return the input file for the task
     */
    @InputFile
    public abstract RegularFileProperty getInput();

    /**
     * Returns the output file for the task.
     *
     * @return the output file for the task
     */
    @OutputFile
    public abstract RegularFileProperty getOutput();
}
