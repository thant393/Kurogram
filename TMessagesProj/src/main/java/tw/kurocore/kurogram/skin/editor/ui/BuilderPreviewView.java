package tw.kurocore.kurogram.skin.editor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.List;

import tw.kurocore.kurogram.skin.editor.EditorSession;

/**
 * Preview A: a simulated phone screen drawn from the draft. Every part registers a hit region, so tapping a part selects the
 * component (and the controls below show its settings). It is a drawing, not the real app: it approximates the look and says
 * so in the Builder; the real-app preview (B) is the reference.
 */
public class BuilderPreviewView extends View {
    public interface Listener {
        void onComponentTapped(String componentId);
    }

    private static final class Hit {
        final String id;
        final RectF r;

        Hit(String id, RectF r) {
            this.id = id;
            this.r = new RectF(r);
        }
    }

    private EditorSession session;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final List<Hit> hits = new ArrayList<>();
    private Listener listener;

    public BuilderPreviewView(Context context, EditorSession session) {
        super(context);
        this.session = session;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(AndroidUtilities.dp(2));
        outline.setPathEffect(new android.graphics.DashPathEffect(new float[]{AndroidUtilities.dp(6), AndroidUtilities.dp(4)}, 0));
    }

    public void setSession(EditorSession s) {
        session = s;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, Math.min(AndroidUtilities.dp(340), width));
    }

    private int c(String name, int light, int dark) {
        return session.color("tokens.colors." + name, "dark".equals(session.appearance()) ? dark : light);
    }

    // ---- values read from the draft, falling back to the app's own defaults like the engine does
    private int dp(float v) {
        return AndroidUtilities.dp(v);
    }

    private float rad(String path, float def) {
        return dp(session.intValue(path, (int) def));
    }

    private boolean bold(String path, boolean def) {
        String w = session.text(path, null);
        return w == null ? def : !"normal".equals(w) && !"regular".equals(w);
    }

    private void round(Canvas canvas, RectF r, float radiusPx) {
        float m = Math.min(r.width(), r.height()) / 2f;
        canvas.drawRoundRect(r, Math.min(radiusPx, m), Math.min(radiusPx, m), p);
    }

    private void shadow(Canvas canvas, RectF r, float radiusPx, int dpElev) {
        if (dpElev <= 0) {
            return;
        }
        int save = p.getColor();
        for (int i = dpElev; i >= 1; i--) {
            p.setColor((int) (0x10 * (1f - i / (float) (dpElev + 1))) << 24);
            RectF g = new RectF(r.left - dp(i * 0.5f), r.top - dp(i * 0.2f), r.right + dp(i * 0.5f), r.bottom + dp(i));
            canvas.drawRoundRect(g, radiusPx + dp(i * 0.5f), radiusPx + dp(i * 0.5f), p);
        }
        p.setColor(save);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        hits.clear();
        int w = getWidth(), h = getHeight();
        boolean dark = "dark".equals(session.appearance());
        int bg = c("background", 0xFFF2F2F7, 0xFF0F0F10), surface = c("surface", 0xFFFFFFFF, 0xFF1C1C1E);
        int text = c("textPrimary", 0xFF14213D, 0xFFFFFFFF), text2 = c("textSecondary", 0xFF7A8499, 0xFF98989F);
        int accent = c("accent", 0xFF3390EC, 0xFF3390EC), onAccent = c("onAccent", 0xFFFFFFFF, 0xFFFFFFFF);
        int divider = c("divider", 0x1F000000, 0x33FFFFFF), icon = c("icon", 0xFF8A93A6, 0xFF98989F);
        int error = c("error", 0xFFE5484D, 0xFFE5484D);
        Ctx x = new Ctx();
        x.w = w;
        x.h = h;
        x.surface = surface;
        x.text = text;
        x.text2 = text2;
        x.accent = accent;
        x.onAccent = onAccent;
        x.divider = divider;
        x.icon = icon;
        x.error = error;
        x.bg = bg;

        // page background: gradient when any stop is set, else the background colour
        int gt = session.color("tokens.gradient.top", 0), gm = session.color("tokens.gradient.middle", 0), gb = session.color("tokens.gradient.bottom", 0);
        rect.set(0, 0, w, h);
        boolean chat = "chat".equals(session.scene());
        int wp = session.color("chat.wallpaperColor", 0);
        if (chat && wp != 0) {
            int wp2 = session.color("chat.wallpaperColor2", wp);
            double ang = Math.toRadians(session.intValue("chat.wallpaperAngle", 0));
            p.setShader(new LinearGradient(w / 2f - (float) Math.sin(ang) * w / 2f, h / 2f - (float) Math.cos(ang) * h / 2f,
                    w / 2f + (float) Math.sin(ang) * w / 2f, h / 2f + (float) Math.cos(ang) * h / 2f, wp, wp2, Shader.TileMode.CLAMP));
        } else if (gt != 0 || gm != 0 || gb != 0) {
            int a = gt != 0 ? gt : gm != 0 ? gm : gb, b = gb != 0 ? gb : gm != 0 ? gm : gt, m = gm != 0 ? gm : a;
            Object dir = session.draft.get("tokens.gradient.direction");
            float x2 = 0, y2 = h;
            if ("horizontal".equals(dir)) {
                x2 = w;
                y2 = 0;
            } else if ("diagonal".equals(dir)) {
                x2 = w;
            }
            p.setShader(new LinearGradient(0, 0, x2, y2, new int[]{a, m, b}, null, Shader.TileMode.CLAMP));
        } else {
            p.setShader(null);
            p.setColor(bg);
        }
        canvas.drawRect(rect, p);
        p.setShader(null);
        add("screenBackground", rect);

        switch (session.scene()) {
            case "settings":
                drawSettings(canvas, x);
                break;
            case "chat":
                drawChat(canvas, x);
                break;
            case "controls":
                drawControls(canvas, x);
                break;
            default:
                drawChats(canvas, x);
        }

        // selection outline
        String sel = session.selected();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.id.equals(sel) && !"global".equals(sel)) {
                outline.setColor(0xFFFF2D87);
                rect.set(hit.r);
                rect.inset(-dp(1), -dp(1));
                canvas.drawRoundRect(rect, dp(4), dp(4), outline);
            }
        }
    }

    private static final class Ctx {
        int w, h, bg, surface, text, text2, accent, onAccent, divider, icon, error;
    }

    private void add(String id, RectF r) {
        hits.add(new Hit(id, r));
    }

    /** Top bar: components.appBar.* over the tokens. Returns its height in px. */
    private int bar(Canvas canvas, Ctx x, String title) {
        int bh = dp(session.intValue("components.appBar.heightDp", 52));
        int fill = session.colorChain(x.surface, "components.appBar.background");
        int fg = session.colorChain(x.text, "components.appBar.foreground");
        rect.set(0, 0, x.w, bh);
        p.setColor(fill);
        canvas.drawRect(rect, p);
        add("appBar", rect);
        p.setColor(fg);
        p.setTextSize(dp(session.intValue("tokens.typography.titleSizeSp", 18)));
        p.setFakeBoldText(true);
        canvas.drawText(title, dp(16), bh / 2f + dp(6), p);
        p.setFakeBoldText(false);
        p.setColor(fg);
        p.setAlpha(150);
        canvas.drawCircle(x.w - dp(28), bh / 2f, dp(8), p);
        canvas.drawCircle(x.w - dp(60), bh / 2f, dp(8), p);
        p.setAlpha(255);
        return bh;
    }

    private void drawTabs(Canvas canvas, Ctx x, int tabsH) {
        float ty = x.h - tabsH;
        int fill = session.colorChain(x.surface, "components.navigationBar.background", "components.bottomNavigation.background");
        int sel = session.colorChain(x.accent, "components.navigationBar.selected");
        int unsel = session.colorChain(x.icon, "components.navigationBar.unselected");
        int pill = session.colorChain(0, "components.bottomNavigation.selectedBackground");
        float ic = dp(session.intValue("components.bottomNavigation.iconSizeDp", 24)) / 2.6f;
        rect.set(0, ty, x.w, x.h);
        p.setColor(fill);
        canvas.drawRect(rect, p);
        add("bottomTabs", rect);
        String[] labels = {"Chats", "Contacts", "Calls", "Settings"};
        for (int i = 0; i < 4; i++) {
            float cx = x.w * (i + 0.5f) / 4f;
            rect.set(cx - x.w / 8f, ty, cx + x.w / 8f, x.h);
            if (i == 0 && pill != 0) {
                RectF b = new RectF(cx - dp(30), ty + dp(6), cx + dp(30), ty + dp(34));
                p.setColor(pill);
                canvas.drawRoundRect(b, dp(14), dp(14), p);
            }
            p.setColor(i == 0 ? sel : unsel);
            canvas.drawCircle(cx, ty + dp(20), ic, p);
            p.setTextSize(dp(10));
            canvas.drawText(labels[i], cx - p.measureText(labels[i]) / 2f, ty + dp(44), p);
            add("bottomTabs.item", rect);
        }
    }

    private void drawChats(Canvas canvas, Ctx x) {
        int top = bar(canvas, x, "Kurogram");
        int rowH = dp(session.intValue("components.cell.heightDp", 64)), tabsH = dp(56);
        int body = session.intValue("tokens.typography.bodySizeSp", 16);
        int cap = session.intValue("tokens.typography.captionSizeSp", 12);
        boolean titleBold = bold("tokens.typography.fontWeight", true);
        String[] names = {"Aye Aye", "Design team", "Ko Min", "Saved Messages", "Daw Mya"};
        String[] msgs = {"See you tomorrow!", "New theme is ready", "ok 👍", "Notes", "Thank you"};
        String[] times = {"21:40", "20:15", "Fri", "Thu", "Mon"};
        int[] avatars = {0xFFE17076, 0xFF7BC862, 0xFF65AADD, 0xFFEE7AAE, 0xFFF3A93C};
        int rows = Math.max(1, Math.min(names.length, (x.h - top - tabsH) / rowH));
        for (int i = 0; i < rows; i++) {
            float y = top + i * rowH;
            rect.set(0, y, x.w, y + rowH);
            p.setColor(x.surface);
            canvas.drawRect(rect, p);
            add("chatRow", rect);
            float cy = y + rowH / 2f;
            p.setColor(avatars[i]);
            float ar = Math.min(dp(22), rowH / 2f - dp(4));
            rect.set(dp(12), cy - ar, dp(12) + 2 * ar, cy + ar);
            canvas.drawOval(rect, p);
            add("chatRow.avatar", rect);
            p.setColor(x.text);
            p.setFakeBoldText(titleBold);
            p.setTextSize(dp(body));
            float tx = dp(24) + 2 * ar;
            canvas.drawText(names[i], tx, cy - dp(4), p);
            rect.set(tx, cy - dp(body + 4), x.w * 0.62f, cy);
            add("chatRow.title", rect);
            p.setFakeBoldText(false);
            p.setColor(x.text2);
            p.setTextSize(dp(Math.max(10, body - 2)));
            canvas.drawText(msgs[i], tx, cy + dp(16), p);
            rect.set(tx, cy + dp(2), x.w * 0.62f, cy + dp(22));
            add("chatRow.preview", rect);
            p.setTextSize(dp(cap));
            float tw = p.measureText(times[i]);
            canvas.drawText(times[i], x.w - dp(12) - tw, cy - dp(6), p);
            rect.set(x.w - dp(12) - tw, cy - dp(cap + 8), x.w - dp(12), cy - dp(2));
            add("chatRow.time", rect);
            if (i < 2) {
                p.setColor(i == 0 ? x.accent : x.error);
                rect.set(x.w - dp(36), cy + dp(4), x.w - dp(12), cy + dp(24));
                canvas.drawRoundRect(rect, dp(10), dp(10), p);
                p.setColor(x.onAccent);
                p.setTextSize(dp(12));
                canvas.drawText(String.valueOf(i + 2), x.w - dp(27), cy + dp(19), p);
                add("chatRow.badge", rect);
            }
            if (session.bool("components.cell.dividerVisible", true)) {
                p.setColor(session.colorChain(x.divider, "components.cell.dividerColor"));
                canvas.drawRect(tx, y + rowH - 1, x.w, y + rowH, p);
            }
        }
        drawTabs(canvas, x, tabsH);
        float fs = dp(session.intValue("components.fab.sizeDp", 52));
        float fx = x.w - dp(16) - fs, fy = x.h - tabsH - dp(16) - fs;
        rect.set(fx, fy, fx + fs, fy + fs);
        p.setColor(x.accent);
        round(canvas, rect, dp(session.intValue("components.fab.radiusDp", 26)));
        p.setColor(x.onAccent);
        p.setStrokeWidth(dp(2.5f));
        canvas.drawLine(fx + fs / 2 - dp(9), fy + fs / 2, fx + fs / 2 + dp(9), fy + fs / 2, p);
        canvas.drawLine(fx + fs / 2, fy + fs / 2 - dp(9), fx + fs / 2, fy + fs / 2 + dp(9), p);
        rect.set(fx, fy, fx + fs, fy + fs);
        add("fab", rect);
    }

    private void drawSettings(Canvas canvas, Ctx x) {
        int top = bar(canvas, x, "Settings");
        boolean card = !"flat".equals(session.text("components.section.style", "card"));
        int gap = dp(session.intValue("components.section.gapDp", 16));
        int rowH = dp(session.intValue("components.cell.heightDp", 56));
        int title = session.intValue("components.cell.titleSizeSp", 15), val = session.intValue("components.cell.valueSizeSp", 12);
        boolean tb = bold("components.cell.titleWeight", false);
        float ic = dp(session.intValue("components.cell.iconSizeDp", 20)) / 2f;
        int padL = dp(session.intValue("components.cell.paddingStartDp", 16));
        String[] titles = {"Notifications", "Privacy", "Data and storage", "Appearance", "Language"};
        String[] subs = {"On", "Calls, last seen", "Network usage", "Theme, colours", "English"};
        int y0 = top + gap;
        int rows = Math.max(1, Math.min(titles.length, (x.h - y0 - gap) / rowH));
        float side = card ? dp(12) : 0;
        rect.set(side, y0, x.w - side, y0 + rows * rowH);
        p.setColor(session.colorChain(x.surface, "components.card.background"));
        float rr = card ? dp(session.intValue("components.section.radiusDp", session.intValue("tokens.shapes.cardRadiusDp", 14))) : 0;
        shadow(canvas, rect, rr, session.intValue("tokens.elevation.cardDp", 0));
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, rr, rr, p);
        add("section", rect);
        for (int i = 0; i < rows; i++) {
            float y = y0 + i * rowH, cy = y + rowH / 2f;
            rect.set(side, y, x.w - side, y + rowH);
            add("cell", rect);
            p.setColor(i % 2 == 0 ? x.accent : x.icon);
            canvas.drawCircle(side + padL + ic, cy, ic, p);
            float tx = side + padL + 2 * ic + dp(14);
            p.setColor(x.text);
            p.setFakeBoldText(tb);
            p.setTextSize(dp(title));
            canvas.drawText(titles[i], tx, cy - dp(2), p);
            p.setFakeBoldText(false);
            p.setColor(x.text2);
            p.setTextSize(dp(val));
            canvas.drawText(subs[i], tx, cy + dp(val + 3), p);
            if (i < rows - 1 && session.bool("components.cell.dividerVisible", true)) {
                p.setColor(session.colorChain(x.divider, "components.cell.dividerColor"));
                float inset = dp(session.intValue("components.cell.dividerInsetDp", 60));
                canvas.drawRect(side + inset, y + rowH - 1, x.w - side, y + rowH, p);
            }
        }
    }

    private void drawChat(Canvas canvas, Ctx x) {
        int top = bar(canvas, x, "Aye Aye");
        rect.set(0, 0, x.w, top);
        hits.remove(hits.size() - 1); // the bar registered itself as appBar; in a conversation it is the chat header too
        add("appBar", rect);
        add("chatHeader", rect);
        int br = session.intValue("chat.bubbleRadiusDp", session.intValue("tokens.shapes.bubbleRadiusDp", 12));
        int bin = session.color("chat.bubbleIn", x.surface), bout = session.color("chat.bubbleOut", x.accent);
        int tin = session.color("chat.bubbleInText", x.text), tout = session.color("chat.bubbleOutText", x.onAccent);
        float fs = dp(session.intValue("chat.fontSizeSp", 16));
        // date pill
        String date = "Today";
        p.setTextSize(dp(12));
        float dw = p.measureText(date) + dp(20);
        rect.set((x.w - dw) / 2f, top + dp(10), (x.w + dw) / 2f, top + dp(30));
        p.setColor(session.color("chat.dateBackground", 0x55000000));
        canvas.drawRoundRect(rect, rad("chat.dateRadiusDp", 10), rad("chat.dateRadiusDp", 10), p);
        p.setColor(session.color("chat.dateText", 0xFFFFFFFF));
        canvas.drawText(date, rect.left + dp(10), rect.bottom - dp(6), p);
        add("datePill", rect);
        String[] msgs = {"Is the new theme ready?", "Yes, sending it now", "Looks great!"};
        boolean[] mine = {false, true, false};
        float y = top + dp(42);
        for (int i = 0; i < msgs.length; i++) {
            p.setTextSize(fs);
            float mw = p.measureText(msgs[i]) + dp(24), mh = fs + dp(20);
            float left = mine[i] ? x.w - dp(12) - mw : dp(12);
            rect.set(left, y, left + mw, y + mh);
            p.setColor(mine[i] ? bout : bin);
            canvas.drawRoundRect(rect, dp(br), dp(br), p);
            add(mine[i] ? "bubble.out" : "bubble.in", rect);
            p.setColor(mine[i] ? tout : tin);
            canvas.drawText(msgs[i], left + dp(12), y + mh / 2f + fs / 3f, p);
            y += mh + dp(8);
        }
        // reaction / menu panel
        float pr = rad("chat.panelRadiusDp", 14);
        rect.set(dp(12), y + dp(4), dp(150), y + dp(44));
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, pr, pr, p);
        add("chatPanel", rect);
        p.setColor(x.text2);
        p.setTextSize(dp(12));
        canvas.drawText("Reply   Copy", dp(24), y + dp(30), p);
        // input row
        float ih = dp(44), iy = x.h - ih - dp(8);
        rect.set(dp(8), iy, x.w - dp(56), iy + ih);
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, rad("chat.inputRadiusDp", 22), rad("chat.inputRadiusDp", 22), p);
        add("chatInput", rect);
        p.setColor(x.text2);
        p.setTextSize(dp(15));
        canvas.drawText("Message", dp(20), iy + ih / 2f + dp(5), p);
        float br2 = rad("chat.buttonRadiusDp", 22);
        rect.set(x.w - dp(48), iy, x.w - dp(4), iy + ih);
        p.setColor(session.color("chat.sendButton", x.accent));
        canvas.drawRoundRect(rect, br2, br2, p);
        add("chatInput", rect);
    }

    private void drawControls(Canvas canvas, Ctx x) {
        int top = bar(canvas, x, "Components");
        float y = top + dp(12);
        float side = dp(12);
        // card
        int cardFill = session.colorChain(x.surface, "components.card.background");
        float cr = rad("tokens.shapes.cardRadiusDp", 14);
        rect.set(side, y, x.w - side, y + dp(70));
        p.setColor(cardFill);
        shadow(canvas, rect, cr, session.intValue("tokens.elevation.cardDp", 0));
        p.setColor(cardFill);
        canvas.drawRoundRect(rect, cr, cr, p);
        int bw = session.intValue("components.card.borderWidthDp", 0);
        if (bw > 0) {
            outlineFill(canvas, rect, cr, session.colorChain(x.divider, "components.card.borderColor"), bw);
        }
        add("card", rect);
        p.setColor(x.text);
        p.setFakeBoldText(true);
        p.setTextSize(dp(session.intValue("tokens.typography.bodySizeSp", 16)));
        canvas.drawText("Card title", side + dp(session.intValue("tokens.spacing.md", 16)), y + dp(28), p);
        p.setFakeBoldText(false);
        p.setColor(x.text2);
        p.setTextSize(dp(13));
        canvas.drawText("Cards group related things", side + dp(session.intValue("tokens.spacing.md", 16)), y + dp(50), p);
        y += dp(82);
        // buttons
        int bh = dp(session.intValue("components.button.heightDp", 44));
        float brad = rad("tokens.shapes.buttonRadiusDp", 22);
        int bfill = session.colorChain(x.accent, "components.button.background"), bfg = session.colorChain(x.onAccent, "components.button.foreground");
        String style = session.text("components.button.style", "filled");
        rect.set(side, y, x.w / 2f - dp(6), y + bh);
        if ("filled".equals(style)) {
            p.setColor(bfill);
            canvas.drawRoundRect(rect, Math.min(brad, bh / 2f), Math.min(brad, bh / 2f), p);
            p.setColor(bfg);
        } else {
            if ("outlined".equals(style)) {
                outlineFill(canvas, rect, Math.min(brad, bh / 2f), bfill, 2);
            }
            p.setColor(bfill);
        }
        p.setTextSize(dp(15));
        p.setFakeBoldText(true);
        canvas.drawText("Button", rect.centerX() - p.measureText("Button") / 2f, rect.centerY() + dp(5), p);
        p.setFakeBoldText(false);
        add("button", rect);
        // switch
        float sx = x.w - side - dp(52), sy = y + bh / 2f;
        boolean on = true;
        rect.set(sx, sy - dp(14), sx + dp(52), sy + dp(14));
        p.setColor(session.colorChain(x.accent, on ? "components.switch.trackOn" : "components.switch.trackOff"));
        canvas.drawRoundRect(rect, dp(14), dp(14), p);
        p.setColor(session.colorChain(0xFFFFFFFF, "components.switch.thumbOn"));
        canvas.drawCircle(sx + dp(38), sy, dp(11), p);
        add("switch", rect);
        y += bh + dp(14);
        // input
        float ir = rad("tokens.shapes.inputRadiusDp", 12);
        rect.set(side, y, x.w - side, y + dp(48));
        p.setColor(session.colorChain(x.surface, "components.input.background"));
        canvas.drawRoundRect(rect, ir, ir, p);
        outlineFill(canvas, rect, ir, x.divider, 1);
        add("input", rect);
        p.setColor(session.colorChain(x.text2, "components.input.hint"));
        p.setTextSize(dp(15));
        canvas.drawText("Type something", side + dp(14), y + dp(30), p);
        y += dp(60);
        // search
        float sr = rad("components.search.radiusDp", 20);
        rect.set(side, y, x.w - side, y + dp(40));
        p.setColor(session.colorChain(x.surface, "components.search.background"));
        canvas.drawRoundRect(rect, sr, sr, p);
        add("search", rect);
        p.setColor(session.colorChain(x.icon, "components.search.iconColor"));
        canvas.drawCircle(side + dp(20), y + dp(20), dp(7), p);
        p.setColor(session.colorChain(x.text2, "components.search.hintColor"));
        p.setTextSize(dp(14));
        canvas.drawText("Search", side + dp(38), y + dp(25), p);
        y += dp(52);
        // device row
        float ar = rad("components.session.avatarRadiusDp", 20);
        rect.set(side, y, side + dp(40), y + dp(40));
        p.setColor(0xFF65AADD);
        canvas.drawRoundRect(rect, ar, ar, p);
        p.setColor(x.text);
        p.setTextSize(dp(session.intValue("components.session.nameSizeSp", 15)));
        canvas.drawText("Pixel 8 · Android", side + dp(52), y + dp(18), p);
        rect.set(side, y, x.w - side, y + dp(40));
        add("session", rect);
        y += dp(52);
        // dialog
        float dr = rad("tokens.shapes.dialogRadiusDp", 16);
        if (y + dp(80) < x.h) {
            rect.set(0, y, x.w, x.h);
            p.setColor(session.color("components.dialog.scrim", 0x66000000));
            canvas.drawRect(rect, p);
            RectF dlg = new RectF(x.w * 0.12f, y + dp(8), x.w * 0.88f, x.h - dp(8));
            p.setColor(session.colorChain(x.surface, "components.dialog.background"));
            shadow(canvas, dlg, dr, session.intValue("tokens.elevation.dialogDp", 0));
            p.setColor(session.colorChain(x.surface, "components.dialog.background"));
            canvas.drawRoundRect(dlg, dr, dr, p);
            add("dialog", dlg);
            p.setColor(x.text);
            p.setTextSize(dp(15));
            canvas.drawText("Dialog", dlg.left + dp(16), dlg.top + dp(26), p);
        }
    }

    private void outlineFill(Canvas canvas, RectF r, float radius, int color, int widthDp) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(widthDp));
        p.setColor(color);
        canvas.drawRoundRect(r, radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            String best = "screenBackground";
            float bestArea = Float.MAX_VALUE;
            for (Hit h : hits) {
                if (h.r.contains(e.getX(), e.getY())) {
                    float area = h.r.width() * h.r.height();
                    if (area <= bestArea) { // later (more specific) regions win ties
                        bestArea = area;
                        best = h.id;
                    }
                }
            }
            if (listener != null) {
                listener.onComponentTapped(best);
            }
            performClick();
        }
        return e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_UP;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
