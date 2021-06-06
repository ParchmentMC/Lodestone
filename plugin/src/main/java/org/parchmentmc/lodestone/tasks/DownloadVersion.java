package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.parchmentmc.feather.io.gson.OffsetDateTimeAdapter;
import org.parchmentmc.feather.manifests.Library;
import org.parchmentmc.feather.manifests.VersionManifest;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.time.OffsetDateTime;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public abstract class DownloadVersion extends DefaultTask
{

    private final Property<String> mcVersion;

    private final DirectoryProperty   sourceDirectory;
    private final RegularFileProperty sourceFile;
    private final Property<String>    sourceFileName;

    private final DirectoryProperty targetDirectory;

    public DownloadVersion()
    {
        if (getProject().getGradle().getStartParameter().isOffline())
        {
            throw new IllegalStateException("Gradle is offline. Can not download minecraft metadata.");
        }

        this.mcVersion = getProject().getObjects().property(String.class);
        this.mcVersion.convention("latest");

        this.sourceDirectory = getProject().getObjects().directoryProperty();
        this.sourceDirectory.convention(this.getProject().getLayout().getBuildDirectory().dir("lodestone"));

        this.sourceFileName = getProject().getObjects().property(String.class);
        this.sourceFileName.convention(this.mcVersion.map(v -> v + ".json"));

        this.sourceFile = getProject().getObjects().fileProperty();
        this.sourceFile.convention(this.sourceDirectory.file(this.sourceFileName));

        this.targetDirectory = getProject().getObjects().directoryProperty();
        this.targetDirectory.convention(this.getProject().getLayout().getBuildDirectory().dir("lodestone").flatMap(s -> s.dir(this.mcVersion)));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void download()
    {

        try
        {
            final Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter()).create();

            final VersionManifest versionManifest = gson.fromJson(new FileReader(this.sourceFile.getAsFile().get()), VersionManifest.class);

            final File outputDirectory = this.targetDirectory.getAsFile().get();

            if (outputDirectory.exists())
                outputDirectory.delete();

            outputDirectory.mkdirs();
            for (Map.Entry<String, VersionManifest.DownloadInfo> entry : versionManifest.getDownloads().entrySet())
            {
                VersionManifest.DownloadInfo fileInfo = entry.getValue();

                final URL downloadUrl = new URL(fileInfo.getUrl());
                String fileName = fileInfo.getUrl().substring(fileInfo.getUrl().lastIndexOf('/') + 1);

                final File target = new File(outputDirectory, fileName);
                final ReadableByteChannel readableByteChannel = Channels.newChannel(downloadUrl.openStream());
                final FileOutputStream fileOutputStream = new FileOutputStream(target, false);
                final FileChannel fileChannel = fileOutputStream.getChannel();

                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            }

            final File librariesDirectory = new File(outputDirectory, "libraries");
            librariesDirectory.mkdirs();
            for (final Library library : versionManifest.getLibraries())
            {
                final File targetFile = new File(librariesDirectory, library.getDownloads().getArtifact().getPath());
                targetFile.getParentFile().mkdirs();
                final URL targetUrl = new URL(library.getDownloads().getArtifact().getUrl());

                final ReadableByteChannel readableByteChannel = Channels.newChannel(targetUrl.openStream());
                final FileOutputStream fileOutputStream = new FileOutputStream(targetFile, false);
                final FileChannel fileChannel = fileOutputStream.getChannel();

                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            }
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

    public Property<String> getMcVersion()
    {
        return mcVersion;
    }

    public DirectoryProperty getSourceDirectory()
    {
        return sourceDirectory;
    }

    public RegularFileProperty getSourceFile()
    {
        return sourceFile;
    }

    @Input
    public Property<String> getSourceFileName()
    {
        return sourceFileName;
    }

    @OutputDirectory
    public DirectoryProperty getTargetDirectory()
    {
        return targetDirectory;
    }
}
