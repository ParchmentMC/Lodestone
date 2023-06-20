package org.parchmentmc.lodestone;

import com.google.gson.Gson;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.parchmentmc.feather.manifests.LauncherManifest;
import org.parchmentmc.lodestone.tasks.DownloadLauncherMetadata;
import org.parchmentmc.lodestone.tasks.DownloadVersionMetadata;
import org.parchmentmc.lodestone.util.Constants;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

public class LodestoneExtension {
    private final Project project;
    private final Property<String> mcVersion;

    @Inject
    public LodestoneExtension(Project project, ObjectFactory factory) {
        this.project = project;

        this.mcVersion = factory.property(String.class).convention("latest");
    }

    public Property<String> getMcVersion() {
        return mcVersion;
    }

    private String resolvedMcVersion;

    public Provider<String> getResolvedMcVersion() {
        return mcVersion.map(mc -> {
            if (resolvedMcVersion != null)
                return resolvedMcVersion;
            if (!mc.equals("latest") && !mc.equals("latest_snapshot") && !mc.equals("latest_release")) {
                resolvedMcVersion = mc;
            } else {
                Gson gson = DownloadLauncherMetadata.getLauncherManifestGson();

                try {
                    LauncherManifest manifest = gson.fromJson(new InputStreamReader(new URL(Constants.MOJANG_LAUNCHER_URL).openStream()), LauncherManifest.class);
                    resolvedMcVersion = DownloadVersionMetadata.resolveMinecraftVersion(mc, manifest);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return resolvedMcVersion;
        });
    }
}
