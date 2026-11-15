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

        System.out.println("editor tests: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
