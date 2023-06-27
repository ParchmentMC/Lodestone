package org.parchmentmc.lodestone.converter;

import org.parchmentmc.feather.metadata.MethodMetadata;
import org.parchmentmc.feather.metadata.MethodMetadataBuilder;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.feather.util.CollectorUtils;
import org.parchmentmc.lodestone.asm.MutableClassInfo;
import org.parchmentmc.lodestone.asm.MutableMethodInfo;

public class MethodConverter {

    /**
     * Converts the given MutableMethodInfo object into a MethodMetadata object for the specified class.
     *
     * @param classInfo the MutableClassInfo object representing the class that the method belongs to
     * @param mutableMethodInfo the MutableMethodInfo object to convert
     * @return a MethodMetadata object representing the converted MutableMethodInfo object
     * @throws ReferenceConversionException if an error occurs while converting a reference in the MutableMethodInfo object
     */
    public MethodMetadata convert(final MutableClassInfo classInfo, final MutableMethodInfo mutableMethodInfo) {
        final ReferenceConverter methodReferenceConverter = new ReferenceConverter();
        final BouncingTargetConverter bouncingTargetConverter = new BouncingTargetConverter();

        return MethodMetadataBuilder.create()
                .withName(NamedBuilder.create().withObfuscated(mutableMethodInfo.getMethod().getName()).build())
                .withOwner(NamedBuilder.create().withObfuscated(classInfo.getName()).build())
                .withDescriptor(NamedBuilder.create().withObfuscated(mutableMethodInfo.getMethod().getDesc()).build())
                .withSignature(NamedBuilder.create().withObfuscated(mutableMethodInfo.getSignature()).build())
                .withSecuritySpecification(mutableMethodInfo.getAccess())
                .withLambda(mutableMethodInfo.isLambda())
                .withOverrides(mutableMethodInfo.getOverrides().stream().map(methodReferenceConverter::convert).collect(CollectorUtils.toLinkedSet()))
                .withParent(methodReferenceConverter.convert(mutableMethodInfo.getParent()))
                .withBouncingTarget(bouncingTargetConverter.convert(mutableMethodInfo.getBouncer()))
                .build();
    }
}
