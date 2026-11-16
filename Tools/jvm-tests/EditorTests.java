import tw.kurocore.kurogram.skin.editor.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Plain-main test runner (no JUnit): run via Tools/jvm-tests/run.sh. Exit code 1 on any failure. */
public class EditorTests {
    static int passed, failed;
    static Path root;

    static void check(boolean ok, String name) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static String read(String rel) throws Exception {
        return new String(Files.readAllBytes(root.resolve(rel)), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath();
        String manifest = read("docs/skin-rules.manifest.json");
        RuleSet rules = RuleSet.fromManifest(manifest);
        Map<String, Object> man = Json.parseObject(manifest);

        // ---- rule set matches the manifest counts
        int expected = 0;
        for (String k : new String[]{"skin", "skinMinor1", "layout"}) expected += ((List<Object>) man.get(k)).size();
        check(rules.all().size() == expected, "RuleSet size " + rules.all().size() + " vs " + expected);

        // ---- every shared vector
        List<Object> vectors = (List<Object>) Json.parseObject(read("docs/skin-rules.vectors.json")).get("vectors");
        int vn = 0;
        for (Object o : vectors) {
            Map<String, Object> v = (Map<String, Object>) o;
            Rule r = Rule.parse((String) v.get("rule"));
            boolean valid = (Boolean) v.get("valid");
            Object val = v.get("value");
            if (val == null) val = Json.NULL;
            boolean got = r.check(val) == null;
            check(got == valid, "vector " + v.get("rule") + " value=" + v.get("value") + " expected " + valid);
            vn++;
        }
        check(vn >= 100, "vector count " + vn);

        // ---- json: semantic equality ignores key order, compares numbers by value, keeps array order
        check(Json.semanticEquals(Json.parse("{\"a\":1,\"b\":[1,2]}"), Json.parse("{\"b\":[1,2],\"a\":1.0}")), "semantic equal key order / number");
        check(!Json.semanticEquals(Json.parse("{\"b\":[1,2]}"), Json.parse("{\"b\":[2,1]}")), "array order matters");
        check(Json.semanticEquals(Json.parse(Json.writePretty(Json.parse("{\"x\":null,\"s\":\"မြန်မာ\",\"q\":\"a\\\"b\\n\"}"))),
                Json.parse("{\"x\":null,\"s\":\"မြန်မာ\",\"q\":\"a\\\"b\\n\"}")), "write/parse round trip incl. null, Burmese, escapes");

        // ---- catalog
        Catalog cat = Catalog.fromJson(read("docs/skin-editor.catalog.json"));
        for (RuleSet.Ref ref : rules.all()) check(cat.entry(ref.docPath) != null, "catalog has " + ref.docPath);
        Catalog.Entry any = cat.entries().get(0);
        check(any.label.get("my", "?").length() > 0 && any.label.get("zz", "?").equals(any.label.get("en", "?")), "locale fallback lang->en");
        check(cat.componentLabel("chatRow", "my").length() > 0, "component label my");
        check(cat.forComponent("chatRow").size() > 0, "chatRow has settings");

        // ---- round trip of every shipped theme: load -> toJson -> semantic equal; no edits => not dirty
        List<String> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root.resolve("docs/skin-presets"), "*.json")) {
            for (Path p : ds) files.add(root.relativize(p).toString());
        }
        files.add("docs/theme-template.json");
        for (String f : files) {
            String src = read(f);
            ThemeDraft d = ThemeDraft.fromJson(src, rules);
            check(Json.semanticEquals(Json.parse(src), Json.parse(d.toJson())), "round trip " + f);
            check(!d.isDirty(), "not dirty " + f);
        }

        // ---- preservation: unknown, future, metadata, extensions, night survive edits
        String themed = "{\"schemaVersion\":2,\"meta\":{\"id\":\"t\",\"name\":\"T\",\"extra\":{\"k\":[1,2]}},"
                + "\"tokens\":{\"colors\":{\"accent\":\"#0866FF\",\"futureColor\":\"#123456\"},\"futureGroup\":{\"a\":1}},"
                + "\"x-vendor\":{\"keep\":true},\"night\":{\"tokens\":{\"colors\":{\"accent\":\"#112233\"}}},\"futureTop\":[1,\"two\",null]}";
        ThemeDraft d = ThemeDraft.fromJson(themed, rules);
        check(d.set("tokens.colors.accent", "#FF0000") == null, "set accent ok");
        d.set("tokens.shapes.cardRadiusDp", 12L);
        Map<String, Object> out = d.toMap();
        check(Json.semanticEquals(out.get("x-vendor"), Json.parse("{\"keep\":true}")), "x-vendor kept");
        check(Json.semanticEquals(out.get("futureTop"), Json.parse("[1,\"two\",null]")), "futureTop kept");
        check(Json.semanticEquals(out.get("night"), Json.parse("{\"tokens\":{\"colors\":{\"accent\":\"#112233\"}}}")), "night kept");
        check(Json.semanticEquals(((Map<String, Object>) out.get("meta")).get("extra"), Json.parse("{\"k\":[1,2]}")), "meta extra kept");
        check("#123456".equals(d.get("tokens.colors.futureColor")), "unknown sibling colour kept");
        check(Json.semanticEquals(d.get("tokens.futureGroup"), Json.parse("{\"a\":1}")), "unknown group kept");
        check(d.isDirty() && d.changedPaths().contains("tokens.colors.accent"), "changedPaths lists edit");

        // ---- refusals
        ThemeDraft r = ThemeDraft.fromJson(themed, rules);
        String before = r.toJson();
        check(r.set("tokens.colors.nope", "#FFFFFF") != null, "unknown path refused");
        check(r.set("tokens.colors.accent", "red") != null, "invalid colour refused");
        check(r.set("tokens.shapes.cardRadiusDp", 9999L) != null, "out of range refused");
        check(r.set("x-vendor.keep", true) != null, "unknown path under extension refused");
        check(Json.semanticEquals(Json.parse(before), Json.parse(r.toJson())) && !r.canUndo(), "refusals change nothing, no undo step");

        // ---- undo / redo exactness
        ThemeDraft u = ThemeDraft.fromJson(themed, rules);
        String orig = u.toJson();
        u.set("tokens.colors.accent", "#00FF00");
        u.set("tokens.shapes.cardRadiusDp", 8L);
        u.set("tokens.shapes.buttonRadiusDp", 6L);
        String edited = u.toJson();
        check(u.undo() && u.undo() && u.undo() && !u.canUndo(), "three undos");
        check(Json.semanticEquals(Json.parse(orig), Json.parse(u.toJson())), "undo restores exactly (incl. created containers)");
        check(u.redo() && u.redo() && u.redo() && !u.canRedo(), "three redos");
        check(Json.semanticEquals(Json.parse(edited), Json.parse(u.toJson())), "redo restores edit");

        // ---- drag coalescing: many continuation edits = one undo step
        ThemeDraft c = ThemeDraft.fromJson(themed, rules);
        c.set("tokens.shapes.cardRadiusDp", 1L, false);
        for (long i = 2; i <= 20; i++) c.set("tokens.shapes.cardRadiusDp", i, true);
        check(Long.valueOf(20).equals(c.get("tokens.shapes.cardRadiusDp")), "drag final value");
        c.undo();
        check(!c.has("tokens.shapes.cardRadiusDp") && !c.canUndo(), "one undo reverts whole drag");

        // ---- group = one undo step
        ThemeDraft g = ThemeDraft.fromJson(themed, rules);
        g.group(() -> {
            g.set("tokens.shapes.cardRadiusDp", 4L);
            g.set("tokens.shapes.buttonRadiusDp", 4L);
            g.set("tokens.shapes.inputRadiusDp", 4L);
        });
        g.undo();
        check(!g.canUndo() && !g.has("tokens.shapes.cardRadiusDp") && !g.has("tokens.shapes.buttonRadiusDp"), "group undone as one step");

        // ---- unset
        ThemeDraft s = ThemeDraft.fromJson(themed, rules);
        s.unset("tokens.colors.accent");
        check(!s.has("tokens.colors.accent") && s.has("tokens.colors.futureColor"), "unset removes only that key");
        s.undo();
        check("#0866FF".equals(s.get("tokens.colors.accent")), "unset undone");

        // ---- every rule: its sample value is accepted and round-trips; undo restores the document
        int sampled = 0;
        for (RuleSet.Ref ref : rules.all()) {
            if (ref.rule.kind == Rule.Kind.NULL) continue;
            ThemeDraft t = ThemeDraft.fromJson("{\"schemaVersion\":2,\"meta\":{\"id\":\"s\",\"name\":\"S\"}}", rules);
            Object sample = ref.rule.sample();
            String err = t.set(ref.docPath, sample);
            check(err == null, "sample accepted " + ref.docPath + " (" + err + ")");
            if (err != null) continue;
            Object back = Json.parse(t.toJson());
            Map<String, Object> m = (Map<String, Object>) back;
            Object cur = m;
            for (String seg : ref.docPath.split("\\.")) cur = ((Map<String, Object>) cur).get(seg);
            check(Json.semanticEquals(Json.deepCopy(sample), cur), "sample round trip " + ref.docPath);
            t.undo();
            check(Json.semanticEquals(Json.parse("{\"schemaVersion\":2,\"meta\":{\"id\":\"s\",\"name\":\"S\"}}"), Json.parse(t.toJson())), "undo clean " + ref.docPath);
            sampled++;
        }
        check(sampled > 150, "sampled rules " + sampled);


        // ---- colour codec
        check(ColorCodec.parse("#112233", Rule.Kind.COLOR) == 0xFF112233, "col rgb");
        check(ColorCodec.parse("#11223380", Rule.Kind.COLOR) == 0x80112233, "col rgba -> argb");
        check(ColorCodec.parse("#80112233", Rule.Kind.ARGB) == 0x80112233, "argb passthrough");
        check(ColorCodec.parse("red", Rule.Kind.COLOR) == 0, "bad colour -> 0");
        for (int c0 : new int[]{0xFF0866FF, 0x80112233, 0x01FFFFFF}) {
            check(ColorCodec.parse(ColorCodec.format(c0, Rule.Kind.COLOR), Rule.Kind.COLOR) == c0, "col round trip " + Integer.toHexString(c0));
            check(ColorCodec.parse(ColorCodec.format(c0, Rule.Kind.ARGB), Rule.Kind.ARGB) == c0, "argb round trip " + Integer.toHexString(c0));
        }

        // ---- structure edits
        ThemeDraft st = ThemeDraft.fromJson(themed, rules);
        check(st.setStructure("appearance", "dark") == null && "dark".equals(st.get("appearance")), "appearance set");
        check(st.setStructure("appearance", "blue") != null, "bad appearance refused");
        check(st.setStructure("meta.name", "New") == null && Json.semanticEquals(((Map<String, Object>) st.toMap().get("meta")).get("extra"), Json.parse("{\"k\":[1,2]}")), "meta.name keeps meta extras");
        check(st.setStructure("meta.name", "") != null, "empty name refused");
        check(st.setStructure("tokens.colors.accent", "#FFFFFF") != null, "structure refuses rule paths");
        st.undo();
        st.undo();
        check(Json.semanticEquals(Json.parse(themed), Json.parse(st.toJson())), "structure edits undo exactly");

        // ---- session: Basic / Advanced, selection, built phase
        EditorSession es = new EditorSession(rules, cat, ThemeDraft.fromJson(themed, rules));
        check(es.settingsFor("global").size() > 0, "global has settings");
        int basicN = es.allSettings().size();
        es.setAdvanced(true);
        check(es.allSettings().size() > basicN, "advanced shows more");
        for (Catalog.Entry e : es.allSettings()) check(e.phase <= EditorSession.BUILT_PHASE, "only built phases shown " + e.path);
        es.select("nope");
        check("global".equals(es.selected()), "unknown component falls back to global");
        check(es.setColor("tokens.colors.accent", 0xFF010203, false) == null && es.color("tokens.colors.accent", 0) == 0xFF010203, "session colour set/get");
        check(es.setColor("tokens.colors.overlay", 0x80000000, false) == null && "#80000000".equals(es.draft.get("tokens.colors.overlay")), "overlay written as argb");
        check(es.color("tokens.colors.divider", 0xFFABCDEF) == 0xFFABCDEF, "fallback when unset");
        for (String comp : es.browsableComponents()) check(cat.componentLabel(comp, "my").length() > 0, "browser label " + comp);


        // ---- every numeric rule: its bounds are accepted, one step outside is refused (what the sliders rely on)
        int ranged = 0;
        for (RuleSet.Ref ref : rules.all()) {
            if (ref.rule.kind != Rule.Kind.INT && ref.rule.kind != Rule.Kind.FLOAT) continue;
            ThemeDraft nd = ThemeDraft.fromJson("{\"schemaVersion\":2,\"meta\":{\"id\":\"n\",\"name\":\"N\"}}", rules);
            boolean isInt = ref.rule.kind == Rule.Kind.INT;
            Object lo = isInt ? (Object) Long.valueOf((long) ref.rule.min) : (Object) Double.valueOf(ref.rule.min);
            Object hi = isInt ? (Object) Long.valueOf((long) ref.rule.max) : (Object) Double.valueOf(ref.rule.max);
            Object below = isInt ? (Object) Long.valueOf((long) ref.rule.min - 1) : (Object) Double.valueOf(ref.rule.min - 0.5);
            Object above = isInt ? (Object) Long.valueOf((long) ref.rule.max + 1) : (Object) Double.valueOf(ref.rule.max + 0.5);
            check(nd.set(ref.docPath, lo) == null && nd.set(ref.docPath, hi) == null, "bounds accepted " + ref.docPath);
            check(nd.set(ref.docPath, below) != null && nd.set(ref.docPath, above) != null, "outside refused " + ref.docPath);
            ranged++;
        }
        check(ranged > 60, "numeric rules " + ranged);

        // ---- session value helpers fall back like the engine
        EditorSession vs = new EditorSession(rules, cat, ThemeDraft.fromJson(themed, rules));
        check(vs.intValue("tokens.shapes.cardRadiusDp", 14) == 14, "int fallback");
        vs.draft.set("tokens.shapes.cardRadiusDp", 20L);
        check(vs.intValue("tokens.shapes.cardRadiusDp", 14) == 20, "int value");
        vs.draft.set("tokens.typography.lineHeightMultiplier", 1.5);
        check(vs.number("tokens.typography.lineHeightMultiplier", 1) == 1.5, "float value");
        check(vs.text("components.button.style", "filled").equals("filled"), "text fallback");
        check(vs.colorChain(7, "components.button.background", "tokens.colors.accent") == 0xFF0866FF, "chain falls to token");
        vs.draft.set("components.button.background", "#010203");
        check(vs.colorChain(7, "components.button.background", "tokens.colors.accent") == 0xFF010203, "chain prefers component");
        vs.setAdvanced(true);
        check(vs.settingsFor("button").size() >= 4, "button has component settings in advanced");
        check(vs.settingsFor("bubble.in").size() >= 3, "bubble has settings");
        check(BuilderText.complete(), "builder text has en and my for every key");
        check(BuilderText.get("my", "undo").length() > 0 && BuilderText.get("zz", "undo").equals(BuilderText.get("en", "undo")), "builder text fallback");

        System.out.println("editor tests: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
