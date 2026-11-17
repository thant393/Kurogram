package tw.kurocore.kurogram.skin.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The styles and colourways of a theme (the "styles" list the store keeps next to the skin file). A style's content and a
 * colourway's patch are partial skins; each is edited through its own {@link ThemeDraft}, so they get the same rule checks,
 * undo and preservation as the main file. Only what a person actually sets ends up in a patch.
 */
public final class StyleSet {
    public static final int MAX_STYLES = 8, MAX_COLORWAYS = 12;

    public static final class Way {
        public String id, name;
        public final String[] swatch = new String[3];
        final ThemeDraft patch;

        Way(String id, String name, ThemeDraft patch) {
            this.id = id;
            this.name = name;
            this.patch = patch;
        }
    }

    public static final class Style {
        public String id, name;
        final ThemeDraft content;
        public final List<Way> ways = new ArrayList<>();

        Style(String id, String name, ThemeDraft content) {
            this.id = id;
            this.name = name;
            this.content = content;
        }
    }

    private final RuleSet rules;
    private final List<Style> styles = new ArrayList<>();
    private Object original = Json.parse("[]");

    public StyleSet(RuleSet rules) {
        this.rules = rules;
    }

    public List<Style> styles() {
        return styles;
    }

    public boolean isEmpty() {
        return styles.isEmpty();
    }

    public Style style(String id) {
        for (Style s : styles) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    public Way way(String styleId, String wayId) {
        Style s = style(styleId);
        if (s != null) {
            for (Way w : s.ways) {
                if (w.id.equals(wayId)) {
                    return w;
                }
            }
        }
        return null;
    }

    /** The draft a person edits when this style (wayId null) or colourway is selected; null when it does not exist. */
    public ThemeDraft draftFor(String styleId, String wayId) {
        Style s = style(styleId);
        if (s == null) {
            return null;
        }
        if (wayId == null) {
            return s.content;
        }
        Way w = way(styleId, wayId);
        return w == null ? null : w.patch;
    }

    private static String slug(String name, String fallback) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty()) {
            s = fallback;
        }
        return s.length() > 20 ? s.substring(0, 20) : s;
    }

    private String uniqueId(String base, List<String> taken) {
        String id = base;
        for (int n = 2; taken.contains(id); n++) {
            id = base + "-" + n;
        }
        return id;
    }

    private ThemeDraft empty() {
        return ThemeDraft.fromJson("{}", rules);
    }

    /** @return the new style's id, or null when the limit is reached */
    public String addStyle(String name) {
        if (styles.size() >= MAX_STYLES) {
            return null;
        }
        List<String> taken = new ArrayList<>();
        for (Style s : styles) {
            taken.add(s.id);
        }
        String id = uniqueId(slug(name, "style"), taken);
        styles.add(new Style(id, clean(name, id), empty()));
        return id;
    }

    public String addColorway(String styleId, String name) {
        Style s = style(styleId);
        if (s == null || s.ways.size() >= MAX_COLORWAYS) {
            return null;
        }
        List<String> taken = new ArrayList<>();
        for (Way w : s.ways) {
            taken.add(w.id);
        }
        String id = uniqueId(slug(name, "look"), taken);
        s.ways.add(new Way(id, clean(name, id), empty()));
        return id;
    }

    private static String clean(String name, String fallback) {
        String n = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty()) {
            n = fallback;
        }
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    public boolean rename(String styleId, String wayId, String name) {
        if (wayId == null) {
            Style s = style(styleId);
            if (s == null) {
                return false;
            }
            s.name = clean(name, s.id);
            return true;
        }
        Way w = way(styleId, wayId);
        if (w == null) {
            return false;
        }
        w.name = clean(name, w.id);
        return true;
    }

    public boolean remove(String styleId, String wayId) {
        Style s = style(styleId);
        if (s == null) {
            return false;
        }
        if (wayId == null) {
            return styles.remove(s);
        }
        Way w = way(styleId, wayId);
        return w != null && s.ways.remove(w);
    }

    public boolean has(String styleId, String wayId) {
        return wayId == null ? style(styleId) != null : way(styleId, wayId) != null;
    }

    // ------------------------------------------------------------------ JSON

    public String toJson() {
        return Json.write(toList());
    }

    private List<Object> toList() {
        List<Object> out = new ArrayList<>();
        for (Style s : styles) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id);
            m.put("name", s.name);
            Map<String, Object> c = s.content.toMap();
            if (!c.isEmpty()) {
                m.put("content", c);
            }
            List<Object> ways = new ArrayList<>();
            for (Way w : s.ways) {
                Map<String, Object> wm = new LinkedHashMap<>();
                wm.put("id", w.id);
                wm.put("name", w.name);
                String[] keys = {"swatch", "swatch2", "swatch3"};
                for (int i = 0; i < 3; i++) {
                    if (w.swatch[i] != null) {
                        wm.put(keys[i], w.swatch[i]);
                    }
                }
                Map<String, Object> p = w.patch.toMap();
                if (!p.isEmpty()) {
                    wm.put("patch", p);
                }
                ways.add(wm);
            }
            if (!ways.isEmpty()) {
                m.put("colorways", ways);
            }
            out.add(m);
        }
        return out;
    }

    /** Loads the "styles" list; unknown keys inside a patch are kept (the drafts never touch what they do not edit). */
    @SuppressWarnings("unchecked")
    public static StyleSet fromJson(String json, RuleSet rules) {
        StyleSet set = new StyleSet(rules);
        Object parsed = Json.parse(json == null || json.trim().isEmpty() ? "[]" : json);
        if (!(parsed instanceof List)) {
            throw new IllegalArgumentException("styles must be a list");
        }
        for (Object o : (List<Object>) parsed) {
            Map<String, Object> m = (Map<String, Object>) o;
            String id = String.valueOf(m.get("id"));
            Object content = m.get("content");
            Style s = new Style(id, m.get("name") instanceof String ? (String) m.get("name") : id,
                    content instanceof Map ? new ThemeDraft(rules, (Map<String, Object>) Json.deepCopy(content)) : set.empty());
            Object ways = m.get("colorways");
            if (ways instanceof List) {
                for (Object wo : (List<Object>) ways) {
                    Map<String, Object> wm = (Map<String, Object>) wo;
                    Object patch = wm.get("patch");
                    Way w = new Way(String.valueOf(wm.get("id")), wm.get("name") instanceof String ? (String) wm.get("name") : String.valueOf(wm.get("id")),
                            patch instanceof Map ? new ThemeDraft(rules, (Map<String, Object>) Json.deepCopy(patch)) : set.empty());
                    String[] keys = {"swatch", "swatch2", "swatch3"};
                    for (int i = 0; i < 3; i++) {
                        if (wm.get(keys[i]) instanceof String) {
                            w.swatch[i] = (String) wm.get(keys[i]);
                        }
                    }
                    s.ways.add(w);
                }
            }
            set.styles.add(s);
        }
        set.original = Json.parse(set.toJson());
        return set;
    }

    /** True when anything differs from what was loaded (or from nothing, for a new set). */
    public boolean isDirty() {
        return !Json.semanticEquals(original, Json.parse(toJson()));
    }
}
