package org.benf.cfr.reader.bytecode.analysis.parse.utils;

public class QuotingUtils {

    /*
     * Expensive!
     */
    public static String enquoteUTF(String s) {
        char[] raw = s.toCharArray();
        StringBuilder stringBuilder = new StringBuilder();
        for (char c : raw) {
            if (c < 32 || c > 126) {
                stringBuilder.append("\\u").append(String.format("%04x", (int) c));
            } else {
                stringBuilder.append(c);
            }
        }
        return stringBuilder.toString();
    }

    public static String enquoteString(String s) {
        char[] raw = s.toCharArray();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\"");
        for (char c : raw) {
            switch (c) {
                case '\n':
                    stringBuilder.append("\\n");
                    break;
                case '\r':
                    stringBuilder.append("\\r");
                    break;
                case '\t':
                    stringBuilder.append("\\t");
                    break;
                case '\b':
                    stringBuilder.append("\\b");
                    break;
                case '\f':
                    stringBuilder.append("\\f");
                    break;
                case '\\':
                    stringBuilder.append("\\\\");
                    break;
                case '\"':
                    stringBuilder.append("\\\"");
                    break;
                default:
                    stringBuilder.append(c);
                    break;
            }
        }
        stringBuilder.append("\"");
        return stringBuilder.toString();
    }

    public static String unquoteString(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s;
    }

    public static String addQuotes(String s, boolean singleIsChar)
    {
        if (singleIsChar && s.length() == 1) {
            return "'" + s + "'";
        }
        return '"' + s + '"';
    }
}
