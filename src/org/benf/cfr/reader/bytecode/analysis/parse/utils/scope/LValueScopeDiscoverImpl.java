package org.benf.cfr.reader.bytecode.analysis.parse.utils.scope;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.LValue;
import org.benf.cfr.reader.bytecode.analysis.parse.StatementContainer;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.FieldVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.lvalue.LocalVariable;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.ReadWrite;
import org.benf.cfr.reader.bytecode.analysis.structured.StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.structured.statement.StructuredCatch;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.MethodPrototype;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.bytecode.analysis.variables.NamedVariable;
import org.benf.cfr.reader.bytecode.analysis.variables.VariableFactory;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

public class LValueScopeDiscoverImpl extends AbstractLValueScopeDiscoverer {
    private final boolean instanceOfDefines;

    public LValueScopeDiscoverImpl(Options options, MethodPrototype prototype, VariableFactory variableFactory, ClassFileVersion version) {
        super(options, prototype, variableFactory);
        instanceOfDefines = options.getOption(OptionsImpl.INSTANCEOF_PATTERN, version);
    }

    @Override
    public void collectLocalVariableAssignment(LocalVariable localVariable, StatementContainer<StructuredStatement> statementContainer, Expression value) {

        // Ensure type clashes are collapsed, otherwise PairTest3 gets duplicate definitions.
        localVariable.getInferredJavaType().collapseTypeClash();

        // Note that just because two local variables in the same scope have the same name, they're not NECESSARILY
        // the same variable - if we've reused a stack location, and don't have any naming hints, the name will have
        // been re-used.  This is why we also have to verify that the type of the new assignment is the same as the type
        // of the previous one, and kick out the previous (and remove from earlier scopes) if that's the case).
        NamedVariable name = localVariable.getName();
        ScopeDefinition previousDef = earliestDefinition.get(name);
        JavaTypeInstance newType = localVariable.getInferredJavaType().getJavaTypeInstance();
        if ( // Doesn't exist at this level.
                previousDef == null ||
                // Total bodge - catch statements.
                ( previousDef.getDepth() == currentDepth && previousDef.getExactStatement() != null &&
                previousDef.getExactStatement().getStatement() instanceof StructuredCatch)
        ) {
            // First use is here.
            ScopeDefinition scopeDefinition = new ScopeDefinition(currentDepth, currentBlock, statementContainer, localVariable, newType, name, null, true);
            earliestDefinition.put(name, scopeDefinition);
            earliestDefinitionsByLevel.get(currentDepth).put(name, true);
            discoveredCreations.add(scopeDefinition);
            return;
        }

        /*
         * Else verify type.
         */
        JavaTypeInstance oldType = previousDef.getJavaTypeInstance();
        if (!oldType.equals(newType)) {
            earliestDefinitionsByLevel.get(previousDef.getDepth()).remove(previousDef.getName());
            if (previousDef.getDepth() == currentDepth) {
                variableFactory.mutatingRenameUnClash(localVariable);
                name = localVariable.getName();
            }

            InferredJavaType inferredJavaType = localVariable.getInferredJavaType();
            ScopeDefinition scopeDefinition = new ScopeDefinition(currentDepth, currentBlock, statementContainer, localVariable, inferredJavaType, name);
            earliestDefinition.put(name, scopeDefinition);
            earliestDefinitionsByLevel.get(currentDepth).put(name, true);
            discoveredCreations.add(scopeDefinition);
        }
    }

    public boolean didDetectInstanceOfMatching() {
        if (!instanceOfDefines) return false;
        ScopeDiscoverInfoCache sdi = getFactCache();
        return sdi.anyFound();
    }

    @Override
    public void collect(LValue lValue, ReadWrite rw) {
        Class<?> lValueClass = lValue.getClass();

        if (lValueClass == LocalVariable.class) {
            LocalVariable localVariable = (LocalVariable) lValue;
            NamedVariable name = localVariable.getName();
            if (name.getStringName().equals(MiscConstants.THIS)) return;

            ScopeDefinition previousDef = earliestDefinition.get(name);
            // If it's in scope, no problem.
            if (previousDef != null) return;

            // If it's out of scope, we have a variable defined but only assigned in an inner scope, but used in the
            // outer scope later.... or EARLIER. (PairTest3b)
            InferredJavaType inferredJavaType = lValue.getInferredJavaType();
            ScopeDefinition scopeDefinition = new ScopeDefinition(currentDepth, currentBlock, currentBlock.peek(), lValue, inferredJavaType, name);
            earliestDefinition.put(name, scopeDefinition);
            earliestDefinitionsByLevel.get(currentDepth).put(name, true);
            discoveredCreations.add(scopeDefinition);
        } else if (lValueClass == FieldVariable.class) {
            lValue.collectLValueUsage(this);
        }
    }

    @Override
    public boolean descendLambdas() {
        return false;
    }

    @Override
    public boolean ifCanDefine() {
        return instanceOfDefines;
    }
}
