package org.benf.cfr.reader.bytecode.analysis.parse.utils;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.AbstractExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;

public class LValueAssignmentExpressionRewriter extends AbstractExpressionRewriter {

    private final LValue lValue;
    private final AbstractAssignmentExpression lValueReplacement;
    private final Op03SimpleStatement source;
    private boolean terminated = false;

    public LValueAssignmentExpressionRewriter(LValue lValue, AbstractAssignmentExpression lValueReplacement, Op03SimpleStatement source) {
        this.lValue = lValue;
        this.lValueReplacement = lValueReplacement;
        this.source = source;
    }

    /* We can't descend any conditionals, so only go down test of those.
     * We also can't pass THROUGH any method calls, however, we CAN pass into arguments.
     * i.e. we can't rewrite foo = bar(); jim.getBob().do(foo), as call order changes.
     */
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (terminated) return expression;
        if (expression instanceof BooleanOperation) {
            return ((BooleanOperation)expression).applyLHSOnlyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        } else if (expression instanceof TernaryExpression) {
            return ((TernaryExpression)expression).applyConditionOnlyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        } else if (expression instanceof LValueExpression) {
            LValue lValue = ((LValueExpression) expression).getLValue();
            if (!lValue.equals(this.lValue)) return expression;
            if (!ssaIdentifiers.isValidReplacement(lValue, statementContainer.getSSAIdentifiers())) return expression;
            SSAIdent ssaIdentOnEntry = source.getSSAIdentifiers().getSSAIdentOnEntry(lValue);
            statementContainer.getSSAIdentifiers().setKnownIdentifierOnEntry(lValue, ssaIdentOnEntry);
            source.nopOut();
            terminated = true;
            return lValueReplacement;
        }
        Expression res = super.rewriteExpression(expression, ssaIdentifiers, statementContainer, flags);
        if (expression instanceof AbstractFunctionInvokation) {
            terminated = true;
        }

        return res;
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (terminated) return expression;
        return (ConditionalExpression)rewriteExpression((Expression)expression, ssaIdentifiers, statementContainer, flags);
    }
}
