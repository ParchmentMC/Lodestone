package org.parchmentmc.lodestone.tasks;

import org.parchmentmc.feather.io.proguard.MetadataProguardParser;
import org.parchmentmc.feather.metadata.SourceMetadata;

import java.io.File;

public abstract class ExtractMetadataFromProguardFile extends ExtractMetadataTask {
    public ExtractMetadataFromProguardFile() {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("proguard.json")));
    }

    @Override
    protected SourceMetadata extractMetadata(File source) {
        return MetadataProguardParser.fromFile(source);
    }
}
