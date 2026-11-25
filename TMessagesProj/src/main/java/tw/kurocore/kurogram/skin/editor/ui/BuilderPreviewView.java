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
import java.util.Arrays;
import java.util.List;

import tw.kurocore.kurogram.skin.editor.EditorSession;
import tw.kurocore.kurogram.skin.editor.PreviewScreens;

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
    private boolean editMode = true; // Edit: tapping selects and the selection is outlined. Preview: neither.
    private int deviceWidthDp = 360;
    private static final float CONTENT_FACTOR = 1.3f; // list screens are laid out this many screens tall so they can be scrolled
    private String[] highlight;
    private float scrollY;
    private float lastY, downY;
    private boolean moved;
    private Runnable navigated;
    private boolean drawingThumb;
    private boolean skipPins; // while a scrolling list is drawn, the parts that stay put are left out and drawn once, on top

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

    /** Called after a tap in Preview mode moved to another screen, so the owner can refresh its own labels. */
    public void setNavigatedListener(Runnable r) {
        navigated = r;
    }

    /** Preview mode taps: the bottom tabs switch screen and a chat row opens the conversation, like the real app. */
    private void previewTap(float tx, float ty) {
        float vh = getHeight();
        float cy = ty + scrollY;
        if (inPin(tx, ty)) {
            cy = ty + (vh * CONTENT_FACTOR - vh);
        } else if (ty < dp(session.intValue("components.appBar.heightDp", 52))) {
            cy = ty;
        }
        int tabIndex = -1, idx = 0;
        String hitId = null;
        float bestArea = Float.MAX_VALUE;
        for (Hit h : hits) {
            if ("bottomTabs.item".equals(h.id)) {
                if (h.r.contains(tx, cy)) {
                    tabIndex = idx;
                }
                idx++;
            }
            if (h.r.contains(tx, cy) && h.r.width() * h.r.height() <= bestArea) {
                bestArea = h.r.width() * h.r.height();
                hitId = h.id;
            }
        }
        String to = null;
        if (tabIndex >= 0) {
            List<String> tabs = listOr("layout.bottomTabs.items", "chats", "contacts", "calls", "settings");
            String t = tabs.get(tabIndex);
            to = "chats".equals(t) ? "chats" : "contacts".equals(t) ? "contacts" : "settings".equals(t) ? "settings"
                    : "calls".equals(t) || "people".equals(t) ? "people" : "profile".equals(t) ? "profile" : null;
        } else if (hitId != null && hitId.startsWith("chatRow")) {
            to = "chat";
        }
        if (to != null && !to.equals(session.scene())) {
            session.setScene(to);
            scrollY = 0;
            invalidate();
            if (navigated != null) {
                navigated.run();
            }
        }
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setEditMode(boolean edit) {
        editMode = edit;
        scrollY = 0;
        invalidate();
    }

    /** Lights up every drawn part with one of these ids (null clears it): shows what a colour will change. */
    public void setHighlight(String[] ids) {
        highlight = ids;
        invalidate();
    }

    public boolean isEditMode() {
        return editMode;
    }

    /** The phone width the screen is laid out for (dp values stay dp); never wider than the room there is. */
    public void setDeviceWidthDp(int dp) {
        deviceWidthDp = dp;
        requestLayout();
    }

    public int deviceWidthDp() {
        return deviceWidthDp;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = Math.min(MeasureSpec.getSize(w), AndroidUtilities.dp(deviceWidthDp));
        int height = Math.max(AndroidUtilities.dp(340), Math.min(AndroidUtilities.dp(720), (int) (width * 2.05f)));
        setMeasuredDimension(width, height);
    }

    private String lastSelId = "";
    private int selOcc;

    /** The drawn copy of the selected part that was tapped (a list has many chat rows, only the tapped one is framed). */
    private Hit pickSelected(String sel) {
        if (!sel.equals(lastSelId)) {
            lastSelId = sel;
            selOcc = 0;
        }
        int n = 0;
        Hit last = null;
        for (Hit h : hits) {
            if (h.id.equals(sel)) {
                if (n == selOcc) {
                    return h;
                }
                n++;
                last = h;
            }
        }
        return last;
    }

    /** Where the selected part is drawn (view pixels), or null when it is not on this screen. */
    public android.graphics.RectF selectedBounds() {
        Hit h = pickSelected(session.selected());
        return h == null ? null : new android.graphics.RectF(h.r);
    }

    /** Ids of the components drawn last, in drawing order without repeats: the layers of the current screen. */
    public List<String> visibleComponents() {
        List<String> out = new ArrayList<>();
        for (Hit h : hits) {
            if (!out.contains(h.id)) {
                out.add(h.id);
            }
        }
        return out;
    }

    /** A small picture of another screen drawn from the same draft; the screen being edited is put back afterwards. */
    public android.graphics.Bitmap thumbnail(String sceneId, int outWidthPx) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) {
            return null;
        }
        String prevScene = session.scene(), prevSel = session.selected();
        boolean prevEdit = editMode;
        editMode = false;
        try {
            session.setScene(sceneId);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(outWidthPx, Math.max(1, outWidthPx * h / w), android.graphics.Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            float sc = (float) outWidthPx / w;
            c.scale(sc, sc);
            drawingThumb = true;
            onDraw(c);
            return bmp;
        } finally {
            drawingThumb = false;
            session.setScene(prevScene);
            session.select(prevSel);
            editMode = prevEdit;
            invalidate();
        }
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
        int vw = getWidth(), vh = getHeight();
        if (!scrollable()) {
            scrollY = 0;
            drawScene(canvas, vh);
            return;
        }
        // Preview mode on a list screen: the screen is drawn taller than the view and slides under the bars,
        // which stay pinned (the top bar always, the tab bar on Chats).
        int ch = (int) (vh * CONTENT_FACTOR);
        scrollY = Math.max(0, Math.min(scrollY, ch - vh));
        canvas.save();
        canvas.clipRect(0, 0, vw, vh);
        canvas.translate(0, -scrollY);
        skipPins = true;
        drawScene(canvas, ch);
        canvas.restore();
        List<Hit> mainHits = new ArrayList<>(hits);
        if (scrollY > 0) {
            canvas.save();
            canvas.clipRect(0, 0, vw, dp(session.intValue("components.appBar.heightDp", 52)));
            drawScene(canvas, ch);
            canvas.restore();
        }
        // the parts that never scroll in the real app (floating bar, buttons) are always drawn at the bottom of the view
        List<Pin> pins = pins(vw, ch);
        skipPins = false;
        List<Hit> pinHits = new ArrayList<>();
        if (!pins.isEmpty()) {
            canvas.save();
            canvas.translate(0, -(ch - vh));
            android.graphics.Path clip = new android.graphics.Path();
            for (Pin pin : pins) {
                clip.addRoundRect(pin.r, pin.rad, pin.rad, android.graphics.Path.Direction.CW);
            }
            canvas.clipPath(clip);
            drawScene(canvas, ch);
            canvas.restore();
            for (Hit h : hits) {
                for (Pin pin : pins) {
                    if (pin.r.contains(h.r.centerX(), h.r.centerY())) {
                        pinHits.add(h);
                        break;
                    }
                }
            }
        }
        hits.clear();
        hits.addAll(mainHits);
        hits.addAll(pinHits);
    }

    private static final class Pin {
        final RectF r;
        final float rad;

        Pin(RectF r, float rad) {
            this.r = r;
            this.rad = rad;
        }
    }

    private boolean hasTabs() {
        String s = session.scene();
        return "chats".equals(s) || "contacts".equals(s) || "settings".equals(s) || "profile".equals(s);
    }

    /** The floating bottom bar, like the real app: a rounded pill with a margin all round. */
    private RectF tabBar(float w, float h) {
        return new RectF(dp(34), h - dp(72), w - dp(34), h - dp(12));
    }

    /** Parts that stay on screen while a list scrolls, in the coordinates of a screen laid out h tall. */
    private List<Pin> pins(float w, float h) {
        List<Pin> out = new ArrayList<>();
        if (session.scene().startsWith("themeStore")) {
            out.add(new Pin(tabBar(w, h), dp(31)));
            return out;
        }
        if (!hasTabs()) {
            return out;
        }
        out.add(new Pin(tabBar(w, h), dp(31)));
        float fs = dp(session.intValue("components.fab.sizeDp", 52));
        float fx = w - dp(16) - fs, fy = h - dp(72) - dp(16) - fs;
        String s = session.scene();
        if ("chats".equals(s) || "contacts".equals(s)) {
            out.add(new Pin(new RectF(fx - dp(1), fy - dp(1), fx + fs + dp(1), fy + fs + dp(2)), (fs + dp(3)) / 2f));
        }
        if ("chats".equals(s)) {
            float cs = dp(40), cx = fx + (fs - cs) / 2f, cy = fy - dp(12) - cs;
            out.add(new Pin(new RectF(cx - dp(1), cy, cx + cs + dp(1), cy + cs + dp(2)), (cs + dp(2)) / 2f));
        }
        if ("profile".equals(s)) {
            out.add(new Pin(new RectF(w / 2f - dp(80), h - dp(72) - dp(16) - dp(44), w / 2f + dp(80), h - dp(72) - dp(16)), dp(22)));
        }
        return out;
    }

    private boolean inPin(float tx, float ty) {
        float vh = getHeight(), ch = vh * CONTENT_FACTOR;
        for (Pin pin : pins(getWidth(), ch)) {
            if (pin.r.contains(tx, ty + (ch - vh))) {
                return true;
            }
        }
        return false;
    }

    private boolean scrollable() {
        if (drawingThumb || session == null) {
            return false;
        }
        switch (session.scene()) {
            case "chat":
            case "drawer":
            case "controls":
                return false;
            default:
                return true;
        }
    }

    private void drawScene(Canvas canvas, int h) {
        hits.clear();
        int w = getWidth();
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
            Object dir = session.raw("tokens.gradient.direction");
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
            case "drawer":
                drawChats(canvas, x);
                hits.clear(); // the scrim covers the list: only the menu and the page behind it can be tapped
                add("screenBackground", new RectF(0, 0, x.w, x.h));
                drawDrawer(canvas, x);
                break;
            case "contacts":
                drawContacts(canvas, x);
                break;
            case "people":
                drawPeople(canvas, x);
                break;
            case "profile":
                drawProfile(canvas, x);
                break;
            case "themeStore":
                drawThemeStore(canvas, x);
                break;
            case "themeStore.stickers":
                drawStoreStickers(canvas, x);
                break;
            case "themeStore.profile":
                drawStoreProfile(canvas, x);
                break;
            default:
                if (PreviewScreens.has(session.scene())) {
                    drawNative(canvas, x, session.scene());
                } else {
                    drawChats(canvas, x);
                }
        }

        if (editMode && highlight != null) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x262F80ED);
            outline.setColor(0xFF2F80ED);
            for (Hit hit : hits) {
                for (String id : highlight) {
                    if (id.equals(hit.id) && !"screenBackground".equals(id)) {
                        canvas.drawRoundRect(hit.r, dp(4), dp(4), p);
                        canvas.drawRoundRect(hit.r, dp(4), dp(4), outline);
                    }
                }
            }
            if (java.util.Arrays.asList(highlight).contains("screenBackground")) {
                rect.set(0, 0, w, h);
                canvas.drawRect(rect, p);
            }
        }

        // selection: a thin blue frame just outside the part, with four corner handles; none in Preview mode
        String sel = session.selected();
        if (editMode && !"global".equals(sel)) {
            Hit chosen = pickSelected(sel);
            for (int i = 0; i < (chosen == null ? 0 : 1); i++) {
                Hit hit = chosen;
                if (hit.id.equals(sel)) {
                    boolean whole = "screenBackground".equals(sel);
                    rect.set(hit.r);
                    rect.inset(whole ? dp(1.5f) : -dp(2), whole ? dp(1.5f) : -dp(2));
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0x142F80ED);
                    canvas.drawRoundRect(rect, dp(4), dp(4), p);
                    outline.setPathEffect(null);
                    outline.setColor(0xFF2F80ED);
                    canvas.drawRoundRect(rect, dp(4), dp(4), outline);
                    if (!whole) {
                        float hr = dp(3.5f);
                        float[][] corners = {{rect.left, rect.top}, {rect.right, rect.top}, {rect.left, rect.bottom}, {rect.right, rect.bottom}};
                        for (float[] cpt : corners) {
                            p.setColor(0xFFFFFFFF);
                            canvas.drawCircle(cpt[0], cpt[1], hr, p);
                            outline.setStrokeWidth(dp(1.5f));
                            canvas.drawCircle(cpt[0], cpt[1], hr, outline);
                        }
                        outline.setStrokeWidth(dp(2));
                    }
                    // name tag, like a design tool: says which part this is
                    String tag = session.catalog.componentLabel(sel, session.lang());
                    p.setTextSize(dp(10));
                    p.setFakeBoldText(true);
                    float tw = p.measureText(tag) + dp(10), th = dp(16);
                    float tx = Math.max(0, Math.min(rect.left, w - tw)), ty = rect.top - th - dp(1) >= 0 ? rect.top - th - dp(1) : rect.bottom + dp(1);
                    p.setColor(0xFF2F80ED);
                    canvas.drawRoundRect(tx, ty, tx + tw, ty + th, dp(4), dp(4), p);
                    p.setColor(0xFFFFFFFF);
                    canvas.drawText(tag, tx + dp(5), ty + th - dp(4.5f), p);
                    p.setFakeBoldText(false);
                    break;
                }
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
        icon(canvas, "more", x.w - dp(26), bh / 2f, dp(9), fg);
        icon(canvas, "search", x.w - dp(60), bh / 2f, dp(9), fg);
        return bh;
    }

    private String item(String id) {
        return session.catalog.itemLabel(id, session.lang());
    }

    private List<String> listOr(String path, String... def) {
        if (session.raw(path) instanceof List) {
            return session.listOrDefault(path);
        }
        return Arrays.asList(def);
    }

    private final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Draws a line icon like the app's own (size s is the half width): chats, contacts, calls, settings, profile, search, more. */
    private void icon(Canvas c, String id, float cx, float cy, float s, int color) {
        ip.setStyle(Paint.Style.STROKE);
        ip.setStrokeWidth(Math.max(dp(1.6f), s / 6.5f));
        ip.setStrokeCap(Paint.Cap.ROUND);
        ip.setStrokeJoin(Paint.Join.ROUND);
        ip.setColor(color);
        android.graphics.Path path = new android.graphics.Path();
        switch (id) {
            case "chats":
                path.addOval(new RectF(cx - s, cy - s * 0.85f, cx + s, cy + s * 0.75f), android.graphics.Path.Direction.CW);
                path.moveTo(cx - s * 0.55f, cy + s * 0.55f);
                path.lineTo(cx - s * 0.9f, cy + s * 1.0f);
                path.lineTo(cx - s * 0.1f, cy + s * 0.72f);
                c.drawPath(path, ip);
                c.drawLine(cx - s * 0.45f, cy - s * 0.2f, cx + s * 0.45f, cy - s * 0.2f, ip);
                c.drawLine(cx - s * 0.45f, cy + s * 0.2f, cx + s * 0.15f, cy + s * 0.2f, ip);
                break;
            case "contacts":
            case "people":
                c.drawCircle(cx, cy, s, ip);
                c.drawCircle(cx, cy - s * 0.22f, s * 0.32f, ip);
                path.addArc(new RectF(cx - s * 0.62f, cy + s * 0.05f, cx + s * 0.62f, cy + s * 1.2f), 200, 140);
                c.drawPath(path, ip);
                break;
            case "profile":
                ip.setStyle(Paint.Style.FILL);
                ip.setAlpha(60);
                c.drawCircle(cx, cy, s, ip);
                ip.setColor(color);
                ip.setStyle(Paint.Style.STROKE);
                c.drawCircle(cx, cy, s, ip);
                c.drawCircle(cx, cy - s * 0.25f, s * 0.3f, ip);
                path.addArc(new RectF(cx - s * 0.6f, cy + s * 0.05f, cx + s * 0.6f, cy + s * 1.15f), 210, 120);
                c.drawPath(path, ip);
                break;
            case "calls":
                path.addArc(new RectF(cx - s, cy - s, cx + s, cy + s), 120, 200);
                path.moveTo(cx - s * 0.5f, cy + s * 0.87f);
                path.lineTo(cx - s * 0.95f, cy + s * 0.35f);
                c.drawPath(path, ip);
                c.drawLine(cx + s * 0.1f, cy - s * 0.1f, cx + s * 0.6f, cy - s * 0.1f, ip);
                break;
            case "settings":
                c.drawCircle(cx, cy, s * 0.36f, ip);
                c.drawCircle(cx, cy, s * 0.8f, ip);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine(cx + (float) Math.cos(a) * s * 0.8f, cy + (float) Math.sin(a) * s * 0.8f,
                            cx + (float) Math.cos(a) * s * 1.05f, cy + (float) Math.sin(a) * s * 1.05f, ip);
                }
                break;
            case "search":
                c.drawCircle(cx - s * 0.15f, cy - s * 0.15f, s * 0.65f, ip);
                c.drawLine(cx + s * 0.35f, cy + s * 0.35f, cx + s * 0.9f, cy + s * 0.9f, ip);
                break;
            case "back":
                c.drawLine(cx - s * 0.8f, cy, cx + s * 0.8f, cy, ip);
                c.drawLine(cx - s * 0.8f, cy, cx - s * 0.1f, cy - s * 0.7f, ip);
                c.drawLine(cx - s * 0.8f, cy, cx - s * 0.1f, cy + s * 0.7f, ip);
                break;
            case "gift":
                c.drawRoundRect(new RectF(cx - s * 0.85f, cy - s * 0.2f, cx + s * 0.85f, cy + s * 0.9f), s * 0.15f, s * 0.15f, ip);
                c.drawRect(cx - s * 0.95f, cy - s * 0.65f, cx + s * 0.95f, cy - s * 0.2f, ip);
                c.drawLine(cx, cy - s * 0.65f, cx, cy + s * 0.9f, ip);
                break;
            case "camera":
                c.drawRoundRect(new RectF(cx - s, cy - s * 0.7f, cx + s, cy + s * 0.8f), s * 0.3f, s * 0.3f, ip);
                c.drawCircle(cx, cy + s * 0.05f, s * 0.38f, ip);
                break;
            case "edit":
                c.drawLine(cx - s * 0.7f, cy + s * 0.7f, cx + s * 0.6f, cy - s * 0.6f, ip);
                c.drawLine(cx - s * 0.7f, cy + s * 0.7f, cx - s * 0.75f, cy + s * 0.95f, ip);
                c.drawLine(cx + s * 0.35f, cy - s * 0.85f, cx + s * 0.85f, cy - s * 0.35f, ip);
                break;
            case "adduser":
                c.drawCircle(cx - s * 0.2f, cy - s * 0.3f, s * 0.4f, ip);
                path.addArc(new RectF(cx - s * 0.85f, cy + s * 0.1f, cx + s * 0.45f, cy + s * 1.2f), 200, 140);
                c.drawPath(path, ip);
                c.drawLine(cx + s * 0.55f, cy - s * 0.1f, cx + s * 1.0f, cy - s * 0.1f, ip);
                c.drawLine(cx + s * 0.78f, cy - s * 0.33f, cx + s * 0.78f, cy + s * 0.13f, ip);
                break;
            case "pin":
                c.save();
                c.rotate(40, cx, cy);
                path.addRoundRect(new RectF(cx - s * 0.38f, cy - s * 0.9f, cx + s * 0.38f, cy + s * 0.15f), s * 0.15f, s * 0.15f, android.graphics.Path.Direction.CW);
                c.drawPath(path, ip);
                c.drawLine(cx - s * 0.75f, cy + s * 0.15f, cx + s * 0.75f, cy + s * 0.15f, ip);
                c.drawLine(cx, cy + s * 0.15f, cx, cy + s * 0.95f, ip);
                c.restore();
                break;
            case "mute":
                path.moveTo(cx - s * 0.9f, cy - s * 0.35f);
                path.lineTo(cx - s * 0.4f, cy - s * 0.35f);
                path.lineTo(cx + s * 0.3f, cy - s * 0.95f);
                path.lineTo(cx + s * 0.3f, cy + s * 0.95f);
                path.lineTo(cx - s * 0.4f, cy + s * 0.35f);
                path.lineTo(cx - s * 0.9f, cy + s * 0.35f);
                path.close();
                c.drawPath(path, ip);
                c.drawLine(cx - s * 0.9f, cy + s * 1.0f, cx + s * 0.9f, cy - s * 1.0f, ip);
                break;
            case "trash":
                c.drawLine(cx - s * 0.9f, cy - s * 0.55f, cx + s * 0.9f, cy - s * 0.55f, ip);
                c.drawLine(cx - s * 0.35f, cy - s * 0.55f, cx - s * 0.35f, cy - s * 0.85f, ip);
                c.drawLine(cx + s * 0.35f, cy - s * 0.55f, cx + s * 0.35f, cy - s * 0.85f, ip);
                c.drawLine(cx - s * 0.35f, cy - s * 0.85f, cx + s * 0.35f, cy - s * 0.85f, ip);
                path.moveTo(cx - s * 0.65f, cy - s * 0.55f);
                path.lineTo(cx - s * 0.55f, cy + s * 0.9f);
                path.lineTo(cx + s * 0.55f, cy + s * 0.9f);
                path.lineTo(cx + s * 0.65f, cy - s * 0.55f);
                c.drawPath(path, ip);
                break;
            case "more":
                ip.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i++) {
                    c.drawCircle(cx, cy + i * s * 0.6f, s * 0.13f + dp(0.8f), ip);
                }
                break;
            default:
                ip.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, s * 0.8f, ip);
        }
    }

    private void drawTabs(Canvas canvas, Ctx x, String active) {
        if (skipPins) {
            return;
        }
        RectF bar = tabBar(x.w, x.h);
        boolean glass = "glass".equals(session.text("layout.bottomTabs.style", "flat"));
        int fill = session.colorChain(x.surface, "components.navigationBar.background", "components.bottomNavigation.background");
        if (glass) {
            fill = (fill & 0x00FFFFFF) | 0xCC000000;
        }
        int sel = session.colorChain(x.accent, "components.navigationBar.selected");
        int unsel = session.colorChain(x.icon, "components.navigationBar.unselected");
        int pill = session.colorChain(0, "components.bottomNavigation.selectedBackground");
        if (pill == 0) {
            pill = (sel & 0x00FFFFFF) | 0x26000000;
        }
        boolean labels = session.bool("layout.bottomTabs.labels", true);
        float ic = dp(session.intValue("components.bottomNavigation.iconSizeDp", 24)) / 2.6f;
        RectF shadow = new RectF(bar);
        shadow.inset(-dp(1), -dp(1));
        shadow.offset(0, dp(1.5f));
        p.setColor(0x26000000);
        canvas.drawRoundRect(shadow, dp(31), dp(31), p);
        p.setColor(fill);
        canvas.drawRoundRect(bar, dp(30), dp(30), p);
        if (session.bool("layout.bottomTabs.border", false)) {
            outlineFill(canvas, bar, dp(30), x.divider, 1);
        }
        add("bottomTabs", bar);
        List<String> tabs = listOr("layout.bottomTabs.items", "chats", "contacts", "settings", "profile");
        int n = tabs.size();
        float cw = bar.width() / n;
        for (int i = 0; i < n; i++) {
            boolean on = tabs.get(i).equals(active);
            float cx = bar.left + cw * (i + 0.5f);
            rect.set(bar.left + cw * i, bar.top, bar.left + cw * (i + 1), bar.bottom);
            if (on) {
                RectF b = new RectF(cx - cw / 2f + dp(4), bar.top + dp(5), cx + cw / 2f - dp(4), bar.bottom - dp(5));
                p.setColor(pill);
                canvas.drawRoundRect(b, dp(24), dp(24), p);
            }
            float icy = bar.top + (labels ? dp(21) : bar.height() / 2f);
            icon(canvas, tabs.get(i), cx, icy, ic * 1.1f, on ? sel : unsel);
            if (labels) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(on ? sel : unsel);
                p.setFakeBoldText(on);
                p.setTextSize(dp(session.intValue("layout.bottomTabs.labelSizeSp", 10)));
                String l = item(tabs.get(i));
                canvas.drawText(l, cx - p.measureText(l) / 2f, bar.top + dp(45), p);
                p.setFakeBoldText(false);
            }
            add("bottomTabs.item", rect);
        }
    }

    private void fab(Canvas canvas, Ctx x, boolean camera) {
        if (skipPins) {
            return;
        }
        float fs = dp(session.intValue("components.fab.sizeDp", 52));
        boolean fabLeft = "left".equals(session.text("layout.chatList.fab.position", "right"));
        if (camera && !session.bool("layout.chatList.fab.camera", true)) {
            camera = false;
        }
        float fx = fabLeft ? dp(16) : x.w - dp(16) - fs, fy = x.h - dp(72) - dp(16) - fs;
        if (camera) {
            float cs = dp(40), cx = fx + (fs - cs) / 2f, cy = fy - dp(12) - cs;
            rect.set(cx, cy, cx + cs, cy + cs);
            p.setColor(0x22000000);
            canvas.drawOval(new RectF(cx - dp(1), cy, cx + cs + dp(1), cy + cs + dp(2)), p);
            p.setColor(x.surface);
            canvas.drawOval(rect, p);
            icon(canvas, "camera", cx + cs / 2f, cy + cs / 2f, dp(9), x.accent);
            add("fab", rect);
        }
        rect.set(fx, fy, fx + fs, fy + fs);
        p.setColor(x.accent);
        round(canvas, rect, dp(session.intValue("components.fab.radiusDp", 26)));
        icon(canvas, camera ? "edit" : "adduser", fx + fs / 2, fy + fs / 2, dp(10), x.onAccent);
        rect.set(fx, fy, fx + fs, fy + fs);
        add("fab", rect);
    }

    private void headerButtons(Canvas canvas, Ctx x, float barH, List<String> left, List<String> right) {
        p.setColor(x.icon);
        for (int i = 0; i < left.size(); i++) {
            icon(canvas, left.get(i), dp(22) + i * dp(34), barH / 2f, dp(9), x.icon);
        }
        for (int i = 0; i < right.size(); i++) {
            icon(canvas, right.get(i), x.w - dp(22) - i * dp(34), barH / 2f, dp(9), x.icon);
        }
    }

    private void drawChats(Canvas canvas, Ctx x) {
        int sbg = session.color("screens.chats.background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int top = bar(canvas, x, "Kurogram");
        if (session.draft.get("layout.chatList.header.left") instanceof List || session.draft.get("layout.chatList.header.right") instanceof List) {
            rect.set(0, 0, x.w, top);
            p.setColor(session.colorChain(x.surface, "components.appBar.background"));
            canvas.drawRect(rect, p);
            p.setColor(x.text);
            p.setTextSize(dp(18));
            p.setFakeBoldText(true);
            canvas.drawText("Kurogram", x.w / 2f - p.measureText("Kurogram") / 2f, top / 2f + dp(6), p);
            p.setFakeBoldText(false);
            headerButtons(canvas, x, top, listOr("layout.chatList.header.left"), listOr("layout.chatList.header.right"));
            add("chatList.header", rect);
        }
        // same order as the real Chats screen: stories, search, folder bar
        if (session.bool("layout.chatList.storyRail.enabled", true)) {
            int sr = dp(session.intValue("layout.chatList.storyRail.avatarSizeDp", 52));
            rect.set(0, top, x.w, top + sr + dp(14));
            p.setColor(x.surface);
            canvas.drawRect(rect, p);
            add("chatList.storyRail", rect);
            float sx = dp(12) + sr / 2f;
            if (session.bool("layout.chatList.storyRail.showCreate", true)) {
                p.setColor((x.accent & 0x00FFFFFF) | 0x33000000);
                canvas.drawCircle(sx, top + dp(7) + sr / 2f, sr / 2f, p);
                p.setColor(x.accent);
                p.setTextSize(dp(22));
                canvas.drawText("+", sx - dp(6), top + dp(15) + sr / 2f, p);
                sx += sr + dp(10);
            }
            for (int i = 0; i < 4 && sx + sr / 2f < x.w; i++) {
                avatar(canvas, sx, top + dp(7) + sr / 2f, sr / 2f, AV[i]);
                outlineCircle(canvas, sx, top + dp(7) + sr / 2f, sr / 2f + dp(2), x.accent);
                sx += sr + dp(10);
            }
            top += sr + dp(14);
        }
        {
            int sh = dp(40);
            RectF sb = new RectF(dp(12), top + dp(4), x.w - dp(12), top + sh - dp(4));
            p.setColor((x.divider & 0x00FFFFFF) | 0x40000000);
            canvas.drawRoundRect(sb, dp(18), dp(18), p);
            add("chatList.search", sb);
            p.setColor(x.text2);
            p.setTextSize(dp(14));
            canvas.drawText("Search Chats", dp(44), top + sh / 2f + dp(5), p);
            canvas.drawCircle(dp(28), top + sh / 2f - dp(1), dp(5), p);
            top += sh;
        }
        String folders = session.text("layout.chatList.folders.style", "chips");
        if (!"none".equals(folders)) {
            int fh = dp(36);
            rect.set(0, top, x.w, top + fh);
            p.setColor(x.surface);
            canvas.drawRect(rect, p);
            add("chatList.folders", rect);
            String[] f = {"All", "Work", "Family"};
            float fx = dp(12);
            for (int i = 0; i < f.length; i++) {
                p.setTextSize(dp(13));
                float fw = p.measureText(f[i]) + dp(20);
                if ("chips".equals(folders)) {
                    p.setColor(i == 0 ? x.accent : (x.divider & 0x00FFFFFF) | 0x33000000);
                    RectF c = new RectF(fx, top + dp(5), fx + fw, top + fh - dp(5));
                    canvas.drawRoundRect(c, dp(session.intValue("screens.chats.tabsRadiusDp", 13)), dp(session.intValue("screens.chats.tabsRadiusDp", 13)), p);
                }
                p.setColor(i == 0 ? ("chips".equals(folders) ? x.onAccent : x.accent) : x.text2);
                canvas.drawText(f[i], fx + dp(10), top + dp(23), p);
                fx += fw + dp(6);
            }
            top += fh;
        }
        boolean grid = "grid".equals(session.text("layout.chatList.mode", "list"));
        int tabsH = 0; // the bottom bar floats over the list
        int body = session.intValue("tokens.typography.bodySizeSp", 16);
        int cap = session.intValue("tokens.typography.captionSizeSp", 12);
        boolean titleBold = bold("layout.chatList.row.title.weight", bold("tokens.typography.fontWeight", true));
        String[] names = {"Aye Aye", "Design team", "Ko Min", "Saved Messages", "Daw Mya", "Mg Mg", "Su Su", "Project chat", "Thura", "Zaw Zaw", "Hla Hla", "Family", "Nandar", "News channel"};
        String[] msgs = {"See you tomorrow!", "New theme is ready", "ok 👍", "Notes", "Thank you", "Call me", "On my way", "Meeting at 3", "Sent a photo", "Got it", "Good night", "Dinner at 7?", "Thanks!", "New post"};
        String[] times = {"21:40", "20:15", "Fri", "Thu", "Mon", "Sun", "Sat", "Fri", "Thu", "Wed", "Tue", "Mon", "Sun", "Sat"};
        int[] avatars = {0xFFE17076, 0xFF7BC862, 0xFF65AADD, 0xFFEE7AAE, 0xFFF3A93C, 0xFF6EC9CB, 0xFFB694F9, 0xFFE17076, 0xFF7BC862, 0xFF65AADD, 0xFFEE7AAE, 0xFFF3A93C, 0xFF6EC9CB, 0xFFB694F9};
        if (grid) {
            int cols = session.intValue("layout.chatList.grid.columns", 3);
            float cw = x.w / (float) cols, ad = dp(session.intValue("layout.chatList.grid.avatarDp", 56));
            for (int i = 0; i < names.length; i++) {
                int cx = i % cols, cy = i / cols;
                float px = cx * cw + cw / 2f, py = top + dp(8) + cy * (ad + dp(34)) + ad / 2f;
                if (py + ad / 2f > x.h - tabsH) {
                    break;
                }
                rect.set(px - cw / 2f, py - ad / 2f - dp(4), px + cw / 2f, py + ad / 2f + dp(30));
                add("chatList.grid", rect);
                p.setColor(avatars[i]);
                rect.set(px - ad / 2f, py - ad / 2f, px + ad / 2f, py + ad / 2f);
                canvas.drawOval(rect, p);
                add("chatRow.avatar", rect);
                if (i < 2 && session.bool("layout.chatList.grid.showBadge", true)) {
                    p.setColor(i == 0 ? x.accent : x.error);
                    canvas.drawCircle(px + ad / 2f - dp(4), py - ad / 2f + dp(4), dp(8), p);
                }
                if (session.bool("layout.chatList.grid.showName", true)) {
                    p.setColor(x.text);
                    p.setTextSize(dp(12));
                    canvas.drawText(names[i], px - p.measureText(names[i]) / 2f, py + ad / 2f + dp(16), p);
                }
            }
        } else {
            int rowH = dp(session.intValue("layout.chatList.row.heightDp", session.intValue("components.cell.heightDp", 64)));
            boolean card = "card".equals(session.text("screens.chats.listStyle", "flat")) || "glass".equals(session.text("screens.chats.listStyle", "flat"));
            boolean showPreview = session.bool("layout.chatList.row.preview.show", true);
            boolean dividers = session.bool("layout.chatList.row.dividers", session.bool("screens.chats.showDividers", true));
            boolean rounded = "rounded".equals(session.text("layout.chatList.row.avatar.shape", "circle"));
            int avDp = session.intValue("layout.chatList.row.avatar.sizeDp", session.intValue("screens.chats.avatarSizeDp", 44));
            float side = card ? dp(10) : 0;
            int rows = Math.max(1, Math.min(names.length, (x.h - top - tabsH) / rowH));
            for (int i = 0; i < rows; i++) {
                float y = top + i * rowH;
                rect.set(side, y, x.w - side, y + rowH);
                int rf = x.surface;
                if ("glass".equals(session.text("screens.chats.listStyle", "flat"))) {
                    rf = (rf & 0x00FFFFFF) | 0xB0000000;
                }
                p.setColor(rf);
                if (card && (i == 0 || i == rows - 1)) {
                    canvas.drawRoundRect(rect, dp(14), dp(14), p);
                } else {
                    canvas.drawRect(rect, p);
                }
                if (i < 2 && session.bool("layout.chatList.row.unreadTint", false)) {
                    p.setColor((x.accent & 0x00FFFFFF) | 0x1A000000);
                    canvas.drawRect(rect, p);
                }
                add("chatRow", rect);
                float cy = y + rowH / 2f;
                float ar0 = Math.min(dp(avDp) / 2f, rowH / 2f - dp(4));
                if (i < 2 && session.bool("layout.chatList.row.avatar.unreadRing", false)) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(dp(2));
                    p.setColor(x.accent);
                    canvas.drawCircle(side + dp(12) + ar0, cy, ar0 + dp(2.5f), p);
                    p.setStyle(Paint.Style.FILL);
                }
                p.setColor(avatars[i]);
                float ar = Math.min(dp(avDp) / 2f, rowH / 2f - dp(4));
                rect.set(side + dp(12), cy - ar, side + dp(12) + 2 * ar, cy + ar);
                if (rounded) {
                    canvas.drawRoundRect(rect, ar * 0.4f, ar * 0.4f, p);
                } else {
                    canvas.drawOval(rect, p);
                }
                add("chatRow.avatar", rect);
                boolean unreadRow = i < 2;
                String uw = session.text("layout.chatList.row.title.unreadWeight", null);
                boolean rowBold = unreadRow && uw != null ? !"normal".equals(uw) : titleBold;
                p.setColor(x.text);
                p.setFakeBoldText(rowBold);
                p.setTextSize(dp(session.intValue("layout.chatList.row.title.sizeSp", body)));
                float tx = side + dp(24) + 2 * ar;
                boolean online = session.raw("layout.chatList.row.avatar.onlineDot.sizeDp") != null || session.raw("layout.chatList.row.avatar.onlineDot.color") != null
                        || "badge".equals(session.text("layout.chatList.row.avatar.lastActive", "none"));
                if (online && i % 2 == 0) {
                    float od = dp(session.intValue("layout.chatList.row.avatar.onlineDot.sizeDp", 10));
                    p.setColor("accent".equals(session.text("layout.chatList.row.avatar.onlineDot.color", "success")) ? x.accent : session.color("tokens.colors.success", 0xFF34C759));
                    canvas.drawCircle(side + dp(12) + 2 * ar - od / 2f, cy + ar - od / 2f, od / 2f, p);
                    p.setColor(x.text);
                }
                canvas.drawText(names[i], tx, showPreview ? cy - dp(4) : cy + dp(5), p);
                float nameEnd = tx + p.measureText(names[i]) + dp(8);
                float nameY = (showPreview ? cy - dp(4) : cy + dp(5)) - dp(5);
                if (i == 1) {
                    p.setColor(x.accent);
                    canvas.drawCircle(nameEnd + dp(6), nameY, dp(6), p);
                    p.setColor(x.onAccent);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(dp(1.4f));
                    canvas.drawLine(nameEnd + dp(3.2f), nameY, nameEnd + dp(5.2f), nameY + dp(2.2f), p);
                    canvas.drawLine(nameEnd + dp(5.2f), nameY + dp(2.2f), nameEnd + dp(9), nameY - dp(2.4f), p);
                    p.setStyle(Paint.Style.FILL);
                    p.setStrokeWidth(0);
                } else if ((i == 4 || i == 5) && session.bool("layout.chatList.row.badges.mute", true)) {
                    icon(canvas, "mute", nameEnd + dp(5), nameY, dp(5), x.text2);
                }
                p.setColor(x.text);
                rect.set(tx, cy - dp(body + 4), x.w * 0.62f, cy);
                add("chatRow.title", rect);
                p.setFakeBoldText(false);
                if (showPreview) {
                    String pc = unreadRow ? session.text("layout.chatList.row.preview.unreadColor", session.text("layout.chatList.row.preview.color", "textSecondary"))
                            : session.text("layout.chatList.row.preview.color", "textSecondary");
                    p.setColor("textPrimary".equals(pc) ? x.text : x.text2);
                    p.setTextSize(dp(session.intValue("layout.chatList.row.preview.sizeSp", Math.max(10, body - 2))));
                    canvas.drawText(msgs[i], tx, cy + dp(16), p);
                    int lines = session.bool("screens.chats.threeLines", false) ? 3 : session.intValue("layout.chatList.row.preview.lines", 1);
                    if (lines > 1 && rowH >= dp(72)) {
                        canvas.drawText("…", tx, cy + dp(32), p);
                    }
                    rect.set(tx, cy + dp(2), x.w * 0.62f, cy + dp(22));
                    add("chatRow.preview", rect);
                }
                String timePos = session.text("layout.chatList.row.meta.time", "top-right");
                p.setColor(x.text2);
                if (!"hidden".equals(timePos)) {
                    p.setTextSize(dp(cap));
                    float tw = p.measureText(times[i]);
                    if (i == 1 && session.bool("layout.chatList.row.meta.checks", true)) {
                        p.setColor(x.accent);
                        p.setStrokeWidth(dp(1.5f));
                        float cx0 = x.w - side - dp(12) - tw - dp(16);
                        float cy0 = cy - dp(10);
                        canvas.drawLine(cx0, cy0, cx0 + dp(3), cy0 + dp(3), p);
                        canvas.drawLine(cx0 + dp(3), cy0 + dp(3), cx0 + dp(8), cy0 - dp(3), p);
                        p.setStrokeWidth(0);
                        p.setColor(x.text2);
                    }
                    float ty2 = "bottom-right".equals(timePos) ? cy + dp(16) : cy - dp(6);
                    canvas.drawText(times[i], x.w - side - dp(12) - tw, ty2, p);
                    rect.set(x.w - side - dp(12) - tw, ty2 - dp(cap + 2), x.w - side - dp(12), ty2 + dp(2));
                    add("chatRow.time", rect);
                }
                if (i == 0 && "icon-left-of-time".equals(session.text("layout.chatList.row.pinned", "none"))) {
                    icon(canvas, "pin", x.w - side - dp(58), cy - dp(10), dp(5), x.text2);
                }
                if (i == 2 || i == 3) {
                    icon(canvas, "pin", x.w - side - dp(22), cy + dp(14), dp(6), x.text2);
                }
                if (i == 3 && "bottom-right".equals(session.text("layout.chatList.row.meta.seenAvatar", "none"))) {
                    avatar(canvas, x.w - side - dp(20), cy + dp(14), dp(7), AV[1]);
                }
                if (i < 2 && "right".equals(session.text("layout.chatList.row.meta.unreadDot", "none"))) {
                    p.setColor(x.accent);
                    canvas.drawCircle(x.w - side - dp(20), cy + dp(14), dp(5), p);
                } else if (i < 2) {
                    p.setColor(i == 0 ? x.accent : x.error);
                    rect.set(x.w - side - dp(36), cy + dp(4), x.w - side - dp(12), cy + dp(24));
                    String bs = session.text("layout.chatList.row.badge.shape", "pill");
                    float br = "square".equals(bs) ? dp(3) : "rounded".equals(bs) ? dp(7) : dp(10);
                    canvas.drawRoundRect(rect, br, br, p);
                    p.setColor(x.onAccent);
                    p.setTextSize(dp(12));
                    canvas.drawText(String.valueOf(i + 2), x.w - side - dp(27), cy + dp(19), p);
                    add("chatRow.badge", rect);
                }
                if (dividers && !card) {
                    p.setColor(session.colorChain(x.divider, "components.cell.dividerColor"));
                    canvas.drawRect("full".equals(session.text("layout.chatList.row.divider.inset", "text")) ? 0 : tx, y + rowH - 1, x.w, y + rowH, p);
                }
            }
        }
        fab(canvas, x, true);
        drawTabs(canvas, x, "chats");
    }

    private void drawSettings(Canvas canvas, Ctx x) {
        int sbg = session.color("screens.settings.background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int top = bar(canvas, x, "Settings");
        String gs = session.text("screens.settings.groupStyle", session.text("components.section.style", "card"));
        boolean card = !"flat".equals(gs);
        int gap = dp(session.intValue("components.section.gapDp", 16));
        int rowH = dp(session.intValue("components.cell.heightDp", 56));
        int title = session.intValue("components.cell.titleSizeSp", 15), val = session.intValue("components.cell.valueSizeSp", 12);
        boolean tb = bold("components.cell.titleWeight", false);
        float ic = dp(session.intValue("components.cell.iconSizeDp", 20)) / 2f;
        int padL = dp(session.intValue("components.cell.paddingStartDp", 16));
        String iconStyle = session.text("screens.settings.iconStyle", "rounded");
        boolean desc = session.bool("screens.settings.showDescriptions", true);
        List<String> ids = listOr("layout.settings.items", "notifications", "privacy", "data", "folders", "language");
        if (desc && session.draft.get("components.cell.twoLineHeightDp") != null) {
            rowH = dp(session.intValue("components.cell.twoLineHeightDp", 64));
        }
        boolean sgrid = "grid".equals(session.text("layout.settings.mode", "list"));
        String shead = session.text("layout.settings.header", null);
        if (shead == null) {
            // the app's own header: big avatar with a camera badge, name, phone and username
            float cx = x.w / 2f, cy = top + dp(60);
            rect.set(dp(0), top, x.w, top + dp(150));
            add("settingsHeader", rect);
            avatar(canvas, cx, cy, dp(40), AV[0]);
            p.setColor(x.accent);
            canvas.drawCircle(cx + dp(30), cy + dp(30), dp(13), p);
            icon(canvas, "camera", cx + dp(30), cy + dp(30), dp(6), x.onAccent);
            p.setColor(x.text);
            p.setFakeBoldText(true);
            p.setTextSize(dp(18));
            canvas.drawText("Your Name", cx - p.measureText("Your Name") / 2f, top + dp(122), p);
            p.setFakeBoldText(false);
            p.setColor(x.text2);
            p.setTextSize(dp(12));
            String ph = "+95 9•• ••• ••• • @username";
            canvas.drawText(ph, cx - p.measureText(ph) / 2f, top + dp(142), p);
            top += dp(150);
        }
        if (shead != null) {
            float hh = dp(88);
            rect.set(0, top, x.w, top + hh);
            p.setColor("cover".equals(shead) ? (x.accent & 0x00FFFFFF) | 0x55000000 : x.surface);
            canvas.drawRect(rect, p);
            add("settingsHeader", rect);
            avatar(canvas, "cover".equals(shead) ? dp(48) : x.w / 2f, top + dp(36), dp(26), AV[0]);
            p.setColor(x.text);
            p.setFakeBoldText(true);
            p.setTextSize(dp(15));
            canvas.drawText("Aye Aye", "cover".equals(shead) ? dp(84) : x.w / 2f - p.measureText("Aye Aye") / 2f, "cover".equals(shead) ? top + dp(42) : top + dp(78), p);
            p.setFakeBoldText(false);
            if ("collapsible".equals(session.text("layout.settings.accounts", "list"))) {
                p.setColor(x.text2);
                canvas.drawText("▾", x.w - dp(28), top + dp(42), p);
            }
            top += (int) hh;
        }
        int y0 = top + gap;
        if (session.draft.get("components.section.headerSizeSp") != null || session.draft.get("components.section.headerColor") != null
                || session.draft.get("components.section.headerWeight") != null) {
            p.setColor(session.colorChain(x.text2, "components.section.headerColor"));
            p.setFakeBoldText(bold("components.section.headerWeight", false));
            p.setTextSize(dp(session.intValue("components.section.headerSizeSp", 13)));
            canvas.drawText("GENERAL", dp(20), y0 + dp(12), p);
            p.setFakeBoldText(false);
            y0 += dp(22);
        }
        if (sgrid) {
            int cols = session.intValue("layout.settings.grid.columns", 3);
            float cw = (x.w - dp(24)) / cols;
            for (int i = 0; i < ids.size(); i++) {
                float gx = dp(12) + (i % cols) * cw, gy = y0 + (i / cols) * dp(80);
                if (gy + dp(72) > x.h) {
                    break;
                }
                rect.set(gx + dp(4), gy, gx + cw - dp(4), gy + dp(72));
                p.setColor(x.surface);
                canvas.drawRoundRect(rect, dp(session.intValue("tokens.shapes.cardRadiusDp", 12)), dp(session.intValue("tokens.shapes.cardRadiusDp", 12)), p);
                add("settingsRow", rect);
                p.setColor(i % 2 == 0 ? x.accent : x.icon);
                canvas.drawCircle(rect.centerX(), gy + dp(26), dp(11), p);
                p.setColor(x.text);
                p.setTextSize(dp(11));
                String l = item(ids.get(i));
                canvas.drawText(l, rect.centerX() - p.measureText(l) / 2f, gy + dp(58), p);
            }
            return;
        }
        float side = card ? dp(12) : 0;
        String[] kuro = {"Kuro Theme", "Kuro Settings", "Number Finder", "Special Contacts", "Online Contacts", "Contact Changes"};
        String[] main = {"Account", "Chat Settings", "Privacy & Security", "Notifications", "Data and Storage", "Chat Folders", "Devices", "Power Saving", "Language"};
        String[] mainSub = {"Number, Username, Bio", "Wallpaper, Night Mode, Animations", "Last Seen, Devices, Passkeys", "Sounds, Calls, Badges", "Media download settings", "Sort chats into folders", "Manage connected devices", "Reduce power usage on low charge", "English"};
        if (session.raw("layout.settings.items") instanceof List) {
            main = new String[ids.size()];
            for (int k = 0; k < main.length; k++) {
                main[k] = item(ids.get(k));
            }
            mainSub = new String[main.length];
            java.util.Arrays.fill(mainSub, "Description");
        }
        float yy = settingsCard(canvas, x, y0, side, rowH, gs, card, kuro, null, null, null, 0);
        yy = settingsCard(canvas, x, yy + gap, side, rowH, gs, card, main, desc ? mainSub : null, null, null, kuro.length);
        String[] prem = {"Telegram Premium", "Telegram Stars", "My Grams", "Telegram Business", "Send a Gift"};
        String[] premVal = {null, "48", null, null, null};
        yy = settingsCard(canvas, x, yy + gap, side, rowH, gs, card, prem, null, premVal, null, 9);
        String[] help = {"Ask a Question", "Telegram FAQ", "Telegram Features", "Privacy Policy"};
        yy = settingsCard(canvas, x, yy + gap, side, rowH, gs, card, help, null, null, "Help", 14);
        p.setColor(x.text2);
        p.setTextSize(dp(12));
        String[] foot = {"Kurogram v1.0.0 (1) release", "Based on Telegram v12.10.6"};
        for (int k = 0; k < foot.length; k++) {
            canvas.drawText(foot[k], x.w / 2f - p.measureText(foot[k]) / 2f, yy + dp(28) + k * dp(18), p);
        }
        drawTabs(canvas, x, "settings");
    }

    /** One rounded card of settings rows (coloured icon, title, optional second line). Returns where it ends. */
    /** Settings row title -> the drawable name KuroIcons draws (null = no glyph in the preview). */
    private static String settingsIconEntry(String title) {
        switch (title) {
            case "Kuro Theme": return "kuro_theme";
            case "Kuro Settings": return "filled_profile_settings";
            case "Number Finder": return "kuro_user_finder";
            case "Special Contacts": return "kuro_special_contacts";
            case "Online Contacts": return "kuro_online_contacts";
            case "Contact Changes": return "kuro_contact_changes";
            case "Account": return "settings_account";
            case "Chat Settings": return "settings_chat";
            case "Privacy & Security": return "settings_privacy";
            case "Notifications": return "settings_sounds";
            case "Data and Storage": return "settings_data";
            case "Chat Folders": return "settings_folders";
            case "Devices": return "settings_devices";
            case "Power Saving": return "settings_power";
            case "Language": return "settings_language";
            case "Ask a Question": return "settings_ask";
            case "Telegram FAQ": return "settings_faq";
            case "Telegram Features": return "settings_features";
            case "Privacy Policy": return "settings_policy";
            default: return null;
        }
    }

    private float settingsCard(Canvas canvas, Ctx x, float y0, float side, int rowH0, String gs, boolean card, String[] titles, String[] subs, String[] values, String header, int colourOffset) {
        int title = session.intValue("components.cell.titleSizeSp", 15), val = session.intValue("components.cell.valueSizeSp", 12);
        boolean tb = bold("components.cell.titleWeight", false);
        float ic = dp(session.intValue("components.cell.iconSizeDp", 20)) / 2f;
        int padL = dp(session.intValue("components.cell.paddingStartDp", 16));
        String iconStyle = session.text("screens.settings.iconStyle", "rounded");
        int rowH = subs != null ? Math.max(rowH0, dp(session.intValue("components.cell.twoLineHeightDp", 62))) : rowH0;
        int rows = titles.length;
        float hdr = header != null ? dp(38) : 0;
        float cardTop = y0;
        rect.set(side, y0, x.w - side, y0 + hdr + rows * rowH);
        int fillC = session.colorChain(x.surface, "components.card.background");
        if ("glass".equals(gs)) {
            fillC = (fillC & 0x00FFFFFF) | 0xB0000000;
        }
        float rr = card ? dp(session.intValue("components.section.radiusDp", session.intValue("tokens.shapes.cardRadiusDp", 16))) : 0;
        shadow(canvas, rect, rr, session.intValue("tokens.elevation.cardDp", 0));
        p.setColor(fillC);
        canvas.drawRoundRect(rect, rr, rr, p);
        add("section", rect);
        if (header != null) {
            p.setColor(x.accent);
            p.setFakeBoldText(true);
            p.setTextSize(dp(14));
            canvas.drawText(header, side + dp(16), y0 + dp(26), p);
            p.setFakeBoldText(false);
            y0 += hdr;
        }
        for (int i = 0; i < rows; i++) {
            float y = y0 + i * rowH, cy = y + rowH / 2f;
            rect.set(side, y, x.w - side, y + rowH);
            add("settingsRow", rect);
            add("cell", rect);
            p.setColor(AV[(i + colourOffset) % AV.length]);
            RectF ib = new RectF(side + padL, cy - ic * 1.25f, side + padL + 2.5f * ic, cy + ic * 1.25f);
            if ("square".equals(iconStyle)) {
                canvas.drawRect(ib, p);
            } else if ("circle".equals(iconStyle)) {
                canvas.drawCircle(ib.centerX(), cy, ic * 1.2f, p);
            } else {
                canvas.drawRoundRect(ib, ic * 0.7f, ic * 0.7f, p);
            }
            String entry = settingsIconEntry(titles[i]);
            if (entry != null) {
                String pack = session.text("screens.settings.iconPack", "default");
                android.graphics.drawable.Drawable g = tw.kurocore.kurogram.skin.KuroIcons.forResource(entry, !"outline".equals(pack));
                if (g != null) {
                    int gi = (int) (ic * 0.95f);
                    g.setBounds((int) ib.centerX() - gi, (int) cy - gi, (int) ib.centerX() + gi, (int) cy + gi);
                    g.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
                    g.draw(canvas);
                }
            }
            float tx = side + padL + 2.5f * ic + dp(14);
            p.setColor(x.text);
            p.setFakeBoldText(tb);
            p.setTextSize(dp(title));
            canvas.drawText(titles[i], tx, subs != null ? cy - dp(2) : cy + dp(5), p);
            p.setFakeBoldText(false);
            if (subs != null) {
                p.setColor(x.text2);
                p.setTextSize(dp(val));
                canvas.drawText(subs[i], tx, cy + dp(val + 3), p);
            }
            if (values != null && values[i] != null) {
                p.setColor(x.accent);
                p.setTextSize(dp(15));
                canvas.drawText(values[i], x.w - side - dp(16) - p.measureText(values[i]), cy + dp(5), p);
            }
            if (i < rows - 1 && session.bool("components.cell.dividerVisible", false)) {
                p.setColor(session.colorChain(x.divider, "components.cell.dividerColor"));
                float inset = dp(session.intValue("components.cell.dividerInsetDp", 60));
                canvas.drawRect(side + inset, y + rowH - 1, x.w - side, y + rowH, p);
            }
        }
        return cardTop + hdr + rows * rowH;
    }

    private void drawDrawer(Canvas canvas, Ctx x) {
        rect.set(0, 0, x.w, x.h);
        p.setColor(session.color("tokens.colors.overlay", 0x66000000));
        canvas.drawRect(rect, p);
        float dw = x.w * 0.72f;
        rect.set(0, 0, dw, x.h);
        p.setColor(x.surface);
        canvas.drawRect(rect, p);
        add("drawer", rect);
        p.setColor(x.accent);
        canvas.drawCircle(dp(40), dp(44), dp(26), p);
        p.setColor(x.text);
        p.setFakeBoldText(true);
        p.setTextSize(dp(16));
        canvas.drawText("Aye Aye", dp(16), dp(96), p);
        p.setFakeBoldText(false);
        List<String> ids = listOr("layout.drawer.items", "profile", "contacts", "calls", "saved", "settings");
        float y = dp(120);
        for (String id : ids) {
            if (y + dp(44) > x.h) {
                break;
            }
            p.setColor(x.icon);
            canvas.drawCircle(dp(28), y + dp(22), dp(9), p);
            p.setColor(x.text);
            p.setTextSize(dp(15));
            canvas.drawText(item(id), dp(52), y + dp(27), p);
            y += dp(44);
        }
    }


    // ------------------------------------------------------------------ list-style screens drawn from PreviewScreens

    private static String nativeBgKey(String scene) {
        switch (scene) {
            case "rows.privacy": return "privacy";
            case "rows.notifications": return "notifications";
            case "rows.dataStorage": return "dataStorage";
            case "rows.profile": return "profile";
            case "devices": return "devices";
            case "numberFinder": return "numberFinder";
            case "specialContacts": return "specialContacts";
            case "onlineContacts": return "onlineContacts";
            case "contactChanges": return "contactChanges";
            case "chatSettings": return "chatSettings";
            case "folders": return "folders";
            case "powerSaving": return "powerSaving";
            case "language": return "language";
            case "kuroSettings": return "kuroSettings";
            default: return null;
        }
    }

    private static final class Grp {
        final List<PreviewScreens.Item> items = new ArrayList<>();
        boolean card;
        float h;
    }

    private float itemH(PreviewScreens.Item it, int rowH) {
        switch (it.kind) {
            case HEADER: return dp(40);
            case ROW:
            case TOGGLE: return it.sub != null && !it.sub.isEmpty() ? Math.max(rowH, dp(62)) : rowH;
            case RADIO: return dp(62);
            case LINK: return rowH;
            case PERSON: return dp(66);
            case DEVICE: return dp(78);
            case SLIDER: return dp(58);
            case BUBBLES: return dp(150);
            case SWATCHES: return dp(112);
            case LIST_TILES: return dp(132);
            case APP_ICONS: return dp(96);
            case TEXT_ROW: return rowH;
            case MODE_SLIDER: return dp(66);
            case CARD_BUTTON: return dp(64);
            default: return rowH;
        }
    }

    private List<String> wrap(String text, float maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String cand = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && p.measureText(cand) > maxW) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(cand);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private void drawNative(Canvas canvas, Ctx x, String scene) {
        String bgKey = nativeBgKey(scene);
        int sbg = bgKey == null ? 0 : session.color("screens." + bgKey + ".background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        String title = PreviewScreens.title(scene), acts = PreviewScreens.actions(scene);
        int bh = dp(session.intValue("components.appBar.heightDp", 52));
        int fillBar = session.colorChain(x.bg, "components.appBar.background");
        int fg = session.colorChain(x.text, "components.appBar.foreground");
        if (!title.isEmpty() || sbg == 0) {
            rect.set(0, 0, x.w, bh);
            p.setColor(title.isEmpty() ? x.bg : fillBar);
            canvas.drawRect(rect, p);
        }
        add("appBar", new RectF(0, 0, x.w, bh));
        icon(canvas, "back", dp(28), bh / 2f, dp(9), fg);
        p.setColor(fg);
        p.setTextSize(dp(session.intValue("tokens.typography.titleSizeSp", 19)));
        p.setFakeBoldText(true);
        canvas.drawText(title, dp(72), bh / 2f + dp(6), p);
        p.setFakeBoldText(false);
        float ax = x.w - dp(26);
        if (acts.contains("more")) {
            icon(canvas, "more", ax, bh / 2f, dp(9), fg);
            ax -= dp(34);
        }
        if (acts.contains("trash")) {
            icon(canvas, "trash", ax, bh / 2f, dp(9), fg);
        }
        if (acts.contains("search")) {
            icon(canvas, "search", ax, bh / 2f, dp(9), fg);
        }

        List<PreviewScreens.Item> items = PreviewScreens.screen(scene);
        String hiddenKey = scene.startsWith("rows.") ? scene.substring(5) : null;
        List<String> hidden = hiddenKey == null ? new ArrayList<>() : listOr("layout." + hiddenKey + ".hiddenRows");
        int rowH = dp(session.intValue("components.cell.heightDp", 54));
        float side = dp(12), pad = dp(16);
        String gs = session.text("screens.settings.groupStyle", session.text("components.section.style", "card"));
        boolean card = !"flat".equals(gs);
        float gap = dp(session.intValue("components.section.gapDp", 14));
        float rr = card ? dp(session.intValue("components.section.radiusDp", session.intValue("tokens.shapes.cardRadiusDp", 16))) : 0;
        int cardFill = session.colorChain(x.surface, "components.card.background");

        // group the rows into cards and loose items
        List<Grp> groups = new ArrayList<>();
        Grp cur = null;
        for (PreviewScreens.Item it : items) {
            switch (it.kind) {
                case GAP:
                    cur = null;
                    continue;
                case INFO:
                case HERO:
                case LOGO:
                case FIELD:
                case BUTTON: {
                    cur = null;
                    Grp g = new Grp();
                    g.items.add(it);
                    groups.add(g);
                    continue;
                }
                default:
            }
            if (it.kind == PreviewScreens.Kind.HEADER && cur != null && !cur.items.isEmpty()) {
                cur = null;
            }
            if (it.hide != null && hidden.contains(it.hide) && !editMode) {
                continue;
            }
            if (cur == null) {
                cur = new Grp();
                cur.card = true;
                groups.add(cur);
            }
            cur.items.add(it);
        }

        float y = bh + dp(8);
        for (Grp g : groups) {
            if (y > x.h) {
                break;
            }
            if (!g.card) {
                y = drawLoose(canvas, x, g.items.get(0), y, side, pad) + dp(8);
                continue;
            }
            float ch = 0;
            for (PreviewScreens.Item it : g.items) {
                ch += itemH(it, rowH);
            }
            rect.set(side, y, x.w - side, y + ch);
            RectF cr = new RectF(rect);
            shadow(canvas, cr, rr, session.intValue("tokens.elevation.cardDp", 0));
            p.setColor(cardFill);
            canvas.drawRoundRect(cr, rr, rr, p);
            add("section", cr);
            float cy0 = y;
            for (int i = 0; i < g.items.size(); i++) {
                PreviewScreens.Item it = g.items.get(i);
                float ih = itemH(it, rowH);
                boolean gone = it.hide != null && hidden.contains(it.hide);
                boolean last = i == g.items.size() - 1;
                boolean nextHeader = !last && g.items.get(i + 1).kind == PreviewScreens.Kind.HEADER;
                drawNativeItem(canvas, x, it, cy0, ih, side, pad, gone, !last && !nextHeader && it.kind != PreviewScreens.Kind.HEADER);
                cy0 += ih;
            }
            y += ch + gap;
        }
    }

    /** Page-level items: hint text, hero pictures, the Kurogram logo, outlined fields and the big button. */
    private float drawLoose(Canvas canvas, Ctx x, PreviewScreens.Item it, float y, float side, float pad) {
        switch (it.kind) {
            case INFO: {
                p.setTextSize(dp(14));
                p.setColor(x.text2);
                float maxW = x.w - 2 * (side + pad);
                List<String> lines = wrap(it.title, maxW);
                float ty = y + dp(6);
                for (String l : lines) {
                    float tx = it.plain ? (x.w - p.measureText(l)) / 2f : side + pad;
                    canvas.drawText(l, tx, ty + dp(13), p);
                    ty += dp(19);
                }
                return ty + dp(6);
            }
            case HERO: {
                float cx = x.w / 2f, top = y + dp(14);
                if ("folder".equals(it.title)) {
                    for (int i = 0; i < 3; i++) {
                        p.setColor(i == 0 ? 0xFFE9B97C : i == 1 ? 0xFFEDC48F : 0xFFF2CF9F);
                        rect.set(cx - dp(32) + i * dp(3), top + dp(12) - i * dp(6), cx + dp(32) - i * dp(3), top + dp(56) - i * dp(6));
                        canvas.drawRoundRect(rect, dp(5), dp(5), p);
                    }
                } else {
                    p.setColor(0xFFBFC4CC);
                    rect.set(cx - dp(40), top + dp(4), cx + dp(40), top + dp(52));
                    canvas.drawRoundRect(rect, dp(5), dp(5), p);
                    p.setColor(x.accent);
                    p.setAlpha(150);
                    rect.set(cx - dp(34), top + dp(10), cx + dp(34), top + dp(46));
                    canvas.drawRoundRect(rect, dp(3), dp(3), p);
                    p.setAlpha(255);
                    p.setColor(0xFF8D939C);
                    rect.set(cx - dp(48), top + dp(52), cx + dp(48), top + dp(60));
                    canvas.drawRoundRect(rect, dp(3), dp(3), p);
                }
                p.setTextSize(dp(14));
                p.setColor(x.text2);
                float ty = top + dp(78);
                for (String l : wrap(it.sub, x.w - 2 * (side + pad + dp(10)))) {
                    canvas.drawText(l, (x.w - p.measureText(l)) / 2f, ty, p);
                    ty += dp(19);
                }
                return ty + dp(4);
            }
            case LOGO: {
                float cx = x.w / 2f, top = y + dp(10);
                p.setColor(x.accent);
                p.setAlpha(90);
                rect.set(cx - dp(46), top, cx + dp(46), top + dp(92));
                canvas.drawRoundRect(rect, dp(34), dp(34), p);
                p.setAlpha(255);
                p.setColor(x.bg);
                canvas.drawCircle(cx + dp(6), top + dp(62), dp(7), p);
                p.setColor(x.text);
                p.setTextSize(dp(24));
                p.setFakeBoldText(true);
                canvas.drawText(it.title, cx - p.measureText(it.title) / 2f, top + dp(130), p);
                p.setFakeBoldText(false);
                p.setTextSize(dp(14));
                p.setColor(x.text2);
                canvas.drawText(it.sub, cx - p.measureText(it.sub) / 2f, top + dp(152), p);
                return top + dp(166);
            }
            case FIELD: {
                float fh = dp(56), ir = rad("tokens.shapes.inputRadiusDp", 12);
                rect.set(side + dp(4), y + dp(8), x.w - side - dp(4), y + dp(8) + fh);
                RectF fr = new RectF(rect);
                p.setColor(session.colorChain(x.bg, "components.input.background"));
                canvas.drawRoundRect(fr, ir, ir, p);
                outlineFill(canvas, fr, ir, x.divider, 1);
                add("input", fr);
                p.setTextSize(dp(12));
                p.setColor(x.text2);
                canvas.drawText(it.title, fr.left + dp(14), fr.top + dp(2), p);
                boolean placeholder = "Phone number".equals(it.value);
                float tx = fr.left + dp(16);
                p.setTextSize(dp(17));
                if (it.sub != null) {
                    p.setColor(x.text);
                    canvas.drawText(it.sub, tx, fr.centerY() + dp(7), p);
                    p.setColor(x.divider);
                    canvas.drawRect(tx + dp(34), fr.top + dp(10), tx + dp(35), fr.bottom - dp(10), p);
                    tx += dp(52);
                }
                p.setColor(placeholder ? session.colorChain(x.text2, "components.input.hint") : x.text);
                canvas.drawText(it.value, tx, fr.centerY() + dp(7), p);
                if (it.chevron) {
                    icon(canvas, "back", fr.right - dp(22), fr.centerY(), dp(6), x.text2);
                }
                return y + dp(8) + fh + dp(6);
            }
            case BUTTON: {
                float bh = dp(session.intValue("components.button.heightDp", 52)), br = rad("tokens.shapes.buttonRadiusDp", 10);
                rect.set(side + dp(4), y + dp(12), x.w - side - dp(4), y + dp(12) + bh);
                RectF b = new RectF(rect);
                p.setColor(session.colorChain(x.accent, "components.button.background"));
                canvas.drawRoundRect(b, br, br, p);
                add("button", b);
                p.setColor(session.colorChain(x.onAccent, "components.button.foreground"));
                p.setTextSize(dp(16));
                p.setFakeBoldText(true);
                canvas.drawText(it.title, b.centerX() - p.measureText(it.title) / 2f, b.centerY() + dp(6), p);
                p.setFakeBoldText(false);
                return b.bottom + dp(8);
            }
            default:
                return y;
        }
    }

    private void drawSwitchAt(Canvas canvas, Ctx x, float right, float cy, boolean on, boolean lock) {
        float sx = right - dp(46);
        rect.set(sx, cy - dp(13), sx + dp(46), cy + dp(13));
        RectF sr = new RectF(rect);
        p.setColor(session.colorChain(on ? x.accent : 0xFFBDC3CC, on ? "components.switch.trackOn" : "components.switch.trackOff"));
        canvas.drawRoundRect(sr, dp(13), dp(13), p);
        p.setColor(session.colorChain(0xFFFFFFFF, "components.switch.thumbOn"));
        canvas.drawCircle(on ? sr.right - dp(13) : sr.left + dp(13), cy, dp(10), p);
        add("switch", sr);
    }

    private void drawNativeItem(Canvas canvas, Ctx x, PreviewScreens.Item it, float y, float h, float side, float pad, boolean gone, boolean divider) {
        float cy = y + h / 2f, left = side + pad, right = x.w - side - pad;
        int titleSp = session.intValue("components.cell.titleSizeSp", 16), valSp = session.intValue("components.cell.valueSizeSp", 13);
        boolean tb = bold("components.cell.titleWeight", false);
        boolean hasSub = it.sub != null && !it.sub.isEmpty();
        int titleC = it.error ? x.error : it.accent || it.kind == PreviewScreens.Kind.LINK ? x.accent : x.text;
        if (it.kind != PreviewScreens.Kind.HEADER) {
            rect.set(side, y, x.w - side, y + h);
            RectF rr0 = new RectF(rect);
            switch (it.kind) {
                case PERSON:
                    add("contactRow", rr0);
                    add("cell", rr0);
                    break;
                case DEVICE:
                    add("session", rr0);
                    add("cell", rr0);
                    break;
                case SLIDER:
                case BUBBLES:
                case SWATCHES:
                case LIST_TILES:
                case APP_ICONS:
                case MODE_SLIDER:
                    break;
                default:
                    add("settingsRow", rr0);
                    add("cell", rr0);
            }
        }
        // icon column
        float textX = left;
        boolean hasIcon = it.icon != null;
        if (hasIcon && it.kind != PreviewScreens.Kind.PERSON) {
            float is = dp(session.intValue("components.cell.iconSizeDp", 24));
            if (it.tile != 0) {
                p.setColor(it.tile);
                rect.set(left, cy - dp(16), left + dp(32), cy + dp(16));
                canvas.drawRoundRect(rect, dp(8), dp(8), p);
                glyph(canvas, it.icon, left + dp(16), cy, dp(19), 0xFFFFFFFF);
                textX = left + dp(48);
            } else {
                glyph(canvas, it.icon, left + dp(12), cy, is, it.error ? x.error : it.accent || it.kind == PreviewScreens.Kind.LINK ? x.accent : session.colorChain(x.icon, "components.cell.iconColor"));
                textX = left + dp(48);
            }
        }
        switch (it.kind) {
            case HEADER: {
                p.setColor(x.accent);
                p.setTextSize(dp(14));
                p.setFakeBoldText(true);
                canvas.drawText(it.title, left, y + dp(28), p);
                p.setFakeBoldText(false);
                if (it.chipText != null) {
                    float cx0 = left + p.measureText(it.title) + dp(10);
                    p.setTextSize(dp(12));
                    float cw = p.measureText(it.chipText) + dp(14);
                    p.setColor(x.accent);
                    p.setAlpha(40);
                    rect.set(cx0, y + dp(12), cx0 + cw, y + dp(32));
                    canvas.drawRoundRect(rect, dp(6), dp(6), p);
                    p.setAlpha(255);
                    p.setColor(x.accent);
                    canvas.drawText(it.chipText, cx0 + dp(7), y + dp(27), p);
                }
                return;
            }
            case ROW:
            case TOGGLE:
            case LINK:
            case TEXT_ROW:
            case RADIO:
                break;
            default:
        }
        switch (it.kind) {
            case ROW:
            case LINK:
            case TEXT_ROW: {
                if (it.handle) {
                    p.setColor(x.text2);
                    for (int i = 0; i < 2; i++) {
                        canvas.drawRect(left + dp(2), cy - dp(5) + i * dp(7), left + dp(20), cy - dp(3.5f) + i * dp(7), p);
                    }
                    textX = left + dp(48);
                }
                p.setColor(titleC);
                p.setAlpha(gone ? 110 : 255);
                p.setFakeBoldText(tb);
                p.setTextSize(dp(titleSp));
                canvas.drawText(it.title, textX, hasSub ? cy - dp(3) : cy + dp(5), p);
                if (gone) {
                    p.setColor(titleC);
                    canvas.drawRect(textX, cy + (hasSub ? -dp(8) : dp(0)), textX + p.measureText(it.title), cy + (hasSub ? -dp(7) : dp(1)), p);
                }
                p.setAlpha(255);
                p.setFakeBoldText(false);
                if (it.star) {
                    p.setColor(0xFF8E7CF0);
                    canvas.drawText("★", textX + p.measureText(it.title) + dp(8), cy + dp(6), p);
                }
                if (it.badge) {
                    p.setTextSize(dp(11));
                    float bw = p.measureText("NEW") + dp(12);
                    float bx = textX + dp(titleSp) * 0 + 0;
                    p.setTextSize(dp(titleSp));
                    bx = textX + p.measureText(it.title) + dp(10);
                    p.setColor(x.accent);
                    rect.set(bx, cy - dp(9), bx + bw, cy + dp(9));
                    canvas.drawRoundRect(rect, dp(5), dp(5), p);
                    p.setColor(x.onAccent);
                    p.setTextSize(dp(11));
                    p.setFakeBoldText(true);
                    canvas.drawText("NEW", bx + dp(6), cy + dp(4), p);
                    p.setFakeBoldText(false);
                }
                if (hasSub) {
                    p.setColor(session.colorChain(x.text2, "components.cell.subtitleColor"));
                    p.setTextSize(dp(valSp));
                    canvas.drawText(it.sub, textX, cy + dp(valSp + 4), p);
                }
                if (it.value != null) {
                    p.setTextSize(dp(15));
                    boolean muted = "47".equals(it.value);
                    p.setColor(muted ? x.text2 : session.colorChain(x.accent, "components.cell.valueColor"));
                    float vr = right - (it.chevron ? dp(14) : 0);
                    canvas.drawText(it.value, vr - p.measureText(it.value), cy + dp(5), p);
                    if (it.chevron) {
                        icon(canvas, "back", right - dp(4), cy, dp(5), x.text2);
                    }
                }
                if (it.menu) {
                    icon(canvas, "more", right - dp(4), cy, dp(8), x.text2);
                }
                break;
            }
            case TOGGLE: {
                if (hasIcon) {
                    // icon was drawn above
                }
                if (it.lock) {
                    // the switch carries a small padlock
                }
                p.setColor(x.text);
                p.setFakeBoldText(tb);
                p.setTextSize(dp(titleSp));
                canvas.drawText(it.title, textX, hasSub ? cy - dp(3) : cy + dp(5), p);
                p.setFakeBoldText(false);
                if (hasSub) {
                    p.setColor(session.colorChain(x.text2, "components.cell.subtitleColor"));
                    p.setTextSize(dp(valSp));
                    canvas.drawText(it.sub, textX, cy + dp(valSp + 4), p);
                }
                float sr = right;
                if (it.split) {
                    p.setColor(x.divider);
                    canvas.drawRect(right - dp(60), cy - dp(11), right - dp(59), cy + dp(11), p);
                }
                if (it.value != null) {
                    p.setColor(x.text);
                    p.setTextSize(dp(15));
                    float vx = right - dp(64) - p.measureText(it.value) - dp(18);
                    canvas.drawText(it.value, vx, cy + dp(5), p);
                    icon(canvas, "back", vx + p.measureText(it.value) + dp(10), cy, dp(4), x.text);
                }
                drawSwitchAt(canvas, x, sr, cy, it.on, it.lock);
                break;
            }
            case RADIO: {
                p.setColor(x.text);
                p.setTextSize(dp(titleSp));
                canvas.drawText(it.title, left + dp(52), cy - dp(2), p);
                p.setColor(x.text2);
                p.setTextSize(dp(valSp));
                canvas.drawText(it.sub, left + dp(52), cy + dp(18), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2));
                p.setColor(it.on ? x.accent : x.text2);
                canvas.drawCircle(left + dp(16), cy, dp(10), p);
                p.setStyle(Paint.Style.FILL);
                if (it.on) {
                    canvas.drawCircle(left + dp(16), cy, dp(5), p);
                }
                break;
            }
            case PERSON: {
                avatar(canvas, left + dp(22), cy, dp(22), AV[Math.abs(it.title.hashCode()) % AV.length]);
                p.setColor(0xFFFFFFFF);
                p.setTextSize(dp(15));
                String ini = it.title.substring(0, 1);
                canvas.drawText(ini, left + dp(22) - p.measureText(ini) / 2f, cy + dp(5), p);
                p.setColor(x.text);
                p.setFakeBoldText(true);
                p.setTextSize(dp(titleSp));
                canvas.drawText(it.title, left + dp(58), cy - dp(3), p);
                p.setFakeBoldText(false);
                p.setColor(x.text2);
                p.setTextSize(dp(valSp));
                String sub = it.sub;
                float mw = right - (left + dp(58)) - (it.value != null ? dp(40) : 0);
                while (p.measureText(sub) > mw && sub.length() > 4) {
                    sub = sub.substring(0, sub.length() - 2);
                }
                if (!sub.equals(it.sub)) {
                    sub += "…";
                }
                if ("online".equals(it.sub)) {
                    p.setColor(x.accent);
                }
                canvas.drawText(sub, left + dp(58), cy + dp(valSp + 4), p);
                if (it.value != null) {
                    p.setColor(x.text2);
                    p.setTextSize(dp(12));
                    canvas.drawText(it.value, right - p.measureText(it.value), cy - dp(6), p);
                }
                if (it.menu) {
                    icon(canvas, "more", right - dp(4), cy, dp(8), x.text2);
                }
                break;
            }
            case DEVICE: {
                p.setColor(0xFF5BBF4D);
                canvas.drawCircle(left + dp(24), cy, dp(24), p);
                p.setColor(0xFFFFFFFF);
                rect.set(left + dp(14), cy - dp(5), left + dp(34), cy + dp(10));
                canvas.drawRoundRect(rect, dp(5), dp(5), p);
                p.setColor(x.text);
                p.setTextSize(dp(titleSp));
                p.setFakeBoldText(true);
                canvas.drawText(it.title, left + dp(64), cy - dp(10), p);
                p.setFakeBoldText(false);
                p.setTextSize(dp(valSp + 1));
                canvas.drawText(it.sub, left + dp(64), cy + dp(8), p);
                p.setColor(x.text2);
                p.setTextSize(dp(valSp));
                canvas.drawText(it.value, left + dp(64), cy + dp(26), p);
                break;
            }
            case SLIDER: {
                float tw = right - left - dp(48), ty = cy;
                p.setColor(x.divider);
                rect.set(left, ty - dp(2), left + tw, ty + dp(2));
                canvas.drawRoundRect(rect, dp(2), dp(2), p);
                float frac = "17".equals(it.value) ? 0.97f : 0.25f;
                p.setColor(x.accent);
                rect.set(left, ty - dp(2), left + tw * frac, ty + dp(2));
                canvas.drawRoundRect(rect, dp(2), dp(2), p);
                canvas.drawCircle(left + tw * frac, ty, dp(8), p);
                p.setTextSize(dp(18));
                canvas.drawText(it.value, right - p.measureText(it.value), ty + dp(6), p);
                break;
            }
            case MODE_SLIDER: {
                p.setTextSize(dp(14));
                p.setColor(x.text2);
                canvas.drawText("Off", left, y + dp(20), p);
                canvas.drawText("On", right - p.measureText("On"), y + dp(20), p);
                p.setColor(x.accent);
                canvas.drawText(it.title, (x.w - p.measureText(it.title)) / 2f, y + dp(20), p);
                p.setColor(x.divider);
                rect.set(left, y + dp(44), right, y + dp(48));
                canvas.drawRoundRect(rect, dp(2), dp(2), p);
                p.setColor(x.accent);
                rect.set(left, y + dp(44), left + (right - left) * 0.1f, y + dp(48));
                canvas.drawRoundRect(rect, dp(2), dp(2), p);
                canvas.drawCircle(left + (right - left) * 0.1f, y + dp(46), dp(8), p);
                break;
            }
            case BUBBLES: {
                p.setColor(0xFF8DB87A);
                rect.set(side, y + dp(4), x.w - side, y + h - dp(4));
                canvas.drawRect(rect, p);
                p.setColor(session.color("chat.bubbleIn", x.surface));
                rect.set(left, y + dp(14), left + dp(210), y + dp(78));
                canvas.drawRoundRect(rect, dp(16), dp(16), p);
                p.setColor(x.text);
                p.setTextSize(dp(14));
                canvas.drawText("Good morning!", left + dp(14), y + dp(40), p);
                canvas.drawText("Do you know what time it is?", left + dp(14), y + dp(64), p);
                p.setColor(session.color("chat.bubbleOut", 0xFFE1FFC7));
                rect.set(x.w - side - pad - dp(190), y + dp(90), x.w - side - pad, y + dp(130));
                canvas.drawRoundRect(rect, dp(16), dp(16), p);
                p.setColor(x.text);
                canvas.drawText("It's morning in Tokyo", rect.left + dp(14), rect.centerY() + dp(5), p);
                break;
            }
            case SWATCHES: {
                int[] c1 = {0xFF6FA36B, 0xFFB9D96B, 0xFF7FB6E8, 0xFFB98BE5, 0xFF72C7C0, 0xFFE59A8E};
                float sw = (right - left - dp(8) * 5) / 6f;
                for (int i = 0; i < 6; i++) {
                    float sx = left + i * (sw + dp(8));
                    p.setColor(c1[i]);
                    rect.set(sx, y + dp(10), sx + sw, y + h - dp(10));
                    canvas.drawRoundRect(rect, dp(10), dp(10), p);
                    if (i == 0) {
                        outlineFill(canvas, rect, dp(10), x.accent, 2);
                    }
                    p.setColor(0xB0FFFFFF);
                    rect.set(sx + dp(6), y + dp(20), sx + sw - dp(6), y + dp(36));
                    canvas.drawRoundRect(rect, dp(8), dp(8), p);
                    rect.set(sx + dp(6), y + dp(44), sx + sw - dp(6) - dp(8), y + dp(60));
                    canvas.drawRoundRect(rect, dp(8), dp(8), p);
                }
                break;
            }
            case LIST_TILES: {
                float tw = (right - left - dp(14)) / 2f;
                for (int t = 0; t < 2; t++) {
                    float tx = left + t * (tw + dp(14));
                    p.setColor(x.bg);
                    rect.set(tx, y + dp(8), tx + tw, y + dp(80));
                    canvas.drawRoundRect(rect, dp(10), dp(10), p);
                    if (t == 0) {
                        outlineFill(canvas, rect, dp(10), x.accent, 2);
                    }
                    for (int r = 0; r < 2; r++) {
                        p.setColor(0xFFC8CCD2);
                        canvas.drawCircle(tx + dp(24), y + dp(26) + r * dp(32), dp(11), p);
                        rect.set(tx + dp(44), y + dp(20) + r * dp(32), tx + tw - dp(40), y + dp(25) + r * dp(32));
                        canvas.drawRoundRect(rect, dp(3), dp(3), p);
                        rect.set(tx + dp(44), y + dp(32) + r * dp(32), tx + tw - dp(24), y + dp(36) + r * dp(32));
                        canvas.drawRoundRect(rect, dp(3), dp(3), p);
                    }
                    p.setColor(x.text);
                    p.setTextSize(dp(14));
                    String lbl = t == 0 ? "Two lines" : "Three lines";
                    canvas.drawText(lbl, tx + (tw - p.measureText(lbl)) / 2f, y + dp(106), p);
                }
                break;
            }
            case APP_ICONS: {
                int[] c2 = {0xFFBBD7FA, 0xFF18B8A4, 0xFFB45CE0, 0xFF3F86F5, 0xFF1B2440, 0xFF4A74E8};
                float gapx = dp(10), sw = (right - left - gapx * 4) / 5f;
                for (int i = 0; i < 5; i++) {
                    float sx = left + i * (sw + gapx);
                    p.setColor(c2[i]);
                    canvas.drawCircle(sx + sw / 2f, y + dp(40), sw / 2f - dp(4), p);
                    if (i == 0) {
                        outlineFill(canvas, new RectF(sx, y + dp(40) - sw / 2f, sx + sw, y + dp(40) + sw / 2f), sw / 2f, x.accent, 2);
                    }
                }
                break;
            }
            case CARD_BUTTON: {
                float bh = dp(48), br = rad("tokens.shapes.buttonRadiusDp", 24);
                rect.set(left, y + dp(8), right, y + dp(8) + bh);
                RectF b = new RectF(rect);
                p.setColor(session.colorChain(x.accent, "components.button.background"));
                canvas.drawRoundRect(b, Math.min(br, bh / 2f), Math.min(br, bh / 2f), p);
                add("button", b);
                p.setColor(session.colorChain(x.onAccent, "components.button.foreground"));
                p.setTextSize(dp(16));
                p.setFakeBoldText(true);
                canvas.drawText(it.title, b.centerX() - p.measureText(it.title) / 2f + dp(12), b.centerY() + dp(6), p);
                p.setFakeBoldText(false);
                glyph(canvas, it.icon, b.centerX() - p.measureText(it.title) / 2f - dp(10), b.centerY(), dp(20), session.colorChain(x.onAccent, "components.button.foreground"));
                break;
            }
            default:
        }
        if (divider) {
            p.setColor(session.colorChain(x.divider, "components.cell.dividerColor"));
            float inset = hasIcon || it.kind == PreviewScreens.Kind.PERSON || it.kind == PreviewScreens.Kind.RADIO || it.handle ? dp(session.intValue("components.cell.dividerInsetDp", 64)) : dp(16);
            canvas.drawRect(side + inset, y + h - 1, x.w - side, y + h, p);
        }
    }

    /** A glyph from KuroIcons, in the colour asked for. */
    private void glyph(Canvas canvas, String name, float cx, float cy, float size, int color) {
        android.graphics.drawable.Drawable g = tw.kurocore.kurogram.skin.KuroIcons.forResource(name, false);
        if (g == null) {
            return;
        }
        int r = (int) (size / 2f);
        g.setBounds((int) cx - r, (int) cy - r, (int) cx + r, (int) cy + r);
        g.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
        g.draw(canvas);
    }

    private void drawRows(Canvas canvas, Ctx x, String screen, String title) {
        int sbg = session.color("screens." + screen + ".background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int top = bar(canvas, x, title);
        List<String> all = session.rules.find("layout." + screen + ".hiddenRows").rule.options;
        List<String> hidden = listOr("layout." + screen + ".hiddenRows");
        float y = top + dp(10), rowH = dp(36);
        rect.set(dp(12), y, x.w - dp(12), x.h - dp(10));
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, dp(14), dp(14), p);
        add("section", rect);
        for (String id : all) {
            if (y + rowH > x.h - dp(10)) {
                break;
            }
            boolean gone = hidden.contains(id);
            p.setColor(gone ? x.text2 : x.text);
            p.setAlpha(gone ? 120 : 255);
            p.setTextSize(dp(14));
            p.setStrikeThruText(gone);
            canvas.drawText(item(id), dp(28), y + dp(24), p);
            p.setStrikeThruText(false);
            p.setAlpha(255);
            y += rowH;
        }
    }


    private void avatar(Canvas canvas, float cx, float cy, float r, int color) {
        p.setColor(color);
        rect.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawOval(rect, p);
    }

    private static final int[] AV = {0xFFE17076, 0xFF7BC862, 0xFF65AADD, 0xFFEE7AAE, 0xFFF3A93C, 0xFF6EC9CB, 0xFFB694F9};

    private void drawContacts(Canvas canvas, Ctx x) {
        int sbg = session.color("screens.contacts.background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int top = bar(canvas, x, "Contacts");
        String[] names = {"Aye Aye", "Ko Min", "Daw Mya", "Mg Mg", "Su Su", "Thura", "Zaw Zaw", "Hla Hla", "Min Min", "Nandar", "Kyaw Kyaw", "Ei Ei", "Phyo", "Htet Htet"};
        String secStyle = session.text("screens.contacts.sectionStyle", "flat");
        boolean grid = "grid".equals(session.text("layout.contacts.mode", "list"));
        boolean time = "time".equals(session.text("layout.contacts.sort", "name"));
        if (grid) {
            int cols = session.intValue("layout.contacts.grid.columns", 3);
            float cw = x.w / (float) cols, ad = dp(session.intValue("layout.contacts.grid.avatarDp", 56));
            for (int i = 0; i < names.length; i++) {
                float px = (i % cols) * cw + cw / 2f, py = top + dp(10) + (i / cols) * (ad + dp(34)) + ad / 2f;
                if (py + ad / 2f > x.h - dp(8)) {
                    break;
                }
                rect.set(px - cw / 2f, py - ad / 2f - dp(4), px + cw / 2f, py + ad / 2f + dp(30));
                add("contactsGrid", rect);
                avatar(canvas, px, py, ad / 2f, AV[i]);
                p.setColor(x.text);
                p.setTextSize(dp(12));
                canvas.drawText(names[i], px - p.measureText(names[i]) / 2f, py + ad / 2f + dp(16), p);
            }
            return;
        }
        int rowH = dp(session.intValue("layout.contacts.row.heightDp", 64));
        int ad = dp(session.intValue("layout.contacts.row.avatarSizeDp", session.intValue("screens.contacts.avatarSizeDp", 46)));
        boolean status = session.bool("layout.contacts.row.status", true);
        float side = dp(12);
        float cr = dp(session.intValue("tokens.shapes.cardRadiusDp", 16));
        float y = top + dp(10);
        // card 1: Invite Friends / Recent calls
        float r1 = dp(54);
        rect.set(side, y, x.w - side, y + 2 * r1);
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, cr, cr, p);
        add("card", rect);
        String[] act = {"Invite Friends", "Recent calls"};
        int[] actColor = {0xFF3390EC, 0xFF4CAF50};
        for (int i = 0; i < 2; i++) {
            float ry = y + i * r1;
            rect.set(side, ry, x.w - side, ry + r1);
            add("cell", rect);
            RectF ib = new RectF(side + dp(14), ry + r1 / 2f - dp(16), side + dp(46), ry + r1 / 2f + dp(16));
            p.setColor(actColor[i]);
            canvas.drawRoundRect(ib, dp(9), dp(9), p);
            icon(canvas, i == 0 ? "adduser" : "calls", ib.centerX(), ib.centerY(), dp(8), 0xFFFFFFFF);
            p.setColor(x.text);
            p.setTextSize(dp(15));
            canvas.drawText(act[i], side + dp(60), ry + r1 / 2f + dp(5), p);
        }
        y += 2 * r1 + dp(12);
        // card 2: the contact list
        float cardH = dp(40) + names.length * rowH;
        rect.set(side, y, x.w - side, y + cardH);
        p.setColor("glass".equals(secStyle) ? (x.surface & 0x00FFFFFF) | 0xB0000000 : x.surface);
        canvas.drawRoundRect(rect, cr, cr, p);
        add("section", rect);
        p.setColor(x.accent);
        p.setTextSize(dp(13));
        canvas.drawText(time ? "Sorted by last seen time" : "Sorted by name", side + dp(16), y + dp(26), p);
        y += dp(40);
        for (int i = 0; i < names.length; i++) {
            rect.set(side, y, x.w - side, y + rowH);
            add("contactRow", rect);
            float cy = y + rowH / 2f;
            avatar(canvas, side + dp(14) + ad / 2f, cy, Math.min(ad, rowH - dp(8)) / 2f, AV[i % AV.length]);
            p.setColor(x.text);
            p.setTextSize(dp(15));
            canvas.drawText(names[i], side + dp(28) + ad, status ? cy - dp(2) : cy + dp(5), p);
            if (status) {
                p.setColor(x.text2);
                p.setTextSize(dp(12));
                canvas.drawText("last seen recently", side + dp(28) + ad, cy + dp(15), p);
            }
            y += rowH;
        }
        fab(canvas, x, false);
        drawTabs(canvas, x, "contacts");
    }

    private void drawPeople(Canvas canvas, Ctx x) {
        int sbg = session.color("screens.contacts.background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int top = bar(canvas, x, "People");
        top += dp(6);
        if ("on".equals(session.text("layout.people.storyRail", "on"))) {
            // story cards: the first one adds a story, the others show a friend's story with a ringed avatar
            String[] labels = {"Add to story", "May", "Thiha", "Referrer", "Thi Ha", "Cristine"};
            int cw = dp(74), ch = dp(104), gap = dp(6);
            rect.set(0, top, x.w, top + ch);
            add("peopleRail", rect);
            for (int i = 0; i < labels.length; i++) {
                float l = dp(10) + i * (cw + gap);
                if (l > x.w) {
                    break;
                }
                rect.set(l, top, l + cw, top + ch);
                if (i == 0) {
                    p.setColor((x.divider & 0x00FFFFFF) | 0x66000000);
                } else {
                    int c = AV[(i - 1) % AV.length];
                    p.setColor(android.graphics.Color.rgb((int) (android.graphics.Color.red(c) * 0.55f), (int) (android.graphics.Color.green(c) * 0.55f), (int) (android.graphics.Color.blue(c) * 0.55f)));
                }
                canvas.drawRoundRect(rect, dp(12), dp(12), p);
                if (i == 0) {
                    p.setColor(x.accent);
                    canvas.drawCircle(l + cw / 2f, top + dp(48), dp(12), p);
                    p.setColor(x.onAccent);
                    p.setStrokeWidth(dp(1.8f));
                    canvas.drawLine(l + cw / 2f - dp(6), top + dp(48), l + cw / 2f + dp(6), top + dp(48), p);
                    canvas.drawLine(l + cw / 2f, top + dp(42), l + cw / 2f, top + dp(54), p);
                    p.setStrokeWidth(0);
                } else {
                    avatar(canvas, l + dp(20), top + dp(20), dp(13), AV[(i - 1) % AV.length]);
                    outlineCircle(canvas, l + dp(20), top + dp(20), dp(15), x.accent);
                }
                p.setColor(i == 0 ? x.text : 0xFFFFFFFF);
                p.setTextSize(dp(11));
                canvas.drawText(labels[i], l + dp(6), top + ch - dp(8), p);
            }
            top += ch + dp(10);
        }
        if ("on".equals(session.text("layout.people.chips", "on"))) {
            String[] chips = {"0 active", "All"};
            float cx = dp(12);
            for (int i = 0; i < chips.length; i++) {
                p.setTextSize(dp(13));
                float cw = p.measureText(chips[i]) + dp(i == 0 ? 38 : 26);
                rect.set(cx, top, cx + cw, top + dp(30));
                p.setColor(i == 0 ? (x.accent & 0x00FFFFFF) | 0x26000000 : (x.divider & 0x00FFFFFF) | 0x66000000);
                canvas.drawRoundRect(rect, dp(15), dp(15), p);
                add("peopleRail", rect);
                if (i == 0) {
                    p.setColor(x.accent);
                    canvas.drawCircle(cx + dp(14), top + dp(15), dp(4), p);
                }
                p.setColor(i == 0 ? x.accent : x.text);
                canvas.drawText(chips[i], cx + dp(i == 0 ? 26 : 13), top + dp(20), p);
                cx += cw + dp(8);
            }
            top += dp(40);
            p.setColor(x.text2);
            p.setTextSize(dp(12));
            canvas.drawText("Nobody is active right now.", dp(14), top + dp(4), p);
            top += dp(18);
        }
        String[] names = {"Ko Ko", "09976433453", "6Lottery", "any", "Aung", "Aung That Htwe", "Aunty Lay"};
        for (int i = 0; i < names.length && top + dp(60) < x.h * 1.3f; i++) {
            rect.set(0, top, x.w, top + dp(64));
            add("contactRow", rect);
            avatar(canvas, dp(36), top + dp(32), dp(24), AV[i % AV.length]);
            p.setColor(x.text);
            p.setTextSize(dp(15));
            canvas.drawText(names[i], dp(72), top + dp(29), p);
            p.setColor(x.text2);
            p.setTextSize(dp(13));
            canvas.drawText("last seen recently", dp(72), top + dp(48), p);
            top += dp(64);
        }
    }

    private void outlineCircle(Canvas canvas, float cx, float cy, float r, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(color);
        canvas.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawProfile(Canvas canvas, Ctx x) {
        int sbg = session.color("screens.profile.background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int top = bar(canvas, x, "Profile");
        String hs = session.text("screens.profile.headerStyle", "centered");
        int ad = dp(session.intValue("screens.profile.avatarSizeDp", 96));
        float ar = dp(session.intValue("screens.profile.avatarRadiusDp", 50));
        boolean card = "card".equals(session.text("screens.profile.photoStyle", "avatar"));
        float hh = card ? ad * 1.5f : ad + dp(60);
        rect.set(0, top, x.w, top + hh);
        p.setColor("glass".equals(hs) ? (x.accent & 0x00FFFFFF) | 0x33000000 : x.surface);
        canvas.drawRect(rect, p);
        add("profileHeader", rect);
        float cx = "left".equals(hs) ? dp(16) + ad / 2f : x.w / 2f, cy = top + dp(16) + ad / 2f;
        if (card) {
            rect.set(dp(16), top + dp(12), x.w - dp(16), top + hh - dp(12));
            p.setColor(AV[2]);
            canvas.drawRoundRect(rect, ar, ar, p);
        } else {
            rect.set(cx - ad / 2f, cy - ad / 2f, cx + ad / 2f, cy + ad / 2f);
            p.setColor(AV[0]);
            canvas.drawRoundRect(rect, Math.min(ar, ad / 2f), Math.min(ar, ad / 2f), p);
        }
        add("profileHeader", rect);
        p.setColor(x.text);
        p.setFakeBoldText(true);
        p.setTextSize(dp(18));
        float tx = "left".equals(hs) ? dp(16) + ad + dp(14) : x.w / 2f - p.measureText("Aye Aye") / 2f;
        canvas.drawText("Aye Aye", tx, "left".equals(hs) ? cy : cy + ad / 2f + dp(24), p);
        p.setFakeBoldText(false);
        p.setColor(x.text2);
        p.setTextSize(dp(13));
        canvas.drawText("online", x.w / 2f - p.measureText("online") / 2f, cy + ad / 2f + dp(42), p);
        hh += dp(24);
        float y = top + hh + dp(12);
        boolean buttons = "buttons".equals(session.text("screens.profile.actionsStyle", "buttons"));
        String[] bl = {"Set Photo", "Edit Info", "Settings"};
        String[] bi = {"camera", "edit", "settings"};
        float bh = buttons ? dp(58) : dp(36);
        for (int i = 0; i < 3; i++) {
            float bw = (x.w - dp(24) - dp(16)) / 3f;
            rect.set(dp(12) + i * (bw + dp(8)), y, dp(12) + i * (bw + dp(8)) + bw, y + bh);
            p.setColor(buttons ? x.surface : (x.accent & 0x00FFFFFF) | 0x22000000);
            canvas.drawRoundRect(rect, dp(16), dp(16), p);
            add("profileHeader", rect);
            icon(canvas, bi[i], rect.centerX(), rect.top + dp(20), dp(9), x.text);
            p.setColor(x.text);
            p.setFakeBoldText(true);
            p.setTextSize(dp(12));
            canvas.drawText(bl[i], rect.centerX() - p.measureText(bl[i]) / 2f, rect.top + dp(46), p);
            p.setFakeBoldText(false);
        }
        y += bh + dp(12);
        float cr = dp(session.intValue("screens.profile.cardRadiusDp", 14));
        String[] labels = {"Mobile", "Bio", "Username", "Birthday"};
        String[] values = {"+95 9•• ••• •••", "Everything is Temporary", "@username", "Jan 1, 2000"};
        float rh = dp(52);
        rect.set(dp(12), y, x.w - dp(12), y + rh * labels.length);
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, cr, cr, p);
        add("infoCard", rect);
        for (int i = 0; i < labels.length; i++) {
            p.setColor(x.text);
            p.setTextSize(dp(15));
            canvas.drawText(values[i], dp(28), y + dp(22) + i * rh, p);
            p.setColor(x.text2);
            p.setTextSize(dp(12));
            canvas.drawText(labels[i], dp(28), y + dp(40) + i * rh, p);
        }
        y += rh * labels.length + dp(16);
        // Posts / Archived Posts switch
        RectF seg = new RectF(x.w / 2f - dp(110), y, x.w / 2f + dp(110), y + dp(36));
        p.setColor(x.surface);
        canvas.drawRoundRect(seg, dp(18), dp(18), p);
        RectF on = new RectF(seg.left + dp(4), seg.top + dp(4), seg.left + dp(70), seg.bottom - dp(4));
        p.setColor((x.accent & 0x00FFFFFF) | 0x26000000);
        canvas.drawRoundRect(on, dp(14), dp(14), p);
        p.setTextSize(dp(13));
        p.setColor(x.accent);
        canvas.drawText("Posts", on.centerX() - p.measureText("Posts") / 2f, seg.centerY() + dp(5), p);
        p.setColor(x.text2);
        canvas.drawText("Archived Posts", seg.right - dp(70) - p.measureText("Archived Posts") / 2f, seg.centerY() + dp(5), p);
        y += dp(80);
        p.setColor(x.text);
        p.setFakeBoldText(true);
        p.setTextSize(dp(17));
        canvas.drawText("No posts yet...", x.w / 2f - p.measureText("No posts yet...") / 2f, y, p);
        p.setFakeBoldText(false);
        p.setColor(x.text2);
        p.setTextSize(dp(12));
        String ph = "Publish photos and videos to display on your page";
        canvas.drawText(ph, x.w / 2f - p.measureText(ph) / 2f, y + dp(22), p);
        // Add a post button, just above the floating bar
        if (!skipPins) {
        RectF ab = new RectF(x.w / 2f - dp(80), x.h - dp(72) - dp(16) - dp(44), x.w / 2f + dp(80), x.h - dp(72) - dp(16));
        p.setColor(x.accent);
        canvas.drawRoundRect(ab, dp(22), dp(22), p);
        add("button", ab);
        icon(canvas, "camera", ab.left + dp(32), ab.centerY(), dp(8), x.onAccent);
        p.setColor(x.onAccent);
        p.setFakeBoldText(true);
        p.setTextSize(dp(14));
        canvas.drawText("Add a post", ab.left + dp(50), ab.centerY() + dp(5), p);
        p.setFakeBoldText(false);
        }
        drawTabs(canvas, x, "profile");
    }

    private void drawThemeStore(Canvas canvas, Ctx x) {
        int sbg = session.color("screens.themeStore.background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int bh = dp(session.intValue("components.appBar.heightDp", 52));
        int fillBar = session.colorChain(x.surface, "components.appBar.background");
        int fg = session.colorChain(x.text, "components.appBar.foreground");
        rect.set(0, 0, x.w, bh);
        p.setColor(fillBar);
        canvas.drawRect(rect, p);
        add("appBar", new RectF(0, 0, x.w, bh));
        icon(canvas, "back", dp(28), bh / 2f, dp(9), fg);
        p.setColor(fg);
        p.setTextSize(dp(session.intValue("tokens.typography.titleSizeSp", 19)));
        p.setFakeBoldText(true);
        canvas.drawText("Kuro Theme", dp(72), bh / 2f + dp(6), p);
        p.setFakeBoldText(false);
        int top = bh;
        if ("on".equals(session.text("layout.themeStore.search", "on"))) {
            icon(canvas, "search", x.w - dp(26), bh / 2f, dp(9), fg);
            add("search", new RectF(x.w - dp(46), 0, x.w, bh));
        }
        top += dp(8);
        if ("on".equals(session.text("layout.themeStore.chips", "on"))) {
            String[] chips = {"Popular", "Newest", "All", "Messenger"};
            boolean[] on = {true, false, true, false};
            float cx = dp(12);
            for (int i = 0; i < chips.length; i++) {
                p.setTextSize(dp(13));
                float cw = p.measureText(chips[i]) + dp(26);
                rect.set(cx, top, cx + cw, top + dp(32));
                RectF cr = new RectF(rect);
                p.setColor(on[i] ? x.accent : session.colorChain(x.surface, "components.card.background"));
                canvas.drawRoundRect(cr, dp(16), dp(16), p);
                add("themeCard", cr);
                p.setColor(on[i] ? x.onAccent : x.text2);
                canvas.drawText(chips[i], cx + dp(13), top + dp(21), p);
                cx += cw + dp(8);
            }
            top += dp(44);
        }
        boolean list = "list".equals(session.text("layout.themeStore.mode", "grid"));
        int cols = list ? 1 : session.intValue("screens.themeStore.gridColumns", 2);
        float gap = dp(session.intValue("screens.themeStore.cardGapDp", 12));
        float cr = dp(session.intValue("screens.themeStore.cardRadiusDp", 20)), pr = dp(session.intValue("screens.themeStore.previewRadiusDp", 16));
        String aspect = session.text("screens.themeStore.previewAspect", "portrait");
        float ratio = "square".equals(aspect) ? 1f : "tall".equals(aspect) ? 1.9f : 1.6f;
        float cw = (x.w - dp(24) - gap * (cols - 1)) / cols;
        float pw = list ? dp(70) : cw - dp(16), ph = list ? dp(70) : Math.min(pw * ratio, x.h * 0.5f);
        float ch = list ? ph + dp(16) : ph + dp(62);
        boolean below = "below".equals(session.text("layout.themeStore.meta.position", "below"));
        String[] titles = {"Messenger Inspired", "TG Profile Card", "Aurora", "Midnight", "Sakura", "Forest"};
        String[] cats = {"Messenger", "Custom", "Light", "Dark", "Light", "Dark"};
        int[][] tint = {{0xFFFDF6F4, 0xFFE9F2FD}, {0xFFFBE8DA, 0xFFD8F6FA}, {0xFFEAF0FF, 0xFFD3DEFF}, {0xFF2A2A40, 0xFF161626}, {0xFFFFE3EA, 0xFFFFC9D6}, {0xFFDFF0E0, 0xFFA8D5A9}};
        for (int i = 0; i < titles.length; i++) {
            int col = i % cols;
            float px = dp(12) + col * (cw + gap), py = top + (i / cols) * (ch + gap);
            if (py > x.h) {
                break;
            }
            rect.set(px, py, px + cw, py + ch);
            RectF card = new RectF(rect);
            p.setColor(session.colorChain(x.surface, "components.card.background"));
            canvas.drawRoundRect(card, cr, cr, p);
            outlineFill(canvas, card, cr, x.divider, 1);
            add("themeCard", card);
            // a miniature of the theme: a phone with a header, a story row and a few chats
            RectF mini = new RectF(px + dp(8), py + dp(8), px + dp(8) + pw, py + dp(8) + ph);
            p.setShader(new LinearGradient(mini.left, mini.top, mini.left, mini.bottom, tint[i][0], tint[i][1], Shader.TileMode.CLAMP));
            canvas.drawRoundRect(mini, pr, pr, p);
            p.setShader(null);
            outlineFill(canvas, mini, pr, x.divider, 1);
            if (!list) {
                boolean dark = i == 3 || i == 5;
                int line1 = dark ? 0xFFE8E8F0 : 0xFF3A3A44, line2 = dark ? 0xFF8A8A9A : 0xFFA9A9B0;
                float ix = mini.left + dp(10), iy = mini.top + dp(14);
                p.setColor(x.accent);
                rect.set(ix, iy, ix + mini.width() * 0.4f, iy + dp(5));
                canvas.drawRoundRect(rect, dp(3), dp(3), p);
                int[] dots = {0xFF3B82F6, 0xFF8B3FF0, 0xFFF63FD4, 0xFFF63F3F};
                float dx = ix;
                for (int d = 0; d < dots.length && dx + dp(16) < mini.right - dp(8); d++) {
                    p.setColor(dots[d]);
                    canvas.drawCircle(dx + dp(8), iy + dp(28), dp(8), p);
                    dx += dp(22);
                }
                float ry = iy + dp(52);
                for (int r = 0; ry + dp(26) < mini.bottom - dp(8) && r < 8; r++) {
                    p.setColor(dots[r % 4]);
                    canvas.drawCircle(ix + dp(8), ry + dp(8), dp(8), p);
                    p.setColor(line1);
                    rect.set(ix + dp(22), ry + dp(1), ix + dp(22) + mini.width() * 0.34f, ry + dp(5));
                    canvas.drawRoundRect(rect, dp(2), dp(2), p);
                    p.setColor(line2);
                    rect.set(ix + dp(22), ry + dp(9), ix + dp(22) + mini.width() * 0.5f, ry + dp(12));
                    canvas.drawRoundRect(rect, dp(2), dp(2), p);
                    ry += dp(24);
                }
            }
            p.setColor(x.text);
            p.setFakeBoldText(true);
            p.setTextSize(dp(session.intValue("screens.themeStore.titleSizeSp", 14)));
            String title = titles[i];
            float tx, ty;
            if (list) {
                tx = px + dp(16) + pw;
                ty = py + dp(28);
            } else {
                tx = card.centerX() - p.measureText(title) / 2f;
                ty = below ? mini.bottom + dp(24) : mini.bottom - dp(8);
            }
            if (!below && !list) {
                p.setColor(0xFFFFFFFF);
            }
            canvas.drawText(title, tx, ty, p);
            p.setFakeBoldText(false);
            p.setColor(x.text2);
            p.setTextSize(dp(11));
            String meta = cats[i] + (session.bool("layout.themeStore.meta.creator", true) ? " \u00B7 by Aye" : "")
                    + (session.bool("layout.themeStore.meta.downloads", true) ? " \u00B7 \u2193 17" : "") + "  \u2665 0";
            if (below || list) {
                float mx = list ? tx : card.centerX() - p.measureText(meta) / 2f;
                canvas.drawText(meta, mx, ty + dp(16), p);
            }
        }
        drawStoreBar(canvas, x, 0);
    }

    /** The Kuro store's floating bottom bar: Themes | Stickers | Profile. */
    private void drawStoreBar(Canvas canvas, Ctx x, int active) {
        if (skipPins) {
            return;
        }
        RectF r = tabBar(x.w, x.h);
        shadow(canvas, r, dp(31), 3);
        p.setColor(session.colorChain(x.surface, "components.bottomNavigation.background"));
        canvas.drawRoundRect(r, dp(31), dp(31), p);
        outlineFill(canvas, r, dp(31), x.divider, 1);
        add("bottomTabs", new RectF(r));
        String[] names = {"Themes", "Stickers", "Profile"};
        String[] glyphs = {"kuro_theme", "ui_sticker", "settings_account"};
        float cw = r.width() / 3f;
        for (int i = 0; i < 3; i++) {
            int col = i == active ? x.accent : x.text2;
            float cx = r.left + cw * i + cw / 2f;
            glyph(canvas, glyphs[i], cx, r.top + dp(22), dp(22), col);
            p.setColor(col);
            p.setTextSize(dp(11));
            canvas.drawText(names[i], cx - p.measureText(names[i]) / 2f, r.top + dp(47), p);
        }
    }

    private void drawStoreStickers(Canvas canvas, Ctx x) {
        int sbg = session.color("screens.themeStore.background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int top = bar(canvas, x, "Kuro Theme");
        String[] chips = {"Popular", "Newest"};
        p.setTextSize(dp(14));
        float tw = 0;
        for (String c : chips) {
            tw += p.measureText(c) + dp(32) + dp(8);
        }
        float cx = (x.w - tw + dp(8)) / 2f;
        for (int i = 0; i < chips.length; i++) {
            float cw = p.measureText(chips[i]) + dp(32);
            rect.set(cx, top + dp(8), cx + cw, top + dp(40));
            p.setColor(i == 1 ? x.accent : session.colorChain(x.surface, "components.card.background"));
            canvas.drawRoundRect(rect, dp(16), dp(16), p);
            add("themeCard", rect);
            p.setColor(i == 1 ? x.onAccent : x.text2);
            canvas.drawText(chips[i], cx + dp(16), top + dp(29), p);
            cx += cw + dp(8);
        }
        top += dp(52);
        String[] names = {"Cat", "Owl", "Bunny", "Chick", "Doge", "Bee", "Fox", "Panda", "Frog", "Duck", "Koala", "Seal"};
        int[] tint = {0xFFFFD6A5, 0xFFC9B6F2, 0xFFFFC4D6, 0xFFFFE680, 0xFFFFC27A, 0xFFFFE08A, 0xFFFFB38A, 0xFFD9D9D9, 0xFFB5E3A8, 0xFFFFEB8A, 0xFFC4CCD6, 0xFFB8D8F0};
        float gap = dp(8), cw = (x.w - dp(24) - gap * 2) / 3f, ch = dp(96), cr = dp(session.intValue("screens.themeStore.cardRadiusDp", 16));
        for (int i = 0; i < names.length; i++) {
            float px = dp(12) + (i % 3) * (cw + gap), py = top + (i / 3) * (ch + gap);
            if (py > x.h) {
                break;
            }
            rect.set(px, py, px + cw, py + ch);
            RectF cr0 = new RectF(rect);
            p.setColor(session.colorChain(x.surface, "components.card.background"));
            canvas.drawRoundRect(cr0, cr, cr, p);
            outlineFill(canvas, cr0, cr, x.divider, 1);
            add("themeCard", cr0);
            p.setColor(tint[i]);
            rect.set(cr0.centerX() - dp(26), py + dp(10), cr0.centerX() + dp(26), py + dp(62));
            canvas.drawRoundRect(rect, dp(12), dp(12), p);
            p.setColor(0x33000000);
            canvas.drawCircle(cr0.centerX() - dp(9), py + dp(32), dp(3), p);
            canvas.drawCircle(cr0.centerX() + dp(9), py + dp(32), dp(3), p);
            p.setColor(x.text);
            p.setTextSize(dp(13));
            p.setFakeBoldText(true);
            canvas.drawText(names[i], cr0.centerX() - p.measureText(names[i]) / 2f, py + dp(82), p);
            p.setFakeBoldText(false);
        }
        drawStoreBar(canvas, x, 1);
    }

    private void drawStoreProfile(Canvas canvas, Ctx x) {
        int sbg = session.color("screens.themeStore.background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int top = bar(canvas, x, "Kuro Theme");
        float cr = dp(session.intValue("screens.themeStore.cardRadiusDp", 20));
        int cardFill = session.colorChain(x.surface, "components.card.background");
        // header card
        float hy = top + dp(8), hh = dp(190);
        rect.set(dp(12), hy, x.w - dp(12), hy + hh);
        RectF hc = new RectF(rect);
        p.setColor(cardFill);
        canvas.drawRoundRect(hc, cr, cr, p);
        add("section", hc);
        float cx = x.w / 2f;
        p.setColor(0xFFC8CCD2);
        canvas.drawCircle(cx, hy + dp(46), dp(34), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(3));
        p.setColor(x.accent);
        canvas.drawCircle(cx, hy + dp(46), dp(38), p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(x.text);
        p.setTextSize(dp(18));
        p.setFakeBoldText(true);
        canvas.drawText("Alex Morgan", cx - p.measureText("Alex Morgan") / 2f, hy + dp(106), p);
        p.setFakeBoldText(false);
        p.setColor(x.accent);
        p.setTextSize(dp(14));
        canvas.drawText("@username", cx - p.measureText("@username") / 2f, hy + dp(126), p);
        p.setColor(x.text2);
        canvas.drawText("+00 000 000 000", cx - p.measureText("+00 000 000 000") / 2f, hy + dp(144), p);
        String[] nums = {"2", "23", "0"}, labs = {"Themes", "Downloads", "Likes"};
        for (int i = 0; i < 3; i++) {
            float sx = hc.left + hc.width() * (i * 2 + 1) / 6f;
            p.setColor(x.text);
            p.setTextSize(dp(16));
            canvas.drawText(nums[i], sx - p.measureText(nums[i]) / 2f, hy + dp(168), p);
            p.setColor(x.text2);
            p.setTextSize(dp(11));
            canvas.drawText(labs[i], sx - p.measureText(labs[i]) / 2f, hy + dp(182), p);
        }
        // content card with the iOS-style switch
        float cy = hy + hh + dp(10);
        rect.set(dp(12), cy, x.w - dp(12), x.h);
        RectF cc = new RectF(rect);
        p.setColor(cardFill);
        canvas.drawRoundRect(cc, cr, cr, p);
        add("section", cc);
        drawSegmented(canvas, x, new RectF(cc.left + dp(12), cy + dp(12), cc.right - dp(12), cy + dp(46)), new String[]{"Themes", "Stickers"}, 0);
        float gy = cy + dp(60), gw = (cc.width() - dp(36)) / 2f;
        rect.set(cc.left + dp(12), gy, cc.left + dp(12) + gw, gy + dp(190));
        RectF nb = new RectF(rect);
        p.setColor(x.accent);
        p.setAlpha(24);
        canvas.drawRoundRect(nb, dp(18), dp(18), p);
        p.setAlpha(255);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(x.accent);
        p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(8), dp(6)}, 0));
        canvas.drawRoundRect(nb, dp(18), dp(18), p);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);
        add("button", nb);
        p.setTextSize(dp(26));
        canvas.drawText("+", nb.centerX() - p.measureText("+") / 2f, nb.centerY(), p);
        p.setTextSize(dp(14));
        p.setFakeBoldText(true);
        canvas.drawText("Create New Theme", nb.centerX() - p.measureText("Create New Theme") / 2f, nb.centerY() + dp(26), p);
        p.setFakeBoldText(false);
        float bx = nb.right + dp(12);
        rect.set(bx, gy, bx + gw, gy + dp(190));
        RectF tc = new RectF(rect);
        p.setColor(x.bg);
        canvas.drawRoundRect(tc, dp(18), dp(18), p);
        add("themeCard", tc);
        p.setShader(new LinearGradient(tc.left, tc.top, tc.left, tc.bottom, 0xFFFBE3DC, 0xFFC9E0F7, Shader.TileMode.CLAMP));
        rect.set(tc.left + dp(8), tc.top + dp(8), tc.right - dp(8), tc.bottom - dp(48));
        canvas.drawRoundRect(rect, dp(12), dp(12), p);
        p.setShader(null);
        p.setColor(x.text);
        p.setTextSize(dp(13));
        p.setFakeBoldText(true);
        canvas.drawText("My Theme", tc.centerX() - p.measureText("My Theme") / 2f, tc.bottom - dp(24), p);
        p.setFakeBoldText(false);
        drawStoreBar(canvas, x, 2);
    }

    /** An iOS-style switch: a grey capsule track with a white capsule under the chosen label. */
    private void drawSegmented(Canvas canvas, Ctx x, RectF r, String[] labels, int active) {
        float rad = r.height() / 2f;
        p.setColor((x.text & 0x00FFFFFF) | 0x14000000);
        canvas.drawRoundRect(r, rad, rad, p);
        float w = (r.width() - dp(4)) / labels.length;
        RectF th = new RectF(r.left + dp(2) + w * active, r.top + dp(2), r.left + dp(2) + w * (active + 1), r.bottom - dp(2));
        p.setColor(session.colorChain(x.surface, "components.card.background"));
        p.setShadowLayer(dp(3), 0, dp(1), 0x33000000);
        canvas.drawRoundRect(th, rad - dp(2), rad - dp(2), p);
        p.clearShadowLayer();
        for (int i = 0; i < labels.length; i++) {
            p.setTextSize(dp(14));
            p.setFakeBoldText(i == active);
            p.setColor(i == active ? x.text : x.text2);
            float cx = r.left + dp(2) + w * i + w / 2f;
            canvas.drawText(labels[i], cx - p.measureText(labels[i]) / 2f, r.centerY() + dp(5), p);
        }
        p.setFakeBoldText(false);
    }

    private void drawChat(Canvas canvas, Ctx x) {
        int br = session.intValue("chat.bubbleRadiusDp", session.intValue("tokens.shapes.bubbleRadiusDp", 16));
        int bin = session.color("chat.bubbleIn", x.surface), bout = session.color("chat.bubbleOut", x.accent);
        int tin = session.color("chat.bubbleInText", x.text), tout = session.color("chat.bubbleOutText", x.onAccent);
        float fs = dp(session.intValue("chat.fontSizeSp", 16));
        int hbg = session.colorChain(x.surface, "components.appBar.background");
        // floating header: back, title pill, menu
        float cs = dp(44), m = dp(12);
        rect.set(m, dp(12), m + cs, dp(12) + cs);
        p.setColor(hbg);
        canvas.drawOval(rect, p);
        add("chatHeader", rect);
        icon(canvas, "back", rect.centerX(), rect.centerY(), dp(9), x.text);
        float hr = rad("chat.headerRadiusDp", 22);
        rect.set(m + cs + dp(8), dp(12), x.w - m - cs - dp(8), dp(12) + cs);
        p.setColor(hbg);
        canvas.drawRoundRect(rect, hr, hr, p);
        add("appBar", rect);
        add("chatHeader", rect);
        avatar(canvas, rect.left + dp(24), rect.centerY(), dp(17), 0xFF222222);
        p.setColor(x.text);
        p.setFakeBoldText(true);
        p.setTextSize(dp(16));
        canvas.drawText("Channel name", rect.left + dp(50), rect.centerY() - dp(1), p);
        p.setFakeBoldText(false);
        p.setColor(x.text2);
        p.setTextSize(dp(12));
        canvas.drawText("1K subscribers", rect.left + dp(50), rect.centerY() + dp(15), p);
        rect.set(x.w - m - cs, dp(12), x.w - m, dp(12) + cs);
        p.setColor(hbg);
        canvas.drawOval(rect, p);
        add("chatHeader", rect);
        icon(canvas, "more", rect.centerX(), rect.centerY(), dp(9), x.text);
        // pinned message with the translate row
        float py = dp(12) + cs + dp(10);
        rect.set(m - dp(2), py, x.w - m + dp(2), py + dp(92));
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, dp(18), dp(18), p);
        add("chatPanel", rect);
        p.setColor(x.accent);
        canvas.drawRect(m + dp(10), py + dp(10), m + dp(13), py + dp(48), p);
        p.setFakeBoldText(true);
        p.setTextSize(dp(14));
        canvas.drawText("Pinned Message", m + dp(24), py + dp(26), p);
        p.setFakeBoldText(false);
        p.setColor(x.text);
        p.setTextSize(dp(13));
        canvas.drawText("A pinned message from the channel…", m + dp(24), py + dp(44), p);
        p.setColor(x.divider);
        canvas.drawRect(m - dp(2), py + dp(56), x.w - m + dp(2), py + dp(57), p);
        p.setColor(x.accent);
        p.setFakeBoldText(true);
        p.setTextSize(dp(14));
        String tr = "Translate to English";
        canvas.drawText(tr, x.w / 2f - p.measureText(tr) / 2f, py + dp(78), p);
        p.setFakeBoldText(false);
        // posts
        float y = py + dp(106);
        float bw = x.w - dp(36);
        p.setTextSize(fs);
        String[] t1 = {"A sample post in the channel with a few", "lines of text to preview your theme."};
        float mh = dp(24) + t1.length * (fs + dp(6)) + dp(44) + dp(46);
        rect.set(dp(8), y, dp(8) + bw, y + mh);
        p.setColor(bin);
        canvas.drawRoundRect(rect, dp(br), dp(br), p);
        add("bubble.in", rect);
        float ty = y + dp(24) + fs / 2f;
        p.setColor(tin);
        for (String l : t1) {
            canvas.drawText(l, dp(20), ty, p);
            ty += fs + dp(6);
        }
        // reactions
        String[] re = {"♥ 1", "★ 1"};
        float rx = dp(18);
        for (String r : re) {
            p.setTextSize(dp(13));
            float rw = p.measureText(r) + dp(28);
            rect.set(rx, ty - dp(6), rx + rw, ty + dp(26));
            p.setColor((x.accent & 0x00FFFFFF) | 0x26000000);
            canvas.drawRoundRect(rect, dp(16), dp(16), p);
            p.setColor(x.accent);
            canvas.drawText(r, rx + dp(14), ty + dp(14), p);
            rx += rw + dp(8);
        }
        p.setColor(x.text2);
        p.setTextSize(dp(12));
        String meta = "1K  Channel, 13:35";
        canvas.drawText(meta, dp(8) + bw - dp(12) - p.measureText(meta), ty + dp(44), p);
        p.setColor(x.divider);
        canvas.drawRect(dp(8), y + mh - dp(46), dp(8) + bw, y + mh - dp(45), p);
        p.setColor(x.accent);
        p.setFakeBoldText(true);
        p.setTextSize(dp(14));
        canvas.drawText("10 comments", dp(66), y + mh - dp(18), p);
        p.setFakeBoldText(false);
        avatar(canvas, dp(30), y + mh - dp(23), dp(12), AV[0]);
        avatar(canvas, dp(48), y + mh - dp(23), dp(12), AV[2]);
        y += mh + dp(10);
        // date pill
        String date = "February 9";
        p.setTextSize(dp(13));
        float dw = p.measureText(date) + dp(28);
        rect.set((x.w - dw) / 2f, y, (x.w + dw) / 2f, y + dp(26));
        p.setColor(session.color("chat.dateBackground", 0x66000000));
        canvas.drawRoundRect(rect, rad("chat.dateRadiusDp", 13), rad("chat.dateRadiusDp", 13), p);
        p.setColor(session.color("chat.dateText", 0xFFFFFFFF));
        canvas.drawText(date, rect.left + dp(14), rect.bottom - dp(8), p);
        add("datePill", rect);
        y += dp(38);
        // an outgoing bubble, so both colours can be edited
        p.setTextSize(fs);
        String out = "Looks great!";
        float ow = p.measureText(out) + dp(24), oh = fs + dp(22);
        rect.set(x.w - dp(12) - ow, y, x.w - dp(12), y + oh);
        p.setColor(bout);
        canvas.drawRoundRect(rect, dp(br), dp(br), p);
        add("bubble.out", rect);
        p.setColor(tout);
        canvas.drawText(out, rect.left + dp(12), y + oh / 2f + fs / 3f, p);
        // bottom: search, the wide input pill, gift
        float ih = dp(46), iy = x.h - ih - dp(10);
        rect.set(dp(10), iy, dp(10) + ih, iy + ih);
        p.setColor(x.surface);
        canvas.drawOval(rect, p);
        add("button", rect);
        icon(canvas, "search", rect.centerX(), rect.centerY(), dp(9), x.icon);
        rect.set(x.w - dp(10) - ih, iy, x.w - dp(10), iy + ih);
        p.setColor(x.surface);
        canvas.drawOval(rect, p);
        add("button", rect);
        icon(canvas, "gift", rect.centerX(), rect.centerY(), dp(9), x.icon);
        rect.set(dp(18) + ih, iy, x.w - dp(18) - ih, iy + ih);
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, rad("chat.inputRadiusDp", 23), rad("chat.inputRadiusDp", 23), p);
        add("chatInput", rect);
        p.setColor(x.text2);
        p.setTextSize(dp(16));
        canvas.drawText("Unmute", rect.centerX() - p.measureText("Unmute") / 2f, rect.centerY() + dp(5), p);
    }

    private void drawControls(Canvas canvas, Ctx x) {
        int top = bar(canvas, x, "Components");
        float y = top + dp(session.intValue("tokens.spacing.sm", 12));
        float side = dp(session.intValue("tokens.spacing.md", 12));
        float lh = (float) session.number("tokens.typography.lineHeightMultiplier", 1.0);
        // card
        int cardFill = session.colorChain(x.surface, "components.card.background");
        float cr = rad("tokens.shapes.cardRadiusDp", 14);
        rect.set(side, y, x.w - side, y + dp(session.intValue("tokens.spacing.lg", 16)) * 2 + dp(22) * lh + dp(16));
        float cardBottom = rect.bottom;
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
        canvas.drawText("Card title", side + dp(session.intValue("tokens.spacing.lg", 16)), y + dp(session.intValue("tokens.spacing.lg", 16)) + dp(14), p);
        p.setFakeBoldText(false);
        p.setColor(x.text2);
        p.setTextSize(dp(13));
        canvas.drawText("Cards group related things", side + dp(session.intValue("tokens.spacing.lg", 16)),
                y + dp(session.intValue("tokens.spacing.lg", 16)) + dp(14) + dp(20) * lh, p);
        y = cardBottom + dp(session.intValue("tokens.spacing.sm", 12));
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
            p.setColor(session.color("components.dialog.scrim", session.color("tokens.colors.overlay", 0x66000000)));
            canvas.drawRect(rect, p);
            RectF dlg = new RectF(x.w * 0.12f, y + dp(8), x.w * 0.88f, x.h - dp(8));
            int dlgFill = session.colorChain(session.color("tokens.colors.surfaceElevated", x.surface), "components.dialog.background");
            p.setColor(dlgFill);
            shadow(canvas, dlg, dr, session.intValue("tokens.elevation.dialogDp", 0));
            p.setColor(dlgFill);
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

    /** View y of a touch -> y in the drawn screen (the top bar and the tab bar stay put while the list scrolls). */
    private float contentY(float tx, float ty) {
        float vh = getHeight();
        if (!scrollable()) {
            return ty;
        }
        if (inPin(tx, ty)) {
            return ty + (vh * CONTENT_FACTOR - vh);
        }
        if (ty < dp(session.intValue("components.appBar.heightDp", 52))) {
            return ty;
        }
        return ty + scrollY;
    }

    private void pickAt(float tx, float ty) {
        float cy = contentY(tx, ty);
        Hit best = null;
        float bestArea = Float.MAX_VALUE;
        for (Hit h : hits) {
            if (h.r.contains(tx, cy)) {
                float area = h.r.width() * h.r.height();
                if (area <= bestArea) { // later (more specific) regions win ties
                    bestArea = area;
                    best = h;
                }
            }
        }
        String id = best == null ? "screenBackground" : best.id;
        int occ = 0;
        if (best != null) {
            for (Hit h : hits) {
                if (h == best) {
                    break;
                }
                if (h.id.equals(id)) {
                    occ++;
                }
            }
        }
        lastSelId = id;
        selOcc = occ;
        if (listener != null) {
            listener.onComponentTapped(id);
        }
        invalidate();
        performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean scroll = scrollable();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastY = downY = e.getY();
                moved = false;
                if (scroll) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (scroll) {
                    if (Math.abs(e.getY() - downY) > dp(8)) {
                        moved = true;
                    }
                    if (moved) {
                        scrollY -= e.getY() - lastY;
                        invalidate();
                    }
                }
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    if (editMode) {
                        pickAt(e.getX(), e.getY());
                    } else {
                        previewTap(e.getX(), e.getY());
                    }
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
