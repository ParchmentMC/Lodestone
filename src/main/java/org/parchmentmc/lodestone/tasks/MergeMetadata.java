package org.parchmentmc.lodestone.tasks;

import com.google.gson.Gson;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.parchmentmc.feather.metadata.*;
import org.parchmentmc.feather.named.Named;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.feather.util.CollectorUtils;
import org.parchmentmc.feather.utils.MetadataMerger;
import org.parchmentmc.lodestone.util.ASMRemapper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * It provides methods for merging metadata in Minecraft versions.
 */
public abstract class MergeMetadata extends MinecraftVersionTask {

    /**
     * Constructs a new {@code MergeMetadata} object.
     * It sets the default output file for merged metadata.
     */
    public MergeMetadata() {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("merged.json")));
    }

    /**
     * Returns the output file property for the merged metadata.
     *
     * @return the output file property
     */
    @OutputFile
    public abstract RegularFileProperty getOutput();

    /**
     * Adapts the types of the source metadata by mapping obfuscated names to Mojang names.
     *
     * @param sourceMetadata the source metadata to adapt
     * @return the adapted source metadata
     */
    private static SourceMetadata adaptTypes(final SourceMetadata sourceMetadata) {
        final Map<String, String> obfToMojClassNameMap = new LinkedHashMap<>();
        final Map<String, MethodMetadata> obfKeyToMojMethodNameMap = new LinkedHashMap<>();
        final Map<String, FieldMetadata> obfKeyToMojFieldNameMap = new LinkedHashMap<>();
        sourceMetadata.getClasses().forEach(classMetadata -> {
            collectClassNames(classMetadata, obfToMojClassNameMap);
            collectMethodNames(classMetadata, obfKeyToMojMethodNameMap);
            collectFieldNames(classMetadata, obfKeyToMojFieldNameMap);
        });

        final SourceMetadataBuilder sourceMetadataBuilder = SourceMetadataBuilder.create();

        sourceMetadataBuilder.withSpecVersion(sourceMetadata.getSpecificationVersion())
                .withMinecraftVersion(sourceMetadata.getMinecraftVersion());

        // No need to retain insertion order, since this is only for lookup and not iterated over
        final Map<String, String> obfToMojMethodNameMap = obfKeyToMojMethodNameMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getName().getMojangName().orElseThrow(() -> new IllegalStateException("Missing mojang name"))
        ));

        ASMRemapper remapper = new ASMRemapper(
                obfToMojClassNameMap,
                obfToMojMethodNameMap
        );

        for (final ClassMetadata aClass : sourceMetadata.getClasses()) {
            sourceMetadataBuilder.addClass(
                    adaptSignatures(
                            aClass,
                            obfToMojClassNameMap,
                            remapper
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

        // No need to retain insertion order, since this is only for lookup and not iterated over
        final Map<String, String> obfToMojMethodNameWithObfMap = obfKeyToMojMethodNameMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue()
                        .getName()
                        .getMojangName()
                        .orElseGet(() -> e.getValue().getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing mojang name")))
        ));

        remapper = new ASMRemapper(
                obfToMojClassNameMap,
                obfToMojMethodNameWithObfMap
        );

        for (final ClassMetadata aClass : signatureRemappedData.getClasses()) {
            bouncerRemappedDataBuilder.addClass(
                    adaptReferences(
                            aClass,
                            obfKeyToMojMethodNameMap,
                            obfKeyToMojFieldNameMap,
                            remapper
                    )
            );
        }

        return bouncerRemappedDataBuilder.build();
    }

    /**
     * Adapts the signatures of a class metadata by mapping obfuscated names to Mojang names.
     *
     * @param classMetadata the class metadata to adapt
     * @param obfToMojNameMap the map of obfuscated names to Mojang names
     * @param remapper the ASMRemapper used for remapping
     * @return the adapted class metadata
     */
    private static ClassMetadata adaptSignatures(
            final ClassMetadata classMetadata,
            final Map<String, String> obfToMojNameMap,
            final ASMRemapper remapper
    ) {

        final ClassMetadataBuilder classMetadataBuilder = ClassMetadataBuilder.create(classMetadata)
                .withInnerClasses(classMetadata.getInnerClasses().stream()
                        .map(inner -> adaptSignatures(inner, obfToMojNameMap, remapper))
                        .collect(CollectorUtils.toLinkedSet()))
                .withMethods(classMetadata.getMethods().stream()
                        .map(method -> {
                            final MethodMetadataBuilder builder = MethodMetadataBuilder.create(method);

                            if (!method.getOwner().hasMojangName() && method.getOwner().hasObfuscatedName()) {
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

                            if (!method.getDescriptor().hasMojangName() && method.getDescriptor().hasObfuscatedName()) {
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

                            if (!method.getSignature().hasMojangName() && method.getSignature().hasObfuscatedName()) {
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
                        .collect(CollectorUtils.toLinkedSet()))
                .withFields(classMetadata.getFields().stream()
                        .map(field -> {
                            final FieldMetadataBuilder fieldMetadataBuilder = FieldMetadataBuilder.create(field);

                            if (!field.getDescriptor().hasMojangName() && field.getDescriptor().hasObfuscatedName()) {
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
                        .collect(CollectorUtils.toLinkedSet()))
                .withRecords(classMetadata.getRecords().stream()
                        .map(record -> {
                            final RecordMetadataBuilder builder = RecordMetadataBuilder.create(record);

                            if (!record.getOwner().hasMojangName() && record.getOwner().hasObfuscatedName()) {
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
                        .collect(CollectorUtils.toLinkedSet()));


        if (!classMetadata.getSuperName().hasMojangName() && classMetadata.getSuperName().hasObfuscatedName()) {
            final String obfuscatedSuperName =
                    classMetadata.getSuperName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated name on super class."));
            final NamedBuilder namedBuilder = NamedBuilder.create(classMetadataBuilder.getSuperName());
            namedBuilder.withMojang(
                    obfToMojNameMap.getOrDefault(obfuscatedSuperName, obfuscatedSuperName)
            );

            classMetadataBuilder.withSuperName(namedBuilder.build());
        }

        if (!classMetadata.getSignature().hasMojangName() && classMetadata.getSignature().hasObfuscatedName()) {
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
                if (interfaceName.hasObfuscatedName() && interfaceName.hasMojangName()) {
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

    /**
     * Adapts the references of a class metadata by mapping obfuscated names to Mojang names.
     *
     * @param classMetadata the class metadata to adapt
     * @param obfKeyToMojMethodNameMap the map of obfuscated method names to Mojang method names
     * @param obfKeyToMojFieldNameMap the map of obfuscated field names to Mojang field names
     * @param remapper the ASMRemapper used for remapping
     * @return the adapted class metadata
     */
    private static ClassMetadata adaptReferences(
            final ClassMetadata classMetadata,
            final Map<String, MethodMetadata> obfKeyToMojMethodNameMap,
            final Map<String, FieldMetadata> obfKeyToMojFieldNameMap,
            final ASMRemapper remapper
    ) {

        final ClassMetadataBuilder classMetadataBuilder = ClassMetadataBuilder.create(classMetadata)
                .withInnerClasses(classMetadata.getInnerClasses().stream()
                        .map(inner -> adaptReferences(inner, obfKeyToMojMethodNameMap, obfKeyToMojFieldNameMap, remapper))
                        .collect(CollectorUtils.toLinkedSet()))
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
                                        final ReferenceBuilder targetBuilder = createRemappedReference(remapper, methodMetadata);
                                        bouncingBuilder.withTarget(targetBuilder.build());
                                    } else {
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
                                        final ReferenceBuilder ownerBuilder = createRemappedReference(remapper, methodMetadata);
                                        bouncingBuilder.withOwner(ownerBuilder.build());
                                    } else {
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
                                    final ReferenceBuilder parentBuilder = createRemappedReference(remapper, methodMetadata);
                                    builder.withParent(parentBuilder.build());
                                }
                            }

                            if (!method.getOverrides().isEmpty()) {
                                final LinkedHashSet<Reference> overrides = new LinkedHashSet<>();
                                for (final Reference override : method.getOverrides()) {
                                    final String obfuscatedKey = buildMethodKey(
                                            override
                                    );
                                    final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedKey);

                                    if (methodMetadata != null) {
                                        final ReferenceBuilder overrideBuilder = createRemappedReference(remapper, methodMetadata);
                                        overrides.add(overrideBuilder.build());
                                    }
                                }

                                builder.withOverrides(overrides);
                            }

                            return builder.build();
                        })
                        .collect(CollectorUtils.toLinkedSet()))
                .withRecords(classMetadata.getRecords().stream()
                        .map(record -> {
                            final RecordMetadataBuilder builder = RecordMetadataBuilder.create(record);

                            final String obfuscatedMethodKey = buildMethodKey(
                                    record.getGetter()
                            );
                            final MethodMetadata methodMetadata = obfKeyToMojMethodNameMap.get(obfuscatedMethodKey);
                            if (methodMetadata != null) {
                                final ReferenceBuilder getterBuilder = createRemappedReference(remapper, methodMetadata);
                                builder.withGetter(getterBuilder.build());
                            }


                            final String obfuscatedFieldKey = buildFieldKey(
                                    record.getField()
                            );
                            final FieldMetadata fieldMetadata = obfKeyToMojFieldNameMap.get(obfuscatedFieldKey);
                            if (fieldMetadata != null) {
                                final ReferenceBuilder fieldBuilder = createRemappedReference(remapper, fieldMetadata);
                                builder.withField(fieldBuilder.build());
                            }

                            return builder.build();
                        })
                        .collect(CollectorUtils.toLinkedSet()));
        return classMetadataBuilder.build();
    }

    /**
     * Creates a remapped reference using the given ASMRemapper and method metadata.
     *
     * @param remapper The ASMRemapper used for remapping the signature.
     * @param methodMetadata The method metadata containing information about the method.
     * @return A ReferenceBuilder representing the remapped reference.
     * @throws IllegalStateException if the obfuscated name is missing in the method signature.
     */
    private static ReferenceBuilder createRemappedReference(final ASMRemapper remapper, final BaseReference methodMetadata) {
        final ReferenceBuilder targetBuilder = ReferenceBuilder.create()
                .withOwner(methodMetadata.getOwner())
                .withName(methodMetadata.getName())
                .withDescriptor(methodMetadata.getDescriptor())
                .withSignature(methodMetadata.getSignature());

        if (!methodMetadata.getSignature().hasMojangName() && methodMetadata.getSignature().hasObfuscatedName()) {
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

    /**
     * Collects the obfuscated class names and their corresponding Mojang names recursively
     * from the given class metadata and stores them in the provided map.
     *
     * @param classMetadata the class metadata to collect names from
     * @param obfToMojMap the map to store the collected names
     */
    private static void collectClassNames(final ClassMetadata classMetadata, final Map<String, String> obfToMojMap) {
        obfToMojMap.put(
                classMetadata.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated name.")),
                classMetadata.getName().getMojangName().orElseThrow(() -> new IllegalStateException("Missing mojang name."))
        );

        classMetadata.getInnerClasses().forEach(innerClassMetadata -> collectClassNames(innerClassMetadata, obfToMojMap));
    }

    /**
     * Recursively collects the method names from the given ClassMetadata and populates them into the objKeyToMojNameMap.
     *
     * @param classMetadata       The ClassMetadata to collect method names from.
     * @param objKeyToMojNameMap  The map to store the method names, where the keys are method keys and the values are MethodMetadata objects.
     */
    private static void collectMethodNames(final ClassMetadata classMetadata, final Map<String, MethodMetadata> objKeyToMojNameMap) {
        classMetadata.getMethods().forEach(methodMetadata -> objKeyToMojNameMap.put(
                buildMethodKey(methodMetadata),
                methodMetadata
        ));

        classMetadata.getInnerClasses().forEach(innerClassMetadata -> collectMethodNames(innerClassMetadata, objKeyToMojNameMap));
    }

    /**
     * Recursively collects the field names from the given ClassMetadata and populates them into the objKeyToMojNameMap.
     *
     * @param classMetadata       The ClassMetadata to collect field names from.
     * @param objKeyToMojNameMap  The map to store the field names, where the keys are field keys and the values are FieldMetadata objects.
     */
    private static void collectFieldNames(final ClassMetadata classMetadata, final Map<String, FieldMetadata> objKeyToMojNameMap) {
        classMetadata.getFields().forEach(fieldMetadata -> objKeyToMojNameMap.put(
                buildFieldKey(fieldMetadata),
                fieldMetadata
        ));

        classMetadata.getInnerClasses().forEach(innerClassMetadata -> collectFieldNames(innerClassMetadata, objKeyToMojNameMap));
    }

    /**
     * Builds a method key based on the given MethodMetadata.
     *
     * @param methodMetadata  The MethodMetadata object containing the necessary information.
     * @return A string representing the method key in the format "className/methodNamemethodDesc".
     * @throws IllegalStateException if any obfuscated name in the MethodMetadata is missing.
     */
    private static String buildMethodKey(final MethodMetadata methodMetadata) {
        return buildMethodKey(
                methodMetadata.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
                methodMetadata.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated method name.")),
                methodMetadata.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    /**
     * Builds a method key based on the given Reference object.
     *
     * @param reference  The Reference object containing the necessary information.
     * @return A string representing the method key in the format "className/methodNamemethodDesc".
     * @throws IllegalStateException if any obfuscated name in the Reference is missing.
     */
    private static String buildMethodKey(final Reference Reference) {
        return buildMethodKey(
                Reference.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
                Reference.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated method name.")),
                Reference.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    /**
     * Builds a method key based on the given class name, method name, and method descriptor.
     *
     * @param className    The obfuscated class name.
     * @param methodName   The obfuscated method name.
     * @param methodDesc   The obfuscated method descriptor.
     * @return A string representing the method key in the format "className/methodNamemethodDesc".
     */
    private static String buildMethodKey(final String className, final String methodName, final String methodDesc) {
        return String.format("%s/%s%s",
                className,
                methodName,
                methodDesc);
    }

    /**
     * Builds a field key based on the given FieldMetadata.
     *
     * @param fieldMetadata  The FieldMetadata object containing the necessary information.
     * @return A string representing the field key in the format "className/fieldNamefieldDesc".
     * @throws IllegalStateException if any obfuscated name in the FieldMetadata is missing.
     */
    private static String buildFieldKey(final FieldMetadata fieldMetadata) {
        return buildFieldKey(
                fieldMetadata.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
                fieldMetadata.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated field name.")),
                fieldMetadata.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    /**
     * Builds a field key based on the given Reference object.
     *
     * @param reference  The Reference object containing the necessary information.
     * @return A string representing the field key in the format "className/fieldNamefieldDesc".
     * @throws IllegalStateException if any obfuscated name in the Reference is missing.
     */
    private static String buildFieldKey(final Reference fieldMetadata) {
        return buildFieldKey(
                fieldMetadata.getOwner().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated owner name.")),
                fieldMetadata.getName().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated field name.")),
                fieldMetadata.getDescriptor().getObfuscatedName().orElseThrow(() -> new IllegalStateException("Missing obfuscated descriptor."))
        );
    }

    /**
     * Builds a field key based on the given class name, field name, and field descriptor.
     *
     * @param className    The obfuscated class name.
     * @param fieldName    The obfuscated field name.
     * @param fieldDesc    The obfuscated field descriptor.
     * @return A string representing the field key in the format "className/fieldNamefieldDesc".
     */
    private static String buildFieldKey(final String className, final String fieldName, final String fieldDesc) {
        return String.format("%s/%s%s",
                className,
                fieldName,
                fieldDesc);
    }

    /**
     * Executes the task to merge source metadata from the left and right sources, adapt the types, and write the merged metadata to the output file.
     *
     * @throws IOException if an I/O error occurs during file operations.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TaskAction
    void execute() throws IOException {
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

    /**
     * Returns the property representing the left source file for merging.
     *
     * @return The property representing the left source file.
     */
    @InputFile
    public abstract RegularFileProperty getLeftSource();

    /**
     * Returns the property representing the right source file for merging.
     *
     * @return The property representing the right source file.
     */
    @InputFile
    public abstract RegularFileProperty getRightSource();
}
