package tw.kurocore.kurogram.stickerstore;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

import tw.kurocore.kurogram.feature.KuroFeatures;

/**
 * Opens the Theme Builder on one of the user's own published themes. The store serves the published file (and its styles),
 * so this screen only loads them and then hands over to the Builder, replacing itself. There is no upload form any more:
 * the Builder is the only way to make a theme, and it publishes by itself ({@link ThemePublisher}).
 */
public class ThemeUploadActivity extends BaseFragment {

    private final ThemeInfo editing;
    private TextView statusView, retry;

    public ThemeUploadActivity(ThemeInfo editing) {
        this.editing = editing;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFFF7F4FF);
        fragmentView = root;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        root.addView(box, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));
        TextView icon = new TextView(context);
        icon.setText("\uD83C\uDFA8");
        icon.setTextSize(44);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        statusView = new TextView(context);
        statusView.setTextSize(16);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextColor(0xFF4A4468);
        box.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        retry = new TextView(context);
        retry.setText("Try again");
        retry.setTextSize(15);
        retry.setTypeface(AndroidUtilities.bold());
        retry.setGravity(Gravity.CENTER);
        retry.setTextColor(0xFF5B4FCF);
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> openBuilder());
        box.addView(retry, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 0));
        openBuilder();
        return fragmentView;
    }

    private void setStatus(String text, boolean error) {
        if (statusView == null) {
            return;
        }
        statusView.setText(text == null ? "" : text);
        retry.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    /** Fetches the published file first so nothing in it is lost, then opens the Builder in place of this screen. */
    private void openBuilder() {
        setStatus("Opening your theme\u2026", false);
        KuroFeatures.themes().fetchVersionContent(editing.id, editing.version, null, null, text -> {
            if (fragmentView == null) {
                return;
            }
            if (text == null) {
                setStatus("Could not load the theme. Check the connection and try again.", true);
                return;
            }
            if (editing.styles.isEmpty()) {
                presentFragment(new tw.kurocore.kurogram.skin.editor.ui.ThemeBuilderActivity(text, editing), true);
            } else {
                recoverStyles(text);
            }
        });
    }

    /**
     * The store serves merged skins, never the patches, so the styles of a published theme are rebuilt by comparing each merged
     * skin with the one it was built from: style = difference to the base, colourway = difference to its style.
     */
    private void recoverStyles(final String baseText) {
        final java.util.List<String[]> wanted = new java.util.ArrayList<>(); // {styleId, colourwayId or null}
        for (ThemeInfo.Style st : editing.styles) {
            wanted.add(new String[]{st.id, null});
            for (ThemeInfo.Way w : st.colorways) {
                wanted.add(new String[]{st.id, w.id});
            }
        }
        final java.util.Map<String, String> got = new java.util.HashMap<>();
        final Runnable[] next = new Runnable[1];
        next[0] = () -> {
            if (fragmentView == null) {
                return;
            }
            int at = got.size();
            if (at < wanted.size()) {
                String[] w = wanted.get(at);
                setStatus("Loading styles… " + (at + 1) + "/" + wanted.size(), false);
                KuroFeatures.themes().fetchVersionContent(editing.id, editing.version, w[0], w[1], text -> {
                    if (text == null) {
                        setStatus("Could not load the theme's styles. Check the connection and try again.", true);
                        return;
                    }
                    got.put(w[0] + "/" + (w[1] == null ? "" : w[1]), text);
                    next[0].run();
                });
                return;
            }
            try {
                java.util.Map<String, Object> base = tw.kurocore.kurogram.skin.editor.Json.parseObject(baseText);
                java.util.List<Object> styles = new java.util.ArrayList<>();
                for (ThemeInfo.Style st : editing.styles) {
                    java.util.Map<String, Object> merged = tw.kurocore.kurogram.skin.editor.Json.parseObject(got.get(st.id + "/"));
                    java.util.Map<String, Object> sm = new java.util.LinkedHashMap<>();
                    sm.put("id", st.id);
                    sm.put("name", st.name);
                    java.util.Map<String, Object> content = tw.kurocore.kurogram.skin.editor.StyleDiff.patch(base, merged);
                    if (!content.isEmpty()) {
                        sm.put("content", content);
                    }
                    java.util.List<Object> ways = new java.util.ArrayList<>();
                    for (ThemeInfo.Way w : st.colorways) {
                        java.util.Map<String, Object> full = tw.kurocore.kurogram.skin.editor.Json.parseObject(got.get(st.id + "/" + w.id));
                        java.util.Map<String, Object> wm = new java.util.LinkedHashMap<>();
                        wm.put("id", w.id);
                        wm.put("name", w.name);
                        if (w.swatch != 0) wm.put("swatch", String.format("#%06X", w.swatch & 0xFFFFFF));
                        if (w.swatch2 != 0) wm.put("swatch2", String.format("#%06X", w.swatch2 & 0xFFFFFF));
                        if (w.swatch3 != 0) wm.put("swatch3", String.format("#%06X", w.swatch3 & 0xFFFFFF));
                        java.util.Map<String, Object> patch = tw.kurocore.kurogram.skin.editor.StyleDiff.patch(merged, full);
                        if (!patch.isEmpty()) {
                            wm.put("patch", patch);
                        }
                        ways.add(wm);
                    }
                    if (!ways.isEmpty()) {
                        sm.put("colorways", ways);
                    }
                    styles.add(sm);
                }
                presentFragment(new tw.kurocore.kurogram.skin.editor.ui.ThemeBuilderActivity(baseText, editing,
                        tw.kurocore.kurogram.skin.editor.Json.write(styles)), true);
            } catch (RuntimeException e) {
                setStatus("Could not read the theme's styles.", true);
            }
        };
        next[0].run();
    }
}
