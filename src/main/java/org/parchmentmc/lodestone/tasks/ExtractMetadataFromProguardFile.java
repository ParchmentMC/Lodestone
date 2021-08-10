package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.io.gson.metadata.MetadataAdapterFactory;
import org.parchmentmc.feather.io.proguard.MetadataProguardParser;
import org.parchmentmc.feather.metadata.SourceMetadata;
import org.parchmentmc.feather.util.SimpleVersion;

import java.io.*;
import java.net.MalformedURLException;

@SuppressWarnings("UnstableApiUsage")
public abstract class ExtractMetadataFromProguardFile extends MinecraftVersionTask
{
    public ExtractMetadataFromProguardFile()
    {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("proguard.json")));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void extract() {

        try
        {
            final Gson gson = new GsonBuilder()
                                .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                                .registerTypeAdapterFactory(new MetadataAdapterFactory())
                                .setPrettyPrinting()
                                .create();

            final File target = this.getOutput().getAsFile().get();
            final File parentDirectory = target.getParentFile();
            parentDirectory.mkdirs();

            final File source = this.getInput().getAsFile().get();

            final SourceMetadata sourceMetadata = MetadataProguardParser.fromFile(source);

            final FileWriter fileWriter = new FileWriter(target);
            gson.toJson(sourceMetadata, fileWriter);
            fileWriter.flush();
            fileWriter.close();
        }
        catch (FileNotFoundException e)
        {
            throw new IllegalStateException("Missing launcher manifest source file.", e);
        }
        catch (MalformedURLException ignored)
        {
            //Url comes from the launcher manifest.
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to download version metadata.", e);
        }
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}
