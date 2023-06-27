package org.parchmentmc.lodestone.converter;

import org.parchmentmc.feather.metadata.Reference;
import org.parchmentmc.feather.metadata.ReferenceBuilder;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.lodestone.asm.MutableMethodReferenceInfo;

public class ReferenceConverter {

    /**
     * Converts the given MutableMethodReferenceInfo object into a Reference object.
     *
     * @param refInfo the MutableMethodReferenceInfo object to convert
     * @return a Reference object representing the converted MutableMethodReferenceInfo object, or null if the input is null
     */
    public Reference convert(final MutableMethodReferenceInfo refInfo) {
        if (refInfo == null)
            return null;

        return ReferenceBuilder.create()
                .withOwner(NamedBuilder.create().withObfuscated(refInfo.getOwner()).build())
                .withName(NamedBuilder.create().withObfuscated(refInfo.getName()).build())
                .withDescriptor(NamedBuilder.create().withObfuscated(refInfo.getDesc()).build())
                .withSignature(NamedBuilder.create().withObfuscated(refInfo.getSignature()).build())
                .build();
    }
}
