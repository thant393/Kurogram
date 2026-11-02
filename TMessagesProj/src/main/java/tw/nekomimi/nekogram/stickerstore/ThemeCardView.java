package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** Grid card for a store theme: a colour preview, its name, owner and counters. */
public class ThemeCardView extends FrameLayout {

    private final PhoneScene phone;
    private ThemeInfo shown;
    private final TextView titleView;
    private final TextView ownerView;
    private final TextView statsView;

    private boolean inset;

    /** Use the inset fill when this card sits on the profile's surface card. */
    public ThemeCardView setInset(boolean inset) {
        this.inset = inset;
        int radius = StoreLook.cardRadiusDp();
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(AndroidUtilities.dp(radius));
        mask.setColor(0xFFFFFFFF);
        setBackground(new RippleDrawable(ColorStateList.valueOf(Theme.getColor(Theme.key_listSelector)),
                tw.nekomimi.nekogram.skin.KuroUi.card(inset, radius), mask));
        return this;
    }

    public ThemeCardView(Context context) {
        super(context);
        int radius = StoreLook.cardRadiusDp();
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(AndroidUtilities.dp(radius));
        mask.setColor(0xFFFFFFFF);
        setBackground(new RippleDrawable(ColorStateList.valueOf(Theme.getColor(Theme.key_listSelector)),
                tw.nekomimi.nekogram.skin.KuroUi.card(false, radius), mask));
        int pad = AndroidUtilities.dp(12);
        setPadding(pad, pad, pad, pad);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final boolean overlay = StoreLook.overlayMeta();
        phone = new PhoneScene(context, PhoneScene.CHATS);
        phone.setHeightRatio(StoreLook.previewAspect());
        if (!overlay) {
            column.addView(phone, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        }

        titleView = new TextView(context);
        titleView.setTextSize(StoreLook.titleSizeSp(15));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        ownerView = new TextView(context);
        ownerView.setTextSize(12);
        ownerView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        ownerView.setGravity(Gravity.CENTER);
        ownerView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        ownerView.setSingleLine(true);
        ownerView.setEllipsize(TextUtils.TruncateAt.END);

        statsView = new TextView(context);
        statsView.setTextSize(12);
        statsView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statsView.setGravity(Gravity.CENTER);
        statsView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        statsView.setSingleLine(true);

        if (!overlay) {
            // unchanged: preview, then title / owner / counters below it
            column.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            column.addView(ownerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 8));
            column.addView(statsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            // layout themeStore.meta.position = overlay: the same three lines on a scrim at the bottom of the preview
            FrameLayout frame = new FrameLayout(context);
            frame.addView(phone, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            LinearLayout meta = new LinearLayout(context);
            meta.setOrientation(LinearLayout.VERTICAL);
            int r = AndroidUtilities.dp(tw.nekomimi.nekogram.skin.KuroUi.t().previewRadiusDp);
            GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xB3000000});
            scrim.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
            meta.setBackground(scrim);
            meta.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(28), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            titleView.setTextColor(0xFFFFFFFF);
            ownerView.setTextColor(0xCCFFFFFF);
            statsView.setTextColor(0xCCFFFFFF);
            meta.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            meta.addView(ownerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));
            meta.addView(statsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            frame.addView(meta, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
            column.addView(frame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    public void setTheme(ThemeInfo t) {
        int fallbackBg = t.dark ? 0xFF1C1C1E : 0xFFF2F2F7;
        int bg = t.background != 0 ? t.background : fallbackBg;
        int accent = t.accent != 0 ? t.accent : Theme.getColor(Theme.key_featuredStickers_addButton);
        phone.set(bg, accent, t.dark, t.gradientTop, t.gradientMiddle, t.gradientBottom, tw.nekomimi.nekogram.skin.KuroUi.t().previewRadiusDp);
        shown = t;
        if (t.id != null && "skin".equals(t.type)) {
            final int fbg = bg, facc = accent;
            ThemeStoreApi.fetchVersionContent(t.id, t.version, content -> {
                if (content == null || shown != t) return;
                try {
                    PhoneScene.Style style = PhoneScene.Style.palette(fbg, facc, t.dark, t.gradientTop, t.gradientMiddle, t.gradientBottom);
                    style.applySkin(tw.nekomimi.nekogram.skin.SkinSpec.parse(content));
                    String embedded = tw.nekomimi.nekogram.layout.LayoutSpec.embeddedJson(content, t.slug);
                    style.applyLayout(embedded != null ? tw.nekomimi.nekogram.layout.LayoutSpec.parse(embedded)
                            : tw.nekomimi.nekogram.layout.LayoutSpec.classic());
                    phone.setStyle(style, tw.nekomimi.nekogram.skin.KuroUi.t().previewRadiusDp);
                } catch (Exception ignored) {
                }
            });
        }
        titleView.setText(t.title);
        String owner = t.ownerName != null && !t.ownerName.isEmpty() ? t.ownerName
                : t.ownerUsername != null && !t.ownerUsername.isEmpty() ? "@" + t.ownerUsername : "";
        ownerView.setText(owner);
        ownerView.setVisibility(owner.isEmpty() || !StoreLook.showCreator() ? GONE : VISIBLE);
        statsView.setText(StoreLook.showDownloads()
                ? t.category + " · ↓ " + CountFormat.format(t.downloads) + " · ♥ " + CountFormat.format(t.likes)
                : t.category + " · ♥ " + CountFormat.format(t.likes));
    }

    static GradientDrawable rounded(int color, int radiusDp, boolean border) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(radiusDp));
        d.setColor(color);
        if (border) {
            d.setStroke(Math.max(1, AndroidUtilities.dp(0.5f)), StickerPackGridCell.strokeColor());
        }
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }
}
