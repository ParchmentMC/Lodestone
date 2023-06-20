package org.parchmentmc.lodestone.asm;

public class MutableMethodReferenceInfo implements Comparable<MutableMethodReferenceInfo> {
    private final String owner;
    private final String name;
    private final String desc;
    private final String signature;

    MutableMethodReferenceInfo(String owner, String name, String desc, String signature) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.signature = signature;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc == null ? "" : desc;
    }

    public String getSignature() {
        return signature == null ? "" : signature;
    }

    @Override
    public String toString() {
        return this.owner + '/' + this.name + this.desc;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MutableMethodReferenceInfo && o.toString().equals(toString());
    }

    private int compare(int a, int b) {
        return a != 0 ? a : b;
    }

    @Override
    public int compareTo(MutableMethodReferenceInfo o) {
        return compare(owner.compareTo(o.owner), compare(name.compareTo(o.name), desc.compareTo(o.desc)));
    }
}
