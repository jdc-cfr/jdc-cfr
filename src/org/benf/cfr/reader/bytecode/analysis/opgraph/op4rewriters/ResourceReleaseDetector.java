package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.matchutil.*;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.MiscStatementTools;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.CompOp;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ComparisonOperation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.LValueExpression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.Literal;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.wildcard.WildcardMatch;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.*;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.BeginBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.ElseBlock;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.placeholder.EndBlock;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.AccessFlagMethod;
import org.benf.cfr.reader.entities.Method;

import java.util.List;

public class ResourceReleaseDetector {
    public static boolean isResourceRelease(Method method, Op04StructuredStatement root) {
        if (!method.getAccessFlags().contains(AccessFlagMethod.ACC_STATIC)) return false;
        if (!method.getAccessFlags().contains(AccessFlagMethod.ACC_SYNTHETIC)) return false;
        List<JavaTypeInstance> argTypes = method.getMethodPrototype().getArgs();
        if (argTypes.size() != 2) return false;
        List<LocalVariable> computedParameters = method.getMethodPrototype().getComputedParameters();
        if (computedParameters.size() != 2) return false;

        List<StructuredStatement> structuredStatements = MiscStatementTools.linearise(root);
        if (structuredStatements == null) return false;

        LocalVariable throwable = computedParameters.get(0);
        LocalVariable autoclose = computedParameters.get(1);

        WildcardMatch wcm = new WildcardMatch();
        Matcher<StructuredStatement> m = getStructuredStatementMatcher(wcm, throwable, autoclose);

        MatchIterator<StructuredStatement> mi = new MatchIterator<StructuredStatement>(structuredStatements);

        MatchResultCollector collector = new EmptyMatchResultCollector();
        mi.advance();
        return m.match(mi, collector);
    }

    public static Matcher<StructuredStatement> getStructuredStatementMatcher(WildcardMatch wcm, LValue throwableLValue, LValue autoclose) {
        LValueExpression autocloseExpression = new LValueExpression(autoclose);
        LValueExpression throwableExpression = new LValueExpression(throwableLValue);
        MatchOneOf closeExpression = getCloseExpressionMatch(wcm, autocloseExpression);

        return new MatchSequence(
                new BeginBlock(null),
                new StructuredIf(BytecodeLoc.NONE, new ComparisonOperation(BytecodeLoc.NONE, throwableExpression, Literal.NULL, CompOp.NE), null),
                new BeginBlock(null),
                new StructuredTry(null, null),
                new BeginBlock(null),
                closeExpression,
                new EndBlock(null),
                new StructuredCatch(null, null, wcm.getLValueWildCard("caught"), null),
                new BeginBlock(null),
                new StructuredExpressionStatement(BytecodeLoc.NONE, wcm.getMemberFunction("addsupp", "addSuppressed", throwableExpression, new LValueExpression(wcm.getLValueWildCard("caught"))), false),
                new EndBlock(null),
                new EndBlock(null),
                new ElseBlock(),
                new BeginBlock(null),
                closeExpression,
                new EndBlock(null),
                new EndBlock(null)
        );
    }

    public static Matcher<StructuredStatement> getNonTestingStructuredStatementMatcher(WildcardMatch wcm, LValue throwableLValue, LValue autoclose) {
        LValueExpression autocloseExpression = new LValueExpression(autoclose);
        LValueExpression throwableExpression = new LValueExpression(throwableLValue);
        MatchOneOf closeExpression = getCloseExpressionMatch(wcm, autocloseExpression);

        MatchSequence inner = new MatchSequence(
                // We shouldn't see this, but might have if we've pulled some gotos up.
                new MatchOpt(
                        new MatchSequence(
                                new StructuredIf(BytecodeLoc.NONE, new ComparisonOperation(BytecodeLoc.NONE, new LValueExpression(autoclose), Literal.NULL, CompOp.EQ), null),
                                new BeginBlock(null),
                                new StructuredThrow(BytecodeLoc.NONE, new LValueExpression(throwableLValue)),
                                new EndBlock(null)
                        )
                ),
                new StructuredTry(null, null),
                new BeginBlock(null),
                closeExpression,
                // We shouldn't see this, but might have if we've pulled some gotos up.
                new MatchOpt(new StructuredThrow(BytecodeLoc.NONE, new LValueExpression(throwableLValue))),
                new EndBlock(null),
                new StructuredCatch(null, null, wcm.getLValueWildCard("caught"), null),
                new BeginBlock(null),
                new StructuredExpressionStatement(BytecodeLoc.NONE, wcm.getMemberFunction("addsupp", "addSuppressed", throwableExpression, new LValueExpression(wcm.getLValueWildCard("caught"))), false),
                new EndBlock(null),
                new StructuredThrow(BytecodeLoc.NONE, new LValueExpression(throwableLValue))
        );

        return inner;
    }

    public static Matcher<StructuredStatement> getSimpleStructuredStatementMatcher(WildcardMatch wcm, LValue throwableLValue, LValue autoclose) {
        LValueExpression autocloseExpression = new LValueExpression(autoclose);
        MatchOneOf closeExpression = getCloseExpressionMatch(wcm, autocloseExpression);

        Matcher<StructuredStatement> matchsimple = new MatchOneOf(new MatchSequence(
                new StructuredIf(BytecodeLoc.NONE, new ComparisonOperation(BytecodeLoc.NONE, new LValueExpression(autoclose), Literal.NULL, CompOp.NE), null),
                new BeginBlock(null),
                closeExpression,
                new EndBlock(null)),
                closeExpression);

        return matchsimple;
    }

    public static MatchOneOf getCloseExpressionMatch(WildcardMatch wcm, LValueExpression autocloseExpression) {
        return new MatchOneOf(
                    new StructuredExpressionStatement(BytecodeLoc.NONE, wcm.getMemberFunction("m1", "close", autocloseExpression), false),
                    new StructuredExpressionStatement(BytecodeLoc.NONE, wcm.getMemberFunction("m2", "close", wcm.getCastExpressionWildcard("cast", autocloseExpression)), false)
            );
    }
}
