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

    /**
     * Converts the given MutableRecordInfo object into a RecordMetadata object for the specified class.
     *
     * @param classInfo the MutableClassInfo object representing the class that the record belongs to
     * @param recordInfo the MutableRecordInfo object to convert
     * @return a RecordMetadata object representing the converted MutableRecordInfo object
     * @throws ReferenceConversionException if an error occurs while converting a reference in the MutableRecordInfo object
     */
    public RecordMetadata convert(final MutableClassInfo classInfo, final MutableRecordInfo recordInfo) {
        final ReferenceConverter referenceConverter = new ReferenceConverter();

        final MutableFieldInfo mutableFieldInfo = classInfo.getFields().get(recordInfo.getName());
        final MutableMethodReferenceInfo mutableMethodReferenceInfo = getGetter(mutableFieldInfo);

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

    /**
     * Returns the MutableMethodReferenceInfo object representing the getter for the given MutableFieldInfo object.
     *
     * @param fieldInfo the MutableFieldInfo object to get the getter for
     * @return the MutableMethodReferenceInfo object representing the getter for the given MutableFieldInfo object, or null if no getter is found
     */
    private static MutableMethodReferenceInfo getGetter(MutableFieldInfo fieldInfo) {
        final Iterator<MutableMethodReferenceInfo> iterator = fieldInfo.getGetters().iterator();
        MutableMethodReferenceInfo result = null;
        while (iterator.hasNext()) {
            result = iterator.next();
        }
        return result;
    }
}
