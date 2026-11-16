package tw.kurocore.kurogram.skin.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The theme being edited. It IS the theme's own JSON (no second format): the document that was loaded is kept
 * as a tree and only the exact paths a control touches are changed, so every field the Builder does not know
 * about (unknown keys, meta, future-version properties, extensions, the night overlay ...) is carried through
 * untouched. Every change is a command, so undo/redo restores the document exactly.
 *
 * Only paths the engine has a rule for can be written, and only with a value that rule accepts: a control can
 * never produce a document the engine or the backend would reject. No Android classes here, so it runs in plain
 * JVM tests.
 */
public final class ThemeDraft {
    private static final Object ABSENT = new Object();
    private static final int MAX_HISTORY = 200;

    private final RuleSet rules;
    private final Map<String, Object> root;
    private final Map<String, Object> original;
    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();
    private Edit open; // the group currently being recorded, or null

    private ThemeDraft(RuleSet rules, Map<String, Object> doc) {
        this.rules = rules;
        this.root = doc;
        this.original = (Map<String, Object>) Json.deepCopy(doc);
    }

    public static ThemeDraft fromJson(String json, RuleSet rules) {
        return new ThemeDraft(rules, Json.parseObject(json));
    }

    // ------------------------------------------------------------------ reading

    /** The value at a path, or null when it is absent. JSON null comes back as {@link Json#NULL}. */
    public Object get(String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map) || !((Map<?, ?>) cur).containsKey(part)) {
                return null;
            }
            cur = ((Map<?, ?>) cur).get(part);
        }
        return cur;
    }

    /** True when the theme sets this path itself (otherwise the app's own default applies). */
    public boolean has(String path) {
        return get(path) != null;
    }

    public Map<String, Object> toMap() {
        return (Map<String, Object>) Json.deepCopy(root);
    }

    public String toJson() {
        return Json.writePretty(root);
    }

    // ------------------------------------------------------------------ writing

    /** Sets a value. Returns null on success, otherwise why it was refused (nothing changes then). */
    public String set(String path, Object value) {
        return set(path, value, false);
    }

    /**
     * @param continuation true for the 2nd..nth step of one slider drag: merged into the previous step, so a
     *                     whole drag is one undo.
     */
    public String set(String path, Object value, boolean continuation) {
        RuleSet.Ref ref = rules.find(path);
        if (ref == null) {
            return "the theme engine has no setting called " + path;
        }
        String problem = ref.rule.check(value);
        if (problem != null) {
            return path + " " + problem;
        }
        return write(path, Json.deepCopy(value), continuation);
    }

    /**
     * The few document keys that are not engine rules but that the Builder edits: appearance (auto|light|dark) and the
     * text fields of meta. Same guarantees as {@link #set}: refused values change nothing.
     */
    public String setStructure(String path, Object value) {
        if ("appearance".equals(path)) {
            if (!("auto".equals(value) || "light".equals(value) || "dark".equals(value))) {
                return "appearance must be auto, light or dark";
            }
        } else if ("meta.name".equals(path) || "meta.id".equals(path) || "meta.description".equals(path) || "meta.author".equals(path)) {
            if (!(value instanceof String) || ((String) value).length() > 120) {
                return path + " must be text of at most 120 characters";
            }
            if (!"meta.description".equals(path) && ((String) value).trim().isEmpty()) {
                return path + " cannot be empty";
            }
        } else {
            return "the Builder does not edit " + path;
        }
        return write(path, Json.deepCopy(value), false);
    }

    /** Back to "not set": the app's own default applies again. Nothing else in the document is touched. */
    public String unset(String path) {
        if (rules.find(path) == null) {
            return "the theme engine has no setting called " + path;
        }
        return write(path, ABSENT, false);
    }

    /** List helpers for ordered settings (tabs, drawer items, hidden rows). Each is one undo step. */
    public String listAdd(String path, String item, int index) {
        List<Object> l = currentList(path);
        index = Math.max(0, Math.min(index, l.size()));
        l.add(index, item);
        return set(path, l);
    }

    public String listRemove(String path, String item) {
        List<Object> l = currentList(path);
        if (!l.remove(item)) {
            return item + " is not in " + path;
        }
        return set(path, l);
    }

    public String listMove(String path, int from, int to) {
        List<Object> l = currentList(path);
        if (from < 0 || from >= l.size() || to < 0 || to >= l.size()) {
            return "position out of range";
        }
        l.add(to, l.remove(from));
        return set(path, l);
    }

    @SuppressWarnings("unchecked")
    private List<Object> currentList(String path) {
        Object v = get(path);
        return v instanceof List ? (List<Object>) Json.deepCopy(v) : new ArrayList<>();
    }

    /** Runs several changes as one undo step (e.g. "apply a radius preset" = several keys). */
    public String group(Runnable changes) {
        if (open != null) {
            changes.run();
            return null;
        }
        open = new Edit(null, null, null, null, false);
        open.children = new ArrayList<>();
        Edit g = open;
        try {
            changes.run();
        } finally {
            open = null;
        }
        if (!g.children.isEmpty()) {
            push(g);
        }
        return null;
    }

    private String write(String path, Object value, boolean continuation) {
        String[] parts = path.split("\\.");
        Map<String, Object> parent = root;
        List<String> created = new ArrayList<>();
        StringBuilder so = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            so.append(i == 0 ? "" : ".").append(parts[i]);
            Object next = parent.get(parts[i]);
            if (next == null) {
                if (value == ABSENT) {
                    return null; // nothing to remove
                }
                Map<String, Object> fresh = new LinkedHashMap<>();
                parent.put(parts[i], fresh);
                created.add(so.toString());
                parent = fresh;
            } else if (next instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) next;
                parent = m;
            } else {
                // The document has something else here; overwriting it could destroy data we do not understand.
                for (int j = created.size() - 1; j >= 0; j--) {
                    removeContainer(created.get(j));
                }
                return so + " already holds a value that is not an object; it was left unchanged";
            }
        }
        String leaf = parts[parts.length - 1];
        Object before = parent.containsKey(leaf) ? parent.get(leaf) : ABSENT;
        if (before == ABSENT && value == ABSENT) {
            return null;
        }
        if (before != ABSENT && value != ABSENT && Json.semanticEquals(before, value)) {
            return null; // no change, no history entry
        }
        if (value == ABSENT) {
            parent.remove(leaf);
        } else {
            parent.put(leaf, value);
        }
        Edit e = new Edit(path, before, value, created, continuation);
        if (open != null) {
            open.children.add(e);
            return null;
        }
        Edit top = undo.peekLast();
        if (continuation && top != null && path.equals(top.path) && top.children == null) {
            top.after = value; // same drag: fold in
            redo.clear();
            return null;
        }
        push(e);
        return null;
    }

    private void push(Edit e) {
        undo.addLast(e);
        redo.clear();
        while (undo.size() > MAX_HISTORY) {
            undo.removeFirst();
        }
    }

    // ------------------------------------------------------------------ undo / redo

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public boolean undo() {
        Edit e = undo.pollLast();
        if (e == null) {
            return false;
        }
        revert(e);
        redo.addLast(e);
        return true;
    }

    public boolean redo() {
        Edit e = redo.pollLast();
        if (e == null) {
            return false;
        }
        reapply(e);
        undo.addLast(e);
        return true;
    }

    private void revert(Edit e) {
        if (e.children != null) {
            for (int i = e.children.size() - 1; i >= 0; i--) {
                revert(e.children.get(i));
            }
            return;
        }
        if (e.before == ABSENT) {
            removeAt(e.path);
            for (int i = e.created.size() - 1; i >= 0; i--) {
                removeContainer(e.created.get(i));
            }
        } else {
            putAt(e.path, Json.deepCopy(e.before));
        }
    }

    private void reapply(Edit e) {
        if (e.children != null) {
            for (Edit c : e.children) {
                reapply(c);
            }
            return;
        }
        if (e.after == ABSENT) {
            removeAt(e.path);
        } else {
            putAt(e.path, Json.deepCopy(e.after));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parentOf(String path, boolean create) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur.get(parts[i]);
            if (!(next instanceof Map)) {
                if (!create) {
                    return null;
                }
                next = new LinkedHashMap<String, Object>();
                cur.put(parts[i], next);
            }
            cur = (Map<String, Object>) next;
        }
        return cur;
    }

    private void putAt(String path, Object value) {
        parentOf(path, true).put(path.substring(path.lastIndexOf('.') + 1), value);
    }

    private void removeAt(String path) {
        Map<String, Object> p = parentOf(path, false);
        if (p != null) {
            p.remove(path.substring(path.lastIndexOf('.') + 1));
        }
    }

    private void removeContainer(String path) {
        Map<String, Object> p = parentOf(path, false);
        if (p == null) {
            return;
        }
        String key = path.substring(path.lastIndexOf('.') + 1);
        Object v = p.get(key);
        if (v instanceof Map && ((Map<?, ?>) v).isEmpty()) {
            p.remove(key);
        }
    }

    // ------------------------------------------------------------------ comparison

    /** True when the document means something different from the one that was loaded. */
    public boolean isDirty() {
        return !Json.semanticEquals(root, original);
    }

    /** Rule paths whose value differs from the loaded document (for "modified" markers). */
    public Set<String> changedPaths() {
        Set<String> out = new LinkedHashSet<>();
        for (RuleSet.Ref r : rules.all()) {
            Object a = valueIn(original, r.docPath);
            Object b = get(r.docPath);
            if (a == null ? b != null : (b == null || !Json.semanticEquals(a, b))) {
                out.add(r.docPath);
            }
        }
        return out;
    }

    private static Object valueIn(Map<String, Object> doc, String path) {
        Object cur = doc;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map) || !((Map<?, ?>) cur).containsKey(part)) {
                return null;
            }
            cur = ((Map<?, ?>) cur).get(part);
        }
        return cur;
    }

    // ------------------------------------------------------------------ history entry

    private static final class Edit {
        final String path;
        final Object before;
        Object after;
        final List<String> created;
        final boolean continuation;
        List<Edit> children;

        Edit(String path, Object before, Object after, List<String> created, boolean continuation) {
            this.path = path;
            this.before = before;
            this.after = after;
            this.created = created;
            this.continuation = continuation;
        }
    }
}
