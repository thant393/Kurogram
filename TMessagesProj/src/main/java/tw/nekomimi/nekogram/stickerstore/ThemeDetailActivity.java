package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
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

/** A store theme: preview, details, and download + apply (a skin applies live, no restart). */
public class ThemeDetailActivity extends BaseFragment {

    private final ThemeInfo info;
    private TextView applyButton;
    private TextView resetButton;
    private TextView statusView;
    private boolean busy;

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
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;
        ScrollView scroll = new ScrollView(context);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        scroll.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ThemeCardView.rounded(Theme.getColor(Theme.key_windowBackgroundWhite), 22, false));
        card.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        column.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 12, 16, 12));

        int bg = info.background != 0 ? info.background : (info.dark ? 0xFF1C1C1E : 0xFFF2F2F7);
        int accent = info.accent != 0 ? info.accent : KuroSkinManager.get().accent();
        FrameLayout preview = new FrameLayout(context);
        preview.setBackground(ThemeCardView.rounded(bg, 18, true));
        View bar = new View(context);
        bar.setBackground(ThemeCardView.rounded(accent, 6, false));
        preview.addView(bar, LayoutHelper.createFrame(110, 12, Gravity.LEFT | Gravity.TOP, 18, 22, 0, 0));
        View dot = new View(context);
        dot.setBackground(ThemeCardView.oval(accent));
        preview.addView(dot, LayoutHelper.createFrame(34, 34, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 18, 18));
        card.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 150, 0, 0, 0, 16));

        TextView title = text(context, info.title, 22, true, Theme.key_windowBackgroundWhiteBlackText);
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
        TextView stats = text(context, "↓ " + CountFormat.format(info.downloads) + "   ♥ " + CountFormat.format(info.likes), 13, false, Theme.key_windowBackgroundWhiteGrayText);
        stats.setGravity(Gravity.CENTER);
        card.addView(stats, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        applyButton = new TextView(context);
        applyButton.setGravity(Gravity.CENTER);
        applyButton.setTextSize(16);
        applyButton.setTypeface(AndroidUtilities.bold());
        applyButton.setTextColor(0xFFFFFFFF);
        applyButton.setOnClickListener(v -> applyTheme());
        column.addView(applyButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 16, 4, 16, 0));

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

        refreshButtons();
        return root;
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
        return info.id != null && info.id.equals(ThemeStoreApi.appliedId());
    }

    private void refreshButtons() {
        int accent = KuroSkinManager.get().accent();
        boolean applied = isApplied();
        applyButton.setBackground(ThemeCardView.rounded(applied ? 0xFF8E8E93 : accent, 26, false));
        applyButton.setText(busy ? "Working…" : applied ? "Applied" : "Download & Apply");
        applyButton.setAlpha(busy ? 0.6f : 1f);
        resetButton.setBackground(ThemeCardView.rounded(Theme.getColor(Theme.key_windowBackgroundWhite), 24, true));
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
        busy = true;
        setStatus("Downloading…", false);
        refreshButtons();
        final long me = UserConfig.getInstance(currentAccount).getClientUserId();
        ThemeStoreApi.download(info.id, me, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (fragmentView == null) return;
                try {
                    if (response == null) throw new IllegalStateException("Empty reply");
                    String content = response.getString("content");
                    String type = response.getString("type");
                    verifyHash(content, response.optString("sha256", ""));
                    if ("skin".equals(type)) {
                        KuroSkinManager.get().apply(content);
                    } else {
                        applyColors(response.optString("slug", "theme"), content);
                    }
                    ThemeStoreApi.setApplied(info.id);
                    setStatus("Applied", false);
                } catch (IllegalArgumentException e) {
                    setStatus("This theme was rejected: " + e.getMessage(), true);
                } catch (Exception e) {
                    setStatus("Couldn't apply the theme: " + e.getMessage(), true);
                }
                busy = false;
                refreshButtons();
            }

            @Override
            public void onError(String message) {
                if (fragmentView == null) return;
                busy = false;
                setStatus(message, true);
                refreshButtons();
            }
        });
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
            KuroSkinManager.get().reset();
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
