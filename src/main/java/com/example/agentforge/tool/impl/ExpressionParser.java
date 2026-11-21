package com.example.agentforge.tool.impl;

 

final class ExpressionParser {

    private final String text;
    private int pos;

    private ExpressionParser(String text) {
        this.text = text;
    }

    static double parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        ExpressionParser parser = new ExpressionParser(expression);
        double value = parser.parseExpression();
        parser.skipWhitespace();
        if (parser.pos != parser.text.length()) {
            throw new IllegalArgumentException("表达式包含非法字符，位置 " + parser.pos);
        }
        return value;
    }

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipWhitespace();
            if (pos >= text.length()) {
                return value;
            }
            char operator = text.charAt(pos);
            if (operator == '+') {
                pos++;
                value += parseTerm();
            } else if (operator == '-') {
                pos++;
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() {
        double value = parseFactor();
        while (true) {
            skipWhitespace();
            if (pos >= text.length()) {
                return value;
            }
            char operator = text.charAt(pos);
            if (operator == '*') {
                pos++;
                value *= parseFactor();
            } else if (operator == '/') {
                pos++;
                double divisor = parseFactor();
                if (divisor == 0d) {
                    throw new IllegalArgumentException("除数不能为零");
                }
                value /= divisor;
            } else {
                return value;
            }
        }
    }

    private double parseFactor() {
        skipWhitespace();
        if (pos >= text.length()) {
            throw new IllegalArgumentException("表达式不完整");
        }
        char current = text.charAt(pos);
        if (current == '+' || current == '-') {
            pos++;
            double value = parseFactor();
            return current == '-' ? -value : value;
        }
        if (current == '(') {
            pos++;
            double value = parseExpression();
            skipWhitespace();
            if (pos >= text.length() || text.charAt(pos) != ')') {
                throw new IllegalArgumentException("缺少右括号");
            }
            pos++;
            return value;
        }
        return parseNumber();
    }

    private double parseNumber() {
        skipWhitespace();
        int start = pos;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isDigit(c) || c == '.') {
                pos++;
            } else {
                break;
            }
        }
        if (start == pos) {
            throw new IllegalArgumentException("期望数字，位置 " + pos);
        }
        try {
            return Double.parseDouble(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法数字: " + text.substring(start, pos));
        }
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}
