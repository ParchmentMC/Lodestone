package org.parchmentmc.lodestone.converter;

import org.parchmentmc.feather.metadata.MethodMetadata;
import org.parchmentmc.feather.metadata.MethodMetadataBuilder;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.lodestone.asm.MutableClassInfo;
import org.parchmentmc.lodestone.asm.MutableMethodInfo;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;

public class MethodConverter
{
    public MethodMetadata convert(final MutableClassInfo classInfo, final MutableMethodInfo mutableMethodInfo)
    {
        final ReferenceConverter methodReferenceConverter = new ReferenceConverter();
        final BouncingTargetConverter bouncingTargetConverter = new BouncingTargetConverter();

        return MethodMetadataBuilder.create()
          .withName(NamedBuilder.create().withObfuscated(mutableMethodInfo.getMethod().getName()).build())
          .withOwner(NamedBuilder.create().withObfuscated(classInfo.getName()).build())
          .withDescriptor(NamedBuilder.create().withObfuscated(mutableMethodInfo.getMethod().getDesc()).build())
          .withSignature(NamedBuilder.create().withObfuscated(mutableMethodInfo.getSignature()).build())
          .withSecuritySpecification(mutableMethodInfo.getAccess())
          .withLambda(mutableMethodInfo.isLambda())
          .withOverrides(mutableMethodInfo.getOverrides().stream().map(methodReferenceConverter::convert).collect(Collectors.toCollection(LinkedHashSet::new)))
          .withParent(methodReferenceConverter.convert(mutableMethodInfo.getParent()))
          .withBouncingTarget(bouncingTargetConverter.convert(mutableMethodInfo.getBouncer()))
          .build();
    }
}
