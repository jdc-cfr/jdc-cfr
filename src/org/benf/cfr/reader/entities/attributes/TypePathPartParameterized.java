package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.types.JavaAnnotatedTypeIterator;
import org.benf.cfr.reader.util.DecompilerComments;

public class TypePathPartParameterized implements TypePathPart {
    private final int index;

    public TypePathPartParameterized(int index) {
        this.index = index;
    }

    @Override
    public JavaAnnotatedTypeIterator apply(JavaAnnotatedTypeIterator it, DecompilerComments comments) {
        return it.moveParameterized(index, comments);
    }
}
