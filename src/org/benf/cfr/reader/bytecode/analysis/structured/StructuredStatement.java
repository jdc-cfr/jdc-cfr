package org.benf.cfr.reader.bytecode.analysis.structured;

import org.benf.cfr.reader.bytecode.analysis.loc.HasByteCodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.Matcher;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.transformers.StructuredStatementTransformer;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.BlockIdentifier;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.LValueScopeDiscoverer;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.scope.ScopeDiscoverInfoCache;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.output.Dumpable;

import java.util.List;
import java.util.Vector;

public interface StructuredStatement extends Dumpable, TypeUsageCollectable, HasByteCodeLoc, Matcher<StructuredStatement> {

    Op04StructuredStatement getContainer();

    void setContainer(Op04StructuredStatement container);

    StructuredStatement claimBlock(Op04StructuredStatement innerBlock, BlockIdentifier blockIdentifier, Vector<BlockIdentifier> blocksCurrentlyIn);

    StructuredStatement informBlockHeirachy(Vector<BlockIdentifier> blockIdentifiers);

    void transformStructuredChildren(StructuredStatementTransformer transformer, StructuredScope scope);

    void transformStructuredChildrenInReverse(StructuredStatementTransformer transformer, StructuredScope scope);

    // This isn't recursive - maybe it should be.
    void rewriteExpressions(ExpressionRewriter expressionRewriter);

    /*
     * Is THIS a structured statement?
     */
    boolean isProperlyStructured();

    /*
     * Is this and its children structured?
     */
    boolean isRecursivelyStructured();

    BlockIdentifier getBreakableBlockOrNull();

    void linearizeInto(List<StructuredStatement> out);

    void traceLocalVariableScope(LValueScopeDiscoverer scopeDiscoverer);

    void markCreator(LValue scopedEntity, StatementContainer<StructuredStatement> hint);

    boolean alwaysDefines(LValue scopedEntity);

    boolean canDefine(LValue scopedEntity, ScopeDiscoverInfoCache factCache);

    boolean supportsContinueBreak();

    boolean supportsBreak();

    boolean isScopeBlock();

    boolean inlineable();

    Op04StructuredStatement getInline();

    // Is it a comment, or a block containing nothign but comments?
    boolean isEffectivelyNOP();

    boolean fallsNopToNext();

    boolean canFall();

    List<LValue> findCreatedHere();

    String suggestName(LocalVariable createdHere, Predicate<String> testNameUsedFn);
}