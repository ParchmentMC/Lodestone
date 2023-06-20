package org.parchmentmc.lodestone.util;

import org.gradle.api.Project;

public class OfflineChecker {
    public static void checkOffline(Project project) {
        if (project.getGradle().getStartParameter().isOffline()) {
            throw new IllegalStateException("Gradle is offline. Cannot download minecraft metadata.");
        }
    }
}
