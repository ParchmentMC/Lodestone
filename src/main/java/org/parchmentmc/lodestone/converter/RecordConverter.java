package org.parchmentmc.lodestone.converter;

import org.parchmentmc.feather.metadata.RecordMetadata;
import org.parchmentmc.feather.metadata.RecordMetadataBuilder;
import org.parchmentmc.feather.metadata.ReferenceBuilder;
import org.parchmentmc.feather.named.Named;
import org.parchmentmc.feather.named.NamedBuilder;
import org.parchmentmc.lodestone.asm.MutableClassInfo;
import org.parchmentmc.lodestone.asm.MutableFieldInfo;
import org.parchmentmc.lodestone.asm.MutableMethodReferenceInfo;
import org.parchmentmc.lodestone.asm.MutableRecordInfo;

import java.util.Iterator;

public class RecordConverter {
    public RecordMetadata convert(final MutableClassInfo classInfo, final MutableRecordInfo recordInfo) {
        final ReferenceConverter referenceConverter = new ReferenceConverter();

        final MutableMethodReferenceInfo mutableMethodReferenceInfo = getGetter(recordInfo);

        final Named owner = NamedBuilder.create().withObfuscated(classInfo.getName()).build();
        return RecordMetadataBuilder.create()
                .withOwner(owner)
                .withField(
                        ReferenceBuilder.create()
                                .withOwner(owner)
                                .withName(NamedBuilder.create().withObfuscated(recordInfo.getName()).build())
                                .withDescriptor(NamedBuilder.create().withObfuscated(recordInfo.getDesc()).build())
                                .build()
                )
                .withGetter(
                        referenceConverter.convert(mutableMethodReferenceInfo)
                )
                .build();
    }

    private static MutableMethodReferenceInfo getGetter(MutableRecordInfo recordInfo) {
        final Iterator<MutableMethodReferenceInfo> iterator = recordInfo.getGetters().iterator();
        MutableMethodReferenceInfo result = null;
        while (iterator.hasNext()) {
            result = iterator.next();
        }
        return result;
    }
}
