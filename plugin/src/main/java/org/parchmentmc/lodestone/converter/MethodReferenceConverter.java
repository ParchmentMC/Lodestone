package org.parchmentmc.lodestone.converter;

import org.parchmentmc.feather.metadata.MethodReference;
import org.parchmentmc.feather.metadata.MethodReferenceBuilder;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.lodestone.asm.MutableMethodReferenceInfo;

public class MethodReferenceConverter
{
    public MethodReference convert(final MutableMethodReferenceInfo refInfo)
    {
        if (refInfo == null)
            return null;

        return MethodReferenceBuilder.create()
          .withOwner(NamedBuilder.create().withObfuscated(refInfo.getOwner()).build())
          .withName(NamedBuilder.create().withObfuscated(refInfo.getName()).build())
          .withDescriptor(NamedBuilder.create().withObfuscated(refInfo.getDesc()).build())
          .withSignature(NamedBuilder.create().withObfuscated(refInfo.getSignature()).build())
          .build();
    }
}
