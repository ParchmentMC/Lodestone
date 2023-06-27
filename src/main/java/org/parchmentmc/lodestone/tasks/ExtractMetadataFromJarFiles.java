package org.parchmentmc.lodestone.tasks;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.parchmentmc.feather.metadata.*;
import org.parchmentmc.feather.named.Named;
import org.parchmentmc.feather.util.CollectorUtils;
import org.parchmentmc.feather.util.SimpleVersion;
import org.parchmentmc.lodestone.asm.CodeCleaner;
import org.parchmentmc.lodestone.asm.CodeTree;
import org.parchmentmc.lodestone.asm.MutableClassInfo;
import org.parchmentmc.lodestone.converter.ClassConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The ExtractMetadataFromJarFiles task extracts metadata from a Minecraft client jar file and any required library
 * jar files, and outputs the metadata as a JSON file.
 */
public abstract class ExtractMetadataFromJarFiles extends ExtractMetadataTask {

    /**
     * Constructs a new ExtractMetadataFromJarFiles task and sets the default output location for the metadata JSON file.
     */
    public ExtractMetadataFromJarFiles() {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("metadata.json")));
    }

    /**
     * Extracts metadata from the given Minecraft client jar file and any required library jar files, and returns
     * a SourceMetadata object that represents the metadata for the client jar and its contents.
     *
     * @param clientJarFile the Minecraft client jar file to extract metadata from
     * @return a SourceMetadata object that represents the metadata for the client jar and its contents
     * @throws IOException if an error occurs while reading or parsing the jar files
     */
    @Override
    protected SourceMetadata extractMetadata(File clientJarFile) throws IOException {
        final File librariesDirectory = this.getLibraries().getAsFile().get();

        final CodeTree codeTree = new CodeTree();
        codeTree.load(clientJarFile.toPath(), false);

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("regex:.+\\.jar");
        try (Stream<Path> libraries = Files.find(librariesDirectory.toPath(), 999, (path, basicFileAttributes) -> basicFileAttributes.isRegularFile() && matcher.matches(path))) {
            for (Path libraryFile : libraries.collect(CollectorUtils.toLinkedSet())) {
                codeTree.load(libraryFile, true);
            }
        }

        final Set<String> minecraftJarClasses = codeTree.getNoneLibraryClasses();
        final Map<String, MutableClassInfo> asmParsedClassInfo = minecraftJarClasses.stream().collect(CollectorUtils.toLinkedMap(
                Function.identity(),
                codeTree::getClassMetadataFor
        ));

        final CodeCleaner codeCleaner = new CodeCleaner(codeTree);
        asmParsedClassInfo.values().forEach(codeCleaner::cleanClass);

        final ClassConverter classConverter = new ClassConverter();
        final Map<String, ClassMetadata> cleanedClassMetadata = minecraftJarClasses.stream().collect(CollectorUtils.toLinkedMap(
                Function.identity(),
                name -> {
                    final MutableClassInfo classInfo = asmParsedClassInfo.get(name);
                    return classConverter.convert(classInfo);
                }
        ));

        final SourceMetadata baseDataSet = SourceMetadataBuilder.create()
                .withSpecVersion(SimpleVersion.of("1.0.0"))
                .withMinecraftVersion(getMcVersion().get())
                .withClasses(new LinkedHashSet<>(cleanedClassMetadata.values()));

        return adaptClassTypes(baseDataSet);
    }

    /**
     * Adapts the class types in the given SourceMetadata object to be compatible with the Minecraft data model.
     *
     * @param sourceMetadata the SourceMetadata object to adapt
     * @return the adapted SourceMetadata object
     */
    private static SourceMetadata adaptClassTypes(final SourceMetadata sourceMetadata) {
        return adaptInnerOuterClassList(sourceMetadata);
    }

    /**
     * Adapts the inner and outer class lists in the given SourceMetadata object to be compatible with the Minecraft
     * data model.
     *
     * @param sourceMetadata the SourceMetadata object to adapt
     * @return the adapted SourceMetadata object
     */
    private static SourceMetadata adaptInnerOuterClassList(final SourceMetadata sourceMetadata) {
        final Map<Named, ClassMetadataBuilder> namedClassMetadataMap = sourceMetadata.getClasses()
                .stream()
                .collect(CollectorUtils.toLinkedMap(
                        WithName::getName,
                        ClassMetadataBuilder::create
                ));

        namedClassMetadataMap.values().forEach(classMetadata -> {
            final Named outerName = classMetadata.getOwner();
            if (namedClassMetadataMap.containsKey(outerName)) {
                final ClassMetadataBuilder outerBuilder = namedClassMetadataMap.get(outerName);
                outerBuilder.addInnerClass(classMetadata);
            }
        });

        return SourceMetadataBuilder.create()
                .withSpecVersion(sourceMetadata.getSpecificationVersion())
                .withMinecraftVersion(sourceMetadata.getMinecraftVersion())
                .withClasses(namedClassMetadataMap.values()
                        .stream()
                        .filter(classMetadataBuilder -> classMetadataBuilder.getOwner().isEmpty())
                        .map(ClassMetadataBuilder::build)
                        .collect(CollectorUtils.toLinkedSet())
                )
                .build();
    }

    /**
     * Returns the input directory containing the required library jar files for the task.
     *
     * @return the input directory containing the required library jar files for the task
     */
    @InputDirectory
    public abstract DirectoryProperty getLibraries();
}
