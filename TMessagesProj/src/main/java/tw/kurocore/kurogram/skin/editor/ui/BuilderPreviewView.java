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
        setMeasuredDimension(width, Math.min(AndroidUtilities.dp(300), width));
    }

    private int c(String name, int light, int dark) {
        return session.color("tokens.colors." + name, "dark".equals(session.appearance()) ? dark : light);
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

        // page background: gradient when any stop is set, else the background colour
        int gt = session.color("tokens.gradient.top", 0), gm = session.color("tokens.gradient.middle", 0), gb = session.color("tokens.gradient.bottom", 0);
        rect.set(0, 0, w, h);
        if (gt != 0 || gm != 0 || gb != 0) {
            int a = gt != 0 ? gt : gm != 0 ? gm : gb, b = gb != 0 ? gb : gm != 0 ? gm : gt, m = gm != 0 ? gm : a;
            Object dir = session.draft.get("tokens.gradient.direction");
            float x1 = 0, y1 = 0, x2 = 0, y2 = h;
            if ("horizontal".equals(dir)) {
                x2 = w;
                y2 = 0;
            } else if ("diagonal".equals(dir)) {
                x2 = w;
            }
            p.setShader(new LinearGradient(x1, y1, x2, y2, new int[]{a, m, b}, null, Shader.TileMode.CLAMP));
        } else {
            p.setShader(null);
            p.setColor(bg);
        }
        canvas.drawRect(rect, p);
        p.setShader(null);
        add("screenBackground", rect);

        if ("settings".equals(session.scene())) {
            drawSettings(canvas, w, h, surface, text, text2, accent, divider, icon);
        } else {
            drawChats(canvas, w, h, surface, text, text2, accent, onAccent, divider, icon, error);
        }

        // selection outline
        String sel = session.selected();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.id.equals(sel) && !"global".equals(sel)) {
                outline.setColor(0xFFFF2D87);
                rect.set(hit.r);
                rect.inset(-AndroidUtilities.dp(1), -AndroidUtilities.dp(1));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), outline);
            }
        }
    }

    private void add(String id, RectF r) {
        hits.add(new Hit(id, r));
    }

    private void bar(Canvas canvas, int w, int fill, int text, int icon, String title) {
        int bh = AndroidUtilities.dp(52);
        rect.set(0, 0, w, bh);
        p.setColor(fill);
        canvas.drawRect(rect, p);
        add("appBar", rect);
        p.setColor(text);
        p.setTextSize(AndroidUtilities.dp(18));
        p.setFakeBoldText(true);
        canvas.drawText(title, AndroidUtilities.dp(16), bh / 2f + AndroidUtilities.dp(6), p);
        p.setFakeBoldText(false);
        p.setColor(icon);
        canvas.drawCircle(w - AndroidUtilities.dp(28), bh / 2f, AndroidUtilities.dp(8), p);
        canvas.drawCircle(w - AndroidUtilities.dp(60), bh / 2f, AndroidUtilities.dp(8), p);
    }

    private void drawChats(Canvas canvas, int w, int h, int surface, int text, int text2, int accent, int onAccent, int divider, int icon, int error) {
        bar(canvas, w, surface, text, icon, "Kurogram");
        int top = AndroidUtilities.dp(52), rowH = AndroidUtilities.dp(64), tabsH = AndroidUtilities.dp(56);
        String[] names = {"Aye Aye", "Design team", "Ko Min", "Saved Messages", "Daw Mya"};
        String[] msgs = {"See you tomorrow!", "New theme is ready", "ok 👍", "Notes", "Thank you"};
        String[] times = {"21:40", "20:15", "Fri", "Thu", "Mon"};
        int[] avatars = {0xFFE17076, 0xFF7BC862, 0xFF65AADD, 0xFFEE7AAE, 0xFFF3A93C};
        int rows = Math.max(1, Math.min(names.length, (h - top - tabsH) / rowH));
        for (int i = 0; i < rows; i++) {
            float y = top + i * rowH;
            rect.set(0, y, w, y + rowH);
            p.setColor(surface);
            canvas.drawRect(rect, p);
            add("chatRow", rect);
            float cy = y + rowH / 2f;
            p.setColor(avatars[i]);
            rect.set(AndroidUtilities.dp(12), cy - AndroidUtilities.dp(22), AndroidUtilities.dp(56), cy + AndroidUtilities.dp(22));
            canvas.drawOval(rect, p);
            add("chatRow.avatar", rect);
            p.setColor(text);
            p.setFakeBoldText(true);
            p.setTextSize(AndroidUtilities.dp(15));
            float tx = AndroidUtilities.dp(68);
            canvas.drawText(names[i], tx, cy - AndroidUtilities.dp(4), p);
            rect.set(tx, cy - AndroidUtilities.dp(20), w * 0.62f, cy);
            add("chatRow.title", rect);
            p.setFakeBoldText(false);
            p.setColor(text2);
            p.setTextSize(AndroidUtilities.dp(14));
            canvas.drawText(msgs[i], tx, cy + AndroidUtilities.dp(16), p);
            rect.set(tx, cy + AndroidUtilities.dp(2), w * 0.62f, cy + AndroidUtilities.dp(22));
            add("chatRow.preview", rect);
            p.setTextSize(AndroidUtilities.dp(12));
            float tw = p.measureText(times[i]);
            canvas.drawText(times[i], w - AndroidUtilities.dp(12) - tw, cy - AndroidUtilities.dp(6), p);
            rect.set(w - AndroidUtilities.dp(12) - tw, cy - AndroidUtilities.dp(20), w - AndroidUtilities.dp(12), cy - AndroidUtilities.dp(2));
            add("chatRow.time", rect);
            if (i < 2) {
                p.setColor(i == 0 ? accent : error);
                rect.set(w - AndroidUtilities.dp(36), cy + AndroidUtilities.dp(4), w - AndroidUtilities.dp(12), cy + AndroidUtilities.dp(24));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), p);
                p.setColor(onAccent);
                p.setTextSize(AndroidUtilities.dp(12));
                canvas.drawText(String.valueOf(i + 2), w - AndroidUtilities.dp(27), cy + AndroidUtilities.dp(19), p);
                add("chatRow.badge", rect);
            }
            p.setColor(divider);
            canvas.drawRect(tx, y + rowH - 1, w, y + rowH, p);
        }
        // bottom tabs
        float ty = h - tabsH;
        rect.set(0, ty, w, h);
        p.setColor(surface);
        canvas.drawRect(rect, p);
        add("bottomTabs", rect);
        for (int i = 0; i < 4; i++) {
            float cx = w * (i + 0.5f) / 4f;
            p.setColor(i == 0 ? accent : icon);
            canvas.drawCircle(cx, ty + AndroidUtilities.dp(20), AndroidUtilities.dp(9), p);
            p.setTextSize(AndroidUtilities.dp(10));
            String l = new String[]{"Chats", "Contacts", "Calls", "Settings"}[i];
            canvas.drawText(l, cx - p.measureText(l) / 2f, ty + AndroidUtilities.dp(44), p);
            rect.set(cx - w / 8f, ty, cx + w / 8f, h);
            add("bottomTabs.item", rect);
        }
        // compose button
        float fr = AndroidUtilities.dp(26);
        float fx = w - AndroidUtilities.dp(16) - fr, fy = ty - AndroidUtilities.dp(16) - fr;
        p.setColor(accent);
        canvas.drawCircle(fx, fy, fr, p);
        p.setColor(onAccent);
        p.setStrokeWidth(AndroidUtilities.dp(2.5f));
        canvas.drawLine(fx - AndroidUtilities.dp(9), fy, fx + AndroidUtilities.dp(9), fy, p);
        canvas.drawLine(fx, fy - AndroidUtilities.dp(9), fx, fy + AndroidUtilities.dp(9), p);
        rect.set(fx - fr, fy - fr, fx + fr, fy + fr);
        add("fab", rect);
    }

    private void drawSettings(Canvas canvas, int w, int h, int surface, int text, int text2, int accent, int divider, int icon) {
        bar(canvas, w, surface, text, icon, "Settings");
        int top = AndroidUtilities.dp(52) + AndroidUtilities.dp(16), rowH = AndroidUtilities.dp(56);
        String[] titles = {"Notifications", "Privacy", "Data and storage", "Appearance", "Language"};
        String[] subs = {"On", "Calls, last seen", "Network usage", "Theme, colours", "English"};
        int rows = Math.max(1, Math.min(titles.length, (h - top - AndroidUtilities.dp(16)) / rowH));
        rect.set(AndroidUtilities.dp(12), top, w - AndroidUtilities.dp(12), top + rows * rowH);
        p.setColor(surface);
        canvas.drawRoundRect(rect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), p);
        add("section", rect);
        for (int i = 0; i < rows; i++) {
            float y = top + i * rowH, cy = y + rowH / 2f;
            rect.set(AndroidUtilities.dp(12), y, w - AndroidUtilities.dp(12), y + rowH);
            add("cell", rect);
            p.setColor(i % 2 == 0 ? accent : icon);
            canvas.drawCircle(AndroidUtilities.dp(36), cy, AndroidUtilities.dp(10), p);
            p.setColor(text);
            p.setTextSize(AndroidUtilities.dp(15));
            canvas.drawText(titles[i], AndroidUtilities.dp(60), cy - AndroidUtilities.dp(2), p);
            p.setColor(text2);
            p.setTextSize(AndroidUtilities.dp(12));
            canvas.drawText(subs[i], AndroidUtilities.dp(60), cy + AndroidUtilities.dp(16), p);
            if (i < rows - 1) {
                p.setColor(divider);
                canvas.drawRect(AndroidUtilities.dp(60), y + rowH - 1, w - AndroidUtilities.dp(12), y + rowH, p);
            }
        }
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
