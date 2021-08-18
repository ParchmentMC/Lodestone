package org.parchmentmc.lodestone.asm;

public class MutableBouncerInfo
{
    private final MutableMethodReferenceInfo target;
    private       MutableMethodReferenceInfo owner;

    public MutableBouncerInfo(MutableMethodReferenceInfo target)
    {
        this.target = target;
    }

    public MutableMethodReferenceInfo getTarget()
    {
        return target;
    }

    public MutableMethodReferenceInfo getOwner()
    {
        return owner;
    }

    public void setOwner(final MutableMethodReferenceInfo owner)
    {
        this.owner = owner;
    }
}
