package org.benf.cfr.reader.bytecode.analysis.parse.rewriters;

import org.benf.cfr.reader.bytecode.analysis.loc.BytecodeLoc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.*;
import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.QuotingUtils;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.RawJavaType;
import org.benf.cfr.reader.bytecode.analysis.types.TypeConstants;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.*;

public class StringBuilderRewriter implements ExpressionRewriter {
    private final boolean stringBuilderEnabled;
    private final boolean stringBufferEnabled;
    private final boolean stringConcatFactoryEnabled;

    public StringBuilderRewriter(Options options, ClassFileVersion classFileVersion) {
        this.stringBufferEnabled = options.getOption(OptionsImpl.SUGAR_STRINGBUFFER, classFileVersion);
        this.stringBuilderEnabled = options.getOption(OptionsImpl.SUGAR_STRINGBUILDER, classFileVersion);
        this.stringConcatFactoryEnabled = options.getOption(OptionsImpl.SUGAR_STRINGCONCATFACTORY, classFileVersion);
    }

    @Override
    public Expression rewriteExpression(Expression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        expression = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);

        Expression result = null;
        if ((stringBufferEnabled || stringBuilderEnabled) && expression instanceof MemberFunctionInvokation) {
            MemberFunctionInvokation memberFunctionInvokation = (MemberFunctionInvokation) expression;
            if ("toString".equals(memberFunctionInvokation.getName())) {
                Expression lhs = memberFunctionInvokation.getObject();
                result = testAppendChain(lhs);
            }
        } else if (stringConcatFactoryEnabled && expression instanceof StaticFunctionInvokation) {
            StaticFunctionInvokation invokation = (StaticFunctionInvokation)expression;
            if ("makeConcatWithConstants".equals(invokation.getName())
                    && invokation.getClazz().getRawName().equals(TypeConstants.stringConcatFactoryName)) {
                result = extractStringConcat(invokation);
            } else if ("makeConcat".equals(invokation.getName())
                    && invokation.getClazz().getRawName().equals(TypeConstants.stringConcatFactoryName)) {
                result = extractStringConcatSimple(invokation);
            }
        }
        if (result != null) {
            return result;
        }
        return expression;
    }

    private Expression extractStringConcatSimple(StaticFunctionInvokation staticFunctionInvokation) {
        List<Expression> args = ListFactory.newList(staticFunctionInvokation.getArgs());
        args.remove(0);
        // Amazingly, "" + foo generates a stringconcat, even though it's a 1 arg one!
        if (args.size() < 1) return null;
        List<Expression> tmp = ListFactory.newList(args);
        Collections.reverse(tmp);
        for (int x=0;x<tmp.size();++x) {
            tmp.set(x, CastExpression.tryRemoveCast(tmp.get(x)));
        }
        Expression res = genStringConcat(tmp);
        if (res == null) return staticFunctionInvokation;
        staticFunctionInvokation.getInferredJavaType().forceDelegate(res.getInferredJavaType());
        return res;
    }

    private Expression extractStringConcat(StaticFunctionInvokation staticFunctionInvokation) {
        List<Expression> args = staticFunctionInvokation.getArgs();
        if (args.size() <= 2) return null;
        Expression arg0 = args.get(1);
        int argIdx = 2;
        int maxArgs = args.size();
        if (!(arg0 instanceof NewAnonymousArray)) return null;
        NewAnonymousArray naArg0 = (NewAnonymousArray)arg0;
        if (naArg0.getNumDims() != 1) return null;
        List<Expression> specs = naArg0.getValues();
        if (specs.size() != 1) return null;
        Expression spec = specs.get(0);
        if (!(spec instanceof Literal)) return null;
        TypedLiteral lSpec = ((Literal)spec).getValue();
        if (lSpec.getType() != TypedLiteral.LiteralType.String) return null;
        // Finally  ;)
        String strSpecQuoted = (String)lSpec.getValue();
        // Odd, but this spec should be bookended with quotes.  Remove.
        String strSpec = QuotingUtils.unquoteString(strSpecQuoted);
        if (strSpec.length() == strSpecQuoted.length()) return null;
        // split doesn't have returnDelims behaviour.
        StringTokenizer st = new StringTokenizer(strSpec, "\u0001", true);
        List<Expression> toks = new ArrayList<Expression>();
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            if (tok.equals("\u0001")) {
                if (argIdx >= maxArgs) {
                    // We've illegally run out of arguments.  Can't handle this.
                    return null;
                }
                Expression arg = CastExpression.tryRemoveCast(args.get(argIdx++));
                toks.add(arg);
            } else {
                toks.add(new Literal(TypedLiteral.getString(QuotingUtils.addQuotes(tok, false))));
            }
        }
        // Or rewrite genStringConcat to not expect arg in reverse!
        Collections.reverse(toks);
        // The previous return type would be 'CallSite'.  If we leave this in place, we have (String)(Callsite)"dddd" + a;
        Expression res = genStringConcat(toks);
        if (res == null) return staticFunctionInvokation;
        staticFunctionInvokation.getInferredJavaType().forceDelegate(res.getInferredJavaType());
        return res;
    }


    @Override
    public void handleStatement(StatementContainer statementContainer) {
    }

    @Override
    public ConditionalExpression rewriteExpression(ConditionalExpression expression, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        Expression res = expression.applyExpressionRewriter(this, ssaIdentifiers, statementContainer, flags);
        return (ConditionalExpression) res;
    }

    @Override
    public LValue rewriteExpression(LValue lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    @Override
    public StackSSALabel rewriteExpression(StackSSALabel lValue, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer, ExpressionRewriterFlags flags) {
        return lValue;
    }

    private Expression testAppendChain(Expression lhs) {
        List<Expression> reverseAppendChain = ListFactory.newList();
        do {
            if (lhs instanceof MemberFunctionInvokation) {
                MemberFunctionInvokation memberFunctionInvokation = (MemberFunctionInvokation) lhs;
                if (memberFunctionInvokation.getName().equals("append") &&
                        memberFunctionInvokation.getArgs().size() == 1) {
                    lhs = memberFunctionInvokation.getObject();
                    Expression e = memberFunctionInvokation.getAppropriatelyCastArgument(0);
                    e = CastExpression.tryRemoveCast(e);
                    reverseAppendChain.add(e);
                } else {
                    return null;
                }
            } else if (lhs instanceof ConstructorInvokationSimple) {
                ConstructorInvokationSimple newObject = (ConstructorInvokationSimple) lhs;
                String rawName = newObject.getTypeInstance().getRawName();
                if ((stringBuilderEnabled && rawName.equals(TypeConstants.stringBuilderName)) ||
                        (stringBufferEnabled && rawName.equals(TypeConstants.stringBufferName))) {
                    // If the constructor has an argument of a String or a CharSequence, we need to add
                    // that to the reverseAppendChain too!
                    switch (newObject.getArgs().size()) {
                        default:
                            return null;
                        case 1: {
                            Expression e = newObject.getArgs().get(0);
                            String typeName = e.getInferredJavaType().getJavaTypeInstance().getRawName();
                            if (typeName.equals(TypeConstants.stringName)) {
                                // Could also do it for type sequences, but .....
                                e = CastExpression.tryRemoveCast(e);
                                reverseAppendChain.add(e);
                            } else {
                                return null;
                            }
                        }
                        // fall through.
                        case 0:
                            return genStringConcat(reverseAppendChain);
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } while (lhs != null);
        return null;
    }

    private Expression genStringConcat(List<Expression> revList) {
        JavaTypeInstance lastType = revList.get(revList.size() - 1).getInferredJavaType().getJavaTypeInstance();
        if (lastType != TypeConstants.STRING) {
            boolean needed = true;
            // If it's a primitive, we can't have 1 + 1 + "" in indyland.
            if (lastType instanceof RawJavaType || RawJavaType.getUnboxedTypeFor(lastType) != null) {
                if (revList.size() > 1 &&
                        revList.get(revList.size() - 2).getInferredJavaType().getJavaTypeInstance() == TypeConstants.STRING) {
                    needed = false;
                }
            } else {
                for (Expression e : revList) {
                    if (e.getInferredJavaType().getJavaTypeInstance() == TypeConstants.STRING) {
                        needed = false;
                        break;
                    }
                }
            }
            if (needed) {
                revList.add(new Literal(TypedLiteral.getString("\"\"")));
            }
        }

        int x = revList.size() - 1;
        if (x < 0) return null;
        Expression head = revList.get(x);
        InferredJavaType inferredJavaType = new InferredJavaType(TypeConstants.STRING, InferredJavaType.Source.STRING_TRANSFORM, true);
        for (--x; x >= 0; --x) {
            Expression appendee = revList.get(x);
            head = new ArithmeticOperation(BytecodeLoc.TODO, inferredJavaType, head, appendee, ArithOp.PLUS);
        }
        return head;
    }
}
