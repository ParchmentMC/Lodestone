package org.parchmentmc.lodestone.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Collections;
import java.util.Set;

public class MutableMethodInfo implements MutableSecuredObjectInfo
{
    private final           MutableClassInfo        mutableClassInfo;
    private final boolean                 isLambda;
    private final MutableMethodReferenceInfo      method;
    private final           Integer                 access;
    private final           String                  signature;
    private final           MutableBouncerInfo      bouncer;
    private                 String                  force;
    private                 Set<MutableMethodReferenceInfo> overrides;
    private                 MutableMethodReferenceInfo      parent;

    MutableMethodInfo(final MutableClassInfo mutableClassInfo, MethodNode node, boolean lambda)
    {
        this.mutableClassInfo = mutableClassInfo;
        this.method = new MutableMethodReferenceInfo(mutableClassInfo.getName(), node.name, node.desc, node.signature);
        this.access = node.access == 0 ? null : node.access;
        this.signature = node.signature;
        this.isLambda = lambda;

        MutableBouncerInfo bounce = null;
        if (!lambda && (node.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 && (node.access & Opcodes.ACC_STATIC) == 0)
        {
            AbstractInsnNode start = node.instructions.getFirst();
            if (start instanceof LabelNode && start.getNext() instanceof LineNumberNode)
            {
                start = start.getNext().getNext();
            }

            if (start instanceof VarInsnNode)
            {
                VarInsnNode n = (VarInsnNode) start;
                if (n.var == 0 && n.getOpcode() == Opcodes.ALOAD)
                {
                    AbstractInsnNode end = node.instructions.getLast();
                    if (end instanceof LabelNode)
                    {
                        end = end.getPrevious();
                    }

                    if (end.getOpcode() >= Opcodes.IRETURN && end.getOpcode() <= Opcodes.RETURN)
                    {
                        end = end.getPrevious();
                    }

                    if (end instanceof MethodInsnNode)
                    {
                        Type[] args = Type.getArgumentTypes(node.desc);
                        int var = 1;
                        int index = 0;
                        start = start.getNext();
                        while (start != end)
                        {
                            if (start instanceof VarInsnNode)
                            {
                                if (((VarInsnNode) start).var != var || index + 1 > args.length)
                                {
                                    //Arguments are switched around, so seems like lambda!
                                    end = null;
                                    break;
                                }
                                var += args[index++].getSize();
                            }
                            else //noinspection StatementWithEmptyBody -> we want to break if we hit something else
                                if (start.getOpcode() == Opcodes.INSTANCEOF || start.getOpcode() == Opcodes.CHECKCAST)
                            {
                                //Valid!
                            }
                            else
                            {
                                // Anything else is invalid in a bouncer {As far as I know}, so we're most likely a lambda
                                end = null;
                                break;
                            }
                            start = start.getNext();
                        }

                        MethodInsnNode mtd = (MethodInsnNode) end;
                        if (end != null && mtd.owner.equals(mutableClassInfo.getName())
                              && Type.getArgumentsAndReturnSizes(node.desc) == Type.getArgumentsAndReturnSizes(mtd.desc))
                        {
                            bounce = new MutableBouncerInfo(new MutableMethodReferenceInfo(mtd.owner, mtd.name, mtd.desc, null));
                        }
                    }
                }
            }
        }
        this.bouncer = bounce;

        //Check if we are a getter!.
        //Required to link record fields.
        if (mutableClassInfo.isRecord() && !this.isStatic() && this.method.getDesc().contains("()") && mutableClassInfo.getFields() != null) {
            AbstractInsnNode start = node.instructions.getFirst();
            if (start instanceof LabelNode && start.getNext() instanceof LineNumberNode)
                start = start.getNext().getNext();

            if (start instanceof VarInsnNode) {
                VarInsnNode instructionNode  = (VarInsnNode)start;
                if (instructionNode.var == 0 && instructionNode.getOpcode() == Opcodes.ALOAD) {
                    if (start.getNext() instanceof FieldInsnNode) {
                        FieldInsnNode fieldInstruction = (FieldInsnNode)start.getNext();
                        if (fieldInstruction.owner.equals(mutableClassInfo.getName()) && fieldInstruction.getNext() != null) {
                            AbstractInsnNode ret = fieldInstruction.getNext();
                            if (ret.getOpcode() >= Opcodes.IRETURN && ret.getOpcode() <= Opcodes.RETURN) {
                                MutableFieldInfo returnedFieldInfo = mutableClassInfo.getFields().get(fieldInstruction.name);
                                if (returnedFieldInfo != null) {
                                    returnedFieldInfo.getGetters().add(getMethod());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public MutableClassInfo getMutableClassInfo()
    {
        return mutableClassInfo;
    }

    public boolean isLambda()
    {
        return isLambda;
    }

    public MutableMethodReferenceInfo getMethod()
    {
        return method;
    }

    public Integer getAccess()
    {
        return access == null ? 0 : access;
    }

    public String getSignature()
    {
        return signature == null ? "" : signature;
    }

    public MutableBouncerInfo getBouncer()
    {
        return bouncer;
    }

    public String getForce()
    {
        return force;
    }

    public void setForce(final String force)
    {
        this.force = force;
    }

    public Set<MutableMethodReferenceInfo> getOverrides()
    {
        return overrides;
    }

    public void setOverrides(final Set<MutableMethodReferenceInfo> overrides)
    {
        this.overrides = overrides;
    }

    public MutableMethodReferenceInfo getParent()
    {
        return parent;
    }

    public void setParent(final MutableMethodReferenceInfo parent)
    {
        this.parent = parent;
    }
}
