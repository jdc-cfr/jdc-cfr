package org.benf.cfr.reader.bytecode.analysis.parse.statement;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLocFactoryImpl;
import org.benf.cfr.reader.bytecode.analysis.loc.HasByteCodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.CloneHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.ConfusedCFRException;
import org.benf.cfr.reader.util.output.Dumper;
import org.benf.cfr.reader.util.output.ToStringDumper;

import java.util.List;
import java.util.Set;

public abstract class AbstractStatement implements Statement {
    private BytecodeLoc loc;
    private StatementContainer<Statement> container;

    public AbstractStatement(BytecodeLoc loc) {
        this.loc = loc;
    }

    @Override
    public BytecodeLoc getLoc() {
        return loc;
    }

    @Override
    public void addLoc(HasByteCodeLoc loc) {
        if (loc.getLoc().isEmpty()) return;
        this.loc = BytecodeLocFactoryImpl.INSTANCE.combine(this, loc);
    }

    @Override
    public void setContainer(StatementContainer<Statement> container) {
        if (container == null) throw new ConfusedCFRException("Trying to setContainer null!");
        this.container = container;
    }

    @Override
    public Statement outerDeepClone(CloneHelper cloneHelper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LValue getCreatedLValue() {
        return null;
    }

    @Override
    public void collectLValueAssignments(LValueAssignmentCollector<Statement> lValueAssigmentCollector) {
    }

    @Override
    public boolean doesBlackListLValueReplacement(LValue lValue, Expression expression) {
        return false;
    }

    @Override
    public void collectObjectCreation(CreationCollector creationCollector) {
    }

    @Override
    public SSAIdentifiers<LValue> collectLocallyMutatedVariables(SSAIdentifierFactory<LValue, ?> ssaIdentifierFactory) {
        return new SSAIdentifiers<LValue>();
    }

    @Override
    public StatementContainer<Statement> getContainer() {
//        if (container == null) {
//            throw new ConfusedCFRException("Null container!");
//        }
        return container;
    }

    @Override
    public Expression getRValue() {
        return null;
    }

    protected Statement getTargetStatement(int idx) {
        return container.getTargetStatement(idx);
    }

    @Override
    public boolean isCompound() {
        return false;
    }

    @Override
    public List<Statement> getCompoundParts() {
        throw new ConfusedCFRException("Should not be calling getCompoundParts on this statement");
    }

    @Override
    public final String toString() {
        Dumper d = new ToStringDumper();
        d.print(getClass().getSimpleName()).print(": ").dump(this);
        return d.toString();
    }

    @Override
    public boolean fallsToNext() {
        return true;
    }

    @Override
    public boolean canThrow(ExceptionCheck caught) {
        return true;
    }

    @Override
    public Set<LValue> wantsLifetimeHint() {
        return null;
    }

    @Override
    public void setLifetimeHint(LValue lv, boolean usedInChildren) {
    }
}
