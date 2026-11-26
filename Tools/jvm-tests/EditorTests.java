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
        check(any.label.get("en", "?").length() > 0 && any.label.get("zz", "?").equals(any.label.get("en", "?")), "locale fallback lang->en");
        check(cat.componentLabel("chatRow", "en").length() > 0, "component label my");
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
        for (String comp : es.browsableComponents()) check(cat.componentLabel(comp, "en").length() > 0, "browser label " + comp);


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

        // ---- list editing and unsupported settings
        EditorSession ls = new EditorSession(rules, cat, ThemeDraft.fromJson(themed, rules));
        check(ls.listOrDefault("layout.bottomTabs.items").equals(Arrays.asList("chats", "people", "settings")), "tabs default list");
        check(ls.listOrDefault("layout.privacy.hiddenRows").isEmpty(), "hidden rows default empty");
        check(ls.draft.set("layout.bottomTabs.items", new ArrayList<Object>(ls.listOrDefault("layout.bottomTabs.items"))) == null, "customize tabs");
        check(ls.draft.listMove("layout.bottomTabs.items", 0, 2) == null && ls.draft.get("layout.bottomTabs.items").equals(Arrays.asList("people", "settings", "chats")), "move tab");
        check(ls.draft.listRemove("layout.bottomTabs.items", "chats") == null, "remove to minimum");
        check(ls.draft.listRemove("layout.bottomTabs.items", "people") != null, "below minimum count refused");
        check(ls.listAvailable("layout.bottomTabs.items").contains("chats") && !ls.listAvailable("layout.bottomTabs.items").contains("people"), "available items");
        check(ls.draft.listAdd("layout.bottomTabs.items", "bogus", 0) != null, "unknown item refused");
        check(ls.draft.listAdd("layout.bottomTabs.items", "chats", 0) == null && ls.draft.listAdd("layout.bottomTabs.items", "chats", 0) != null, "duplicate refused");
        check(cat.itemLabel("kuroTheme", "en").length() > 0 && cat.itemLabel("zzz", "en").equals("zzz"), "item labels");
        int unsup = 0;
        for (Catalog.Entry e : cat.entries()) if (!ls.isSupported(e)) unsup++;
        check(unsup == 10, "unsupported entries " + unsup);
        check(!ls.isSupported(cat.entry("layout.chat.input.style")) && !ls.isSupported(cat.entry("tokens.typography.fontFamily")) && ls.isSupported(cat.entry("layout.chatList.mode")), "support flags");

        // ---- night overlay
        ThemeDraft nt = ThemeDraft.fromJson(themed, rules);
        check(nt.set("night.tokens.colors.background", "#000000") == null, "night colour set");
        check(nt.set("night.tokens.colors.accent", "red") != null, "night value checked by the same rule");
        check(nt.set("night.layout.bottomTabs.labels", true) != null, "layout has no night version");
        check(nt.set("night.tokens.nope", "#000000") != null, "unknown night path refused");
        check("#112233".equals(nt.get("night.tokens.colors.accent")) && Json.semanticEquals(nt.toMap().get("night"), Json.parse("{\"tokens\":{\"colors\":{\"accent\":\"#112233\",\"background\":\"#000000\"}}}")), "night overlay keeps its other keys");
        nt.undo();
        check(Json.semanticEquals(Json.parse(themed), Json.parse(nt.toJson())), "night edit undone exactly");
        EditorSession ns = new EditorSession(rules, cat, ThemeDraft.fromJson(themed, rules));
        ns.setNight(true);
        check("dark".equals(ns.appearance()), "night preview is dark");
        check(ns.color("tokens.colors.accent", 0) == 0xFF112233, "night value wins");
        check(ns.color("tokens.colors.divider", 5) == 5 && ns.setValue("tokens.colors.divider", "#445566") == null && "#445566".equals(ns.draft.get("night.tokens.colors.divider")), "night writes go to the overlay");
        check(ns.draft.get("tokens.colors.divider") == null, "day untouched by night edit");
        ns.draft.set("tokens.shapes.cardRadiusDp", 9L);
        check(ns.intValue("tokens.shapes.cardRadiusDp", 1) == 9, "night inherits day");
        check(ns.unsetValue("tokens.colors.accent") == null && ns.draft.get("tokens.colors.accent").equals("#0866FF") && ns.color("tokens.colors.accent", 0) == 0xFF0866FF, "unset night falls back to day");
        check(ns.setColor("tokens.colors.surface", 0xFF202020, false) == null && "#202020".equals(ns.draft.get("night.tokens.colors.surface")) && ns.draft.get("tokens.colors.surface") == null, "setColor writes to night overlay");
        ns.setAdvanced(true);
        for (Catalog.Entry e : ns.allSettings()) check(!e.path.startsWith("layout.") && !e.group.equals("structure"), "night shows only skin settings " + e.path);

        // ---- styles and colourways
        Map<String, Object> baseDoc = Json.parseObject(themed);
        Map<String, Object> look = Json.parseObject("{\"tokens\":{\"colors\":{\"accent\":\"#FF0000\",\"futureColor\":null},\"shapes\":{\"cardRadiusDp\":4}},\"night\":{\"tokens\":{\"colors\":{\"accent\":\"#AA0000\"}}}}");
        Map<String, Object> mergedDoc = StyleDiff.merge(baseDoc, look);
        check(mergedDoc.get("x-vendor") != null && !((Map<String, Object>) ((Map<String, Object>) mergedDoc.get("tokens")).get("colors")).containsKey("futureColor"), "merge: null removes, others stay");
        check(Json.semanticEquals(Json.parse(themed), Json.parse(Json.write(baseDoc))), "merge leaves its inputs alone");
        Map<String, Object> back = StyleDiff.patch(baseDoc, mergedDoc);
        check(Json.semanticEquals(StyleDiff.merge(baseDoc, back), mergedDoc), "patch is the inverse of merge");
        check(StyleDiff.patch(baseDoc, Json.parseObject(themed)).isEmpty(), "no difference -> empty patch");
        for (String f : files) {
            Map<String, Object> a = Json.parseObject(read(f)), b = StyleDiff.merge(a, Json.parseObject("{\"tokens\":{\"colors\":{\"accent\":\"#010101\"}}}"));
            check(Json.semanticEquals(StyleDiff.merge(a, StyleDiff.patch(a, b)), b), "patch/merge round trip " + f);
        }

        StyleSet set = new StyleSet(rules);
        String sid = set.addStyle("Rounded Look");
        check("rounded-look".equals(sid) && "rounded-look-2".equals(set.addStyle("Rounded Look")), "style ids are unique slugs");
        check(set.addStyle("!!!") != null && set.style("style") != null, "empty slug falls back");
        String wid = set.addColorway(sid, "Ocean");
        check("ocean".equals(wid) && set.draftFor(sid, wid) != null && set.draftFor(sid, null) != null && set.draftFor("zz", null) == null, "colourway drafts");
        for (int i = 0; i < 20; i++) set.addStyle("s" + i);
        check(set.styles().size() == StyleSet.MAX_STYLES, "style limit " + set.styles().size());
        check(set.draftFor(sid, wid).set("tokens.colors.accent", "#0A84FF") == null, "patch edited through rules");
        check(set.draftFor(sid, wid).set("tokens.colors.accent", "blue") != null, "patch edits are checked");
        String sj = set.toJson();
        check(sj.contains("\"patch\"") && !sj.contains("\"content\""), "only what is set is written");
        StyleSet again = StyleSet.fromJson(sj, rules);
        check(Json.semanticEquals(Json.parse(sj), Json.parse(again.toJson())) && !again.isDirty(), "styles round trip, clean after load");
        again.draftFor(sid, wid).set("tokens.shapes.cardRadiusDp", 6L);
        check(again.isDirty(), "styles dirty after edit");
        check(again.remove(sid, wid) && again.way(sid, wid) == null && again.rename(sid, null, "Renamed") && "Renamed".equals(again.style(sid).name), "remove and rename");

        // ---- session layers: colourway over style over base
        EditorSession ss = new EditorSession(rules, cat, ThemeDraft.fromJson(themed, rules), new StyleSet(rules));
        String sty = ss.styles.addStyle("Round");
        String cw = ss.styles.addColorway(sty, "Red");
        check(ss.onBase() && ss.active() == ss.draft, "base is edited by default");
        ss.setTarget(sty, null);
        check(ss.setValue("tokens.shapes.cardRadiusDp", 3L) == null && ss.draft.get("tokens.shapes.cardRadiusDp") == null, "style edit stays in the style");
        ss.setTarget(sty, cw);
        check(ss.setColor("tokens.colors.accent", 0xFFFF0000, false) == null && "#FF0000".equals(ss.styles.draftFor(sty, cw).get("tokens.colors.accent")), "colourway edit goes to its patch");
        check("#0866FF".equals(ss.draft.get("tokens.colors.accent")), "base untouched by colourway edit");
        check(ss.intValue("tokens.shapes.cardRadiusDp", 0) == 3 && ss.color("tokens.colors.accent", 0) == 0xFFFF0000, "colourway sees style and its own colours");
        ss.draft.set("tokens.colors.divider", "#112244");
        check(ss.color("tokens.colors.divider", 0) == 0xFF112244, "and inherits the base");
        check(ss.ownValue("tokens.shapes.cardRadiusDp") == null, "inherited is not 'own'");
        ss.setTarget("nope", null);
        check(ss.onBase(), "unknown look falls back to base");
        ss.setTarget(sty, cw);
        Map<String, Object> eff = Json.parseObject(ss.mergedJson(sty, cw));
        check("#FF0000".equals(((Map<String, Object>) ((Map<String, Object>) eff.get("tokens")).get("colors")).get("accent"))
                && Json.semanticEquals(eff.get("x-vendor"), baseDoc.get("x-vendor")), "merged json = what the store serves");
        ss.refreshSwatches();
        check("#FF0000".equals(ss.styles.way(sty, cw).swatch[0]), "swatch from the colourway's accent");
        check(ss.active().canUndo(), "undo belongs to the edited look");
        ss.active().undo();
        check(ss.isDirty(), "session dirty");
        ss.setAdvanced(true);
        for (Catalog.Entry e : ss.allSettings()) check(!e.group.equals("structure"), "no structure settings inside a look " + e.path);

        // ---- readability
        check(Math.abs(Contrast.ratio(0xFF000000, 0xFFFFFFFF) - 21.0) < 0.01 && Math.abs(Contrast.ratio(0xFFFFFFFF, 0xFFFFFFFF) - 1.0) < 0.01, "contrast extremes");
        check(Contrast.ratio(0x80000000, 0xFFFFFFFF) < Contrast.ratio(0xFF000000, 0xFFFFFFFF), "translucent text is mixed with the background");
        EditorSession rs = new EditorSession(rules, cat, ThemeDraft.fromJson("{\"schemaVersion\":2,\"meta\":{\"id\":\"r\",\"name\":\"R\"}}", rules));
        check(rs.readability().isEmpty(), "nothing set, nothing to blame");
        rs.setColor("tokens.colors.textPrimary", 0xFFEEEEEE, false);
        rs.setColor("tokens.colors.background", 0xFFFFFFFF, false);
        check(rs.readability().size() >= 1 && !rs.readability().get(0).night, "light text on white is flagged");
        rs.setColor("tokens.colors.textPrimary", 0xFF111111, false);
        rs.setNight(true);
        rs.setColor("tokens.colors.textPrimary", 0xFF202020, false);
        rs.setColor("tokens.colors.background", 0xFF101010, false);
        rs.setNight(false);
        boolean nightFlag = false;
        for (EditorSession.Readability rd : rs.readability()) nightFlag |= rd.night;
        check(nightFlag, "night pair flagged separately");
        check(!rs.night(), "readability leaves the editor state alone");
        for (String id : BuilderText.SCENES) check(!BuilderText.get("en", "scene." + id).equals("scene." + id), "scene text " + id);
        check(BuilderText.complete(), "builder text has en and my for every key");
        check(BuilderText.get("en", "undo").length() > 0 && BuilderText.get("zz", "undo").equals(BuilderText.get("en", "undo")), "builder text fallback");


        // ---- Theme Studio model
        {
            EditorSession stu = new EditorSession(rules, cat, ThemeDraft.fromJson("{\"schemaVersion\":2,\"meta\":{\"id\":\"s\",\"name\":\"S\"}}", rules));
            stu.setAdvanced(true);
            int sectioned = 0;
            for (Catalog.Entry e : stu.allSettings()) {
                RuleSet.Ref ref = rules.find(e.path);
                StudioModel.Section sec = StudioModel.sectionOf(e, ref == null ? null : ref.rule);
                check(sec != null, "section for " + e.path);
                if (ref != null && (ref.rule.kind == Rule.Kind.COLOR || ref.rule.kind == Rule.Kind.ARGB) && !e.path.toLowerCase().contains("font"))
                    check(sec == StudioModel.Section.APPEARANCE, "colour is appearance " + e.path);
                sectioned++;
            }
            check(sectioned > 100, "sections cover the settings");
            for (StudioModel.Section k : StudioModel.Section.values())
                check(!BuilderText.get("en", StudioModel.sectionKey(k)).equals(StudioModel.sectionKey(k)), "section text " + k);
            // layers: dotted ids hang under their shown parent, everything else under the screen background
            StudioModel.Node tree = StudioModel.layers(stu, Arrays.asList("screenBackground", "appBar", "chatRow", "chatRow.avatar", "chatRow.title", "bottomTabs", "bottomTabs.item"));
            check("screenBackground".equals(tree.id), "layers root");
            StudioModel.Node chatRow = null, tabs = null;
            for (StudioModel.Node k : tree.kids) { if (k.id.equals("chatRow")) chatRow = k; if (k.id.equals("bottomTabs")) tabs = k; }
            check(chatRow != null && chatRow.kids.size() == 2, "chat row owns avatar and title");
            check(tabs != null && tabs.kids.size() == 1 && tabs.kids.get(0).id.equals("bottomTabs.item"), "tab item under tabs");
            StudioModel.Node orphan = StudioModel.layers(stu, Arrays.asList("chatRow.avatar")).kids.get(0);
            check(orphan.id.equals("chatRow.avatar"), "child without shown parent hangs under the screen");
            // reset touches only the component asked, as one undo step
            String chatTitleP = null, tabP = null;
            for (Catalog.Entry e : cat.forComponent("chatRow.title")) { RuleSet.Ref r2 = rules.find(e.path); if (r2 != null && r2.rule.kind == Rule.Kind.COLOR && stu.isSupported(e)) { chatTitleP = e.path; break; } }
            for (Catalog.Entry e : cat.forComponent("fab")) { RuleSet.Ref r2 = rules.find(e.path); if (r2 != null && r2.rule.kind == Rule.Kind.COLOR && stu.isSupported(e) && !e.targets.contains("chatRow.title")) { tabP = e.path; break; } }
            check(chatTitleP != null && tabP != null, "found colour settings for reset test " + chatTitleP + " " + tabP);
            if (chatTitleP != null && tabP != null) {
                check(stu.setColor(chatTitleP, 0xFF112233, false) == null, "set title colour");
                check(stu.setColor(tabP, 0xFF445566, false) == null, "set fab colour");
                int undoBefore = 0; while (stu.active().canUndo()) { stu.active().undo(); undoBefore++; }
                while (stu.active().canRedo()) stu.active().redo();
                int removed = StudioModel.resetComponent(stu, "chatRow.title", null);
                check(removed >= 1 && stu.ownValue(chatTitleP) == null, "reset removes the component's values");
                check(stu.ownValue(tabP) != null, "reset leaves other components alone");
                stu.active().undo();
                check(stu.ownValue(chatTitleP) != null, "reset is one undo step");
                // copy / paste
                java.util.Map<String, Object> copied = StudioModel.copyStyle(stu, "fab");
                check(copied.containsKey(tabP), "copy takes set values");
                check(StudioModel.pasteStyle(stu, "chatRow.preview", java.util.Collections.<String, Object>singletonMap("components.nothing", "x")) == 0, "paste ignores settings the target lacks");
            }
            // warnings
            check(StudioModel.componentOfPath(cat, "no.such.path").equals("global"), "unknown path goes to the whole app");
            check(StudioModel.suggestText(0xFF000000) == 0xFFFFFFFF && StudioModel.suggestText(0xFFFFFFFF) == 0xFF000000, "suggested text colour reads");
        }


        // ---- presets and clone
        {
            for (Presets.Preset pr : Presets.all()) {
                String pj = Presets.toJson(pr);
                ThemeDraft pd = ThemeDraft.fromJson(pj, rules);
                EditorSession ps = new EditorSession(rules, cat, pd);
                check("dark".equals(ps.appearance()) == pr.dark, "preset appearance " + pr.id);
                check(ps.readability().isEmpty(), "preset reads well " + pr.id);
                check(ps.color("tokens.colors.accent", 0) != 0, "preset accent " + pr.id);
                check(Json.semanticEquals(Json.parse(pd.toJson()), Json.parse(pj)), "preset round trip " + pr.id);
            }
            String cl = Presets.cloneOf("{\"schemaVersion\":2,\"meta\":{\"id\":\"a\",\"name\":\"Mine\",\"extra\":1},\"zzUnknown\":{\"k\":2}}");
            check(cl != null && cl.contains("Mine (copy)") && cl.contains("\"a-copy\"") && cl.contains("zzUnknown") && cl.contains("extra"), "clone keeps everything, renames");
            check(Presets.cloneOf(Presets.cloneOf(cl)).contains("Mine (copy)") && !Presets.cloneOf(cl).contains("(copy) (copy)"), "clone of a clone is not stacked");
            check(Presets.cloneOf("not json") == null, "clone of garbage is null");
        }

        {
            java.util.Map<String, tw.kurocore.kurogram.layout.BlockSpec.Screen> sc = tw.kurocore.kurogram.layout.BlockSpec.parse(Json.parse("{\"people\":{\"title\":\"People\",\"blocks\":[{\"type\":\"storyRail\",\"style\":\"cards\"},{\"type\":\"chips\",\"items\":[\"online\",\"all\"]},{\"type\":\"note\",\"text\":\"Hi\"},{\"type\":\"future\"},{\"type\":\"list\",\"source\":\"contacts\",\"row\":\"compact\",\"limit\":50}]}}"));
            check(sc.size() == 1 && sc.get("people").blocks.size() == 4, "blocks: unknown type skipped");
            check("cards".equals(sc.get("people").blocks.get(0).str("style", "")) && sc.get("people").blocks.get(3).intValue("limit", 0) == 50, "blocks: props read");
            String[] bad = {"{\"p\":{\"blocks\":[{\"type\":\"list\",\"source\":\"secret\"}]}}", "{\"P\":{\"blocks\":[]}}", "{\"p1\":{\"blocks\":[{\"type\":\"chips\",\"items\":[\"nope\"]}]}}", "{\"pp\":{\"blocks\":[{\"type\":\"spacer\",\"heightDp\":999}]}}", "{\"pp\":{}}", "{\"pp\":{\"blocks\":[{\"type\":\"note\",\"text\":\"" + "x".repeat(121) + "\"}]}}"};
            for (String b : bad) {
                boolean threw = false;
                try {
                    tw.kurocore.kurogram.layout.BlockSpec.parse(Json.parse(b));
                } catch (IllegalArgumentException e) {
                    threw = true;
                }
                check(threw, "blocks: rejects " + b.substring(0, Math.min(40, b.length())));
            }
            check(tw.kurocore.kurogram.layout.BlockSpec.parse(null).isEmpty(), "blocks: none is fine");
        }

        System.out.println("editor tests: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
