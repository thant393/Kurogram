package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
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

import tw.nekomimi.nekogram.skin.KuroSkinManager;
import tw.nekomimi.nekogram.skin.SkinSpec;

/** A store theme: preview, details, and download + apply (a skin applies live, no restart). */
public class ThemeDetailActivity extends BaseFragment {

    private final ThemeInfo info;
    private TextView applyButton;
    private TextView resetButton;
    private TextView previewButton;
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
    private int likes;

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
        tw.nekomimi.nekogram.skin.KuroUi.applyPageBackground(root);
        fragmentView = root;
        ScrollView scroll = new ScrollView(context);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.setClipToPadding(false);
        column.setPadding(0, 0, 0, AndroidUtilities.dp(KuroBarStyle.CONTENT_INSET_DP));
        scroll.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ThemeCardView.rounded(Theme.getColor(Theme.key_windowBackgroundWhite), 22, false));
        card.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        column.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 12, 16, 12));

        int bg = info.background != 0 ? info.background : (info.dark ? 0xFF1C1C1E : 0xFFF2F2F7);
        int accent = info.accent != 0 ? info.accent : KuroSkinManager.get().accent();
        ThemeCardView.PhonePreview preview = new ThemeCardView.PhonePreview(context);
        preview.set(bg, accent, info.dark, tw.nekomimi.nekogram.skin.KuroUi.t().previewRadiusDp);
        // A phone-sized preview, centred (not stretched across the whole card).
        card.addView(preview, LayoutHelper.createLinear(170, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        TextView title = text(context, info.title, (int) tw.nekomimi.nekogram.skin.KuroUi.title(22), true, Theme.key_windowBackgroundWhiteBlackText);
        title.setGravity(Gravity.CENTER);
        card.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView meta = text(context, info.category + " · " + ("skin".equals(info.type) ? "Layout skin" : "Colors"), 13, false, Theme.key_windowBackgroundWhiteGrayText);
        meta.setGravity(Gravity.CENTER);
        card.addView(meta, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        if (!TextUtils.isEmpty(info.description)) {
            TextView desc = text(context, info.description, 14, false, Theme.key_windowBackgroundWhiteBlackText);
            desc.setGravity(Gravity.CENTER);
            card.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        }

        // Owner row (tap: their profile; your own theme only shows a note).
        String owner = !TextUtils.isEmpty(info.ownerName) ? info.ownerName
                : !TextUtils.isEmpty(info.ownerUsername) ? "@" + info.ownerUsername : "ID " + info.ownerTelegramId;
        TextView ownerView = text(context, "By " + owner, 14, true, Theme.key_featuredStickers_addButton);
        ownerView.setText("By " + owner);
        ownerView.setTextColor(KuroSkinManager.get().accent());
        ownerView.setGravity(Gravity.CENTER);
        ownerView.setOnClickListener(v -> openOwner());
        card.addView(ownerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));
        likes = info.likes;
        TextView stats = text(context, "", 13, false, Theme.key_windowBackgroundWhiteGrayText);
        statsView = stats;
        updateStats();
        stats.setGravity(Gravity.CENTER);
        card.addView(stats, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        refreshReactions();

        previewButton = new TextView(context);
        previewButton.setGravity(Gravity.CENTER);
        previewButton.setTextSize(15);
        previewButton.setTypeface(AndroidUtilities.bold());
        previewButton.setOnClickListener(v -> togglePreview());
        previewButton.setVisibility("skin".equals(info.type) ? View.VISIBLE : View.GONE);
        column.addView(previewButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 10, 16, 0));

        resetButton = new TextView(context);
        resetButton.setGravity(Gravity.CENTER);
        resetButton.setTextSize(15);
        resetButton.setTypeface(AndroidUtilities.bold());
        resetButton.setText("Reset to default");
        resetButton.setOnClickListener(v -> resetTheme());
        column.addView(resetButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 10, 16, 0));

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

    private void updateStats() {
        statsView.setText((info.ratingCount > 0 ? String.format(java.util.Locale.US, "★ %.1f (%d)   ", info.ratingAvg, info.ratingCount) : "")
                + "↓ " + CountFormat.format(info.downloads) + "   ♥ " + CountFormat.format(likes));
    }

    private void refreshReactions() {
        if (starButton == null || likeView == null) return;
        int on = KuroSkinManager.get().accent();
        int off = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        boolean rated = ThemeStoreApi.myRating(info.id) > 0;
        starButton.setImageResource(rated ? R.drawable.kuro_star_filled : R.drawable.kuro_star_outline);
        starButton.setColorFilter(rated ? on : off);
        boolean liked = ThemeStoreApi.isLiked(info.id);
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
        normalBar.addView(applyButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, tw.nekomimi.nekogram.skin.KuroUi.buttonHeightDp(52), 12, 0, 12, 0));

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
        d.setShape(GradientDrawable.OVAL);
        d.setColor(tw.nekomimi.nekogram.skin.KuroUi.barFill());
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
            pendingStars = ThemeStoreApi.myRating(info.id);
            refreshPickStars();
        }
        final View in = show ? ratingBar : normalBar;
        final View out = show ? normalBar : ratingBar;
        in.setVisibility(View.VISIBLE);
        in.animate().cancel();
        in.setScaleX(0.96f);
        in.animate().alpha(1f).scaleX(1f).setDuration(tw.nekomimi.nekogram.skin.KuroUi.anim(180)).start();
        out.animate().cancel();
        out.animate().alpha(0f).setDuration(tw.nekomimi.nekogram.skin.KuroUi.anim(120)).withEndAction(() -> {
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
        ThemeStoreApi.rate(info.id, me, stars, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (fragmentView == null) return;
                ThemeStoreApi.setMyRating(info.id, stars);
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
        final boolean want = !ThemeStoreApi.isLiked(info.id);
        final long me = UserConfig.getInstance(currentAccount).getClientUserId();
        // Optimistic: flip now, undo if the server refuses.
        ThemeStoreApi.setLikedLocal(info.id, want);
        likes = Math.max(0, likes + (want ? 1 : -1));
        updateStats();
        refreshReactions();
        if (want) {
            likeView.animate().cancel();
            likeView.setScaleX(1f);
            likeView.setScaleY(1f);
            likeView.animate().scaleX(1.3f).scaleY(1.3f).setDuration(tw.nekomimi.nekogram.skin.KuroUi.anim(120))
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                    .withEndAction(() -> likeView.animate().scaleX(1f).scaleY(1f).setDuration(tw.nekomimi.nekogram.skin.KuroUi.anim(110)).start()).start();
        }
        ThemeStoreApi.setLike(info.id, me, want, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (fragmentView == null || response == null || !response.has("total_likes")) return;
                likes = response.optInt("total_likes", likes);
                updateStats();
            }

            @Override
            public void onError(String message) {
                if (fragmentView == null) return;
                ThemeStoreApi.setLikedLocal(info.id, !want);
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
                ThemeStoreApi.report(info.id, UserConfig.getInstance(currentAccount).getClientUserId(), reason, new StickerStoreApi.ResultCallback() {
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
        return info.id != null && info.id.equals(ThemeStoreApi.appliedId()) && ThemeStoreApi.appliedVersion() >= info.version;
    }

    private void refreshButtons() {
        int accent = tw.nekomimi.nekogram.skin.KuroUi.accent();
        boolean applied = isApplied();
        int buttonRadius = tw.nekomimi.nekogram.skin.KuroUi.t().buttonRadiusDp;
        applyButton.setBackground(tw.nekomimi.nekogram.skin.KuroUi.primaryButton(applied));
        applyButton.setTextColor(tw.nekomimi.nekogram.skin.KuroUi.primaryButtonText(applied));
        boolean needsNewerApp = info.minAppVersion > SkinSpec.ENGINE_LEVEL || info.schemaVersion > SkinSpec.SCHEMA_VERSION;
        boolean updatable = !applied && info.id != null && info.id.equals(ThemeStoreApi.appliedId());
        applyButton.setText(busy ? "Working…" : needsNewerApp ? "Needs a newer Kurogram" : applied ? "Applied" : updatable ? "Update" : "Download & Apply");
        applyButton.setAlpha(busy ? 0.6f : 1f);
        resetButton.setBackground(ThemeCardView.rounded(Theme.getColor(Theme.key_windowBackgroundWhite), buttonRadius, true));
        previewButton.setBackground(ThemeCardView.rounded(Theme.getColor(Theme.key_windowBackgroundWhite), buttonRadius, true));
        previewButton.setTextColor(accent);
        previewButton.setText(KuroSkinManager.get().isPreviewing() ? "Stop preview" : "Preview");
        previewButton.setAlpha(busy ? 0.6f : 1f);
        resetButton.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        resetButton.setVisibility(applied ? View.VISIBLE : View.GONE);
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
        if (busy || isApplied()) {
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
        ThemeStoreApi.download(info.id, me, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (fragmentView == null) return;
                handle(response, previewOnly, false);
            }

            @Override
            public void onError(String message) {
                if (fragmentView == null) return;
                // Offline? Use the copy saved the last time this version was downloaded.
                JSONObject cached = ThemeStoreApi.cachedDownload(info.id, info.version);
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
                ThemeStoreApi.cacheContent(info.id, response.optInt("version", info.version), type, response.optString("sha256", ""), content);
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
                } else {
                    applyColors(response.optString("slug", "theme"), content);
                }
                ThemeStoreApi.setApplied(info.id, response.optInt("version", info.version));
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

    private void applyColors(String slug, String content) throws Exception {
        File file = new File(ApplicationLoader.applicationContext.getCacheDir(), "kuro_" + slug + ".attheme");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes("UTF-8"));
        }
        if (Theme.applyThemeFile(file, "kuro_" + slug, null, false) == null) {
            throw new IllegalStateException("Telegram couldn't read the colors");
        }
    }

    private void resetTheme() {
        if ("skin".equals(info.type)) {
            try {
                KuroSkinManager.get().reset();
            } catch (Exception e) {
                setStatus(e.getMessage(), true);
                refreshButtons();
                return;
            }
        } else {
            org.telegram.ui.ActionBar.Theme.ThemeInfo fallback = Theme.getTheme("Blue");
            if (fallback != null) {
                Theme.applyTheme(fallback);
            } else {
                setStatus("Pick a theme in Settings → Chat Settings to switch back.", true);
                return;
            }
        }
        ThemeStoreApi.clearApplied();
        setStatus("Back to the default", false);
        refreshButtons();
    }
}
