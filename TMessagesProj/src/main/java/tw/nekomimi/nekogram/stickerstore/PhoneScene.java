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

import java.util.Arrays;
import java.util.List;

import tw.nekomimi.nekogram.layout.LayoutSpec;
import tw.nekomimi.nekogram.skin.SkinSpec;

/**
 * A miniature of one Telegram screen (chats list, a chat, settings) drawn in code from the theme's
 * real data: a skin's colours, radii and gradient, and a layout pack's structure (tab bar style,
 * header buttons, story rail, folders, dividers, avatar size, input bar). It follows the same rules
 * the real screens use, so a preview changes exactly when the UI code's inputs change.
 */
class PhoneScene extends View {
    static final int CHATS = 0, CHAT = 1, SETTINGS = 2;

    /** Everything the miniature needs. Defaults are Telegram's own (classic) look. */
    static final class Style {
        int bg = 0xFFF2F2F7, accent = 0xFF3390EC, onAccent = 0xFFFFFFFF, text = 0xFF14213D, textSecondary = 0, bubbleIn = 0, bubbleOut = 0, bubbleOutText = 0;
        int top, mid, bottom;           // page gradient (0 = none)
        int cardFill = 0, cardBorder = 0;
        boolean dark;
        int cardRadiusDp = 22, bubbleRadiusDp = 12;
        // layout pack
        boolean flatTabs, dividers = true, storyRail, inputPill, headerMenu, headerCompose, headerSearch;
        String folders = "tabs";        // tabs | chips | none
        int avatarDp = 52, tabCount = 4, rowHeightDp = 70;
        boolean plusLeading;

        static Style palette(int bg, int accent, boolean dark, int top, int mid, int bottom) {
            Style s = new Style();
            s.bg = bg;
            s.accent = accent;
            s.dark = dark;
            s.text = dark ? 0xFFFFFFFF : 0xFF14213D;
            s.top = top;
            s.mid = mid;
            s.bottom = bottom;
            return s;
        }

        /** Overrides from a real skin (colours that are set, radii, gradient). */
        void applySkin(SkinSpec k) {
            int v;
            if ((v = k.colorValue("tokens.colors.accent")) != 0) accent = v;
            if ((v = k.colorValue("tokens.colors.onAccent")) != 0) onAccent = v;
            if ((v = k.colorValue("tokens.colors.background")) != 0) bg = v | 0xFF000000;
            if ((v = k.colorValue("tokens.colors.textPrimary")) != 0) {
                text = v | 0xFF000000;
                dark = ColorUtils.calculateLuminance(text) > 0.5;
            }
            if ((v = k.colorValue("tokens.colors.textSecondary")) != 0) textSecondary = v;
            if ((v = k.colorValue("tokens.gradient.top")) != 0) top = v | 0xFF000000;
            if ((v = k.colorValue("tokens.gradient.middle")) != 0) mid = v | 0xFF000000;
            if ((v = k.colorValue("tokens.gradient.bottom")) != 0) bottom = v | 0xFF000000;
            if ((v = k.colorValue("components.card.background")) != 0) cardFill = v;
            else if ((v = k.colorValue("tokens.colors.surface")) != 0) cardFill = v;
            if ((v = k.colorValue("components.card.borderColor")) != 0) cardBorder = v;
            if ((v = k.colorValue("chat.bubbleIn")) != 0) bubbleIn = v;
            if ((v = k.colorValue("chat.bubbleOut")) != 0) bubbleOut = v;
            if ((v = k.colorValue("chat.bubbleOutText")) != 0) bubbleOutText = v;
            cardRadiusDp = k.intValue("tokens.shapes.cardRadiusDp", cardRadiusDp);
            int b = k.intValue("chat.bubbleRadiusDp", k.intValue("tokens.shapes.bubbleRadiusDp", -1));
            if (b >= 0) bubbleRadiusDp = b;
        }

        /** Structure from a layout pack; a classic layout changes nothing. */
        void applyLayout(LayoutSpec l) {
            if (l == null || l.isClassic()) return;
            flatTabs = "flat".equals(l.stringValue("bottomTabs.style", "glass"));
            dividers = l.boolValue("chatList.row.dividers", dividers);
            storyRail = l.boolValue("chatList.storyRail.enabled", false);
            folders = l.stringValue("chatList.folders.style", folders);
            avatarDp = l.intValue("chatList.row.avatar.sizeDp", avatarDp);
            rowHeightDp = l.intValue("chatList.row.heightDp", rowHeightDp);
            List<String> left = l.listValue("chatList.header.left", null);
            List<String> right = l.listValue("chatList.header.right", null);
            if (left != null || right != null) {
                headerMenu = left != null && left.contains("menu");
                headerCompose = (right != null && right.contains("compose")) || (left != null && left.contains("compose"));
                headerSearch = (right != null && right.contains("search")) || (left != null && left.contains("search"));
            }
            List<String> tabs = l.listValue("bottomTabs.items", null);
            if (tabs != null) tabCount = tabs.size();
            inputPill = "pill".equals(l.stringValue("chat.input.style", "classic"));
            List<String> lead = l.listValue("chat.input.leading", null);
            plusLeading = lead != null && lead.contains("plus");
        }
    }

    private final int mode;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF r = new RectF();
    private Style st = new Style();
    private int radiusDp = 14;
    private boolean iconMode;

    PhoneScene(Context context, int mode) {
        super(context);
        this.mode = mode;
    }

    void setIconMode(boolean on) {
        iconMode = on;
        invalidate();
    }

    /** Palette only (grid cards, before a theme's content is known). */
    void set(int bg, int accent, boolean dark, int gradientTop, int gradientMiddle, int gradientBottom, int radiusDp) {
        this.st = Style.palette(bg, accent, dark, gradientTop, gradientMiddle, gradientBottom);
        this.radiusDp = radiusDp;
        invalidate();
    }

    void setStyle(Style style, int radiusDp) {
        this.st = style;
        this.radiusDp = radiusDp;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) (width * 1.9f));
    }

    private boolean hasGradient() {
        return st.top != 0 && st.bottom != 0;
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

        paint.setStyle(Paint.Style.FILL);
        if (hasGradient()) {
            int[] colors = st.mid != 0 ? new int[]{st.top, st.mid, st.bottom} : new int[]{st.top, st.bottom};
            paint.setShader(new LinearGradient(0, 0, w * 0.6f, h, colors, null, Shader.TileMode.CLAMP));
        } else {
            paint.setColor(st.bg);
        }
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        if (iconMode) {
            drawIcon(canvas, u);
        } else if (mode == CHATS) {
            drawChats(canvas, u, w, h);
        } else if (mode == CHAT) {
            drawChat(canvas, u, w, h);
        } else {
            drawSettings(canvas, u, w, h);
        }
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, AndroidUtilities.dp(0.5f)));
        paint.setColor(StickerPackGridCell.strokeColor());
        r.set(0, 0, w, h);
        canvas.drawRoundRect(r, radius, radius, paint);
    }

    // ---- shared pieces ----

    private int fill() {
        if (st.cardFill != 0) {
            return st.cardFill;
        }
        return hasGradient() ? ColorUtils.setAlphaComponent(0xFFFFFFFF, st.dark ? 0x26 : 0x99)
                : ColorUtils.blendARGB(st.bg, 0xFFFFFFFF, st.dark ? 0.10f : 0.7f);
    }

    private float cr(float u) {
        // card radius scaled to the miniature (a 22dp card on a 360dp screen ~ 6u)
        return Math.min(8 * u, st.cardRadiusDp / 22f * 5f * u);
    }

    private void glass(Canvas c, float l, float t, float rr, float b, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill());
        r.set(l, t, rr, b);
        c.drawRoundRect(r, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, AndroidUtilities.dp(0.5f)));
        paint.setColor(st.cardBorder != 0 ? st.cardBorder : ColorUtils.setAlphaComponent(0xFFFFFFFF, st.dark ? 0x40 : 0xCC));
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
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(st.accent, hsv);
        hsv[0] = (hsv[0] + i * 47) % 360;
        hsv[1] = Math.max(0.35f, Math.min(0.8f, hsv[1]));
        hsv[2] = Math.max(0.65f, hsv[2]);
        return android.graphics.Color.HSVToColor(hsv);
    }

    /** The bottom tab bar: a floating glass pill (classic) or a flat full-width bar (flat layout). */
    private void tabs(Canvas c, float u, float w, float h, int selected) {
        int n = Math.max(2, st.tabCount);
        float barTop;
        if (st.flatTabs) {
            barTop = h - 13 * u;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(hasGradient() ? ColorUtils.setAlphaComponent(0xFFFFFFFF, st.dark ? 0x30 : 0xB0) : ColorUtils.blendARGB(st.bg, st.dark ? 0xFF000000 : 0xFFFFFFFF, 0.6f));
            c.drawRect(0, barTop, w, h, paint);
            line(c, st.text, 0.12f, 0, barTop, w, barTop + 0.4f * u);
        } else {
            barTop = h - 14 * u;
            glass(c, 5 * u, barTop, w - 5 * u, h - 4 * u, 5 * u);
        }
        float left = st.flatTabs ? 0 : 5 * u, cell = (w - 2 * left) / n;
        float cy = barTop + (st.flatTabs ? 4.4f : 4.2f) * u;
        for (int i = 0; i < n; i++) {
            float cx = left + cell * (i + 0.5f);
            boolean on = i == Math.min(selected, n - 1);
            if (!st.flatTabs && on) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(st.accent, 0x30));
                r.set(cx - 9 * u, barTop + 1 * u, cx + 9 * u, barTop + 9 * u);
                c.drawRoundRect(r, 4 * u, 4 * u, paint);
            }
            dot(c, on ? st.accent : ColorUtils.setAlphaComponent(st.text, 0x70), cx, cy, 2.2f * u);
            if (st.flatTabs) {
                line(c, on ? st.accent : st.text, on ? 0.9f : 0.35f, cx - 4 * u, cy + 4.6f * u, cx + 4 * u, cy + 5.8f * u);
            }
        }
    }

    // ---- screens ----

    private void drawChats(Canvas c, float u, float w, float h) {
        float y;
        // header
        if (st.headerMenu) {
            for (int i = 0; i < 3; i++) line(c, st.text, 0.8f, 5 * u, (6.5f + i * 1.7f) * u, 12 * u, (7.4f + i * 1.7f) * u);
            line(c, st.accent, 0.95f, 16 * u, 6.6f * u, 46 * u, 9.6f * u);
        } else {
            dot(c, shade(1), 10 * u, 9 * u, 4 * u);
            line(c, st.accent, 0.9f, 18 * u, 7.6f * u, 42 * u, 10.4f * u);
        }
        if (st.headerCompose || st.headerSearch) {
            float x = w - 7 * u;
            if (st.headerSearch) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1, AndroidUtilities.dp(1)));
                paint.setColor(ColorUtils.setAlphaComponent(st.text, 0xC0));
                c.drawCircle(x, 9 * u, 1.8f * u, paint);
                x -= 8 * u;
            }
            if (st.headerCompose) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(ColorUtils.setAlphaComponent(st.text, 0xC0));
                r.set(x - 2 * u, 7 * u, x + 2 * u, 11 * u);
                c.drawRoundRect(r, 1 * u, 1 * u, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        } else {
            dot(c, ColorUtils.setAlphaComponent(st.text, 0x80), w - 8 * u, 9 * u, 1.3f * u);
        }
        y = 16 * u;
        if (st.storyRail) {
            float rad = 5.2f * u;
            for (int i = 0; i < 5; i++) {
                float cx = 11 * u + i * 17 * u;
                dot(c, shade(i), cx, y + rad, rad);
                if (i == 0) {
                    dot(c, st.accent, cx + rad * 0.75f, y + rad * 1.7f, 1.7f * u);
                } else {
                    dot(c, 0xFF31C45B, cx + rad * 0.75f, y + rad * 1.65f, 1.3f * u);
                }
                line(c, st.text, 0.35f, cx - 4 * u, y + 2 * rad + 1.6f * u, cx + 4 * u, y + 2 * rad + 2.8f * u);
            }
            y += 2 * 5.2f * u + 6 * u;
        }
        if (!st.headerSearch) {
            glass(c, 5 * u, y, w - 5 * u, y + 7 * u, 3.5f * u);
            line(c, st.text, 0.25f, 12 * u, y + 2.6f * u, 36 * u, y + 4.4f * u);
            y += 10 * u;
        }
        if ("chips".equals(st.folders)) {
            float x = 5 * u;
            for (int i = 0; i < 3; i++) {
                float cw = (i == 0 ? 14 : 18) * u;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(i == 0 ? ColorUtils.setAlphaComponent(st.accent, 0xE0) : fill());
                r.set(x, y, x + cw, y + 5.4f * u);
                c.drawRoundRect(r, 2.7f * u, 2.7f * u, paint);
                line(c, i == 0 ? st.onAccent : st.text, i == 0 ? 0.9f : 0.4f, x + 2.5f * u, y + 2.2f * u, x + cw - 2.5f * u, y + 3.2f * u);
                x += cw + 2.5f * u;
            }
            y += 8.4f * u;
        } else if ("tabs".equals(st.folders)) {
            glass(c, 5 * u, y, w - 5 * u, y + 7 * u, 3.5f * u);
            r.set(7 * u, y + 1 * u, 28 * u, y + 6 * u);
            paint.setColor(ColorUtils.setAlphaComponent(st.accent, 0x38));
            c.drawRoundRect(r, 2.5f * u, 2.5f * u, paint);
            line(c, st.accent, 0.9f, 10 * u, y + 2.8f * u, 24 * u, y + 4.2f * u);
            line(c, st.text, 0.3f, 34 * u, y + 2.8f * u, 48 * u, y + 4.2f * u);
            line(c, st.text, 0.3f, 56 * u, y + 2.8f * u, 70 * u, y + 4.2f * u);
            y += 10 * u;
        }
        float av = st.avatarDp / 52f * 4.6f * u;          // avatar radius
        float rowH = Math.max(av * 2 + 2.4f * u, st.rowHeightDp / 70f * 12 * u);
        float bottomLimit = h - 15 * u;
        int i = 0;
        while (y + rowH <= bottomLimit && i < 8) {
            float cy = y + rowH / 2;
            dot(c, shade(i), 5 * u + av, cy, av);
            if (st.flatTabs && i % 2 == 1) {
                dot(c, 0xFF31C45B, 5 * u + av * 1.7f, cy + av * 0.7f, 1.4f * u);
            }
            float tx = 5 * u + av * 2 + 3 * u;
            line(c, st.text, i % 3 == 0 ? 0.95f : 0.7f, tx, cy - 3.4f * u, tx + 34 * u, cy - 1.2f * u);
            line(c, st.text, 0.3f, tx, cy + 1.2f * u, tx + 48 * u, cy + 2.8f * u);
            line(c, st.text, 0.25f, w - 17 * u, cy - 3.2f * u, w - 6 * u, cy - 1.6f * u);
            if (i % 2 == 0) dot(c, st.accent, w - 8.5f * u, cy + 3 * u, 2.2f * u);
            if (st.dividers && i > 0) line(c, st.text, 0.1f, tx, y, w - 5 * u, y + 0.35f * u);
            y += rowH;
            i++;
        }
        tabs(c, u, w, h, 0);
    }

    private void bubble(Canvas c, float l, float t, float rr, float b, int fillColor, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fillColor);
        r.set(l, t, rr, b);
        c.drawRoundRect(r, radius, radius, paint);
    }

    private void drawChat(Canvas c, float u, float w, float h) {
        float br = Math.max(1 * u, Math.min(6 * u, st.bubbleRadiusDp / 17f * 6 * u));
        int incoming = st.bubbleIn != 0 ? st.bubbleIn : fill();
        int outgoing = st.bubbleOut != 0 ? st.bubbleOut : ColorUtils.setAlphaComponent(st.accent, hasGradient() ? 0xCC : 0xFF);
        int outText = st.bubbleOutText != 0 ? st.bubbleOutText
                : ColorUtils.calculateLuminance(outgoing | 0xFF000000) > 0.6 ? 0xFF14213D : 0xFFFFFFFF;

        glass(c, 4 * u, 4 * u, w - 4 * u, 15 * u, 5.5f * u);
        dot(c, ColorUtils.setAlphaComponent(st.text, 0x70), 10 * u, 9.5f * u, 2 * u);
        dot(c, shade(2), 20 * u, 9.5f * u, 3.6f * u);
        line(c, st.text, 0.75f, 27 * u, 7.6f * u, 54 * u, 10.2f * u);
        line(c, st.text, 0.3f, 27 * u, 11.2f * u, 46 * u, 12.8f * u);

        float y = 22 * u;
        bubble(c, 6 * u, y, 62 * u, y + 13 * u, incoming, br);
        line(c, st.accent, 0.9f, 10 * u, y + 2.6f * u, 26 * u, y + 4.4f * u);
        line(c, st.text, 0.7f, 10 * u, y + 6.4f * u, 54 * u, y + 8.2f * u);
        line(c, st.text, 0.7f, 10 * u, y + 9.4f * u, 40 * u, y + 11.2f * u);
        y += 17 * u;
        bubble(c, 6 * u, y, 44 * u, y + 8.5f * u, incoming, br);
        line(c, st.text, 0.7f, 10 * u, y + 3.2f * u, 36 * u, y + 5f * u);
        y += 14 * u;
        bubble(c, 36 * u, y, w - 6 * u, y + 11 * u, outgoing, br);
        line(c, outText, 0.9f, 41 * u, y + 3.2f * u, w - 14 * u, y + 5f * u);
        line(c, outText, 0.9f, 41 * u, y + 6.4f * u, w - 26 * u, y + 8.2f * u);
        y += 15 * u;
        bubble(c, 58 * u, y, w - 6 * u, y + 8 * u, outgoing, br);
        line(c, outText, 0.9f, 62 * u, y + 3f * u, w - 12 * u, y + 4.8f * u);
        y += 12 * u;
        bubble(c, 6 * u, y, 52 * u, y + 8.5f * u, incoming, br);
        line(c, st.text, 0.7f, 10 * u, y + 3.2f * u, 44 * u, y + 5f * u);

        float inLeft = 4 * u;
        if (st.plusLeading) {
            dot(c, st.accent, 8.5f * u, h - 8.5f * u, 3.4f * u);
            line(c, st.onAccent, 0.95f, 6.8f * u, h - 8.9f * u, 10.2f * u, h - 8.1f * u);
            inLeft = 14 * u;
        }
        glass(c, inLeft, h - 13 * u, w - 17 * u, h - 4 * u, st.inputPill ? 4.5f * u : 2.2f * u);
        line(c, st.text, 0.3f, inLeft + 6 * u, h - 9.6f * u, inLeft + 28 * u, h - 7.6f * u);
        dot(c, st.accent, w - 9 * u, h - 8.5f * u, 4.4f * u);
    }

    private void drawSettings(Canvas c, float u, float w, float h) {
        float cr = cr(u);
        dot(c, shade(3), w / 2, 17 * u, 9 * u);
        dot(c, st.accent, w / 2 + 6.5f * u, 22.5f * u, 2.6f * u);
        line(c, st.text, 0.8f, w / 2 - 14 * u, 30 * u, w / 2 + 14 * u, 32.6f * u);
        line(c, st.text, 0.3f, w / 2 - 10 * u, 34 * u, w / 2 + 10 * u, 35.8f * u);

        glass(c, 5 * u, 40 * u, w - 5 * u, 54 * u, cr);
        line(c, st.accent, 0.9f, 10 * u, 42.6f * u, 24 * u, 44.2f * u);
        dot(c, shade(4), 11 * u, 49.6f * u, 2.6f * u);
        line(c, st.text, 0.7f, 17 * u, 48.6f * u, 46 * u, 50.6f * u);

        float top2 = 57 * u;
        glass(c, 5 * u, top2, w - 5 * u, top2 + 44 * u, cr);
        float y = top2 + 4 * u;
        for (int i = 0; i < 4; i++) {
            r.set(9 * u, y, 17 * u, y + 8 * u);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(shade(i));
            c.drawRoundRect(r, 2.4f * u, 2.4f * u, paint);
            line(c, st.text, 0.75f, 21 * u, y + 1.4f * u, 56 * u, y + 3.6f * u);
            line(c, st.text, 0.28f, 21 * u, y + 4.8f * u, 44 * u, y + 6.6f * u);
            y += 10 * u;
        }
        tabs(c, u, w, h, 2);
    }

    /** The theme's logo: its page with two glass bubbles, the second in the accent. */
    private void drawIcon(Canvas c, float u) {
        glass(c, 14 * u, 22 * u, 70 * u, 50 * u, 12 * u);
        line(c, st.text, 0.6f, 24 * u, 32 * u, 58 * u, 38 * u);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(st.accent);
        r.set(34 * u, 56 * u, 88 * u, 80 * u);
        c.drawRoundRect(r, 11 * u, 11 * u, paint);
        line(c, ColorUtils.calculateLuminance(st.accent) > 0.6 ? 0xFF14213D : 0xFFFFFFFF, 0.9f, 44 * u, 65 * u, 76 * u, 71 * u);
    }
}
