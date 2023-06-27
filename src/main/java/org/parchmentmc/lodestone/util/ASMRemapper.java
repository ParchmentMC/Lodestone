package org.parchmentmc.lodestone.util;

import org.objectweb.asm.commons.Remapper;

import java.util.Map;

/**
 * ASMRemapper provides mapping functionality for class and method names.
 * It allows renaming classes and methods based on the provided mappings.
 */
public class ASMRemapper extends Remapper {

    private final Map<String, String> classRenames;
    private final Map<String, String> methodRenames;

    /**
     * Constructs a new ASMRemapper with the specified class and method rename mappings.
     *
     * @param classRenames  A map of class rename mappings.
     * @param methodRenames A map of method rename mappings.
     */
    public ASMRemapper(final Map<String, String> classRenames, final Map<String, String> methodRenames) {
        this.classRenames = classRenames;
        this.methodRenames = methodRenames;
    }

    /**
     * Maps the given method name based on the owner, name, and descriptor.
     *
     * @param owner      The internal name of the owning class.
     * @param name       The original name of the method.
     * @param descriptor The method descriptor.
     * @return The mapped method name, or the original name if no mapping is found.
     */
    @Override
    public String mapMethodName(final String owner, final String name, final String descriptor) {
        final String methodKey = String.format("%s/%s%s",
                owner,
                name,
                descriptor);

        return methodRenames.getOrDefault(methodKey, name);
    }

    /**
     * Maps the given key, which can be a class name or any other identifier.
     *
     * @param key The key to be mapped.
     * @return The mapped key, or the original key if no mapping is found.
     */
    @Override
    public String map(final String key) {
        return classRenames.getOrDefault(key, key);
    }
}
