package org.benf.cfr.reader.bytecode.analysis.variables;

public class Ident {
    private final int stackpos;
    private final int idx;

    public Ident(int stackpos, int idx) {
        this.stackpos = stackpos;
        this.idx = idx;
    }

    @Override
    public String toString() {
        if (idx == 0) return "" + stackpos;
        return "" + stackpos + "_" + idx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Ident ident = (Ident) o;

        if (idx != ident.idx) return false;
        if (stackpos != ident.stackpos) return false;

        return true;
    }

    public int getIdx() {
        return idx;
    }

    @Override
    public int hashCode() {
        int result = stackpos;
        result = 31 * result + idx;
        return result;
    }
}
