package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.feather.io.gson.OffsetDateTimeAdapter;
import org.parchmentmc.lodestone.util.Constants;
import org.parchmentmc.lodestone.util.OfflineChecker;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.time.OffsetDateTime;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * The DownloadLauncherMetadata task downloads the launcher metadata from the Mojang server and saves it to a file.
 * The task is executed during the build process and can be configured to output the metadata file to a specific location.
 */
@SuppressWarnings("UnstableApiUsage")
public abstract class DownloadLauncherMetadata extends DefaultTask {
    
    /**
     * Constructs a new DownloadLauncherMetadata task and sets the default output location for the metadata file.
     */
    public DownloadLauncherMetadata() {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("launcher.json")));
    }

    /**
     * Downloads the launcher metadata from the Mojang server and saves it to a file.
     *
     * @throws IOException if an error occurs while downloading or saving the metadata file
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void download() throws IOException {
        OfflineChecker.checkOffline(getProject());

        final File target = this.getOutput().getAsFile().get();
        final File parentDirectory = target.getParentFile();
        parentDirectory.mkdirs();

        final URL launcherUrl = new URL(Constants.MOJANG_LAUNCHER_URL);

        target.getParentFile().mkdirs();
        try (final ReadableByteChannel input = Channels.newChannel(launcherUrl.openStream());
             final FileChannel output = FileChannel.open(target.toPath(), WRITE, CREATE, TRUNCATE_EXISTING)) {
            output.transferFrom(input, 0, Long.MAX_VALUE);
        }
    }

    /**
     * Returns the output file property for the launcher metadata file.
     *
     * @return the output file property for the launcher metadata file
     */
    @OutputFile
    public abstract RegularFileProperty getOutput();

    /**
     * Returns a Gson instance configured to deserialize the Mojang launcher metadata format.
     *
     * @return a Gson instance configured to deserialize the Mojang launcher metadata format
     */
    public static Gson getLauncherManifestGson() {
        return new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter()).disableHtmlEscaping().create();
    }
}
