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
import tw.kurocore.kurogram.stickerstore.ThemePublisher;

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
    private TextView undoBtn, redoBtn, sceneBtn, variantBtn, lookBtn, hint;
    private TextView modeItem, readItem, liveItem; // entries of the three-dots menu whose words change
    private boolean publishing;

    // soft pastel palette: fill, stronger tone of the same colour, and the two text colours
    private static final int[] PASTEL = {0xFFE9E3FF, 0xFFDDF5E6, 0xFFFFE5D9, 0xFFDCEEFF, 0xFFFFE0EC, 0xFFFFF3CC};
    private static final int[] DEEP = {0xFF8E7CF0, 0xFF5CBF8A, 0xFFF09A72, 0xFF5AA9E6, 0xFFEE7FA6, 0xFFE6B422};
    private static final int INK = 0xFF3B3558;
    private static final int SOFT = 0xFF8A84A3;
    private static final int PAGE = 0xFFF7F4FF;
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
        org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, tr("next"));
        org.telegram.ui.ActionBar.ActionBarMenuItem more = menu.addItem(2, R.drawable.ic_ab_other);
        modeItem = more.addSubItem(11, tr("menu.advanced"));
        more.addSubItem(12, tr("language"));
        readItem = more.addSubItem(13, tr("contrast.title"));
        liveItem = more.addSubItem(14, tr("live"));
        more.addSubItem(15, tr("use"));
        more.addSubItem(16, tr("startOver"));
        if (editing != null) {
            more.addSubItem(17, tr("removeStore"));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    confirmLeave();
                } else if (id == 1) {
                    next();
                } else if (id == 11) {
                    session.setAdvanced(!session.advanced());
                    refreshAll();
                } else if (id == 12) {
                    session.setLang("my".equals(session.lang()) ? "en" : "my");
                    prefs().edit().putString(KEY_LANG, session.lang()).apply();
                    actionBar.setTitle(tr("title"));
                    refreshAll();
                } else if (id == 13) {
                    readabilityDialog();
                } else if (id == 14) {
                    setLive(!live);
                } else if (id == 15) {
                    useOnPhone();
                } else if (id == 16) {
                    startOver();
                } else if (id == 17) {
                    removeFromStore();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(PAGE);
        fragmentView = root;
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // three big, friendly choices; everything else lives in the three-dots menu
        LinearLayout pills = new LinearLayout(context);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        sceneBtn = pill(context, v -> {
            final String[] ids = BuilderText.SCENES;
            String[] names = new String[ids.length];
            for (int k = 0; k < ids.length; k++) {
                names[k] = tr("scene." + ids[k]);
            }
            new AlertDialog.Builder(getParentActivity()).setTitle(tr("scene")).setItems(names, (d, which) -> {
                session.setScene(ids[which]);
                refreshAll();
            }).show();
        });
        variantBtn = pill(context, v -> {
            session.setNight(!session.night());
            refreshAll();
        });
        lookBtn = pill(context, v -> lookDialog());
        TextView[] three = {sceneBtn, variantBtn, lookBtn};
        for (int k = 0; k < three.length; k++) {
            pills.addView(three[k], LayoutHelper.createLinear(0, 42, 1f, k == 0 ? 0 : 8, 0, 0, 0));
        }
        col.addView(pills, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 10, 14, 0));

        // the preview sits on a soft rounded card
        FrameLayout card = new FrameLayout(context);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFFFFFFFF);
        cardBg.setCornerRadius(AndroidUtilities.dp(24));
        card.setBackground(cardBg);
        card.setClipToOutline(true);
        card.setElevation(AndroidUtilities.dp(3));
        preview = new BuilderPreviewView(context, session);
        preview.setListener(id -> {
            session.select(id);
            refreshChips();
            refreshPanel();
            preview.invalidate();
        });
        card.addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        col.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 10, 14, 0));

        // hint on the left, undo / redo on the right
        LinearLayout under = new LinearLayout(context);
        under.setOrientation(LinearLayout.HORIZONTAL);
        under.setGravity(Gravity.CENTER_VERTICAL);
        hint = new TextView(context);
        hint.setTextSize(12);
        hint.setTextColor(SOFT);
        hint.setMaxLines(2);
        hint.setOnClickListener(v -> {
            if (!session.readability().isEmpty()) {
                readabilityDialog();
            }
        });
        under.addView(hint, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 8, 0));
        undoBtn = round(context, "↶", v -> {
            session.active().undo();
            changed(false);
        });
        redoBtn = round(context, "↷", v -> {
            session.active().redo();
            changed(false);
        });
        under.addView(undoBtn, LayoutHelper.createLinear(38, 38, 0, 0, 8, 0));
        under.addView(redoBtn, LayoutHelper.createLinear(38, 38));
        col.addView(under, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 8, 14, 6));

        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(2), AndroidUtilities.dp(14), AndroidUtilities.dp(6));
        chipScroll.addView(chips);
        col.addView(chipScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        scroll.addView(list, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        col.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        refreshAll();
        return fragmentView;
    }

    /** A wide soft pill; the three main choices. */
    private TextView pill(Context context, View.OnClickListener l) {
        TextView t = new TextView(context);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(AndroidUtilities.bold());
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        t.setOnClickListener(l);
        return t;
    }

    private TextView round(Context context, String glyph, View.OnClickListener l) {
        TextView t = new TextView(context);
        t.setText(glyph);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(l);
        return t;
    }

    private static GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(AndroidUtilities.dp(radiusDp));
        if (stroke != 0) {
            d.setStroke(AndroidUtilities.dp(2), stroke);
        }
        return d;
    }

    /** Soft fill in the pastel of {@code idx}; selected ones get a stronger outline. */
    private void soft(View t, int idx, boolean selected, int radiusDp) {
        int i = ((idx % PASTEL.length) + PASTEL.length) % PASTEL.length;
        t.setBackground(shape(PASTEL[i], radiusDp, selected ? DEEP[i] : 0));
        if (t instanceof TextView) {
            ((TextView) t).setTextColor(INK);
        }
    }

    // ------------------------------------------------------------------ refresh

    private void refreshAll() {
        refreshToolbar();
        refreshChips();
        refreshPanel();
        preview.invalidate();
    }

    private void refreshToolbar() {
        sceneBtn.setText("📱 " + tr("scene." + session.scene()));
        soft(sceneBtn, 3, false, 21);
        variantBtn.setText(session.night() ? "🌙 " + tr("variant.night") : "☀️ " + tr("variant.day"));
        soft(variantBtn, session.night() ? 0 : 5, session.night(), 21);
        lookBtn.setText("🎨 " + lookName());
        soft(lookBtn, 4, !session.onBase(), 21);
        soft(undoBtn, 0, false, 19);
        soft(redoBtn, 0, false, 19);
        undoBtn.setAlpha(session.active().canUndo() ? 1f : 0.35f);
        redoBtn.setAlpha(session.active().canRedo() ? 1f : 0.35f);
        int issues = session.readability().size();
        if (modeItem != null) {
            modeItem.setText(tr(session.advanced() ? "menu.basic" : "menu.advanced"));
            readItem.setText(tr("contrast.title") + (issues > 0 ? "  ⚠ " + issues : "  ✓"));
            liveItem.setText(tr("live") + (live ? "  ✓" : ""));
        }
        hint.setText(live ? tr("live.on") : issues > 0 ? "⚠ " + tr("contrast.low") + " (" + issues + ")" : tr("simulated"));
        hint.setTextColor(!live && issues > 0 ? 0xFFD9822B : SOFT);
    }

    private void refreshChips() {
        chips.removeAllViews();
        chipIndex = 0;
        Context context = chips.getContext();
        addChip(context, "global", session.catalog.componentLabel("global", session.lang()));
        for (String id : session.browsableComponents()) {
            if (!"global".equals(id)) {
                addChip(context, id, session.catalog.componentLabel(id, session.lang()));
            }
        }
    }

    private int chipIndex;

    private void addChip(Context context, String id, String label) {
        TextView t = new TextView(context);
        t.setText(label);
        t.setTextSize(14);
        t.setSingleLine(true);
        boolean on = id.equals(session.selected());
        t.setTypeface(on ? AndroidUtilities.bold() : android.graphics.Typeface.DEFAULT);
        t.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(9), AndroidUtilities.dp(16), AndroidUtilities.dp(9));
        soft(t, chipIndex++, on, 18);
        t.setOnClickListener(v -> {
            session.select(id);
            refreshChips();
            refreshPanel();
            preview.invalidate();
        });
        chips.addView(t, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
    }

    private void refreshPanel() {
        list.removeAllViews();
        Context context = list.getContext();
        String lang = session.lang();
        TextView head = text(context, "✨ " + session.catalog.componentLabel(session.selected(), lang), 19, true);
        list.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 6, 20, 10));
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
        t.setTextColor(bold ? INK : SOFT);
        if (bold) {
            t.setTypeface(AndroidUtilities.bold());
        }
        return t;
    }

    private LinearLayout row(Context context, String label, String sub) {
        LinearLayout r = new LinearLayout(context);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(AndroidUtilities.dp(64));
        r.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        r.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),
                shape(0xFFFFFFFF, 20, 0), shape(0xFFFFFFFF, 20, 0)));
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(context, label, 16, true));
        if (sub != null && !sub.isEmpty()) {
            words.addView(text(context, sub, 13, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        } else {
            words.addView(text(context, "", 13, false));
        }
        r.addView(words, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        list.addView(r, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 10));
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
            r.addView(sw, LayoutHelper.createLinear(34, 34, 8, 0, 8, 0));
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
            TextView x = text(context, "✕", 16, false);
            x.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8));
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
        r.addView(sb, 1, LayoutHelper.createLinear(130, LayoutHelper.WRAP_CONTENT));
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

    /** Warns, never blocks: a theme with low contrast can still be published. */
    private void readabilityDialog() {
        List<EditorSession.Readability> list = session.readability();
        StringBuilder sb = new StringBuilder();
        for (EditorSession.Readability r : list) {
            sb.append(r.night ? "🌙 " : "☀ ")
                    .append(session.catalog.entryLabel(r.textPath, session.lang())).append(" / ")
                    .append(session.catalog.entryLabel(r.backgroundPath, session.lang())).append(": ")
                    .append(String.format(java.util.Locale.US, "%.1f", r.ratio)).append(" < ").append(String.format(java.util.Locale.US, "%.1f", r.minimum)).append('\n');
        }
        new AlertDialog.Builder(getParentActivity()).setTitle(tr("contrast.title"))
                .setMessage(list.isEmpty() ? tr("contrast.ok") : tr("contrast.low") + ":\n\n" + sb)
                .setPositiveButton(tr("done"), null).show();
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
        if (ThemePublisher.nameOf(json).isEmpty()) {
            session.select("global");
            refreshAll();
            Toast.makeText(getParentActivity(), tr("needName"), Toast.LENGTH_LONG).show();
            return;
        }
        endLive();
        session.refreshSwatches();
        saveDraft();
        final String stylesJson = !session.styles.isEmpty() || session.styles.isDirty() ? session.styles.toJson() : null;
        final String finalJson = json;
        new AlertDialog.Builder(getParentActivity())
                .setTitle(ThemePublisher.nameOf(json))
                .setMessage(tr(editing == null ? "publishAsk" : "updateAsk"))
                .setPositiveButton(tr(editing == null ? "publish" : "update"), (d, w) -> publish(finalJson, stylesJson))
                .setNegativeButton(tr("cancel"), null).show();
    }

    private AlertDialog busyDialog(String message) {
        AlertDialog dlg = new AlertDialog.Builder(getParentActivity()).setMessage(message).setCancelable(false).create();
        dlg.show();
        return dlg;
    }

    private void publish(String json, String stylesJson) {
        if (publishing) {
            return;
        }
        publishing = true;
        final AlertDialog dlg = busyDialog(tr("publishing"));
        ThemePublisher.publish(currentAccount, editing, json, stylesJson, new ThemePublisher.Callback() {
            @Override
            public void done(String warning) {
                publishing = false;
                dlg.dismiss();
                if (fragmentView == null) {
                    return;
                }
                if (warning != null) {
                    Toast.makeText(getParentActivity(), tr("stylesWarn") + warning, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getParentActivity(), tr("published"), Toast.LENGTH_SHORT).show();
                }
                prefs().edit().remove(KEY_DRAFT).remove(KEY_STYLES).apply(); // it is in the store now; the next new theme starts fresh
                finishFragment();
            }

            @Override
            public void failed(String message) {
                publishing = false;
                dlg.dismiss();
                if (fragmentView != null) {
                    Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void removeFromStore() {
        new AlertDialog.Builder(getParentActivity()).setTitle(tr("removeStore")).setMessage(tr("removeAsk"))
                .setPositiveButton(tr("done"), (d, w) -> {
                    final AlertDialog dlg = busyDialog(tr("publishing"));
                    ThemePublisher.remove(currentAccount, editing, new ThemePublisher.Callback() {
                        @Override
                        public void done(String warning) {
                            dlg.dismiss();
                            if (fragmentView != null) {
                                Toast.makeText(getParentActivity(), tr("removed"), Toast.LENGTH_SHORT).show();
                                finishFragment();
                            }
                        }

                        @Override
                        public void failed(String message) {
                            dlg.dismiss();
                            if (fragmentView != null) {
                                Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }).setNegativeButton(tr("cancel"), null).show();
    }
}
