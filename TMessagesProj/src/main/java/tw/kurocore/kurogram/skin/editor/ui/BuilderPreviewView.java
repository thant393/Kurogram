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
    private static final float CONTENT_FACTOR = 1.8f; // list screens are laid out this many screens tall so they can be scrolled
    private String[] highlight;
    private float scrollY;
    private float lastY, downY;
    private boolean moved;
    private Runnable navigated;
    private boolean drawingThumb;

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
        if (hasTabs() && ty > vh - dp(72)) {
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
        drawScene(canvas, ch);
        canvas.restore();
        List<Hit> mainHits = new ArrayList<>(hits);
        if (scrollY > 0) {
            canvas.save();
            canvas.clipRect(0, 0, vw, dp(session.intValue("components.appBar.heightDp", 52)));
            drawScene(canvas, ch);
            canvas.restore();
            if (hasTabs()) {
                canvas.save();
                canvas.translate(0, -(ch - vh));
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(tabBar(vw, ch), dp(30), dp(30), android.graphics.Path.Direction.CW);
                canvas.clipPath(clip);
                drawScene(canvas, ch);
                canvas.restore();
            }
        }
        hits.clear();
        hits.addAll(mainHits);
    }

    private boolean hasTabs() {
        String s = session.scene();
        return "chats".equals(s) || "contacts".equals(s) || "settings".equals(s) || "profile".equals(s);
    }

    /** The floating bottom bar, like the real app: a rounded pill with a margin all round. */
    private RectF tabBar(float w, float h) {
        return new RectF(dp(34), h - dp(72), w - dp(34), h - dp(12));
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
            case "rows.privacy":
                drawRows(canvas, x, "privacy", "Privacy");
                break;
            case "rows.notifications":
                drawRows(canvas, x, "notifications", "Notifications");
                break;
            case "rows.dataStorage":
                drawRows(canvas, x, "dataStorage", "Data and storage");
                break;
            case "rows.profile":
                drawRows(canvas, x, "profile", "Profile");
                break;
            default:
                drawChats(canvas, x);
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
        float fs = dp(session.intValue("components.fab.sizeDp", 52));
        float fx = x.w - dp(16) - fs, fy = x.h - dp(72) - dp(16) - fs;
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
                add("chatRow", rect);
                float cy = y + rowH / 2f;
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
                    float ty2 = "bottom-right".equals(timePos) ? cy + dp(16) : cy - dp(6);
                    canvas.drawText(times[i], x.w - side - dp(12) - tw, ty2, p);
                    rect.set(x.w - side - dp(12) - tw, ty2 - dp(cap + 2), x.w - side - dp(12), ty2 + dp(2));
                    add("chatRow.time", rect);
                }
                if (i == 0 && "icon-left-of-time".equals(session.text("layout.chatList.row.pinned", "none"))) {
                    p.setColor(x.text2);
                    canvas.drawRect(x.w - side - dp(52), cy - dp(16), x.w - side - dp(44), cy - dp(8), p);
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
                    canvas.drawRoundRect(rect, dp(10), dp(10), p);
                    p.setColor(x.onAccent);
                    p.setTextSize(dp(12));
                    canvas.drawText(String.valueOf(i + 2), x.w - side - dp(27), cy + dp(19), p);
                    add("chatRow.badge", rect);
                }
                if (dividers && !card) {
                    p.setColor(session.colorChain(x.divider, "components.cell.dividerColor"));
                    canvas.drawRect(tx, y + rowH - 1, x.w, y + rowH, p);
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
        float yy = settingsCard(canvas, x, y0, side, rowH, gs, card, kuro, null, 0);
        settingsCard(canvas, x, yy + gap, side, rowH, gs, card, main, desc ? mainSub : null, kuro.length);
        drawTabs(canvas, x, "settings");
    }

    /** One rounded card of settings rows (coloured icon, title, optional second line). Returns where it ends. */
    private float settingsCard(Canvas canvas, Ctx x, float y0, float side, int rowH0, String gs, boolean card, String[] titles, String[] subs, int colourOffset) {
        int title = session.intValue("components.cell.titleSizeSp", 15), val = session.intValue("components.cell.valueSizeSp", 12);
        boolean tb = bold("components.cell.titleWeight", false);
        float ic = dp(session.intValue("components.cell.iconSizeDp", 20)) / 2f;
        int padL = dp(session.intValue("components.cell.paddingStartDp", 16));
        String iconStyle = session.text("screens.settings.iconStyle", "rounded");
        int rowH = subs != null ? Math.max(rowH0, dp(session.intValue("components.cell.twoLineHeightDp", 62))) : rowH0;
        int rows = titles.length;
        rect.set(side, y0, x.w - side, y0 + rows * rowH);
        int fillC = session.colorChain(x.surface, "components.card.background");
        if ("glass".equals(gs)) {
            fillC = (fillC & 0x00FFFFFF) | 0xB0000000;
        }
        float rr = card ? dp(session.intValue("components.section.radiusDp", session.intValue("tokens.shapes.cardRadiusDp", 16))) : 0;
        shadow(canvas, rect, rr, session.intValue("tokens.elevation.cardDp", 0));
        p.setColor(fillC);
        canvas.drawRoundRect(rect, rr, rr, p);
        add("section", rect);
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
            if (i < rows - 1 && session.bool("components.cell.dividerVisible", false)) {
                p.setColor(session.colorChain(x.divider, "components.cell.dividerColor"));
                float inset = dp(session.intValue("components.cell.dividerInsetDp", 60));
                canvas.drawRect(side + inset, y + rowH - 1, x.w - side, y + rowH, p);
            }
        }
        return y0 + rows * rowH;
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
        int top = bar(canvas, x, "People");
        if ("on".equals(session.text("layout.people.chips", "on"))) {
            String[] chips = {"All", "Online", "New"};
            float cx = dp(12);
            for (int i = 0; i < chips.length; i++) {
                p.setTextSize(dp(13));
                float cw = p.measureText(chips[i]) + dp(22);
                rect.set(cx, top + dp(8), cx + cw, top + dp(36));
                p.setColor(i == 0 ? x.accent : (x.divider & 0x00FFFFFF) | 0x33000000);
                canvas.drawRoundRect(rect, dp(14), dp(14), p);
                add("peopleRail", rect);
                p.setColor(i == 0 ? x.onAccent : x.text2);
                canvas.drawText(chips[i], cx + dp(11), top + dp(27), p);
                cx += cw + dp(8);
            }
            top += dp(44);
        }
        if ("on".equals(session.text("layout.people.storyRail", "on"))) {
            rect.set(0, top, x.w, top + dp(76));
            add("peopleRail", rect);
            for (int i = 0; i < 5; i++) {
                float cx = dp(36) + i * dp(64);
                avatar(canvas, cx, top + dp(30), dp(24), AV[i]);
                outlineCircle(canvas, cx, top + dp(30), dp(27), x.accent);
            }
            top += dp(80);
        }
        String[] names = {"Aye Aye", "Ko Min", "Daw Mya", "Mg Mg"};
        for (int i = 0; i < names.length && top + dp(56) < x.h; i++) {
            rect.set(0, top, x.w, top + dp(56));
            p.setColor(x.surface);
            canvas.drawRect(rect, p);
            add("contactRow", rect);
            avatar(canvas, dp(34), top + dp(28), dp(20), AV[i]);
            p.setColor(x.text);
            p.setTextSize(dp(15));
            canvas.drawText(names[i], dp(66), top + dp(33), p);
            top += dp(56);
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
        drawTabs(canvas, x, "profile");
    }

    private void drawThemeStore(Canvas canvas, Ctx x) {
        int sbg = session.color("screens.themeStore.background", 0);
        if (sbg != 0) {
            rect.set(0, 0, x.w, x.h);
            p.setColor(sbg);
            canvas.drawRect(rect, p);
        }
        int top = bar(canvas, x, "Themes");
        if ("on".equals(session.text("layout.themeStore.search", "on"))) {
            rect.set(dp(12), top + dp(8), x.w - dp(12), top + dp(44));
            p.setColor(session.colorChain(x.surface, "components.search.background"));
            canvas.drawRoundRect(rect, dp(18), dp(18), p);
            add("search", rect);
            p.setColor(x.text2);
            p.setTextSize(dp(14));
            canvas.drawText("Search themes", dp(30), top + dp(31), p);
            top += dp(52);
        }
        if ("on".equals(session.text("layout.themeStore.chips", "on"))) {
            String[] chips = {"All", "Dark", "Light"};
            float cx = dp(12);
            for (int i = 0; i < chips.length; i++) {
                p.setTextSize(dp(13));
                float cw = p.measureText(chips[i]) + dp(22);
                rect.set(cx, top, cx + cw, top + dp(28));
                p.setColor(i == 0 ? x.accent : (x.divider & 0x00FFFFFF) | 0x33000000);
                canvas.drawRoundRect(rect, dp(14), dp(14), p);
                add("themeCard", rect);
                p.setColor(i == 0 ? x.onAccent : x.text2);
                canvas.drawText(chips[i], cx + dp(11), top + dp(19), p);
                cx += cw + dp(8);
            }
            top += dp(36);
        }
        boolean list = "list".equals(session.text("layout.themeStore.mode", "grid"));
        int cols = list ? 1 : session.intValue("screens.themeStore.gridColumns", 2);
        int gap = dp(session.intValue("screens.themeStore.cardGapDp", 10));
        float cr = dp(session.intValue("screens.themeStore.cardRadiusDp", 14)), pr = dp(session.intValue("screens.themeStore.previewRadiusDp", 12));
        String aspect = session.text("screens.themeStore.previewAspect", "portrait");
        float ratio = "square".equals(aspect) ? 1f : "tall".equals(aspect) ? 1.9f : 1.45f;
        float cw = (x.w - dp(24) - gap * (cols - 1)) / cols;
        float pw = list ? dp(70) : cw - dp(16), ph = list ? dp(70) : Math.min(pw * ratio, x.h * 0.28f);
        float ch = list ? ph + dp(16) : ph + dp(60);
        boolean below = "below".equals(session.text("layout.themeStore.meta.position", "below"));
        String[] titles = {"Aurora", "Midnight", "Sakura", "Forest", "Ocean", "Sand"};
        int[] tint = {0xFF4F7CFF, 0xFF1C1C2E, 0xFFFFB7C5, 0xFF2E7D32, 0xFF0288D1, 0xFFE0C097};
        for (int i = 0; i < titles.length; i++) {
            int col = i % cols;
            float px = dp(12) + col * (cw + gap), py = top + (i / cols) * (ch + gap);
            if (py + ch > x.h) {
                break;
            }
            rect.set(px, py, px + cw, py + ch);
            p.setColor(x.surface);
            canvas.drawRoundRect(rect, cr, cr, p);
            add("themeCard", rect);
            rect.set(px + dp(8), py + dp(8), px + dp(8) + pw, py + dp(8) + ph);
            p.setColor(tint[i]);
            canvas.drawRoundRect(rect, pr, pr, p);
            p.setColor(x.text);
            p.setFakeBoldText(true);
            p.setTextSize(dp(session.intValue("screens.themeStore.titleSizeSp", 14)));
            float tx = list ? px + dp(16) + pw : px + dp(10);
            float ty = list ? py + dp(28) : below ? py + ph + dp(26) : py + ph - dp(6);
            if (!below && !list) {
                p.setColor(0xFFFFFFFF);
            }
            canvas.drawText(titles[i], tx, ty, p);
            p.setFakeBoldText(false);
            p.setColor(x.text2);
            p.setTextSize(dp(11));
            String meta = (session.bool("layout.themeStore.meta.creator", true) ? "by Aye " : "")
                    + (session.bool("layout.themeStore.meta.downloads", true) ? "· 1.2k" : "");
            if (below || list) {
                canvas.drawText(meta, tx, ty + dp(16), p);
            }
        }
    }

    private void drawChat(Canvas canvas, Ctx x) {
        int top = bar(canvas, x, "Aye Aye");
        rect.set(0, 0, x.w, top);
        hits.remove(hits.size() - 1); // the bar registered itself as appBar; in a conversation it is the chat header too
        add("appBar", rect);
        add("chatHeader", rect);
        float hr = rad("chat.headerRadiusDp", 0);
        if (hr > 0) { // rounded bottom corners: paint the page colour into the corners
            p.setColor(x.bg);
            canvas.drawRect(0, top - hr, hr, top, p);
            canvas.drawRect(x.w - hr, top - hr, x.w, top, p);
            p.setColor(session.colorChain(x.surface, "components.appBar.background"));
            rect.set(0, top - hr * 2, x.w, top);
            canvas.drawRoundRect(rect, hr, hr, p);
        }
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
        float rr2 = rad("chat.reactionRadiusDp", 16);
        rect.set(dp(12), y + dp(2), dp(120), y + dp(30));
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, rr2, rr2, p);
        add("chatPanel", rect);
        y += dp(34);
        float pr = rad("chat.menuRadiusDp", rad("chat.panelRadiusDp", 14));
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
    private float contentY(float ty) {
        float vh = getHeight();
        if (!scrollable()) {
            return ty;
        }
        if (hasTabs() && ty > vh - dp(72)) {
            return ty + (vh * CONTENT_FACTOR - vh);
        }
        if (ty < dp(session.intValue("components.appBar.heightDp", 52))) {
            return ty;
        }
        return ty + scrollY;
    }

    private void pickAt(float tx, float ty) {
        float cy = contentY(ty);
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
