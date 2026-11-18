package tw.kurocore.kurogram.skin.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Starting palettes for a new theme: a handful of ready colour sets that read well (checked by the tests), as plain theme JSON. */
public final class Presets {
    private Presets() {
    }

    public static final class Preset {
        public final String id, name;
        public final boolean dark;
        public final String accent, background, surface, textPrimary, textSecondary, onAccent;

        Preset(String id, String name, boolean dark, String accent, String background, String surface, String textPrimary, String textSecondary, String onAccent) {
            this.id = id;
            this.name = name;
            this.dark = dark;
            this.accent = accent;
            this.background = background;
            this.surface = surface;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.onAccent = onAccent;
        }
    }

    public static List<Preset> all() {
        List<Preset> l = new ArrayList<>();
        l.add(new Preset("ocean", "Ocean", false, "#1A73C8", "#EAF4FB", "#FFFFFF", "#12304A", "#5B7388", "#FFFFFF"));
        l.add(new Preset("sunset", "Sunset", false, "#C2410C", "#FFF3E8", "#FFFFFF", "#3B1F10", "#8A5A44", "#FFFFFF"));
        l.add(new Preset("mint", "Mint", false, "#0F766E", "#E9F8F1", "#FFFFFF", "#0F2E2A", "#4F7A72", "#FFFFFF"));
        l.add(new Preset("lavender", "Lavender", false, "#6D4FD1", "#F1EDFC", "#FFFFFF", "#241B3D", "#6F6590", "#FFFFFF"));
        l.add(new Preset("rose", "Rose", false, "#C2185B", "#FDEDF3", "#FFFFFF", "#3A1020", "#8C5A6B", "#FFFFFF"));
        l.add(new Preset("midnight", "Midnight", true, "#7AA7FF", "#0F1115", "#1B1F27", "#F2F4F8", "#A3ABBA", "#0B1220"));
        return l;
    }

    /** A new theme in this palette. */
    public static String toJson(Preset p) {
        Map<String, Object> colors = new LinkedHashMap<>();
        colors.put("accent", p.accent);
        colors.put("background", p.background);
        colors.put("surface", p.surface);
        colors.put("textPrimary", p.textPrimary);
        colors.put("textSecondary", p.textSecondary);
        colors.put("onAccent", p.onAccent);
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("colors", colors);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", "my-" + p.id);
        meta.put("name", "My " + p.name);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 2L);
        root.put("meta", meta);
        root.put("appearance", p.dark ? "dark" : "light");
        root.put("tokens", tokens);
        return Json.write(root);
    }

    /** The theme being used now as the start of a new one: same content, its own id and a name that says it is a copy. Null when unreadable. */
    public static String cloneOf(String themeJson) {
        try {
            Map<String, Object> root = Json.parseObject(themeJson);
            Object m = root.get("meta");
            Map<String, Object> meta = m instanceof Map ? castMap(m) : new LinkedHashMap<String, Object>();
            Object name = meta.get("name");
            String base = name == null || String.valueOf(name).trim().isEmpty() ? "My Theme" : String.valueOf(name).trim();
            meta.put("name", base.endsWith(" (copy)") ? base : base + " (copy)");
            Object id = meta.get("id");
            String idBase = id == null || String.valueOf(id).isEmpty() ? "my-theme" : String.valueOf(id);
            meta.put("id", idBase.endsWith("-copy") ? idBase : idBase + "-copy");
            root.put("meta", meta);
            return Json.write(root);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}
