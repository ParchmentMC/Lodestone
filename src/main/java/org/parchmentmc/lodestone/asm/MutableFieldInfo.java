package org.parchmentmc.lodestone.asm;

import org.objectweb.asm.tree.FieldNode;

import java.util.LinkedHashSet;
import java.util.Set;

public class MutableFieldInfo implements MutableSecuredObjectInfo {
    private final transient String name;
    private final transient String desc;
    private final Integer access;
    private final String signature;
    private final Set<MutableMethodReferenceInfo> getters = new LinkedHashSet<>();

    MutableFieldInfo(final MutableClassInfo classInfo, final FieldNode node) {
        this.name = node.name;
        this.desc = node.desc;
        this.access = node.access == 0 ? null : node.access;
        this.signature = node.signature;

        if (classInfo.isRecord() && !this.isStatic() && this.isFinal()) {
            classInfo.addRecord(this.name, this.desc);
        }
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc == null ? "" : desc;
    }

    public Integer getAccess() {
        return access == null ? 0 : access;
    }

    public String getSignature() {
        return signature == null ? "" : signature;
    }

    public Set<MutableMethodReferenceInfo> getGetters() {
        return getters;
    }
}
