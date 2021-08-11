package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import org.parchmentmc.feather.manifests.LauncherManifest;
import org.parchmentmc.lodestone.util.OfflineChecker;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

import static java.nio.file.StandardOpenOption.*;

@SuppressWarnings("UnstableApiUsage")
public abstract class DownloadVersionMetadata extends MinecraftVersionTask
{
    public DownloadVersionMetadata()
    {

        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).flatMap(d -> d.file(this.getMcVersion().map(s -> s + ".json"))));
    }

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
        try (FileReader reader = new FileReader(source))
        {
            launcherManifest = gson.fromJson(reader, LauncherManifest.class);
        }

        final String selectedVersion = resolveMinecraftVersion(getMcVersion().get(), launcherManifest);
        final LauncherManifest.VersionData versionData = launcherManifest.getVersions().stream().filter(v -> v.getId().equals(selectedVersion)).findFirst()
          .orElseThrow(() -> new IllegalStateException("Missing minecraft version: " + selectedVersion));

        final String versionUrl = versionData.getUrl();
        final URL url = new URL(versionUrl);

        target.getParentFile().mkdirs();
        try (final ReadableByteChannel input = Channels.newChannel(url.openStream());
             final FileChannel output = FileChannel.open(target.toPath(), WRITE, CREATE, TRUNCATE_EXISTING))
        {
            output.transferFrom(input, 0, Long.MAX_VALUE);
        }
    }

    public static String resolveMinecraftVersion(String mcVersion, LauncherManifest launcherManifest) {
        if (mcVersion.equals("latest_snapshot")) {
            return launcherManifest.getLatest().getSnapshot();
        } else if (mcVersion.equals("latest_release")) {
            return launcherManifest.getLatest().getRelease();
        } else if (mcVersion.equals("latest")) {
            final String latestSnapshot = launcherManifest.getLatest().getSnapshot();
            final String latestRelease = launcherManifest.getLatest().getRelease();

            final LauncherManifest.VersionData latestSnapshotData = launcherManifest.getVersions().stream().filter(v -> v.getId().equals(latestSnapshot)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing minecraft version: " + latestSnapshot));

            final LauncherManifest.VersionData latestReleaseData = launcherManifest.getVersions().stream().filter(v -> v.getId().equals(latestRelease)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing minecraft version: " + latestRelease));

            if (latestSnapshotData.getReleaseTime().isBefore(latestReleaseData.getReleaseTime())) {
                return latestRelease;
            }
            else
            {
                return latestSnapshot;
            }
        }

        return mcVersion;
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}
