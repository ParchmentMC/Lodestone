package org.parchmentmc.lodestone.asm;

import org.objectweb.asm.Opcodes;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

public class CodeCleaner {

    private final CodeTree codeTree;

    public CodeCleaner(final CodeTree codeTree) {
        this.codeTree = codeTree;
    }

    public void cleanClass(final MutableClassInfo classMetadata) {
        doCleanClass(
                classMetadata.getName()
        );
    }

    private void doCleanClass(final String className) {
        MutableClassInfo info = codeTree.getClassMetadataFor(className);
        if (info == null || info.isResolved())
            return;

        if (info.getSuperName() != null)
            doCleanClass(info.getSuperName());

        if (info.getInterfaces() != null && !info.getInterfaces().isEmpty())
            for (String interfaceClassName : info.getInterfaces())
                doCleanClass(interfaceClassName);

        if (info.getMethods() != null && !info.getMethods().isEmpty()) {
            //Synthetic Bouncers!
            for (MutableMethodInfo method : info.getMethods().values()) {
                if (method.getBouncer() != null) {
                    MutableMethodReferenceInfo owner = doWalkBouncers(method, info.getName());
                    if (owner != null && !owner.getOwner().equals(info.getName()))
                        method.getBouncer().setOwner(owner);
                }
            }

            //Resolve the 'root' owner of each method.
            for (MutableMethodInfo method : info.getMethods().values()) {
                method.setOverrides(doFindOverrides(method, info.getName(), new TreeSet<>()));
                method.setParent(doFindFirstOverride(method, info.getName()));
            }
        }

        if (!info.isAbstract()) {
            resolveAbstract(info);
        }

        resolveRecord(info);

        info.setResolved(true);
    }

    private MutableMethodReferenceInfo doWalkBouncers(final MutableMethodInfo methodMetadata, String className) {
        final MutableClassInfo classMetadata = codeTree.getClassMetadataFor(className);
        if (!classMetadata.getMethods().isEmpty()) {
            MutableMethodInfo ownerMethodMetadata = classMetadata.getMethods().get(methodMetadata.getMethod().getName() + methodMetadata.getMethod().getDesc());

            if (ownerMethodMetadata != null && (
                    (!ownerMethodMetadata.isFinal() && !ownerMethodMetadata.isPrivate()) ||
                            //We ignore all final or private methods, they can not be overridden so no bouncer.
                            classMetadata.getName()
                                    .equals(methodMetadata.getMethod().getOwner()) //We always execute this branch if the current method is owned by the current class (first level recursion).
            )) {
                if (ownerMethodMetadata.getBouncer() != null) {
                    final Set<MutableMethodReferenceInfo> overrides = findOverrides(
                            ownerMethodMetadata,
                            classMetadata.getName()
                    );
                    if (overrides.isEmpty()) {
                        return new MutableMethodReferenceInfo(
                                classMetadata.getName(),
                                ownerMethodMetadata.getMethod().getName(),
                                ownerMethodMetadata.getMethod().getDesc(),
                                ownerMethodMetadata.getSignature()
                        );
                    } else {
                        return overrides.iterator().next();
                        //We pick the first regardless of how many there are in there.
                        //Most likely it is the one from the super class, but it is actually not that relevant.
                    }
                }

                for (final MutableMethodInfo ownerMethod : classMetadata.getMethods().values()) {
                    final MutableMethodReferenceInfo target = ownerMethod.getBouncer() != null ? ownerMethod.getBouncer().getTarget() : null;
                    if (
                            target != null &&
                                    ownerMethodMetadata.getMethod().getName().equals(target.getName()) &&
                                    ownerMethodMetadata.getMethod().getDesc().equals(target.getDesc())
                    ) {
                        if (ownerMethod.getBouncer().getOwner() != null) {
                            return ownerMethod.getBouncer().getOwner();
                        }

                        final MutableMethodReferenceInfo newBouncerOwner = doWalkBouncers(
                                ownerMethod,
                                classMetadata.getName()
                        );

                        if (newBouncerOwner != null && !newBouncerOwner.getOwner().equals(classMetadata.getName())) {
                            ownerMethod.getBouncer().setOwner(newBouncerOwner);
                            return newBouncerOwner;
                        }
                    }
                }
            }
        }

        if (classMetadata.getSuperName() != null) {
            MutableMethodReferenceInfo ret = doWalkBouncers(methodMetadata, classMetadata.getSuperName());
            if (ret != null)
                return ret;
        }

        if (classMetadata.getInterfaces() != null && !classMetadata.getInterfaces().isEmpty()) {
            for (String interfaceName : classMetadata.getInterfaces()) {
                MutableMethodReferenceInfo ret = doWalkBouncers(methodMetadata, interfaceName);
                if (ret != null)
                    return ret;
            }
        }

        return null;
    }

    private Set<MutableMethodReferenceInfo> findOverrides(MutableMethodInfo methodMetadata, String ownerName) {
        return doFindOverrides(methodMetadata, ownerName, new LinkedHashSet<>());
    }

    private Set<MutableMethodReferenceInfo> doFindOverrides(MutableMethodInfo methodMetadata, String className, Set<MutableMethodReferenceInfo> overrides) {
        if (methodMetadata.isStatic() || methodMetadata.isPrivate() || methodMetadata.getMethod().getName().startsWith("<")) {
            return overrides;
        }

        final MutableClassInfo classMetadata = codeTree.getClassMetadataFor(className);
        if (classMetadata == null) {
            return overrides;
        }

        if (classMetadata.getMethods() != null && !classMetadata.getMethods().isEmpty()) {
            for (MutableMethodInfo ownerMethodMetadata : classMetadata.getMethods().values()) {
                final MutableMethodReferenceInfo target = ownerMethodMetadata.getBouncer() != null ? ownerMethodMetadata.getBouncer().getTarget() : null;
                if (target != null &&
                        methodMetadata.getMethod().getName().equals(target.getName()) &&
                        methodMetadata.getMethod().getDesc().equals(target.getDesc())) {
                    doFindOverrides(ownerMethodMetadata, classMetadata.getName(), overrides);
                }
            }

            MutableMethodInfo ownerMethodMetadata = classMetadata.getMethods().get(methodMetadata.getMethod().getName() + methodMetadata.getMethod().getDesc());
            if (ownerMethodMetadata != null && ownerMethodMetadata != methodMetadata && (
                    !ownerMethodMetadata.isFinal() && !ownerMethodMetadata.isPrivate()
            )) {
                if (ownerMethodMetadata.getOverrides().isEmpty()) {
                    overrides.add(
                            new MutableMethodReferenceInfo(
                                    classMetadata.getName(),
                                    ownerMethodMetadata.getMethod().getName(),
                                    ownerMethodMetadata.getMethod().getDesc(),
                                    ownerMethodMetadata.getSignature()
                            ));
                } else {
                    overrides.addAll(ownerMethodMetadata.getOverrides());
                }
            }
        }

        if (classMetadata.getSuperName() != null) {
            doFindOverrides(methodMetadata, classMetadata.getSuperName(), overrides);
        }

        if (classMetadata.getInterfaces() != null && !classMetadata.getInterfaces().isEmpty()) {
            for (final String interfaceName : classMetadata.getInterfaces()) {
                doFindOverrides(methodMetadata, interfaceName, overrides);
            }
        }

        return overrides;
    }

    private MutableMethodReferenceInfo doFindFirstOverride(MutableMethodInfo mtd, String owner) {
        if (mtd.isStatic() || mtd.isPrivate() || mtd.getMethod().getName().startsWith("<"))
            return null;

        MutableClassInfo ownerInfo = codeTree.getClassMetadataFor(owner);

        if (ownerInfo == null)
            return null;

        if (ownerInfo.getMethods() != null && !ownerInfo.getMethods().isEmpty()) {
            MutableMethodInfo methodInOwner = ownerInfo.getMethods().get(mtd.getMethod().getName() + mtd.getMethod().getDesc());
            if (codeTree.isGameClass(ownerInfo.getName()) && methodInOwner != null && methodInOwner != mtd && (methodInOwner.getAccess() & (Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE)) == 0)
                return new MutableMethodReferenceInfo(
                        ownerInfo.getName(),
                        methodInOwner.getMethod().getName(),
                        methodInOwner.getMethod().getDesc(),
                        methodInOwner.getSignature()
                );

            for (MutableMethodInfo m : ownerInfo.getMethods().values()) {
                MutableMethodReferenceInfo target = m.getBouncer() == null ? null : m.getBouncer().getTarget();
                if (target != null && mtd.getMethod().getName().equals(target.getName())
                        && mtd.getMethod().getDesc().equals(target.getDesc())) {
                    MutableMethodReferenceInfo ret = doFindFirstOverride(m, ownerInfo.getName());
                    if (ret != null)
                        return ret;
                }
            }
        }

        if (ownerInfo.getSuperName() != null) {
            MutableMethodReferenceInfo ret = doFindFirstOverride(mtd, ownerInfo.getSuperName());
            if (ret != null)
                return ret;
        }

        if (ownerInfo.getInterfaces() != null && !ownerInfo.getInterfaces().isEmpty()) {
            for (String interfaceName : ownerInfo.getInterfaces()) {
                MutableMethodReferenceInfo ret = doFindFirstOverride(mtd, interfaceName);
                if (ret != null)
                    return ret;
            }
        }

        return null;
    }

    private void resolveAbstract(MutableClassInfo cls) {
        Map<String, String> abs = new HashMap<>();
        Set<String> known = new TreeSet<>();
        Queue<String> que = new LinkedList<>();
        Consumer<String> add = c -> {
            if (!known.contains(c)) {
                que.add(c);
                known.add(c);
            }
        };

        add.accept(cls.getName());

        while (!que.isEmpty()) {
            MutableClassInfo info = codeTree.getClassMetadataFor(que.poll());
            if (info == null)
                continue;

            if (info.getMethods() != null)
                info.getMethods().values().stream()
                        .filter(MutableMethodInfo::isAbstract)
                        .filter(mtd -> mtd.getOverrides() == null || mtd.getOverrides().isEmpty()) //We only want the roots
                        .forEach(mtd -> abs.put(mtd.getMethod().getName() + mtd.getMethod().getDesc(), info.getName()));

            if (info.getSuperName() != null)
                add.accept(info.getSuperName());

            if (info.getInterfaces() != null)
                info.getInterfaces().forEach(add);
        }

        known.clear();
        add.accept(cls.getName());

        while (!que.isEmpty()) {
            MutableClassInfo info = codeTree.getClassMetadataFor(que.poll());
            if (info == null)
                continue;

            if (info.getMethods() != null && !info.getMethods().isEmpty()) {
                for (MutableMethodInfo mtd : info.getMethods().values()) {
                    if (mtd.isAbstract())
                        continue;

                    String towner = abs.remove(mtd.getMethod().getName() + mtd.getMethod().getDesc());
                    if (towner == null)
                        continue;
                    MutableMethodReferenceInfo target = new MutableMethodReferenceInfo(
                            towner,
                            mtd.getMethod().getName(),
                            mtd.getMethod().getDesc(),
                            mtd.getSignature()
                    );

                    if (mtd.getOverrides() != null) {
                        mtd.getOverrides().add(target);
                    } else {
                        mtd.setOverrides(new HashSet<>(Collections.singletonList(target)));
                    }
                }
            }

            if (info.getSuperName() != null)
                add.accept(info.getSuperName());

            if (info.getInterfaces() != null && !info.getInterfaces().isEmpty())
                info.getInterfaces().forEach(add);
        }
    }

    private void resolveRecord(MutableClassInfo mutableClassInfo) {
        if (!mutableClassInfo.isRecord() || mutableClassInfo.getRecords().isEmpty() || mutableClassInfo.getFields().isEmpty())
            return;

        for (MutableRecordInfo mutableRecordInfo : mutableClassInfo.getRecords().values()) {
            MutableFieldInfo mutableFieldInfo = mutableClassInfo.getFields().get(mutableRecordInfo.getName());
            if (mutableFieldInfo != null && !mutableFieldInfo.getGetters().isEmpty()) {
                mutableRecordInfo.getGetters().addAll(
                        mutableFieldInfo.getGetters()
                );
            }
        }
    }

}
