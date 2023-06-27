package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.feather.manifests.Library;
import org.parchmentmc.feather.manifests.VersionManifest;
import org.parchmentmc.lodestone.util.OfflineChecker;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.Objects;

import static java.nio.file.StandardOpenOption.*;

/**
 * The DownloadVersion task downloads the necessary files for a Minecraft version to be run, including the Minecraft
 * client jar, the server jar, and any libraries required by those jars.
 */
public abstract class DownloadVersion extends MinecraftVersionTask {

    /**
     * Constructs a new DownloadVersion task and sets the default input and output locations for the downloaded files.
     */
    public DownloadVersion() {
        this.getInput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).flatMap(d -> d.file(this.getMcVersion().map(s -> s + ".json"))));
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).flatMap(s -> s.dir(this.getMcVersion())));
    }

    /**
     * Downloads the necessary files for the Minecraft version to be run, including the Minecraft client jar, the
     * server jar, and any libraries required by those jars.
     *
     * @throws IOException if an error occurs while downloading or saving the files
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void download() throws IOException {
        OfflineChecker.checkOffline(getProject());

        final Gson gson = DownloadLauncherMetadata.getLauncherManifestGson();

        final VersionManifest versionManifest;
        try (FileReader reader = new FileReader(this.getInput().getAsFile().get())) {
            versionManifest = gson.fromJson(reader, VersionManifest.class);
        }

        final File outputDirectory = this.getOutput().getAsFile().get();

        if (outputDirectory.exists())
            outputDirectory.delete();

        outputDirectory.mkdirs();
        for (Map.Entry<String, VersionManifest.DownloadInfo> entry : versionManifest.getDownloads().entrySet()) {
            VersionManifest.DownloadInfo fileInfo = entry.getValue();

            final URL downloadUrl = new URL(fileInfo.getUrl());
            String fileName = fileInfo.getUrl().substring(fileInfo.getUrl().lastIndexOf('/') + 1);
            final File target = new File(outputDirectory, fileName);

            target.getParentFile().mkdirs();
            try (final ReadableByteChannel input = Channels.newChannel(downloadUrl.openStream());
                 final FileChannel output = FileChannel.open(target.toPath(), WRITE, CREATE, TRUNCATE_EXISTING)) {
                output.transferFrom(input, 0, Long.MAX_VALUE);
            }
        }

        final File librariesDirectory = new File(outputDirectory, "libraries");
        librariesDirectory.mkdirs();
        for (final Library library : versionManifest.getLibraries()) {
            final File targetFile = new File(librariesDirectory, Objects.requireNonNull(library.getDownloads().getArtifact(), "No artifact was available.").getPath());
            targetFile.getParentFile().mkdirs();
            final URL targetUrl = new URL(library.getDownloads().getArtifact().getUrl());

            targetFile.getParentFile().mkdirs();
            try (final ReadableByteChannel input = Channels.newChannel(targetUrl.openStream());
                 final FileChannel output = FileChannel.open(targetFile.toPath(), WRITE, CREATE, TRUNCATE_EXISTING)) {
                output.transferFrom(input, 0, Long.MAX_VALUE);
            }
        }
    }

    /**
     * Returns the input file property for the version manifest JSON file.
     *
     * @return the input file property for the version manifest JSON file
     */
    @InputFile
    public abstract RegularFileProperty getInput();

    /**
     * Returns the output directory property for the downloaded files.
     *
     * @return the output directory property for the downloaded files
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutput();
}
