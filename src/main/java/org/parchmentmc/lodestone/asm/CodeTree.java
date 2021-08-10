package org.parchmentmc.lodestone.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CodeTree
{
    private final Set<String>         noneLibraryClasses = new HashSet<>();
    private final Map<String, byte[]> sources            = new HashMap<>();

    private final Map<String, MutableClassInfo> parsedClasses = new HashMap<>();

    public Set<String> getNoneLibraryClasses()
    {
        return noneLibraryClasses;
    }

    public final void load(final Path path, final boolean library) throws IOException {
        try(ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(path)))
        {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class"))
                    continue;

                String cls = name.substring(0, name.length() - 6);
                if (!sources.containsKey(cls)) {
                    byte[] data = readStreamFully(zipInputStream);
                    sources.put(cls, data);
                    if (!library)
                        noneLibraryClasses.add(cls);
                }
            }
        }
    }

    public MutableClassInfo getClassMetadataFor(String cls) {
        MutableClassInfo classMetadata = parsedClasses.get(cls);
        if (classMetadata == null) {
            byte[] data = sources.remove(cls);
            if (data == null) {
                return null;
            }
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(data);
            classReader.accept(classNode, 0);

            classMetadata = buildClass(classNode);

            parsedClasses.put(cls, classMetadata);
        }
        return classMetadata;
    }

    public boolean isGameClass(final String cls) {
        return noneLibraryClasses.contains(cls);
    }

    private MutableClassInfo buildClass(final ClassNode classNode) {
        return new MutableClassInfo(classNode);
    }

    private static byte[] readStreamFully(InputStream is) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(Math.max(8192, is.available()));
        byte[] buffer = new byte[8192];
        int read;
        while((read = is.read(buffer)) >= 0) {
            byteArrayOutputStream.write(buffer, 0, read);
        }
        return byteArrayOutputStream.toByteArray();
    }
}
