package org.parchmentmc.lodestone.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.parchmentmc.lodestone.LodestoneExtension;

public abstract class MinecraftVersionTask extends DefaultTask {
    @Input
    public abstract Property<String> getMcVersion();

    protected MinecraftVersionTask() {
        LodestoneExtension extension = getExtensions().findByType(LodestoneExtension.class);
        if (extension == null) {
            getMcVersion().convention("latest");
        } else {
            getMcVersion().convention(extension.getResolvedMcVersion());
        }
    }
}
