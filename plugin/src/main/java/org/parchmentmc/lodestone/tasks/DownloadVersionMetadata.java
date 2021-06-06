package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.parchmentmc.feather.io.gson.OffsetDateTimeAdapter;
import org.parchmentmc.feather.manifests.LauncherManifest;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.time.OffsetDateTime;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

@SuppressWarnings("UnstableApiUsage")
public abstract class DownloadVersionMetadata extends DefaultTask
{

    private final DirectoryProperty   sourceDirectory;
    private final RegularFileProperty sourceFile;
    private final Property<String> sourceFileName;

    private final DirectoryProperty   targetDirectory;
    private final RegularFileProperty targetFile;
    private final Property<String> targetFileName;

    private final Property<String> mcVersion;

    public DownloadVersionMetadata()
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
        this.sourceFileName.convention("launcher.json");

        this.sourceFile = getProject().getObjects().fileProperty();
        this.sourceFile.convention(this.sourceDirectory.file(this.sourceFileName));

        this.targetDirectory = getProject().getObjects().directoryProperty();
        this.targetDirectory.convention(this.getProject().getLayout().getBuildDirectory().dir("lodestone"));

        this.targetFileName = getProject().getObjects().property(String.class);
        this.targetFileName.convention(this.mcVersion.map(s -> s + ".json"));

        this.targetFile = getProject().getObjects().fileProperty();
        this.targetFile.convention(this.targetDirectory.file(this.targetFileName));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void download() {

        try
        {
            final Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter()).create();

            final File target = this.targetFile.getAsFile().get();
            final File parentDirectory = target.getParentFile();
            parentDirectory.mkdirs();

            final File source = this.sourceFile.getAsFile().get();


            final LauncherManifest launcherManifest = gson.fromJson(new FileReader(source), LauncherManifest.class);

            String workingVersion = this.mcVersion.get();
            if (workingVersion.equals("latest_snapshot")) {
                workingVersion = launcherManifest.getLatest().getSnapshot();
            } else if (workingVersion.equals("latest_release")) {
                workingVersion = launcherManifest.getLatest().getRelease();
            } else if (workingVersion.equals("latest")) {
                final String latestSnapshot = launcherManifest.getLatest().getSnapshot();
                final String latestRelease = launcherManifest.getLatest().getRelease();

                final LauncherManifest.VersionData latestSnapshotData = launcherManifest.getVersions().stream().filter(v -> v.getId().equals(latestSnapshot)).findFirst()
                                                                   .orElseThrow(() -> new IllegalStateException("Missing minecraft version: " + latestSnapshot));

                final LauncherManifest.VersionData latestReleaseData = launcherManifest.getVersions().stream().filter(v -> v.getId().equals(latestRelease)).findFirst()
                                                                   .orElseThrow(() -> new IllegalStateException("Missing minecraft version: " + latestRelease));

                if (latestSnapshotData.getReleaseTime().isBefore(latestReleaseData.getReleaseTime())) {
                    workingVersion = latestRelease;
                }
                else
                {
                    workingVersion = latestSnapshot;
                }
            }

            final String selectedVersion = workingVersion;
            final LauncherManifest.VersionData versionData = launcherManifest.getVersions().stream().filter(v -> v.getId().equals(selectedVersion)).findFirst()
              .orElseThrow(() -> new IllegalStateException("Missing minecraft version: " + selectedVersion));

            final String versionUrl = versionData.getUrl();
            final URL url = new URL(versionUrl);

            try (final ReadableByteChannel input = Channels.newChannel(url.openStream());
                 final FileChannel output = FileChannel.open(target.toPath(), WRITE, TRUNCATE_EXISTING))
            {
                output.transferFrom(input, 0, Long.MAX_VALUE);
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
