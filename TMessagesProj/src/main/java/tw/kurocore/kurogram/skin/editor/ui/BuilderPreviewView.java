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
    private static final float CONTENT_FACTOR = 2.2f; // Preview mode lays the list screens out this many screens tall
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
        if ("chats".equals(session.scene()) && ty > vh - dp(56)) {
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

    /** Where the selected part is drawn (view pixels), or null when it is not on this screen. */
    public android.graphics.RectF selectedBounds() {
        String sel = session.selected();
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).id.equals(sel)) {
                return new android.graphics.RectF(hits.get(i).r);
            }
        }
        return null;
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
            if ("chats".equals(session.scene())) {
                int band = dp(56);
                canvas.save();
                canvas.clipRect(0, vh - band, vw, vh);
                canvas.translate(0, -(ch - vh));
                drawScene(canvas, ch);
                canvas.restore();
            }
        }
        hits.clear();
        hits.addAll(mainHits);
    }

    private boolean scrollable() {
        if (editMode || drawingThumb || session == null) {
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
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
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
        p.setAlpha(150);
        canvas.drawCircle(x.w - dp(28), bh / 2f, dp(8), p);
        canvas.drawCircle(x.w - dp(60), bh / 2f, dp(8), p);
        p.setAlpha(255);
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

    private void drawTabs(Canvas canvas, Ctx x, int tabsH) {
        float ty = x.h - tabsH;
        boolean glass = "glass".equals(session.text("layout.bottomTabs.style", "flat"));
        int fill = session.colorChain(x.surface, "components.navigationBar.background", "components.bottomNavigation.background");
        if (glass) {
            fill = (fill & 0x00FFFFFF) | 0xAA000000;
        }
        int sel = session.colorChain(x.accent, "components.navigationBar.selected");
        int unsel = session.colorChain(x.icon, "components.navigationBar.unselected");
        int pill = session.colorChain(0, "components.bottomNavigation.selectedBackground");
        if (pill == 0 && "pill".equals(session.text("layout.bottomTabs.indicator", "none"))) {
            pill = (sel & 0x00FFFFFF) | 0x33000000;
        }
        boolean labels = session.bool("layout.bottomTabs.labels", true);
        float ic = dp(session.intValue("components.bottomNavigation.iconSizeDp", 24)) / 2.6f;
        rect.set(0, ty, x.w, x.h);
        p.setColor(fill);
        canvas.drawRect(rect, p);
        if (session.bool("layout.bottomTabs.border", false)) {
            p.setColor(x.divider);
            canvas.drawRect(0, ty, x.w, ty + 1, p);
        }
        add("bottomTabs", rect);
        List<String> tabs = listOr("layout.bottomTabs.items", "chats", "contacts", "calls", "settings");
        int n = tabs.size();
        for (int i = 0; i < n; i++) {
            float cx = x.w * (i + 0.5f) / n;
            rect.set(cx - x.w / (2f * n), ty, cx + x.w / (2f * n), x.h);
            if (i == 0 && pill != 0) {
                RectF b = new RectF(cx - dp(30), ty + dp(6), cx + dp(30), ty + dp(34));
                p.setColor(pill);
                canvas.drawRoundRect(b, dp(14), dp(14), p);
            }
            p.setColor(i == 0 ? sel : unsel);
            canvas.drawCircle(cx, ty + (labels ? dp(20) : tabsH / 2f), ic, p);
            if (labels) {
                p.setTextSize(dp(session.intValue("layout.bottomTabs.labelSizeSp", 10)));
                String l = item(tabs.get(i));
                canvas.drawText(l, cx - p.measureText(l) / 2f, ty + dp(44), p);
            }
            add("bottomTabs.item", rect);
        }
    }

    private void headerButtons(Canvas canvas, Ctx x, float barH, List<String> left, List<String> right) {
        p.setColor(x.icon);
        for (int i = 0; i < left.size(); i++) {
            canvas.drawCircle(dp(20) + i * dp(34), barH / 2f, dp(7), p);
        }
        for (int i = 0; i < right.size(); i++) {
            canvas.drawCircle(x.w - dp(20) - i * dp(34), barH / 2f, dp(7), p);
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
        int tabsH = dp(56);
        int body = session.intValue("tokens.typography.bodySizeSp", 16);
        int cap = session.intValue("tokens.typography.captionSizeSp", 12);
        boolean titleBold = bold("layout.chatList.row.title.weight", bold("tokens.typography.fontWeight", true));
        String[] names = {"Aye Aye", "Design team", "Ko Min", "Saved Messages", "Daw Mya", "Mg Mg"};
        String[] msgs = {"See you tomorrow!", "New theme is ready", "ok 👍", "Notes", "Thank you", "Call me"};
        String[] times = {"21:40", "20:15", "Fri", "Thu", "Mon", "Sun"};
        int[] avatars = {0xFFE17076, 0xFF7BC862, 0xFF65AADD, 0xFFEE7AAE, 0xFFF3A93C, 0xFF6EC9CB};
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
        int rows = Math.max(1, Math.min(ids.size(), (x.h - y0 - gap) / rowH));
        float side = card ? dp(12) : 0;
        rect.set(side, y0, x.w - side, y0 + rows * rowH);
        int fillC = session.colorChain(x.surface, "components.card.background");
        if ("glass".equals(gs)) {
            fillC = (fillC & 0x00FFFFFF) | 0xB0000000;
        }
        float rr = card ? dp(session.intValue("components.section.radiusDp", session.intValue("tokens.shapes.cardRadiusDp", 14))) : 0;
        shadow(canvas, rect, rr, session.intValue("tokens.elevation.cardDp", 0));
        p.setColor(fillC);
        canvas.drawRoundRect(rect, rr, rr, p);
        add("section", rect);
        for (int i = 0; i < rows; i++) {
            float y = y0 + i * rowH, cy = y + rowH / 2f;
            rect.set(side, y, x.w - side, y + rowH);
            if (i == 1 && session.draft.get("components.cell.pressedColor") != null) {
                p.setColor(session.color("components.cell.pressedColor", 0x22000000));
                canvas.drawRect(rect, p);
            }
            add("settingsRow", rect);
            add("cell", rect);
            p.setColor(i % 2 == 0 ? x.accent : x.icon);
            RectF ib = new RectF(side + padL, cy - ic, side + padL + 2 * ic, cy + ic);
            if ("square".equals(iconStyle)) {
                canvas.drawRect(ib, p);
            } else if ("rounded".equals(iconStyle)) {
                canvas.drawRoundRect(ib, ic * 0.5f, ic * 0.5f, p);
            } else {
                canvas.drawCircle(ib.centerX(), cy, ic * 0.7f, p);
            }
            float tx = side + padL + 2 * ic + dp(14);
            p.setColor(x.text);
            p.setFakeBoldText(tb);
            p.setTextSize(dp(title));
            canvas.drawText(item(ids.get(i)), tx, desc ? cy - dp(2) : cy + dp(5), p);
            p.setFakeBoldText(false);
            if (desc) {
                p.setColor(x.text2);
                p.setTextSize(dp(val));
                canvas.drawText("Description", tx, cy + dp(val + 3), p);
            }
            if (i < rows - 1 && session.bool("components.cell.dividerVisible", true)) {
                p.setColor(session.colorChain(x.divider, "components.cell.dividerColor"));
                float inset = dp(session.intValue("components.cell.dividerInsetDp", 60));
                canvas.drawRect(side + inset, y + rowH - 1, x.w - side, y + rowH, p);
            }
        }
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
        String[] names = {"Aye Aye", "Ko Min", "Daw Mya", "Mg Mg", "Su Su", "Thura", "Zaw Zaw"};
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
        int rowH = dp(session.intValue("layout.contacts.row.heightDp", 60));
        int ad = dp(session.intValue("layout.contacts.row.avatarSizeDp", session.intValue("screens.contacts.avatarSizeDp", 44)));
        boolean status = session.bool("layout.contacts.row.status", true);
        float side = "flat".equals(secStyle) ? 0 : dp(10);
        float y = top + dp(8);
        p.setColor(x.text2);
        p.setTextSize(dp(12));
        canvas.drawText(time ? "RECENT" : "A", dp(16), y + dp(12), p);
        y += dp(20);
        for (int i = 0; i < names.length && y + rowH < x.h; i++) {
            rect.set(side, y, x.w - side, y + rowH);
            int fill = x.surface;
            if ("glass".equals(secStyle)) {
                fill = (fill & 0x00FFFFFF) | 0xB0000000;
            }
            p.setColor(fill);
            canvas.drawRect(rect, p);
            add("contactRow", rect);
            float cy = y + rowH / 2f;
            avatar(canvas, side + dp(12) + ad / 2f, cy, Math.min(ad, rowH - dp(8)) / 2f, AV[i]);
            p.setColor(x.text);
            p.setTextSize(dp(15));
            canvas.drawText(names[i], side + dp(24) + ad, status ? cy - dp(2) : cy + dp(5), p);
            if (status) {
                p.setColor(x.text2);
                p.setTextSize(dp(12));
                canvas.drawText(time ? "last seen recently" : "online", side + dp(24) + ad, cy + dp(15), p);
            }
            y += rowH;
        }
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
        float y = top + hh + dp(12);
        boolean buttons = "buttons".equals(session.text("screens.profile.actionsStyle", "buttons"));
        for (int i = 0; i < 3; i++) {
            float bw = (x.w - dp(24) - dp(16)) / 3f;
            rect.set(dp(12) + i * (bw + dp(8)), y, dp(12) + i * (bw + dp(8)) + bw, y + (buttons ? dp(44) : dp(32)));
            p.setColor(buttons ? x.surface : (x.accent & 0x00FFFFFF) | 0x22000000);
            canvas.drawRoundRect(rect, dp(12), dp(12), p);
            add("profileHeader", rect);
        }
        y += (buttons ? dp(44) : dp(32)) + dp(12);
        float cr = dp(session.intValue("screens.profile.cardRadiusDp", 14));
        rect.set(dp(12), y, x.w - dp(12), Math.min(x.h - dp(8), y + dp(150)));
        p.setColor(x.surface);
        canvas.drawRoundRect(rect, cr, cr, p);
        add("infoCard", rect);
        String[] lines = {"Phone", "Bio", "Username"};
        for (int i = 0; i < lines.length && y + dp(44) * (i + 1) < x.h; i++) {
            p.setColor(x.text2);
            p.setTextSize(dp(12));
            canvas.drawText(lines[i], dp(28), y + dp(18) + i * dp(44), p);
            p.setColor(x.text);
            p.setTextSize(dp(15));
            canvas.drawText("•••••", dp(28), y + dp(36) + i * dp(44), p);
        }
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

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!editMode) {
            // Preview mode: dragging scrolls the list screens; taps do nothing
            if (!scrollable()) {
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    previewTap(e.getX(), e.getY());
                }
                return e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_UP;
            }
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastY = downY = e.getY();
                    moved = false;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        previewTap(e.getX(), e.getY());
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getY() - downY) > dp(8)) {
                        moved = true;
                    }
                    scrollY -= e.getY() - lastY;
                    lastY = e.getY();
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
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
