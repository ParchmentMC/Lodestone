package org.parchmentmc.lodestone.asm;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class MutableClassInfo implements MutableSecuredObjectInfo
{
    private static final Handle LAMBDA_METAFACTORY    = new Handle(Opcodes.H_INVOKESTATIC,
      "java/lang/invoke/LambdaMetafactory",
      "metafactory",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
      false);
    private static final Handle LAMBDA_ALTMETAFACTORY = new Handle(Opcodes.H_INVOKESTATIC,
      "java/lang/invoke/LambdaMetafactory",
      "altMetafactory",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
      false);

    private final String                         name;
    private final String                         superName;
    private final List<String>                   interfaces;
    private final Integer                        access;
    private final String                         signature;
    private final Map<String, MutableFieldInfo>  fields;
    private final Map<String, MutableMethodInfo> methods;
    private final Map<String, MutableRecordInfo> records;
    private final boolean                        isRecord;
    private       boolean                        resolved = false;

    MutableClassInfo(ClassNode node)
    {
        this.name = node.name;
        this.superName = "java/lang/Object".equals(node.superName) ? null : node.superName;
        this.isRecord = "java/lang/Record".equals(node.superName);
        this.interfaces = node.interfaces != null && !node.interfaces.isEmpty() ? new ArrayList<>(node.interfaces) : null;
        this.access = node.access == 0 ? null : node.access;
        this.signature = node.signature;

        this.records = new TreeMap<>();
        if (node.fields == null || node.fields.isEmpty())
        {
            this.fields = null;
        }
        else
        {
            this.fields = new TreeMap<>();
            node.fields.forEach(fld -> this.fields.put(fld.name, new MutableFieldInfo(this, fld)));
        }

        if (node.methods == null || node.methods.isEmpty())
        {
            this.methods = null;
        }
        else
        {
            //Gather Lambda methods so we can skip them in bouncers?
            Set<String> lambdas = new HashSet<>();
            for (MethodNode mtd : node.methods)
            {
                for (AbstractInsnNode asn : (Iterable<AbstractInsnNode>) () -> mtd.instructions.iterator())
                {
                    if (asn instanceof InvokeDynamicInsnNode)
                    {
                        Handle target = getLambdaTarget((InvokeDynamicInsnNode) asn);
                        if (target != null)
                        {
                            lambdas.add(target.getOwner() + '/' + target.getName() + target.getDesc());
                        }
                    }
                }
            }

            this.methods = new TreeMap<>();
            for (MethodNode mtd : node.methods)
            {
                String key = mtd.name + mtd.desc;
                this.methods.put(key, new MutableMethodInfo(this, mtd, lambdas.contains(this.name + '/' + key)));
            }
        }
    }

    private Handle getLambdaTarget(InvokeDynamicInsnNode idn)
    {
        if (LAMBDA_METAFACTORY.equals(idn.bsm) && idn.bsmArgs != null && idn.bsmArgs.length == 3 && idn.bsmArgs[1] instanceof Handle)
        {
            return ((Handle) idn.bsmArgs[1]);
        }
        if (LAMBDA_ALTMETAFACTORY.equals(idn.bsm) && idn.bsmArgs != null && idn.bsmArgs.length == 5 && idn.bsmArgs[1] instanceof Handle)
        {
            return ((Handle) idn.bsmArgs[1]);
        }
        return null;
    }

    public String getName()
    {
        return name;
    }

    public String getSuperName()
    {
        return superName == null ? "" : superName;
    }

    public List<String> getInterfaces()
    {
        return interfaces == null ? Collections.emptyList() : interfaces;
    }

    public Integer getAccess()
    {
        return access == null ? 0 : access;
    }

    public String getSignature()
    {
        return signature == null ? "" : signature;
    }

    public Map<String, MutableFieldInfo> getFields()
    {
        return fields == null ? Collections.emptyMap() : fields;
    }

    public Map<String, MutableMethodInfo> getMethods()
    {
        return methods == null ? Collections.emptyMap() : methods;
    }

    public boolean isRecord()
    {
        return isRecord;
    }

    public boolean isResolved()
    {
        return resolved;
    }

    public void setResolved(final boolean resolved)
    {
        this.resolved = resolved;
    }

    public void addRecord(final String name, final String desc)
    {
        this.records.put(name, new MutableRecordInfo(name, desc));
    }

    public Map<String, MutableRecordInfo> getRecords()
    {
        return records;
    }
}
