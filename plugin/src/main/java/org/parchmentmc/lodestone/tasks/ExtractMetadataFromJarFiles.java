package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.io.gson.metadata.MetadataAdapterFactory;
import org.parchmentmc.feather.metadata.*;
import org.parchmentmc.feather.named.Named;
import org.parchmentmc.feather.util.SimpleVersion;
import org.parchmentmc.lodestone.asm.CodeCleaner;
import org.parchmentmc.lodestone.asm.CodeTree;
import org.parchmentmc.lodestone.asm.MutableClassInfo;
import org.parchmentmc.lodestone.converter.ClassConverter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public abstract class ExtractMetadataFromJarFiles extends MinecraftVersionTask
{
    public ExtractMetadataFromJarFiles()
    {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("metadata.json")));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void execute() {
        try
        {
            final File target = this.getOutput().getAsFile().get();
            final File parentDirectory = target.getParentFile();
            parentDirectory.mkdirs();

            final File clientJarFile = this.getInput().getAsFile().get();
            final File librariesDirectory = this.getLibraries().getAsFile().get();

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
              .withMinecraftVersion(getMcVersion().get())
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

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @InputDirectory
    public abstract DirectoryProperty getLibraries();
}
