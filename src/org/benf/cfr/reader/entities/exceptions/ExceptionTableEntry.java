package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.types.BindingSuperContainer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.functors.UnaryFunction;

public class ExceptionTableEntry implements Comparable<ExceptionTableEntry> {
    private static final int OFFSET_INDEX_FROM = 0;
    private static final int OFFSET_INDEX_TO = 2;
    private static final int OFFSET_INDEX_HANDLER = 4;
    private static final int OFFSET_CATCH_TYPE = 6;

    private final int bytecode_index_from;        // [ a
    private final int bytecode_index_to;          // ) b    st a <= x < b
    private final int bytecode_index_handler;
    private final int catch_type;

    private final int priority;

    private ExceptionTableEntry(ByteData raw, int priority) {
        this(
                raw.getU2At(OFFSET_INDEX_FROM),
                raw.getU2At(OFFSET_INDEX_TO),
                raw.getU2At(OFFSET_INDEX_HANDLER),
                raw.getU2At(OFFSET_CATCH_TYPE),
                priority);
    }

    ExceptionTableEntry(int from, int to, int handler, int catchType, int priority) {
        this.bytecode_index_from = from;
        this.bytecode_index_to = to;
        this.bytecode_index_handler = handler;
        this.catch_type = catchType;
        this.priority = priority;
        if (to < from) {
            throw new IllegalStateException("Malformed exception block, to < from");
        }
    }

    // TODO : Refactor into constructor.
    JavaRefTypeInstance getCatchType(ConstantPool cp) {
        if (catch_type == 0) {
            return cp.getClassCache().getRefClassFor(TypeConstants.throwableName);
        } else {
            JavaRefTypeInstance refTypeInstance = (JavaRefTypeInstance) cp.getClassEntry(catch_type).getTypeInstance();
            // It's possible we won't be able to load the type.
            // If so, we at least know it's throwable.
            if (refTypeInstance.getBindingSupers() == null) {
                BindingSuperContainer bsc = BindingSuperContainer.unknownThrowable(refTypeInstance);
                refTypeInstance.forceBindingSupers(bsc);
            }
            return refTypeInstance;
        }
    }

    ExceptionTableEntry copyWithRange(int from, int to) {
        return new ExceptionTableEntry(from, to, this.bytecode_index_handler, this.catch_type, this.priority);
    }

    int getBytecodeIndexFrom() {
        return bytecode_index_from;
    }

    int getBytecodeIndexTo() {
        return bytecode_index_to;
    }

    int getBytecodeIndexHandler() {
        return bytecode_index_handler;
    }

    int getCatchType() {
        return catch_type;
    }

    int getPriority() {
        return priority;
    }

    ExceptionTableEntry aggregateWith(ExceptionTableEntry later) {
        if ((this.bytecode_index_from >= later.bytecode_index_from) ||
                (this.bytecode_index_to != later.bytecode_index_from)) {
            throw new ConfusedCFRException("Can't aggregate exceptionTableEntries");
        }
        // TODO : Priority is not quite right here.
        return new ExceptionTableEntry(this.bytecode_index_from, later.bytecode_index_to, this.bytecode_index_handler, this.catch_type, this.priority);
    }

    ExceptionTableEntry aggregateWithLenient(ExceptionTableEntry later) {
        if (this.bytecode_index_from >= later.bytecode_index_from) {
            throw new ConfusedCFRException("Can't aggregate exceptionTableEntries");
        }
        // TODO : Priority is not quite right here.
        return new ExceptionTableEntry(this.bytecode_index_from, later.bytecode_index_to, this.bytecode_index_handler, this.catch_type, this.priority);
    }

    public static UnaryFunction<ByteData, ExceptionTableEntry> getBuilder() {
        return new ExceptionTableEntryBuilder();
    }

    private static class ExceptionTableEntryBuilder implements UnaryFunction<ByteData, ExceptionTableEntry> {
        int idx = 0;

        ExceptionTableEntryBuilder() {
        }

        @Override
        public ExceptionTableEntry invoke(ByteData arg) {
            return new ExceptionTableEntry(arg, idx++);
        }
    }

    @Override
    public int compareTo(ExceptionTableEntry other) {
        int res = bytecode_index_from - other.bytecode_index_from;
        if (res != 0) return res;
        res = bytecode_index_to - other.bytecode_index_to;
//        res = other.bytecode_index_to - bytecode_index_to;
        if (res != 0) return 0 - res;
        res = bytecode_index_handler - other.bytecode_index_handler;
        return res;
    }

    @Override
    public String toString() {
        return "ExceptionTableEntry " + priority + " : [" + bytecode_index_from + "->" + bytecode_index_to + ") : " + bytecode_index_handler;
    }

}
