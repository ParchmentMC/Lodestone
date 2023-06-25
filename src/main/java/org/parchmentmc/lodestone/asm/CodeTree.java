package org.parchmentmc.lodestone.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CodeTree {
    
    private final Set<String> noneLibraryClasses = new LinkedHashSet<>();
    private final Map<String, byte[]> sources = new HashMap<>();

    /**
     * A map consisting of the class string identifier as the key and the Mutable class metadata as the value.
     */
    private final Map<String, MutableClassInfo> parsedClasses = new HashMap<>();

    public Set<String> getNoneLibraryClasses() {
        return noneLibraryClasses;
    }

    /**
     * METHOD EXPLAINATION GOES HERE
     * 
     * @param path The file path to the file being loaded.
     * @param library If the loaded file is a library or not.
     * @throws IOException Throws an IOException if it couldn't read the file using the ZipInputStream.
     */
    public final void load(final Path path, final boolean library) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(path))) {
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

    /**
     * METHOD EXPLAINATION GOES HERE
     * 
     * @param cls The class identifier name.
     * @return Returns the mutable metadata for the class.
     */
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

    /**
     * METHOD EXPLAINATION GOES HERE
     * 
     * @param is The input stream thats being read.
     * @return Returns the read file in the form of a byte array of data.
     * @throws IOException Throws an IOException if the data cannot be read.
     */
    private static byte[] readStreamFully(InputStream is) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(Math.max(8192, is.available()));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) >= 0) {
            byteArrayOutputStream.write(buffer, 0, read);
        }
        return byteArrayOutputStream.toByteArray();
    }
}
