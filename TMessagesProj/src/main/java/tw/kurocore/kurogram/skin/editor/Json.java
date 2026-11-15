package tw.kurocore.kurogram.skin.editor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny JSON tree (Map / List / String / Long / Double / Boolean / {@link #NULL}) with no Android or
 * library dependency, so the theme editor's model can be unit-tested on a plain JVM. Objects keep their
 * key order. Used only inside the editor; the engine still receives ordinary JSON text.
 */
public final class Json {
    /** JSON null (kept distinct from "key absent"). */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private Json() {
    }

    // ------------------------------------------------------------------ parse

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.i != p.s.length()) {
            throw p.error("unexpected text after the document");
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("a theme must be a JSON object");
        }
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Invalid JSON at " + i + ": " + msg);
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') {
                    i++;
                } else {
                    break;
                }
            }
        }

        Object value() {
            if (i >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    return literal("true", Boolean.TRUE);
                case 'f':
                    return literal("false", Boolean.FALSE);
                case 'n':
                    return literal("null", NULL);
                default:
                    return number();
            }
        }

        Object literal(String word, Object v) {
            if (!s.startsWith(word, i)) {
                throw error("expected " + word);
            }
            i += word.length();
            return v;
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                skipWs();
                if (i >= s.length() || s.charAt(i) != '"') {
                    throw error("expected a key");
                }
                String key = string();
                skipWs();
                if (i >= s.length() || s.charAt(i) != ':') {
                    throw error("expected ':'");
                }
                i++;
                skipWs();
                m.put(key, value());
                skipWs();
                if (i >= s.length()) {
                    throw error("unterminated object");
                }
                char c = s.charAt(i++);
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; // [
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                skipWs();
                l.add(value());
                skipWs();
                if (i >= s.length()) {
                    throw error("unterminated array");
                }
                char c = s.charAt(i++);
                if (c == ']') {
                    return l;
                }
                if (c != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            i++; // opening quote
            while (true) {
                if (i >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    throw error("bad escape");
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(e);
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (i + 4 > s.length()) {
                            throw error("bad \\u escape");
                        }
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:
                        throw error("bad escape \\" + e);
                }
            }
        }

        Object number() {
            int start = i;
            boolean integral = true;
            if (i < s.length() && s.charAt(i) == '-') {
                i++;
            }
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    i++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    integral = false;
                    i++;
                } else {
                    break;
                }
            }
            String t = s.substring(start, i);
            if (t.isEmpty() || t.equals("-")) {
                throw error("unexpected character");
            }
            try {
                if (integral) {
                    try {
                        return Long.parseLong(t);
                    } catch (NumberFormatException tooBig) {
                        return new BigDecimal(t).doubleValue();
                    }
                }
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                throw error("bad number " + t);
            }
        }
    }

    // ------------------------------------------------------------------ write

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v, -1, 0);
        return sb.toString();
    }

    /** Two-space indented; what the Builder hands to the engine and the store. */
    public static String writePretty(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v, 2, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent, int depth) {
        if (v == null || v == NULL) {
            sb.append("null");
        } else if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, indent, depth + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(indent >= 0 ? ": " : ":");
                write(sb, e.getValue(), indent, depth + 1);
            }
            newline(sb, indent, depth);
            sb.append('}');
        } else if (v instanceof List) {
            List<?> l = (List<?>) v;
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            boolean first = true;
            for (Object o : l) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, indent, depth + 1);
                write(sb, o, indent, depth + 1);
            }
            newline(sb, indent, depth);
            sb.append(']');
        } else if (v instanceof String) {
            quote(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else {
                sb.append(new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString());
            }
        } else if (v instanceof Number) {
            sb.append(((Number) v).longValue());
        } else {
            throw new IllegalArgumentException("not a JSON value: " + v.getClass());
        }
    }

    private static void newline(StringBuilder sb, int indent, int depth) {
        if (indent < 0) {
            return;
        }
        sb.append('\n');
        for (int i = 0; i < depth * indent; i++) {
            sb.append(' ');
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    public static Object deepCopy(Object v) {
        if (v instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                out.put(e.getKey(), deepCopy(e.getValue()));
            }
            return out;
        }
        if (v instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object o : (List<Object>) v) {
                out.add(deepCopy(o));
            }
            return out;
        }
        return v;
    }

    /** Same meaning: object key order ignored, numbers compared by value (1 == 1.0), arrays ordered. */
    public static boolean semanticEquals(Object a, Object b) {
        if (a == null) {
            a = NULL;
        }
        if (b == null) {
            b = NULL;
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> x = (Map<?, ?>) a;
            Map<?, ?> y = (Map<?, ?>) b;
            if (x.size() != y.size()) {
                return false;
            }
            for (Map.Entry<?, ?> e : x.entrySet()) {
                if (!y.containsKey(e.getKey()) || !semanticEquals(e.getValue(), y.get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof List && b instanceof List) {
            List<?> x = (List<?>) a;
            List<?> y = (List<?>) b;
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                if (!semanticEquals(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        return a.equals(b);
    }
}
