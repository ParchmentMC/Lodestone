package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
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
public abstract class ExtractMetadataFromProguardFile extends DefaultTask
{
    private final DirectoryProperty   sourceDirectory;
    private final     RegularFileProperty sourceFile;
    private final     Property<String>    sourceFileName;

    private final DirectoryProperty   targetDirectory;
    private final RegularFileProperty targetFile;
    private final Property<String> targetFileName;

    private final Property<String> mcVersion;

    public ExtractMetadataFromProguardFile()
    {
        this.mcVersion = getProject().getObjects().property(String.class);
        this.mcVersion.convention("latest");

        this.sourceDirectory = getProject().getObjects().directoryProperty();
        this.sourceDirectory.convention(this.getProject().getLayout().getBuildDirectory().dir("lodestone").flatMap(s -> s.dir(this.mcVersion)));

        this.sourceFileName = getProject().getObjects().property(String.class);
        this.sourceFileName.convention("client.txt");

        this.sourceFile = getProject().getObjects().fileProperty();
        this.sourceFile.convention(this.sourceDirectory.file(this.sourceFileName));

        this.targetDirectory = getProject().getObjects().directoryProperty();
        this.sourceDirectory.convention(this.getProject().getLayout().getBuildDirectory().dir("lodestone").flatMap(s -> s.dir(this.mcVersion)));

        this.targetFileName = getProject().getObjects().property(String.class);
        this.targetFileName.convention("proguard.json");

        this.targetFile = getProject().getObjects().fileProperty();
        this.targetFile.convention(this.targetDirectory.file(this.targetFileName));
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

            final File target = this.targetFile.getAsFile().get();
            final File parentDirectory = target.getParentFile();
            parentDirectory.mkdirs();

            final File source = this.sourceFile.getAsFile().get();

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

    public DirectoryProperty getSourceDirectory()
    {
        return sourceDirectory;
    }

    public RegularFileProperty getSourceFile()
    {
        return sourceFile;
    }

    public Property<String> getSourceFileName()
    {
        return sourceFileName;
    }

    public DirectoryProperty getTargetDirectory()
    {
        return targetDirectory;
    }

    @OutputFile
    public RegularFileProperty getTargetFile()
    {
        return targetFile;
    }

    public Property<String> getTargetFileName()
    {
        return targetFileName;
    }

    @Input
    public Property<String> getMcVersion()
    {
        return mcVersion;
    }
}
