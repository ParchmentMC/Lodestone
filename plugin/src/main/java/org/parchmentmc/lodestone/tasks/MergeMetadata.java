package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.io.gson.metadata.MetadataAdapterFactory;
import org.parchmentmc.feather.metadata.*;
import org.parchmentmc.feather.named.Named;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.feather.util.SimpleVersion;
import org.parchmentmc.feather.utils.MetadataMerger;
import org.parchmentmc.lodestone.util.ASMRemapper;

import java.io.*;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public abstract class MergeMetadata extends MinecraftVersionTask
{
    public MergeMetadata()
    {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("merged.json")));
    }

    private static SourceMetadata adaptTypes(final SourceMetadata sourceMetadata)
    {
        final Map<String, String> obfToMojClassNameMap = new HashMap<>();
        final Map<String, MethodMetadata> obfKeyToMojMethodNameMap = new HashMap<>();
        sourceMetadata.getClasses().forEach(classMetadata -> {
            collectClassNames(classMetadata, obfToMojClassNameMap);
            collectMethodNames(classMetadata, obfKeyToMojMethodNameMap);
        });

        final SourceMetadataBuilder sourceMetadataBuilder = SourceMetadataBuilder.create();

        sourceMetadataBuilder.withSpecVersion(sourceMetadata.getSpecificationVersion())
          .withMinecraftVersion(sourceMetadata.getMinecraftVersion());

        for (final ClassMetadata aClass : sourceMetadata.getClasses())
        {
            sourceMetadataBuilder.addClass(
              adaptSignatures(
                aClass,
                obfToMojClassNameMap,
                obfKeyToMojMethodNameMap
              )
            );
        }

        final SourceMetadata signatureRemappedData = sourceMetadataBuilder.build();
        obfToMojClassNameMap.clear();
        obfKeyToMojMethodNameMap.clear();
        signatureRemappedData.getClasses().forEach(classMetadata -> {
            collectClassNames(classMetadata, obfToMojClassNameMap);
            collectMethodNames(classMetadata, obfKeyToMojMethodNameMap);
        });

        final SourceMetadataBuilder bouncerRemappedDataBuilder = SourceMetadataBuilder.create();

        bouncerRemappedDataBuilder.withSpecVersion(sourceMetadata.getSpecificationVersion())
          .withMinecraftVersion(sourceMetadata.getMinecraftVersion());

        for (final ClassMetadata aClass : signatureRemappedData.getClasses())
        {
            bouncerRemappedDataBuilder.addClass(
              adaptMethodReferences(
                aClass,
                obfToMojClassNameMap,
                obfKeyToMojMethodNameMap
              )
            );
        }

        return bouncerRemappedDataBuilder.build();
    }

    private static ClassMetadata adaptSignatures(
      final ClassMetadata classMetadata,
      final Map<String, String> obfToMojNameMap,
      final Map<String, MethodMetadata> obfKeyToMojMethodNameMap
    )
    {
        final Map<String, String> obfToMojMethodNameMap = obfKeyToMojMethodNameMap.entrySet().stream().collect(Collectors.toMap(
          Map.Entry::getKey,
          e -> e.getValue().getName().getMojangName().orElseThrow(() -> new IllegalStateException("Missing mojang name"))
        ));

        final ASMRemapper remapper = new ASMRemapper(
          obfToMojNameMap,
          obfToMojMethodNameMap
        );

        final ClassMetadataBuilder classMetadataBuilder = ClassMetadataBuilder.create(classMetadata)
                                                            .withInnerClasses(classMetadata.getInnerClasses().stream()
                                                                                .map(inner -> adaptSignatures(inner, obfToMojNameMap, obfKeyToMojMethodNameMap))
                                                                                .collect(Collectors.toSet()))
                                                            .withMethods(classMetadata.getMethods().stream()
                                                                           .map(method -> {
                                                                               final MethodMetadataBuilder builder = MethodMetadataBuilder.create(method);

                                                                               if (!method.getOwner().hasMojangName() && method.getOwner().hasObfuscatedName())
                                                                               {
                                                                                   builder.withOwner(
                                                                                     NamedBuilder.create(method.getOwner())
                                                                                       .withMojang(
                                                                                         obfToMojNameMap.getOrDefault(
                                                                                           method.getOwner()
                                                                                             .getObfuscatedName()
                                                                                             .orElseThrow(() -> new IllegalStateException("Missing obfuscated method owner name")),
                                                                                           method.getOwner()
                                                                                             .getObfuscatedName()
                                                                                             .orElseThrow(() -> new IllegalStateException("Missing obfuscated method owner name"))
                                                                                         )
                                                                                       )
                                                                                       .build()
                                                                                   );
                                                                               }

                                                                               if (!method.getDescriptor().hasMojangName() && method.getDescriptor().hasObfuscatedName())
                                                                               {
                                                                                   builder.withDescriptor(
                                                                                     NamedBuilder.create(method.getDescriptor())
                                                                                       .withMojang(
                                                                                         remapper.mapMethodDesc(
                                                                                           method.getDescriptor()
                                                                                             .getObfuscatedName()
                                                                                             .orElseThrow(() -> new IllegalStateException("Missing obfuscated method descriptor."))
                                                                                         )
                                                                                       )
                                                                                       .build()
                                                                                   );
                                                                               }

                                                                               if (!method.getSignature().hasMojangName() && method.getSignature().hasObfuscatedName())
                                                                               {
                                                                                   builder.withSignature(
                                                                                     NamedBuilder.create(method.getSignature())
                                                                                       .withMojang(
                                                                                         remapper.mapSignature(
                                                                                           method.getSignature()
                                                                                             .getObfuscatedName()
                                                                                             .orElseThrow(() -> new IllegalStateException("Missing obfuscated method signature.")),
                                                                                           false
                                                                                         )
                                                                                       )
                                                                                       .build()
                                                                                   );
                                                                               }
                                                                               return builder.build();
                                                                           })
                                                                           .collect(Collectors.toSet()))
                                                            .withFields(classMetadata.getFields().stream()
                                                                          .map(field -> {
                                                                              final FieldMetadataBuilder fieldMetadataBuilder = FieldMetadataBuilder.create(field);

                                                                              if (!field.getDescriptor().hasMojangName() && field.getDescriptor().hasObfuscatedName())
                                                                              {
                                                                                  fieldMetadataBuilder.withDescriptor(
                                                                                    NamedBuilder.create(field.getDescriptor())
                                                                                      .withMojang(
                                                                                        remapper.mapMethodDesc(
                                                                                          field.getDescriptor()
                                                                                            .getObfuscatedName()
                                                                                            .orElseThrow(() -> new IllegalStateException("Missing obfuscated field descriptor."))
                                                                                        )
                                                                                      )
                                                                                      .build()
                                                                                  );
                                                                              }

                                                                              if (field.getSignature().hasObfuscatedName() && !field.getSignature().hasMojangName()) {
                                                                                  fieldMetadataBuilder.withSignature(
                                                                                    NamedBuilder.create(field.getSignature())
                                                                                        .withMojang(
                                                                                          remapper.mapSignature(
                                                                                            field.getSignature().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated field signature")),
                                                                                            true
                                                                                          )
                                                                                        )
                                                                                    .build()
                                                                                  );
                                                                              }

                                                                              return fieldMetadataBuilder.build();
                                                                          })
                                                                          .collect(Collectors.toSet()));


        if (!classMetadata.getSuperName().hasMojangName() && classMetadata.getSuperName().hasObfuscatedName())
        {
            final String obfuscatedSuperName =
              classMetadata.getSuperName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated name on super class."));
            final NamedBuilder namedBuilder = NamedBuilder.create(classMetadataBuilder.getSuperName());
            namedBuilder.withMojang(
              obfToMojNameMap.getOrDefault(obfuscatedSuperName, obfuscatedSuperName)
            );

            classMetadataBuilder.withSuperName(namedBuilder.build());
        }

        if (!classMetadata.getSignature().hasMojangName() && classMetadata.getSignature().hasObfuscatedName())
        {
            final String obfuscatedSignature =
              classMetadata.getSignature().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated signature on class."));
            final NamedBuilder namedBuilder = NamedBuilder.create(classMetadataBuilder.getSignature());
            namedBuilder.withMojang(
              remapper.mapSignature(obfuscatedSignature, false)
            );

            classMetadataBuilder.withSuperName(namedBuilder.build());
        }

        if (!classMetadata.getInterfaces().isEmpty()) {
            final LinkedHashSet<Named> interfaces = new LinkedHashSet<>();
            classMetadata.getInterfaces().forEach(interfaceName -> {
                if (interfaceName.hasObfuscatedName() && interfaceName.hasMojangName())
                {
                    interfaces.add(interfaceName);
                } else if (interfaceName.hasObfuscatedName() && !interfaceName.hasMojangName()) {
                    interfaces.add(NamedBuilder.create(interfaceName)
                      .withMojang(remapper.mapType(
                        interfaceName.getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated interface name"))
                      ))
                      .build()
                    );
                }
                classMetadataBuilder.withInterfaces(interfaces);
            });
        }

        return classMetadataBuilder.build();
    }

    private static ClassMetadata adaptMethodReferences(
      final ClassMetadata classMetadata,
      final Map<String, String> obfToMojNameMap,
      final Map<String, MethodMetadata> obfKeyToMojMethodNameMap
    )
    {
        final Map<String, String> obfToMojMethodNameMap = obfKeyToMojMethodNameMap.entrySet().stream().collect(Collectors.toMap(
          Map.Entry::getKey,
          e -> e.getValue().getName().getMojangName().orElseGet(() -> e.getValue().getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing mojang name")))
        ));

        final ASMRemapper remapper = new ASMRemapper(
          obfToMojNameMap,
          obfToMojMethodNameMap
        );

        final ClassMetadataBuilder classMetadataBuilder = ClassMetadataBuilder.create(classMetadata)
                                                            .withInnerClasses(classMetadata.getInnerClasses().stream()
                                                                                .map(inner -> adaptMethodReferences(inner, obfToMojNameMap, obfKeyToMojMethodNameMap))
                                                                                .collect(Collectors.toSet()))
                                                            .withMethods(classMetadata.getMethods().stream()
                                                                           .map(method -> {
                                                                               final MethodMetadataBuilder builder = MethodMetadataBuilder.create(method);

                                                                               if (method.getBouncingTarget().isPresent()) {
                                                                                   final BouncingTargetMetadataBuilder bouncingBuilder = BouncingTargetMetadataBuilder.create();

                                                                                   if (method.getBouncingTarget().get().getTarget().isPresent()) {
                                                                                       final String obfuscatedKey = buildMethodKey(
                                                                                         method.getBouncingTarget().get().getTarget().get()
                                                                                       );
                                                                                       final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedKey);
                                                                                       if (methodMetadata != null) {
                                                                                           final MethodReferenceBuilder targetBuilder = MethodReferenceBuilder.create()
                                                                                                                                          .withOwner(methodMetadata.getOwner())
                                                                                                                                          .withName(methodMetadata.getName())
                                                                                                                                          .withDescriptor(methodMetadata.getDescriptor())
                                                                                                                                          .withSignature(methodMetadata.getSignature());

                                                                                           if (!methodMetadata.getSignature().hasMojangName() && methodMetadata.getSignature().hasObfuscatedName())
                                                                                           {
                                                                                               targetBuilder.withSignature(
                                                                                                 NamedBuilder.create(methodMetadata.getSignature())
                                                                                                   .withMojang(
                                                                                                     remapper.mapSignature(
                                                                                                       methodMetadata.getSignature()
                                                                                                         .getObfuscatedName()
                                                                                                         .orElseThrow(() -> new IllegalStateException("Missing obfuscated method signature.")),
                                                                                                       false
                                                                                                     )
                                                                                                   )
                                                                                                   .build()
                                                                                               );
                                                                                           }

                                                                                           bouncingBuilder.withTarget(targetBuilder.build());
                                                                                       }
                                                                                       else
                                                                                       {
                                                                                           bouncingBuilder.withTarget(
                                                                                             method.getBouncingTarget().get().getTarget().get()
                                                                                           );
                                                                                       }
                                                                                   }

                                                                                   if (method.getBouncingTarget().get().getOwner().isPresent()) {
                                                                                       final String obfuscatedKey = buildMethodKey(
                                                                                         method.getBouncingTarget().get().getOwner().get()
                                                                                       );
                                                                                       final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedKey);
                                                                                       if (methodMetadata != null) {
                                                                                           final MethodReferenceBuilder ownerBuilder = MethodReferenceBuilder.create()
                                                                                                                                         .withOwner(methodMetadata.getOwner())
                                                                                                                                         .withName(methodMetadata.getName())
                                                                                                                                         .withDescriptor(methodMetadata.getDescriptor())
                                                                                                                                         .withSignature(methodMetadata.getSignature());

                                                                                           if (!methodMetadata.getSignature().hasMojangName() && methodMetadata.getSignature().hasObfuscatedName())
                                                                                           {
                                                                                               ownerBuilder.withSignature(
                                                                                                 NamedBuilder.create(methodMetadata.getSignature())
                                                                                                   .withMojang(
                                                                                                     remapper.mapSignature(
                                                                                                       methodMetadata.getSignature()
                                                                                                         .getObfuscatedName()
                                                                                                         .orElseThrow(() -> new IllegalStateException("Missing obfuscated method signature.")),
                                                                                                       false
                                                                                                     )
                                                                                                   )
                                                                                                   .build()
                                                                                               );
                                                                                           }

                                                                                           bouncingBuilder.withOwner(ownerBuilder.build());
                                                                                       }
                                                                                       else
                                                                                       {
                                                                                           bouncingBuilder.withOwner(
                                                                                             method.getBouncingTarget().get().getTarget().get()
                                                                                           );
                                                                                       }
                                                                                   }

                                                                                   builder.withBouncingTarget(bouncingBuilder.build());
                                                                               }

                                                                               if (method.getParent().isPresent()) {
                                                                                   final String obfuscatedKey = buildMethodKey(
                                                                                     method.getParent().get()
                                                                                   );
                                                                                   final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedKey);

                                                                                   if (methodMetadata != null) {
                                                                                       final MethodReferenceBuilder parentBuilder = MethodReferenceBuilder.create()
                                                                                                                                      .withOwner(methodMetadata.getOwner())
                                                                                                                                      .withName(methodMetadata.getName())
                                                                                                                                      .withDescriptor(methodMetadata.getDescriptor())
                                                                                                                                      .withSignature(methodMetadata.getSignature());

                                                                                       if (!methodMetadata.getSignature().hasMojangName() && methodMetadata.getSignature().hasObfuscatedName())
                                                                                       {
                                                                                           parentBuilder.withSignature(
                                                                                             NamedBuilder.create(methodMetadata.getSignature())
                                                                                               .withMojang(
                                                                                                 remapper.mapSignature(
                                                                                                   methodMetadata.getSignature()
                                                                                                     .getObfuscatedName()
                                                                                                     .orElseThrow(() -> new IllegalStateException("Missing obfuscated method signature.")),
                                                                                                   false
                                                                                                 )
                                                                                               )
                                                                                               .build()
                                                                                           );
                                                                                       }

                                                                                       builder.withParent(parentBuilder.build());
                                                                                   }
                                                                               }

                                                                               if (!method.getOverrides().isEmpty()) {
                                                                                   final LinkedHashSet<MethodReference> overrides = new LinkedHashSet<>();
                                                                                   for (final MethodReference override : method.getOverrides())
                                                                                   {
                                                                                       final String obfuscatedKey = buildMethodKey(
                                                                                         override
                                                                                       );
                                                                                       final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedKey);

                                                                                       if (methodMetadata != null) {
                                                                                           final MethodReferenceBuilder overrideBuilder = MethodReferenceBuilder.create()
                                                                                                                                          .withOwner(methodMetadata.getOwner())
                                                                                                                                          .withName(methodMetadata.getName())
                                                                                                                                          .withDescriptor(methodMetadata.getDescriptor())
                                                                                                                                          .withSignature(methodMetadata.getSignature());

                                                                                           if (!methodMetadata.getSignature().hasMojangName() && methodMetadata.getSignature().hasObfuscatedName())
                                                                                           {
                                                                                               overrideBuilder.withSignature(
                                                                                                 NamedBuilder.create(methodMetadata.getSignature())
                                                                                                   .withMojang(
                                                                                                     remapper.mapSignature(
                                                                                                       methodMetadata.getSignature()
                                                                                                         .getObfuscatedName()
                                                                                                         .orElseThrow(() -> new IllegalStateException("Missing obfuscated method signature.")),
                                                                                                       false
                                                                                                     )
                                                                                                   )
                                                                                                   .build()
                                                                                               );
                                                                                           }

                                                                                           overrides.add(overrideBuilder.build());
                                                                                       }
                                                                                   }

                                                                                   builder.withOverrides(overrides);
                                                                               }

                                                                               return builder.build();
                                                                           })
                                                                           .collect(Collectors.toSet()));
        return classMetadataBuilder.build();
    }

    private static void collectClassNames(final ClassMetadata classMetadata, final Map<String, String> obfToMojMap)
    {
        obfToMojMap.put(
          classMetadata.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated name.")),
          classMetadata.getName().getMojangName().orElseThrow(() -> new IllegalStateException("Missing mojang name."))
        );

        classMetadata.getInnerClasses().forEach(innerClassMetadata -> collectClassNames(innerClassMetadata, obfToMojMap));
    }

    private static void collectMethodNames(final ClassMetadata classMetadata, final Map<String, MethodMetadata> objKeyToMojNameMap)
    {
        classMetadata.getMethods().forEach(methodMetadata -> {
            objKeyToMojNameMap.put(
              buildMethodKey(methodMetadata),
              methodMetadata
            );
        });

        classMetadata.getInnerClasses().forEach(innerClassMetadata -> collectMethodNames(innerClassMetadata, objKeyToMojNameMap));
    }

    private static String buildMethodKey(final MethodMetadata methodMetadata)
    {
        return buildMethodKey(
          methodMetadata.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
          methodMetadata.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated method name.")),
          methodMetadata.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    private static String buildMethodKey(final MethodReference methodReference)
    {
        return buildMethodKey(
          methodReference.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
          methodReference.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated method name.")),
          methodReference.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    private static String buildMethodKey(final String className, final String methodName, final String methodDesc)
    {
        return String.format("%s/%s%s",
          className,
          methodName,
          methodDesc);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void execute()
    {
        try
        {
            final File target = this.getOutput().getAsFile().get();
            final File parentDirectory = target.getParentFile();
            parentDirectory.mkdirs();

            final File leftSourceFile = this.getLeftSource().getAsFile().get();
            final File rightSourceFile = this.getRightSource().getAsFile().get();

            final Gson gson = new GsonBuilder()
                                .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                                .registerTypeAdapterFactory(new MetadataAdapterFactory())
                                .setPrettyPrinting()
                                .create();

            final SourceMetadata leftSourceMetadata = gson.fromJson(new FileReader(leftSourceFile), SourceMetadata.class);
            final SourceMetadata rightSourceMetadata = gson.fromJson(new FileReader(rightSourceFile), SourceMetadata.class);

            final SourceMetadata mergedMetadata = MetadataMerger.mergeOnObfuscatedNames(leftSourceMetadata, rightSourceMetadata);

            final SourceMetadata adaptedMetadata = adaptTypes(mergedMetadata);

            final FileWriter fileWriter = new FileWriter(target);
            gson.toJson(adaptedMetadata, fileWriter);
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

    @InputFile
    public abstract RegularFileProperty getLeftSource();

    @InputFile
    public abstract RegularFileProperty getRightSource();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}
