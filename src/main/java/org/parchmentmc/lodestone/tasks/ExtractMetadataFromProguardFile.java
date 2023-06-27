package org.parchmentmc.lodestone.tasks;

import org.parchmentmc.feather.io.proguard.MetadataProguardParser;
import org.parchmentmc.feather.metadata.SourceMetadata;

import java.io.File;

/**
 * The ExtractMetadataFromProguardFile task extracts metadata from a Proguard mapping file and outputs the metadata
 * as a JSON file.
 */
public abstract class ExtractMetadataFromProguardFile extends ExtractMetadataTask {

    /**
     * Constructs a new ExtractMetadataFromProguardFile task and sets the default output location for the metadata JSON file.
     */
    public ExtractMetadataFromProguardFile() {
        this.getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("proguard.json")));
    }

    /**
     * Extracts metadata from the given Proguard mapping file and returns a SourceMetadata object that represents the
     * metadata for the obfuscated code.
     *
     * @param source the Proguard mapping file to extract metadata from
     * @return a SourceMetadata object that represents the metadata for the obfuscated code
     */
    @Override
    protected SourceMetadata extractMetadata(File source) {
        return MetadataProguardParser.fromFile(source);
    }
}
