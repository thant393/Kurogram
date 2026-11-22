package tw.kurocore.kurogram.skin;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;

/**
 * Settings icons drawn entirely in code (no image files), on a 24x24 grid. A theme picks the look with
 * {@code screens.settings.iconPack}: "outline" (line icons) or "solid" (filled shapes with cut-out details).
 * Unknown icons return null so the caller keeps Telegram's own drawable.
 */
public final class KuroIcons {

    private KuroIcons() {
    }

    private static final int MAIN = 0;   // filled in solid, stroked in outline
    private static final int DETAIL = 1; // cut out in solid, stroked in outline
    private static final int LINE = 2;   // always stroked

    private static final class Shape {
        final Path path;
        final int kind;

        Shape(Path path, int kind) {
            this.path = path;
            this.kind = kind;
        }
    }

    /** Returns a drawable for a resource entry name such as "settings_chat", or null when not covered. */
    public static Drawable forResource(String entryName, boolean solid) {
        if (entryName == null) {
            return null;
        }
        ArrayList<Shape> shapes = build(entryName);
        return shapes == null ? null : new IconDrawable(shapes, solid);
    }

    public static boolean isPack(String pack) {
        return "outline".equals(pack) || "solid".equals(pack);
    }

    private static Path roundRect(float l, float t, float r, float b, float rad) {
        Path p = new Path();
        p.addRoundRect(new RectF(l, t, r, b), rad, rad, Path.Direction.CW);
        return p;
    }

    private static Path circle(float x, float y, float r) {
        Path p = new Path();
        p.addCircle(x, y, r, Path.Direction.CW);
        return p;
    }

    private static Path line(float x1, float y1, float x2, float y2) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        return p;
    }

    private static Path poly(float... xy) {
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
    }

    private static Path dot(float x, float y) {
        return line(x, y, x, y + 0.01f);
    }

    private static Path arrowArc(float cx, float cy, float r, float start, float sweep) {
        Path p = new Path();
        p.addArc(new RectF(cx - r, cy - r, cx + r, cy + r), start, sweep);
        double end = Math.toRadians(start + sweep);
        float ex = (float) (cx + r * Math.cos(end));
        float ey = (float) (cy + r * Math.sin(end));
        double tangent = end + Math.PI / 2;
        for (int s = -1; s <= 1; s += 2) {
            double a = tangent + Math.PI + s * Math.toRadians(35);
            p.moveTo(ex, ey);
            p.lineTo((float) (ex + 3.2 * Math.cos(a)), (float) (ey + 3.2 * Math.sin(a)));
        }
        return p;
    }

    private static Path star(float cx, float cy, float outer, float inner) {
        Path p = new Path();
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(-90 + i * 36);
            float rad = i % 2 == 0 ? outer : inner;
            float x = (float) (cx + rad * Math.cos(a));
            float y = (float) (cy + rad * Math.sin(a));
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.close();
        return p;
    }

    private static ArrayList<Shape> build(String name) {
        ArrayList<Shape> s = new ArrayList<>();
        switch (name) {
            case "settings_account": {
                s.add(new Shape(circle(12, 8, 4), MAIN));
                Path body = new Path();
                body.moveTo(4, 20.5f);
                body.quadTo(4, 14.5f, 12, 14.5f);
                body.quadTo(20, 14.5f, 20, 20.5f);
                body.close();
                s.add(new Shape(body, MAIN));
                break;
            }
            case "settings_chat": {
                Path b = roundRect(3, 4, 21, 17, 5);
                s.add(new Shape(b, MAIN));
                s.add(new Shape(poly(8, 16, 8, 21, 13, 16.5f), MAIN));
                s.add(new Shape(line(8, 9, 16, 9), DETAIL));
                s.add(new Shape(line(8, 12.5f, 13, 12.5f), DETAIL));
                break;
            }
            case "settings_privacy": {
                Path sh = new Path();
                sh.moveTo(12, 3);
                sh.lineTo(20, 6);
                sh.lineTo(20, 12);
                sh.quadTo(20, 19, 12, 21);
                sh.quadTo(4, 19, 4, 12);
                sh.lineTo(4, 6);
                sh.close();
                s.add(new Shape(sh, MAIN));
                s.add(new Shape(poly(8.5f, 12, 11, 14.5f, 15.5f, 9.5f), DETAIL));
                break;
            }
            case "settings_sounds": {
                Path bell = new Path();
                bell.moveTo(6, 17);
                bell.lineTo(6, 11);
                bell.arcTo(new RectF(6, 5, 18, 17), 180, 180);
                bell.lineTo(18, 17);
                bell.lineTo(19.5f, 18.5f);
                bell.lineTo(4.5f, 18.5f);
                bell.close();
                s.add(new Shape(bell, MAIN));
                s.add(new Shape(line(10, 21, 14, 21), LINE));
                break;
            }
            case "settings_data": {
                s.add(new Shape(roundRect(4, 3.5f, 20, 10, 3), MAIN));
                s.add(new Shape(roundRect(4, 14, 20, 20.5f, 3), MAIN));
                s.add(new Shape(dot(7.8f, 6.75f), DETAIL));
                s.add(new Shape(dot(7.8f, 17.25f), DETAIL));
                break;
            }
            case "settings_folders": {
                Path f = new Path();
                f.moveTo(3, 7);
                f.quadTo(3, 5, 5, 5);
                f.lineTo(9.5f, 5);
                f.lineTo(11.5f, 7.5f);
                f.lineTo(19, 7.5f);
                f.quadTo(21, 7.5f, 21, 9.5f);
                f.lineTo(21, 17);
                f.quadTo(21, 19.5f, 19, 19.5f);
                f.lineTo(5, 19.5f);
                f.quadTo(3, 19.5f, 3, 17);
                f.close();
                s.add(new Shape(f, MAIN));
                break;
            }
            case "settings_devices": {
                s.add(new Shape(roundRect(7, 2.5f, 17, 21.5f, 3), MAIN));
                s.add(new Shape(line(10.5f, 18.5f, 13.5f, 18.5f), DETAIL));
                break;
            }
            case "settings_power": {
                s.add(new Shape(roundRect(3, 7, 19, 17, 3), MAIN));
                s.add(new Shape(roundRect(20, 10.5f, 22, 13.5f, 1), MAIN));
                s.add(new Shape(poly(12.5f, 9, 9.5f, 12.5f, 13.5f, 12.5f, 10.5f, 16), DETAIL));
                break;
            }
            case "settings_language": {
                s.add(new Shape(circle(12, 12, 9), MAIN));
                Path m = new Path();
                m.addOval(new RectF(8, 3, 16, 21), Path.Direction.CW);
                s.add(new Shape(m, DETAIL));
                s.add(new Shape(line(3, 12, 21, 12), DETAIL));
                break;
            }
            case "settings_ask": {
                s.add(new Shape(roundRect(3, 4, 21, 17, 5), MAIN));
                s.add(new Shape(poly(8, 16, 8, 21, 13, 16.5f), MAIN));
                Path q = new Path();
                q.moveTo(9.8f, 9.6f);
                q.quadTo(9.8f, 7.4f, 12, 7.4f);
                q.quadTo(14.2f, 7.4f, 14.2f, 9.3f);
                q.quadTo(14.2f, 10.5f, 12, 11.3f);
                q.lineTo(12, 12);
                s.add(new Shape(q, DETAIL));
                s.add(new Shape(dot(12, 14), DETAIL));
                break;
            }
            case "settings_faq": {
                s.add(new Shape(roundRect(5, 3, 19, 21, 3), MAIN));
                s.add(new Shape(line(8.5f, 9, 15.5f, 9), DETAIL));
                s.add(new Shape(line(8.5f, 12.5f, 15.5f, 12.5f), DETAIL));
                s.add(new Shape(line(8.5f, 16, 12.5f, 16), DETAIL));
                break;
            }
            case "settings_features": {
                Path sp = new Path();
                sp.moveTo(12, 3);
                sp.quadTo(13, 11, 21, 12);
                sp.quadTo(13, 13, 12, 21);
                sp.quadTo(11, 13, 3, 12);
                sp.quadTo(11, 11, 12, 3);
                sp.close();
                s.add(new Shape(sp, MAIN));
                break;
            }
            case "settings_policy": {
                s.add(new Shape(roundRect(5, 3, 19, 21, 3), MAIN));
                s.add(new Shape(poly(8.8f, 12.5f, 11.2f, 14.9f, 15.4f, 10), DETAIL));
                break;
            }
            case "kuro_theme": {
                s.add(new Shape(circle(12, 12, 9), MAIN));
                s.add(new Shape(dot(8.3f, 10), DETAIL));
                s.add(new Shape(dot(12, 7.3f), DETAIL));
                s.add(new Shape(dot(15.8f, 10), DETAIL));
                s.add(new Shape(dot(9.5f, 15), DETAIL));
                break;
            }
            case "filled_profile_settings": {
                s.add(new Shape(circle(12, 12, 6), MAIN));
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    s.add(new Shape(line((float) (12 + 7.8 * Math.cos(a)), (float) (12 + 7.8 * Math.sin(a)),
                            (float) (12 + 10 * Math.cos(a)), (float) (12 + 10 * Math.sin(a))), LINE));
                }
                s.add(new Shape(circle(12, 12, 2.2f), DETAIL));
                break;
            }
            case "kuro_user_finder": {
                s.add(new Shape(circle(10.5f, 10.5f, 6.5f), MAIN));
                s.add(new Shape(line(15.5f, 15.5f, 20.5f, 20.5f), LINE));
                break;
            }
            case "kuro_special_contacts": {
                s.add(new Shape(star(12, 12.8f, 9.5f, 4.2f), MAIN));
                break;
            }
            case "kuro_online_contacts": {
                s.add(new Shape(circle(10, 8.5f, 3.7f), MAIN));
                Path body = new Path();
                body.moveTo(3.5f, 20);
                body.quadTo(3.5f, 14.5f, 10, 14.5f);
                body.quadTo(16.5f, 14.5f, 16.5f, 20);
                body.close();
                s.add(new Shape(body, MAIN));
                s.add(new Shape(circle(19, 6, 2.3f), MAIN));
                break;
            }
            case "kuro_contact_changes": {
                s.add(new Shape(arrowArc(12, 12, 7.5f, 200, 140), LINE));
                s.add(new Shape(arrowArc(12, 12, 7.5f, 20, 140), LINE));
                break;
            }
            default:
                return buildUi(name);
        }
        return s;
    }

    /** Glyphs for the Studio's list screens (and any theme that wants them). */
    private static ArrayList<Shape> buildUi(String name) {
        ArrayList<Shape> s = new ArrayList<>();
        switch (name) {
            case "ui_grid":
                s.add(new Shape(roundRect(4, 4, 10.5f, 10.5f, 2), MAIN));
                s.add(new Shape(roundRect(13.5f, 4, 20, 10.5f, 2), MAIN));
                s.add(new Shape(roundRect(4, 13.5f, 10.5f, 20, 2), MAIN));
                s.add(new Shape(roundRect(13.5f, 13.5f, 20, 20, 2), MAIN));
                break;
            case "ui_chat":
                s.add(new Shape(circle(12, 11, 8.5f), MAIN));
                s.add(new Shape(poly(6, 17, 4, 21, 9.5f, 18.6f), MAIN));
                break;
            case "ui_lock":
                s.add(new Shape(roundRect(5, 10.5f, 19, 21, 3), MAIN));
                Path sh = new Path();
                sh.moveTo(8.5f, 10.5f);
                sh.lineTo(8.5f, 8);
                sh.arcTo(new RectF(8.5f, 3.5f, 15.5f, 12.5f), 180, 180);
                sh.lineTo(15.5f, 10.5f);
                s.add(new Shape(sh, LINE));
                s.add(new Shape(dot(12, 15.7f), DETAIL));
                break;
            case "ui_star":
                s.add(new Shape(star(12, 12.8f, 9.5f, 4.2f), MAIN));
                break;
            case "ui_megaphone":
                s.add(new Shape(poly(4, 10, 9, 10, 17, 5, 17, 19, 9, 14, 4, 14), MAIN));
                s.add(new Shape(line(8, 14.5f, 9.5f, 20), LINE));
                s.add(new Shape(line(20, 9.5f, 20, 14.5f), LINE));
                break;
            case "ui_people":
            case "ui_user": {
                s.add(new Shape(circle(9.5f, 8, 3.6f), MAIN));
                Path b = new Path();
                b.moveTo(3, 20);
                b.quadTo(3, 14, 9.5f, 14);
                b.quadTo(16, 14, 16, 20);
                b.close();
                s.add(new Shape(b, MAIN));
                if ("ui_people".equals(name)) {
                    s.add(new Shape(circle(17, 8.5f, 2.8f), MAIN));
                    Path b2 = new Path();
                    b2.moveTo(17.5f, 14.2f);
                    b2.quadTo(21.5f, 14.8f, 21.5f, 19);
                    s.add(new Shape(b2, LINE));
                }
                break;
            }
            case "ui_link":
                s.add(new Shape(roundRect(3, 8.5f, 12, 15.5f, 3.5f), MAIN));
                s.add(new Shape(roundRect(12, 8.5f, 21, 15.5f, 3.5f), MAIN));
                s.add(new Shape(line(9.5f, 12, 14.5f, 12), LINE));
                break;
            case "ui_heart": {
                Path hp = new Path();
                hp.moveTo(12, 20);
                hp.cubicTo(2.5f, 13.5f, 3.5f, 5.5f, 8.5f, 5.5f);
                hp.cubicTo(10.5f, 5.5f, 11.6f, 6.6f, 12, 7.8f);
                hp.cubicTo(12.4f, 6.6f, 13.5f, 5.5f, 15.5f, 5.5f);
                hp.cubicTo(20.5f, 5.5f, 21.5f, 13.5f, 12, 20);
                hp.close();
                s.add(new Shape(hp, MAIN));
                break;
            }
            case "ui_phone": {
                Path ph = new Path();
                ph.moveTo(6.5f, 4);
                ph.lineTo(9.5f, 4);
                ph.lineTo(11, 8.5f);
                ph.lineTo(9, 10);
                ph.quadTo(10.5f, 13.5f, 14, 15);
                ph.lineTo(15.5f, 13);
                ph.lineTo(20, 14.5f);
                ph.lineTo(20, 17.5f);
                ph.quadTo(20, 20, 17.5f, 20);
                ph.quadTo(4, 19, 4, 6.5f);
                ph.quadTo(4, 4, 6.5f, 4);
                ph.close();
                s.add(new Shape(ph, MAIN));
                break;
            }
            case "ui_calls": {
                Path ph = new Path();
                ph.moveTo(6.5f, 4);
                ph.lineTo(9.5f, 4);
                ph.lineTo(11, 8.5f);
                ph.lineTo(9, 10);
                ph.quadTo(10.5f, 13.5f, 14, 15);
                ph.lineTo(15.5f, 13);
                ph.lineTo(20, 14.5f);
                ph.lineTo(20, 17.5f);
                ph.quadTo(20, 20, 17.5f, 20);
                ph.quadTo(4, 19, 4, 6.5f);
                ph.quadTo(4, 4, 6.5f, 4);
                ph.close();
                s.add(new Shape(ph, MAIN));
                break;
            }
            case "ui_at": {
                s.add(new Shape(circle(12, 12, 3.2f), MAIN));
                Path a = new Path();
                a.addArc(new RectF(3.5f, 3.5f, 20.5f, 20.5f), 40, 290);
                a.lineTo(16, 12);
                a.quadTo(16, 15.6f, 18.4f, 15.6f);
                s.add(new Shape(a, LINE));
                break;
            }
            case "ui_cake":
                s.add(new Shape(roundRect(4.5f, 11, 19.5f, 20, 2.5f), MAIN));
                s.add(new Shape(line(4.5f, 15, 19.5f, 15), DETAIL));
                s.add(new Shape(line(12, 7, 12, 11), LINE));
                s.add(new Shape(dot(12, 5), LINE));
                break;
            case "ui_ai":
                s.add(new Shape(star(8, 8, 4.4f, 1.8f), MAIN));
                s.add(new Shape(line(12, 20, 15, 11), LINE));
                s.add(new Shape(line(15, 11, 18, 20), LINE));
                s.add(new Shape(line(13, 17, 17, 17), LINE));
                break;
            case "ui_userplus":
                s.add(new Shape(circle(10, 8, 4), MAIN));
                Path up = new Path();
                up.moveTo(3, 20);
                up.quadTo(3, 14, 10, 14);
                up.quadTo(13, 14, 14.5f, 15);
                s.add(new Shape(up, LINE));
                s.add(new Shape(line(18, 14, 18, 20), LINE));
                s.add(new Shape(line(15, 17, 21, 17), LINE));
                break;
            case "ui_logout":
                s.add(new Shape(poly(10, 4, 5, 4, 5, 20, 10, 20), LINE));
                s.add(new Shape(line(9, 12, 20, 12), LINE));
                s.add(new Shape(poly(16, 8, 20, 12, 16, 16), LINE));
                break;
            case "ui_wallpaper":
                s.add(new Shape(roundRect(4, 4, 20, 20, 3), MAIN));
                s.add(new Shape(poly(4.5f, 17, 9, 12, 13, 16, 16, 13, 19.5f, 17), DETAIL));
                s.add(new Shape(dot(15.5f, 8.5f), DETAIL));
                break;
            case "ui_namecolor":
            case "ui_brush":
                s.add(new Shape(circle(12, 12, 9), MAIN));
                s.add(new Shape(dot(8.5f, 10), DETAIL));
                s.add(new Shape(dot(12, 7.5f), DETAIL));
                s.add(new Shape(dot(15.5f, 10), DETAIL));
                s.add(new Shape(dot(9.5f, 15), DETAIL));
                break;
            case "ui_night": {
                Path m = new Path();
                m.moveTo(15, 4);
                m.quadTo(8, 5, 8, 12);
                m.quadTo(8, 19, 15.5f, 20);
                m.quadTo(5, 22, 4.5f, 12);
                m.quadTo(5, 2.5f, 15, 4);
                s.add(new Shape(m, MAIN));
                s.add(new Shape(star(18, 7, 2.4f, 1), MAIN));
                break;
            }
            case "ui_timer":
                s.add(new Shape(circle(12, 12, 8.5f), MAIN));
                s.add(new Shape(poly(12, 7.5f, 12, 12, 15, 14), DETAIL));
                break;
            case "ui_key":
                s.add(new Shape(circle(8.5f, 14.5f, 4.5f), MAIN));
                s.add(new Shape(line(12, 11.5f, 20, 4.5f), LINE));
                s.add(new Shape(line(17, 7.5f, 19.5f, 10), LINE));
                break;
            case "ui_mail":
                s.add(new Shape(roundRect(3, 5.5f, 21, 18.5f, 3), MAIN));
                s.add(new Shape(poly(4, 7.5f, 12, 13.5f, 20, 7.5f), DETAIL));
                break;
            case "ui_hand":
                s.add(new Shape(roundRect(7, 9, 17, 20, 4), MAIN));
                s.add(new Shape(line(8.5f, 9, 8.5f, 5), LINE));
                s.add(new Shape(line(11, 9, 11, 3.5f), LINE));
                s.add(new Shape(line(13.5f, 9, 13.5f, 4), LINE));
                s.add(new Shape(line(16, 9, 16, 6), LINE));
                break;
            case "ui_stories":
                s.add(new Shape(circle(12, 12, 8.5f), MAIN));
                s.add(new Shape(poly(10.5f, 8.5f, 15.5f, 12, 10.5f, 15.5f), DETAIL));
                break;
            case "ui_storage":
                s.add(new Shape(circle(12, 12, 8), MAIN));
                s.add(new Shape(circle(12, 12, 3.5f), DETAIL));
                break;
            case "ui_bars":
                s.add(new Shape(roundRect(5, 12, 8.5f, 20, 1), MAIN));
                s.add(new Shape(roundRect(10.3f, 6, 13.7f, 20, 1), MAIN));
                s.add(new Shape(roundRect(15.5f, 9, 19, 20, 1), MAIN));
                break;
            case "ui_plus":
                s.add(new Shape(circle(12, 12, 9), MAIN));
                s.add(new Shape(line(12, 7.5f, 12, 16.5f), DETAIL));
                s.add(new Shape(line(7.5f, 12, 16.5f, 12), DETAIL));
                break;
            case "ui_qr":
                s.add(new Shape(roundRect(4, 4, 10, 10, 1.5f), MAIN));
                s.add(new Shape(roundRect(14, 4, 20, 10, 1.5f), MAIN));
                s.add(new Shape(roundRect(4, 14, 10, 20, 1.5f), MAIN));
                s.add(new Shape(dot(15, 15), LINE));
                s.add(new Shape(dot(19, 15), LINE));
                s.add(new Shape(dot(15, 19), LINE));
                s.add(new Shape(dot(19, 19), LINE));
                break;
            case "ui_sticker":
                s.add(new Shape(roundRect(4, 4, 20, 20, 5), MAIN));
                s.add(new Shape(dot(9.5f, 10), DETAIL));
                s.add(new Shape(dot(14.5f, 10), DETAIL));
                Path sm = new Path();
                sm.moveTo(8.5f, 14);
                sm.quadTo(12, 18, 15.5f, 14);
                s.add(new Shape(sm, DETAIL));
                break;
            case "ui_smile": {
                s.add(new Shape(circle(12, 12, 9), MAIN));
                s.add(new Shape(dot(9, 10), DETAIL));
                s.add(new Shape(dot(15, 10), DETAIL));
                Path sm2 = new Path();
                sm2.moveTo(8, 14);
                sm2.quadTo(12, 18, 16, 14);
                s.add(new Shape(sm2, DETAIL));
                break;
            }
            case "ui_chatdots":
                s.add(new Shape(circle(12, 11, 8.5f), MAIN));
                s.add(new Shape(poly(6, 17, 4, 21, 9.5f, 18.6f), MAIN));
                s.add(new Shape(dot(8.5f, 11), DETAIL));
                s.add(new Shape(dot(12, 11), DETAIL));
                s.add(new Shape(dot(15.5f, 11), DETAIL));
                break;
            case "ui_video":
                s.add(new Shape(roundRect(3, 6.5f, 16, 17.5f, 3), MAIN));
                s.add(new Shape(poly(16.5f, 10.5f, 21, 8, 21, 16, 16.5f, 13.5f), MAIN));
                break;
            case "ui_gif":
                s.add(new Shape(roundRect(3, 6, 21, 18, 3.5f), MAIN));
                s.add(new Shape(line(8, 10.5f, 8, 13.5f), DETAIL));
                s.add(new Shape(line(12, 10.5f, 12, 13.5f), DETAIL));
                s.add(new Shape(line(15.5f, 10.5f, 15.5f, 13.5f), DETAIL));
                break;
            default:
                return null;
        }
        return s;
    }

    private static final class IconDrawable extends Drawable {
        private final ArrayList<Shape> shapes;
        private final boolean solid;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconDrawable(ArrayList<Shape> shapes, boolean solid) {
            this.shapes = shapes;
            this.solid = solid;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xffffffff);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(0xffffffff);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(1.8f);
            clear.setStyle(Paint.Style.STROKE);
            clear.setStrokeCap(Paint.Cap.ROUND);
            clear.setStrokeJoin(Paint.Join.ROUND);
            clear.setStrokeWidth(1.8f);
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) {
                return;
            }
            int layer = canvas.saveLayer(b.left, b.top, b.right, b.bottom, null);
            canvas.translate(b.left, b.top);
            float sc = Math.min(b.width(), b.height()) / 24f;
            canvas.scale(sc, sc);
            for (Shape sh : shapes) {
                if (sh.kind == LINE || !solid) {
                    canvas.drawPath(sh.path, stroke);
                } else if (sh.kind == MAIN) {
                    canvas.drawPath(sh.path, fill);
                    canvas.drawPath(sh.path, stroke);
                }
            }
            if (solid) {
                for (Shape sh : shapes) {
                    if (sh.kind == DETAIL) {
                        canvas.drawPath(sh.path, clear);
                    }
                }
            }
            canvas.restoreToCount(layer);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            stroke.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            stroke.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 24;
        }

        @Override
        public int getIntrinsicHeight() {
            return 24;
        }
    }
}
