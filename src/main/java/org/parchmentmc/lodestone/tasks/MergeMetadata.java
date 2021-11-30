package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.feather.metadata.*;
import org.parchmentmc.feather.named.Named;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.feather.utils.MetadataMerger;
import org.parchmentmc.lodestone.util.ASMRemapper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class MergeMetadata extends MinecraftVersionTask
{

    public MergeMetadata()
    {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("merged.json")));
    }

    @OutputFile
    public abstract RegularFileProperty getOutput();

    private static SourceMetadata adaptTypes(final SourceMetadata sourceMetadata)
    {
        final Map<String, String> obfToMojClassNameMap = new HashMap<>();
        final Map<String, MethodMetadata> obfKeyToMojMethodNameMap = new HashMap<>();
        final Map<String, FieldMetadata> obfKeyToMojFieldNameMap = new HashMap<>();
        sourceMetadata.getClasses().forEach(classMetadata -> {
            collectClassNames(classMetadata, obfToMojClassNameMap);
            collectMethodNames(classMetadata, obfKeyToMojMethodNameMap);
            collectFieldNames(classMetadata, obfKeyToMojFieldNameMap);
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
        obfKeyToMojFieldNameMap.clear();
        signatureRemappedData.getClasses().forEach(classMetadata -> {
            collectClassNames(classMetadata, obfToMojClassNameMap);
            collectMethodNames(classMetadata, obfKeyToMojMethodNameMap);
            collectFieldNames(classMetadata, obfKeyToMojFieldNameMap);
        });

        final SourceMetadataBuilder bouncerRemappedDataBuilder = SourceMetadataBuilder.create();

        bouncerRemappedDataBuilder.withSpecVersion(sourceMetadata.getSpecificationVersion())
          .withMinecraftVersion(sourceMetadata.getMinecraftVersion());

        for (final ClassMetadata aClass : signatureRemappedData.getClasses())
        {
            bouncerRemappedDataBuilder.addClass(
              adaptReferences(
                aClass,
                obfToMojClassNameMap,
                obfKeyToMojMethodNameMap,
                obfKeyToMojFieldNameMap
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

                if (field.getSignature().hasObfuscatedName() && !field.getSignature().hasMojangName())
                {
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
            .collect(Collectors.toSet()))
          .withRecords(classMetadata.getRecords().stream()
            .map(record -> {
                final RecordMetadataBuilder builder = RecordMetadataBuilder.create(record);

                if (!record.getOwner().hasMojangName() && record.getOwner().hasObfuscatedName())
                {
                    builder.withOwner(
                      NamedBuilder.create(record.getOwner())
                        .withMojang(
                          obfToMojNameMap.getOrDefault(
                            record.getOwner()
                              .getObfuscatedName()
                              .orElseThrow(() -> new IllegalStateException("Missing obfuscated record owner name")),
                            record.getOwner()
                              .getObfuscatedName()
                              .orElseThrow(() -> new IllegalStateException("Missing obfuscated record owner name"))
                          )
                        )
                        .build()
                    );
                }

                return builder.build();
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

        if (!classMetadata.getInterfaces().isEmpty())
        {
            final LinkedHashSet<Named> interfaces = new LinkedHashSet<>();
            classMetadata.getInterfaces().forEach(interfaceName -> {
                if (interfaceName.hasObfuscatedName() && interfaceName.hasMojangName())
                {
                    interfaces.add(interfaceName);
                }
                else if (interfaceName.hasObfuscatedName() && !interfaceName.hasMojangName())
                {
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

    private static ClassMetadata adaptReferences(
      final ClassMetadata classMetadata,
      final Map<String, String> obfToMojNameMap,
      final Map<String, MethodMetadata> obfKeyToMojMethodNameMap,
      final Map<String, FieldMetadata> obfKeyToMojFieldNameMap
    )
    {
        final Map<String, String> obfToMojMethodNameMap = obfKeyToMojMethodNameMap.entrySet().stream().collect(Collectors.toMap(
          Map.Entry::getKey,
          e -> e.getValue()
            .getName()
            .getMojangName()
            .orElseGet(() -> e.getValue().getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing mojang name")))
        ));

        final ASMRemapper remapper = new ASMRemapper(
          obfToMojNameMap,
          obfToMojMethodNameMap
        );

        final ClassMetadataBuilder classMetadataBuilder = ClassMetadataBuilder.create(classMetadata)
          .withInnerClasses(classMetadata.getInnerClasses().stream()
            .map(inner -> adaptReferences(inner, obfToMojNameMap, obfKeyToMojMethodNameMap, obfKeyToMojFieldNameMap))
            .collect(Collectors.toSet()))
          .withMethods(classMetadata.getMethods().stream()
            .map(method -> {
                final MethodMetadataBuilder builder = MethodMetadataBuilder.create(method);

                if (method.getBouncingTarget().isPresent())
                {
                    final BouncingTargetMetadataBuilder bouncingBuilder = BouncingTargetMetadataBuilder.create();

                    if (method.getBouncingTarget().get().getTarget().isPresent())
                    {
                        final String obfuscatedKey = buildMethodKey(
                          method.getBouncingTarget().get().getTarget().get()
                        );
                        final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedKey);
                        if (methodMetadata != null)
                        {
                            final ReferenceBuilder targetBuilder = createRemappedReference(remapper, methodMetadata);
                            bouncingBuilder.withTarget(targetBuilder.build());
                        }
                        else
                        {
                            bouncingBuilder.withTarget(
                              method.getBouncingTarget().get().getTarget().get()
                            );
                        }
                    }

                    if (method.getBouncingTarget().get().getOwner().isPresent())
                    {
                        final String obfuscatedKey = buildMethodKey(
                          method.getBouncingTarget().get().getOwner().get()
                        );
                        final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedKey);
                        if (methodMetadata != null)
                        {
                            final ReferenceBuilder ownerBuilder = createRemappedReference(remapper, methodMetadata);
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

                if (method.getParent().isPresent())
                {
                    final String obfuscatedKey = buildMethodKey(
                      method.getParent().get()
                    );
                    final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedKey);

                    if (methodMetadata != null)
                    {
                        final ReferenceBuilder parentBuilder = createRemappedReference(remapper, methodMetadata);
                        builder.withParent(parentBuilder.build());
                    }
                }

                if (!method.getOverrides().isEmpty())
                {
                    final LinkedHashSet<Reference> overrides = new LinkedHashSet<>();
                    for (final Reference override : method.getOverrides())
                    {
                        final String obfuscatedKey = buildMethodKey(
                          override
                        );
                        final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedKey);

                        if (methodMetadata != null)
                        {
                            final ReferenceBuilder overrideBuilder = createRemappedReference(remapper, methodMetadata);
                            overrides.add(overrideBuilder.build());
                        }
                    }

                    builder.withOverrides(overrides);
                }

                return builder.build();
            })
            .collect(Collectors.toSet()))
          .withRecords(classMetadata.getRecords().stream()
            .map(record -> {
                final RecordMetadataBuilder builder = RecordMetadataBuilder.create(record);

                final String obfuscatedMethodKey = buildMethodKey(
                  record.getGetter()
                );
                final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedMethodKey);
                if (methodMetadata != null)
                {
                    final ReferenceBuilder getterBuilder = createRemappedReference(remapper, methodMetadata);
                    builder.withGetter(getterBuilder.build());
                }


                final String obfuscatedFieldKey = buildFieldKey(
                  record.getField()
                );
                final FieldMetadata fieldMetadata = obfKeyToMojFieldNameMap.get(obfuscatedFieldKey);
                if (fieldMetadata != null)
                {
                    final ReferenceBuilder fieldBuilder = createRemappedReference(remapper, fieldMetadata);
                    builder.withField(fieldBuilder.build());
                }

                return builder.build();
            })
            .collect(Collectors.toSet()));
        return classMetadataBuilder.build();
    }

    private static ReferenceBuilder createRemappedReference(final ASMRemapper remapper, final BaseReference methodMetadata)
    {
        final ReferenceBuilder targetBuilder = ReferenceBuilder.create()
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

        return targetBuilder;
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
        classMetadata.getMethods().forEach(methodMetadata -> objKeyToMojNameMap.put(
          buildMethodKey(methodMetadata),
          methodMetadata
        ));

        classMetadata.getInnerClasses().forEach(innerClassMetadata -> collectMethodNames(innerClassMetadata, objKeyToMojNameMap));
    }

    private static void collectFieldNames(final ClassMetadata classMetadata, final Map<String, FieldMetadata> objKeyToMojNameMap)
    {
        classMetadata.getFields().forEach(fieldMetadata -> objKeyToMojNameMap.put(
          buildFieldKey(fieldMetadata),
          fieldMetadata
        ));

        classMetadata.getInnerClasses().forEach(innerClassMetadata -> collectFieldNames(innerClassMetadata, objKeyToMojNameMap));
    }

    private static String buildMethodKey(final MethodMetadata methodMetadata)
    {
        return buildMethodKey(
          methodMetadata.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
          methodMetadata.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated method name.")),
          methodMetadata.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    private static String buildMethodKey(final Reference Reference)
    {
        return buildMethodKey(
          Reference.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
          Reference.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated method name.")),
          Reference.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    private static String buildMethodKey(final String className, final String methodName, final String methodDesc)
    {
        return String.format("%s/%s%s",
          className,
          methodName,
          methodDesc);
    }

    private static String buildFieldKey(final FieldMetadata fieldMetadata)
    {
        return buildFieldKey(
          fieldMetadata.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
          fieldMetadata.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated field name.")),
          fieldMetadata.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    private static String buildFieldKey(final Reference fieldMetadata)
    {
        return buildFieldKey(
          fieldMetadata.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
          fieldMetadata.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated field name.")),
          fieldMetadata.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    private static String buildFieldKey(final String className, final String fieldName, final String fieldDesc)
    {
        return String.format("%s/%s%s",
          className,
          fieldName,
          fieldDesc);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void execute() throws IOException
    {
        final File target = this.getOutput().getAsFile().get();
        final File parentDirectory = target.getParentFile();
        parentDirectory.mkdirs();

        final File leftSourceFile = this.getLeftSource().getAsFile().get();
        final File rightSourceFile = this.getRightSource().getAsFile().get();

        final Gson gson = ExtractMetadataTask.createMetadataGson();

        final SourceMetadata leftSourceMetadata = gson.fromJson(new FileReader(leftSourceFile), SourceMetadata.class);
        final SourceMetadata rightSourceMetadata = gson.fromJson(new FileReader(rightSourceFile), SourceMetadata.class);

        final SourceMetadata mergedMetadata = MetadataMerger.mergeOnObfuscatedNames(leftSourceMetadata, rightSourceMetadata);

        final SourceMetadata adaptedMetadata = adaptTypes(mergedMetadata);

        final FileWriter fileWriter = new FileWriter(target);
        gson.toJson(adaptedMetadata, fileWriter);
        fileWriter.flush();
        fileWriter.close();
    }

    @InputFile
    public abstract RegularFileProperty getLeftSource();

    @InputFile
    public abstract RegularFileProperty getRightSource();
}
