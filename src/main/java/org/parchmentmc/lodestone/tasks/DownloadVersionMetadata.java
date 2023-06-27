package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.feather.manifests.LauncherManifest;
import org.parchmentmc.lodestone.util.OfflineChecker;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

import static java.nio.file.StandardOpenOption.*;

/**
 * The DownloadVersionMetadata task downloads the version metadata for a given Minecraft version, 
 * including the JSON file that contains information about the version's client jar, server jar, 
 * and any required libraries.
 */
public abstract class DownloadVersionMetadata extends MinecraftVersionTask {

    /**
     * Constructs a new DownloadVersionMetadata task and sets the default input and output locations for the downloaded
     * version metadata files.
     */
    public DownloadVersionMetadata() {
        this.getInput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("launcher.json")));
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).flatMap(d -> d.file(this.getMcVersion().map(s -> s + ".json"))));
    }

    /**
     * Downloads the version metadata for the given Minecraft version, including the JSON file that contains
     * information about the version's client jar, server jar, and any required libraries.
     *
     * @throws IOException if an error occurs while downloading or saving the version metadata files
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void download() throws IOException {
        OfflineChecker.checkOffline(getProject());

        final Gson gson = DownloadLauncherMetadata.getLauncherManifestGson();

        final File target = this.getOutput().getAsFile().get();
        final File parentDirectory = target.getParentFile();
        parentDirectory.mkdirs();

        final File source = this.getInput().getAsFile().get();

        final LauncherManifest launcherManifest;
        try (FileReader reader = new FileReader(source)) {
            launcherManifest = gson.fromJson(reader, LauncherManifest.class);
        }

        final String selectedVersion = resolveMinecraftVersion(getMcVersion().get(), launcherManifest);
        final LauncherManifest.VersionData versionData = launcherManifest.getVersions().stream().filter(v -> v.getId().equals(selectedVersion)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing minecraft version: " + selectedVersion));

        final String versionUrl = versionData.getUrl();
        final URL url = new URL(versionUrl);

        target.getParentFile().mkdirs();
        try (final ReadableByteChannel input = Channels.newChannel(url.openStream());
             final FileChannel output = FileChannel.open(target.toPath(), WRITE, CREATE, TRUNCATE_EXISTING)) {
            output.transferFrom(input, 0, Long.MAX_VALUE);
        }
    }

    /**
     * Resolves the Minecraft version to download based on the provided Minecraft version string and the launcher
     * manifest.
     *
     * @param mcVersion         the Minecraft version string to resolve
     * @param launcherManifest  the launcher manifest containing the available Minecraft versions
     * @return the resolved Minecraft version string
     */
    public static String resolveMinecraftVersion(String mcVersion, LauncherManifest launcherManifest) {
        switch (mcVersion) {
            case "latest_snapshot":
                return launcherManifest.getLatest().getSnapshot();
            case "latest_release":
                return launcherManifest.getLatest().getRelease();
            case "latest":
                final String latestSnapshot = launcherManifest.getLatest().getSnapshot();
                final String latestRelease = launcherManifest.getLatest().getRelease();

                final LauncherManifest.VersionData latestSnapshotData = launcherManifest.getVersions().stream().filter(v -> v.getId().equals(latestSnapshot)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Missing minecraft version: " + latestSnapshot));

                final LauncherManifest.VersionData latestReleaseData = launcherManifest.getVersions().stream().filter(v -> v.getId().equals(latestRelease)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Missing minecraft version: " + latestRelease));

                if (latestSnapshotData.getReleaseTime().isBefore(latestReleaseData.getReleaseTime())) {
                    return latestRelease;
                } else {
                    return latestSnapshot;
                }
        }

        return mcVersion;
    }

    /**
     * Returns the input file property for the launcher manifest JSON file.
     *
     * @return the input file property for the launcher manifest JSON file
     */
    @InputFile
    public abstract RegularFileProperty getInput();

    /**
     * Returns the output file property for the downloaded version metadata file.
     *
     * @return the output file property for the downloaded version metadata file
     */
    @OutputFile
    public abstract RegularFileProperty getOutput();
}
