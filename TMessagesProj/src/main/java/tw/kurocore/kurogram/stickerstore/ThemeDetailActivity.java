package tw.kurocore.kurogram.stickerstore;

import tw.kurocore.kurogram.feature.KuroFeatures;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

import tw.kurocore.kurogram.skin.KuroSkinManager;
import tw.kurocore.kurogram.skin.SkinSpec;

/** A store theme: preview, details, and download + apply (a skin applies live, no restart). */
public class ThemeDetailActivity extends BaseFragment {

    private final ThemeInfo info;
    private TextView applyButton;
    private TextView statusView;
    private boolean busy;
    private android.widget.ImageView starButton;
    private android.widget.ImageView likeView;
    private final android.widget.ImageView[] pickStars = new android.widget.ImageView[5];
    private android.widget.ImageView cancelRatingButton;
    private android.widget.ImageView confirmRatingButton;
    private LinearLayout normalBar;
    private LinearLayout ratingBar;
    private boolean ratingMode;
    private int pendingStars;
    private TextView statsView;
    private final java.util.ArrayList<PhoneScene> scenes = new java.util.ArrayList<>();
    private PhoneScene logoScene;
    private int likes;
    // chosen look of a theme that has styles / colourways (null = the theme's default look)
    private String selStyle, selWay;
    private LinearLayout variantBox;
    private int previewBg, previewAccent;

    public ThemeDetailActivity(ThemeInfo info) {
        this.info = info;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Theme");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 2) {
                    reportTheme();
                }
            }
        });
        // Three dots, same as the sticker pack screen (no Report on your own theme).
        if (info.ownerTelegramId != UserConfig.getInstance(currentAccount).getClientUserId()) {
            org.telegram.ui.ActionBar.ActionBarMenuItem more = actionBar.createMenu().addItem(1, R.drawable.ic_ab_other);
            more.addSubItem(2, R.drawable.msg_report, "Report");
        }

        FrameLayout root = new FrameLayout(context);
        tw.kurocore.kurogram.skin.KuroUi.applyPageBackground(root);
        fragmentView = root;
        ScrollView scroll = new ScrollView(context);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.setClipToPadding(false);
        column.setPadding(0, 0, 0, AndroidUtilities.dp(KuroBarStyle.CONTENT_INSET_DP));
        scroll.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        int bg = info.background != 0 ? info.background : (info.dark ? 0xFF1C1C1E : 0xFFF2F2F7);
        int accent = info.accent != 0 ? info.accent : KuroSkinManager.get().accent();
        int textColor = tw.kurocore.kurogram.skin.KuroUi.textPrimary();
        int subColor = tw.kurocore.kurogram.skin.KuroUi.textSecondary();

        // Header: square logo, then name, creator and the numbers.
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 16, 20, 0));
        PhoneScene logo = new PhoneScene(context, PhoneScene.CHAT) {
            @Override
            protected void onMeasure(int w, int h) {
                setMeasuredDimension(AndroidUtilities.dp(64), AndroidUtilities.dp(64));
            }
        };
        logo.set(bg, accent, info.dark, info.gradientTop, info.gradientMiddle, info.gradientBottom, 16);
        logo.setIconMode(true);
        logoScene = logo;
        scenes.add(logo);
        header.addView(logo, LayoutHelper.createLinear(64, 64, 0, 0, 14, 0));
        LinearLayout names = new LinearLayout(context);
        names.setOrientation(LinearLayout.VERTICAL);
        header.addView(names, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView title = text(context, info.title, (int) tw.kurocore.kurogram.skin.KuroUi.title(24), true, Theme.key_windowBackgroundWhiteBlackText);
        title.setTextColor(textColor);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        String owner = !TextUtils.isEmpty(info.ownerName) ? info.ownerName
                : !TextUtils.isEmpty(info.ownerUsername) ? "@" + info.ownerUsername : "ID " + info.ownerTelegramId;
        TextView ownerView = text(context, owner, 15, false, Theme.key_windowBackgroundWhiteGrayText);
        ownerView.setTextColor(subColor);
        ownerView.setOnClickListener(v -> openOwner());
        names.addView(ownerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        likes = info.likes;
        TextView stats = text(context, "", 14, false, Theme.key_windowBackgroundWhiteGrayText);
        stats.setTextColor(subColor);
        statsView = stats;
        updateStats();
        names.addView(stats, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        // Previews: the Chats list, a chat and Settings, drawn in code from the theme's own palette.
        android.widget.HorizontalScrollView strip = new android.widget.HorizontalScrollView(context);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipToPadding(false);
        strip.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(row, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        String[] names3 = {"Chats", "Chat", "Settings"};
        int[] modes = {PhoneScene.CHATS, PhoneScene.CHAT, PhoneScene.SETTINGS};
        for (int i = 0; i < 3; i++) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            PhoneScene scene = new PhoneScene(context, modes[i]);
            scene.set(bg, accent, info.dark, info.gradientTop, info.gradientMiddle, info.gradientBottom,
                    tw.kurocore.kurogram.skin.KuroUi.t().previewRadiusDp);
            scenes.add(scene);
            cell.addView(scene, LayoutHelper.createLinear(150, LayoutHelper.WRAP_CONTENT));
            TextView label = text(context, names3[i], 12, false, Theme.key_windowBackgroundWhiteGrayText);
            label.setTextColor(subColor);
            cell.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
            row.addView(cell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, i < 2 ? 10 : 0, 0));
        }
        column.addView(strip, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));

        previewBg = bg;
        previewAccent = accent;
        loadRealPreview(bg, accent);
        addVariantPicker(context, column, subColor, textColor);

        // Categories and tags
        TextView ctTitle = text(context, "Categories and tags", 18, true, Theme.key_windowBackgroundWhiteBlackText);
        ctTitle.setTextColor(textColor);
        column.addView(ctTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 24, 20, 0));
        addChipGroup(context, column, "Categories", java.util.Collections.singletonList(
                info.category + ""), subColor, textColor);
        if (!info.tags.isEmpty()) {
            addChipGroup(context, column, "Tags", info.tags, subColor, textColor);
        }

        if (!TextUtils.isEmpty(info.description)) {
            TextView dTitle = text(context, "Description", 18, true, Theme.key_windowBackgroundWhiteBlackText);
            dTitle.setTextColor(textColor);
            column.addView(dTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 24, 20, 0));
            TextView desc = text(context, info.description, 15, false, Theme.key_windowBackgroundWhiteBlackText);
            desc.setTextColor(subColor);
            column.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 8, 20, 0));
        }

        refreshReactions();

        statusView = text(context, "", 13, false, Theme.key_windowBackgroundWhiteGrayText);
        statusView.setGravity(Gravity.CENTER);
        statusView.setVisibility(View.GONE);
        column.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 12, 24, 0));

        root.addView(KuroBarStyle.createBottomScrim(context),
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, KuroBarStyle.SCRIM_HEIGHT_DP, Gravity.BOTTOM));
        root.addView(buildBottomBar(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, KuroBarStyle.BAR_HEIGHT_DP, Gravity.BOTTOM,
                KuroBarStyle.SIDE_MARGIN_DP, 0, KuroBarStyle.SIDE_MARGIN_DP, KuroBarStyle.BOTTOM_MARGIN_DP));
        refreshReactions();
        refreshButtons();
        return root;
    }

    /** Redraws the previews from the theme's real file (skin colours / layout structure), once it is read. */
    private void loadRealPreview(final int bg, final int accent) {
        if (info.id == null || !"skin".equals(info.type)) {
            return;
        }
        KuroFeatures.themes().fetchVersionContent(info.id, info.version, selStyle, selWay, content -> {
            if (content == null || fragmentView == null) {
                return;
            }
            try {
                PhoneScene.Style style = PhoneScene.Style.palette(bg, accent, info.dark, info.gradientTop, info.gradientMiddle, info.gradientBottom);
                style.applySkin(SkinSpec.parse(content));
                String embedded = tw.kurocore.kurogram.layout.LayoutSpec.embeddedJson(content, info.slug);
                style.applyLayout(embedded != null ? tw.kurocore.kurogram.layout.LayoutSpec.parse(embedded)
                        : tw.kurocore.kurogram.layout.LayoutSpec.classic());
                int radius = tw.kurocore.kurogram.skin.KuroUi.t().previewRadiusDp;
                for (PhoneScene s : scenes) {
                    s.setStyle(style, s == logoScene ? 16 : radius);
                }
            } catch (Exception ignored) {
                // keep the palette-only preview
            }
        });
    }

    private void addChipGroup(Context context, LinearLayout column, String label, java.util.List<String> items, int subColor, int textColor) {
        TextView l = text(context, label, 14, false, Theme.key_windowBackgroundWhiteGrayText);
        l.setTextColor(subColor);
        column.addView(l, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 12, 20, 0));
        ChipFlow flow = new ChipFlow(context, AndroidUtilities.dp(8));
        for (String item : items) {
            TextView chip = text(context, item, 14, false, Theme.key_windowBackgroundWhiteBlackText);
            chip.setTextColor(textColor);
            chip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(AndroidUtilities.dp(20));
            d.setColor(0x00000000);
            d.setStroke(Math.max(1, AndroidUtilities.dp(1)), tw.kurocore.kurogram.skin.KuroUi.hairline());
            chip.setBackground(d);
            flow.addView(chip);
        }
        column.addView(flow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 8, 20, 0));
    }

    private void updateStats() {
        statsView.setText((info.ratingCount > 0 ? String.format(java.util.Locale.US, "★ %.1f (%d)   ", info.ratingAvg, info.ratingCount) : "")
                + "↓ " + CountFormat.format(info.downloads) + "   ♥ " + CountFormat.format(likes));
    }

    private void refreshReactions() {
        if (starButton == null || likeView == null) return;
        int on = KuroSkinManager.get().accent();
        int off = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        boolean rated = KuroFeatures.themes().myRating(info.id) > 0;
        starButton.setImageResource(rated ? R.drawable.kuro_star_filled : R.drawable.kuro_star_outline);
        starButton.setColorFilter(rated ? on : off);
        boolean liked = KuroFeatures.themes().isLiked(info.id);
        likeView.setImageResource(liked ? R.drawable.kuro_heart_filled : R.drawable.kuro_heart_outline);
        likeView.setColorFilter(liked ? Theme.getColor(Theme.key_text_RedBold) : off);
    }

    private View buildBottomBar(Context context) {
        FrameLayout bar = new FrameLayout(context);
        normalBar = new LinearLayout(context);
        normalBar.setOrientation(LinearLayout.HORIZONTAL);
        normalBar.setGravity(Gravity.CENTER);
        bar.addView(normalBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        starButton = barIcon(context, R.drawable.kuro_star_outline, 24);
        starButton.setBackground(circleBackground());
        starButton.setOnClickListener(v -> setRatingMode(true));
        normalBar.addView(starButton, LayoutHelper.createLinear(52, 52));

        applyButton = new TextView(context);
        applyButton.setGravity(Gravity.CENTER);
        applyButton.setTextSize(15);
        applyButton.setTypeface(AndroidUtilities.bold());
        applyButton.setTextColor(0xFFFFFFFF);
        applyButton.setMinWidth(AndroidUtilities.dp(170));
        applyButton.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        applyButton.setOnClickListener(v -> applyTheme());
        normalBar.addView(applyButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, tw.kurocore.kurogram.skin.KuroUi.buttonHeightDp(52), 12, 0, 12, 0));

        likeView = barIcon(context, R.drawable.kuro_heart_outline, 24);
        likeView.setBackground(circleBackground());
        likeView.setOnClickListener(v -> toggleLike());
        normalBar.addView(likeView, LayoutHelper.createLinear(52, 52));

        ratingBar = new LinearLayout(context);
        ratingBar.setOrientation(LinearLayout.HORIZONTAL);
        ratingBar.setGravity(Gravity.CENTER_VERTICAL);
        ratingBar.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        ratingBar.setBackground(KuroBarStyle.background());
        ratingBar.setVisibility(View.GONE);
        ratingBar.setAlpha(0f);
        bar.addView(ratingBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        cancelRatingButton = barIcon(context, R.drawable.kuro_close, 22);
        cancelRatingButton.setColorFilter(Theme.getColor(Theme.key_text_RedBold));
        cancelRatingButton.setOnClickListener(v -> setRatingMode(false));
        ratingBar.addView(cancelRatingButton, LayoutHelper.createLinear(48, 48));

        LinearLayout holder = new LinearLayout(context);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        holder.setGravity(Gravity.CENTER);
        ratingBar.addView(holder, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        for (int i = 0; i < 5; i++) {
            final int value = i + 1;
            android.widget.ImageView star = barIcon(context, R.drawable.kuro_star_outline, 28);
            star.setOnClickListener(v -> {
                pendingStars = value;
                refreshPickStars();
            });
            pickStars[i] = star;
            holder.addView(star, LayoutHelper.createLinear(44, 48));
        }

        confirmRatingButton = barIcon(context, R.drawable.kuro_check, 24);
        confirmRatingButton.setColorFilter(KuroSkinManager.get().accent());
        confirmRatingButton.setOnClickListener(v -> {
            if (pendingStars > 0) {
                rateTheme(pendingStars);
                setRatingMode(false);
            }
        });
        ratingBar.addView(confirmRatingButton, LayoutHelper.createLinear(48, 48));
        return bar;
    }

    private android.graphics.drawable.Drawable circleBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(tw.kurocore.kurogram.skin.KuroUi.floatingRadiusDp(26)));
        d.setColor(tw.kurocore.kurogram.skin.KuroUi.barFill());
        d.setStroke(Math.max(1, AndroidUtilities.dp(0.5f)), androidx.core.graphics.ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), 0x90));
        return d;
    }

    private android.widget.ImageView barIcon(Context context, int res, int sizeDp) {
        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageResource(res);
        int pad = Math.max(0, (48 - sizeDp) / 2);
        icon.setPadding(AndroidUtilities.dp(pad), AndroidUtilities.dp(pad), AndroidUtilities.dp(pad), AndroidUtilities.dp(pad));
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        icon.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        return icon;
    }

    private void setRatingMode(boolean show) {
        if (ratingMode == show || normalBar == null) return;
        ratingMode = show;
        if (show) {
            pendingStars = KuroFeatures.themes().myRating(info.id);
            refreshPickStars();
        }
        final View in = show ? ratingBar : normalBar;
        final View out = show ? normalBar : ratingBar;
        in.setVisibility(View.VISIBLE);
        in.animate().cancel();
        in.setScaleX(0.96f);
        in.animate().alpha(1f).scaleX(1f).setDuration(tw.kurocore.kurogram.skin.KuroUi.anim(180)).start();
        out.animate().cancel();
        out.animate().alpha(0f).setDuration(tw.kurocore.kurogram.skin.KuroUi.anim(120)).withEndAction(() -> {
            if ((out == normalBar) == ratingMode) out.setVisibility(View.GONE);
        }).start();
    }

    private void refreshPickStars() {
        int on = KuroSkinManager.get().accent();
        int off = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        for (int i = 0; i < 5; i++) {
            boolean f = i < pendingStars;
            pickStars[i].setImageResource(f ? R.drawable.kuro_star_filled : R.drawable.kuro_star_outline);
            pickStars[i].setColorFilter(f ? on : off);
        }
        confirmRatingButton.setAlpha(pendingStars > 0 ? 1f : 0.35f);
    }

    private void rateTheme(int stars) {
        final long me = UserConfig.getInstance(currentAccount).getClientUserId();
        KuroFeatures.themes().rate(info.id, me, stars, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (fragmentView == null) return;
                KuroFeatures.themes().setMyRating(info.id, stars);
                refreshReactions();
            }

            @Override
            public void onError(String message) {
                if (fragmentView == null) return;
                setStatus(message, true);
            }
        });
    }

    private void toggleLike() {
        final boolean want = !KuroFeatures.themes().isLiked(info.id);
        final long me = UserConfig.getInstance(currentAccount).getClientUserId();
        // Optimistic: flip now, undo if the server refuses.
        KuroFeatures.themes().setLikedLocal(info.id, want);
        likes = Math.max(0, likes + (want ? 1 : -1));
        updateStats();
        refreshReactions();
        if (want) {
            likeView.animate().cancel();
            likeView.setScaleX(1f);
            likeView.setScaleY(1f);
            likeView.animate().scaleX(1.3f).scaleY(1.3f).setDuration(tw.kurocore.kurogram.skin.KuroUi.anim(120))
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                    .withEndAction(() -> likeView.animate().scaleX(1f).scaleY(1f).setDuration(tw.kurocore.kurogram.skin.KuroUi.anim(110)).start()).start();
        }
        KuroFeatures.themes().setLike(info.id, me, want, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (fragmentView == null || response == null || !response.has("total_likes")) return;
                likes = response.optInt("total_likes", likes);
                updateStats();
            }

            @Override
            public void onError(String message) {
                if (fragmentView == null) return;
                KuroFeatures.themes().setLikedLocal(info.id, !want);
                likes = Math.max(0, likes + (want ? -1 : 1));
                updateStats();
                refreshReactions();
                setStatus(message, true);
            }
        });
    }

    private void reportTheme() {
        if (getParentActivity() == null) return;
        if (ReportDialog.isReported("theme", info.id)) {
            Toast.makeText(getParentActivity(), "You already reported this theme", Toast.LENGTH_SHORT).show();
            return;
        }
        ReportDialog.show(getParentActivity(), "Report this theme", reason ->
                KuroFeatures.themes().report(info.id, UserConfig.getInstance(currentAccount).getClientUserId(), reason, new StickerStoreApi.ResultCallback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        ReportDialog.markReported("theme", info.id);
                        if (getParentActivity() != null) Toast.makeText(getParentActivity(), "Thanks, we'll take a look", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        if (fragmentView != null) setStatus(message, true);
                    }
                }));
    }

    private TextView text(Context context, String s, int size, boolean bold, int colorKey) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(size);
        if (bold) t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(Theme.getColor(colorKey));
        return t;
    }

    private boolean isApplied() {
        return info.id != null && info.id.equals(KuroFeatures.themes().appliedId()) && KuroFeatures.themes().appliedVersion() >= info.version
                && variantKey().equals(appliedVariant());
    }

    private String variantKey() {
        return (selStyle == null ? "" : selStyle) + "|" + (selWay == null ? "" : selWay);
    }

    private android.content.SharedPreferences variantPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("kuro_theme_variant", Context.MODE_PRIVATE);
    }

    private String appliedVariant() {
        try {
            return variantPrefs().getString(info.id, "|");
        } catch (Exception e) {
            return "|";
        }
    }

    private void rememberVariant() {
        try {
            variantPrefs().edit().putString(info.id, variantKey()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Style chips and colour dots for a theme that offers several looks. */
    private void addVariantPicker(Context context, LinearLayout column, int subColor, int textColor) {
        if (info.styles.isEmpty()) {
            return;
        }
        variantBox = new LinearLayout(context);
        variantBox.setOrientation(LinearLayout.VERTICAL);
        column.addView(variantBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 20, 20, 0));
        rebuildVariantPicker(context, subColor, textColor);
    }

    private void chooseVariant(String style, String way) {
        selStyle = style;
        selWay = way;
        loadRealPreview(previewBg, previewAccent);
        if (variantBox != null) {
            rebuildVariantPicker(variantBox.getContext(), tw.kurocore.kurogram.skin.KuroUi.textSecondary(), tw.kurocore.kurogram.skin.KuroUi.textPrimary());
        }
        refreshButtons();
    }

    private void rebuildVariantPicker(Context context, int subColor, int textColor) {
        variantBox.removeAllViews();
        final int accent = tw.kurocore.kurogram.skin.KuroUi.accent();
        TextView styleLabel = text(context, "Style", 14, false, Theme.key_windowBackgroundWhiteGrayText);
        styleLabel.setTextColor(subColor);
        variantBox.addView(styleLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        ChipFlow flow = new ChipFlow(context, AndroidUtilities.dp(8));
        flow.addView(variantChip(context, "Default", selStyle == null, accent, textColor, () -> chooseVariant(null, null)));
        ThemeInfo.Style current = null;
        for (final ThemeInfo.Style st : info.styles) {
            boolean on = st.id.equals(selStyle);
            if (on) {
                current = st;
            }
            flow.addView(variantChip(context, st.name, on, accent, textColor, () -> chooseVariant(st.id, null)));
        }
        variantBox.addView(flow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        if (current != null && !current.colorways.isEmpty()) {
            TextView colourLabel = text(context, "Colour", 14, false, Theme.key_windowBackgroundWhiteGrayText);
            colourLabel.setTextColor(subColor);
            variantBox.addView(colourLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));
            ChipFlow dots = new ChipFlow(context, AndroidUtilities.dp(10));
            final String styleId = current.id;
            for (final ThemeInfo.Way w : current.colorways) {
                View dot = new View(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        setMeasuredDimension(AndroidUtilities.dp(40), AndroidUtilities.dp(40));
                    }
                };
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(w.swatch != 0 ? w.swatch : 0xFF9E9E9E);
                boolean on = w.id.equals(selWay);
                d.setStroke(AndroidUtilities.dp(on ? 3 : 1), on ? accent : tw.kurocore.kurogram.skin.KuroUi.hairline());
                dot.setBackground(d);
                dot.setContentDescription(w.name);
                dot.setOnClickListener(v -> chooseVariant(styleId, on ? null : w.id));
                dots.addView(dot);
            }
            variantBox.addView(dots, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }
    }

    private TextView variantChip(Context context, String label, boolean on, int accent, int textColor, Runnable click) {
        TextView chip = text(context, label, 14, on, Theme.key_windowBackgroundWhiteBlackText);
        chip.setTextColor(on ? 0xFFFFFFFF : textColor);
        chip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(20));
        d.setColor(on ? accent : 0x00000000);
        d.setStroke(Math.max(1, AndroidUtilities.dp(1)), on ? accent : tw.kurocore.kurogram.skin.KuroUi.hairline());
        chip.setBackground(d);
        chip.setOnClickListener(v -> click.run());
        return chip;
    }

    private void refreshButtons() {
        int accent = tw.kurocore.kurogram.skin.KuroUi.accent();
        boolean applied = isApplied();
        int buttonRadius = tw.kurocore.kurogram.skin.KuroUi.t().buttonRadiusDp;
        // Applied: the same button becomes "Reset to default" (red); no separate Preview / Reset buttons.
        GradientDrawable applyBg = tw.kurocore.kurogram.skin.KuroUi.primaryButton(false);
        if (applied) {
            applyBg.setColor(tw.kurocore.kurogram.skin.KuroUi.error());
        }
        int fill = applied ? tw.kurocore.kurogram.skin.KuroUi.error()
                : (tw.kurocore.kurogram.skin.KuroUi.t().buttonBackground != 0 ? tw.kurocore.kurogram.skin.KuroUi.t().buttonBackground : accent);
        int style = StoreLook.buttonStyle();
        if (style == 1 || style == 2) {
            // components.button.style outlined / text: this screen's Apply button only (filled or absent = unchanged)
            GradientDrawable plain = new GradientDrawable();
            plain.setShape(GradientDrawable.RECTANGLE);
            plain.setCornerRadius(AndroidUtilities.dp(buttonRadius));
            plain.setColor(0x00000000);
            if (style == 1) {
                plain.setStroke(AndroidUtilities.dp(1.5f), fill);
            }
            applyButton.setBackground(plain);
            applyButton.setTextColor(fill);
        } else {
            applyButton.setBackground(applyBg);
            applyButton.setTextColor(applied ? 0xFFFFFFFF : tw.kurocore.kurogram.skin.KuroUi.primaryButtonText(false));
        }
        boolean needsNewerApp = info.minAppVersion > SkinSpec.ENGINE_LEVEL || info.schemaVersion > SkinSpec.SCHEMA_VERSION;
        boolean updatable = !applied && info.id != null && info.id.equals(KuroFeatures.themes().appliedId());
        applyButton.setText(busy ? "Working…" : needsNewerApp ? "Needs a newer Kurogram" : applied ? "Reset to default" : updatable ? "Update" : "Download & Apply");
        applyButton.setAlpha(busy ? 0.6f : 1f);
    }

    private void setStatus(String s, boolean error) {
        statusView.setText(s);
        statusView.setTextColor(error ? Theme.getColor(Theme.key_text_RedBold) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusView.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
    }

    private void openOwner() {
        if (info.ownerTelegramId == 0 || getParentActivity() == null) {
            return;
        }
        if (info.ownerTelegramId == UserConfig.getInstance(currentAccount).getClientUserId()) {
            Toast.makeText(getParentActivity(), "This is your theme. You can't view your own profile as another user.", Toast.LENGTH_SHORT).show();
            return;
        }
        presentFragment(new OwnerProfileActivity(info.ownerTelegramId, info.ownerName, info.ownerUsername));
    }

    private void applyTheme() {
        if (busy) {
            return;
        }
        if (isApplied()) {
            resetTheme();
            return;
        }
        fetchAndRun(false);
    }

    /** Shows the theme temporarily (skins only); nothing is saved and Telegram's own settings are untouched. */
    private void togglePreview() {
        if (busy) {
            return;
        }
        if (KuroSkinManager.get().isPreviewing()) {
            KuroSkinManager.get().endPreview();
            setStatus("Preview ended", false);
            refreshButtons();
            return;
        }
        fetchAndRun(true);
    }

    private void fetchAndRun(final boolean previewOnly) {
        if (info.minAppVersion > SkinSpec.ENGINE_LEVEL || info.schemaVersion > SkinSpec.SCHEMA_VERSION) {
            setStatus("This theme needs a newer version of Kurogram", true);
            return;
        }
        busy = true;
        setStatus("Downloading…", false);
        refreshButtons();
        final long me = UserConfig.getInstance(currentAccount).getClientUserId();
        KuroFeatures.themes().download(info.id, me, selStyle, selWay, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (fragmentView == null) return;
                handle(response, previewOnly, false);
            }

            @Override
            public void onError(String message) {
                if (fragmentView == null) return;
                // Offline? Use the copy saved the last time this version was downloaded.
                JSONObject cached = KuroFeatures.themes().cachedDownload(info.id, info.version);
                if (cached != null) {
                    handle(cached, previewOnly, true);
                    return;
                }
                busy = false;
                setStatus(message, true);
                refreshButtons();
            }
        });
    }

    private void handle(JSONObject response, boolean previewOnly, boolean fromCache) {
        try {
            if (response == null) throw new IllegalStateException("Empty reply");
            String content = response.getString("content");
            String type = response.getString("type");
            verifyHash(content, response.optString("sha256", ""));
            if (!fromCache) {
                KuroFeatures.themes().cacheContent(info.id, response.optInt("version", info.version), type, response.optString("sha256", ""), content);
            }
            if (previewOnly) {
                if (!"skin".equals(type)) {
                    throw new IllegalStateException("Only skin themes can be previewed");
                }
                KuroSkinManager.get().preview(content);
                setStatus("Previewing. Nothing is saved until you tap Apply.", false);
            } else {
                if ("skin".equals(type)) {
                    KuroSkinManager.get().apply(content);
                    // A skin can carry its own layout section: colours and structure arrive as one theme.
                    String embedded = tw.kurocore.kurogram.layout.LayoutSpec.embeddedJson(content, info.slug);
                    if (embedded != null) {
                        tw.kurocore.kurogram.layout.LayoutManager.get().apply(embedded);
                    } else {
                        // A theme is a skin plus a layout; no layout section means Telegram's own layout.
                        tw.kurocore.kurogram.layout.LayoutManager.get().reset();
                    }
                } else {
                    throw new IllegalArgumentException("unsupported theme type");
                }
                KuroFeatures.themes().setApplied(info.id, response.optInt("version", info.version));
                rememberVariant();
                setStatus(fromCache ? "Applied (offline copy)" : "Applied", false);
            }
        } catch (IllegalArgumentException e) {
            setStatus("This theme was rejected: " + e.getMessage(), true);
        } catch (Exception e) {
            setStatus("Couldn't apply the theme: " + e.getMessage(), true);
        }
        busy = false;
        refreshButtons();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        // A preview is only for looking: leaving this screen puts the real look back.
        KuroSkinManager.get().endPreview();
    }

    /** The file must be exactly what the server hashed; anything else is not applied. */
    private static void verifyHash(String content, String expected) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        if (!sb.toString().equalsIgnoreCase(expected)) {
            throw new IllegalArgumentException("the file didn't match its checksum");
        }
    }

    private void resetTheme() {
        if ("skin".equals(info.type)) {
            try {
                KuroSkinManager.get().reset();
                tw.kurocore.kurogram.layout.LayoutManager.get().reset();
            } catch (Exception e) {
                setStatus(e.getMessage(), true);
                refreshButtons();
                return;
            }
        }
        KuroFeatures.themes().clearApplied();
        setStatus("Back to the default", false);
        refreshButtons();
    }
}
