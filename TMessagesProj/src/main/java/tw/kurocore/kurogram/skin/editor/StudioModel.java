package tw.kurocore.kurogram.skin.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Theme Studio's logic that has no Android in it, so it is tested on a plain JVM: which inspector section a setting belongs
 * to, the layer tree of a screen, resetting one component, copying and pasting a style, and finding the component that owns a
 * readability warning. Everything works on the existing draft through {@link EditorSession}; nothing here writes JSON itself.
 */
public final class StudioModel {
    private StudioModel() {
    }

    public enum Section {
        APPEARANCE, LAYOUT, TYPOGRAPHY, ADVANCED
    }

    /** Words of the section tabs live in {@link BuilderText} as "section.*". */
    public static String sectionKey(Section s) {
        return "section." + s.name().toLowerCase(java.util.Locale.US);
    }

    /**
     * Colours, gradients, shadows and see-through values are Appearance; sizes, spacing, shapes and ordered lists are Layout;
     * font settings are Typography; everything else (switches, modes, animations, assets) is Advanced.
     */
    public static Section sectionOf(Catalog.Entry e, Rule rule) {
        String p = e.path.toLowerCase(java.util.Locale.US);
        if (p.contains("typography") || p.contains("font") || p.contains("textsize") || p.contains("linespacing")
                || p.contains("letterspacing") || p.contains("textalign") || p.contains("bold") || e.group.equals("typography")) {
            return Section.TYPOGRAPHY;
        }
        if (rule == null) {
            return Section.ADVANCED;
        }
        switch (rule.kind) {
            case COLOR:
            case ARGB:
                return Section.APPEARANCE;
            case LIST:
                return Section.LAYOUT;
            case INT:
            case FLOAT:
                if (p.contains("blur") || p.contains("opacity") || p.contains("alpha") || p.contains("elevation") || p.contains("shadow")
                        || p.contains("stroke") || p.contains("border") || e.group.equals("elevation") || e.group.equals("gradient")) {
                    return Section.APPEARANCE;
                }
                return Section.LAYOUT;
            default:
                if (e.group.equals("gradient") || e.group.equals("elevation") || e.group.equals("colors")) {
                    return Section.APPEARANCE;
                }
                if (e.group.startsWith("layout.") || e.group.equals("shapes") || e.group.equals("spacing")) {
                    return Section.LAYOUT;
                }
                return Section.ADVANCED;
        }
    }

    /** The sections that have something to show, in tab order. */
    public static List<Section> sectionsOf(EditorSession s, List<Catalog.Entry> entries) {
        Set<Section> found = new LinkedHashSet<>();
        for (Catalog.Entry e : entries) {
            RuleSet.Ref ref = s.rules.find(e.path);
            found.add(sectionOf(e, ref == null ? null : ref.rule));
        }
        List<Section> out = new ArrayList<>();
        for (Section k : Section.values()) {
            if (found.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ layers

    public static final class Node {
        public final String id;
        public final List<Node> kids = new ArrayList<>();
        public boolean editable;

        Node(String id) {
            this.id = id;
        }
    }

    /**
     * The layer tree of a screen from the components its preview shows (their stable ids). A dotted id ("chatRow.avatar") is the
     * child of the nearest shown ancestor; everything else hangs under the screen background. A layer is editable when it has at
     * least one setting in the current mode; the others are listed but cannot be edited.
     */
    public static Node layers(EditorSession s, List<String> shown) {
        Map<String, Node> byId = new LinkedHashMap<>();
        Node root = new Node("screenBackground");
        root.editable = !s.settingsFor("screenBackground").isEmpty();
        byId.put(root.id, root);
        for (String id : shown) {
            if (!byId.containsKey(id)) {
                Node n = new Node(id);
                n.editable = !s.settingsFor(id).isEmpty();
                byId.put(id, n);
            }
        }
        for (Node n : byId.values()) {
            if (n == root) {
                continue;
            }
            Node parent = root;
            String id = n.id;
            int dot;
            while ((dot = id.lastIndexOf('.')) > 0) {
                id = id.substring(0, dot);
                if (byId.containsKey(id)) {
                    parent = byId.get(id);
                    break;
                }
            }
            parent.kids.add(n);
        }
        return root;
    }

    // ------------------------------------------------------------------ reset, copy, paste

    private static List<Catalog.Entry> editable(EditorSession s, String component) {
        List<Catalog.Entry> out = new ArrayList<>();
        for (Catalog.Entry e : s.catalog.forComponent(component)) {
            if (e.phase <= EditorSession.BUILT_PHASE && s.isSupported(e) && s.rules.find(e.path) != null
                    && !"schemaVersion".equals(e.path) && !"schemaMinor".equals(e.path) && !e.group.equals("structure")) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Removes the values this component's own settings have in the layer being edited, as one undo step. A setting shared with
     * other components (an accent colour) is shared on purpose: resetting it from here resets it there too, which is why the caller
     * asks first; settings of other components are never touched. Returns how many values were removed.
     */
    public static int resetComponent(final EditorSession s, final String component, final Section only) {
        final int[] n = {0};
        s.active().group(new Runnable() {
            @Override
            public void run() {
                for (Catalog.Entry e : editable(s, component)) {
                    RuleSet.Ref ref = s.rules.find(e.path);
                    if (only != null && sectionOf(e, ref.rule) != only) {
                        continue;
                    }
                    if (s.ownValue(e.path) != null && s.unsetValue(e.path) == null) {
                        n[0]++;
                    }
                }
            }
        });
        return n[0];
    }

    /** Values of this component's settings that are set in the layer being edited, by path. */
    public static Map<String, Object> copyStyle(EditorSession s, String component) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Catalog.Entry e : editable(s, component)) {
            Object v = s.ownValue(e.path);
            if (v != null && !(e.group.startsWith("layout."))) {
                out.put(e.path, v);
            }
        }
        return out;
    }

    /**
     * Applies copied values to another component, but only those settings the target really has; the rule checks of the draft
     * still apply. Returns how many were applied (0 = nothing in common).
     */
    public static int pasteStyle(final EditorSession s, final String component, final Map<String, Object> style) {
        final Set<String> mine = new LinkedHashSet<>();
        for (Catalog.Entry e : editable(s, component)) {
            mine.add(e.path);
        }
        final int[] n = {0};
        s.active().group(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Object> kv : style.entrySet()) {
                    if (mine.contains(kv.getKey()) && s.setValue(kv.getKey(), kv.getValue()) == null) {
                        n[0]++;
                    }
                }
            }
        });
        return n[0];
    }

    // ------------------------------------------------------------------ warnings

    /** The component whose inspector holds this setting: the first target of its catalog entry, or the whole app. */
    public static String componentOfPath(Catalog c, String path) {
        Catalog.Entry e = c.entry(path);
        return e == null || e.targets.isEmpty() ? "global" : e.targets.get(0);
    }

    /** Black or white text, whichever reads better on this background (a suggestion; the person decides). */
    public static int suggestText(int background) {
        return Contrast.ratio(0xFF000000, background) >= Contrast.ratio(0xFFFFFFFF, background) ? 0xFF000000 : 0xFFFFFFFF;
    }
}
