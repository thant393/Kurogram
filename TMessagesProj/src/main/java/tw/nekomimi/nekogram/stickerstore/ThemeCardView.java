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

    private final PhonePreview phone;
    private final TextView titleView;
    private final TextView ownerView;
    private final TextView statsView;

    private boolean inset;

    /** Use the inset fill when this card sits on the profile's surface card. */
    public ThemeCardView setInset(boolean inset) {
        this.inset = inset;
        int radius = StickerPackGridCell.cellRadiusDp();
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(AndroidUtilities.dp(radius));
        mask.setColor(0xFFFFFFFF);
        setBackground(new RippleDrawable(ColorStateList.valueOf(Theme.getColor(Theme.key_listSelector)),
                StickerPackGridCell.cardBackground(inset), mask));
        return this;
    }

    public ThemeCardView(Context context) {
        super(context);
        int radius = StickerPackGridCell.cellRadiusDp();
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(AndroidUtilities.dp(radius));
        mask.setColor(0xFFFFFFFF);
        setBackground(new RippleDrawable(ColorStateList.valueOf(Theme.getColor(Theme.key_listSelector)),
                StickerPackGridCell.cardBackground(), mask));
        int pad = AndroidUtilities.dp(12);
        setPadding(pad, pad, pad, pad);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        phone = new PhonePreview(context);
        column.addView(phone, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        titleView = new TextView(context);
        titleView.setTextSize(15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ownerView = new TextView(context);
        ownerView.setTextSize(12);
        ownerView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        ownerView.setGravity(Gravity.CENTER);
        ownerView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        ownerView.setSingleLine(true);
        ownerView.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(ownerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 8));

        statsView = new TextView(context);
        statsView.setTextSize(12);
        statsView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statsView.setGravity(Gravity.CENTER);
        statsView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        statsView.setSingleLine(true);
        column.addView(statsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public void setTheme(ThemeInfo t) {
        int fallbackBg = t.dark ? 0xFF1C1C1E : 0xFFF2F2F7;
        int bg = t.background != 0 ? t.background : fallbackBg;
        int accent = t.accent != 0 ? t.accent : Theme.getColor(Theme.key_featuredStickers_addButton);
        phone.set(bg, accent, t.dark, tw.nekomimi.nekogram.skin.KuroUi.t().previewRadiusDp);
        titleView.setText(t.title);
        String owner = t.ownerName != null && !t.ownerName.isEmpty() ? t.ownerName
                : t.ownerUsername != null && !t.ownerUsername.isEmpty() ? "@" + t.ownerUsername : "";
        ownerView.setText(owner);
        ownerView.setVisibility(owner.isEmpty() ? GONE : VISIBLE);
        statsView.setText(t.category + " · ↓ " + CountFormat.format(t.downloads) + " · ♥ " + CountFormat.format(t.likes));
    }

    /** A small portrait phone screen (top bar, chat bubbles, input bar) drawn in the theme's colours. */
    static final class PhonePreview extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path clip = new android.graphics.Path();
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private int bg, accent, radiusDp = 14;
        private boolean dark;

        PhonePreview(Context context) {
            super(context);
        }

        void set(int bg, int accent, boolean dark, int radiusDp) {
            this.bg = bg;
            this.accent = accent;
            this.dark = dark;
            this.radiusDp = radiusDp;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            // Phone-screen proportions (about 9:16).
            setMeasuredDimension(width, (int) (width * 1.7f));
        }

        private int blend(int c, int over, float a) {
            return androidx.core.graphics.ColorUtils.blendARGB(c, over, a);
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float r = AndroidUtilities.dp(radiusDp);
            clip.reset();
            rect.set(0, 0, w, h);
            clip.addRoundRect(rect, r, r, android.graphics.Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(bg);
            canvas.drawRect(rect, paint);
            int text = dark ? 0xFFFFFFFF : 0xFF000000;
            float u = w / 100f;
            // top bar
            paint.setColor(accent);
            canvas.drawRect(0, 0, w, 14 * u, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(230);
            rect.set(8 * u, 5.5f * u, 40 * u, 8.5f * u);
            canvas.drawRoundRect(rect, 2 * u, 2 * u, paint);
            // incoming bubbles
            int incoming = blend(bg, text, 0.12f);
            paint.setColor(incoming);
            rect.set(8 * u, 22 * u, 62 * u, 34 * u);
            canvas.drawRoundRect(rect, 5 * u, 5 * u, paint);
            rect.set(8 * u, 38 * u, 46 * u, 48 * u);
            canvas.drawRoundRect(rect, 5 * u, 5 * u, paint);
            // outgoing bubbles
            paint.setColor(accent);
            rect.set(38 * u, 54 * u, 92 * u, 68 * u);
            canvas.drawRoundRect(rect, 5 * u, 5 * u, paint);
            rect.set(56 * u, 72 * u, 92 * u, 82 * u);
            canvas.drawRoundRect(rect, 5 * u, 5 * u, paint);
            // input bar
            paint.setColor(blend(bg, text, 0.08f));
            canvas.drawRect(0, h - 14 * u, w, h, paint);
            paint.setColor(blend(bg, text, 0.16f));
            rect.set(6 * u, h - 10.5f * u, w - 18 * u, h - 3.5f * u);
            canvas.drawRoundRect(rect, 3.5f * u, 3.5f * u, paint);
            paint.setColor(accent);
            canvas.drawCircle(w - 9 * u, h - 7 * u, 4.5f * u, paint);
            canvas.restore();
            // hairline border
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, AndroidUtilities.dp(0.5f)));
            paint.setColor(StickerPackGridCell.strokeColor());
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, paint);
        }
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
