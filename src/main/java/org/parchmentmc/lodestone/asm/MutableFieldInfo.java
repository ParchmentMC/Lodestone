package org.parchmentmc.lodestone.asm;

import org.objectweb.asm.tree.FieldNode;

public class MutableFieldInfo implements MutableSecuredObjectInfo
{
    private final transient String  name;
    private final transient String  desc;
    private final           Integer access;
    private final           String  signature;

    MutableFieldInfo(FieldNode node)
    {
        this.name = node.name;
        this.desc = node.desc;
        this.access = node.access == 0 ? null : node.access;
        this.signature = node.signature;
    }

    public String getName()
    {
        return name;
    }

    public String getDesc()
    {
        return desc == null ? "" : desc;
    }

    public Integer getAccess()
    {
        return access == null ? 0 : access;
    }

    public String getSignature()
    {
        return signature == null ? "" : signature;
    }
}
