package org.benf.cfr.reader.bytecode.analysis.parse.utils.finalhelp;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.MemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.statement.*;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement;
import org.benf.cfr.reader.bytecode.analysis.opgraph.op3rewriters.Misc;
import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.Statement;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.StackSSALabel;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.entities.exceptions.ExceptionTableEntry;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.*;

public class FinallyGraphHelper {
    private final FinallyCatchBody finallyCatchBody;


    public FinallyGraphHelper(FinallyCatchBody finallyCatchBody) {
        this.finallyCatchBody = finallyCatchBody;
    }

    public FinallyCatchBody getFinallyCatchBody() {
        return finallyCatchBody;
    }

    private List<Op03SimpleStatement> filterFalseNegatives(List<Op03SimpleStatement> in, Set<Op03SimpleStatement> toRemove) {
        List<Op03SimpleStatement> res = ListFactory.newList();
        for (Op03SimpleStatement i : in) {
            while (i != null && i.getStatement() instanceof Nop) {
                switch (i.getTargets().size()) {
                    case 0:
                        i = null;
                        break;
                    case 1:
                        if (toRemove != null) toRemove.add(i);
                        i = i.getTargets().get(0);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
            if (i != null) res.add(i);
        }
        return res;
    }

    public Result match(Op03SimpleStatement test) {
        Set<BlockIdentifier> minBlockSet = SetFactory.newOrderedSet(test.getBlockIdentifiers());
        Op03SimpleStatement finalThrowProxy = null;
        Op03SimpleStatement finalThrow = finallyCatchBody.getThrowOp();
        Map<Op03SimpleStatement, Op03SimpleStatement> matched = new IdentityHashMap<Op03SimpleStatement, Op03SimpleStatement>();
        Set<Op03SimpleStatement> toRemove = SetFactory.newOrderedSet();
        LinkedList<Pair<Op03SimpleStatement, Op03SimpleStatement>> pending = ListFactory.newLinkedList();
        if (finallyCatchBody.isEmpty()) {
            return new Result(toRemove, null, null);
        }
        Pair<Op03SimpleStatement, Op03SimpleStatement> start = Pair.make(test, finallyCatchBody.getCatchCodeStart());
        pending.add(start);
        matched.put(start.getSecond(), start.getFirst());

        final FinallyEquivalenceConstraint equivalenceConstraint = new FinallyEquivalenceConstraint();

        Set<Op03SimpleStatement> finalThrowProxySources = SetFactory.newOrderedSet();
        while (!pending.isEmpty()) {
            Pair<Op03SimpleStatement, Op03SimpleStatement> p = pending.removeFirst();
            Op03SimpleStatement a = p.getFirst();
            Op03SimpleStatement b = p.getSecond();
            Statement sa = a.getStatement();
            Statement sb = b.getStatement();

            /*
             * Collect anything which is created here.  We will only consider something as being
             * equivalent if it's been overwritten inside the finally statement, or actually IS identical.
             */
            sa.collectLValueAssignments(equivalenceConstraint);

            if (!sa.equivalentUnder(sb, equivalenceConstraint)) {
                /*
                 * jdk13+ may need us to consider the following equivalent
                 *
                 * test:
                 * a
                 * b
                 *
                 * finallyCatchBody:
                 * a
                 * try {
                 * b
                 * } catch( x)
                 * e.addSuppressed(x)
                 *
                 */
                if (treatAsJava13Finally(b, sb)) {
                    b = b.getTargets().get(0);
                    sb = b.getStatement();
                    if (!sa.equivalentUnder(sb, equivalenceConstraint)) return Result.FAIL;
                    if (b.getTargets().size() != 1) return Result.FAIL;
                    Statement stm = b.getTargets().get(0).getStatement();
                    if (!(stm instanceof GotoStatement)) return Result.FAIL;
                    b = b.getTargets().get(0);
                } else {
                    return Result.FAIL;
                }
            }

            List<Op03SimpleStatement> tgta = ListFactory.newList(a.getTargets());
            List<Op03SimpleStatement> tgtb = ListFactory.newList(b.getTargets());
            // This fixes 22a, but breaks 1!!
//            tgta.remove(finalThrowProxy);
//            tgtb.remove(finallyCatchBody.throwOp);
            /* Process both, walk no-ops ... walk goto as well? */
            tgta = filterFalseNegatives(tgta, toRemove);
            tgtb = filterFalseNegatives(tgtb, null);

            if (tgta.size() != tgtb.size()) {
                return Result.FAIL;
            }
            toRemove.add(a);

            for (int x = 0, len = tgta.size(); x < len; ++x) {
                Op03SimpleStatement tgttestx = tgta.get(x);   // test tgt
                Op03SimpleStatement tgthayx = tgtb.get(x); // expected tgt
                // Skip Comments.
                tgttestx = Misc.skipComments(tgttestx);
                tgthayx = Misc.skipComments(tgthayx);

                Op03SimpleStatement tgttestx2 = Misc.followNopGotoChain(tgttestx, false, false);
                Op03SimpleStatement tgthayx2 = Misc.followNopGotoChain(tgthayx, false, false);
                Op03SimpleStatement finalyThrowProxy2 = Misc.followNopGotoChain(finalThrowProxy, false, false);
                /*
                 * We require that it's in at LEAST all the blocks the test started in.
                 */
                Set<BlockIdentifier> newBlockIdentifiers = tgttestx.getBlockIdentifiers();
                if (newBlockIdentifiers.containsAll(minBlockSet)) {
                    if (tgthayx2 == finalThrow) {
                        if (finalThrowProxy != null &&
                                !(finalThrowProxy == tgttestx2 || finalyThrowProxy2 == tgttestx2)) {
                            /*
                             * What if it's identical to finalThrowProxy?
                             */
                            Statement s1 = tgttestx.getStatement();
                            Statement s2 = finalThrowProxy.getStatement();
                            if (s1.getClass() == GotoStatement.class && s1.equals(s2)) {
                                // nada.
                                int y = 1;
                            } else {
                                return Result.FAIL;
                            }
                        }
                        if (finalThrowProxy == null) {
                            finalThrowProxy = tgttestx;
                        }
                        finalThrowProxySources.add(a);
                    }
                    if ((!matched.containsKey(tgthayx)) && finallyCatchBody.contains(tgthayx)) {
                        matched.put(tgthayx, tgttestx);
                        pending.add(Pair.make(tgttestx, tgthayx));
                    }
                }
            }
        }

        return new Result(toRemove, test, Misc.followNopGotoChain(finalThrowProxy, false, false));
    }

    private boolean treatAsJava13Finally(Op03SimpleStatement b, Statement sb) {
        if (!(sb instanceof TryStatement)) return false;
        List<Op03SimpleStatement> bTargets = b.getTargets();
        if (bTargets.size() != 2) return false;
        Op03SimpleStatement catchStm = bTargets.get(1);
        if (!(catchStm.getStatement() instanceof CatchStatement)) return false;
        List<Op03SimpleStatement> catchTargets = catchStm.getTargets();
        if (catchTargets.size() != 1) return false;
        Statement addSupp = catchTargets.get(0).getStatement();
        if (!(addSupp instanceof ExpressionStatement)) return false;
        Expression eAddSup = ((ExpressionStatement) addSupp).getExpression();
        if (!(eAddSup instanceof MemberFunctionInvokation)) return false;
        MemberFunctionInvokation mfi = (MemberFunctionInvokation)eAddSup;
        if (!mfi.getMethodPrototype().getName().equals("addSuppressed")) return false;
        return true;
    }

    private class FinallyEquivalenceConstraint extends DefaultEquivalenceConstraint implements LValueAssignmentCollector<Statement> {
        /*
        * We allow ssa lvalues to mismatch, but they must continue to....
        */
        private final Map<StackSSALabel, StackSSALabel> rhsToLhsMap = MapFactory.newMap();
        private final Map<LocalVariable, LocalVariable> rhsToLhsLVMap = MapFactory.newMap();
        private final Set<StackSSALabel> validSSA = SetFactory.newSet();
        private final Set<LocalVariable> validLocal = SetFactory.newSet();

        private StackSSALabel mapSSALabel(StackSSALabel s1, StackSSALabel s2) {
            StackSSALabel r1 = rhsToLhsMap.get(s2);
            if (r1 != null) return r1;
            rhsToLhsMap.put(s2, s1);
            return s1;
        }

        private LocalVariable mapLocalVariable(LocalVariable s1, LocalVariable s2) {
            LocalVariable r1 = rhsToLhsLVMap.get(s2);
            if (r1 != null) return r1;
            rhsToLhsLVMap.put(s2, s1);
            return s1;
        }


        @Override
        public boolean equivalent(Object o1, Object o2) {
            if (o1 == null) return o2 == null;
            if (o1 instanceof Collection && o2 instanceof Collection) {
                return equivalent((Collection) o1, (Collection) o2);
            }
            if (o1 instanceof StackSSALabel && o2 instanceof StackSSALabel) {
                if (validSSA.contains(o1)) {
                    o2 = mapSSALabel((StackSSALabel) o1, (StackSSALabel) o2);
                } else {
                    int x = 1;
                }
            }
            if (o1 instanceof LocalVariable && o2 instanceof LocalVariable) {
                if (validLocal.contains(o1)) {
                    o2 = mapLocalVariable((LocalVariable) o1, (LocalVariable) o2);
                } else {
                    int x = 1;
                }
            }
            if (o1 instanceof ExceptionTableEntry && o2 instanceof ExceptionTableEntry) {
                return true;
            }
            return super.equivalent(o1, o2);
        }

        @Override
        public void collect(StackSSALabel lValue, StatementContainer<Statement> statementContainer, Expression value) {
            validSSA.add(lValue);
        }

        @Override
        public void collectMultiUse(StackSSALabel lValue, StatementContainer<Statement> statementContainer, Expression value) {
            validSSA.add(lValue);
        }

        @Override
        public void collectMutatedLValue(LValue lValue, StatementContainer<Statement> statementContainer, Expression value) {
            int x = 1;
        }

        @Override
        public void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<Statement> statementContainer, Expression value) {
            validLocal.add(localVariable);
        }
    }
}
