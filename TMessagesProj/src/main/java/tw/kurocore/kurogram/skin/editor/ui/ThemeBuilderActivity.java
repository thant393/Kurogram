package tw.kurocore.kurogram.skin.editor.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import tw.kurocore.kurogram.skin.KuroSkinManager;
import tw.kurocore.kurogram.skin.SkinSpec;
import tw.kurocore.kurogram.skin.editor.BuilderText;
import tw.kurocore.kurogram.skin.editor.Catalog;
import tw.kurocore.kurogram.skin.editor.ColorCodec;
import tw.kurocore.kurogram.skin.editor.EditorSession;
import tw.kurocore.kurogram.skin.editor.Rule;
import tw.kurocore.kurogram.skin.editor.RuleSet;
import tw.kurocore.kurogram.skin.editor.StyleSet;
import tw.kurocore.kurogram.skin.editor.ThemeDraft;
import tw.kurocore.kurogram.stickerstore.ThemeUploadActivity;

/**
 * The no-code Theme Builder. A simulated preview at the top (tap a part to select it), the settings of the selected part
 * below, undo/redo, Basic/Advanced, English/Burmese. The draft is the skin JSON itself ({@link ThemeDraft}); the engine's own
 * parser has the last word before anything is applied or uploaded. A real-app preview exists but is always temporary
 * ({@link PreviewGuard}). Phase 1: colours, gradient, light/dark, name.
 */
public class ThemeBuilderActivity extends BaseFragment {
    private static final String PREFS = "kuro_theme_builder";
    private static final String KEY_DRAFT = "draft";
    private static final String KEY_LANG = "lang";
    private static final String KEY_STYLES = "styles";
    private static final String TEMPLATE = "{\"schemaVersion\":2,\"meta\":{\"id\":\"my-theme\",\"name\":\"My Theme\"},\"tokens\":{\"colors\":{\"accent\":\"#3390EC\"}}}";

    private final String startJson; // an existing theme to edit, or null
    private final tw.kurocore.kurogram.stickerstore.ThemeInfo editing; // its store entry when editing
    private final String startStyles; // the styles of a theme being edited (JSON list) or null
    private final PreviewGuard guard = new PreviewGuard();
    private EditorSession session;
    private BuilderPreviewView preview;
    private LinearLayout chips, list;
    private TextView undoBtn, redoBtn, modeBtn, sceneBtn, liveBtn, variantBtn, lookBtn, hint;
    private boolean live;
    private final Runnable livePush = this::pushLive;
    private final Runnable autosave = this::saveDraft;

    public ThemeBuilderActivity() {
        this(null, null);
    }

    public ThemeBuilderActivity(String existingThemeJson, tw.kurocore.kurogram.stickerstore.ThemeInfo editing) {
        this(existingThemeJson, editing, null);
    }

    public ThemeBuilderActivity(String existingThemeJson, tw.kurocore.kurogram.stickerstore.ThemeInfo editing, String stylesJson) {
        this.startJson = existingThemeJson;
        this.editing = editing;
        this.startStyles = stylesJson;
    }

    // ------------------------------------------------------------------ setup

    private static String readAsset(String name) throws Exception {
        try (InputStream in = ApplicationLoader.applicationContext.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public boolean onFragmentCreate() {
        try {
            RuleSet rules = RuleSet.fromManifest(readAsset("kuro/skin-rules.manifest.json"));
            Catalog catalog = Catalog.fromJson(readAsset("kuro/skin-editor.catalog.json"));
            String json = startJson;
            if (json == null) {
                json = prefs().getString(KEY_DRAFT, null);
            }
            ThemeDraft draft;
            try {
                draft = ThemeDraft.fromJson(json == null ? TEMPLATE : json, rules);
            } catch (RuntimeException bad) { // a damaged saved draft must not lock the Builder
                draft = ThemeDraft.fromJson(TEMPLATE, rules);
            }
            StyleSet styleSet;
            try {
                String sj = startJson != null ? startStyles : prefs().getString(KEY_STYLES, null);
                styleSet = StyleSet.fromJson(sj, rules);
            } catch (RuntimeException bad) {
                styleSet = new StyleSet(rules);
            }
            session = new EditorSession(rules, catalog, draft, styleSet);
            session.setLang(prefs().getString(KEY_LANG, "en"));
            return super.onFragmentCreate();
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    private String tr(String key) {
        return BuilderText.get(session.lang(), key);
    }

    @Override
    public void onFragmentDestroy() {
        endLive();
        saveDraft();
        super.onFragmentDestroy();
    }

    @Override
    public void onPause() {
        // app to background: the real-app preview must not outlive the Builder being on screen
        if (live) {
            setLive(false);
        }
        saveDraft();
        super.onPause();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(tr("title"));
        actionBar.createMenu().addItem(1, tr("next"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    confirmLeave();
                } else if (id == 1) {
                    next();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // toolbar
        HorizontalScrollView bar = new HorizontalScrollView(context);
        bar.setHorizontalScrollBarEnabled(false);
        LinearLayout barRow = new LinearLayout(context);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        bar.addView(barRow);
        undoBtn = button(context, "undo", v -> {
            session.active().undo();
            changed(false);
        });
        redoBtn = button(context, "redo", v -> {
            session.active().redo();
            changed(false);
        });
        modeBtn = button(context, "advanced", v -> {
            session.setAdvanced(!session.advanced());
            refreshAll();
        });
        sceneBtn = button(context, "scene.chat", v -> {
            String[] order = {"chats", "chat", "settings", "controls", "drawer", "rows"};
            int at = 0;
            for (int i = 0; i < order.length; i++) {
                if (order[i].equals(session.scene())) {
                    at = i;
                }
            }
            session.setScene(order[(at + 1) % order.length]);
            refreshAll();
        });
        liveBtn = button(context, "live", v -> setLive(!live));
        lookBtn = button(context, "look", v -> lookDialog());
        variantBtn = button(context, "variant.day", v -> {
            session.setNight(!session.night());
            refreshAll();
        });
        TextView lang = button(context, "language", v -> {
            session.setLang("my".equals(session.lang()) ? "en" : "my");
            prefs().edit().putString(KEY_LANG, session.lang()).apply();
            actionBar.setTitle(tr("title"));
            refreshAll();
        });
        TextView use = button(context, "use", v -> useOnPhone());
        TextView startOver = button(context, "startOver", v -> startOver());
        for (TextView b : new TextView[]{undoBtn, redoBtn, lookBtn, variantBtn, modeBtn, sceneBtn, liveBtn, lang, use, startOver}) {
            barRow.addView(b, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 0, 0, 6, 0));
        }
        col.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        preview = new BuilderPreviewView(context, session);
        preview.setListener(id -> {
            session.select(id);
            refreshPanel();
            preview.invalidate();
        });
        col.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 0));

        hint = new TextView(context);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        col.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 4, 12, 4));

        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        chipScroll.addView(chips);
        col.addView(chipScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        col.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        refreshAll();
        return fragmentView;
    }

    private TextView button(Context context, String key, View.OnClickListener l) {
        TextView t = new TextView(context);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(AndroidUtilities.bold());
        t.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        t.setTag(key);
        t.setOnClickListener(l);
        t.setText(tr(key));
        return t;
    }

    private void styleButton(TextView t, boolean on) {
        int accent = KuroSkinManager.get().accent();
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(18));
        d.setColor(on ? accent : (accent & 0x00FFFFFF) | 0x22000000);
        t.setBackground(d);
        t.setTextColor(on ? 0xFFFFFFFF : accent);
    }

    // ------------------------------------------------------------------ refresh

    private void refreshAll() {
        refreshToolbar();
        refreshChips();
        refreshPanel();
        preview.invalidate();
    }

    private void refreshToolbar() {
        for (TextView b : new TextView[]{undoBtn, redoBtn, modeBtn, sceneBtn, liveBtn}) {
            b.setText(tr((String) b.getTag()));
        }
        variantBtn.setText(tr(session.night() ? "variant.night" : "variant.day"));
        lookBtn.setText(tr("look") + ": " + lookName());
        styleButton(lookBtn, !session.onBase());
        styleButton(variantBtn, session.night());
        modeBtn.setText(tr(session.advanced() ? "basic" : "advanced"));
        sceneBtn.setText(tr("scene." + session.scene()));
        styleButton(undoBtn, false);
        styleButton(redoBtn, false);
        undoBtn.setAlpha(session.active().canUndo() ? 1f : 0.4f);
        redoBtn.setAlpha(session.active().canRedo() ? 1f : 0.4f);
        styleButton(modeBtn, session.advanced());
        styleButton(sceneBtn, false);
        styleButton(liveBtn, live);
        hint.setText(tr(live ? "live.on" : "simulated"));
    }

    private void refreshChips() {
        chips.removeAllViews();
        Context context = chips.getContext();
        addChip(context, "global", session.catalog.componentLabel("global", session.lang()));
        for (String id : session.browsableComponents()) {
            if (!"global".equals(id)) {
                addChip(context, id, session.catalog.componentLabel(id, session.lang()));
            }
        }
    }

    private void addChip(Context context, String id, String label) {
        TextView t = new TextView(context);
        t.setText(label);
        t.setTextSize(13);
        t.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        styleButton(t, id.equals(session.selected()));
        t.setOnClickListener(v -> {
            session.select(id);
            refreshChips();
            refreshPanel();
            preview.invalidate();
        });
        chips.addView(t, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
    }

    private void refreshPanel() {
        list.removeAllViews();
        Context context = list.getContext();
        String lang = session.lang();
        TextView head = text(context, tr("selected") + ": " + session.catalog.componentLabel(session.selected(), lang), 15, true);
        list.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 4));
        if ("global".equals(session.selected()) && !session.night()) {
            addAppearanceRow(context);
            addNameRow(context);
        }
        List<Catalog.Entry> entries = session.settings();
        if (entries.isEmpty() && !"global".equals(session.selected())) {
            list.addView(text(context, tr("noSettings") + " " + tr("later"), 13, false),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 8));
        }
        for (Catalog.Entry e : entries) {
            if (e.path.equals("appearance") || e.path.equals("meta") || e.path.equals("night") || e.path.equals("layout")) {
                continue; // handled by their own rows or later phases
            }
            RuleSet.Ref ref = session.rules.find(e.path);
            if (ref != null) {
                addRuleRow(context, e, ref.rule);
            }
        }
    }

    private TextView text(Context context, String s, int sp, boolean bold) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Theme.getColor(bold ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText));
        if (bold) {
            t.setTypeface(AndroidUtilities.bold());
        }
        return t;
    }

    private LinearLayout row(Context context, String label, String sub) {
        LinearLayout r = new LinearLayout(context);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        r.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(context, label, 15, false));
        ((TextView) words.getChildAt(0)).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (sub != null && !sub.isEmpty()) {
            words.addView(text(context, sub, 12, false));
        }
        r.addView(words, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        list.addView(r, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 1));
        return r;
    }

    private void addAppearanceRow(Context context) {
        LinearLayout r = row(context, tr("appearance"), tr(session.appearance()));
        r.setOnClickListener(v -> {
            String[] keys = {"auto", "light", "dark"};
            String[] names = {tr("auto"), tr("light"), tr("dark")};
            new AlertDialog.Builder(getParentActivity()).setItems(names, (d, i) -> {
                report(session.draft.setStructure("appearance", keys[i]));
                changed(true);
            }).show();
        });
    }

    private void addNameRow(Context context) {
        Object n = session.draft.get("meta.name");
        LinearLayout r = row(context, tr("name"), n == null ? "" : String.valueOf(n));
        r.setOnClickListener(v -> {
            android.widget.EditText input = new android.widget.EditText(context);
            input.setText(n == null ? "" : String.valueOf(n));
            new AlertDialog.Builder(getParentActivity()).setTitle(tr("name")).setView(input)
                    .setPositiveButton(tr("done"), (d, w) -> {
                        report(session.draft.setStructure("meta.name", input.getText().toString().trim()));
                        changed(true);
                    }).setNegativeButton(tr("cancel"), null).show();
        });
    }

    private void addRuleRow(Context context, Catalog.Entry e, Rule rule) {
        String label = e.label.get(session.lang(), e.path);
        if (!session.isSupported(e)) {
            LinearLayout off = row(context, label, tr("unsupported"));
            off.setAlpha(0.45f);
            return;
        }
        Object cur = session.ownValue(e.path);
        String shown = cur == null ? (session.night() && session.raw(e.path) != null ? tr("inherited") + ": " + String.valueOf(session.raw(e.path)) : tr("notSet")) : cur instanceof List ? listSummary(cur) : String.valueOf(cur);
        LinearLayout r = row(context, label, shown);
        if (rule.kind == Rule.Kind.COLOR || rule.kind == Rule.Kind.ARGB) {
            View sw = new View(context);
            int c = cur instanceof String ? ColorCodec.parse((String) cur, rule.kind) : 0;
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(c);
            d.setStroke(AndroidUtilities.dp(1), 0x55888888);
            sw.setBackground(d);
            r.addView(sw, LayoutHelper.createLinear(28, 28, 8, 0, 8, 0));
            r.setOnClickListener(v -> colorDialog(e, rule, label));
        } else if (rule.kind == Rule.Kind.ENUM) {
            r.setOnClickListener(v -> new AlertDialog.Builder(getParentActivity())
                    .setItems(rule.options.toArray(new String[0]), (d, i) -> {
                        report(session.setValue(e.path, rule.options.get(i)));
                        changed(true);
                    }).show());
        } else if (rule.kind == Rule.Kind.LIST) {
            r.setOnClickListener(v -> listDialog(e, rule, label));
        } else if (rule.kind == Rule.Kind.INT || rule.kind == Rule.Kind.FLOAT) {
            addSlider(context, r, e, rule, cur);
        } else if (rule.kind == Rule.Kind.BOOL) {
            r.setOnClickListener(v -> {
                report(session.setValue(e.path, !Boolean.TRUE.equals(cur)));
                changed(true);
            });
        }
        if (cur != null) {
            TextView x = text(context, "✕", 16, true);
            x.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4), 0);
            x.setOnClickListener(v -> {
                report(session.unsetValue(e.path));
                changed(true);
            });
            r.addView(x);
        }
    }

    /** INT and FLOAT rules: a slider inside the row. A whole drag is one undo step; the rule's own range limits it. */
    private void addSlider(Context context, LinearLayout r, Catalog.Entry e, Rule rule, Object cur) {
        final boolean isInt = rule.kind == Rule.Kind.INT;
        final double min = rule.min, max = rule.max;
        final int steps = isInt ? (int) (max - min) : 100;
        final TextView value = (TextView) ((LinearLayout) r.getChildAt(0)).getChildAt(1);
        double start = cur instanceof Number ? ((Number) cur).doubleValue() : min;
        SeekBar sb = new SeekBar(context);
        sb.setMax(steps);
        sb.setProgress((int) Math.round((start - min) / (max - min) * steps));
        final boolean[] fresh = {true};
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                double real = min + (max - min) * v / steps;
                Object val = isInt ? (Object) Long.valueOf(Math.round(real)) : (Object) Double.valueOf(Math.round(real * 100) / 100.0);
                String err = session.setValue(e.path, val, !fresh[0]);
                fresh[0] = false;
                if (err == null) {
                    value.setText(String.valueOf(val));
                    preview.invalidate();
                    refreshToolbar();
                    scheduleLive();
                    AndroidUtilities.cancelRunOnUIThread(autosave);
                    AndroidUtilities.runOnUIThread(autosave, 800);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                fresh[0] = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                fresh[0] = true;
            }
        });
        r.addView(sb, 1, LayoutHelper.createLinear(120, LayoutHelper.WRAP_CONTENT));
    }

    private String listSummary(Object list) {
        StringBuilder sb = new StringBuilder();
        for (Object o : (List<?>) list) {
            sb.append(sb.length() == 0 ? "" : ", ").append(session.catalog.itemLabel(String.valueOf(o), session.lang()));
        }
        return sb.length() == 0 ? tr("none") : sb.toString();
    }

    /**
     * Ordered list settings (tabs, menu items, header buttons, hidden rows): up/down to reorder, ✕ to remove, + to add from the
     * allowed items. Every change goes through the draft, so the rule's own limits (minimum/maximum count, allowed items) apply.
     */
    private void listDialog(Catalog.Entry e, Rule rule, String label) {
        Context context = getParentActivity();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), 0);
        ScrollView sv = new ScrollView(context);
        sv.addView(box);
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle(label).setView(sv)
                .setPositiveButton(tr("done"), (d, w) -> changed(true)).setOnCancelListener(d -> changed(true)).create();
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            if (!(session.active().get(session.writePath(e.path)) instanceof List)) { // not customised yet: first change writes the defaults
                TextView first = text(context, tr("customize"), 14, true);
                first.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
                first.setOnClickListener(v -> {
                    report(session.setValue(e.path, new java.util.ArrayList<Object>(session.listOrDefault(e.path))));
                    fill[0].run();
                    preview.invalidate();
                });
                box.addView(first);
                return;
            }
            final List<String> items = session.listOrDefault(e.path);
            for (int i = 0; i < items.size(); i++) {
                final int at = i;
                LinearLayout line = new LinearLayout(context);
                line.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = text(context, session.catalog.itemLabel(items.get(i), session.lang()), 15, false);
                name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                line.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                for (String[] a : new String[][]{{"▲", "up"}, {"▼", "down"}, {"✕", "del"}}) {
                    TextView b = text(context, a[0], 18, true);
                    b.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
                    b.setOnClickListener(v -> {
                        String err = "up".equals(a[1]) ? (at > 0 ? session.active().listMove(e.path, at, at - 1) : null)
                                : "down".equals(a[1]) ? (at < items.size() - 1 ? session.active().listMove(e.path, at, at + 1) : null)
                                : session.active().listRemove(e.path, items.get(at));
                        report(err);
                        fill[0].run();
                        preview.invalidate();
                        refreshToolbar();
                        scheduleLive();
                    });
                    line.addView(b);
                }
                box.addView(line);
            }
            for (String opt : session.listAvailable(e.path)) {
                TextView add = text(context, "＋ " + session.catalog.itemLabel(opt, session.lang()), 14, false);
                add.setTextColor(KuroSkinManager.get().accent());
                add.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
                add.setOnClickListener(v -> {
                    report(session.active().listAdd(e.path, opt, session.listOrDefault(e.path).size()));
                    fill[0].run();
                    preview.invalidate();
                    refreshToolbar();
                    scheduleLive();
                });
                box.addView(add);
            }
            TextView back = text(context, tr("reset"), 13, true);
            back.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(8));
            back.setOnClickListener(v -> {
                report(session.unsetValue(e.path));
                fill[0].run();
                preview.invalidate();
                refreshToolbar();
                scheduleLive();
            });
            box.addView(back);
        };
        fill[0].run();
        dialog.show();
    }

    private String lookName() {
        if (session.onBase()) {
            return tr("look.base");
        }
        StyleSet.Style s = session.styles.style(session.styleId());
        if (s == null) {
            return tr("look.base");
        }
        StyleSet.Way w = session.wayId() == null ? null : session.styles.way(s.id, session.wayId());
        return w == null ? s.name : s.name + " / " + w.name;
    }

    /**
     * Styles and colourways: the theme's own file is the base look; a style changes parts of it (shapes, layout…), a colourway
     * changes a style's colours. Choosing one makes every edit go to that look only; everything else is inherited.
     */
    private void lookDialog() {
        Context context = getParentActivity();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), 0);
        ScrollView sv = new ScrollView(context);
        sv.addView(box);
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle(tr("look")).setView(sv)
                .setPositiveButton(tr("done"), null).create();
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            addLookRow(box, tr("look.base"), null, null, dialog, fill);
            for (StyleSet.Style s : session.styles.styles()) {
                addLookRow(box, "◆ " + s.name, s.id, null, dialog, fill);
                for (StyleSet.Way w : s.ways) {
                    addLookRow(box, "      ● " + w.name, s.id, w.id, dialog, fill);
                }
                TextView addWay = text(context, "      ＋ " + tr("look.newWay"), 13, false);
                addWay.setTextColor(KuroSkinManager.get().accent());
                addWay.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
                addWay.setOnClickListener(v -> askName(tr("look.newWay"), "", n -> {
                    String id = session.styles.addColorway(s.id, n);
                    if (id == null) {
                        report(tr("look.limit"));
                    } else {
                        session.setTarget(s.id, id);
                        changed(true);
                        refreshAll();
                    }
                    fill[0].run();
                }));
                box.addView(addWay);
            }
            TextView add = text(context, "＋ " + tr("look.newStyle"), 14, true);
            add.setTextColor(KuroSkinManager.get().accent());
            add.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(8));
            add.setOnClickListener(v -> askName(tr("look.newStyle"), "", n -> {
                String id = session.styles.addStyle(n);
                if (id == null) {
                    report(tr("look.limit"));
                } else {
                    session.setTarget(id, null);
                    changed(true);
                    refreshAll();
                }
                fill[0].run();
            }));
            box.addView(add);
        };
        fill[0].run();
        dialog.show();
    }

    private void addLookRow(LinearLayout box, String label, String sid, String wid, AlertDialog dialog, Runnable[] fill) {
        Context context = box.getContext();
        LinearLayout line = new LinearLayout(context);
        line.setGravity(Gravity.CENTER_VERTICAL);
        boolean current = java.util.Objects.equals(sid, session.styleId()) && java.util.Objects.equals(wid, session.wayId());
        TextView name = text(context, (current ? "✓ " : "") + label, 15, current);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        name.setOnClickListener(v -> {
            session.setTarget(sid, wid);
            dialog.dismiss();
            changed(true);
            refreshAll();
            scheduleLive();
        });
        line.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        if (sid != null) {
            TextView ren = text(context, "✎", 18, true);
            ren.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            ren.setOnClickListener(v -> askName(tr("name"), label.replace("◆ ", "").replace("      ● ", ""), n -> {
                session.styles.rename(sid, wid, n);
                changed(true);
                fill[0].run();
            }));
            TextView del = text(context, "✕", 18, true);
            del.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4), 0);
            del.setOnClickListener(v -> {
                if (java.util.Objects.equals(sid, session.styleId())) {
                    session.setTarget(null, null);
                }
                session.styles.remove(sid, wid);
                changed(true);
                refreshAll();
                fill[0].run();
            });
            line.addView(ren);
            line.addView(del);
        }
        box.addView(line);
    }

    private interface NameCallback {
        void got(String name);
    }

    private void askName(String title, String initial, NameCallback cb) {
        final android.widget.EditText input = new android.widget.EditText(getParentActivity());
        input.setText(initial);
        input.setSingleLine(true);
        new AlertDialog.Builder(getParentActivity()).setTitle(title).setView(input)
                .setPositiveButton(tr("done"), (d, w) -> cb.got(input.getText().toString().trim()))
                .setNegativeButton(tr("cancel"), null).show();
    }

    // ------------------------------------------------------------------ colour dialog

    private void colorDialog(Catalog.Entry e, Rule rule, String label) {
        Context context = getParentActivity();
        int start = session.color(e.path, rule.kind == Rule.Kind.ARGB ? 0x80000000 : 0xFF3390EC);
        final int[] argb = {start};
        final boolean[] first = {true};
        final boolean[] edited = {false};
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), 0);
        final View swatch = new View(context);
        box.addView(swatch, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 8));
        final TextView hex = text(context, "", 14, true);
        box.addView(hex);
        final SeekBar[] bars = new SeekBar[4];
        String[] names = {"R", "G", "B", "A"};
        final Runnable[] sync = new Runnable[1];
        for (int i = 0; i < 4; i++) {
            final int ch = i;
            LinearLayout line = new LinearLayout(context);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(text(context, names[i], 13, true), LayoutHelper.createLinear(20, LayoutHelper.WRAP_CONTENT));
            SeekBar sb = new SeekBar(context);
            sb.setMax(255);
            bars[i] = sb;
            line.addView(sb, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            box.addView(line);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                    if (!fromUser) {
                        return;
                    }
                    int a = Color.alpha(argb[0]), r = Color.red(argb[0]), g = Color.green(argb[0]), b = Color.blue(argb[0]);
                    if (ch == 0) r = v; else if (ch == 1) g = v; else if (ch == 2) b = v; else a = v;
                    argb[0] = Color.argb(a, r, g, b);
                    String err = session.setColor(e.path, argb[0], !first[0]);
                    first[0] = false;
                    edited[0] = true;
                    if (err == null) {
                        scheduleLive();
                    }
                    sync[0].run();
                    preview.invalidate();
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                    first[0] = true; // the next drag is its own undo step
                }
            });
        }
        sync[0] = () -> {
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(AndroidUtilities.dp(8));
            d.setColor(argb[0]);
            d.setStroke(AndroidUtilities.dp(1), 0x55888888);
            swatch.setBackground(d);
            hex.setText(ColorCodec.format(argb[0], rule.kind));
            bars[0].setProgress(Color.red(argb[0]));
            bars[1].setProgress(Color.green(argb[0]));
            bars[2].setProgress(Color.blue(argb[0]));
            bars[3].setProgress(Color.alpha(argb[0]));
        };
        sync[0].run();
        final android.widget.EditText hexInput = new android.widget.EditText(context);
        hexInput.setHint("#RRGGBB");
        hexInput.setSingleLine(true);
        hexInput.setTextSize(14);
        hexInput.setOnEditorActionListener((tv, action, ev) -> {
            int c = ColorCodec.parse(tv.getText().toString().trim(), rule.kind);
            if (c != 0) {
                argb[0] = c;
                report(session.setColor(e.path, c, false));
                first[0] = true;
                edited[0] = true;
                scheduleLive();
                sync[0].run();
                preview.invalidate();
            }
            return false;
        });
        box.addView(hexInput);
        new AlertDialog.Builder(context).setTitle(label).setView(box)
                .setPositiveButton(tr("done"), (d, w) -> changed(true))
                .setNegativeButton(tr("cancel"), (d, w) -> {
                    if (edited[0]) {
                        session.draft.undo(); // a whole drag is one step; a typed value is one step
                    }
                    changed(true);
                })
                .setOnCancelListener(d -> changed(true))
                .show();
    }

    // ------------------------------------------------------------------ change handling

    private void report(String refusal) {
        if (refusal != null && getParentActivity() != null) {
            Toast.makeText(getParentActivity(), tr("refused") + refusal, Toast.LENGTH_SHORT).show();
        }
    }

    private void changed(boolean rebuildPanel) {
        refreshToolbar();
        if (rebuildPanel) {
            refreshPanel();
        } else {
            refreshChips();
            refreshPanel();
        }
        preview.invalidate();
        scheduleLive();
        AndroidUtilities.cancelRunOnUIThread(autosave);
        AndroidUtilities.runOnUIThread(autosave, 800);
    }

    private void saveDraft() {
        try {
            if (session != null) {
                prefs().edit().putString(KEY_DRAFT, session.draft.toJson()).putString(KEY_STYLES, session.styles.toJson()).apply();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void startOver() {
        new AlertDialog.Builder(getParentActivity()).setMessage(tr("startOver") + "?")
                .setPositiveButton(tr("done"), (d, w) -> {
                    prefs().edit().remove(KEY_DRAFT).remove(KEY_STYLES).apply();
                    setLive(false);
                    session = new EditorSession(session.rules, session.catalog, ThemeDraft.fromJson(TEMPLATE, session.rules));
                    session.setLang(prefs().getString(KEY_LANG, "en"));
                    preview.setSession(session);
                    refreshAll();
                }).setNegativeButton(tr("cancel"), null).show();
    }

    // ------------------------------------------------------------------ real-app preview (always temporary)

    private void setLive(boolean on) {
        live = on;
        if (on) {
            pushLive();
        } else {
            endLive();
        }
        refreshToolbar();
    }

    private void scheduleLive() {
        if (live) {
            AndroidUtilities.cancelRunOnUIThread(livePush);
            AndroidUtilities.runOnUIThread(livePush, 300);
        }
    }

    private void pushLive() {
        if (!live) {
            return;
        }
        String err = guard.show(session.effectiveJson());
        if (err != null) {
            live = false;
            refreshToolbar();
            if (getParentActivity() != null) {
                Toast.makeText(getParentActivity(), tr("invalid") + err, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void endLive() {
        live = false;
        AndroidUtilities.cancelRunOnUIThread(livePush);
        guard.end();
    }

    // ------------------------------------------------------------------ leave / use / publish

    private void confirmLeave() {
        if (!session.isDirty() || getParentActivity() == null) {
            finishFragment();
            return;
        }
        new AlertDialog.Builder(getParentActivity()).setMessage(tr("exit"))
                .setPositiveButton(tr("leave"), (d, w) -> {
                    endLive();
                    finishFragment();
                }).setNegativeButton(tr("stay"), null).show();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (invoked && session != null && session.isDirty() && getParentActivity() != null) {
            confirmLeave();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    /** The engine's own parser has the last word. Returns the JSON, or null after telling the person why not. */
    private String validated(String json, String what) {
        try {
            SkinSpec.parse(json);
            return json;
        } catch (IllegalArgumentException e) {
            Toast.makeText(getParentActivity(), tr("invalid") + (what == null ? "" : what + ": ") + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private void useOnPhone() {
        final String json = validated(session.effectiveJson(), null);
        if (json == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity()).setMessage(tr("useConfirm"))
                .setPositiveButton(tr("done"), (d, w) -> {
                    endLive();
                    try {
                        KuroSkinManager.get().apply(json);
                        Toast.makeText(getParentActivity(), tr("applied"), Toast.LENGTH_SHORT).show();
                    } catch (RuntimeException e) {
                        Toast.makeText(getParentActivity(), tr("invalid") + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton(tr("cancel"), null).show();
    }

    private void next() {
        final String json = validated(session.draft.toJson(), null);
        if (json == null) {
            return;
        }
        // every style and colourway must also make a valid skin on its own (the store checks the same on upload)
        for (StyleSet.Style s : session.styles.styles()) {
            if (validated(session.mergedJson(s.id, null), s.name) == null) {
                return;
            }
            for (StyleSet.Way w : s.ways) {
                if (validated(session.mergedJson(s.id, w.id), s.name + " / " + w.name) == null) {
                    return;
                }
            }
        }
        endLive();
        session.refreshSwatches();
        saveDraft();
        ThemeUploadActivity upload = editing == null ? new ThemeUploadActivity() : new ThemeUploadActivity(editing);
        upload.setPrefill(json);
        if (!session.styles.isEmpty() || session.styles.isDirty()) {
            upload.setPrefillStyles(session.styles.toJson());
        }
        presentFragment(upload);
    }
}
