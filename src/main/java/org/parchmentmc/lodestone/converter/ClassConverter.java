package org.parchmentmc.lodestone.converter;

import org.parchmentmc.feather.metadata.ClassMetadata;
import org.parchmentmc.feather.metadata.ClassMetadataBuilder;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.lodestone.asm.MutableClassInfo;

import java.util.stream.Collectors;

public class ClassConverter {

    public ClassMetadata convert(final MutableClassInfo classInfo) {
        final MethodConverter methodConverter = new MethodConverter();
        final FieldConverter fieldConverter = new FieldConverter();
        final RecordConverter recordConverter = new RecordConverter();

        final ClassMetadataBuilder classMetadataBuilder = ClassMetadataBuilder.create()
                .withName(NamedBuilder.create().withObfuscated(classInfo.getName()).build())
                .withSuperName(NamedBuilder.create().withObfuscated(classInfo.getSuperName()).build())
                .withSecuritySpecifications(classInfo.getAccess())
                .withSignature(NamedBuilder.create().withObfuscated(classInfo.getSignature()).build())
                .withInterfaces(classInfo.getInterfaces().stream().map(interfaceName -> NamedBuilder.create().withObfuscated(interfaceName).build()).collect(Collectors.toSet()))
                .withFields(classInfo.getFields().values().stream().map(fieldInfo -> fieldConverter.convert(classInfo, fieldInfo)).collect(Collectors.toSet()))
                .withMethods(classInfo.getMethods().values().stream().map(methodInfo -> methodConverter.convert(classInfo, methodInfo)).collect(Collectors.toSet()))
                .withRecords(
                        classInfo.getRecords().values().stream().map(recordInfo -> recordConverter.convert(classInfo, recordInfo)).collect(
                                Collectors.toSet())
                )
                .withIsRecord(classInfo.isRecord());

        if (classInfo.getName().contains("$")) {
            final String outerName = classInfo.getName().substring(0, classInfo.getName().lastIndexOf("$"));
            classMetadataBuilder.withOwner(NamedBuilder.create().withObfuscated(outerName).build());
        }

        return classMetadataBuilder.build();
    }
}
