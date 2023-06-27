package org.parchmentmc.lodestone.converter;

import org.parchmentmc.feather.metadata.BouncingTargetMetadata;
import org.parchmentmc.feather.metadata.BouncingTargetMetadataBuilder;
import org.parchmentmc.lodestone.asm.MutableBouncerInfo;

public class BouncingTargetConverter {

    /**
     * Converts the given MutableBouncerInfo object into a BouncingTargetMetadata object.
     *
     * @param bouncerInfo the MutableBouncerInfo object to convert
     * @return a BouncingTargetMetadata object representing the converted MutableBouncerInfo object, or null if the input is null
     * @throws ReferenceConversionException if an error occurs while converting a method reference in the MutableBouncerInfo object
     */
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
