package tw.kurocore.kurogram.skin.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every rule the Theme Engine accepts, read from docs/skin-rules.manifest.json (the one list the app's Java,
 * the backend and CI already keep identical). The editor takes its types, ranges and allowed values from here
 * and nowhere else.
 */
public final class RuleSet {
    public enum Section {SKIN, SKIN_MINOR1, LAYOUT}

    /** A rule together with where it sits in the theme document. */
    public static final class Ref {
        public final Section section;
        public final Rule rule;
        /** Path inside the theme document: layout rules live under "layout.". */
        public final String docPath;

        Ref(Section section, Rule rule) {
            this.section = section;
            this.rule = rule;
            this.docPath = section == Section.LAYOUT ? "layout." + rule.path : rule.path;
        }
    }

    private final Map<String, Ref> byDocPath = new LinkedHashMap<>();
    private final Set<String> implementedMinor1 = new HashSet<>();
    private final Set<String> layoutNotYet = new HashSet<>();
    private final List<String> structure = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public static RuleSet fromManifest(String manifestJson) {
        Map<String, Object> m = Json.parseObject(manifestJson);
        RuleSet rs = new RuleSet();
        rs.add(Section.SKIN, (List<Object>) m.get("skin"));
        rs.add(Section.SKIN_MINOR1, (List<Object>) m.get("skinMinor1"));
        rs.add(Section.LAYOUT, (List<Object>) m.get("layout"));
        for (Object o : (List<Object>) m.get("implementedMinor1")) {
            rs.implementedMinor1.add((String) o);
        }
        for (Object o : (List<Object>) m.get("layoutNotYetSupported")) {
            rs.layoutNotYet.add((String) o);
        }
        for (Object o : (List<Object>) m.get("structure")) {
            rs.structure.add((String) o);
        }
        if (rs.byDocPath.isEmpty()) {
            throw new IllegalArgumentException("the rule manifest is empty");
        }
        return rs;
    }

    private void add(Section s, List<Object> list) {
        if (list == null) {
            throw new IllegalArgumentException("manifest section missing: " + s);
        }
        for (Object o : list) {
            Ref r = new Ref(s, Rule.parse((String) o));
            if (byDocPath.put(r.docPath, r) != null) {
                throw new IllegalArgumentException("duplicate rule path: " + r.docPath);
            }
        }
    }

    public List<Ref> all() {
        return Collections.unmodifiableList(new ArrayList<>(byDocPath.values()));
    }

    /**
     * The rule for a document path. A path under {@code night.} resolves to the rule of the same path without it: the night
     * section holds the same skin settings (never layout), listing only what differs from day.
     */
    public Ref find(String docPath) {
        Ref r = byDocPath.get(docPath);
        if (r == null && docPath.startsWith("night.")) {
            Ref day = byDocPath.get(docPath.substring("night.".length()));
            if (day != null && day.section != Section.LAYOUT) {
                return day;
            }
        }
        return r;
    }

    public boolean isKnown(String docPath) {
        return find(docPath) != null;
    }

    /** Minor-1 rules that at least one screen reads today (the manifest's own "implemented" list). */
    public boolean isImplementedMinor1(String path) {
        return implementedMinor1.contains(path);
    }

    /** Layout rules the engine knows by name but does not execute yet. */
    public boolean isLayoutNotYetSupported(String layoutPath) {
        return layoutNotYet.contains(layoutPath);
    }

    public List<String> structure() {
        return Collections.unmodifiableList(structure);
    }
}
