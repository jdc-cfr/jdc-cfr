package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.output.IllegalIdentifierDump;

import java.util.LinkedList;

class TypeUsageUtils {
    static String generateInnerClassShortName(final IllegalIdentifierDump iid, final JavaRefTypeInstance clazz, JavaRefTypeInstance analysisType, boolean prefixAnalysisType) {
        LinkedList<JavaRefTypeInstance> classStack = ListFactory.newLinkedList();

        boolean analysisTypeFound = false;
        if (clazz.getRawName().startsWith(analysisType.getRawName())) {
            // In case we don't have full info.
            // This is, at best, a guess.
            String possible = clazz.getRawName().substring(analysisType.getRawName().length());
            if (!possible.isEmpty()) {
                switch (possible.charAt(0)) {
                    case '$':
                    case '.':
                        analysisTypeFound = true;
                        break;
                }
            }
        }
        JavaRefTypeInstance currentClass = clazz;
        boolean first = true;
        do {
            InnerClassInfo innerClassInfo = currentClass.getInnerClassHereInfo();
            // Need to skip anonymous classes, see (eg) anonymousInnerClassTest3.
            if (!innerClassInfo.isAnonymousClass() || first) {
                classStack.addFirst(currentClass);
            }
            first = false;
            if (!innerClassInfo.isInnerClass()) {
                break;
            }
            currentClass = innerClassInfo.getOuterClass();
            if (currentClass.equals(analysisType)) {
                analysisTypeFound = true;
                break;  // We don't want to go any further back than here!
            }
        } while (true);
        /*
         * Local inner class.  We want the smallest postfix.  We can drop the local class, but because we're not doing
         * imports for local classes, we can't drop any more.
         */
        if (analysisTypeFound == currentClass.equals(analysisType)) {
            StringBuilder sb = new StringBuilder();
            first = true;
            /*
             * if we've been overridden, we need to prefix the analysis type. (See ShortNameTest5)
             */
            if (prefixAnalysisType) {
                sb.append(analysisType.getRawShortName(iid));
                first = false;
            }

            for (JavaRefTypeInstance stackClass : classStack) {
                first = StringUtils.dot(first, sb);
                sb.append(stackClass.getRawShortName(iid));
            }
            return sb.toString();
        } else {
            // string approximation.
            String clazzRawName = clazz.getRawName(iid);
            // Cheat using $.
            String analysisTypeRawName = analysisType.getRawName(iid);

            if (clazzRawName.equals(analysisTypeRawName)) {
                int idx = clazzRawName.lastIndexOf('.');
                if (idx >= 1 && idx < (clazzRawName.length() - 1)) {
                    return clazzRawName.substring(idx + 1);
                }
            }

            if (analysisTypeRawName.length() >= (clazzRawName.length() - 1)) {
                return clazzRawName;
            }
            return clazzRawName.substring(analysisType.getRawName().length() + 1);
        }
    }
}
