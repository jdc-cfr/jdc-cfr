package org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op4rewriters.util.BoxingHelper;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.rewriteinterface.BoxingProcessor;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.classfilehelpers.OverloadMethodSet;

import java.util.List;

/**
 * This seems daft - why do I need to have all this boilerplate?
 * Why not just replace with a cast, and a function pointer.
 */
public class PrimitiveBoxingRewriter implements ExpressionRewriter {

    public PrimitiveBoxingRewriter() {
    }

    @Override
    public void handleStatement(StatementContainer statementContainer) {
        Object statement = statementContainer.getStatement();
        if (statement instanceof BoxingProcessor) {
            ((BoxingProcessor) statement).rewriteBoxing(this);
        }
    }

    /*
     * Expression rewriter boilerplate - note that we can't expect ssaIdentifiers to be non-null.
     */
    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof BoxingProcessor) {
            BoxingProcessor boxingProcessor = (BoxingProcessor) expression;
            if (boxingProcessor.rewriteBoxing(this)) {
                boxingProcessor.applyNonArgExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
                return expression;
            }
        }
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return expression;
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        if (expression instanceof BoxingProcessor) {
            BoxingProcessor boxingProcessor = (BoxingProcessor) expression;
            if (boxingProcessor.rewriteBoxing(this)) {
                boxingProcessor.applyNonArgExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
                return expression;
            }
        }
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (ConditionalExpression) res;
    }

//    @Override
//    public AbstractAssignmentExpression rewriteExpression(AbstractAssignmentExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
//        if (expression instanceof BoxingProcessor) {
//            ((BoxingProcessor) expression).rewriteBoxing(this);
//        }
//        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
//        return (AbstractAssignmentExpression) res;
//    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    @Override
    public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    // Strip out boxing and casting - but if it changes the function being called, or no longer matches, then
    // ignore the change.
    public Expression sugarParameterBoxing(Expression in, int argIdx, OverloadMethodSet possibleMethods, GenericTypeBinder gtb, MethodPrototype methodPrototype) {
        Expression res = in;
        InferredJavaType outerCastType = null;
        Expression res1 = null;
        if (in instanceof CastExpression) {
            boolean wasRaw = true;
            if (methodPrototype != null) {
                List<JavaTypeInstance> argTypes = methodPrototype.getArgs();
                if (argIdx <= argTypes.size() - 1) {
                    wasRaw = argTypes.get(argIdx) instanceof RawJavaType;
                }
            }
            outerCastType = in.getInferredJavaType();
            res = CastExpression.removeImplicitOuterType(res, gtb, wasRaw);
            res1 = res;
        }

        if (res instanceof MemberFunctionInvokation) {
            res = BoxingHelper.sugarUnboxing((MemberFunctionInvokation) res);
        } else if (res instanceof StaticFunctionInvokation) {
            res = BoxingHelper.sugarBoxing((StaticFunctionInvokation) res);
        }
        if (res == in) return in;
        if (!possibleMethods.callsCorrectMethod(res, argIdx, gtb)) {
            if (outerCastType != null) {
                if (res.getInferredJavaType().getJavaTypeInstance().impreciseCanCastTo(outerCastType.getJavaTypeInstance(), gtb)) {
                    res = new CastExpression(BytecodeLoc.NONE, outerCastType, res);
                    if (possibleMethods.callsCorrectMethod(res, argIdx, gtb)) {
                        return res;
                    }
                }
            }
            if (res1 != null && possibleMethods.callsCorrectMethod(res1, argIdx, gtb)) {
                return res1;
            }
            return in;
        }
        return res;
//        return sugarParameterBoxing(res, argIdx, possibleMethods);
    }

    public void removeRedundantCastOnly(List<Expression> mutableIn) {
        for (int x = 0, len = mutableIn.size(); x < len; ++x) {
            mutableIn.set(x, removeRedundantCastOnly(mutableIn.get(x)));
        }
    }

    private Expression removeRedundantCastOnly(Expression in) {
        if (in instanceof CastExpression) {
            if (((CastExpression) in).isForced()) return in;
            JavaTypeInstance castType = in.getInferredJavaType().getJavaTypeInstance();
            JavaTypeInstance childType = ((CastExpression) in).getChild().getInferredJavaType().getJavaTypeInstance();
            if (castType.equals(childType)) {
                return removeRedundantCastOnly(((CastExpression) in).getChild());
            }
        }
        return in;
    }

    public Expression sugarNonParameterBoxing(final Expression in, JavaTypeInstance tgtType) {
        Expression res = in;
        boolean recast = false;
        if (in instanceof CastExpression && ((CastExpression) in).couldBeImplicit(null)) {
            // We can strip this IF it is a cast that could be implicit.
            res = ((CastExpression) in).getChild();
            /*
             * But if this has left us with a simple null, and the target type is not an object, it won't work.
             */
            if (Literal.NULL.equals(res) && !tgtType.isObject()) return in;
            /*
             * Or if this has gone from one raw type to another, and the target type is exactly object
             * (could probably be more generous).
             */
            if (tgtType.isObject()
                && !BoxingHelper.isBoxedType(tgtType)
                && in.getInferredJavaType().getJavaTypeInstance().isRaw()
                && res.getInferredJavaType().getJavaTypeInstance().isRaw()
                && res.getInferredJavaType().getJavaTypeInstance() != in.getInferredJavaType().getJavaTypeInstance()) {
                return in;
            }
            recast = !(tgtType instanceof RawJavaType);
        } else if (in instanceof MemberFunctionInvokation) {
            res = BoxingHelper.sugarUnboxing((MemberFunctionInvokation) in);
        } else if (in instanceof StaticFunctionInvokation) {
            res = BoxingHelper.sugarBoxing((StaticFunctionInvokation) in);
        }
        if (res == in) return in;
        if (!res.getInferredJavaType().getJavaTypeInstance().implicitlyCastsTo(in.getInferredJavaType().getJavaTypeInstance(), null))
            return in;
        /*
         * However, there's a possibility that the original, unboxed type, could be cast to tgtType, (even if it required
         * an explicit cast), but that the boxed version cannot.
         *
         * i.e. Double d
         * (Integer)(int)(double)d is valid
         * but
         * (Integer)(int)d is not.
         * nor is
         * (Integer)(double)d .
         */
        if (!res.getInferredJavaType().getJavaTypeInstance().impreciseCanCastTo(tgtType, null)) {
            return in;
        }
        res = sugarNonParameterBoxing(res, tgtType);
        if (recast) {
            CastExpression cast = (CastExpression) in;
            if (!cast.isForced()) {
                if (cast.getInferredJavaType().getJavaTypeInstance() instanceof RawJavaType) {
                    if (res.getInferredJavaType().getJavaTypeInstance() instanceof JavaRefTypeInstance) {
                        // hmm - we've stripped this cast.
                        res = new CastExpression(BytecodeLoc.NONE, cast.getInferredJavaType(), res);
                    }
                }
            }
        }
        return res;
    }

    public Expression sugarUnboxing(Expression in) {
        if (in instanceof MemberFunctionInvokation) {
            return BoxingHelper.sugarUnboxing((MemberFunctionInvokation) in);
        }
        return in;
    }

    // Cheat, but...
    public boolean isUnboxedType(Expression in) {
        JavaTypeInstance type = in.getInferredJavaType().getJavaTypeInstance();
        if (!(type instanceof RawJavaType)) return false;
        if (in instanceof AbstractMemberFunctionInvokation) return false;
        RawJavaType rawJavaType = (RawJavaType) type;
        return rawJavaType.isUsableType();
    }
}
