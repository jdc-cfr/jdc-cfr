package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.TypeContext;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Strips the outer class name off anything which preceeds this inner class.
 */
public class InnerClassTypeUsageInformation implements TypeUsageInformation {
    private final IllegalIdentifierDump iid;
    private final TypeUsageInformation delegate;
    private final JavaRefTypeInstance analysisInnerClass;
    private final Map<JavaRefTypeInstance, String> localTypeNames = MapFactory.newMap();
    private final Set<String> usedLocalTypeNames = SetFactory.newSet();
    private final Set<JavaRefTypeInstance> usedInnerClassTypes = SetFactory.newSet();

    public InnerClassTypeUsageInformation(TypeUsageInformation delegate, JavaRefTypeInstance analysisInnerClass) {
        this.delegate = delegate;
        this.analysisInnerClass = analysisInnerClass;
        this.iid = delegate.getIid();
        initializeFrom();
    }

    private boolean clashesWithField(String name) {
        JavaRefTypeInstance type = analysisInnerClass;
        if (type == null) return false;
        ClassFile classFile = type.getClassFile();
        if (classFile == null) return false;
        if (classFile.hasLocalField(name)) return true;
        return false;
    }

    @Override
    public IllegalIdentifierDump getIid() {
        return iid;
    }

    @Override
    public JavaRefTypeInstance getAnalysisType() {
        return delegate.getAnalysisType();
    }

    private void initializeFrom() {
        Set<JavaRefTypeInstance> outerInners = delegate.getUsedInnerClassTypes();
        for (JavaRefTypeInstance outerInner : outerInners) {
            if (outerInner.getInnerClassHereInfo().isTransitiveInnerClassOf(analysisInnerClass)) {
                usedInnerClassTypes.add(outerInner);
                String name = TypeUsageUtils.generateInnerClassShortName(iid, outerInner, analysisInnerClass, false);
                if (!usedLocalTypeNames.contains(name)) {
                    localTypeNames.put(outerInner, name);
                    usedLocalTypeNames.add(name);
                }
            }
        }
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedClassTypes() {
        return delegate.getUsedClassTypes();
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedInnerClassTypes() {
        return usedInnerClassTypes;
    }

    @Override
    public boolean hasLocalInstance(JavaRefTypeInstance type) {
        return localTypeNames.get(type) != null;
    }

    @Override
    public String getName(JavaTypeInstance type, TypeContext typeContext) {
        //noinspection SuspiciousMethodCalls - if it fails it fails....
        String local = localTypeNames.get(type);
        if (local != null) {
            return local;
        }

        String res = delegate.getName(type, typeContext);
        if (usedLocalTypeNames.contains(res)) {
            return type.getRawName(iid);
        }
        if (isNameClash(type, res, typeContext)) {
            return type.getRawName(iid);
        }
        return res;
    }

    @Override
    public boolean isNameClash(JavaTypeInstance type, String name, TypeContext typeContext) {
        if (typeContext == TypeContext.Static && (clashesWithField(name) || delegate.isNameClash(type, name, typeContext))) {
            return true;
        }
        return false;
    }

    @Override
    public String generateInnerClassShortName(JavaRefTypeInstance clazz) {
        return delegate.generateInnerClassShortName(clazz);
    }

    @Override
    public boolean isStaticImport(JavaTypeInstance clazz, String fixedName) {
        return false;
    }

    @Override
    public Set<DetectedStaticImport> getDetectedStaticImports() {
        return Collections.emptySet();
    }

    @Override
    public String generateOverriddenName(JavaRefTypeInstance clazz) {
        return delegate.generateOverriddenName(clazz);
    }

    @Override
    public Set<JavaRefTypeInstance> getShortenedClassTypes() {
        return delegate.getShortenedClassTypes();
    }
}
