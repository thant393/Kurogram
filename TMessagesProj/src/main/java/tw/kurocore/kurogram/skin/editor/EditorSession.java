package tw.kurocore.kurogram.skin.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the Builder screen needs that is not drawing: the draft, which component is selected, Basic/Advanced mode,
 * language, and which settings the current selection offers. Plain Java so it is covered by the JVM tests.
 */
public final class EditorSession {
    /** Highest roadmap phase whose controls exist in the app. Settings of later phases stay hidden until built. */
    public static final int BUILT_PHASE = 3;

    public final RuleSet rules;
    public final Catalog catalog;
    public final ThemeDraft draft;
    private String lang = "en";
    private boolean advanced;
    private String scene = "chats";
    private String selected = "global";

    public EditorSession(RuleSet rules, Catalog catalog, ThemeDraft draft) {
        this.rules = rules;
        this.catalog = catalog;
        this.draft = draft;
    }

    public String lang() {
        return lang;
    }

    public void setLang(String l) {
        lang = l == null ? Catalog.DEFAULT_LANG : l;
    }

    public boolean advanced() {
        return advanced;
    }

    public void setAdvanced(boolean a) {
        advanced = a;
    }

    public String scene() {
        return scene;
    }

    public void setScene(String s) {
        scene = s;
        selected = "global";
    }

    public String selected() {
        return selected;
    }

    public void select(String componentId) {
        selected = catalog.hasComponent(componentId) ? componentId : "global";
    }

    /** Settings the current selection offers, in catalog order. Basic mode shows only the basic ones. */
    public List<Catalog.Entry> settings() {
        return settingsFor(selected);
    }

    public List<Catalog.Entry> settingsFor(String component) {
        List<Catalog.Entry> out = new ArrayList<>();
        for (Catalog.Entry e : catalog.forComponent(component)) {
            if (isShown(e)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Every setting that is built and allowed in the current mode, used by the Component Browser's "all settings" list. */
    public List<Catalog.Entry> allSettings() {
        List<Catalog.Entry> out = new ArrayList<>();
        for (Catalog.Entry e : catalog.entries()) {
            if (isShown(e)) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * False for rules the engine cannot act on today (validated but ignored): such settings are listed disabled, never silently
     * editable, so a theme cannot promise something the app will not do.
     */
    public boolean isSupported(Catalog.Entry e) {
        RuleSet.Ref ref = rules.find(e.path);
        if (ref == null) {
            return true; // structure items
        }
        Rule r = ref.rule;
        if (r.kind == Rule.Kind.NULL || (r.kind == Rule.Kind.ENUM && r.options.size() < 2)) {
            return false;
        }
        if (ref.section == RuleSet.Section.LAYOUT && rules.isLayoutNotYetSupported(e.path.substring("layout.".length()))) {
            return false;
        }
        return ref.section != RuleSet.Section.SKIN_MINOR1 || rules.isImplementedMinor1(e.path) || "schemaMinor".equals(e.path);
    }

    /** A list setting as it will be written when first customised: the rule's own smallest valid sample. */
    public List<String> listOrDefault(String path) {
        Object v = draft.get(path);
        List<String> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object o : (List<?>) v) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        RuleSet.Ref ref = rules.find(path);
        if ("layout.bottomTabs.items".equals(path)) {
            out.add("chats");
            out.add("people");
            out.add("settings");
        } else if (ref != null && ref.rule.min > 0 && ref.rule.sample() instanceof List) {
            for (Object o : (List<?>) ref.rule.sample()) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    /** Options of a list setting that are not in it yet. */
    public List<String> listAvailable(String path) {
        List<String> out = new ArrayList<>();
        RuleSet.Ref ref = rules.find(path);
        if (ref == null) {
            return out;
        }
        List<String> have = listOrDefault(path);
        for (String o : ref.rule.options) {
            if (!have.contains(o)) {
                out.add(o);
            }
        }
        return out;
    }

    private boolean isShown(Catalog.Entry e) {
        if (e.phase > BUILT_PHASE) {
            return false;
        }
        if ("schemaVersion".equals(e.path) || "schemaMinor".equals(e.path)) {
            return false; // set automatically
        }
        return advanced || e.isBasic();
    }

    /** Component ids that have at least one visible setting in the current mode: what the Component Browser lists. */
    public List<String> browsableComponents() {
        List<String> out = new ArrayList<>();
        for (String id : catalog.componentIds()) {
            if (!settingsFor(id).isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    /** Current colour at a path as 0xAARRGGBB, or {@code fallback} when the draft leaves it out. */
    public int color(String path, int fallback) {
        Object v = draft.get(path);
        RuleSet.Ref ref = rules.find(path);
        if (!(v instanceof String) || ref == null) {
            return fallback;
        }
        int c = ColorCodec.parse((String) v, ref.rule.kind);
        return c == 0 ? fallback : c;
    }

    public String setColor(String path, int argb, boolean continuation) {
        RuleSet.Ref ref = rules.find(path);
        if (ref == null) {
            return "unknown setting " + path;
        }
        return draft.set(path, ColorCodec.format(argb, ref.rule.kind), continuation);
    }

    /** Whole number setting, or {@code def} when the draft leaves it out. */
    public int intValue(String path, int def) {
        Object v = draft.get(path);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public double number(String path, double def) {
        Object v = draft.get(path);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    public boolean bool(String path, boolean def) {
        Object v = draft.get(path);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public String text(String path, String def) {
        Object v = draft.get(path);
        return v instanceof String ? (String) v : def;
    }

    /** First colour in the list that the draft sets, else {@code def}: components fall back to the tokens, as the engine does. */
    public int colorChain(int def, String... paths) {
        for (String p : paths) {
            if (draft.get(p) instanceof String) {
                int c = color(p, 0);
                if (c != 0) {
                    return c;
                }
            }
        }
        return def;
    }

    public String appearance() {
        Object v = draft.get("appearance");
        return v instanceof String ? (String) v : "auto";
    }
}
