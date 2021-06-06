package org.parchmentmc.lodestone.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.lodestone.util.Constants;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

@SuppressWarnings("UnstableApiUsage")
public abstract class DownloadLauncherMetadata extends DefaultTask
{
    private final DirectoryProperty destinationDirectory;
    private final RegularFileProperty targetFile;

    private final Property<String>    fileName;

    @Inject
    public DownloadLauncherMetadata(final FileFactory fileFactory)
    {
        if (getProject().getGradle().getStartParameter().isOffline())
        {
            throw new IllegalStateException("Gradle is offline. Can not download minecraft metadata.");
        }

        this.destinationDirectory = getProject().getObjects().directoryProperty();
        this.destinationDirectory.convention(this.getProject().provider(() -> fileFactory.dir(new File(getProject().getBuildDir(), "lodestone"))));

        this.fileName = getProject().getObjects().property(String.class);
        this.fileName.convention(this.getProject().provider(() -> "launcher.json"));

        this.targetFile = getProject().getObjects().fileProperty();
        this.targetFile.convention(this.destinationDirectory.file(this.fileName));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void download() {
        final File target = this.targetFile.getAsFile().get();
        final File parentDirectory = target.getParentFile();
        parentDirectory.mkdirs();

        try
        {
            final URL launcherUrl = new URL(Constants.MOJANG_LAUNCHER_URL);
            final ReadableByteChannel readableByteChannel = Channels.newChannel(launcherUrl.openStream());
            final FileOutputStream fileOutputStream = new FileOutputStream(target, false);
            final FileChannel fileChannel = fileOutputStream.getChannel();

            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        }
        catch (MalformedURLException ignored)
        {
            //This is a damn constant.... It will never throw a damn exception.....
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to download the launcher metadata.", e);
        }
    }

    public Property<String> getFileName()
    {
        return fileName;
    }

    @OutputFile
    public Provider<RegularFile> getTargetFile() {
        return this.targetFile;
    }

    @OutputDirectory
    public DirectoryProperty getDestinationDirectory() {
        return this.destinationDirectory;
    }
}
