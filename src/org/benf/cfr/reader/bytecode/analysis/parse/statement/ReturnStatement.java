package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.DeepCloneable;

public abstract class ReturnStatement extends AbstractStatement {

    public ReturnStatement(BytecodeLoc loc) {
        super(loc);
    }

    @Override
    public boolean fallsToNext() {
        return false;
    }

    @Override
    public ReturnStatement outerDeepClone(CloneHelper cloneHelper) {
        throw new UnsupportedOperationException();
    }
}
