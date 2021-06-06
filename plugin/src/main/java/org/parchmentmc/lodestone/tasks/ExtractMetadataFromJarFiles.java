package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.io.gson.metadata.MetadataAdapterFactory;
import org.parchmentmc.feather.metadata.*;
import org.parchmentmc.feather.named.Named;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.feather.util.SimpleVersion;
import org.parchmentmc.feather.utils.RemapHelper;
import org.parchmentmc.lodestone.asm.CodeCleaner;
import org.parchmentmc.lodestone.asm.CodeTree;
import org.parchmentmc.lodestone.asm.MutableClassInfo;
import org.parchmentmc.lodestone.converter.ClassConverter;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public abstract class ExtractMetadataFromJarFiles extends DefaultTask
{
    private final DirectoryProperty   sourceDirectory;
    private final     RegularFileProperty sourceFile;
    private final     Property<String>    sourceFileName;

    private final DirectoryProperty   librariesDirectory;

    private final DirectoryProperty   targetDirectory;
    private final RegularFileProperty targetFile;
    private final Property<String> targetFileName;

    private final Property<String> mcVersion;

    @Inject
    public ExtractMetadataFromJarFiles(final FileFactory fileFactory, final FilePropertyFactory filePropertyFactory)
    {
        if (getProject().getGradle().getStartParameter().isOffline())
        {
            throw new IllegalStateException("Gradle is offline. Can not download minecraft metadata.");
        }

        this.mcVersion = getProject().getObjects().property(String.class);
        this.mcVersion.convention(getProject().provider(() -> "latest"));

        this.sourceDirectory = getProject().getObjects().directoryProperty();
        this.sourceDirectory.convention(this.getProject().provider(() -> fileFactory.dir(new File(getProject().getBuildDir(), "lodestone" + File.separator + this.mcVersion.getOrElse("latest")))));

        this.sourceFileName = getProject().getObjects().property(String.class);
        this.sourceFileName.convention(this.getProject().provider(() -> "client.jar"));

        this.sourceFile = getProject().getObjects().fileProperty();
        this.sourceFile.convention(this.sourceDirectory.file(this.sourceFileName));

        this.librariesDirectory = getProject().getObjects().directoryProperty();
        this.librariesDirectory.convention(this.getProject().provider(() -> this.sourceFile.getAsFile().get()).map(file -> fileFactory.dir(new File(file.getParentFile(), "libraries"))));

        this.targetDirectory = getProject().getObjects().directoryProperty();
        this.targetDirectory.convention(this.getProject().provider(() -> fileFactory.dir(new File(getProject().getBuildDir(), "lodestone" + File.separator + this.mcVersion.getOrElse("latest")))));

        this.targetFileName = getProject().getObjects().property(String.class);
        this.targetFileName.convention(this.getProject().provider(() -> "metadata.json"));

        this.targetFile = getProject().getObjects().fileProperty();
        this.targetFile.convention(this.targetDirectory.file(this.targetFileName));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void execute() {
        try
        {
            final File target = this.targetFile.getAsFile().get();
            final File parentDirectory = target.getParentFile();
            parentDirectory.mkdirs();

            final File clientJarFile = this.sourceFile.getAsFile().get();
            final File librariesDirectory = this.librariesDirectory.getAsFile().get();

            final CodeTree codeTree = new CodeTree();
            codeTree.load(clientJarFile.toPath(), false);

            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("regex:.+\\.jar");
            final Set<Path> libraries = Files.find(librariesDirectory.toPath(), 999, (path, basicFileAttributes) -> basicFileAttributes.isRegularFile() && matcher.matches(path)).collect(Collectors.toSet());

            for (Path libraryFile : libraries)
            {
                codeTree.load(libraryFile, true);
            }

            final Set<String> minecraftJarClasses = codeTree.getNoneLibraryClasses();
            final Map<String, MutableClassInfo> asmParsedClassInfo = minecraftJarClasses.stream().collect(Collectors.toMap(
              Function.identity(),
              codeTree::getClassMetadataFor
            ));

            final CodeCleaner codeCleaner = new CodeCleaner(codeTree);
            asmParsedClassInfo.values().forEach(codeCleaner::cleanClass);

            final ClassConverter classConverter = new ClassConverter();
            final Map<String, ClassMetadata> cleanedClassMetadata = minecraftJarClasses.stream().collect(Collectors.toMap(
              Function.identity(),
              name -> {
                  final MutableClassInfo classInfo = asmParsedClassInfo.get(name);
                  return classConverter.convert(classInfo);
              }
            ));

            final SourceMetadata baseDataSet = SourceMetadataBuilder.create()
              .withSpecVersion(SimpleVersion.of("1.0.0"))
              .withMinecraftVersion(mcVersion.get())
              .withClasses(new LinkedHashSet<>(cleanedClassMetadata.values()));

            final SourceMetadata sourceMetadata = adaptClassTypes(baseDataSet);

            final Gson gson = new GsonBuilder()
                                .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                                .registerTypeAdapterFactory(new MetadataAdapterFactory())
                                .setPrettyPrinting()
                                .create();

            final FileWriter fileWriter = new FileWriter(target);
            gson.toJson(sourceMetadata, fileWriter);
            fileWriter.flush();
            fileWriter.close();
        }
        catch (FileNotFoundException e)
        {
            throw new IllegalStateException("Missing components of the client installation. Could not find the client jar.", e);
        }
        catch (MalformedURLException ignored)
        {
            //Url comes from the launcher manifest.
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to load a jar into the code tree");
        }
    }

    private static SourceMetadata adaptClassTypes(final SourceMetadata sourceMetadata) {
        return adaptInnerOuterClassList(sourceMetadata);
    }

    private static SourceMetadata adaptInnerOuterClassList(final SourceMetadata sourceMetadata) {
        final Map<Named, ClassMetadataBuilder> namedClassMetadataMap = sourceMetadata.getClasses()
                                                                         .stream()
                                                                         .collect(Collectors.toMap(
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
                                .collect(Collectors.toCollection(LinkedHashSet::new))
                 )
                 .build();
    }

    public DirectoryProperty getSourceDirectory()
    {
        return sourceDirectory;
    }

    public RegularFileProperty getSourceFile()
    {
        return sourceFile;
    }

    public Property<String> getSourceFileName()
    {
        return sourceFileName;
    }

    public DirectoryProperty getTargetDirectory()
    {
        return targetDirectory;
    }

    @OutputFile
    public RegularFileProperty getTargetFile()
    {
        return targetFile;
    }

    public Property<String> getTargetFileName()
    {
        return targetFileName;
    }

    @Input
    public Property<String> getMcVersion()
    {
        return mcVersion;
    }
}
