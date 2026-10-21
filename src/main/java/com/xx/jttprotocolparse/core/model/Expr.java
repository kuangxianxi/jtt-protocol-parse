package com.xx.jttprotocolparse.core.model;

import java.util.Map;

/**
 * 极简算式解析：只支持 数字、"$字段名"、*、+、括号。
 * 例："$motorCount"、"$motorCount*13+2"、"rest"（由调用方处理）。
 */
public final class Expr {

    private Expr() {
    }

    /** 解析算式，无法解析/引用未定义变量时返回 null */
    public static Double eval(String s, Map<String, Long> vars) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return new P(s.replace(" ", ""), vars).parseExpr();
    }

    private static final class P {
        private final String s;
        private final Map<String, Long> vars;
        private int i;

        P(String s, Map<String, Long> vars) {
            this.s = s;
            this.vars = vars;
        }

        double parseExpr() {
            double v = parseTerm();
            while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                char op = s.charAt(i++);
                double r = parseTerm();
                v = op == '+' ? v + r : v - r;
            }
            return v;
        }

        double parseTerm() {
            double v = parseFactor();
            while (i < s.length() && s.charAt(i) == '*') {
                i++;
                v *= parseFactor();
            }
            return v;
        }

        double parseFactor() {
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                double v = parseExpr();
                if (i >= s.length() || s.charAt(i++) != ')') {
                    return Double.NaN;
                }
                return v;
            }
            if (i < s.length() && s.charAt(i) == '$') {
                i++;
                int st = i;
                while (i < s.length() && Character.isLetterOrDigit(s.charAt(i))) {
                    i++;
                }
                Long v = vars.get(s.substring(st, i));
                return v == null ? Double.NaN : v;
            }
            int st = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                i++;
            }
            if (st == i) {
                return Double.NaN;
            }
            return Double.parseDouble(s.substring(st, i));
        }
    }
}
