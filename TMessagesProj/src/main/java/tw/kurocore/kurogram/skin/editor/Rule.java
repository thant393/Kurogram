package tw.kurocore.kurogram.skin.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One engine rule, parsed from its manifest text ("path|kind", e.g. "chatList.row.heightDp|int:56:96").
 * {@link #check} mirrors the engine's own validators; docs/skin-rules.vectors.json (the cases both the app
 * and the backend must agree on) is run through it by the tests, so the editor can never offer or write a
 * value the engine would reject.
 */
public final class Rule {
    public enum Kind {COLOR, ARGB, BOOL, NULL, INT, FLOAT, ENUM, LIST}

    public final String path;
    public final String raw;
    public final Kind kind;
    public final double min, max;          // INT, FLOAT; LIST = item count range
    public final List<String> options;     // ENUM, LIST

    private Rule(String path, String raw, Kind kind, double min, double max, List<String> options) {
        this.path = path;
        this.raw = raw;
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.options = options;
    }

    public static Rule parse(String text) {
        int bar = text.indexOf('|');
        if (bar <= 0) {
            throw new IllegalArgumentException("not a rule: " + text);
        }
        String path = text.substring(0, bar);
        String k = text.substring(bar + 1);
        String[] p = k.split(":");
        switch (p[0]) {
            case "col":
                return new Rule(path, text, Kind.COLOR, 0, 0, Collections.<String>emptyList());
            case "argb":
                return new Rule(path, text, Kind.ARGB, 0, 0, Collections.<String>emptyList());
            case "bool":
                return new Rule(path, text, Kind.BOOL, 0, 0, Collections.<String>emptyList());
            case "null":
                return new Rule(path, text, Kind.NULL, 0, 0, Collections.<String>emptyList());
            case "int":
                return new Rule(path, text, Kind.INT, Long.parseLong(p[1]), Long.parseLong(p[2]), Collections.<String>emptyList());
            case "float":
                return new Rule(path, text, Kind.FLOAT, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Collections.<String>emptyList());
            case "enum":
                return new Rule(path, text, Kind.ENUM, 0, 0, Collections.unmodifiableList(Arrays.asList(p[1].split(","))));
            case "list":
                return new Rule(path, text, Kind.LIST, Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                        Collections.unmodifiableList(Arrays.asList(p[1].split(","))));
            default:
                throw new IllegalArgumentException("unknown rule kind: " + text);
        }
    }

    /** Null when the value is allowed; otherwise a short reason. A JSON null is allowed only for NULL rules. */
    public String check(Object v) {
        switch (kind) {
            case COLOR:
            case ARGB:
                return v instanceof String && ((String) v).matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")
                        ? null : "must be a colour like #RRGGBB";
            case BOOL:
                return v instanceof Boolean ? null : "must be true or false";
            case NULL:
                return v == null || v == Json.NULL ? null : "is not supported yet";
            case INT:
                return (v instanceof Long || v instanceof Integer) && ((Number) v).longValue() >= min && ((Number) v).longValue() <= max
                        ? null : "must be a whole number " + (long) min + "-" + (long) max;
            case FLOAT:
                return v instanceof Number && ((Number) v).doubleValue() >= min && ((Number) v).doubleValue() <= max
                        ? null : "must be a number " + min + "-" + max;
            case ENUM:
                return v instanceof String && options.contains(v) ? null : "must be one of " + options;
            case LIST:
                if (!(v instanceof List)) {
                    return "must be a list";
                }
                List<?> l = (List<?>) v;
                if (l.size() < min || l.size() > max) {
                    return "needs " + (int) min + "-" + (int) max + " items";
                }
                Set<Object> seen = new HashSet<>();
                for (Object o : l) {
                    if (!(o instanceof String) || !options.contains(o)) {
                        return "contains an item that is not allowed: " + o;
                    }
                    if (!seen.add(o)) {
                        return "contains a duplicate: " + o;
                    }
                }
                return null;
            default:
                return "unknown rule";
        }
    }

    /** A valid example value (used by tests and as a starting value for new controls). */
    public Object sample() {
        switch (kind) {
            case COLOR:
            case ARGB:
                return "#336699";
            case BOOL:
                return Boolean.TRUE;
            case NULL:
                return Json.NULL;
            case INT:
                return (long) min;
            case FLOAT:
                return min;
            case ENUM:
                return options.get(0);
            case LIST:
                List<Object> out = new ArrayList<>();
                for (int i = 0; i < (int) Math.max(min, 1) && i < options.size(); i++) {
                    out.add(options.get(i));
                }
                return out;
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        return raw;
    }
}
