package org.parchmentmc.lodestone.asm;

import org.objectweb.asm.Opcodes;

public interface MutableSecuredObjectInfo {
    Integer getAccess();

    default boolean isInterface() {
        return ((getAccess() & Opcodes.ACC_INTERFACE) != 0);
    }

    default boolean isAbstract() {
        return ((getAccess() & Opcodes.ACC_ABSTRACT) != 0);
    }

    default boolean isSynthetic() {
        return ((getAccess() & Opcodes.ACC_SYNTHETIC) != 0);
    }

    default boolean isAnnotation() {
        return ((getAccess() & Opcodes.ACC_ANNOTATION) != 0);
    }

    default boolean isEnum() {
        return ((getAccess() & Opcodes.ACC_ENUM) != 0);
    }

    default boolean isPackagePrivate() {
        return (getAccess() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) == 0;
    }

    default boolean isPublic() {
        return (getAccess() & Opcodes.ACC_PUBLIC) != 0;
    }

    default boolean isPrivate() {
        return (getAccess() & Opcodes.ACC_PRIVATE) != 0;
    }

    default boolean isProtected() {
        return (getAccess() & Opcodes.ACC_PROTECTED) != 0;
    }

    default boolean isStatic() {
        return (getAccess() & Opcodes.ACC_STATIC) != 0;
    }

    default boolean isFinal() {
        return (getAccess() & Opcodes.ACC_FINAL) != 0;
    }
}
