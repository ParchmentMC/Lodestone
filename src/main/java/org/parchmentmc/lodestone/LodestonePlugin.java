/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package org.parchmentmc.lodestone;

import org.gradle.api.Project;
import org.gradle.api.Plugin;

public class LodestonePlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getLogger().lifecycle("Applying lodestone...");
        LodestoneExtension extension = project.getExtensions().create("lodestone", LodestoneExtension.class, project);
    }
}
