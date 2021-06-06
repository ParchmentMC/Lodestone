package org.parchmentmc.lodestone.util;

import org.objectweb.asm.commons.Remapper;

import java.util.Map;

public class ASMRemapper extends Remapper
{

    private final Map<String, String> classRenames;
    private final Map<String, String> methodRenames;

    public ASMRemapper(final Map<String, String> classRenames, final Map<String, String> methodRenames) {
        this.classRenames = classRenames;
        this.methodRenames = methodRenames;
    }

    @Override
    public String mapMethodName(final String owner, final String name, final String descriptor) {
        final String methodKey = String.format("%s/%s%s",
          owner,
          name,
          descriptor);

        return methodRenames.getOrDefault(methodKey, name);
    }

    @Override
    public String map(final String key) {
        return classRenames.getOrDefault(key, key);
    }
}
