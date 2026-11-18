package tw.kurocore.kurogram.stickerstore;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

import tw.kurocore.kurogram.skin.KuroSkinManager;
import tw.kurocore.kurogram.skin.editor.Presets;
import tw.kurocore.kurogram.skin.editor.ui.ThemeBuilderActivity;

/** Where a new theme starts: a preset palette, a copy of the theme in use, or a blank page (and the saved draft, when there is one). */
public final class QuickStartSheet {
    private static final int INK = 0xFF1F2328, SOFT = 0xFF6B7280, LINE = 0xFFE5E7EB, BLUE = 0xFF2F80ED;

    private QuickStartSheet() {
    }

    public static void show(final BaseFragment fragment) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final FrameLayout wrap = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        float r = AndroidUtilities.dp(22);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        wrap.setBackground(bg);
        dialog.setContentView(wrap);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.35f);
        }
        showChoices(fragment, context, dialog, wrap);
        dialog.show();
    }

    private static void showChoices(final BaseFragment fragment, final Context context, final Dialog dialog, final FrameLayout wrap) {
        wrap.removeAllViews();
        LinearLayout box = column(context);
        box.addView(title(context, "Start a new theme"));
        if (ThemeBuilderActivity.hasDraft()) {
            box.addView(option(context, "✎", "Continue your draft", "Pick up where you left off", v -> {
                dialog.dismiss();
                fragment.presentFragment(new ThemeBuilderActivity(null, null));
            }));
        }
        box.addView(option(context, "🎨", "Start from a preset palette", "Choose a colour set, then fine-tune it", v -> showPresets(fragment, context, dialog, wrap)));
        final String applied = KuroSkinManager.get().activeJson();
        final String copy = applied == null ? null : Presets.cloneOf(applied);
        box.addView(option(context, "⧉", "Clone current theme", copy != null ? "Start from the theme you are using now" : "You are using the built-in look; nothing to clone", v -> {
            if (copy == null) {
                Toast.makeText(context, "Nothing to clone yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            fragment.presentFragment(new ThemeBuilderActivity(copy, null));
        }));
        box.addView(option(context, "□", "Start from scratch", "A blank theme", v -> {
            dialog.dismiss();
            fragment.presentFragment(new ThemeBuilderActivity(ThemeBuilderActivity.blankTheme(), null));
        }));
        wrap.addView(box);
    }

    private static void showPresets(final BaseFragment fragment, final Context context, final Dialog dialog, final FrameLayout wrap) {
        wrap.removeAllViews();
        LinearLayout box = column(context);
        TextView back = title(context, "‹  Preset palettes");
        back.setOnClickListener(v -> showChoices(fragment, context, dialog, wrap));
        box.addView(back);
        List<Presets.Preset> all = Presets.all();
        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            }
            final Presets.Preset p = all.get(i);
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(10));
            card.setBackground(shape(Color.parseColor(p.background), 16, LINE));
            LinearLayout dots = new LinearLayout(context);
            dots.setOrientation(LinearLayout.HORIZONTAL);
            for (String c : new String[]{p.accent, p.surface, p.textPrimary}) {
                View dot = new View(context);
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(Color.parseColor(c));
                d.setStroke(AndroidUtilities.dp(1), 0x33000000);
                dot.setBackground(d);
                dots.addView(dot, LayoutHelper.createLinear(22, 22, 3, 0, 3, 0));
            }
            card.addView(dots);
            TextView name = new TextView(context);
            name.setText(p.name);
            name.setTextSize(13);
            name.setTextColor(Color.parseColor(p.textPrimary));
            name.setTypeface(AndroidUtilities.bold());
            name.setGravity(Gravity.CENTER);
            card.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
            card.setOnClickListener(v -> {
                dialog.dismiss();
                fragment.presentFragment(new ThemeBuilderActivity(Presets.toJson(p), null));
            });
            row.addView(card, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 4, 0));
        }
        wrap.addView(box);
    }

    private static LinearLayout column(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(20));
        return box;
    }

    private static TextView title(Context context, String s) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(18);
        t.setTextColor(INK);
        t.setTypeface(AndroidUtilities.bold());
        t.setPadding(AndroidUtilities.dp(4), 0, 0, AndroidUtilities.dp(12));
        return t;
    }

    private static View option(Context context, String glyph, String name, String sub, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        row.setBackground(shape(0xFFF6F7F9, 16, 0));
        TextView icon = new TextView(context);
        icon.setText(glyph);
        icon.setTextSize(20);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(BLUE);
        icon.setBackground(shape(0xFFEAF2FF, 14, 0));
        row.addView(icon, LayoutHelper.createLinear(44, 44, 0, 0, 14, 0));
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView n = new TextView(context);
        n.setText(name);
        n.setTextSize(16);
        n.setTextColor(INK);
        n.setTypeface(AndroidUtilities.bold());
        TextView s = new TextView(context);
        s.setText(sub);
        s.setTextSize(12.5f);
        s.setTextColor(SOFT);
        words.addView(n);
        words.addView(s);
        row.addView(words, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        row.setOnClickListener(l);
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10);
        row.setLayoutParams(lp);
        return row;
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
}
