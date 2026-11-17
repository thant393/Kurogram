package tw.kurocore.kurogram.skin.editor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The merge the store applies for styles and colourways (objects merge deeply, the patch wins, arrays and scalars replace,
 * JSON null removes the key) and its inverse. The inverse is used to recover the patches of a theme that is already
 * published: the store only returns merged skins, never the patches.
 */
public final class StyleDiff {
    private StyleDiff() {
    }

    /** {@code base} with {@code patch} merged in, as the backend's skin_styles.apply() does. Neither argument is changed. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> patch) {
        Map<String, Object> out = (Map<String, Object>) Json.deepCopy(base);
        mergeInto(out, patch);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void mergeInto(Map<String, Object> target, Map<String, Object> patch) {
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            Object v = e.getValue();
            if (v == Json.NULL) {
                target.remove(e.getKey());
            } else if (v instanceof Map && target.get(e.getKey()) instanceof Map) {
                mergeInto((Map<String, Object>) target.get(e.getKey()), (Map<String, Object>) v);
            } else {
                target.put(e.getKey(), Json.deepCopy(v));
            }
        }
    }

    /** The smallest patch that turns {@code from} into {@code to} when merged over it. Empty when they are the same. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> patch(Map<String, Object> from, Map<String, Object> to) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : to.entrySet()) {
            Object a = from.get(e.getKey()), b = e.getValue();
            if (!from.containsKey(e.getKey())) {
                out.put(e.getKey(), Json.deepCopy(b));
            } else if (a instanceof Map && b instanceof Map) {
                Map<String, Object> sub = patch((Map<String, Object>) a, (Map<String, Object>) b);
                if (!sub.isEmpty()) {
                    out.put(e.getKey(), sub);
                }
            } else if (!Json.semanticEquals(a, b)) {
                out.put(e.getKey(), Json.deepCopy(b));
            }
        }
        for (String k : from.keySet()) {
            if (!to.containsKey(k)) {
                out.put(k, Json.NULL);
            }
        }
        return out;
    }
}
