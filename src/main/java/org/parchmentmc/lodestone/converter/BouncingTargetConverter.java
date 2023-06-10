package org.parchmentmc.lodestone.converter;

import org.parchmentmc.feather.metadata.BouncingTargetMetadata;
import org.parchmentmc.feather.metadata.BouncingTargetMetadataBuilder;
import org.parchmentmc.lodestone.asm.MutableBouncerInfo;

public class BouncingTargetConverter {
    public BouncingTargetMetadata convert(final MutableBouncerInfo bouncerInfo) {
        final ReferenceConverter methodReferenceConverter = new ReferenceConverter();

        if (bouncerInfo == null)
            return null;

        return BouncingTargetMetadataBuilder.create()
                .withTarget(methodReferenceConverter.convert(bouncerInfo.getTarget()))
                .withOwner(methodReferenceConverter.convert(bouncerInfo.getOwner()))
                .build();
    }
}
