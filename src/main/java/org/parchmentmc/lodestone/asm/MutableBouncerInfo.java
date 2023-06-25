package org.parchmentmc.lodestone.asm;

public class MutableBouncerInfo {
    /**
     * The target method reference.
     */
    private final MutableMethodReferenceInfo target;
    
    /**
     * The method super reference.
     */
    private MutableMethodReferenceInfo owner;

    /**
     * Main Constructor 
     * 
     * @param target The target method reference.
     */
    public MutableBouncerInfo(MutableMethodReferenceInfo target) {
        this.target = target;
    }

    public MutableMethodReferenceInfo getTarget() {
        return target;
    }

    public MutableMethodReferenceInfo getOwner() {
        return owner;
    }

    public void setOwner(final MutableMethodReferenceInfo owner) {
        this.owner = owner;
    }
}
