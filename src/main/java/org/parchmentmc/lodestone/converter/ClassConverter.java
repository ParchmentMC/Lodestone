package org.parchmentmc.lodestone.converter;

import org.parchmentmc.feather.metadata.ClassMetadata;
import org.parchmentmc.feather.metadata.ClassMetadataBuilder;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.feather.util.CollectorUtils;
import org.parchmentmc.lodestone.asm.MutableClassInfo;

public class ClassConverter {

    /**
     * Converts the given MutableClassInfo object into a ClassMetadata object.
     *
     * @param classInfo the MutableClassInfo object to convert
     * @return a ClassMetadata object representing the converted MutableClassInfo object
     * @throws ReferenceConversionException if an error occurs while converting a reference in the MutableClassInfo object
     */
    public ClassMetadata convert(final MutableClassInfo classInfo) {
        final MethodConverter methodConverter = new MethodConverter();
        final FieldConverter fieldConverter = new FieldConverter();
        final RecordConverter recordConverter = new RecordConverter();

        final ClassMetadataBuilder classMetadataBuilder = ClassMetadataBuilder.create()
                .withName(NamedBuilder.create().withObfuscated(classInfo.getName()).build())
                .withSuperName(NamedBuilder.create().withObfuscated(classInfo.getSuperName()).build())
                .withSecuritySpecifications(classInfo.getAccess())
                .withSignature(NamedBuilder.create().withObfuscated(classInfo.getSignature()).build())
                .withInterfaces(classInfo.getInterfaces().stream().map(interfaceName -> NamedBuilder.create().withObfuscated(interfaceName).build()).collect(CollectorUtils.toLinkedSet()))
                .withFields(classInfo.getFields().values().stream().map(fieldInfo -> fieldConverter.convert(classInfo, fieldInfo)).collect(CollectorUtils.toLinkedSet()))
                .withMethods(classInfo.getMethods().values().stream().map(methodInfo -> methodConverter.convert(classInfo, methodInfo)).collect(CollectorUtils.toLinkedSet()))
                .withRecords(
                        classInfo.getRecords().values().stream().map(recordInfo -> recordConverter.convert(classInfo, recordInfo)).collect(CollectorUtils.toLinkedSet())
                )
                .withIsRecord(classInfo.isRecord());

        if (classInfo.getName().contains("$")) {
            final String outerName = classInfo.getName().substring(0, classInfo.getName().lastIndexOf("$"));
            classMetadataBuilder.withOwner(NamedBuilder.create().withObfuscated(outerName).build());
        }

        return classMetadataBuilder.build();
    }
}
