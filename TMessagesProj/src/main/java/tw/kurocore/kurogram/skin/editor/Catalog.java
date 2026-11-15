package tw.kurocore.kurogram.skin.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the editor shows for each engine rule: group, who it belongs to on screen, how basic it is and its
 * words in every supported language. Types, ranges and allowed values are NOT here (they come from the rule),
 * so a rule can never be offered with a control that does not fit it. Source: docs/skin-editor.catalog.json,
 * checked against the rule manifest by Tools/check_catalog.py.
 *
 * Words: {"en": "...", "my": "..."}. A missing language falls back to English, then to the raw id, so a new
 * language is added by adding strings only; no editor code changes.
 */
public final class Catalog {
    public static final String DEFAULT_LANG = "en";

    /** Text in several languages. */
    public static final class Text {
        private final Map<String, String> byLang;

        Text(Map<String, String> byLang) {
            this.byLang = byLang;
        }

        public String get(String lang, String fallback) {
            String v = byLang.get(lang);
            if (v == null || v.isEmpty()) {
                v = byLang.get(DEFAULT_LANG);
            }
            return v == null || v.isEmpty() ? fallback : v;
        }

        public boolean has(String lang) {
            String v = byLang.get(lang);
            return v != null && !v.isEmpty();
        }
    }

    public static final class Entry {
        public final String path;          // document path, like RuleSet.Ref.docPath
        public final String group;         // id of a group in Catalog.groups
        public final String level;         // "basic" or "advanced"
        public final int phase;            // roadmap phase that delivers its control (1..5)
        public final boolean built;        // true once the control exists in the app
        public final List<String> screens; // screen ids whose preview shows it ("*" = every screen)
        public final List<String> targets; // component ids that open this setting when tapped
        public final Text label;
        public final Text description;
        public final String note;

        Entry(String path, String group, String level, int phase, boolean built, List<String> screens,
              List<String> targets, Text label, Text description, String note) {
            this.path = path;
            this.group = group;
            this.level = level;
            this.phase = phase;
            this.built = built;
            this.screens = screens;
            this.targets = targets;
            this.label = label;
            this.description = description;
            this.note = note;
        }

        public boolean isBasic() {
            return "basic".equals(level);
        }
    }

    public final int version;
    public final List<String> languages;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Text> groups = new LinkedHashMap<>();
    private final Map<String, Text> components = new LinkedHashMap<>();
    private final Map<String, Text> screens = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public static Catalog fromJson(String json) {
        Map<String, Object> m = Json.parseObject(json);
        Catalog c = new Catalog(m);
        return c;
    }

    @SuppressWarnings("unchecked")
    private Catalog(Map<String, Object> m) {
        version = ((Number) m.get("catalogVersion")).intValue();
        List<String> langs = new ArrayList<>();
        for (Object o : (List<Object>) m.get("languages")) {
            langs.add((String) o);
        }
        languages = Collections.unmodifiableList(langs);
        readTexts((Map<String, Object>) m.get("groups"), groups);
        readTexts((Map<String, Object>) m.get("components"), components);
        readTexts((Map<String, Object>) m.get("screens"), screens);
        for (Object o : (List<Object>) m.get("entries")) {
            Map<String, Object> e = (Map<String, Object>) o;
            String path = (String) e.get("path");
            Entry entry = new Entry(path, (String) e.get("group"), (String) e.get("level"),
                    ((Number) e.get("phase")).intValue(), Boolean.TRUE.equals(e.get("built")),
                    strings(e.get("screens")), strings(e.get("targets")),
                    text(e.get("label")), text(e.get("description")), (String) e.get("note"));
            if (entries.put(path, entry) != null) {
                throw new IllegalArgumentException("duplicate catalog entry: " + path);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void readTexts(Map<String, Object> src, Map<String, Text> out) {
        if (src == null) {
            return;
        }
        for (Map.Entry<String, Object> e : src.entrySet()) {
            Map<String, Object> v = (Map<String, Object>) e.getValue();
            out.put(e.getKey(), text(v.get("label")));
        }
    }

    @SuppressWarnings("unchecked")
    private static Text text(Object o) {
        Map<String, String> m = new LinkedHashMap<>();
        if (o instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (e.getValue() instanceof String) {
                    m.put(e.getKey(), (String) e.getValue());
                }
            }
        }
        return new Text(m);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object s : (List<Object>) o) {
                out.add((String) s);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public Entry entry(String docPath) {
        return entries.get(docPath);
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public String groupLabel(String groupId, String lang) {
        Text t = groups.get(groupId);
        return t == null ? groupId : t.get(lang, groupId);
    }

    public String componentLabel(String componentId, String lang) {
        Text t = components.get(componentId);
        return t == null ? componentId : t.get(lang, componentId);
    }

    public String screenLabel(String screenId, String lang) {
        Text t = screens.get(screenId);
        return t == null ? screenId : t.get(lang, screenId);
    }

    public boolean hasGroup(String id) {
        return groups.containsKey(id);
    }

    public boolean hasComponent(String id) {
        return components.containsKey(id);
    }

    public boolean hasScreen(String id) {
        return screens.containsKey(id);
    }

    /** Entries a tap on this component should open. */
    public List<Entry> forComponent(String componentId) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.targets.contains(componentId)) {
                out.add(e);
            }
        }
        return out;
    }
}
