package tw.kurocore.kurogram.skin.editor.ui;

import android.app.AlertDialog;
import android.app.Dialog;
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
import tw.kurocore.kurogram.skin.editor.StudioModel;
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
    private static final String KEY_STYLES = "styles";
    private static final String TEMPLATE = "{\"schemaVersion\":2,\"meta\":{\"id\":\"my-theme\",\"name\":\"My Theme\"},\"tokens\":{\"colors\":{\"accent\":\"#3390EC\"}}}";

    /** A blank theme: what "start from scratch" opens. */
    public static String blankTheme() {
        return TEMPLATE;
    }

    /** True when an unfinished draft from an earlier session is saved on this phone. */
    public static boolean hasDraft() {
        try {
            return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DRAFT, null) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private final String startJson; // an existing theme to edit, or null
    private final tw.kurocore.kurogram.stickerstore.ThemeInfo editing; // its store entry when editing
    private final String startStyles; // the styles of a theme being edited (JSON list) or null
    private final PreviewGuard guard = new PreviewGuard();
    private EditorSession session;
    private BuilderPreviewView preview;
    private static final int PANEL_DP = 340;
    private static final int INK = 0xFF1F2328;
    private static final int SOFT = 0xFF6B7280;
    private static final int PAGE = 0xFFF4F5F7;
    private static final int LINE = 0xFFE5E7EB;
    private static final int BLUE = 0xFF2F80ED;
    private static final int BLUE_SOFT = 0xFFEAF2FF;

    private boolean wide;
    private boolean editMode = true;
    private FrameLayout root;
    private LinearLayout col, sheet, sectionRow, list;
    private FrameLayout canvasArea;
    private ScrollView inspScroll;
    private HorizontalScrollView sectionScroll;
    private TextView dayBtn, editBtn, viewBtn, warnChip, liveBtn, hint, sheetTitle, sheetPlace, screensTab;
    private org.telegram.ui.ActionBar.ActionBarMenuItem undoItem, redoItem;
    private LinearLayout idleBar, pill;
    private FrameLayout canvasFrame;
    private LinearLayout phoneHolder;
    private FrameLayout phone;
    private boolean inspecting;   // a part (or the global colours) is open in the inspector; otherwise the 3-tab bar shows
    private boolean globalPanel;  // the inspector shows only the quick global colours
    private static final String[] GLOBAL_COLORS = {"tokens.colors.accent", "tokens.colors.background", "tokens.colors.surface", "tokens.colors.textPrimary"};
    private TextView levelItem, readItem, liveItem, lookItem; // entries of the three-dots menu whose words change
    private int sheetState; // 0 collapsed, 1 half, 2 full
    private android.animation.ValueAnimator sheetAnim;
    private StudioModel.Section section = StudioModel.Section.APPEARANCE;
    private String focusPath; // a setting to mark when the inspector opens on it
    private View focusRow;
    private java.util.Map<String, Object> styleClip;
    private final java.util.Set<String> collapsedLayers = new java.util.HashSet<>();
    private boolean publishing;
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

    // ------------------------------------------------------------------ the studio shell

    @Override
    public View createView(Context context) {
        wide = AndroidUtilities.displaySize.x >= AndroidUtilities.dp(840);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitleActionRunnable(this::renameDialog);
        org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
        undoItem = menu.addItem(3, "\u21B6");
        redoItem = menu.addItem(4, "\u21B7");
        menu.addItem(1, tr("next"));
        org.telegram.ui.ActionBar.ActionBarMenuItem more = menu.addItem(2, R.drawable.ic_ab_other);
        more.addSubItem(11, tr("search"));
        more.addSubItem(12, tr("rename"));
        more.addSubItem(13, tr("device"));
        levelItem = more.addSubItem(14, tr("menu.advanced"));
        readItem = more.addSubItem(16, tr("contrast.title"));
        liveItem = more.addSubItem(17, tr("live"));
        more.addSubItem(18, tr("use"));
        more.addSubItem(19, tr("startOver"));
        if (editing != null) {
            more.addSubItem(20, tr("removeStore"));
        }
        lookItem = more.addSubItem(21, tr("look"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                switch (id) {
                    case -1:
                        confirmLeave();
                        break;
                    case 1:
                        next();
                        break;
                    case 3:
                        session.active().undo();
                        changed(false);
                        break;
                    case 4:
                        session.active().redo();
                        changed(false);
                        break;
                    case 11:
                        searchDialog();
                        break;
                    case 12:
                        renameDialog();
                        break;
                    case 13:
                        deviceDialog();
                        break;
                    case 14:
                        session.setAdvanced(!session.advanced());
                        refreshAll();
                        break;
                    case 16:
                        warningsDialog();
                        break;
                    case 17:
                        setLive(!live);
                        break;
                    case 18:
                        useOnPhone();
                        break;
                    case 19:
                        startOver();
                        break;
                    case 20:
                        removeFromStore();
                        break;
                    case 21:
                        lookDialog();
                        break;
                    default:
                        break;
                }
            }
        });

        root = new FrameLayout(context);
        root.setBackgroundColor(PAGE);
        fragmentView = root;
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) {
                applySheet(false);
            }
        });
        col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START, 0, 0, wide ? PANEL_DP : 0, 0));

        // the canvas: a phone frame that is scaled / shifted so the selected part is never under the sheet
        canvasArea = new FrameLayout(context);
        canvasArea.setClipChildren(true);
        phoneHolder = new LinearLayout(context);
        phoneHolder.setOrientation(LinearLayout.VERTICAL);
        phoneHolder.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        phone = new FrameLayout(context);
        phone.setBackground(shape(0xFF1F2328, 30, 0));
        phone.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        phone.setElevation(AndroidUtilities.dp(6));
        phone.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot || r - l != or - ol) {
                fitCanvas(false);
            }
        });
        FrameLayout screenClip = new FrameLayout(context);
        screenClip.setBackground(shape(0xFFFFFFFF, 24, 0));
        screenClip.setClipToOutline(true);
        preview = new BuilderPreviewView(context, session);
        preview.setListener(this::onPicked);
        preview.setNavigatedListener(this::refreshAll);
        screenClip.addView(preview, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        phone.addView(screenClip, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        phoneHolder.addView(phone, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 52, 0, 0));
        canvasArea.addView(phoneHolder, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        canvasArea.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot || r - l != or - ol) {
                fitCanvas(false);
            }
        });
        col.addView(canvasArea, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        // floating pill over the canvas: Day/Night and Edit | Preview
        pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(shape(0xFFFFFFFF, 22, LINE));
        pill.setElevation(AndroidUtilities.dp(4));
        pill.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        dayBtn = pillButton(context, "\u2600", tr("variant.day"), v -> {
            String keep = session.selected();
            session.setNight(!session.night());
            session.select(keep);
            refreshAll();
        });
        editBtn = pillButton(context, "\u270E", tr("mode.edit"), v -> setEditMode(true));
        viewBtn = pillButton(context, "\u25B6", tr("mode.preview"), v -> setEditMode(false));
        View divider = new View(context);
        divider.setBackgroundColor(LINE);
        pill.addView(dayBtn, LayoutHelper.createLinear(38, 36));
        pill.addView(divider, LayoutHelper.createLinear(1, 20, 4, 0, 4, 0));
        pill.addView(editBtn, LayoutHelper.createLinear(38, 36));
        pill.addView(viewBtn, LayoutHelper.createLinear(38, 36));
        root.addView(pill, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.END, 0, 8, 12 + (wide ? PANEL_DP : 0), 0));

        // a small badge instead of a banner: it opens the details only when tapped
        warnChip = new TextView(context);
        warnChip.setTextSize(13);
        warnChip.setTypeface(AndroidUtilities.bold());
        warnChip.setTextColor(0xFF8A5A00);
        warnChip.setGravity(Gravity.CENTER);
        warnChip.setBackground(shape(0xFFFFF0D6, 18, 0xFFF2C46B));
        warnChip.setElevation(AndroidUtilities.dp(3));
        warnChip.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        warnChip.setOnClickListener(v -> warningsDialog());
        root.addView(warnChip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.START, 12, 10, 0, 0));

        hint = new TextView(context);
        hint.setTextSize(12);
        hint.setTextColor(SOFT);
        hint.setGravity(Gravity.CENTER);
        hint.setBackground(shape(0xE6FFFFFF, 14, 0));
        hint.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(5), AndroidUtilities.dp(12), AndroidUtilities.dp(5));
        liveBtn = new TextView(context);
        liveBtn.setTextSize(14);
        liveBtn.setTypeface(AndroidUtilities.bold());
        liveBtn.setGravity(Gravity.CENTER);
        liveBtn.setElevation(AndroidUtilities.dp(3));
        liveBtn.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        liveBtn.setOnClickListener(v -> setLive(!live));
        FrameLayout.LayoutParams hp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, wide ? PANEL_DP : 0, 14);
        canvasArea.addView(hint, hp);
        canvasArea.addView(liveBtn, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 42, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        buildSheet(context);
        buildIdleBar(context);
        refreshAll();
        applySheet(false);
        return fragmentView;
    }

    private TextView pillButton(Context context, String glyph, String description, View.OnClickListener l) {
        TextView t = new TextView(context);
        t.setText(glyph);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(description);
        t.setOnClickListener(l);
        return t;
    }

    /** What shows while nothing is selected: quick global colours, the screen switcher, and the layers. */
    private void buildIdleBar(Context context) {
        idleBar = new LinearLayout(context);
        idleBar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        float r = AndroidUtilities.dp(22);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        idleBar.setBackground(bg);
        idleBar.setElevation(AndroidUtilities.dp(10));
        idleBar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        TextView colours = idleTab(context, "\uD83C\uDFA8", tr("globalColors"), v -> openGlobalColors());
        screensTab = idleTab(context, "\u25A6", "", v -> screensDialog());
        TextView layers = idleTab(context, "\u2630", tr("layers"), v -> layersDialog());
        idleBar.addView(colours, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        idleBar.addView(screensTab, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        idleBar.addView(layers, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        root.addView(idleBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, IDLE_DP, Gravity.BOTTOM, 0, 0, wide ? PANEL_DP : 0, 0));
    }

    private TextView idleTab(Context context, String glyph, String label, View.OnClickListener l) {
        TextView t = new TextView(context);
        t.setText(glyph + "\n" + label);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(INK);
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setOnClickListener(l);
        return t;
    }

    private void openGlobalColors() {
        session.select("global");
        inspecting = true;
        globalPanel = true;
        if (!wide) {
            sheetState = 1;
        }
        applySheet(true);
        refreshPanel();
        preview.invalidate();
    }

    /** Deselects: the inspector closes and the 3-tab bar comes back. */
    private void closeInspector() {
        inspecting = false;
        globalPanel = false;
        session.select("global");
        applySheet(true);
        refreshPanel();
        preview.invalidate();
        fitCanvas(true);
    }

    private static GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(AndroidUtilities.dp(radiusDp));
        if (stroke != 0) {
            d.setStroke(AndroidUtilities.dp(1), stroke);
        }
        return d;
    }

    /** A row of options inside a soft rounded box; the chosen one is lifted. */
    private LinearLayout segmented(Context context, String[] labels, int selected, final IntCallback cb) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackground(shape(0xFFEEF0F3, 12, 0));
        box.setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3));
        for (int i = 0; i < labels.length; i++) {
            final int at = i;
            TextView t = new TextView(context);
            t.setText(labels[i]);
            t.setTextSize(13);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            boolean on = i == selected;
            t.setTextColor(on ? INK : SOFT);
            t.setTypeface(on ? AndroidUtilities.bold() : android.graphics.Typeface.DEFAULT);
            if (on) {
                t.setBackground(shape(0xFFFFFFFF, 10, 0));
                t.setElevation(AndroidUtilities.dp(1));
            }
            t.setOnClickListener(v -> cb.picked(at));
            box.addView(t, LayoutHelper.createLinear(0, 34, 1f));
        }
        return box;
    }

    private interface IntCallback {
        void picked(int index);
    }

    // ------------------------------------------------------------------ the inspector sheet

    private void buildSheet(Context context) {
        sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        float top = wide ? 0 : AndroidUtilities.dp(22);
        bg.setCornerRadii(new float[]{top, top, top, top, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setElevation(AndroidUtilities.dp(10));

        // header: drag handle, name and place of the selected part, a menu for reset / copy / paste
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        View handle = new View(context);
        handle.setBackground(shape(0xFFD5D8DD, 2, 0));
        if (!wide) {
            header.addView(handle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 10, 0));
        }
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(context);
        names.setOrientation(LinearLayout.VERTICAL);
        sheetTitle = new TextView(context);
        sheetTitle.setTextSize(17);
        sheetTitle.setTextColor(INK);
        sheetTitle.setTypeface(AndroidUtilities.bold());
        sheetTitle.setSingleLine(true);
        sheetTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sheetPlace = new TextView(context);
        sheetPlace.setTextSize(12);
        sheetPlace.setTextColor(SOFT);
        sheetPlace.setSingleLine(true);
        sheetPlace.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(sheetTitle);
        names.addView(sheetPlace);
        titleRow.addView(names, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView menuBtn = new TextView(context);
        menuBtn.setText("⋯");
        menuBtn.setTextSize(22);
        menuBtn.setTextColor(INK);
        menuBtn.setGravity(Gravity.CENTER);
        menuBtn.setContentDescription(tr("more"));
        menuBtn.setOnClickListener(v -> partMenu(v));
        titleRow.addView(menuBtn, LayoutHelper.createLinear(44, 44));
        TextView closeBtn = new TextView(context);
        closeBtn.setText("\u2715");
        closeBtn.setTextSize(17);
        closeBtn.setTextColor(INK);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setContentDescription(tr("close"));
        closeBtn.setBackground(shape(0xFFF1F2F4, 18, 0));
        closeBtn.setOnClickListener(v -> closeInspector());
        titleRow.addView(closeBtn, LayoutHelper.createLinear(36, 36, 4, 0, 6, 0));
        header.addView(titleRow);
        sheet.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (!wide) {
            header.setOnTouchListener(new View.OnTouchListener() {
                float downY;
                int startH;
                boolean moved;

                @Override
                public boolean onTouch(View v, android.view.MotionEvent e) {
                    switch (e.getAction()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            downY = e.getRawY();
                            startH = sheet.getLayoutParams().height;
                            moved = false;
                            return true;
                        case android.view.MotionEvent.ACTION_MOVE:
                            if (Math.abs(e.getRawY() - downY) > AndroidUtilities.dp(8)) {
                                moved = true;
                            }
                            if (moved) {
                                setSheetHeight(Math.max(sheetHeightFor(0), Math.min(sheetHeightFor(2), startH + (int) (downY - e.getRawY()))));
                            }
                            return true;
                        case android.view.MotionEvent.ACTION_UP:
                            if (!moved) {
                                setSheetState(sheetState == 0 ? 1 : sheetState == 1 ? 2 : 1, true);
                            } else {
                                int h = sheet.getLayoutParams().height, best = 0;
                                for (int s = 1; s <= 2; s++) {
                                    if (Math.abs(sheetHeightFor(s) - h) < Math.abs(sheetHeightFor(best) - h)) {
                                        best = s;
                                    }
                                }
                                setSheetState(best, true);
                            }
                            return true;
                        default:
                            return true;
                    }
                }
            });
        }

        sectionScroll = new HorizontalScrollView(context);
        sectionScroll.setHorizontalScrollBarEnabled(false);
        sectionRow = new LinearLayout(context);
        sectionRow.setOrientation(LinearLayout.HORIZONTAL);
        sectionScroll.addView(sectionRow);
        sheet.addView(sectionScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 6));

        inspScroll = new ScrollView(context);
        inspScroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, AndroidUtilities.dp(32));
        inspScroll.addView(list, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        sheet.addView(inspScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        if (wide) {
            root.addView(sheet, LayoutHelper.createFrame(PANEL_DP, LayoutHelper.MATCH_PARENT, Gravity.END | Gravity.TOP));
        } else {
            root.addView(sheet, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM));
        }
    }

    private static final int IDLE_DP = 64;

    private int sheetHeightFor(int state) {
        int rh = Math.max(root.getHeight(), AndroidUtilities.dp(480));
        if (state == 0) {
            return AndroidUtilities.dp(82);
        }
        return state == 1 ? Math.max(AndroidUtilities.dp(250), (int) (rh * 0.44f)) : (int) (rh * 0.86f);
    }

    private void setSheetHeight(int px) {
        if (wide || sheet == null) {
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        lp.height = px;
        sheet.setLayoutParams(lp);
        LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) canvasArea.getLayoutParams();
        cp.bottomMargin = editMode && inspecting ? px : editMode ? AndroidUtilities.dp(IDLE_DP) : 0;
        canvasArea.setLayoutParams(cp);
        boolean open = px > sheetHeightFor(0) + AndroidUtilities.dp(20);
        sectionScroll.setVisibility(open ? View.VISIBLE : View.GONE);
        inspScroll.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    private void setSheetState(int state, boolean animate) {
        sheetState = state;
        if (wide) {
            return;
        }
        final int target = sheetHeightFor(state);
        if (sheetAnim != null) {
            sheetAnim.cancel();
        }
        if (!animate || sheet.getLayoutParams().height <= 0) {
            setSheetHeight(target);
            return;
        }
        sheetAnim = android.animation.ValueAnimator.ofInt(sheet.getLayoutParams().height, target);
        sheetAnim.setDuration(180);
        sheetAnim.addUpdateListener(a -> setSheetHeight((Integer) a.getAnimatedValue()));
        sheetAnim.start();
    }

    /** Shows the right bottom element: nothing in Preview mode, the 3-tab bar when idle, the inspector when a part is open. */
    private void applySheet(boolean animate) {
        if (sheet == null) {
            return;
        }
        boolean showSheet = editMode && (inspecting || wide);
        sheet.setVisibility(showSheet ? View.VISIBLE : View.GONE);
        idleBar.setVisibility(editMode && !inspecting ? View.VISIBLE : View.GONE);
        if (!wide) {
            if (inspecting) {
                setSheetState(Math.max(sheetState, 0), animate);
            } else {
                setSheetHeight(sheetHeightFor(0)); // keeps the canvas margin right for the idle bar
            }
        } else {
            LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) canvasArea.getLayoutParams();
            cp.bottomMargin = editMode && !inspecting ? AndroidUtilities.dp(IDLE_DP) : 0;
            canvasArea.setLayoutParams(cp);
        }
        fitCanvas(animate);
    }

    private void setEditMode(boolean edit) {
        if (editMode == edit) {
            return;
        }
        editMode = edit;
        preview.setEditMode(edit);
        if (!edit) {
            sheetState = Math.min(sheetState, 1);
        }
        applySheet(false);
        refreshToolbar();
    }

    /**
     * Keeps what matters in view: the whole phone when nothing is selected, or the selected part when the inspector is open.
     * The phone is shifted up first and shrunk only when the part is taller than the room left above the sheet.
     */
    private void fitCanvas(boolean animate) {
        if (phone == null || canvasArea.getHeight() == 0 || phone.getHeight() == 0) {
            return;
        }
        final float top = AndroidUtilities.dp(52);          // the pill's room, where the phone starts
        float avail = canvasArea.getHeight() - top - AndroidUtilities.dp(10);
        float phoneH = phone.getHeight();
        float a = 0, b = phoneH;
        android.graphics.RectF sel = editMode && inspecting && !"global".equals(session.selected()) && !"screenBackground".equals(session.selected())
                ? preview.selectedBounds() : null;
        if (sel != null) {
            float pad = AndroidUtilities.dp(6);
            a = pad + sel.top - AndroidUtilities.dp(8);
            b = pad + sel.bottom + AndroidUtilities.dp(8);
        }
        float s = 1f, ty = 0f;
        if (sel == null) {
            s = Math.min(1f, avail / phoneH);
        } else if (b > avail) {
            if (b - a <= avail) {
                ty = avail - b;
            } else {
                s = Math.max(0.5f, avail / (b - a));
                ty = Math.min(0f, avail - b * s);
            }
        }
        phone.setPivotX(phone.getWidth() / 2f);
        phone.setPivotY(0f);
        if (animate) {
            phone.animate().scaleX(s).scaleY(s).translationY(ty).setDuration(180).start();
        } else {
            phone.animate().cancel();
            phone.setScaleX(s);
            phone.setScaleY(s);
            phone.setTranslationY(ty);
        }
    }

    // ------------------------------------------------------------------ refresh

    private void refreshAll() {
        refreshToolbar();
        refreshPanel();
        preview.invalidate();
        if (phone != null) {
            phone.post(() -> fitCanvas(false));
        }
    }

    private void refreshToolbar() {
        actionBar.setTitle(themeName());
        actionBar.setSubtitle(tr(editing != null ? "draft.published" : "draft.saved") + (session.onBase() ? "" : "  \u00B7  " + lookName()));
        dayBtn.setText(session.night() ? "\uD83C\uDF19" : "\u2600");
        dayBtn.setContentDescription(tr(session.night() ? "variant.night" : "variant.day"));
        dayBtn.setBackground(shape(session.night() ? 0xFF1F2328 : 0xFFFFFFFF, 18, 0));
        dayBtn.setTextColor(session.night() ? 0xFFFFFFFF : INK);
        undoItem.setAlpha(session.active().canUndo() ? 1f : 0.35f);
        redoItem.setAlpha(session.active().canRedo() ? 1f : 0.35f);
        for (TextView b : new TextView[]{editBtn, viewBtn}) {
            boolean on = (b == editBtn) == editMode;
            b.setTextColor(on ? 0xFFFFFFFF : SOFT);
            b.setBackground(on ? shape(BLUE, 18, 0) : null);
        }
        int issues = session.readability().size();
        warnChip.setVisibility(issues > 0 && editMode ? View.VISIBLE : View.GONE);
        warnChip.setText("\u26A0 " + issues);
        warnChip.setContentDescription(tr("contrast.low"));
        if (levelItem != null) {
            levelItem.setText(tr(session.advanced() ? "menu.basic" : "menu.advanced"));
            readItem.setText(tr("contrast.title") + (issues > 0 ? "  \u26A0 " + issues : "  \u2713"));
            liveItem.setText(tr("live") + (live ? "  \u2713" : ""));
            lookItem.setText(tr("look") + ": " + lookName());
        }
        screensTab.setText("\u25A6\n" + tr("activities") + " \u25BE");
        hint.setText(live ? tr("live.on") : tr("tapHint"));
        hint.setVisibility(editMode && !inspecting && !live ? View.VISIBLE : View.GONE);
        liveBtn.setVisibility(editMode ? View.GONE : View.VISIBLE);
        liveBtn.setText(live ? "\u25A0  " + tr("live.off") : "\u25B6  " + tr("live"));
        liveBtn.setTextColor(live ? 0xFFFFFFFF : BLUE);
        liveBtn.setBackground(shape(live ? BLUE : 0xFFFFFFFF, 21, live ? 0 : BLUE));
    }

    private String themeName() {
        Object n = session.draft.get("meta.name");
        return n == null || String.valueOf(n).isEmpty() ? tr("title") : String.valueOf(n);
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

    private void onPicked(String id) {
        session.select(id);
        focusPath = null;
        inspecting = true;
        globalPanel = false;
        if (!wide && sheetState == 0) {
            sheetState = 1;
        }
        applySheet(true);
        refreshPanel();
        refreshToolbar();
        preview.invalidate();
        if (phone != null) {
            phone.post(() -> fitCanvas(true));
        }
    }

    /** Where the selected part is: screen, then its parent part when it has one. */
    private String placeOf(String id) {
        String lang = session.lang();
        String place = tr("scene." + session.scene());
        int dot = id.lastIndexOf('.');
        if (dot > 0 && session.catalog.hasComponent(id.substring(0, dot))) {
            place += "  ›  " + session.catalog.componentLabel(id.substring(0, dot), lang);
        }
        return place;
    }

    private void refreshPanel() {
        final int keepScroll = inspScroll.getScrollY();
        list.removeAllViews();
        sectionRow.removeAllViews();
        Context context = list.getContext();
        String lang = session.lang();
        String sel = session.selected();
        final boolean quick = globalPanel || (wide && !inspecting);
        sheetTitle.setText(quick ? tr("globalColors") : session.catalog.componentLabel(sel, lang));
        sheetPlace.setText(quick ? tr("scene." + session.scene()) : placeOf(sel));
        if (quick) {
            focusRow = null;
            sectionRow.removeAllViews();
            for (String path : GLOBAL_COLORS) {
                Catalog.Entry e = session.catalog.entry(path);
                RuleSet.Ref ref = session.rules.find(path);
                if (e != null && ref != null) {
                    addRuleRow(context, e, ref.rule);
                }
            }
            LinearLayout moreBox = rowBox(context);
            headLine(context, moreBox, tr("moreGlobal"), null);
            moreBox.setOnClickListener(v -> {
                globalPanel = false;
                inspecting = true;
                session.select("global");
                refreshAll();
            });
            return;
        }

        final boolean globalRows = "global".equals(sel) && !session.night();
        List<Catalog.Entry> entries = new java.util.ArrayList<>();
        for (Catalog.Entry e : session.settings()) {
            if (e.path.equals("appearance") || e.path.equals("meta") || e.path.equals("night") || e.path.equals("layout")) {
                continue; // handled by their own rows
            }
            if (session.rules.find(e.path) != null) {
                entries.add(e);
            }
        }
        List<StudioModel.Section> sections = StudioModel.sectionsOf(session, entries);
        if (globalRows && !sections.contains(StudioModel.Section.APPEARANCE)) {
            sections.add(0, StudioModel.Section.APPEARANCE);
        }
        if (sections.isEmpty()) {
            list.addView(text(context, tr("sheet.none"), 14, false), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 12, 20, 8));
            return;
        }
        if (!sections.contains(section)) {
            section = sections.get(0);
        }
        if (sections.size() > 1) {
            String[] labels = new String[sections.size()];
            int at = 0;
            for (int i = 0; i < labels.length; i++) {
                labels[i] = tr(StudioModel.sectionKey(sections.get(i)));
                if (sections.get(i) == section) {
                    at = i;
                }
            }
            final List<StudioModel.Section> shown = sections;
            sectionRow.addView(segmented(context, labels, at, i -> {
                section = shown.get(i);
                focusPath = null;
                refreshPanel();
            }), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
        focusRow = null;
        if (globalRows && section == StudioModel.Section.APPEARANCE) {
            addAppearanceRow(context);
            addNameRow(context);
        }
        for (Catalog.Entry e : entries) {
            RuleSet.Ref ref = session.rules.find(e.path);
            if (StudioModel.sectionOf(e, ref.rule) == section) {
                addRuleRow(context, e, ref.rule);
            }
        }
        if (focusRow != null) {
            final View target = focusRow;
            inspScroll.post(() -> inspScroll.scrollTo(0, Math.max(0, target.getTop() - AndroidUtilities.dp(40))));
        } else {
            inspScroll.post(() -> inspScroll.scrollTo(0, keepScroll));
        }
    }

    private LinearLayout rowBox(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        box.setMinimumHeight(AndroidUtilities.dp(56));
        list.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        View line = new View(context);
        line.setBackgroundColor(0xFFEEF0F3);
        list.addView(line, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 18, 0, 0, 0));
        return box;
    }

    /** The first line of a row: the name (with a small note under it) on the left, whatever controls it on the right. */
    private LinearLayout headLine(Context context, LinearLayout box, String label, String note) {
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(context, label, 15, true));
        if (note != null && !note.isEmpty()) {
            words.addView(text(context, note, 12, false));
        }
        head.addView(words, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        box.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return head;
    }

    private void addAppearanceRow(Context context) {
        LinearLayout box = rowBox(context);
        headLine(context, box, tr("appearance"), null);
        final String[] keys = {"auto", "light", "dark"};
        int at = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(session.appearance())) {
                at = i;
            }
        }
        box.addView(segmented(context, new String[]{tr("auto"), tr("light"), tr("dark")}, at, i -> {
            report(session.draft.setStructure("appearance", keys[i]));
            changed(true);
        }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 6, 0));
    }

    private void addNameRow(Context context) {
        LinearLayout box = rowBox(context);
        headLine(context, box, tr("name"), themeName());
        box.setOnClickListener(v -> renameDialog());
    }

    private void renameDialog() {
        final Object n = session.draft.get("meta.name");
        final android.widget.EditText input = new android.widget.EditText(getParentActivity());
        input.setText(n == null ? "" : String.valueOf(n));
        input.setSingleLine(true);
        new AlertDialog.Builder(getParentActivity()).setTitle(tr("name")).setView(input)
                .setPositiveButton(tr("done"), (d, w) -> {
                    report(session.draft.setStructure("meta.name", input.getText().toString().trim()));
                    changed(true);
                }).setNegativeButton(tr("cancel"), null).show();
    }

    private void addRuleRow(Context context, final Catalog.Entry e, final Rule rule) {
        final String label = e.label.get(session.lang(), e.path);
        LinearLayout box = rowBox(context);
        if (e.path.equals(focusPath)) {
            box.setBackgroundColor(BLUE_SOFT);
            focusRow = box;
        }
        if (!session.isSupported(e)) {
            headLine(context, box, label, tr("unsupported"));
            box.setAlpha(0.45f);
            return;
        }
        final Object cur = session.ownValue(e.path);
        Object eff = session.raw(e.path);
        String note = cur != null ? null : eff != null && session.night() ? tr("inherited") + ": " + eff : tr("notSet");
        if (rule.kind == Rule.Kind.LIST && cur == null) {
            note = tr("notSet");
        }
        LinearLayout head = headLine(context, box, label, note);
        if (rule.kind == Rule.Kind.COLOR || rule.kind == Rule.Kind.ARGB) {
            View sw = new View(context);
            Object shownColour = cur != null ? cur : eff;
            int c = shownColour instanceof String ? ColorCodec.parse((String) shownColour, rule.kind) : 0;
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(c);
            d.setStroke(AndroidUtilities.dp(1), 0xFFD5D8DD);
            sw.setBackground(d);
            head.addView(sw, LayoutHelper.createLinear(30, 30, 8, 0, 6, 0));
            box.setOnClickListener(v -> colorDialog(e, rule, label));
        } else if (rule.kind == Rule.Kind.ENUM) {
            final List<String> opts = rule.options;
            if (opts.size() <= 4) {
                int at = opts.indexOf(cur != null ? cur : eff);
                box.addView(segmented(context, opts.toArray(new String[0]), at, i -> {
                    report(session.setValue(e.path, opts.get(i)));
                    changed(true);
                }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 6, 0));
            } else {
                head.addView(text(context, String.valueOf(cur != null ? cur : eff == null ? "" : eff) + "  ›", 13, false));
                box.setOnClickListener(v -> new AlertDialog.Builder(getParentActivity())
                        .setItems(opts.toArray(new String[0]), (d, i) -> {
                            report(session.setValue(e.path, opts.get(i)));
                            changed(true);
                        }).show());
            }
        } else if (rule.kind == Rule.Kind.LIST) {
            if (cur instanceof List) {
                TextView sum = text(context, listSummary(cur), 12, false);
                sum.setSingleLine(true);
                sum.setEllipsize(android.text.TextUtils.TruncateAt.END);
                sum.setMaxWidth(AndroidUtilities.dp(130));
                head.addView(sum);
            }
            head.addView(text(context, "  ›", 16, false));
            box.setOnClickListener(v -> listDialog(e, rule, label));
        } else if (rule.kind == Rule.Kind.INT || rule.kind == Rule.Kind.FLOAT) {
            TextView value = text(context, cur != null ? String.valueOf(cur) : eff != null ? String.valueOf(eff) : "", 13, true);
            head.addView(value);
            addSlider(context, box, value, e, rule, cur != null ? cur : eff);
        } else if (rule.kind == Rule.Kind.BOOL) {
            final android.widget.Switch sw = new android.widget.Switch(context);
            sw.setChecked(Boolean.TRUE.equals(eff));
            sw.setOnCheckedChangeListener((b, on) -> {
                report(session.setValue(e.path, on));
                changed(true);
            });
            head.addView(sw);
        }
        if (cur != null) {
            TextView x = text(context, "↺", 18, false);
            x.setContentDescription(tr("reset"));
            x.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
            x.setOnClickListener(v -> {
                report(session.unsetValue(e.path));
                changed(true);
            });
            head.addView(x);
        }
    }

    /** INT and FLOAT rules: a slider under the name. A whole drag is one undo step; the rule's own range limits it. */
    private void addSlider(Context context, LinearLayout box, final TextView value, final Catalog.Entry e, final Rule rule, Object cur) {
        final boolean isInt = rule.kind == Rule.Kind.INT;
        final double min = rule.min, max = rule.max;
        final int steps = isInt ? (int) (max - min) : 100;
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
                refreshPanel(); // show the reset arrow now that the value is set
            }
        });
        box.addView(sb, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 6, 0));
    }

    // ------------------------------------------------------------------ part menu: copy, paste, reset

    private void partMenu(View anchor) {
        final String sel = session.selected();
        android.widget.PopupMenu pm = new android.widget.PopupMenu(getParentActivity(), anchor);
        pm.getMenu().add(0, 1, 0, tr("copyStyle"));
        pm.getMenu().add(0, 2, 1, tr("pasteStyle")).setEnabled(styleClip != null);
        pm.getMenu().add(0, 3, 2, tr("resetSection"));
        pm.getMenu().add(0, 4, 3, tr("resetComponent"));
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: {
                    java.util.Map<String, Object> copied = StudioModel.copyStyle(session, sel);
                    if (copied.isEmpty()) {
                        Toast.makeText(getParentActivity(), tr("nothingToCopy"), Toast.LENGTH_SHORT).show();
                    } else {
                        styleClip = copied;
                        Toast.makeText(getParentActivity(), tr("copied"), Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                case 2: {
                    int n = StudioModel.pasteStyle(session, sel, styleClip);
                    Toast.makeText(getParentActivity(), n == 0 ? tr("nothingCommon") : tr("pasted"), Toast.LENGTH_SHORT).show();
                    if (n > 0) {
                        changed(true);
                    }
                    return true;
                }
                case 3:
                    confirmReset(sel, section);
                    return true;
                case 4:
                    confirmReset(sel, null);
                    return true;
                default:
                    return false;
            }
        });
        pm.show();
    }

    private void confirmReset(final String component, final StudioModel.Section only) {
        new AlertDialog.Builder(getParentActivity()).setMessage(tr("resetAsk"))
                .setPositiveButton(tr("done"), (d, w) -> {
                    int n = StudioModel.resetComponent(session, component, only);
                    Toast.makeText(getParentActivity(), tr("resetDone") + " (" + n + ")", Toast.LENGTH_SHORT).show();
                    changed(true);
                }).setNegativeButton(tr("cancel"), null).show();
    }

    // ------------------------------------------------------------------ bottom panels: screens, layers, search, warnings, size

    /** A panel that rises from the bottom and covers at most most of the screen. */
    private Dialog panel(View content) {
        Context context = getParentActivity();
        Dialog dlg = new Dialog(context);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        FrameLayout wrap = new FrameLayout(context);
        wrap.setBackground(shape(0xFFFFFFFF, 22, 0));
        wrap.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        wrap.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        dlg.setContentView(wrap);
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.35f);
        }
        return dlg;
    }

    /** The app's own screens, in the order of the real app. */
    private static final String[] MAIN_SCREENS = {"chats", "chat", "contacts", "profile", "settings", "themeStore"};
    /** Screens that open from inside Settings (each is its own activity in the app). */
    private static final String[] SETTINGS_SCREENS = {"rows.privacy", "rows.notifications", "rows.dataStorage", "rows.profile"};
    /** Screens made for Kuro themes only. */
    private static final String[] SUB_SCREENS = {"people", "drawer", "controls"};

    private void screensDialog() {
        boolean sub = java.util.Arrays.asList(SUB_SCREENS).contains(session.scene());
        screensDialog(sub ? 1 : 0);
    }

    /** Two tabs of thumbnails, drawn from the same draft, so each shows what that screen looks like with the theme as it is now. */
    private void screensDialog(final int tab) {
        Context context = getParentActivity();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        final Dialog[] dlg = new Dialog[1];
        box.addView(segmented(context, new String[]{tr("screens.main"), tr("screens.sub")}, tab, i -> {
            if (i != tab) {
                Dialog old = dlg[0];
                screensDialog(i);
                old.dismiss();
            }
        }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));
        if (tab == 0) {
            sceneGrid(context, box, MAIN_SCREENS, dlg);
            box.addView(text(context, tr("screens.fromSettings"), 13, true), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 8, 0, 8));
            sceneGrid(context, box, SETTINGS_SCREENS, dlg);
        } else {
            sceneGrid(context, box, SUB_SCREENS, dlg);
        }
        ScrollView sv = new ScrollView(context);
        sv.addView(box);
        dlg[0] = panel(sv);
        dlg[0].show();
        preview.invalidate();
    }

    private void sceneGrid(Context context, LinearLayout box, String[] ids, Dialog[] dlg) {
        int cols = wide ? 5 : 3;
        int thumbW = AndroidUtilities.dp(wide ? 110 : 96);
        LinearLayout rowLayout = null;
        for (int i = 0; i < ids.length; i++) {
            if (i % cols == 0) {
                rowLayout = new LinearLayout(context);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(rowLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
            }
            final String id = ids[i];
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            boolean current = id.equals(session.scene());
            card.setBackground(shape(current ? BLUE_SOFT : 0xFFF4F5F7, 14, current ? BLUE : 0));
            card.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(6));
            android.graphics.Bitmap bmp = preview.thumbnail(id, thumbW);
            if (bmp != null) {
                android.widget.ImageView iv = new android.widget.ImageView(context);
                iv.setImageBitmap(bmp);
                iv.setBackground(shape(0xFFFFFFFF, 8, LINE));
                iv.setAdjustViewBounds(true);
                iv.setClipToOutline(true);
                card.addView(iv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }
            TextView name = text(context, tr("scene." + id), 11, current);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            card.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
            card.setOnClickListener(v -> {
                session.setScene(id);
                inspecting = false;
                globalPanel = false;
                dlg[0].dismiss();
                applySheet(false);
                refreshAll();
            });
            rowLayout.addView(card, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 3, 0, 3, 0));
        }
    }

    private void layersDialog() {
        Context context = getParentActivity();
        final StudioModel.Node tree = StudioModel.layers(session, preview.visibleComponents());
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(14));
        final Dialog[] dlg = new Dialog[1];
        final Runnable[] fill = new Runnable[1];
        final boolean[] lockedOpen = {false};
        fill[0] = () -> {
            box.removeAllViews();
            box.addView(text(context, tr("scene." + session.scene()) + "  \u00B7  " + tr("layers"), 17, true),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 8));
            java.util.List<String> locked = new java.util.ArrayList<>();
            addLayer(context, box, tree, 0, dlg, fill, locked);
            if (!locked.isEmpty()) { // parts that cannot be edited stay out of the way, folded at the bottom
                TextView head = text(context, (lockedOpen[0] ? "\u25BE " : "\u25B8 ") + tr("locked") + " (" + locked.size() + ")", 13, false);
                head.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
                head.setOnClickListener(v -> {
                    lockedOpen[0] = !lockedOpen[0];
                    fill[0].run();
                });
                box.addView(head);
                if (lockedOpen[0]) {
                    for (String id : locked) {
                        TextView t = text(context, session.catalog.componentLabel(id, session.lang()), 14, false);
                        t.setTextColor(0xFFA4A9B3);
                        t.setPadding(AndroidUtilities.dp(26), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
                        box.addView(t);
                    }
                }
            }
        };
        fill[0].run();
        ScrollView sv = new ScrollView(context);
        sv.addView(box);
        dlg[0] = panel(sv);
        dlg[0].show();
    }

    /** Whether this layer or anything under it can be edited. */
    private static boolean hasEditable(StudioModel.Node n) {
        if (n.editable) {
            return true;
        }
        for (StudioModel.Node k : n.kids) {
            if (hasEditable(k)) {
                return true;
            }
        }
        return false;
    }

    private void addLayer(final Context context, LinearLayout box, final StudioModel.Node n, int depth, final Dialog[] dlg, final Runnable[] fill, java.util.List<String> locked) {
        if (!hasEditable(n)) {
            collectIds(n, locked);
            return;
        }
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        boolean current = n.id.equals(session.selected()) && inspecting;
        line.setBackground(current ? shape(BLUE_SOFT, 10, 0) : null);
        line.setPadding(AndroidUtilities.dp(8 + depth * 18), 0, AndroidUtilities.dp(8), 0);
        boolean hasKids = false;
        for (StudioModel.Node k : n.kids) {
            hasKids |= hasEditable(k);
        }
        TextView arrow = text(context, !hasKids ? "" : collapsedLayers.contains(n.id) ? "\u25B8" : "\u25BE", 16, false);
        arrow.setGravity(Gravity.CENTER);
        if (hasKids) {
            arrow.setOnClickListener(v -> {
                if (!collapsedLayers.remove(n.id)) {
                    collapsedLayers.add(n.id);
                }
                fill[0].run();
            });
        }
        line.addView(arrow, LayoutHelper.createLinear(28, 44));
        TextView name = text(context, session.catalog.componentLabel(n.id, session.lang()), 15, n.editable);
        name.setTextColor(n.editable ? INK : 0xFFA4A9B3);
        line.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        if (n.editable) {
            line.setOnClickListener(v -> {
                dlg[0].dismiss();
                onPicked(n.id);
            });
        }
        box.addView(line, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        if (!collapsedLayers.contains(n.id)) {
            for (StudioModel.Node k : n.kids) {
                addLayer(context, box, k, depth + 1, dlg, fill, locked);
            }
        }
    }

    private static void collectIds(StudioModel.Node n, java.util.List<String> out) {
        out.add(n.id);
        for (StudioModel.Node k : n.kids) {
            collectIds(k, out);
        }
    }

    private void searchDialog() {
        final Context context = getParentActivity();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        final android.widget.EditText input = new android.widget.EditText(context);
        input.setHint(tr("search.hint"));
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setBackground(shape(0xFFF1F2F4, 14, 0));
        input.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        box.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        final LinearLayout results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(context);
        sv.addView(results);
        box.addView(sv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 260));
        final Dialog[] dlg = new Dialog[1];
        final java.util.List<String> onScreen = preview.visibleComponents();
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            results.removeAllViews();
            String q = input.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            int shown = 0;
            for (String id : session.browsableComponents()) {
                String label = session.catalog.componentLabel(id, session.lang());
                if (!q.isEmpty() && !label.toLowerCase(java.util.Locale.ROOT).contains(q) && !id.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    continue;
                }
                TextView t = text(context, label + (onScreen.contains(id) ? "" : "   ·   " + tr("search.other")), 15, onScreen.contains(id));
                t.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12));
                final String pick = id;
                t.setOnClickListener(v -> {
                    dlg[0].dismiss();
                    onPicked(pick);
                    setSheetState(Math.max(sheetState, 1), true);
                });
                results.addView(t);
                shown++;
            }
            if (shown == 0) {
                results.addView(text(context, tr("search.none"), 14, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 12, 8, 0));
            }
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                fill[0].run();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        fill[0].run();
        dlg[0] = panel(box);
        dlg[0].show();
    }

    private void deviceDialog() {
        final int[] sizes = {320, 360, 412};
        String[] names = new String[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            names[i] = tr("device." + sizes[i]) + (sizes[i] == preview.deviceWidthDp() ? "  ✓" : "");
        }
        new AlertDialog.Builder(getParentActivity()).setTitle(tr("device")).setMessage(tr("device.note"))
                .setItems(names, (d, i) -> {
                    preview.setDeviceWidthDp(sizes[i]);
                    preview.invalidate();
                }).show();
    }

    /** Every readability problem, each with the part to fix, what is wrong, a suggestion, and a way straight to the setting. */
    private void warningsDialog() {
        final Context context = getParentActivity();
        final List<EditorSession.Readability> issues = session.readability();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        box.addView(text(context, tr("contrast.title"), 17, true), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        final Dialog[] dlg = new Dialog[1];
        if (issues.isEmpty()) {
            box.addView(text(context, tr("contrast.ok"), 14, false));
        }
        for (final EditorSession.Readability r : issues) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(shape(0xFFF7F8FA, 14, 0));
            card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
            String line = (r.night ? "🌙 " : "☀ ") + session.catalog.entryLabel(r.textPath, session.lang()) + " " + tr("warn.line") + " "
                    + session.catalog.entryLabel(r.backgroundPath, session.lang());
            card.addView(text(context, line, 14, true));
            card.addView(text(context, String.format(java.util.Locale.US, "%.1f : 1  (≥ %.1f)", r.ratio, r.minimum), 12, false));
            final int bg = backgroundOf(r);
            final int suggested = StudioModel.suggestText(bg);
            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView go = text(context, tr("warn.fix"), 13, true);
            go.setTextColor(BLUE);
            go.setPadding(0, AndroidUtilities.dp(10), AndroidUtilities.dp(18), 0);
            go.setOnClickListener(v -> {
                dlg[0].dismiss();
                goToSetting(r.night, r.textPath);
            });
            TextView use = text(context, tr("warn.suggest") + "  " + String.format("#%06X", suggested & 0xFFFFFF), 13, true);
            use.setTextColor(BLUE);
            use.setPadding(0, AndroidUtilities.dp(10), 0, 0);
            use.setOnClickListener(v -> {
                dlg[0].dismiss();
                boolean was = session.night();
                session.setNight(r.night);
                report(session.setColor(r.textPath, suggested, false));
                session.setNight(was);
                changed(true);
            });
            actions.addView(go);
            actions.addView(use);
            card.addView(actions);
            box.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        }
        ScrollView sv = new ScrollView(context);
        sv.addView(box);
        dlg[0] = panel(sv);
        dlg[0].show();
    }

    /** The colour behind a warned text colour, in the variant the warning is about. */
    private int backgroundOf(EditorSession.Readability r) {
        boolean was = session.night();
        session.setNight(r.night);
        try {
            int def = r.backgroundPath.endsWith("accent") ? 0xFF3390EC
                    : r.backgroundPath.endsWith("background") ? (r.night ? 0xFF0F0F10 : 0xFFF2F2F7) : (r.night ? 0xFF1C1C2E : 0xFFFFFFFF);
            return session.color(r.backgroundPath, def);
        } finally {
            session.setNight(was);
        }
    }

    /** Opens the inspector on the setting itself: right variant, right part, right tab, the row marked. */
    private void goToSetting(boolean night, String path) {
        session.setNight(night);
        Catalog.Entry e = session.catalog.entry(path);
        if (e != null && !e.isBasic()) {
            session.setAdvanced(true);
        }
        session.select(StudioModel.componentOfPath(session.catalog, path));
        RuleSet.Ref ref = session.rules.find(path);
        if (e != null && ref != null) {
            section = StudioModel.sectionOf(e, ref.rule);
        }
        focusPath = path;
        inspecting = true;
        globalPanel = false;
        setEditMode(true);
        setSheetState(2, true);
        applySheet(true);
        refreshAll();
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
        refreshPanel();
        preview.invalidate();
        scheduleLive();
        AndroidUtilities.cancelRunOnUIThread(autosave);
        AndroidUtilities.runOnUIThread(autosave, 800);
    }

    private void saveDraft() {
        try {
            if (session != null && editing == null && session.isDirty()) { // only a new theme has a draft; a published one is saved by publishing
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
                    inspecting = false;
                    globalPanel = false;
                    session = new EditorSession(session.rules, session.catalog, ThemeDraft.fromJson(TEMPLATE, session.rules));
                    preview.setSession(session);
                    applySheet(false);
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
                if (editing == null) {
                    prefs().edit().remove(KEY_DRAFT).remove(KEY_STYLES).apply(); // it is in the store now; the next new theme starts fresh
                }
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
