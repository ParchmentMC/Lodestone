package org.parchmentmc.lodestone.asm;

import java.util.HashSet;
import java.util.Set;

public class MutableRecordInfo
{
    private final String                          name;
    private final String                          desc;
    private final Set<MutableMethodReferenceInfo> getters = new HashSet<>();

    public MutableRecordInfo(final String name, final String desc) {
        this.name = name;
        this.desc = desc;
    }

    public String getName()
    {
        return name;
    }

    public String getDesc()
    {
        return desc;
    }

    public Set<MutableMethodReferenceInfo> getGetters()
    {
        return getters;
    }
}
