package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;
import org.benf.cfr.reader.util.output.TypeContext;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class LocalClassAwareTypeUsageInformation implements TypeUsageInformation {
    private final TypeUsageInformation delegate;
    private final Map<JavaTypeInstance, String> localTypeNames;
    private final Set<String> usedLocalTypeNames;

    public LocalClassAwareTypeUsageInformation(Map<JavaRefTypeInstance, String> localClassTypes, TypeUsageInformation delegate) {
        this.delegate = delegate;
        Map<String, Integer> lastClassByName = MapFactory.newLazyMap(new UnaryFunction<String, Integer>() {
            @Override
            public Integer invoke(String arg) {
                return 0;
            }
        });
        localTypeNames = MapFactory.newMap();
        usedLocalTypeNames = SetFactory.newSet();
        for (Map.Entry<JavaRefTypeInstance, String> entry : localClassTypes.entrySet()) {
            JavaRefTypeInstance localType = entry.getKey();
            String suggestedName = entry.getValue();
            String usedName;
            if (suggestedName != null) {
                usedName = suggestedName;
            } else {
                String name = delegate.generateInnerClassShortName(localType);
                /*
                 * But strip all the numerics off the front.
                 */
                for (int idx = 0, len = name.length(); idx < len; ++idx) {
                    char c = name.charAt(idx);
                    if (c >= '0' && c <= '9') continue;
                    name = name.substring(idx);
                    break;
                }
                int x = lastClassByName.get(name);
                lastClassByName.put(name, x + 1);
                usedName = name + ((x == 0) ? "" : ("_" + x));
            }
            localTypeNames.put(localType, usedName);
            usedLocalTypeNames.add(usedName);
        }
    }

    @Override
    public IllegalIdentifierDump getIid() {
        return delegate.getIid();
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
    public JavaRefTypeInstance getAnalysisType() {
        return delegate.getAnalysisType();
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedClassTypes() {
        return delegate.getUsedClassTypes();
    }

    @Override
    public Set<JavaRefTypeInstance> getUsedInnerClassTypes() {
        return delegate.getUsedInnerClassTypes();
    }

    @Override
    public String getName(JavaTypeInstance type, TypeContext typeContext) {
        String local = localTypeNames.get(type);
        if (local != null) return local;

        String res = delegate.getName(type, typeContext);
        if (usedLocalTypeNames.contains(res)) {
            if (type instanceof JavaRefTypeInstance) {
                return delegate.generateOverriddenName((JavaRefTypeInstance) type);
            } else {
                return type.getRawName();
            }
        }
        return res;
    }

    @Override
    public boolean isNameClash(JavaTypeInstance type, String name, TypeContext typeContext) {
        return delegate.isNameClash(type, name, typeContext);
    }

    @Override
    public String generateInnerClassShortName(JavaRefTypeInstance clazz) {
        return delegate.generateInnerClassShortName(clazz);
    }

    @Override
    public boolean hasLocalInstance(JavaRefTypeInstance type) {
        return localTypeNames.containsKey(type);
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
