package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;

/**
 * A miniature of one Telegram screen (chats list, a chat, settings), drawn in code from a theme's
 * palette the way the real screen is built: page (gradient or flat), glass or flat cards, accent
 * for selection and outgoing bubbles. There are no screenshots, so every theme gets its own preview.
 */
class PhoneScene extends View {
    static final int CHATS = 0, CHAT = 1, SETTINGS = 2;

    private final int mode;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF r = new RectF();
    private int bg, accent, top, mid, bottom, radiusDp = 14;
    private boolean dark;
    private boolean iconMode;

    PhoneScene(Context context, int mode) {
        super(context);
        this.mode = mode;
    }

    void setIconMode(boolean on) {
        iconMode = on;
        invalidate();
    }

    void set(int bg, int accent, boolean dark, int gradientTop, int gradientMiddle, int gradientBottom, int radiusDp) {
        this.bg = bg;
        this.accent = accent;
        this.dark = dark;
        this.top = gradientTop;
        this.mid = gradientMiddle;
        this.bottom = gradientBottom;
        this.radiusDp = radiusDp;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) (width * 1.9f));
    }

    private boolean hasGradient() {
        return top != 0 && bottom != 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float u = w / 100f;
        float radius = AndroidUtilities.dp(radiusDp);
        clip.reset();
        r.set(0, 0, w, h);
        clip.addRoundRect(r, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        // page: the skin's gradient, else its flat background (the same rule the real screens use)
        paint.setStyle(Paint.Style.FILL);
        if (hasGradient()) {
            int[] colors = mid != 0 ? new int[]{top, mid, bottom} : new int[]{top, bottom};
            paint.setShader(new LinearGradient(0, 0, w * 0.6f, h, colors, null, Shader.TileMode.CLAMP));
        } else {
            paint.setColor(bg);
        }
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        int text = dark ? 0xFFFFFFFF : 0xFF14213D;
        if (iconMode) {
            drawIcon(canvas, u, w, h, text);
        } else switch (mode) {
            case CHATS:
                drawChats(canvas, u, w, h, text);
                break;
            case CHAT:
                drawChat(canvas, u, w, h, text);
                break;
            default:
                drawSettings(canvas, u, w, h, text);
                break;
        }
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, AndroidUtilities.dp(0.5f)));
        paint.setColor(StickerPackGridCell.strokeColor());
        r.set(0, 0, w, h);
        canvas.drawRoundRect(r, radius, radius, paint);
    }

    // ---- shared pieces ----

    private int cardFill() {
        return hasGradient() ? ColorUtils.setAlphaComponent(dark ? 0xFFFFFFFF : 0xFFFFFFFF, dark ? 0x26 : 0x99)
                : ColorUtils.blendARGB(bg, dark ? 0xFFFFFFFF : 0xFFFFFFFF, dark ? 0.10f : 0.7f);
    }

    private void glass(Canvas c, float l, float t, float rr, float b, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(cardFill());
        r.set(l, t, rr, b);
        c.drawRoundRect(r, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, AndroidUtilities.dp(0.5f)));
        paint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, dark ? 0x40 : 0xCC));
        c.drawRoundRect(r, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void line(Canvas c, int color, float alpha, float l, float t, float rr, float b) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(color, (int) (alpha * 255)));
        r.set(l, t, rr, b);
        c.drawRoundRect(r, (b - t) / 2, (b - t) / 2, paint);
    }

    private void dot(Canvas c, int color, float cx, float cy, float rad) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(cx, cy, rad, paint);
    }

    private int shade(int i) {
        // a few related hues for avatars/icons so the screen is not one colour
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(accent, hsv);
        hsv[0] = (hsv[0] + i * 47) % 360;
        hsv[1] = Math.max(0.35f, Math.min(0.8f, hsv[1]));
        hsv[2] = Math.max(0.65f, hsv[2]);
        return android.graphics.Color.HSVToColor(hsv);
    }

    private void bottomNav(Canvas c, float u, float w, float h, int text) {
        glass(c, 5 * u, h - 14 * u, w - 5 * u, h - 4 * u, 5 * u);
        float cell = (w - 10 * u) / 4f;
        for (int i = 0; i < 4; i++) {
            float cx = 5 * u + cell * (i + 0.5f);
            if (i == (mode == SETTINGS ? 2 : 0)) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(accent, 0x30));
                r.set(cx - 9 * u, h - 13 * u, cx + 9 * u, h - 5 * u);
                c.drawRoundRect(r, 4 * u, 4 * u, paint);
                dot(c, accent, cx, h - 10.5f * u, 2.2f * u);
            } else {
                dot(c, ColorUtils.setAlphaComponent(text, 0x70), cx, h - 10.5f * u, 2.2f * u);
            }
        }
    }

    /** The theme's logo: its page with two glass bubbles, the second in the accent. */
    private void drawIcon(Canvas c, float u, float w, float h, int text) {
        glass(c, 14 * u, 22 * u, 70 * u, 50 * u, 12 * u);
        line(c, text, 0.6f, 24 * u, 32 * u, 58 * u, 38 * u);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        r.set(34 * u, 56 * u, 88 * u, 80 * u);
        c.drawRoundRect(r, 11 * u, 11 * u, paint);
        line(c, ColorUtils.calculateLuminance(accent) > 0.6 ? 0xFF14213D : 0xFFFFFFFF, 0.9f, 44 * u, 65 * u, 76 * u, 71 * u);
    }

    // ---- screens ----

    private void drawChats(Canvas c, float u, float w, float h, int text) {
        dot(c, shade(1), 10 * u, 9 * u, 4 * u);
        line(c, accent, 0.9f, 18 * u, 7.6f * u, 42 * u, 10.4f * u);
        dot(c, ColorUtils.setAlphaComponent(text, 0x80), w - 8 * u, 9 * u, 1.3f * u);
        glass(c, 5 * u, 16 * u, w - 5 * u, 23 * u, 3.5f * u);
        line(c, text, 0.25f, 12 * u, 18.6f * u, 36 * u, 20.4f * u);
        glass(c, 5 * u, 26 * u, w - 5 * u, 33 * u, 3.5f * u);
        r.set(7 * u, 27 * u, 28 * u, 32 * u);
        paint.setColor(ColorUtils.setAlphaComponent(accent, 0x38));
        c.drawRoundRect(r, 2.5f * u, 2.5f * u, paint);
        line(c, accent, 0.9f, 10 * u, 28.8f * u, 24 * u, 30.2f * u);
        line(c, text, 0.3f, 34 * u, 28.8f * u, 48 * u, 30.2f * u);
        line(c, text, 0.3f, 56 * u, 28.8f * u, 70 * u, 30.2f * u);
        float y = 38 * u;
        for (int i = 0; i < 6; i++) {
            dot(c, shade(i), 11 * u, y + 5 * u, 5 * u);
            line(c, text, 0.75f, 20 * u, y + 1.6f * u, 54 * u, y + 4.2f * u);
            line(c, text, 0.3f, 20 * u, y + 6.4f * u, 70 * u, y + 8.4f * u);
            line(c, text, 0.25f, w - 18 * u, y + 1.8f * u, w - 7 * u, y + 3.8f * u);
            if (i % 2 == 0) {
                dot(c, accent, w - 10 * u, y + 7.4f * u, 2.4f * u);
            }
            y += 12.2f * u;
        }
        bottomNav(c, u, w, h, text);
    }

    private void drawChat(Canvas c, float u, float w, float h, int text) {
        glass(c, 4 * u, 4 * u, w - 4 * u, 15 * u, 5.5f * u);
        dot(c, ColorUtils.setAlphaComponent(text, 0x70), 10 * u, 9.5f * u, 2 * u);
        dot(c, shade(2), 20 * u, 9.5f * u, 3.6f * u);
        line(c, text, 0.75f, 27 * u, 7.6f * u, 54 * u, 10.2f * u);
        line(c, text, 0.3f, 27 * u, 11.2f * u, 46 * u, 12.8f * u);

        float y = 22 * u;
        glass(c, 6 * u, y, 62 * u, y + 13 * u, 4.5f * u);
        line(c, accent, 0.9f, 10 * u, y + 2.6f * u, 26 * u, y + 4.4f * u);
        line(c, text, 0.7f, 10 * u, y + 6.4f * u, 54 * u, y + 8.2f * u);
        line(c, text, 0.7f, 10 * u, y + 9.4f * u, 40 * u, y + 11.2f * u);
        y += 17 * u;
        glass(c, 6 * u, y, 44 * u, y + 8.5f * u, 4.5f * u);
        line(c, text, 0.7f, 10 * u, y + 3.2f * u, 36 * u, y + 5f * u);
        y += 14 * u;
        // outgoing: accent-tinted glass
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(accent, hasGradient() ? 0xCC : 0xFF));
        r.set(36 * u, y, w - 6 * u, y + 11 * u);
        c.drawRoundRect(r, 4.5f * u, 4.5f * u, paint);
        int onAccent = ColorUtils.calculateLuminance(accent) > 0.6 ? 0xFF14213D : 0xFFFFFFFF;
        line(c, onAccent, 0.9f, 41 * u, y + 3.2f * u, w - 14 * u, y + 5f * u);
        line(c, onAccent, 0.9f, 41 * u, y + 6.4f * u, w - 26 * u, y + 8.2f * u);
        y += 15 * u;
        paint.setColor(ColorUtils.setAlphaComponent(accent, hasGradient() ? 0xCC : 0xFF));
        r.set(58 * u, y, w - 6 * u, y + 8 * u);
        c.drawRoundRect(r, 4.5f * u, 4.5f * u, paint);
        line(c, onAccent, 0.9f, 62 * u, y + 3f * u, w - 12 * u, y + 4.8f * u);
        y += 12 * u;
        glass(c, 6 * u, y, 52 * u, y + 8.5f * u, 4.5f * u);
        line(c, text, 0.7f, 10 * u, y + 3.2f * u, 44 * u, y + 5f * u);

        // input: pill + round send
        glass(c, 4 * u, h - 13 * u, w - 17 * u, h - 4 * u, 4.5f * u);
        line(c, text, 0.3f, 10 * u, h - 9.6f * u, 32 * u, h - 7.6f * u);
        dot(c, accent, w - 9 * u, h - 8.5f * u, 4.4f * u);
    }

    private void drawSettings(Canvas c, float u, float w, float h, int text) {
        dot(c, shade(3), w / 2, 17 * u, 9 * u);
        dot(c, accent, w / 2 + 6.5f * u, 22.5f * u, 2.6f * u);
        line(c, text, 0.8f, w / 2 - 14 * u, 30 * u, w / 2 + 14 * u, 32.6f * u);
        line(c, text, 0.3f, w / 2 - 10 * u, 34 * u, w / 2 + 10 * u, 35.8f * u);

        glass(c, 5 * u, 40 * u, w - 5 * u, 54 * u, 5 * u);
        line(c, accent, 0.9f, 10 * u, 42.6f * u, 24 * u, 44.2f * u);
        dot(c, shade(4), 11 * u, 49.6f * u, 2.6f * u);
        line(c, text, 0.7f, 17 * u, 48.6f * u, 46 * u, 50.6f * u);

        float top2 = 57 * u;
        glass(c, 5 * u, top2, w - 5 * u, top2 + 44 * u, 5 * u);
        float y = top2 + 4 * u;
        for (int i = 0; i < 4; i++) {
            r.set(9 * u, y, 17 * u, y + 8 * u);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(shade(i));
            c.drawRoundRect(r, 2.4f * u, 2.4f * u, paint);
            line(c, text, 0.75f, 21 * u, y + 1.4f * u, 56 * u, y + 3.6f * u);
            line(c, text, 0.28f, 21 * u, y + 4.8f * u, 44 * u, y + 6.6f * u);
            y += 10 * u;
        }
        bottomNav(c, u, w, h, text);
    }
}
